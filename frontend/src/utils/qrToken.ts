const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;

/**
 * Extracts the verification token from scanned QR text. Accepts either a bare token or
 * a verification URL ending in /verify/<token>. Returns null for anything else so that
 * arbitrary QR codes are never sent to the backend as-is.
 */
export function extractToken(scanned: string): string | null {
  const text = scanned.trim();
  if (TOKEN_PATTERN.test(text)) return text;
  try {
    const url = new URL(text);
    const match = url.pathname.match(/\/verify\/([A-Za-z0-9_-]{43})\/?$/);
    return match ? match[1] : null;
  } catch {
    return null;
  }
}
