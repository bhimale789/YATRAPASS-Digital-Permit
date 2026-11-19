import { useEffect, useRef, useState } from 'react';
import { adminApi } from '../../api/endpoints';
import { AreaImage } from '../../components/AreaImage';
import { Alert } from '../../components/ui';
import type { Area } from '../../types';
import { errorMessage } from '../../utils/errors';

const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPT = 'image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp';

/** Upload, preview, replace or remove the destination photo shown to tourists. */
export function AreaPhotoEditor({ area, onChange }: { area: Area; onChange: (area: Area) => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: 'error' | 'success'; text: string } | null>(null);

  // Revoke the local preview URL when it changes or the editor closes.
  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview);
  }, [preview]);

  const choose = (f: File | undefined) => {
    setMessage(null);
    if (!f) return;
    if (f.size > MAX_BYTES) {
      setMessage({ tone: 'error', text: 'Photo is larger than 5 MB.' });
      return;
    }
    setFile(f);
    setPreview(URL.createObjectURL(f));
  };

  const reset = () => {
    setFile(null);
    setPreview(null);
    if (input.current) input.current.value = '';
  };

  const upload = async () => {
    if (!file) return;
    setBusy(true);
    setMessage(null);
    try {
      onChange(await adminApi.uploadAreaPhoto(area.id, file));
      reset();
      setMessage({ tone: 'success', text: 'Photo saved. Tourists will see it on destination cards.' });
    } catch (err) {
      setMessage({ tone: 'error', text: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!confirm('Remove this photo? The card will show a generated illustration instead.')) return;
    setBusy(true);
    setMessage(null);
    try {
      onChange(await adminApi.removeAreaPhoto(area.id));
      setMessage({ tone: 'success', text: 'Photo removed.' });
    } catch (err) {
      setMessage({ tone: 'error', text: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="photo-editor">
      <div className="photo-preview">
        {preview ? <img className="area-photo" src={preview} alt="New photo preview" /> :
          <AreaImage code={area.code} photoUrl={area.photoUrl} alt={area.name} />}
        <span className="photo-badge">{preview ? 'Preview — not saved' : area.photoUrl ? 'Current photo' : 'Generated illustration'}</span>
      </div>
      <div className="photo-controls">
        <strong>Destination photo</strong>
        <p className="muted small">Shown to tourists on destination cards. JPEG, PNG or WebP, max 5 MB. A wide landscape photo (3:2) looks best.</p>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        <input ref={input} type="file" accept={ACCEPT} onChange={(e) => choose(e.target.files?.[0])} disabled={busy}
          aria-label="Choose destination photo" />
        <div className="row-actions photo-buttons">
          {file && (
            <>
              <button type="button" className="btn btn-primary" onClick={upload} disabled={busy}>
                {busy ? 'Uploading…' : area.photoUrl ? 'Replace photo' : 'Upload photo'}
              </button>
              <button type="button" className="btn btn-ghost" onClick={reset} disabled={busy}>Discard</button>
            </>
          )}
          {!file && area.photoUrl && (
            <button type="button" className="btn btn-danger-ghost" onClick={remove} disabled={busy}>Remove photo</button>
          )}
        </div>
      </div>
    </div>
  );
}
