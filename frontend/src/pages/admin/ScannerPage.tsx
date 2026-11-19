import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { areaApi, verificationApi } from '../../api/endpoints';
import { VerificationResultPanel } from '../../components/VerificationResultPanel';
import { Alert, Card, PageHeader, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { VerificationResponse } from '../../types';
import { errorMessage } from '../../utils/errors';
import { extractToken } from '../../utils/qrToken';

const READER_ID = 'qr-reader';
/** Sent when a scanned code is not a permit URL/token, so the attempt is still verified & logged server-side. */
const UNRECOGNIZED = 'unrecognized-code';

type Phase = 'idle' | 'starting' | 'scanning' | 'verifying' | 'result';

function cameraErrorText(err: unknown): string {
  const text = String((err as Error)?.message ?? err);
  if (/NotAllowed|Permission/i.test(text)) return 'Camera permission was denied. Allow camera access in your browser settings, or enter the code manually below.';
  if (/NotFound|no camera|Requested device not found/i.test(text)) return 'No camera was found on this device. Enter the code manually below.';
  if (!window.isSecureContext) return 'The camera requires HTTPS (or localhost). Enter the code manually below.';
  return 'Could not start the camera. Enter the code manually below.';
}

export function ScannerPage() {
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const handledRef = useRef(false);
  const [phase, setPhase] = useState<Phase>('idle');
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [result, setResult] = useState<VerificationResponse | null>(null);
  const [manual, setManual] = useState('');
  const [areaId, setAreaId] = useState('');
  const [attachLocation, setAttachLocation] = useState(false);
  const areas = useAsync(() => areaApi.listActive(), []);
  const resultRef = useRef<HTMLDivElement>(null);

  // On phones the result sits below the camera: bring it into view after each scan.
  useEffect(() => {
    if (phase === 'result') resultRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [phase, result]);

  const stopCamera = useCallback(async () => {
    const scanner = scannerRef.current;
    scannerRef.current = null;
    if (scanner) {
      try {
        if (scanner.isScanning) await scanner.stop();
        scanner.clear();
      } catch {
        /* already stopped */
      }
    }
  }, []);

  useEffect(() => () => void stopCamera(), [stopCamera]);

  const currentLocation = (): Promise<{ latitude?: number; longitude?: number }> =>
    new Promise((resolve) => {
      if (!attachLocation || !navigator.geolocation) return resolve({});
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve({ latitude: +pos.coords.latitude.toFixed(6), longitude: +pos.coords.longitude.toFixed(6) }),
        () => resolve({}),
        { timeout: 5000, maximumAge: 60_000 },
      );
    });

  const verify = async (scannedText: string) => {
    setPhase('verifying');
    setApiError(null);
    setResult(null);
    const token = extractToken(scannedText) ?? UNRECOGNIZED;
    try {
      const location = await currentLocation();
      const res = await verificationApi.verify(token, { areaId: areaId ? Number(areaId) : undefined, ...location });
      setResult(res);
      navigator.vibrate?.(res.valid ? 120 : [80, 60, 80]);
      setPhase('result');
    } catch (err) {
      setApiError(errorMessage(err, 'Verification failed. Please try again.'));
      setPhase('idle');
    }
  };

  const startCamera = async () => {
    setCameraError(null);
    setApiError(null);
    setResult(null);
    handledRef.current = false;
    setPhase('starting');
    try {
      const scanner = new Html5Qrcode(READER_ID, {
        formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE],
        verbose: false,
      });
      scannerRef.current = scanner;
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: (w, h) => { const s = Math.floor(Math.min(w, h) * 0.7); return { width: s, height: s }; } },
        (decodedText) => {
          if (handledRef.current) return; // ignore repeated frames of the same code
          handledRef.current = true;
          void stopCamera().then(() => verify(decodedText));
        },
        () => { /* no QR in this frame */ },
      );
      setPhase('scanning');
    } catch (err) {
      await stopCamera();
      setCameraError(cameraErrorText(err));
      setPhase('idle');
    }
  };

  const submitManual = async (e: FormEvent) => {
    e.preventDefault();
    if (!manual.trim()) return;
    await stopCamera();
    await verify(manual);
    setManual('');
  };

  const scanning = phase === 'scanning' || phase === 'starting';

  return (
    <>
      <PageHeader title="QR Scanner" subtitle="Scan a tourist's permit QR code to verify it" />

      <div className="scanner-layout">
        <Card>
          <div className="scanner-options">
            <label className="field">
              <span className="field-label">Checkpoint area (optional)</span>
              <select value={areaId} onChange={(e) => setAreaId(e.target.value)} disabled={scanning}>
                <option value="">Any area</option>
                {areas.data?.map((a) => (
                  <option key={a.id} value={a.id}>{a.name}</option>
                ))}
              </select>
            </label>
            <label className="check">
              <input type="checkbox" checked={attachLocation} onChange={(e) => setAttachLocation(e.target.checked)} />
              Attach my location (stored only if enabled by the system)
            </label>
          </div>

          <div className={`reader ${scanning ? 'reader-on' : ''}`}>
            <div id={READER_ID} />
            {!scanning && phase !== 'verifying' && (
              <div className="reader-placeholder">
                <span aria-hidden>▣</span>
                <p>Camera is off</p>
              </div>
            )}
            {phase === 'verifying' && <div className="reader-placeholder"><Spinner label="Verifying…" /></div>}
          </div>

          <div className="scanner-actions">
            {scanning ? (
              <button className="btn btn-ghost btn-lg" onClick={() => { void stopCamera(); setPhase('idle'); }}>
                Stop camera
              </button>
            ) : (
              <button className="btn btn-primary btn-lg" onClick={startCamera} disabled={phase === 'verifying'}>
                {phase === 'result' ? 'Scan next QR' : 'Start camera & scan'}
              </button>
            )}
          </div>
          <Alert>{cameraError}</Alert>

          <form className="manual-entry" onSubmit={submitManual}>
            <label className="field">
              <span className="field-label">Or enter the code / verification URL manually</span>
              <input value={manual} onChange={(e) => setManual(e.target.value)} placeholder="https://…/verify/…"
                maxLength={300} autoComplete="off" spellCheck={false} />
            </label>
            <button className="btn btn-ghost" disabled={!manual.trim() || phase === 'verifying'}>Verify</button>
          </form>
        </Card>

        <div ref={resultRef} className="scanner-result">
          <Alert>{apiError}</Alert>
          {result ? (
            <VerificationResultPanel result={result} />
          ) : (
            <Card className="result-placeholder">
              <p className="muted">The verification result will appear here.</p>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}
