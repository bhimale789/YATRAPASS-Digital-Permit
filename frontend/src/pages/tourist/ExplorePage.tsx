import { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { areaApi } from '../../api/endpoints';
import { DestinationCard } from '../../components/DestinationCard';
import { Alert, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';

/** All permit-required destinations, filterable by the header search (?q=). */
export function ExplorePage() {
  const [params] = useSearchParams();
  const q = (params.get('q') ?? '').trim().toLowerCase();
  const { data, error, loading } = useAsync(() => areaApi.listActive(), []);

  const results = useMemo(
    () =>
      (data ?? [])
        .filter((a) => a.permitRequired)
        .filter((a) => !q || [a.name, a.code, a.district, a.province, a.description]
          .some((v) => v?.toLowerCase().includes(q))),
    [data, q],
  );

  if (loading) return <Spinner />;
  if (error) return <Alert>{error}</Alert>;

  return (
    <div className="t-page">
      <div className="t-section-head">
        <h1>{q ? <>Results for “{params.get('q')}”</> : 'Explore destinations'}</h1>
        <span className="muted">{results.length} destination{results.length === 1 ? '' : 's'}</span>
      </div>
      {results.length ? (
        <div className="dest-grid">
          {results.map((a) => <DestinationCard key={a.id} area={a} />)}
        </div>
      ) : (
        <div className="t-empty">No destinations match your search.</div>
      )}
    </div>
  );
}
