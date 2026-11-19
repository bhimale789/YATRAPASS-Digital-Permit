import { useEffect, useState } from 'react';
import { AreaArt } from './AreaArt';

/**
 * Destination picture: the admin-uploaded photo when there is one, otherwise (or if the
 * photo fails to load) the generated landscape for the area code.
 */
export function AreaImage({ code, photoUrl, alt }: { code: string; photoUrl?: string | null; alt: string }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [photoUrl]);

  if (photoUrl && !failed) {
    return <img className="area-photo" src={photoUrl} alt={alt} loading="lazy" decoding="async" onError={() => setFailed(true)} />;
  }
  return <AreaArt seed={code} />;
}
