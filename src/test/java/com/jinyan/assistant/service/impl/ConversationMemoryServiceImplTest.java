package com.jinyan.assistant.service.impl;

import com.jinyan.assistant.config.MemoryProperties;
import com.jinyan.assistant.entity.MemoryContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryServiceImplTest {

    @Test
    void shouldEvictOldMessagesFromShortTermWindow() {
        MemoryProperties properties = properties(2, false);
        ConversationMemoryServiceImpl service = new ConversationMemoryServiceImpl(
                fixedEmbeddingModel(),
                new RecordingEmbeddingStore(),
                properties
        );

        service.remember("conversation-1", "问题一", "答案一");
        service.remember("conversation-1", "问题二", "答案二");

        assertThat(service.shortTermMemory("conversation-1").messages())
                .containsExactly("用户：问题二", "助手：答案二");
        assertThat(service.shortTermMemory("conversation-2").messages()).isEmpty();
    }

    @Test
    void shouldStoreConversationMetadataAndFilterLongTermSearch() {
        MemoryProperties properties = properties(10, true);
        RecordingEmbeddingStore store = new RecordingEmbeddingStore();
        ConversationMemoryServiceImpl service = new ConversationMemoryServiceImpl(
                fixedEmbeddingModel(),
                store,
                properties
        );

        service.remember("conversation-1", "我偏好简洁回答", "后续使用简洁格式。");

        assertThat(store.storedSegment.metadata().getString("memoryType"))
                .isEqualTo("conversation_memory");
        assertThat(store.storedSegment.metadata().getString("conversationId"))
                .isEqualTo("conversation-1");
        assertThat(store.storedSegment.metadata().getString("question"))
                .isEqualTo("我偏好简洁回答");
        assertThat(store.storedSegment.metadata().getString("answer"))
                .isEqualTo("后续使用简洁格式。");

        List<MemoryContext> memories = service.searchLongTermMemory(
                "conversation-1",
                "回答格式",
                5
        );

        assertThat(store.lastSearchRequest).isNotNull();
        assertThat(store.lastSearchRequest.maxResults()).isEqualTo(3);
        assertThat(store.lastSearchRequest.filter()).isNotNull();
        assertThat(memories).singleElement()
                .extracting(MemoryContext::conversationId)
                .isEqualTo("conversation-1");
    }

    /**
     * 创建测试用记忆配置。
     *
     * @param shortTermMaxMessages 短期窗口消息数
     * @param longTermEnabled 是否启用长期记忆
     * @return 测试配置
     */
    private MemoryProperties properties(int shortTermMaxMessages, boolean longTermEnabled) {
        MemoryProperties properties = new MemoryProperties();
        properties.setShortTermMaxMessages(shortTermMaxMessages);
        properties.setLongTermEnabled(longTermEnabled);
        return properties;
    }

    /**
     * 创建固定二维向量模型，避免测试依赖本地 Ollama。
     *
     * @return 测试向量模型
     */
    private EmbeddingModel fixedEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                return Response.from(Embedding.from(new float[]{1.0f, 0.0f}));
            }
        };
    }

    /** 记录长期记忆写入和查询参数的测试向量库。 */
    private static final class RecordingEmbeddingStore implements EmbeddingStore<TextSegment> {

        private TextSegment storedSegment;
        private EmbeddingSearchRequest lastSearchRequest;

        @Override
        public String add(Embedding embedding) {
            return "memory-1";
        }

        @Override
        public void add(String id, Embedding embedding) {
            // 该测试只验证带 TextSegment 的批量写入。
        }

        @Override
        public String add(Embedding embedding, TextSegment embedded) {
            storedSegment = embedded;
            return "memory-1";
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            return List.of("memory-1");
        }

        @Override
        public void addAll(
                List<String> ids,
                List<Embedding> embeddings,
                List<TextSegment> embedded
        ) {
            storedSegment = embedded.get(0);
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            lastSearchRequest = request;
            return new EmbeddingSearchResult<>(List.of(
                    new EmbeddingMatch<>(
                            0.91,
                            "memory-1",
                            Embedding.from(new float[]{1.0f, 0.0f}),
                            storedSegment
                    )
            ));
        }
    }
}
