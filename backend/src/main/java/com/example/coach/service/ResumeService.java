package com.example.coach.service;

import com.example.coach.config.CoachProperties;
import com.example.coach.dto.DocumentDtos.ResumeResponse;
import com.example.coach.dto.DocumentDtos.ResumeSummary;
import com.example.coach.exception.ApiException;
import com.example.coach.model.Resume;
import com.example.coach.repository.ResumeRepository;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Upload pipeline: detect type, extract text (Tika), mask PII, then store. Raw files are never kept. */
@Service
public class ResumeService {

    private final ResumeParserService parser;
    private final PiiRedactor redactor;
    private final ResumeRepository resumes;
    private final int maxChars;

    public ResumeService(ResumeParserService parser, PiiRedactor redactor, ResumeRepository resumes,
            CoachProperties properties) {
        this.parser = parser;
        this.redactor = redactor;
        this.resumes = resumes;
        this.maxChars = properties.ai().maxDocumentChars();
    }

    public ResumeResponse upload(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a PDF or DOCX file to upload.");
        }
        String fileName = cleanFileName(file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The upload could not be read. Please try again.");
        }
        ResumeParserService.ParsedDocument document = parser.parse(fileName, bytes);
        PiiRedactor.Result redacted = redactor.redact(document.text());
        String text = redacted.text().length() > maxChars ? redacted.text().substring(0, maxChars) : redacted.text();

        Resume resume = resumes.save(new Resume(userId, fileName, document.contentType(), text));
        return new ResumeResponse(resume.getId(), fileName, text.length(), redacted.counts(), text,
                resume.getCreatedAt());
    }

    public List<ResumeSummary> list(long userId) {
        return resumes.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(resume -> new ResumeSummary(resume.getId(), resume.getFileName(), resume.getCreatedAt()))
                .toList();
    }

    static String cleanFileName(String original) {
        if (original == null || original.isBlank()) {
            return "resume";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty()) {
            return "resume";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
