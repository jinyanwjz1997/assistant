package com.jinyan.assistant.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagSegmentMessageTest {

    @Test
    void shouldRoundTripThroughRedisStreamFields() {
        RagSegmentMessage message = new RagSegmentMessage(
                "segment-1",
                "file-1",
                "manual.pdf",
                "application/pdf",
                2,
                8,
                "这是文档片段。",
                0
        );

        RagSegmentMessage restored = RagSegmentMessage.fromMap(message.toMap());

        assertThat(restored).isEqualTo(message);
    }

    @Test
    void shouldIncrementRetryWithoutChangingSegmentIdentity() {
        RagSegmentMessage message = new RagSegmentMessage(
                "segment-1",
                "file-1",
                "manual.pdf",
                "application/pdf",
                0,
                1,
                "content",
                1
        );

        RagSegmentMessage retried = message.nextRetry();

        assertThat(retried.retryCount()).isEqualTo(2);
        assertThat(retried.segmentId()).isEqualTo(message.segmentId());
        assertThat(retried.text()).isEqualTo(message.text());
    }

    @Test
    void shouldPreserveParentChildMetadataDuringRoundTrip() {
        RagSegmentMessage message = new RagSegmentMessage(
                "segment-2",
                "file-1",
                "manual.pdf",
                "application/pdf",
                1,
                3,
                "命中的子片段",
                "parent-1",
                "完整父章节",
                "退款规则",
                "用户手册 > 退款规则",
                8,
                "segment-1",
                "segment-3",
                0
        );

        RagSegmentMessage restored = RagSegmentMessage.fromMap(message.toMap());

        assertThat(restored).isEqualTo(message);
    }
}
