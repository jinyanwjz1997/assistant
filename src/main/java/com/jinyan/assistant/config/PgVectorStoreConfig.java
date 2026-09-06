package com.jinyan.assistant.config;

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * PGvector 向量库配置：声明单例 {@link PgVectorEmbeddingStore}。
 *
 * <p>复用 Spring 的 {@link DataSource}（来自 spring.datasource，HikariCP 连接池），
 * 只建一个连接池、一个 store 实例，供所有 service 注入复用。
 */
@Configuration
public class PgVectorStoreConfig {

    @Bean("knowledgeEmbeddingStore")
    @Primary
    public PgVectorEmbeddingStore knowledgeEmbeddingStore(DataSource dataSource) {
        return createStore(dataSource, "jinyan.knowledge_vector_store");
    }

    @Bean("userEmbeddingStore")
    public PgVectorEmbeddingStore userEmbeddingStore(DataSource dataSource) {
        return createStore(dataSource, "jinyan.user_vector_store");
    }

    private PgVectorEmbeddingStore createStore(
            DataSource dataSource,
            String tableName
    ) {
        return PgVectorEmbeddingStore.datasourceBuilder()
            .datasource(dataSource)
            .table(tableName)
            .dimension(2560)
            .createTable(true)
            .skipCreateVectorExtension(true)
            .build();
    }
}
