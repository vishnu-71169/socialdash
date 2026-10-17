import type { Platform } from '../api/types';

const compact = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 });
const full = new Intl.NumberFormat();

export const fmtCompact = (n: number) => compact.format(n);
export const fmtNumber = (n: number) => full.format(n);
export const fmtPct = (n: number) => `${n.toFixed(2)}%`;

export function fmtSigned(n: number) {
  return `${n > 0 ? '+' : ''}${compact.format(n)}`;
}

export function fmtDateTime(iso: string | null) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function fmtDay(isoDate: string) {
  // Analytics days are UTC dates; render them without shifting into local time.
  return new Date(`${isoDate}T00:00:00Z`).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });
}

export function timeAgo(iso: string) {
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 0) {
    const ahead = -diff;
    if (ahead < 3600) return `in ${Math.max(1, Math.round(ahead / 60))}m`;
    if (ahead < 86400) return `in ${Math.round(ahead / 3600)}h`;
    return `in ${Math.round(ahead / 86400)}d`;
  }
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.round(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.round(diff / 3600)}h ago`;
  return `${Math.round(diff / 86400)}d ago`;
}

export function utcDate(d: Date) {
  return d.toISOString().slice(0, 10);
}

export function daysAgo(n: number) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - n);
  return utcDate(d);
}

/** Converts a datetime-local value (browser local time) to an ISO instant. */
export function localInputToIso(value: string) {
  return value ? new Date(value).toISOString() : null;
}

/** Converts an ISO instant to a datetime-local value in browser local time. */
export function isoToLocalInput(iso: string | null) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export const PLATFORM_LABEL: Record<Platform, string> = {
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
  TWITTER: 'X / Twitter',
  LINKEDIN: 'LinkedIn',
};

export function titleCase(s: string) {
  return s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ');
}
