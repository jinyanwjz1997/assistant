package com.jinyan.assistant.service.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class VectorServiceImplPdfTest {

    @Test
    void shouldPreserveLogicalReadingOrderForTwoColumnPdf() throws IOException {
        byte[] pdf = createTwoColumnPdf();

        VectorServiceImpl.ParsedDocument parsed = VectorServiceImpl.parsePdf(
                new ByteArrayInputStream(pdf)
        );

        assertThat(parsed.pages()).hasSize(1);
        assertThat(parsed.pages().get(0).text())
                .containsSubsequence("LEFT-1", "LEFT-2", "RIGHT-1", "RIGHT-2");
    }

    /**
     * 内容流先写完整左栏再写完整右栏，但两栏坐标处于相同高度。
     * 如果启用按坐标排序，结果会错误地变为 LEFT-1、RIGHT-1、LEFT-2、RIGHT-2。
     */
    private byte[] createTwoColumnPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                writeText(content, font, "LEFT-1", 60, 700);
                writeText(content, font, "LEFT-2", 60, 680);
                writeText(content, font, "RIGHT-1", 320, 700);
                writeText(content, font, "RIGHT-2", 320, 680);
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * 在指定页面坐标写入一行测试文本。
     *
     * @param content PDF 页面内容流
     * @param font 测试字体
     * @param text 文本内容
     * @param x 横坐标
     * @param y 纵坐标
     * @throws IOException 当 PDF 内容写入失败时抛出
     */
    private void writeText(
            PDPageContentStream content,
            PDType1Font font,
            String text,
            float x,
            float y
    ) throws IOException {
        content.beginText();
        content.setFont(font, 12);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }
}
