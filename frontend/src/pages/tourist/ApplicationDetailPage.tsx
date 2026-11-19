import { useRef, useState, type FormEvent } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { touristApi } from '../../api/endpoints';
import { PaymentCard } from '../../components/PaymentCard';
import { Alert, Card, DetailList, EmptyState, PageHeader, Spinner, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { DocumentType } from '../../types';
import { saveBlob } from '../../utils/download';
import { errorMessage } from '../../utils/errors';
import { formatBytes, formatDate, formatDateTime, humanize } from '../../utils/format';

const DOC_TYPES: DocumentType[] = ['PASSPORT', 'VISA', 'PHOTO', 'TRAVEL_INSURANCE', 'ITINERARY', 'OTHER'];
const MAX_BYTES = 5 * 1024 * 1024;

export function ApplicationDetailPage() {
  const id = Number(useParams().id);
  const location = useLocation();
  const created = (location.state as { created?: boolean } | null)?.created;
  const { data: app, error: loadError, loading, setData, reload } = useAsync(() => touristApi.application(id), [id]);
  const [docType, setDocType] = useState<DocumentType>('PASSPORT');
  const fileInput = useRef<HTMLInputElement>(null);
  const [message, setMessage] = useState<{ tone: 'error' | 'success'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  if (loading && !app) return <Spinner />;
  if (loadError || !app) return <Alert>{loadError ?? 'Application not found'}</Alert>;

  const open = app.status === 'PENDING' || app.status === 'UNDER_REVIEW';
  const hasPassport = app.documents?.some((d) => d.documentType === 'PASSPORT');

  const upload = async (e: FormEvent) => {
    e.preventDefault();
    const file = fileInput.current?.files?.[0];
    if (!file) return;
    if (file.size > MAX_BYTES) {
      setMessage({ tone: 'error', text: 'File is larger than 5 MB.' });
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      await touristApi.uploadDocument(id, docType, file);
      if (fileInput.current) fileInput.current.value = '';
      setMessage({ tone: 'success', text: `${humanize(docType)} uploaded.` });
      reload();
    } catch (err) {
      setMessage({ tone: 'error', text: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  };

  const remove = async (documentId: number) => {
    if (!confirm('Remove this document?')) return;
    try {
      await touristApi.deleteDocument(id, documentId);
      reload();
    } catch (err) {
      setMessage({ tone: 'error', text: errorMessage(err) });
    }
  };

  const download = async (documentId: number, fileName: string) => {
    try {
      saveBlob(await touristApi.documentBlob(id, documentId), fileName);
    } catch (err) {
      setMessage({ tone: 'error', text: errorMessage(err) });
    }
  };

  const cancel = async () => {
    if (!confirm('Cancel this application? This cannot be undone.')) return;
    try {
      setData(await touristApi.cancelApplication(id));
      reload();
    } catch (err) {
      setMessage({ tone: 'error', text: errorMessage(err) });
    }
  };

  return (
    <>
      <PageHeader
        title={app.applicationNumber}
        subtitle={<StatusBadge status={app.status} />}
        actions={
          open && (
            <button className="btn btn-danger-ghost" onClick={cancel}>
              Cancel application
            </button>
          )
        }
      />
      {created && (
        <Alert tone="success">
          Application submitted with status PENDING. Step 2: upload your documents below (a passport copy is required).
        </Alert>
      )}
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      {app.status === 'REJECTED' && <Alert>Rejected: {app.rejectionReason}</Alert>}
      {app.permit && (
        <Alert tone="success">
          Approved — permit <Link to="/tourist/permits">{app.permit.permitNumber}</Link> has been issued.
        </Alert>
      )}

      <PaymentCard app={app} onChanged={reload} />

      <div className="grid-2 align-start">
        <Card title="Trip details">
          <DetailList
            items={[
              ['Destination', `${app.area.name} (${app.area.code})`],
              ['Start date', formatDate(app.startDate)],
              ['End date', formatDate(app.endDate)],
              ['Duration', `${app.requestedDays} day(s)`],
              ['Purpose', app.purpose],
              ['Submitted', formatDateTime(app.submittedAt)],
              ['Reviewed', app.reviewedAt ? formatDateTime(app.reviewedAt) : '—'],
            ]}
          />
        </Card>

        <Card title="Documents">
          {open && !hasPassport && <Alert tone="info">A passport copy is required before approval.</Alert>}
          {!app.documents?.length ? (
            <EmptyState>No documents uploaded.</EmptyState>
          ) : (
            <ul className="doc-list">
              {app.documents.map((d) => (
                <li key={d.id}>
                  <div>
                    <strong>{humanize(d.documentType)}</strong>
                    <span className="muted small">
                      {d.fileName} · {formatBytes(d.fileSize)} {d.verified && '· ✓ verified'}
                    </span>
                  </div>
                  <div className="row-actions">
                    <button className="btn btn-ghost btn-sm" onClick={() => download(d.id, d.fileName)}>
                      Download
                    </button>
                    {app.status === 'PENDING' && (
                      <button className="btn btn-danger-ghost btn-sm" onClick={() => remove(d.id)}>
                        Remove
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
          {open && (
            <form className="upload-form" onSubmit={upload}>
              <select value={docType} onChange={(e) => setDocType(e.target.value as DocumentType)} aria-label="Document type">
                {DOC_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {humanize(t)}
                  </option>
                ))}
              </select>
              <input ref={fileInput} type="file" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" required />
              <button className="btn btn-primary" disabled={busy}>
                {busy ? 'Uploading…' : 'Upload'}
              </button>
              <span className="field-hint">PDF, JPEG or PNG · max 5 MB</span>
            </form>
          )}
        </Card>
      </div>
    </>
  );
}
