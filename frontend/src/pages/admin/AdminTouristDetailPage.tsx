import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { Alert, Card, DetailList, PageHeader, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage } from '../../utils/errors';
import { formatDate, formatDateTime, humanize } from '../../utils/format';

export function AdminTouristDetailPage() {
  const id = Number(useParams().id);
  const { data: t, error: loadError, loading, setData } = useAsync(() => adminApi.tourist(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const [showPassport, setShowPassport] = useState(false);

  if (loading && !t) return <Spinner />;
  if (loadError || !t) return <Alert>{loadError ?? 'Not found'}</Alert>;

  const toggle = async () => {
    const action = t.enabled ? 'Disable' : 'Enable';
    if (!confirm(`${action} this tourist's account?`)) return;
    try {
      setData(await adminApi.setTouristEnabled(id, !t.enabled));
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader
        title={`${t.firstName} ${t.lastName}`}
        subtitle={t.email}
        actions={
          <button className={`btn ${t.enabled ? 'btn-danger-ghost' : 'btn-success'}`} onClick={toggle}>
            {t.enabled ? 'Disable account' : 'Enable account'}
          </button>
        }
      />
      <Alert>{error}</Alert>
      <div className="grid-2 align-start">
        <Card title="Identity">
          <DetailList
            items={[
              [
                'Passport',
                t.passportNumber ? (
                  <span className="mono">
                    {showPassport ? t.passportNumber : `****${t.passportNumber.slice(-4)}`}{' '}
                    <button className="link-btn" onClick={() => setShowPassport((s) => !s)}>
                      {showPassport ? 'hide' : 'reveal'}
                    </button>
                  </span>
                ) : (
                  '—'
                ),
              ],
              ['Nationality', t.nationality],
              ['Date of birth', formatDate(t.dateOfBirth)],
              ['Gender', humanize(t.gender)],
              ['Profile', t.profileComplete ? 'Complete' : 'Incomplete'],
            ]}
          />
        </Card>
        <Card title="Contact & account">
          <DetailList
            items={[
              ['Phone', t.phone],
              ['Address', t.address],
              ['Emergency contact', t.emergencyContact],
              ['Account', t.enabled ? 'Enabled' : 'Disabled'],
              ['Registered', formatDateTime(t.registeredAt)],
              ['Applications', <Link to={`/admin/applications?q=${encodeURIComponent(`${t.firstName} ${t.lastName}`)}`}>{t.applicationCount}</Link>],
              ['Permits', <Link to={`/admin/permits?q=${encodeURIComponent(`${t.firstName} ${t.lastName}`)}`}>{t.permitCount}</Link>],
            ]}
          />
        </Card>
      </div>
    </>
  );
}
