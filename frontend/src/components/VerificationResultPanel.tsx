import type { VerificationReason, VerificationResponse } from '../types';
import { formatDate, formatDateTime } from '../utils/format';

const REASON_TEXT: Record<VerificationReason, string> = {
  INVALID_TOKEN: 'This QR code is not a valid permit code.',
  TOKEN_REVOKED: 'This QR code has been revoked.',
  PERMIT_NOT_FOUND: 'No permit is associated with this QR code.',
  PERMIT_CANCELLED: 'Permit has been cancelled.',
  PERMIT_EXPIRED: 'Permit has expired.',
  PERMIT_NOT_ACTIVE: 'Permit is not active.',
  PERMIT_NOT_YET_VALID: 'Permit is not valid yet.',
  AREA_MISMATCH: 'Permit is not valid for this area.',
};

/** Large, unambiguous VALID / INVALID result for checkpoint officers. */
export function VerificationResultPanel({ result }: { result: VerificationResponse }) {
  if (result.valid) {
    return (
      <section className="verify-result verify-valid" role="status" aria-live="assertive">
        <div className="verify-icon" aria-hidden>✓</div>
        <h2>VALID PERMIT</h2>
        <dl className="verify-details">
          <div><dt>Permit Number</dt><dd>{result.permitNumber}</dd></div>
          <div><dt>Tourist</dt><dd>{result.touristName}</dd></div>
          <div><dt>Nationality</dt><dd>{result.nationality}</dd></div>
          <div><dt>Area</dt><dd>{result.area}</dd></div>
          <div><dt>Valid From</dt><dd>{formatDate(result.validFrom)}</dd></div>
          <div><dt>Valid Until</dt><dd>{formatDate(result.validUntil)}</dd></div>
          <div><dt>Status</dt><dd>{result.status}</dd></div>
        </dl>
        <p className="verify-meta">Verified {formatDateTime(result.verifiedAt)} · Check the tourist's passport matches the name.</p>
      </section>
    );
  }

  const fakeCode = result.reason === 'INVALID_TOKEN';
  return (
    <section className="verify-result verify-invalid" role="alert" aria-live="assertive">
      <div className="verify-icon" aria-hidden>✕</div>
      <h2>{fakeCode ? 'INVALID QR CODE' : 'INVALID PERMIT'}</h2>
      <p className="verify-reason">
        <strong>Reason:</strong> {result.reason && REASON_TEXT[result.reason]}
        <br />
        <code>{result.reason}</code>
      </p>
      {result.permitNumber && (
        <dl className="verify-details">
          <div><dt>Permit Number</dt><dd>{result.permitNumber}</dd></div>
          {result.area && <div><dt>Area</dt><dd>{result.area}</dd></div>}
          {result.validFrom && <div><dt>Valid From</dt><dd>{formatDate(result.validFrom)}</dd></div>}
          {result.validUntil && <div><dt>Valid Until</dt><dd>{formatDate(result.validUntil)}</dd></div>}
          {result.status && <div><dt>Status</dt><dd>{result.status}</dd></div>}
        </dl>
      )}
      <p className="verify-meta">Checked {formatDateTime(result.verifiedAt)}</p>
    </section>
  );
}
