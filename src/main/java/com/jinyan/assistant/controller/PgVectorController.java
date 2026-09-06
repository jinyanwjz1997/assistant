package com.jinyan.assistant.controller;

import com.jinyan.assistant.entity.RelevantContext;
import com.jinyan.assistant.entity.RagIngestionStatus;
import com.jinyan.assistant.entity.VectorQuery;
import com.jinyan.assistant.service.RedisStreamRagSegmentQueue;
import com.jinyan.assistant.service.VectorService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/embedding")
public class PgVectorController {

    @Resource
    private VectorService vectorService;

    @Resource
    private RedisStreamRagSegmentQueue ragSegmentQueue;

    @PostMapping("/addKnowledge")
    public String addKnowledge(@RequestBody VectorQuery vectorQuery) {
        vectorService.addTextToVector(vectorQuery.context(), vectorQuery.sourceFile());
        return "success";
    }

    @PostMapping("/searchRelevantContext")
    public List<RelevantContext> searchRelevantContext(@RequestBody VectorQuery vectorQuery) {
        return vectorService.searchRelevantContext(vectorQuery.context(), vectorQuery.maxResult())
            .stream()
            .map(match -> new RelevantContext(
                match.score(),
                match.embedded().text(),
                match.embedded().metadata().getString("fileId")
            ))
            .toList();
    }

    @PostMapping("/readDocument")
    public String readDocument(@RequestParam String filePath) {
        return vectorService.readDocument(filePath);
    }

    @PostMapping(value = "/uploadDocument", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadDocument(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }

        try (InputStream inputStream = file.getInputStream()) {
            return vectorService.readDocument(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    inputStream
            );
        }
    }

    @GetMapping("/ingestion/{fileId}")
    public RagIngestionStatus ingestionStatus(@PathVariable String fileId) {
        return ragSegmentQueue.getStatus(fileId);
    }
}
