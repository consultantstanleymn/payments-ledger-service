import { describe, expect, it } from 'vitest';
import { formatMinor } from './money';

describe('formatMinor', () => {
  it('formats 0', () => {
    expect(formatMinor(0, 'USD')).toBe('0.00');
  });

  it('formats negative', () => {
    expect(formatMinor(-1, 'USD')).toBe('-0.01');
  });

  it('formats 1 minor unit as 0.01 USD', () => {
    expect(formatMinor(1, 'USD')).toBe('0.01');
  });

  it('formats JPY with 0 decimals', () => {
    expect(formatMinor(1, 'JPY')).toBe('1');
  });

  it('formats 9007199254740993 without precision loss', () => {
    const formatted = formatMinor('9007199254740993', 'USD');
    expect(formatted.replace(/[^0-9.]/g, '')).toBe('90071992547409.93');
  });

  it('rejects non-integer input', () => {
    expect(() => formatMinor(1.25, 'USD')).toThrow();
    expect(() => formatMinor('abc', 'USD')).toThrow();
  });
});
