import { useState } from 'react';
import { errorMessage } from '../utils/errors';
import { saveBlob } from '../utils/download';

interface Props {
  /** Loads the PDF with authentication (permit PDFs can contain a live QR code, so they are never public URLs). */
  load: () => Promise<Blob>;
  permitNumber: string;
  className?: string;
}

/** Downloads the printable permit PDF; the user prints it from their PDF viewer. */
export function PermitPdfButton({ load, permitNumber, className = 'btn btn-ghost btn-sm' }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const download = async () => {
    setBusy(true);
    setError(null);
    try {
      saveBlob(await load(), `${permitNumber}.pdf`);
    } catch (e) {
      setError(errorMessage(e, 'Could not create the permit PDF'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <button type="button" className={className} onClick={download} disabled={busy}>
        {busy ? 'Preparing PDF…' : '⬇ Permit PDF'}
      </button>
      {error && <span className="muted small" role="alert">{error}</span>}
    </>
  );
}
