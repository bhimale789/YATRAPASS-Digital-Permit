import { useEffect, useRef, useState, type FormEvent } from 'react';

interface Props {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel: string;
  danger?: boolean;
  onConfirm: (reason: string) => Promise<void>;
  onClose: () => void;
}

/** Modal that collects a mandatory reason before a destructive admin action. */
export function ReasonDialog({ open, title, description, confirmLabel, danger, onConfirm, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      setReason('');
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) return;
    setBusy(true);
    try {
      await onConfirm(reason.trim());
    } finally {
      setBusy(false);
    }
  };

  return (
    <dialog ref={ref} className="dialog" onClose={onClose}>
      <form onSubmit={submit}>
        <h2>{title}</h2>
        {description && <p className="muted">{description}</p>}
        <label className="field">
          <span className="field-label">Reason</span>
          <textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={1000} rows={3} required autoFocus />
        </label>
        <div className="dialog-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} disabled={busy || !reason.trim()}>
            {busy ? 'Working…' : confirmLabel}
          </button>
        </div>
      </form>
    </dialog>
  );
}
