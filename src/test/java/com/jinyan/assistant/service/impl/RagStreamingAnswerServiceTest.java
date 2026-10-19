package com.jinyan.assistant.service.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagStreamingAnswerServiceTest {

    @Test
    void shouldReplaceGenericCitationSplitAcrossStreamingChunks() {
        List<String> output = new ArrayList<>();
        RagStreamingAnswerService.CitationNormalizingConsumer consumer =
                new RagStreamingAnswerService.CitationNormalizingConsumer(
                        "[资料1][资料2]",
                        output::add
                );

        consumer.accept("第一条结论 [资");
        consumer.accept("料]\n第二条结论 [资料");
        consumer.accept("2]");
        consumer.flush();

        assertThat(String.join("", output))
                .isEqualTo("第一条结论 [资料1][资料2]\n第二条结论 [资料2]")
                .doesNotContain("[资料]");
    }
}
