package com.example.ragtest.rag.condition;

public interface UserConditionExtractor {
    ExtractedUserCondition extract(String question);
}
