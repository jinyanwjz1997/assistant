package com.jinyan.assistant.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegmentSummaryServiceImplTest {

    @Test
    void shouldUseCompleteSentenceForFallbackSummary() {
        String text = "第一句话介绍核心业务规则。"
                + "第二句话包含更多实现细节和约束条件。".repeat(20);

        String summary = SegmentSummaryServiceImpl.createFallbackSummary(text);

        assertThat(summary)
                .isNotBlank()
                .hasSizeLessThanOrEqualTo(200)
                .endsWith("。");
    }

    @Test
    void shouldRejectBlankFallbackSource() {
        assertThatThrownBy(() -> SegmentSummaryServiceImpl.createFallbackSummary("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank segment text");
    }
}
