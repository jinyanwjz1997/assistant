package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.RagChunkingProperties;
import com.jinyan.assistant.entity.DocumentChunk;
import com.jinyan.assistant.entity.DocumentPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntelligentDocumentChunkerTest {

    private IntelligentDocumentChunker chunker;

    @BeforeEach
    void setUp() {
        RagChunkingProperties properties = new RagChunkingProperties();
        properties.setChildMaxCharacters(80);
        properties.setChildOverlapCharacters(15);
        properties.setMinChildCharacters(20);
        properties.setParentMaxCharacters(180);
        chunker = new IntelligentDocumentChunker(properties);
    }

    @Test
    void shouldPreserveMarkdownHeadingPathAndPageNumber() {
        String markdown = """
                # 用户手册

                这是手册的介绍内容。

                ## 退款规则

                用户需要在购买后七天内提交退款申请。数字商品不支持退款。
                """;

        List<DocumentChunk> chunks = chunker.split(
                "file-1",
                List.of(new DocumentPage(3, markdown)),
                "text/markdown"
        );

        DocumentChunk refundChunk = chunks.stream()
                .filter(chunk -> chunk.headingPath().contains("退款规则"))
                .findFirst()
                .orElseThrow();
        assertThat(refundChunk.heading()).isEqualTo("退款规则");
        assertThat(refundChunk.headingPath()).isEqualTo("用户手册 > 退款规则");
        assertThat(refundChunk.pageNumber()).isEqualTo(3);
        assertThat(refundChunk.parentText()).contains("七天内提交退款申请");
        assertThat(refundChunk.parentText()).doesNotContain("用户手册", "退款规则");
        assertThat(refundChunk.text()).doesNotStartWith("用户手册 > 退款规则");
    }

    @Test
    void shouldUseSlidingWindowForAnOversizedParagraph() {
        String longParagraph = "这是一个没有自然句子边界的长段落".repeat(15);

        List<DocumentChunk> chunks = chunker.split(
                "file-2",
                List.of(new DocumentPage(1, longParagraph)),
                "text/plain"
        );

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.text().length()).isLessThanOrEqualTo(80));
        assertThat(chunks.get(1).text())
                .startsWith(chunks.get(0).text().substring(chunks.get(0).text().length() - 15));
    }

    @Test
    void shouldMergeSmallParagraphsBeforeCreatingChildren() {
        String text = "第一段内容。\n\n第二段内容。\n\n第三段内容。";

        List<DocumentChunk> chunks = chunker.split(
                "file-3",
                List.of(new DocumentPage(1, text)),
                "text/plain"
        );

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("第一段内容", "第二段内容", "第三段内容");
    }

    @Test
    void shouldNotMergeParentSectionsAcrossPages() {
        List<DocumentChunk> chunks = chunker.split(
                "file-4",
                List.of(
                        new DocumentPage(1, "第一页正文内容。"),
                        new DocumentPage(2, "第二页正文内容。")
                ),
                "application/pdf"
        );

        assertThat(chunks).extracting(DocumentChunk::pageNumber).containsExactly(1, 2);
        assertThat(chunks).extracting(DocumentChunk::parentSectionId).doesNotHaveDuplicates();
    }

    @Test
    void shouldUseTopLevelJsonFieldsAsSections() {
        String json = """
                {
                  "refund": {"days": 7, "digitalGoods": false},
                  "shipping": {"company": "顺丰", "days": 3}
                }
                """;

        List<DocumentChunk> chunks = chunker.split(
                "file-5",
                List.of(new DocumentPage(1, json)),
                "application/json"
        );

        assertThat(chunks).extracting(DocumentChunk::headingPath)
                .containsExactly("refund", "shipping");
        assertThat(chunks.get(0).parentText()).contains("digitalGoods");
        assertThat(chunks.get(0).parentText()).doesNotContain("refund\n\n");
    }

    @Test
    void shouldUseXmlElementsAsSections() {
        String xml = """
                <manual>
                  <refund>购买后七天内可以申请退款。</refund>
                  <shipping>默认使用顺丰发货。</shipping>
                </manual>
                """;

        List<DocumentChunk> chunks = chunker.split(
                "file-6",
                List.of(new DocumentPage(1, xml)),
                "application/xml"
        );

        assertThat(chunks).extracting(DocumentChunk::headingPath)
                .containsExactly("manual > refund", "manual > shipping");
    }

    @Test
    void shouldNotTreatNumericSentenceAsHeadingOrRepeatItInEveryChunk() {
        String profile = """
                7 年 Java 后端开发经验，长期参与政务行业核心业务系统研发，具备复杂业务建模和性能优化经验

                熟悉 Spring Cloud、Redis 和 Kafka，能够完成复杂系统的设计与落地。
                负责多个核心项目的接口设计、数据治理和线上稳定性保障。
                """;

        List<DocumentChunk> chunks = chunker.split(
                "resume-file",
                List.of(new DocumentPage(1, profile)),
                "application/pdf"
        );

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.heading()).isEmpty();
            assertThat(chunk.headingPath()).isEmpty();
        });
        assertThat(chunks.stream()
                .filter(chunk -> chunk.text().startsWith("7 年 Java 后端开发经验"))
                .count()).isEqualTo(1);
    }

    @Test
    void shouldCoverEverySourceCharacterAcrossSlidingWindowChunks() {
        StringBuilder source = new StringBuilder("第一段包含完整的业务背景和处理规则。");
        for (int index = 0; index < 12; index++) {
            source.append("第")
                    .append(index)
                    .append("条规则继续说明数据校验、状态流转和异常处理。");
        }
        source.append("最后一段给出归档条件和审计要求。");
        String text = source.toString();

        List<DocumentChunk> chunks = chunker.split(
                "coverage-file",
                List.of(new DocumentPage(1, text)),
                "text/plain"
        );

        String normalizedSource = withoutWhitespace(text);
        boolean[] covered = new boolean[normalizedSource.length()];
        int searchFrom = 0;
        for (DocumentChunk chunk : chunks) {
            String normalizedChunk = withoutWhitespace(chunk.text());
            int position = normalizedSource.indexOf(
                    normalizedChunk,
                    Math.max(0, searchFrom - 80)
            );
            assertThat(position).isGreaterThanOrEqualTo(0);
            for (int index = position; index < position + normalizedChunk.length(); index++) {
                covered[index] = true;
            }
            searchFrom = Math.max(searchFrom, position + normalizedChunk.length());
        }

        assertThat(covered).doesNotContain(false);
    }

    private String withoutWhitespace(String value) {
        return value.replaceAll("\\s+", "");
    }
}
