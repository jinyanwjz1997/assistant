package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.service.VectorService;
import com.jinyan.assistant.entity.DocumentChunk;
import com.jinyan.assistant.entity.DocumentPage;
import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.entity.RagSegmentMessage;
import com.jinyan.assistant.service.DocumentChunker;
import com.jinyan.assistant.service.FileService;
import com.jinyan.assistant.service.RedisStreamRagSegmentQueue;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 负责文档解析、原文持久化、智能切片入队及向量相似度检索。 */
@Service
@Slf4j
public class VectorServiceImpl implements VectorService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final Set<String> TEXT_APPLICATION_TYPES = Set.of(
            "application/json",
            "application/xml",
            "application/yaml",
            "application/x-yaml"
    );

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final FileService fileService;
    private final RedisStreamRagSegmentQueue segmentQueue;
    private final DocumentChunker documentChunker;

    /**
     * 通过构造器注入，embeddingStore 是 PgVectorStoreConfig 里声明的单例 Bean，
     * 不用在每个方法里重复 new。
     *
     * @param embeddingModel 文本向量模型
     * @param embeddingStore 知识库使用的向量存储
     * @param fileService 文件原文持久化服务
     * @param segmentQueue RAG 片段 Redis Stream 队列
     * @param documentChunker 结构感知文档切片器
     */
    public VectorServiceImpl(EmbeddingModel embeddingModel,
                             @Qualifier("knowledgeEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore,
                             FileService fileService,
                             RedisStreamRagSegmentQueue segmentQueue,
                             DocumentChunker documentChunker) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.fileService = fileService;
        this.segmentQueue = segmentQueue;
        this.documentChunker = documentChunker;
    }

    /**
     * 保存一段普通文本，并将智能切片后的消息发送到异步处理队列。
     *
     * @param context 待入库的完整文本
     * @param sourceFile 文本来源文件或来源标识
     * @throws IllegalArgumentException 当来源标识为空时抛出
     */
    @Override
    public void addTextToVector(String context, String sourceFile) {
        String fileName = extractFileName(sourceFile);
        FileInfo fileInfo = fileService.saveFile(fileName, context);
        enqueueSegments(
                fileInfo.getFileId(),
                sourceFile,
                "text/plain",
                List.of(new DocumentPage(1, context))
        );
    }

    /**
     * 将用户问题向量化并从知识库中检索相似片段。
     *
     * @param userQuery 用户查询文本
     * @param maxResults 最终期望返回的父章节数量
     * @return 按相似度排序的候选子片段
     * @throws IllegalArgumentException 当查询为空或结果数量小于 1 时抛出
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> searchRelevantContext(String userQuery, int maxResults) {
        if (!StringUtils.hasText(userQuery)) {
            throw new IllegalArgumentException("userQuery must not be blank");
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be positive");
        }

        // 同一父章节可能命中多个子片段，因此多取候选，RAG 回答服务再按 parentSectionId 去重。
        int candidateCount = Math.min(100, Math.max(maxResults, maxResults * 3));
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(userQuery).content())
                .maxResults(candidateCount)
                .minScore(0.7)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);
        return result.matches();
    }

    /**
     * 从本地文件系统读取文档，保存原文并异步生成摘要和向量。
     *
     * @param filePath 本地文件路径
     * @return 已保存文档的文件 ID，可用于查询入库进度
     * @throws IllegalArgumentException 当文件不存在或不是普通文件时抛出
     * @throws UncheckedIOException 当文件读取失败时抛出
     */
    @Override
    public String readDocument(String filePath) {
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            return readDocument(
                    path.getFileName().toString(),
                    Files.probeContentType(path),
                    inputStream,
                    path.toString()
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read file: " + path, exception);
        }
    }

    /**
     * 从上传流读取文档，保存原文并异步生成摘要和向量。
     *
     * @param fileName 上传文件名
     * @param contentType 客户端声明的 MIME 类型，可以为空
     * @param inputStream 文件内容输入流，由调用方负责关闭
     * @return 已保存文档的文件 ID，可用于查询入库进度
     */
    @Override
    public String readDocument(String fileName, String contentType, InputStream inputStream) {
        String safeFileName = StringUtils.cleanPath(
                StringUtils.hasText(fileName) ? fileName : "uploaded-file"
        );
        return readDocument(safeFileName, contentType, inputStream, safeFileName);
    }

    /**
     * 执行统一的文档解析、原文保存和切片入队流程。
     *
     * @param fileName 用于格式判断和持久化的文件名
     * @param contentType 原始 MIME 类型
     * @param inputStream 文件内容输入流
     * @param source 写入向量元数据的来源信息
     * @return 已保存文档的文件 ID
     * @throws IllegalArgumentException 当文档格式不支持或未提取出文本时抛出
     */
    private String readDocument(String fileName,
                                String contentType,
                                InputStream inputStream,
                                String source) {
        String normalizedContentType = resolveContentType(contentType, fileName);
        ParsedDocument parsedDocument = parseDocument(
                fileName,
                normalizedContentType,
                inputStream
        );
        if (!StringUtils.hasText(parsedDocument.fullText())) {
            throw new IllegalArgumentException(
                    "No text could be extracted from file: " + fileName
                            + ". Scanned PDF files require OCR."
            );
        }

        FileInfo fileInfo = fileService.saveFile(fileName, parsedDocument.fullText());
        enqueueSegments(
                fileInfo.getFileId(),
                source,
                normalizedContentType,
                parsedDocument.pages()
        );
        log.info("文档已解析并进入RAG异步队列, fileName:{}, contentType:{}, fileId:{}",
                fileName, contentType, fileInfo.getFileId());
        return fileInfo.getFileId();
    }

    /**
     * 对解析后的页面执行智能切片，补充稳定 ID 和相邻关系后批量入队。
     *
     * @param fileId 已持久化文档的 ID
     * @param source 文件来源信息
     * @param contentType 规范化后的 MIME 类型
     * @param pages 按原文顺序排列的页面
     */
    private void enqueueSegments(String fileId,
                                 String source,
                                 String contentType,
                                 List<DocumentPage> pages) {
        // 标题、段落和页码在这里转换成父子片段；摘要及向量化仍由消费者异步执行。
        List<DocumentChunk> chunks = documentChunker.split(fileId, pages, contentType);
        List<String> segmentIds = new java.util.ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            // fileId + 片段序号可稳定生成同一 ID，为 Redis 重投和数据库 upsert 提供幂等键。
            segmentIds.add(UUID.nameUUIDFromBytes(
                    (fileId + ":" + index).getBytes(StandardCharsets.UTF_8)
            ).toString());
        }

        List<RagSegmentMessage> messages = new java.util.ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            DocumentChunk chunk = chunks.get(index);
            messages.add(new RagSegmentMessage(
                    segmentIds.get(index),
                    fileId,
                    source,
                    contentType,
                    index,
                    chunks.size(),
                    chunk.text(),
                    chunk.parentSectionId(),
                    chunk.parentText(),
                    chunk.heading(),
                    chunk.headingPath(),
                    chunk.pageNumber(),
                    index == 0 ? "" : segmentIds.get(index - 1),
                    index + 1 == chunks.size() ? "" : segmentIds.get(index + 1),
                    0
            ));
        }
        segmentQueue.enqueue(fileId, messages);
    }

    /**
     * 根据 MIME 类型选择普通文本解析器或保留页码的 PDF 解析器。
     *
     * @param fileName 文件名，用于 MIME 类型不可靠时辅助识别格式
     * @param normalizedContentType 规范化后的 MIME 类型
     * @param inputStream 文件内容输入流
     * @return 包含完整文本和分页文本的解析结果
     * @throws IllegalArgumentException 当文件格式不受支持时抛出
     */
    private ParsedDocument parseDocument(
            String fileName,
            String normalizedContentType,
            InputStream inputStream
    ) {
        if (isPdf(normalizedContentType, fileName)) {
            return parsePdf(inputStream);
        }
        if (normalizedContentType.startsWith("text/")
                || TEXT_APPLICATION_TYPES.contains(normalizedContentType)) {
            return parseText(inputStream);
        }

        String normalizedFileName = fileName.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedContentType)
                || "application/octet-stream".equals(normalizedContentType)) {
            if (normalizedFileName.endsWith(".txt")
                    || normalizedFileName.endsWith(".md")
                    || normalizedFileName.endsWith(".json")
                    || normalizedFileName.endsWith(".xml")
                    || normalizedFileName.endsWith(".yaml")
                    || normalizedFileName.endsWith(".yml")) {
                return parseText(inputStream);
            }
        }

        throw new IllegalArgumentException(
                "Unsupported content type: " + normalizedContentType + " for file: " + fileName
        );
    }

    /**
     * 根据 MIME 类型和文件扩展名判断文档是否为 PDF。
     *
     * @param normalizedContentType 规范化后的 MIME 类型
     * @param fileName 文件名
     * @return 应使用 PDF 解析器时返回 {@code true}
     */
    private boolean isPdf(String normalizedContentType, String fileName) {
        if (PDF_CONTENT_TYPE.equals(normalizedContentType)) {
            return true;
        }
        return (!StringUtils.hasText(normalizedContentType)
                || "application/octet-stream".equals(normalizedContentType))
                && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * 使用 UTF-8 读取普通文本类文档。
     *
     * @param inputStream 文档输入流
     * @return 单页形式的文本解析结果
     */
    private ParsedDocument parseText(InputStream inputStream) {
        Document document = new TextDocumentParser(StandardCharsets.UTF_8).parse(inputStream);
        return new ParsedDocument(document.text(), List.of(new DocumentPage(1, document.text())));
    }

    /**
     * PDFBox 默认一次性返回整份文本。这里逐页提取，以便把真实 pageNumber 写入向量 metadata。
     *
     * @param inputStream PDF 文件输入流
     * @return 包含完整文本及非空页面的解析结果
     * @throws UncheckedIOException 当 PDF 读取或解析失败时抛出
     */
    static ParsedDocument parsePdf(InputStream inputStream) {
        try (PDDocument pdf = Loader.loadPDF(inputStream.readAllBytes())) {
            List<DocumentPage> pages = new java.util.ArrayList<>(pdf.getNumberOfPages());
            StringBuilder fullText = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            // 优先遵循 PDF 内容流的逻辑顺序。按坐标排序会把双栏文档的左右两栏逐行交错，
            // 造成文字看似丢失或语句被打散；LibreOffice、Word 等导出的 PDF 通常保留逻辑顺序。
            stripper.setSortByPosition(false);

            for (int pageNumber = 1; pageNumber <= pdf.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String pageText = stripper.getText(pdf).strip();
                if (!StringUtils.hasText(pageText)) {
                    continue;
                }
                if (!fullText.isEmpty()) {
                    fullText.append("\n\n");
                }
                fullText.append(pageText);
                pages.add(new DocumentPage(pageNumber, pageText));
            }
            return new ParsedDocument(fullText.toString(), pages);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to parse PDF document", exception);
        }
    }

    /**
     * 移除 MIME 参数并统一为小写格式。
     *
     * @param contentType 原始 Content-Type，可以为空
     * @return 规范化后的 MIME 类型；输入为空时返回空字符串
     */
    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int parametersStart = contentType.indexOf(';');
        String mediaType = parametersStart >= 0
                ? contentType.substring(0, parametersStart)
                : contentType;
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Content-Type 不可靠时根据扩展名补全，确保解析器和切片器使用同一种格式策略。
     *
     * @param contentType 客户端或文件系统给出的 MIME 类型
     * @param fileName 文件名
     * @return 可用于解析器和切片器选择的规范化 MIME 类型
     */
    private String resolveContentType(String contentType, String fileName) {
        String normalized = normalizeContentType(contentType);
        if (StringUtils.hasText(normalized) && !"application/octet-stream".equals(normalized)) {
            return normalized;
        }

        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(".pdf")) {
            return PDF_CONTENT_TYPE;
        }
        if (lowerCaseName.endsWith(".md")) {
            return "text/markdown";
        }
        if (lowerCaseName.endsWith(".json")) {
            return "application/json";
        }
        if (lowerCaseName.endsWith(".xml")) {
            return "application/xml";
        }
        if (lowerCaseName.endsWith(".yaml") || lowerCaseName.endsWith(".yml")) {
            return "application/yaml";
        }
        if (lowerCaseName.endsWith(".txt")) {
            return "text/plain";
        }
        return normalized;
    }

    /**
     * 从来源路径中提取文件名。
     *
     * @param sourceFile 文件路径或来源文件名
     * @return 不包含父目录的文件名
     * @throws IllegalArgumentException 当来源为空时抛出
     */
    private String extractFileName(String sourceFile) {
        if (!StringUtils.hasText(sourceFile)) {
            throw new IllegalArgumentException("sourceFile must not be blank");
        }
        Path fileName = Path.of(sourceFile).getFileName();
        return fileName == null ? sourceFile : fileName.toString();
    }

    /**
     * 文档解析结果。
     *
     * @param fullText 用于文件表持久化的完整文本
     * @param pages 用于智能切片的分页文本
     */
    record ParsedDocument(String fullText, List<DocumentPage> pages) {
    }
}
