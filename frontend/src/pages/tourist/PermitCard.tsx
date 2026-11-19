import { touristApi } from '../../api/endpoints';
import { PermitPdfButton } from '../../components/PermitPdfButton';
import { QrImage } from '../../components/QrImage';
import { DetailList, StatusBadge } from '../../components/ui';
import type { Permit } from '../../types';
import { formatDate } from '../../utils/format';

/** Digital permit card with QR code — what the tourist shows at a checkpoint. */
export function PermitCard({ permit }: { permit: Permit }) {
  const showQr = permit.status === 'ACTIVE' && permit.qrActive;
  return (
    <article className={`permit-card permit-${permit.status.toLowerCase()}`}>
      <header>
        <div>
          <div className="permit-label">Digital Permit</div>
          <div className="permit-number">{permit.permitNumber}</div>
        </div>
        <StatusBadge status={permit.status} />
      </header>
      <DetailList
        items={[
          ['Destination', permit.area.name],
          ['Valid from', formatDate(permit.validFrom)],
          ['Valid until', formatDate(permit.validUntil)],
        ]}
      />
      {showQr ? (
        <QrImage
          load={() => touristApi.permitQrBlob(permit.id)}
          alt={`QR code for permit ${permit.permitNumber}`}
          downloadName={`${permit.permitNumber}-qr.png`}
        />
      ) : (
        <p className="muted small">
          {permit.status === 'ACTIVE'
            ? 'QR code is currently unavailable. Please contact the permit office.'
            : 'QR codes are only shown for active permits.'}
        </p>
      )}
      <div className="row-actions">
        <PermitPdfButton load={() => touristApi.permitPdfBlob(permit.id)} permitNumber={permit.permitNumber} />
      </div>
    </article>
  );
}
