package com.example.coach.service;

import com.example.coach.dto.AuthDtos.CountryResponse;
import com.example.coach.exception.ApiException;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Countries with their dialling codes, and mobile number validation, backed by Google's libphonenumber
 * (the library Android uses), so every country's numbering plan is checked properly.
 */
@Service
public class PhoneNumberService {

    private static final Set<PhoneNumberType> MOBILE_TYPES = Set.of(PhoneNumberType.MOBILE,
            PhoneNumberType.FIXED_LINE_OR_MOBILE);

    private final PhoneNumberUtil phones = PhoneNumberUtil.getInstance();
    private final List<CountryResponse> countries;

    public PhoneNumberService() {
        Collator byName = Collator.getInstance(Locale.ENGLISH);
        this.countries = phones.getSupportedRegions().stream()
                .map(region -> new CountryResponse(region, countryName(region),
                        "+" + phones.getCountryCodeForRegion(region)))
                .sorted(Comparator.comparing(CountryResponse::name, byName))
                .toList();
    }

    /** Every supported country, sorted by English name. */
    public List<CountryResponse> countries() {
        return countries;
    }

    /**
     * Checks that {@code number} is a valid mobile number in {@code countryCode} and returns it in E.164 form
     * (for example {@code +919876543210}). Accepts national format and an explicit +dialling-code prefix.
     */
    public String normalizeMobile(String countryCode, String number) {
        String region = countryCode.strip().toUpperCase(Locale.ROOT);
        if (!phones.getSupportedRegions().contains(region)) {
            throw ApiException.invalidField("countryCode", "Choose a country from the list.");
        }
        PhoneNumber parsed;
        try {
            parsed = phones.parse(number, region);
        } catch (NumberParseException e) {
            throw ApiException.invalidField("mobileNumber", "Enter a valid mobile number.");
        }
        if (!phones.isValidNumberForRegion(parsed, region)) {
            throw ApiException.invalidField("mobileNumber",
                    "This isn't a valid mobile number for " + countryName(region) + ".");
        }
        if (!MOBILE_TYPES.contains(phones.getNumberType(parsed))) {
            throw ApiException.invalidField("mobileNumber", "Enter a mobile number rather than a landline.");
        }
        return phones.format(parsed, PhoneNumberFormat.E164);
    }

    private static String countryName(String region) {
        String name = Locale.of("", region).getDisplayCountry(Locale.ENGLISH);
        return name == null || name.isBlank() ? region : name;
    }
}
