import { describe, expect, it } from 'vitest';
import { guessCountry, passwordChecks, validateRegistration, type RegistrationForm } from './account';

const valid: RegistrationForm = {
  firstName: 'Asha',
  lastName: "D'Souza-Verma",
  email: 'asha@example.com',
  countryCode: 'IN',
  mobileNumber: '98765 43210',
  password: 'interview-ready-1',
  confirmPassword: 'interview-ready-1',
};

describe('validateRegistration', () => {
  it('accepts a complete, valid form', () => {
    expect(validateRegistration(valid)).toEqual({});
    expect(validateRegistration({ ...valid, firstName: 'José', lastName: 'Ñúñez' })).toEqual({});
  });

  it('reports every problem at once, field by field', () => {
    const errors = validateRegistration({
      firstName: 'R2-D2', lastName: ' ', email: 'asha@example', countryCode: '', mobileNumber: 'call me',
      password: 'short', confirmPassword: 'short',
    });
    expect(Object.keys(errors).sort()).toEqual(['countryCode', 'email', 'firstName', 'lastName', 'mobileNumber', 'password']);
  });

  it('needs a password with a letter and a number, typed twice', () => {
    expect(validateRegistration({ ...valid, password: 'lettersonly', confirmPassword: 'lettersonly' }).password)
      .toMatch(/letter and one number/);
    expect(validateRegistration({ ...valid, confirmPassword: 'something-else-1' }).confirmPassword)
      .toMatch(/do not match/);
  });
});

describe('passwordChecks', () => {
  it('checks length, letters and numbers separately', () => {
    expect(passwordChecks('abc')).toEqual({ length: false, letter: true, number: false });
    expect(passwordChecks('abcdefg1')).toEqual({ length: true, letter: true, number: true });
  });
});

describe('guessCountry', () => {
  const known = new Set(['IN', 'US', 'GB', 'DE']);

  it('prefers the time zone, then the language region, then the US', () => {
    expect(guessCountry('Asia/Kolkata', ['en-US'], known)).toBe('IN');
    expect(guessCountry('Europe/Zurich', ['de-DE', 'en'], known)).toBe('DE');
    expect(guessCountry(undefined, ['en'], known)).toBe('US');
  });
});
