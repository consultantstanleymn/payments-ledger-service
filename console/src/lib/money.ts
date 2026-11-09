const INTEGER_MINOR_PATTERN = /^-?\d+$/;

function amountToBigInt(amount: number | string): bigint {
  if (typeof amount === 'number') {
    if (!Number.isInteger(amount)) {
      throw new TypeError('amount must be an integer minor unit');
    }
    return BigInt(amount);
  }

  if (!INTEGER_MINOR_PATTERN.test(amount)) {
    throw new TypeError('amount must be an integer minor unit');
  }

  return BigInt(amount);
}

function minorDigits(currency: string): number {
  return (
    new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency
    }).resolvedOptions().maximumFractionDigits ?? 2
  );
}

export function formatMinor(amount: number | string, currency: string): string {
  const value = amountToBigInt(amount);
  const negative = value < 0n;
  const absolute = negative ? -value : value;
  const fractionDigits = minorDigits(currency);
  const factor = 10n ** BigInt(fractionDigits);
  const major = absolute / factor;
  const minor = absolute % factor;

  const groupedMajor = new Intl.NumberFormat(undefined, {
    useGrouping: true
  }).format(major);

  if (fractionDigits === 0) {
    return `${negative ? '-' : ''}${groupedMajor}`;
  }

  const minorPadded = minor.toString().padStart(fractionDigits, '0');
  return `${negative ? '-' : ''}${groupedMajor}.${minorPadded}`;
}
