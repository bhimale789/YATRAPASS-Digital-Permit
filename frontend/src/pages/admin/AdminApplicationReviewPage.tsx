import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { ReasonDialog } from '../../components/ReasonDialog';
import { AdminFeeCard } from './AdminFeeCard';
import { Alert, Card, DetailList, EmptyState, PageHeader, Spinner, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { saveBlob } from '../../utils/download';
import { errorMessage } from '../../utils/errors';
import { formatBytes, formatDate, formatDateTime, humanize } from '../../utils/format';

export function AdminApplicationReviewPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { data: app, error: loadError, loading, reload } = useAsync(() => adminApi.application(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);

  if (loading && !app) return <Spinner />;
  if (loadError || !app) return <Alert>{loadError ?? 'Not found'}</Alert>;

  const open = app.status === 'PENDING' || app.status === 'UNDER_REVIEW';
  const hasPassport = app.documents?.some((d) => d.documentType === 'PASSPORT');
  const feeOutstanding = !!app.payment?.requiredForApproval && app.payment.status !== 'PAID';

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      reload();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const approve = async () => {
    if (!confirm(`Approve ${app.applicationNumber} and issue a permit with QR code?`)) return;
    setBusy(true);
    setError(null);
    try {
      const permit = await adminApi.approve(id);
      navigate(`/admin/permits/${permit.id}`, { state: { issued: true } });
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  const download = (documentId: number, fileName: string) =>
    run(async () => saveBlob(await adminApi.documentBlob(documentId), fileName));

  return (
    <>
      <PageHeader
        title={app.applicationNumber}
        subtitle={<StatusBadge status={app.status} />}
        actions={
          open && (
            <>
              {app.status === 'PENDING' && (
                <button className="btn btn-ghost" disabled={busy} onClick={() => run(() => adminApi.startReview(id))}>
                  Start review
                </button>
              )}
              <button className="btn btn-danger-ghost" disabled={busy} onClick={() => setRejectOpen(true)}>
                Reject
              </button>
              <button className="btn btn-success" disabled={busy || !hasPassport || feeOutstanding} onClick={approve}
                title={!hasPassport ? 'A passport document is required' : feeOutstanding ? 'The permit fee is not paid' : undefined}>
                Approve & issue permit
              </button>
            </>
          )
        }
      />
      <Alert>{error}</Alert>
      {open && !hasPassport && <Alert tone="info">Approval requires an uploaded passport document.</Alert>}
      {app.status === 'REJECTED' && <Alert>Rejected: {app.rejectionReason}</Alert>}
      {app.permit && (
        <Alert tone="success">
          Permit issued: <Link to={`/admin/permits/${app.permit.id}`}>{app.permit.permitNumber}</Link>
        </Alert>
      )}

      <AdminFeeCard app={app} onChanged={reload} />

      <div className="grid-2 align-start">
        <Card title="Application">
          <DetailList
            items={[
              ['Area', `${app.area.name} (${app.area.code})`],
              ['Travel dates', `${formatDate(app.startDate)} – ${formatDate(app.endDate)}`],
              ['Duration', `${app.requestedDays} day(s)`],
              ['Purpose', app.purpose],
              ['Submitted', formatDateTime(app.submittedAt)],
              ['Reviewed', formatDateTime(app.reviewedAt)],
            ]}
          />
        </Card>
        <Card
          title="Tourist"
          actions={app.tourist && <Link to={`/admin/tourists/${app.tourist.id}`}>Full profile →</Link>}
        >
          {app.tourist && (
            <DetailList
              items={[
                ['Name', app.tourist.fullName],
                ['Email', app.tourist.email],
                ['Nationality', app.tourist.nationality],
                ['Passport', <span className="mono">{app.tourist.passportNumberMasked}</span>],
                ['Account', app.tourist.enabled ? 'Enabled' : 'Disabled'],
              ]}
            />
          )}
        </Card>
      </div>

      <Card title="Submitted documents">
        {!app.documents?.length ? (
          <EmptyState>No documents uploaded.</EmptyState>
        ) : (
          <ul className="doc-list">
            {app.documents.map((d) => (
              <li key={d.id}>
                <div>
                  <strong>{humanize(d.documentType)}</strong>
                  <span className="muted small">
                    {d.fileName} · {formatBytes(d.fileSize)} · uploaded {formatDateTime(d.uploadedAt)}
                  </span>
                </div>
                <div className="row-actions">
                  {d.verified ? (
                    <span className="badge badge-ok">✓ Verified</span>
                  ) : (
                    <button className="btn btn-ghost btn-sm" disabled={busy}
                      onClick={() => run(() => adminApi.verifyDocument(d.id))}>
                      Mark verified
                    </button>
                  )}
                  <button className="btn btn-ghost btn-sm" onClick={() => download(d.id, d.fileName)}>
                    Download
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <ReasonDialog
        open={rejectOpen}
        title={`Reject ${app.applicationNumber}`}
        description="The reason is shown to the tourist."
        confirmLabel="Reject application"
        danger
        onClose={() => setRejectOpen(false)}
        onConfirm={async (reason) => {
          try {
            await adminApi.reject(id, reason);
            setRejectOpen(false);
            reload();
          } catch (err) {
            setError(errorMessage(err));
            setRejectOpen(false);
          }
        }}
      />
    </>
  );
}
