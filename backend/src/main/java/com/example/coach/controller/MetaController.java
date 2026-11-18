package com.example.coach.controller;

import com.example.coach.ai.ClaudeClient;
import com.example.coach.ai.agent.InterviewLevel;
import com.example.coach.ai.agent.InterviewPlan;
import com.example.coach.ai.agent.QuestionCategory;
import com.example.coach.config.CoachProperties;
import com.example.coach.dto.AuthDtos.CountryResponse;
import com.example.coach.dto.ReportDtos.CategoryCount;
import com.example.coach.dto.ReportDtos.CategoryInfo;
import com.example.coach.dto.ReportDtos.LevelInfo;
import com.example.coach.dto.ReportDtos.MetaResponse;
import com.example.coach.dto.ReportDtos.UsageResponse;
import com.example.coach.security.CurrentUserId;
import com.example.coach.service.MailService;
import com.example.coach.service.PhoneNumberService;
import com.example.coach.service.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Meta")
public class MetaController {

    private final ClaudeClient claude;
    private final CoachProperties properties;
    private final UsageService usageService;
    private final MailService mailService;
    private final PhoneNumberService phoneNumbers;

    public MetaController(ClaudeClient claude, CoachProperties properties, UsageService usageService,
            MailService mailService, PhoneNumberService phoneNumbers) {
        this.claude = claude;
        this.properties = properties;
        this.usageService = usageService;
        this.mailService = mailService;
        this.phoneNumbers = phoneNumbers;
    }

    @GetMapping("/meta")
    @Operation(summary = "Public app settings: AI and email status, interview levels and question categories")
    public MetaResponse meta() {
        CoachProperties.Interview interview = properties.interview();
        List<LevelInfo> levels = Arrays.stream(InterviewLevel.values()).map(level -> {
            int questions = level.questions(interview);
            Map<QuestionCategory, Integer> counts = InterviewPlan.breakdown(level, questions);
            List<CategoryCount> breakdown = counts.entrySet().stream().filter(entry -> entry.getValue() > 0)
                    .map(entry -> new CategoryCount(entry.getKey().name(), entry.getKey().label(), entry.getValue()))
                    .toList();
            return new LevelInfo(level.name(), level.label(), level.description(), questions, breakdown);
        }).toList();
        List<CategoryInfo> categories = Arrays.stream(QuestionCategory.values())
                .map(category -> new CategoryInfo(category.name(), category.label(), category.description()))
                .toList();
        return new MetaResponse(claude.isConfigured(), properties.ai().fastModel(), properties.ai().smartModel(),
                mailService.isEnabled(), levels, categories, 2, interview.maxAnswerChars());
    }

    @GetMapping("/meta/countries")
    @Operation(summary = "Countries and their dialling codes, for the sign-up form")
    public ResponseEntity<List<CountryResponse>> countries() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(phoneNumbers.countries());
    }

    @GetMapping("/usage/me")
    @Operation(summary = "Your AI usage: tokens, cost and remaining requests today")
    public UsageResponse usage(@CurrentUserId Long userId) {
        return usageService.summary(userId);
    }
}
