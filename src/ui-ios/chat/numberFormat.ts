/**
 * Cached Intl.NumberFormat (audit #23): constructing one goes through ICU (JNI on Android) and cost more
 * than formatting; the receipt and place rows formatted several numbers per render, each with a new one.
 */
const cache = new Map<string, Intl.NumberFormat>();

export function numberFormat(locale: string, minFraction: number, maxFraction: number): Intl.NumberFormat {
  const key = `${locale}|${minFraction}|${maxFraction}`;
  let f = cache.get(key);
  if (!f) cache.set(key, (f = new Intl.NumberFormat(locale, { minimumFractionDigits: minFraction, maximumFractionDigits: maxFraction })));
  return f;
}
