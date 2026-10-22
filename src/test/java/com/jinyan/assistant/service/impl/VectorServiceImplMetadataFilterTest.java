package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.entity.KnowledgeMetadataFilter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VectorServiceImplMetadataFilterTest {

    @Test
    void shouldAddCombinedMetadataFilterToEmbeddingSearchRequest() {
        CapturingEmbeddingStore embeddingStore = new CapturingEmbeddingStore();
        VectorServiceImpl service = serviceUsing(embeddingStore);
        KnowledgeMetadataFilter filter = new KnowledgeMetadataFilter(
                List.of(" file-1 ", "file-2", "file-1"),
                List.of("Application/PDF; charset=UTF-8"),
                "退款规则",
                2,
                5
        );

        service.searchRelevantContext("如何退款", 3, filter);

        EmbeddingSearchRequest request = embeddingStore.lastSearchRequest;
        assertThat(request).isNotNull();
        assertThat(request.maxResults()).isEqualTo(9);
        assertThat(request.filter()).isNotNull();
        assertThat(filter.fileIds()).containsExactly("file-1", "file-2");
        assertThat(filter.contentTypes()).containsExactly("application/pdf");

        Metadata matching = metadata("file-2", "application/pdf", "售后 > 退款规则", 3);
        assertThat(request.filter().test(matching)).isTrue();
        assertThat(request.filter().test(
                metadata("other-file", "application/pdf", "售后 > 退款规则", 3)
        )).isFalse();
        assertThat(request.filter().test(
                metadata("file-1", "text/plain", "售后 > 退款规则", 3)
        )).isFalse();
        assertThat(request.filter().test(
                metadata("file-1", "application/pdf", "商品介绍", 3)
        )).isFalse();
        assertThat(request.filter().test(
                metadata("file-1", "application/pdf", "售后 > 退款规则", 6)
        )).isFalse();
    }

    @Test
    void shouldNotSetFilterWhenRequestContainsNoEffectiveCondition() {
        CapturingEmbeddingStore embeddingStore = new CapturingEmbeddingStore();
        VectorServiceImpl service = serviceUsing(embeddingStore);

        service.searchRelevantContext(
                "查询内容",
                2,
                new KnowledgeMetadataFilter(List.of(" "), null, " ", null, null)
        );

        assertThat(embeddingStore.lastSearchRequest.filter()).isNull();
    }

    @Test
    void shouldRejectInvalidPageRange() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new KnowledgeMetadataFilter(List.of(), List.of(), null, 6, 5)
        ).withMessage("minPageNumber must not be greater than maxPageNumber");
    }

    /**
     * 创建只包含向量查询依赖的服务实例。
     *
     * @param embeddingStore 用于捕获查询请求的向量存储
     * @return 向量服务
     */
    private VectorServiceImpl serviceUsing(CapturingEmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                return Response.from(Embedding.from(new float[]{0.1f, 0.2f}));
            }
        };
        return new VectorServiceImpl(embeddingModel, embeddingStore, null, null, null);
    }

    /**
     * 创建用于执行过滤表达式断言的元数据。
     *
     * @param fileId 文件 ID
     * @param contentType MIME 类型
     * @param headingPath 标题路径
     * @param pageNumber 页码
     * @return 文档元数据
     */
    private Metadata metadata(
            String fileId,
            String contentType,
            String headingPath,
            int pageNumber
    ) {
        return new Metadata()
                .put("fileId", fileId)
                .put("contentType", contentType)
                .put("headingPath", headingPath)
                .put("pageNumber", pageNumber);
    }

    /** 记录最后一次向量查询请求，避免单元测试依赖数据库。 */
    private static final class CapturingEmbeddingStore implements EmbeddingStore<TextSegment> {

        private EmbeddingSearchRequest lastSearchRequest;

        @Override
        public String add(Embedding embedding) {
            throw new UnsupportedOperationException("Not needed by this test");
        }

        @Override
        public void add(String id, Embedding embedding) {
            throw new UnsupportedOperationException("Not needed by this test");
        }

        @Override
        public String add(Embedding embedding, TextSegment embedded) {
            throw new UnsupportedOperationException("Not needed by this test");
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            throw new UnsupportedOperationException("Not needed by this test");
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            lastSearchRequest = request;
            return new EmbeddingSearchResult<>(List.of());
        }
    }
}
