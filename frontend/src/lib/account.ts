import type { RegisterRequest } from '../api/types';

/** Same rules as the server (AuthDtos), checked in the browser first for instant feedback. */
const NAME = /^[\p{L}\p{M}][\p{L}\p{M}' .-]*$/u;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const MOBILE = /^[0-9 ()+.-]+$/;

export interface PasswordChecks {
  length: boolean;
  letter: boolean;
  number: boolean;
}

export function passwordChecks(password: string): PasswordChecks {
  return {
    length: password.length >= 8 && password.length <= 72,
    letter: /\p{L}/u.test(password),
    number: /\p{N}/u.test(password),
  };
}

export function passwordProblem(password: string): string | undefined {
  if (!password) return 'Choose a password.';
  const checks = passwordChecks(password);
  if (!checks.length) return 'Use 8 to 72 characters.';
  if (!checks.letter || !checks.number) return 'Include at least one letter and one number.';
  return undefined;
}

export type RegistrationForm = RegisterRequest & { confirmPassword: string };

/** Field name to message for everything that is wrong; empty when the form can be sent. */
export function validateRegistration(form: RegistrationForm): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const [field, label] of [['firstName', 'first name'], ['lastName', 'last name']] as const) {
    const value = form[field].trim();
    if (!value) errors[field] = `Enter your ${label}.`;
    else if (value.length > 60) errors[field] = 'Use at most 60 characters.';
    else if (!NAME.test(value)) errors[field] = 'Use letters only (spaces, hyphens and apostrophes are fine).';
  }
  const email = form.email.trim();
  if (!email) errors.email = 'Enter your email address.';
  else if (!EMAIL.test(email) || email.length > 255) errors.email = 'Enter a valid email address, like name@example.com.';
  if (!form.countryCode) errors.countryCode = 'Choose your country.';
  const mobile = form.mobileNumber.trim();
  const digits = mobile.replace(/\D/g, '');
  if (!mobile) errors.mobileNumber = 'Enter your mobile number.';
  else if (!MOBILE.test(mobile) || digits.length < 6 || digits.length > 15) errors.mobileNumber = 'Enter a valid mobile number.';
  const password = passwordProblem(form.password);
  if (password) errors.password = password;
  if (!errors.password && form.confirmPassword !== form.password) errors.confirmPassword = 'The passwords do not match.';
  return errors;
}

/** Common time zones, so the country list starts on the user's own country. */
const ZONE_COUNTRIES: Record<string, string> = {
  'Asia/Kolkata': 'IN', 'Asia/Calcutta': 'IN', 'Europe/London': 'GB', 'America/New_York': 'US',
  'America/Chicago': 'US', 'America/Denver': 'US', 'America/Phoenix': 'US', 'America/Los_Angeles': 'US',
  'America/Toronto': 'CA', 'America/Vancouver': 'CA', 'Australia/Sydney': 'AU', 'Australia/Melbourne': 'AU',
  'Asia/Singapore': 'SG', 'Asia/Dubai': 'AE', 'Europe/Berlin': 'DE', 'Europe/Paris': 'FR', 'Europe/Dublin': 'IE',
  'Europe/Amsterdam': 'NL', 'Asia/Tokyo': 'JP', 'Asia/Karachi': 'PK', 'Asia/Dhaka': 'BD', 'Asia/Colombo': 'LK',
  'Asia/Kathmandu': 'NP', 'Africa/Lagos': 'NG', 'Africa/Nairobi': 'KE', 'Africa/Johannesburg': 'ZA',
  'America/Sao_Paulo': 'BR', 'America/Mexico_City': 'MX',
};

/** Best guess at the user's country: time zone first, then the browser language's region, else the US. */
export function guessCountry(timeZone: string | undefined, languages: readonly string[], known: Set<string>): string {
  const fromZone = timeZone ? ZONE_COUNTRIES[timeZone] : undefined;
  if (fromZone && known.has(fromZone)) return fromZone;
  for (const language of languages) {
    const region = language.split('-')[1]?.toUpperCase();
    if (region && known.has(region)) return region;
  }
  return known.has('US') ? 'US' : [...known][0] ?? '';
}
