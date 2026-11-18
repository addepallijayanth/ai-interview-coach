package com.example.coach.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    void masksContactDetailsAndCountsThem() {
        PiiRedactor.Result result = redactor.redact("""
                Asha Verma
                asha.verma@example.com | +91 98765 43210 | https://www.linkedin.com/in/asha-verma
                12 MG Road, Bengaluru - 560001""");

        assertThat(result.text())
                .contains("Asha Verma", "[EMAIL]", "[PHONE]", "[PROFILE_URL]", "[ADDRESS]")
                .doesNotContain("asha.verma@", "98765", "linkedin.com/in", "MG Road", "560001");
        assertThat(result.counts()).containsEntry("email", 1).containsEntry("phone", 1).containsEntry("profileUrl", 1);
        assertThat(result.counts().get("address")).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"(555) 123-4567", "+1-555-123-4567", "555.123.4567", "+44 20 7946 0958", "9876543210"})
    void masksCommonPhoneFormats(String phone) {
        assertThat(redactor.redact("Call me on " + phone + " any time").text()).isEqualTo("Call me on [PHONE] any time");
    }

    @Test
    void masksUsAndUkAddresses() {
        assertThat(redactor.redact("221B Baker Street, London NW1 6XE").text()).doesNotContain("Baker", "NW1 6XE");
        assertThat(redactor.redact("Austin, TX 78701").text()).isEqualTo("[ADDRESS]");
    }

    @Test
    void leavesOrdinaryResumeContentAlone() {
        String text = """
                Senior Engineer, Acme (2019 - 2024)
                - Cut p99 latency by 40% and served 10,000 requests per second
                - Led 5 engineers on the main payments product, v2.3.1 release in 01/2023
                - Reduced costs with a new way of batching jobs""";
        PiiRedactor.Result result = redactor.redact(text);
        assertThat(result.text()).isEqualTo(text);
        assertThat(result.counts()).isEmpty();
    }
}
