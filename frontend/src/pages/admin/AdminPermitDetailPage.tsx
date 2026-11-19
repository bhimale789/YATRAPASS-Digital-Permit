import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { PermitPdfButton } from '../../components/PermitPdfButton';
import { QrImage } from '../../components/QrImage';
import { ReasonDialog } from '../../components/ReasonDialog';
import { Alert, Card, DetailList, EmptyState, PageHeader, Spinner, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { formatDate, formatDateTime, humanize } from '../../utils/format';

type DialogKind = 'cancel' | 'regenerate' | 'revoke' | null;

export function AdminPermitDetailPage() {
  const id = Number(useParams().id);
  const issued = (useLocation().state as { issued?: boolean } | null)?.issued;
  const { data: permit, error: loadError, loading, setData } = useAsync(() => adminApi.permit(id), [id]);
  const history = useAsync(() => adminApi.verifications({ permitId: id, size: 10 }), [id, permit]);
  const [dialog, setDialog] = useState<DialogKind>(null);
  const [error, setError] = useState<string | null>(null);
  const [qrVersion, setQrVersion] = useState(0);

  if (loading && !permit) return <Spinner />;
  if (loadError || !permit) return <Alert>{loadError ?? 'Not found'}</Alert>;
  const active = permit.status === 'ACTIVE';

  const complete = async () => {
    if (!confirm(`Mark ${permit.permitNumber} as completed? Its QR will stop verifying.`)) return;
    try {
      setData(await adminApi.completePermit(id));
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  const dialogs: Record<Exclude<DialogKind, null>, { title: string; description: string; label: string;
    run: (reason: string) => Promise<unknown> }> = {
    cancel: {
      title: `Cancel ${permit.permitNumber}`,
      description: 'The permit becomes CANCELLED and its QR verifies as invalid. This cannot be undone.',
      label: 'Cancel permit',
      run: async (reason) => setData(await adminApi.cancelPermit(id, reason)),
    },
    regenerate: {
      title: 'Regenerate QR code',
      description: 'The current QR is revoked immediately and a new one is issued to the tourist.',
      label: 'Regenerate QR',
      run: async (reason) => {
        setData(await adminApi.regenerateQr(id, reason));
        setQrVersion((v) => v + 1);
      },
    },
    revoke: {
      title: 'Revoke QR code',
      description: 'The current QR stops verifying. No replacement is issued until you regenerate.',
      label: 'Revoke QR',
      run: async (reason) => setData(await adminApi.revokeQr(id, reason)),
    },
  };
  const current = dialog ? dialogs[dialog] : null;

  return (
    <>
      <PageHeader
        title={permit.permitNumber}
        subtitle={<StatusBadge status={permit.status} />}
        actions={
          <>
            <PermitPdfButton
              load={() => adminApi.permitPdfBlob(id)}
              permitNumber={permit.permitNumber}
              className="btn btn-ghost"
            />
            {active && (
              <>
                <button className="btn btn-ghost" onClick={complete}>
                  Mark completed
                </button>
                <button className="btn btn-danger-ghost" onClick={() => setDialog('cancel')}>
                  Cancel permit
                </button>
              </>
            )}
          </>
        }
      />
      {issued && <Alert tone="success">Permit issued and secure QR code generated.</Alert>}
      <Alert>{error}</Alert>
      {permit.status === 'CANCELLED' && <Alert>Cancelled: {permit.cancellationReason}</Alert>}

      <div className="grid-2 align-start">
        <Card title="Permit">
          <DetailList
            items={[
              ['Tourist', permit.tourist && <Link to={`/admin/tourists/${permit.tourist.id}`}>{permit.tourist.fullName}</Link>],
              ['Nationality', permit.tourist?.nationality],
              ['Area', `${permit.area.name} (${permit.area.code})`],
              ['Valid from', formatDate(permit.validFrom)],
              ['Valid until', formatDate(permit.validUntil)],
              ['Issued', formatDate(permit.issueDate)],
              ['Application', <Link to={`/admin/applications/${permit.applicationId}`}>{permit.applicationNumber}</Link>],
            ]}
          />
        </Card>
        <Card
          title="QR code"
          actions={
            active && (
              <div className="row-actions">
                <button className="btn btn-ghost btn-sm" onClick={() => setDialog('regenerate')}>
                  Regenerate
                </button>
                {permit.qrActive && (
                  <button className="btn btn-danger-ghost btn-sm" onClick={() => setDialog('revoke')}>
                    Revoke
                  </button>
                )}
              </div>
            )
          }
        >
          {permit.qrActive ? (
            <QrImage load={() => adminApi.permitQrBlob(id)} alt={`QR for ${permit.permitNumber}`} version={qrVersion} />
          ) : (
            <EmptyState>No active QR code.</EmptyState>
          )}
        </Card>
      </div>

      <Card title="Verification history" actions={<Link to={`/admin/verifications?permitId=${id}`}>All →</Link>}>
        {!history.data?.content.length ? (
          <EmptyState>This permit has not been scanned.</EmptyState>
        ) : (
          <ul className="activity">
            {history.data.content.map((v) => (
              <li key={v.id}>
                <span>{formatDateTime(v.verifiedAt)}</span>
                <span>
                  {v.verifiedByEmail} {v.failureReason && `· ${humanize(v.failureReason)}`}
                </span>
                <StatusBadge status={v.result} />
              </li>
            ))}
          </ul>
        )}
      </Card>

      {current && (
        <ReasonDialog
          open
          title={current.title}
          description={current.description}
          confirmLabel={current.label}
          danger={dialog !== 'regenerate'}
          onClose={() => setDialog(null)}
          onConfirm={async (reason) => {
            try {
              setError(null);
              await current.run(reason);
            } catch (err) {
              setError(errorMessage(err));
            } finally {
              setDialog(null);
            }
          }}
        />
      )}
    </>
  );
}
