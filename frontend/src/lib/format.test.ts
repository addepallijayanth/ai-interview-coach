import { describe, expect, it } from 'vitest';
import { describeRedactions, formatUsd, roleLabel, scoreTone } from './format';

describe('format helpers', () => {
  it('bands scores relative to their maximum', () => {
    expect(scoreTone(80)).toBe('good');
    expect(scoreTone(55)).toBe('ok');
    expect(scoreTone(30)).toBe('weak');
    expect(scoreTone(8, 10)).toBe('good');
    expect(scoreTone(4, 10)).toBe('weak');
  });

  it('shows small AI costs with enough precision', () => {
    expect(formatUsd(0.0042)).toBe('$0.0042');
    expect(formatUsd(1.5)).toBe('$1.50');
    expect(formatUsd(undefined)).toBe('$0.00');
  });

  it('describes redactions in plain English', () => {
    expect(describeRedactions({ email: 1, phone: 2, address: 0 })).toBe('1 email, 2 phone numbers');
    expect(describeRedactions({})).toBe('');
  });

  it('labels roles with an optional company', () => {
    expect(roleLabel('Backend Engineer', 'Globex')).toBe('Backend Engineer at Globex');
    expect(roleLabel('Backend Engineer')).toBe('Backend Engineer');
  });
});
