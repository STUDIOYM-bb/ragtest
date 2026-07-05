package com.example.ragtest.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class VectorStoreSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("create extension if not exists vector");
        jdbcTemplate.execute("""
                create table if not exists vector_store (
                    id uuid primary key default gen_random_uuid(),
                    content text not null,
                    metadata json not null,
                    embedding vector(1536) not null
                )
                """);
        jdbcTemplate.execute("""
                create index if not exists vector_store_embedding_hnsw_idx
                on vector_store using hnsw (embedding vector_cosine_ops)
                """);
    }
}
