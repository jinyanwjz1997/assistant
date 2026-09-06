package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.service.VectorService;
import com.jinyan.assistant.entity.FileInfo;
import com.jinyan.assistant.entity.RagSegmentMessage;
import com.jinyan.assistant.service.FileService;
import com.jinyan.assistant.service.RedisStreamRagSegmentQueue;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * 通过构造器注入，embeddingStore 是 PgVectorStoreConfig 里声明的单例 Bean，
     * 不用在每个方法里重复 new。
     */
    public VectorServiceImpl(EmbeddingModel embeddingModel,
                             @Qualifier("knowledgeEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore,
                             FileService fileService,
                             RedisStreamRagSegmentQueue segmentQueue) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.fileService = fileService;
        this.segmentQueue = segmentQueue;
    }

    @Override
    public void addTextToVector(String context, String sourceFile) {
        String fileName = extractFileName(sourceFile);
        FileInfo fileInfo = fileService.saveFile(fileName, context);

        Metadata metadata = Metadata.from("source", sourceFile)
                .put("fileId", fileInfo.getFileId())
                .put("documentId", fileInfo.getFileId());
        Document document = Document.from(context, metadata);
        enqueueSegments(document, fileInfo.getFileId(), sourceFile, "text/plain", 300, 30);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> searchRelevantContext(String userQuery, int maxResults) {
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(userQuery).content())
                .maxResults(maxResults)
                .minScore(0.7)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);
        return result.matches();
    }

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

    @Override
    public String readDocument(String fileName, String contentType, InputStream inputStream) {
        String safeFileName = StringUtils.cleanPath(
                StringUtils.hasText(fileName) ? fileName : "uploaded-file"
        );
        return readDocument(safeFileName, contentType, inputStream, safeFileName);
    }

    private String readDocument(String fileName,
                                String contentType,
                                InputStream inputStream,
                                String source) {
        DocumentParser parser = selectParser(contentType, fileName);
        Document loadedDocument = parser.parse(inputStream);
        if (!StringUtils.hasText(loadedDocument.text())) {
            throw new IllegalArgumentException(
                    "No text could be extracted from file: " + fileName
                            + ". Scanned PDF files require OCR."
            );
        }

        FileInfo fileInfo = fileService.saveFile(fileName, loadedDocument.text());
        Metadata metadata = Metadata.from("source", source)
                .put("contentType", normalizeContentType(contentType))
                .put("fileId", fileInfo.getFileId())
                .put("documentId", fileInfo.getFileId());
        String normalizedContentType = normalizeContentType(contentType);
        Document document = Document.from(loadedDocument.text(), metadata);
        enqueueSegments(document, fileInfo.getFileId(), source, normalizedContentType, 500, 100);
        log.info("文档已解析并进入RAG异步队列, fileName:{}, contentType:{}, fileId:{}",
                fileName, contentType, fileInfo.getFileId());
        return fileInfo.getFileId();
    }

    private void enqueueSegments(Document document,
                                 String fileId,
                                 String source,
                                 String contentType,
                                 int maxSegmentSize,
                                 int overlapSize) {
        List<TextSegment> segments = DocumentSplitters
                .recursive(maxSegmentSize, overlapSize)
                .split(document);
        List<RagSegmentMessage> messages = new java.util.ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            String segmentId = UUID.nameUUIDFromBytes(
                    (fileId + ":" + index).getBytes(StandardCharsets.UTF_8)
            ).toString();
            messages.add(new RagSegmentMessage(
                    segmentId,
                    fileId,
                    source,
                    contentType,
                    index,
                    segments.size(),
                    segments.get(index).text(),
                    0
            ));
        }
        segmentQueue.enqueue(fileId, messages);
    }

    private DocumentParser selectParser(String contentType, String fileName) {
        String normalizedContentType = normalizeContentType(contentType);
        if (PDF_CONTENT_TYPE.equals(normalizedContentType)) {
            return new ApachePdfBoxDocumentParser();
        }
        if (normalizedContentType.startsWith("text/")
                || TEXT_APPLICATION_TYPES.contains(normalizedContentType)) {
            return new TextDocumentParser(StandardCharsets.UTF_8);
        }

        String normalizedFileName = fileName.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedContentType)
                || "application/octet-stream".equals(normalizedContentType)) {
            if (normalizedFileName.endsWith(".pdf")) {
                return new ApachePdfBoxDocumentParser();
            }
            if (normalizedFileName.endsWith(".txt")
                    || normalizedFileName.endsWith(".md")
                    || normalizedFileName.endsWith(".json")
                    || normalizedFileName.endsWith(".xml")
                    || normalizedFileName.endsWith(".yaml")
                    || normalizedFileName.endsWith(".yml")) {
                return new TextDocumentParser(StandardCharsets.UTF_8);
            }
        }

        throw new IllegalArgumentException(
                "Unsupported content type: " + normalizedContentType + " for file: " + fileName
        );
    }

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

    private String extractFileName(String sourceFile) {
        if (!StringUtils.hasText(sourceFile)) {
            throw new IllegalArgumentException("sourceFile must not be blank");
        }
        Path fileName = Path.of(sourceFile).getFileName();
        return fileName == null ? sourceFile : fileName.toString();
    }
}
