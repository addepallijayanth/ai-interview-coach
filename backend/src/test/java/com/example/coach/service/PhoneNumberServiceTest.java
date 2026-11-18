package com.example.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coach.dto.AuthDtos.CountryResponse;
import com.example.coach.exception.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhoneNumberServiceTest {

    private final PhoneNumberService phones = new PhoneNumberService();

    @Test
    void listsCountriesWithTheirDiallingCodes() {
        assertThat(phones.countries()).hasSizeGreaterThan(200)
                .contains(new CountryResponse("IN", "India", "+91"), new CountryResponse("US", "United States", "+1"),
                        new CountryResponse("GB", "United Kingdom", "+44"));
        assertThat(phones.countries().getFirst().name()).isEqualTo("Afghanistan");
    }

    @Test
    void normalisesMobileNumbersToE164() {
        assertThat(phones.normalizeMobile("IN", "98765 43210")).isEqualTo("+919876543210");
        assertThat(phones.normalizeMobile("in", "+91 98765-43210")).isEqualTo("+919876543210");
        assertThat(phones.normalizeMobile("GB", "07400 123456")).isEqualTo("+447400123456");
        assertThat(phones.normalizeMobile("US", "(201) 555-0123")).isEqualTo("+12015550123");
    }

    @Test
    void rejectsNumbersThatDoNotFitTheCountry() {
        assertThatThrownBy(() -> phones.normalizeMobile("IN", "12345"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(errors(e)).containsKey("mobileNumber"));
        assertThatThrownBy(() -> phones.normalizeMobile("IN", "+44 7400 123456")) // a UK number with India chosen
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(errors(e).get("mobileNumber")).contains("India"));
        assertThatThrownBy(() -> phones.normalizeMobile("GB", "020 7946 0018")) // a London landline
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(errors(e).get("mobileNumber")).contains("mobile number"));
        assertThatThrownBy(() -> phones.normalizeMobile("ZZ", "98765 43210"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(errors(e)).containsKey("countryCode"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> errors(ApiException e) {
        assertThat(e.getCode()).isEqualTo("validation_failed");
        return (Map<String, String>) e.getProperties().get("errors");
    }
}
