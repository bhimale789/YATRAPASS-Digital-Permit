import { useEffect, useState } from 'react';
import { errorMessage } from '../utils/errors';
import { saveBlob } from '../utils/download';
import { Spinner } from './ui';

interface Props {
  /** Loads the PNG with authentication (QR images are never public URLs). */
  load: () => Promise<Blob>;
  alt: string;
  downloadName?: string;
  /** Change to force a reload (e.g. after QR regeneration). */
  version?: unknown;
}

export function QrImage({ load, alt, downloadName, version }: Props) {
  const [blob, setBlob] = useState<Blob | null>(null);
  const [url, setUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;
    setError(null);
    setUrl(null);
    load()
      .then((b) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(b);
        setBlob(b);
        setUrl(objectUrl);
      })
      .catch((e) => !cancelled && setError(errorMessage(e, 'QR code unavailable')));
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version]);

  if (error) return <div className="qr-box qr-error">{error}</div>;
  if (!url) return <div className="qr-box"><Spinner label="Loading QR…" /></div>;
  return (
    <div className="qr-wrap">
      <img className="qr-box" src={url} alt={alt} width={240} height={240} />
      {downloadName && blob && (
        <button className="btn btn-ghost btn-sm" onClick={() => saveBlob(blob, downloadName)}>
          ⬇ Download QR
        </button>
      )}
    </div>
  );
}
