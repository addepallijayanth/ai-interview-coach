package com.example.coach.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.example.coach.ai.AiCall;
import com.example.coach.ai.AiExecutor;
import com.example.coach.ai.AnalysisPipeline;
import com.example.coach.ai.ClaudeClient;
import com.example.coach.ai.CostCalculator;
import com.example.coach.ai.JsonSchemas;
import com.example.coach.ai.ModelRouter;
import com.example.coach.ai.PromptTemplates;
import com.example.coach.ai.StructuredOutputService;
import com.example.coach.ai.TokenUsage;
import com.example.coach.ai.UsageTracker;
import com.example.coach.config.CoachProperties;
import com.example.coach.support.TestProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Measures analysis quality with the real models on 20 labelled resume/job pairs and writes
 * target/eval-report.md. It costs real money (about $0.50 per full run at list prices: 60 calls, mostly the
 * Sonnet comparison), so it is excluded from normal builds:
 *
 * <pre>ANTHROPIC_API_KEY=... ./mvnw test -Peval</pre>
 *
 * Fails if fewer than 80% of cases pass (override with -Deval.minPassRate=0.9).
 */
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class MatchQualityEvalTest {

    @Test
    void matchScoresLandInTheExpectedRangesAndKeyGapsAreFound() throws Exception {
        String baseUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://api.anthropic.com");
        CoachProperties properties = TestProperties.withBaseUrl(baseUrl);
        CostCalculator costs = new CostCalculator(properties);
        Map<Long, BigDecimal> costPerCase = new ConcurrentHashMap<>();
        UsageTracker tracker = new UsageTracker() {
            @Override
            public void checkBudget(AiCall call) {}

            @Override
            public void record(AiCall call, String model, TokenUsage usage) {
                costPerCase.merge(call.userId(), costs.cost(model, usage), BigDecimal::add);
            }
        };

        ObjectMapper mapper = JsonMapper.builder().findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        ClaudeClient claude = new ClaudeClient(AnthropicOkHttpClient.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY")).baseUrl(baseUrl).maxRetries(4).build(), true, tracker);
        StructuredOutputService structured = new StructuredOutputService(claude, new ModelRouter(properties),
                new JsonSchemas(mapper), mapper, Validation.buildDefaultValidatorFactory().getValidator());
        AnalysisPipeline pipeline = new AnalysisPipeline(structured, new PromptTemplates(), mapper, new AiExecutor(),
                properties);

        List<MatchEval.Case> cases = MatchEval.loadCases();
        Semaphore concurrency = new Semaphore(3); // stay well inside default rate limits
        List<MatchEval.Result> results;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<MatchEval.Result>> futures = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                long caseKey = i;
                MatchEval.Case evalCase = cases.get(i);
                futures.add(executor.submit(() -> {
                    concurrency.acquire();
                    long started = System.currentTimeMillis();
                    try {
                        var analysis = pipeline.analyze(caseKey, evalCase.resume(), evalCase.jobDescription());
                        return MatchEval.grade(evalCase, analysis.match().value().matchScore(),
                                analysis.match().value().missingSkills(),
                                costPerCase.getOrDefault(caseKey, BigDecimal.ZERO), System.currentTimeMillis() - started);
                    } catch (RuntimeException e) {
                        return MatchEval.failed(evalCase, e.getMessage(), System.currentTimeMillis() - started);
                    } finally {
                        concurrency.release();
                    }
                }));
            }
            results = new ArrayList<>();
            for (Future<MatchEval.Result> future : futures) {
                results.add(future.get());
            }
        }

        String report = MatchEval.markdownReport(results);
        Path reportFile = Path.of("target", "eval-report.md");
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, report, StandardCharsets.UTF_8);
        System.out.println(report);

        double passRate = results.stream().filter(MatchEval.Result::passed).count() / (double) results.size();
        double minimum = Double.parseDouble(System.getProperty("eval.minPassRate", "0.8"));
        assertThat(passRate).as("eval pass rate (see target/eval-report.md)").isGreaterThanOrEqualTo(minimum);
    }
}
