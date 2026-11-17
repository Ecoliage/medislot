const FORMULA_TRIGGER = /^[=+\-@\t\r]/;

export function csvCell(value: unknown, separator = ';') {
  if (value === null || value === undefined) {
    return '';
  }
  if (typeof value === 'number') {
    return String(value);
  }
  const raw = String(value);
  const text = FORMULA_TRIGGER.test(raw) ? `'${raw}` : raw;
  const needsQuotes = text.includes(separator) || /["\r\n]/.test(text);
  return needsQuotes ? `"${text.replace(/"/g, '""')}"` : text;
}
