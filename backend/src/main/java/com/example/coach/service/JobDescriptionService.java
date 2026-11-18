package com.example.coach.service;

import com.example.coach.dto.DocumentDtos.JobDescriptionRequest;
import com.example.coach.dto.DocumentDtos.JobDescriptionResponse;
import com.example.coach.model.JobDescription;
import com.example.coach.repository.JobDescriptionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JobDescriptionService {

    private final JobDescriptionRepository jobDescriptions;

    public JobDescriptionService(JobDescriptionRepository jobDescriptions) {
        this.jobDescriptions = jobDescriptions;
    }

    public JobDescriptionResponse create(long userId, JobDescriptionRequest request) {
        JobDescription saved = jobDescriptions.save(new JobDescription(userId, blankToNull(request.title()),
                blankToNull(request.company()), ResumeParserService.normalize(request.text())));
        return toResponse(saved);
    }

    public List<JobDescriptionResponse> list(long userId) {
        return jobDescriptions.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(JobDescriptionService::toResponse)
                .toList();
    }

    private static JobDescriptionResponse toResponse(JobDescription jd) {
        return new JobDescriptionResponse(jd.getId(), jd.getTitle(), jd.getCompany(), jd.getText().length(),
                jd.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
