package com.example.ragtest.external.normalizer;

import com.example.ragtest.policy.domain.Policy;

public interface PolicyNormalizer<T> {
    Policy normalize(T source);
}
