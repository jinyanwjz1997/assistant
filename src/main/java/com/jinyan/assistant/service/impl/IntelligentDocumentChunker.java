package com.jinyan.assistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinyan.assistant.config.RagChunkingProperties;
import com.jinyan.assistant.entity.DocumentChunk;
import com.jinyan.assistant.entity.DocumentPage;
import com.jinyan.assistant.service.DocumentChunker;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构感知文档切片器。
 *
 * <p>切片顺序为：页 -> 标题章节 -> 段落合并 -> 句子边界 -> 滑动窗口。标题和页码用于
 * 形成父章节 metadata，真正参与向量检索的是尺寸较小且带重叠的子片段。
 */
@Service
public class IntelligentDocumentChunker implements DocumentChunker {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "^第[一二三四五六七八九十百千0-9]+[章节篇部]\\s*.*$"
    );
    private static final Pattern CHINESE_NUMBER_HEADING = Pattern.compile(
            "^[一二三四五六七八九十百]+[、．.]\\s*.+$"
    );
    private static final Pattern BRACKET_HEADING = Pattern.compile(
            "^[（(][一二三四五六七八九十百0-9]+[）)]\\s*.+$"
    );
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
            "^(\\d+)[、．.]\\s*(.+)$"
    );
    private static final Pattern DECIMAL_HEADING = Pattern.compile(
            "^(\\d+(?:\\.\\d+){1,5})\\s+(.+)$"
    );
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
            "(?<=[。！？!?；;])|(?<=[.])\\s+|\\n+"
    );

    private final RagChunkingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建结构感知文档切片器。
     *
     * @param properties RAG 切片大小、重叠长度等配置
     */
    public IntelligentDocumentChunker(RagChunkingProperties properties) {
        this.properties = properties;
    }

    /**
     * 将按页解析的文档转换为用于向量检索的父子片段。
     *
     * @param fileId 文档 ID，用于生成稳定的父章节 ID
     * @param pages 按原文顺序排列的页面
     * @param contentType 文档 MIME 类型，用于选择结构解析策略
     * @return 按原文顺序排列的子片段；页面为空时返回空列表
     * @throws IllegalArgumentException 当 {@code fileId} 为空时抛出
     * @throws IllegalStateException 当切片参数不合法时抛出
     */
    @Override
    public List<DocumentChunk> split(String fileId, List<DocumentPage> pages, String contentType) {
        validateConfiguration();
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("fileId must not be blank");
        }
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        List<ParentSection> parents = buildParentSections(fileId, pages, contentType);
        List<DocumentChunk> chunks = new ArrayList<>();
        for (ParentSection parent : parents) {
            List<String> childTexts = splitText(
                    parent.body(),
                    properties.getChildMaxCharacters(),
                    properties.getChildOverlapCharacters(),
                    properties.getMinChildCharacters()
            );
            for (String childText : childTexts) {
                chunks.add(new DocumentChunk(
                        childText,
                        parent.body(),
                        parent.id(),
                        parent.heading(),
                        parent.headingPath(),
                        parent.pageNumber()
                ));
            }
        }
        return chunks;
    }

    /**
     * 标题是强边界，页边界是保守边界。每个标题章节如果仍然过长，会优先按句子拆成多个
     * 父章节，避免把整份长文重复写入每个子片段的 metadata。
     *
     * @param fileId 文档 ID，用于生成父章节稳定 ID
     * @param pages 待处理的页面列表
     * @param contentType 文档 MIME 类型
     * @return 按页面和标题顺序排列的父章节
     */
    private List<ParentSection> buildParentSections(
            String fileId,
            List<DocumentPage> pages,
            String contentType
    ) {
        List<ParentSection> parents = new ArrayList<>();
        Map<Integer, String> headingLevels = new LinkedHashMap<>();
        int parentIndex = 0;

        for (DocumentPage page : pages) {
            SectionSeed current = new SectionSeed(
                    page.pageNumber(),
                    currentHeading(headingLevels),
                    headingPath(headingLevels)
            );

            for (Block block : parseBlocks(page.text(), contentType)) {
                if (block.heading()) {
                    parentIndex = flushSeed(fileId, current, parentIndex, parents);
                    updateHeadingPath(headingLevels, block.level(), block.text());
                    current = new SectionSeed(
                            page.pageNumber(),
                            block.text(),
                            headingPath(headingLevels)
                    );
                } else {
                    current.addParagraph(block.text());
                }
            }
            // 不跨页合并父章节，确保 pageNumber 始终能够准确指向命中内容。
            flushSeed(fileId, current, parentIndex, parents);
            parentIndex = parents.size();
        }
        return parents;
    }

    /**
     * 将当前章节缓冲区切分为一个或多个父章节并写入结果集合。
     *
     * @param fileId 文档 ID
     * @param seed 当前页面中的章节缓冲区
     * @param parentIndex 下一个父章节序号
     * @param target 父章节结果集合
     * @return 写入完成后的下一个父章节序号
     */
    private int flushSeed(
            String fileId,
            SectionSeed seed,
            int parentIndex,
            List<ParentSection> target
    ) {
        if (seed == null || !StringUtils.hasText(seed.body())) {
            return parentIndex;
        }

        List<String> parentBodies = splitText(
                seed.body(),
                properties.getParentMaxCharacters(),
                0,
                1
        );
        for (String parentBody : parentBodies) {
            String parentId = UUID.nameUUIDFromBytes(
                    (fileId + ":parent:" + parentIndex).getBytes(StandardCharsets.UTF_8)
            ).toString();
            target.add(new ParentSection(
                    parentId,
                    parentBody,
                    seed.heading(),
                    seed.headingPath(),
                    seed.pageNumber()
            ));
            parentIndex++;
        }
        return parentIndex;
    }

    /**
     * 根据内容类型选择结构解析方式，解析失败时退回通用段落规则。
     *
     * @param pageText 当前页面的原始文本
     * @param contentType 文档 MIME 类型
     * @return 当前页面解析出的标题块和正文块
     */
    private List<Block> parseBlocks(String pageText, String contentType) {
        if (!StringUtils.hasText(pageText)) {
            return List.of();
        }

        try {
            if ("application/json".equals(contentType)) {
                return parseJsonBlocks(pageText);
            }
            if ("application/xml".equals(contentType) || "text/xml".equals(contentType)) {
                return parseXmlBlocks(pageText);
            }
            if ("application/yaml".equals(contentType)
                    || "application/x-yaml".equals(contentType)
                    || "text/yaml".equals(contentType)) {
                return parseYamlBlocks(pageText);
            }
        } catch (Exception ignored) {
            // 结构化内容不完整时仍允许使用通用文本切片，不让单个格式问题阻断入库。
        }
        return parsePlainTextBlocks(pageText);
    }

    /**
     * 将普通文本和 Markdown 解析为标题块和正文段落块。
     *
     * @param pageText 当前页面文本
     * @return 标题块与正文块列表
     */
    private List<Block> parsePlainTextBlocks(String pageText) {

        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String rawLine : normalize(pageText).split("\\n", -1)) {
            String line = rawLine.strip();
            if (!StringUtils.hasText(line)) {
                flushParagraph(paragraph, blocks);
                continue;
            }

            Heading heading = detectHeading(line);
            if (heading != null) {
                flushParagraph(paragraph, blocks);
                blocks.add(new Block(heading.text(), true, heading.level()));
                continue;
            }

            // PDF 经常每一视觉行都有换行。这里把连续行合并，只有空行或标题才结束段落。
            if (!paragraph.isEmpty()) {
                paragraph.append(shouldJoinWithoutSpace(paragraph, line) ? "" : " ");
            }
            paragraph.append(line);
        }
        flushParagraph(paragraph, blocks);
        return blocks;
    }

    /**
     * 将 JSON 顶层字段或数组元素转换为章节，避免从对象内部直接切开。
     *
     * @param text JSON 原文
     * @return 根据顶层结构生成的标题块和正文块
     * @throws Exception 当 JSON 无法解析时抛出，由上层回退到普通文本策略
     */
    private List<Block> parseJsonBlocks(String text) throws Exception {
        JsonNode root = objectMapper.readTree(text);
        List<Block> blocks = new ArrayList<>();
        if (root.isObject()) {
            root.properties().forEach(field -> {
                blocks.add(new Block(field.getKey(), true, 1));
                blocks.add(new Block(field.getValue().toPrettyString(), false, 0));
            });
        } else if (root.isArray()) {
            for (int index = 0; index < root.size(); index++) {
                blocks.add(new Block("数组元素 " + index, true, 1));
                blocks.add(new Block(root.get(index).toPrettyString(), false, 0));
            }
        } else {
            blocks.add(new Block(root.toPrettyString(), false, 0));
        }
        return blocks;
    }

    /**
     * 将 XML 根节点和一级元素转换为章节，并禁用外部实体以避免 XXE。
     *
     * @param text XML 原文
     * @return 根据 XML 节点生成的标题块和正文块
     * @throws Exception 当 XML 无法安全解析时抛出，由上层回退到普通文本策略
     */
    private List<Block> parseXmlBlocks(String text) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Element root = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(text)))
                .getDocumentElement();
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block(root.getTagName(), true, 1));

        NodeList children = root.getChildNodes();
        boolean hasElementChild = false;
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            hasElementChild = true;
            Element element = (Element) node;
            blocks.add(new Block(element.getTagName(), true, 2));
            String value = element.getTextContent().strip();
            if (StringUtils.hasText(value)) {
                blocks.add(new Block(value, false, 0));
            }
        }
        if (!hasElementChild && StringUtils.hasText(root.getTextContent())) {
            blocks.add(new Block(root.getTextContent().strip(), false, 0));
        }
        return blocks;
    }

    /**
     * 使用 YAML 缩进层级和无值键识别章节，其余行保持为正文。
     *
     * @param text YAML 原文
     * @return 根据键和缩进生成的标题块与正文块
     */
    private List<Block> parseYamlBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        Pattern headingPattern = Pattern.compile("^(\\s*)([^#][^:]{0,100}):\\s*$");

        for (String rawLine : normalize(text).split("\\n", -1)) {
            Matcher matcher = headingPattern.matcher(rawLine);
            if (matcher.matches()) {
                flushParagraph(paragraph, blocks);
                int level = Math.min(6, matcher.group(1).length() / 2 + 1);
                blocks.add(new Block(matcher.group(2).strip(), true, level));
                continue;
            }
            if (!StringUtils.hasText(rawLine)) {
                flushParagraph(paragraph, blocks);
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append('\n');
            }
            paragraph.append(rawLine.stripTrailing());
        }
        flushParagraph(paragraph, blocks);
        return blocks;
    }

    /**
     * 将非空段落缓冲区写入块列表，并清空缓冲区。
     *
     * @param paragraph 当前段落缓冲区
     * @param blocks 接收正文块的结果列表
     */
    private void flushParagraph(StringBuilder paragraph, List<Block> blocks) {
        String text = paragraph.toString().strip();
        if (StringUtils.hasText(text)) {
            blocks.add(new Block(text, false, 0));
        }
        paragraph.setLength(0);
    }

    /**
     * 根据 Markdown 和常见中英文编号格式识别标题及其层级。
     *
     * @param line 去除首尾空白后的单行文本
     * @return 识别出的标题；该行不是标题时返回 {@code null}
     */
    private Heading detectHeading(String line) {
        if (line.length() > 100 || endsLikeSentence(line)) {
            return null;
        }

        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) {
            return new Heading(markdown.group(2).strip(), markdown.group(1).length());
        }
        if (CHAPTER_HEADING.matcher(line).matches()) {
            return new Heading(line, 1);
        }
        if (CHINESE_NUMBER_HEADING.matcher(line).matches()) {
            return new Heading(line, 2);
        }
        if (BRACKET_HEADING.matcher(line).matches()) {
            return new Heading(line, 3);
        }

        Matcher numbered = NUMBERED_HEADING.matcher(line);
        if (numbered.matches()) {
            return new Heading(line, 1);
        }

        Matcher decimal = DECIMAL_HEADING.matcher(line);
        if (decimal.matches()) {
            int level = Math.min(6, decimal.group(1).split("\\.").length);
            return new Heading(line, level);
        }
        return null;
    }

    /**
     * 先按自然句子边界聚合；单句过长或无法识别边界时，再按字符滑动窗口兜底。
     *
     * @param text 待切分文本
     * @param maxCharacters 每个片段允许的最大字符数
     * @param overlap 相邻片段期望保留的重叠字符数
     * @param minCharacters 尾片段期望达到的最小字符数
     * @return 按原文顺序排列的文本片段
     */
    private List<String> splitText(String text, int maxCharacters, int overlap, int minCharacters) {
        String normalized = normalize(text).strip();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        if (normalized.length() <= maxCharacters) {
            return List.of(normalized);
        }

        List<String> units = new ArrayList<>();
        for (String sentence : SENTENCE_BOUNDARY.split(normalized)) {
            String value = sentence.strip();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (value.length() <= maxCharacters) {
                units.add(value);
            } else {
                units.addAll(hardWindows(value, maxCharacters, overlap));
            }
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (current.isEmpty()) {
                current.append(unit);
                continue;
            }
            if (current.length() + 1 + unit.length() <= maxCharacters) {
                current.append(separatorBetween(current, unit)).append(unit);
                continue;
            }

            String completed = current.toString().strip();
            result.add(completed);
            current.setLength(0);

            int availableOverlap = Math.max(0, maxCharacters - unit.length() - 1);
            String overlapText = tail(completed, Math.min(overlap, availableOverlap));
            if (StringUtils.hasText(overlapText)) {
                current.append(overlapText).append(separatorBetween(current, unit));
            }
            current.append(unit);
        }
        if (!current.isEmpty()) {
            result.add(current.toString().strip());
        }

        mergeSmallTail(result, maxCharacters, minCharacters);
        return result;
    }

    /**
     * 对无法按句子拆分的长文本执行固定字符滑动窗口切分。
     *
     * @param text 待切分的长文本
     * @param maxCharacters 窗口最大字符数
     * @param overlap 相邻窗口重叠字符数
     * @return 按顺序生成的窗口文本
     */
    private List<String> hardWindows(String text, int maxCharacters, int overlap) {
        List<String> windows = new ArrayList<>();
        int step = maxCharacters - overlap;
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(text.length(), start + maxCharacters);
            windows.add(text.substring(start, end).strip());
            if (end == text.length()) {
                break;
            }
        }
        return windows;
    }

    /**
     * 在不超过最大长度的前提下，将过短的尾片段合并到前一个片段。
     *
     * @param chunks 待调整的可变片段列表
     * @param maxCharacters 片段最大字符数
     * @param minCharacters 尾片段最小字符数
     */
    private void mergeSmallTail(List<String> chunks, int maxCharacters, int minCharacters) {
        if (chunks.size() < 2) {
            return;
        }
        int lastIndex = chunks.size() - 1;
        String tail = chunks.get(lastIndex);
        String previous = chunks.get(lastIndex - 1);
        if (tail.length() < minCharacters
                && previous.length() + 1 + tail.length() <= maxCharacters) {
            chunks.set(lastIndex - 1, previous + separatorBetween(previous, tail) + tail);
            chunks.remove(lastIndex);
        }
    }

    /**
     * 更新当前标题路径，并移除同级及更深层级的旧标题。
     *
     * @param headings 标题层级到标题文本的有序映射
     * @param level 新标题层级
     * @param text 新标题文本
     */
    private void updateHeadingPath(Map<Integer, String> headings, int level, String text) {
        headings.keySet().removeIf(existingLevel -> existingLevel >= level);
        headings.put(level, text);
    }

    /**
     * 将当前标题层级拼接为可读路径。
     *
     * @param headings 标题层级到标题文本的有序映射
     * @return 使用 {@code >} 分隔的完整标题路径
     */
    private String headingPath(Map<Integer, String> headings) {
        return String.join(" > ", headings.values());
    }

    /**
     * 获取当前标题路径中层级最深的标题。
     *
     * @param headings 标题层级到标题文本的有序映射
     * @return 当前标题；没有标题时返回空字符串
     */
    private String currentHeading(Map<Integer, String> headings) {
        String current = "";
        for (String heading : headings.values()) {
            current = heading;
        }
        return current;
    }

    /**
     * 将不同平台的换行符统一为 {@code \n}。
     *
     * @param text 原始文本，可以为 {@code null}
     * @return 标准化后的文本；输入为 {@code null} 时返回空字符串
     */
    private String normalize(String text) {
        return text == null
                ? ""
                : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * 判断一行是否以常见句末或停顿标点结束。
     *
     * @param line 待判断文本行
     * @return 是完整语句时返回 {@code true}
     */
    private boolean endsLikeSentence(String line) {
        return line.matches(".*[。！？!?；;，,：:]$");
    }

    /**
     * 判断连续视觉行合并时是否应省略空格。
     *
     * @param previous 已累积的前文
     * @param current 当前文本行
     * @return 任一边界字符属于中日韩文字时返回 {@code true}
     */
    private boolean shouldJoinWithoutSpace(StringBuilder previous, String current) {
        if (previous.isEmpty() || current.isEmpty()) {
            return false;
        }
        char left = previous.charAt(previous.length() - 1);
        char right = current.charAt(0);
        return isCjk(left) || isCjk(right);
    }

    /**
     * 判断字符是否属于中日韩文字脚本。
     *
     * @param value 待判断字符
     * @return 属于汉字、平假名或片假名时返回 {@code true}
     */
    private boolean isCjk(char value) {
        Character.UnicodeScript script = Character.UnicodeScript.of(value);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }

    /**
     * 根据左右文本边界选择拼接分隔符，避免中文被插入多余空格。
     *
     * @param left 左侧文本
     * @param right 右侧文本
     * @return 应插入的空字符串或单个空格
     */
    private String separatorBetween(CharSequence left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return "";
        }
        char leftChar = left.charAt(left.length() - 1);
        char rightChar = right.charAt(0);
        if (leftChar == '\n' || Character.isWhitespace(leftChar)) {
            return "";
        }
        return isCjk(leftChar) || isCjk(rightChar) ? "" : " ";
    }

    /**
     * 截取文本尾部，用作下一个滑动窗口的重叠上下文。
     *
     * @param value 来源文本
     * @param maxCharacters 最多保留的字符数
     * @return 去除首尾空白后的尾部文本
     */
    private String tail(String value, int maxCharacters) {
        if (maxCharacters <= 0 || !StringUtils.hasText(value)) {
            return "";
        }
        return value.substring(Math.max(0, value.length() - maxCharacters)).strip();
    }

    /**
     * 校验父子片段大小及重叠参数之间的约束。
     *
     * @throws IllegalStateException 当任一切片参数不满足约束时抛出
     */
    private void validateConfiguration() {
        int max = properties.getChildMaxCharacters();
        int overlap = properties.getChildOverlapCharacters();
        int min = properties.getMinChildCharacters();
        int parentMax = properties.getParentMaxCharacters();
        if (max < 1 || parentMax < max) {
            throw new IllegalStateException(
                    "RAG chunking requires parentMaxCharacters >= childMaxCharacters > 0"
            );
        }
        if (overlap < 0 || overlap >= max) {
            throw new IllegalStateException(
                    "childOverlapCharacters must be between 0 and childMaxCharacters"
            );
        }
        if (min < 1 || min > max) {
            throw new IllegalStateException(
                    "minChildCharacters must be between 1 and childMaxCharacters"
            );
        }
    }

    /**
     * 标题识别结果。
     *
     * @param text 标题文本
     * @param level 标题层级，数值越小层级越高
     */
    private record Heading(String text, int level) {
    }

    /**
     * 结构解析后的最小内容块。
     *
     * @param text 内容块文本
     * @param heading 是否为标题块
     * @param level 标题层级；正文块使用 0
     */
    private record Block(String text, boolean heading, int level) {
    }

    /**
     * 已完成的父章节。
     *
     * @param id 父章节稳定 ID
     * @param body 父章节正文
     * @param heading 当前标题
     * @param headingPath 完整标题路径
     * @param pageNumber 父章节所在页码
     */
    private record ParentSection(
            String id,
            String body,
            String heading,
            String headingPath,
            int pageNumber
    ) {
    }

    /** 用于累积单个页面内当前标题章节正文的可变缓冲区。 */
    private static final class SectionSeed {

        private final int pageNumber;
        private final String heading;
        private final String headingPath;
        private final StringBuilder body = new StringBuilder();

        /**
         * 创建章节缓冲区。
         *
         * @param pageNumber 当前页码
         * @param heading 当前标题
         * @param headingPath 完整标题路径
         */
        private SectionSeed(int pageNumber, String heading, String headingPath) {
            this.pageNumber = pageNumber;
            this.heading = heading;
            this.headingPath = headingPath;
        }

        /**
         * 将正文段落追加到当前章节，段落之间保留一个空行。
         *
         * @param paragraph 待追加的正文段落
         */
        private void addParagraph(String paragraph) {
            if (!body.isEmpty()) {
                body.append("\n\n");
            }
            body.append(paragraph);
        }

        /**
         * 获取当前章节页码。
         *
         * @return 页码
         */
        private int pageNumber() {
            return pageNumber;
        }

        /**
         * 获取当前章节标题。
         *
         * @return 当前标题
         */
        private String heading() {
            return heading;
        }

        /**
         * 获取当前章节完整标题路径。
         *
         * @return 完整标题路径
         */
        private String headingPath() {
            return headingPath;
        }

        /**
         * 获取当前累计的章节正文。
         *
         * @return 章节正文
         */
        private String body() {
            return body.toString();
        }
    }
}
