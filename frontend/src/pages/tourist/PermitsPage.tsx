import { useState } from 'react';
import { touristApi } from '../../api/endpoints';
import { Alert, EmptyState, PageHeader, Pagination, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { PermitStatus } from '../../types';
import { PermitCard } from './PermitCard';

const TABS: { label: string; status?: PermitStatus }[] = [
  { label: 'Active', status: 'ACTIVE' },
  { label: 'Expired', status: 'EXPIRED' },
  { label: 'All history' },
];

export function PermitsPage() {
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const { data, error, loading } = useAsync(
    () => touristApi.permits({ status: TABS[tab].status, page, size: 9 }),
    [tab, page],
  );

  return (
    <>
      <PageHeader title="My Permits" subtitle="Show the QR code at checkpoints. Keep it private — it identifies your permit." />
      <div className="tabs" role="tablist">
        {TABS.map((t, i) => (
          <button
            key={t.label}
            role="tab"
            aria-selected={tab === i}
            className={tab === i ? 'active' : ''}
            onClick={() => {
              setTab(i);
              setPage(0);
            }}
          >
            {t.label}
          </button>
        ))}
      </div>
      <Alert>{error}</Alert>
      {loading ? (
        <Spinner />
      ) : !data?.content.length ? (
        <EmptyState>No permits in this list.</EmptyState>
      ) : (
        <div className="permit-grid">
          {data.content.map((p) => (
            <PermitCard key={p.id} permit={p} />
          ))}
        </div>
      )}
      <Pagination page={data} onChange={setPage} />
    </>
  );
}
