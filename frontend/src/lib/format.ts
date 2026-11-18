export type Tone = 'good' | 'ok' | 'weak';

/** Colour band for a score: 75%+ good, 50%+ ok, otherwise weak. */
export function scoreTone(score: number, max = 100): Tone {
  const ratio = max === 0 ? 0 : score / max;
  if (ratio >= 0.75) return 'good';
  if (ratio >= 0.5) return 'ok';
  return 'weak';
}

export function formatUsd(value: number | undefined): string {
  if (value === undefined || Number.isNaN(value)) return '$0.00';
  return value < 0.01 && value > 0 ? `$${value.toFixed(4)}` : `$${value.toFixed(2)}`;
}

export function formatDate(iso: string | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat().format(value);
}

/** "1 email, 2 phones" from the redaction counts returned on upload. */
export function describeRedactions(counts: Record<string, number>): string {
  const labels: Record<string, [string, string]> = {
    email: ['email', 'emails'],
    phone: ['phone number', 'phone numbers'],
    address: ['address', 'addresses'],
    profileUrl: ['profile link', 'profile links'],
  };
  return Object.entries(counts)
    .filter(([, count]) => count > 0)
    .map(([kind, count]) => {
      const [one, many] = labels[kind] ?? [kind, kind];
      return `${count} ${count === 1 ? one : many}`;
    })
    .join(', ');
}

export function roleLabel(title: string, company?: string): string {
  return company ? `${title} at ${company}` : title;
}
