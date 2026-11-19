import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useEffect, useRef } from 'react';
import type { Coordinate, DrivingRoute, GeometryType, RoadCondition, RoadStatus } from '../types';
import { formatDateTime, humanize } from '../utils/format';

export const ROAD_STATUS_COLOR: Record<RoadStatus, string> = {
  OPEN: '#16a34a',
  CAUTION: '#d97706',
  PARTIALLY_BLOCKED: '#ea580c',
  CLOSED: '#dc2626',
};

const NEPAL_CENTER: L.LatLngExpression = [28.3, 84.1];
const TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

interface Props {
  conditions: RoadCondition[];
  height?: number;
  selectedId?: number | null;
  onSelect?: (id: number) => void;
  /** In-progress geometry while an admin is drawing. */
  draft?: { geometryType: GeometryType; coordinates: Coordinate[] } | null;
  onMapClick?: (point: Coordinate) => void;
  /** When this value changes, the view re-fits to the shown conditions. */
  fitKey?: unknown;
  /** Driving route drawn beneath the conditions; the view fits to it when it changes. */
  route?: DrivingRoute | null;
}

/** Builds popup content with DOM text nodes (never HTML strings) so admin text cannot inject markup. */
function popupContent(c: RoadCondition): HTMLElement {
  const root = document.createElement('div');
  root.className = 'road-popup';
  const status = document.createElement('span');
  status.className = 'road-popup-status';
  status.style.background = ROAD_STATUS_COLOR[c.status];
  status.textContent = humanize(c.status);
  const title = document.createElement('strong');
  title.textContent = c.title;
  root.append(status, title);
  if (c.area) {
    const area = document.createElement('div');
    area.className = 'road-popup-meta';
    area.textContent = c.area.name;
    root.append(area);
  }
  if (c.description) {
    const desc = document.createElement('p');
    desc.textContent = c.description;
    root.append(desc);
  }
  const updated = document.createElement('div');
  updated.className = 'road-popup-meta';
  updated.textContent = `Updated ${formatDateTime(c.updatedAt)}${c.active ? '' : ' · hidden from public'}`;
  root.append(updated);
  return root;
}

/** OpenStreetMap (Leaflet) map of road conditions: colour-coded road segments and points. */
export function RoadMap({ conditions, height = 380, selectedId, onSelect, draft, onMapClick, fitKey, route }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const conditionLayer = useRef<L.LayerGroup | null>(null);
  const draftLayer = useRef<L.LayerGroup | null>(null);
  const routeLayer = useRef<L.LayerGroup | null>(null);
  const clickRef = useRef(onMapClick);
  const selectRef = useRef(onSelect);
  const fittedFor = useRef<unknown>(Symbol('unfitted'));
  clickRef.current = onMapClick;
  selectRef.current = onSelect;

  // Create the map once.
  useEffect(() => {
    if (!container.current) return;
    const map = L.map(container.current, { center: NEPAL_CENTER, zoom: 7, scrollWheelZoom: false, worldCopyJump: true });
    L.tileLayer(TILE_URL, {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors',
      // OSM's tile usage policy requires a Referer; the site default is no-referrer.
      referrerPolicy: 'strict-origin-when-cross-origin',
    } as L.TileLayerOptions).addTo(map);
    routeLayer.current = L.layerGroup().addTo(map); // added first so conditions draw on top
    conditionLayer.current = L.layerGroup().addTo(map);
    draftLayer.current = L.layerGroup().addTo(map);
    map.on('click', (e: L.LeafletMouseEvent) => clickRef.current?.({ lat: +e.latlng.lat.toFixed(6), lng: +e.latlng.lng.toFixed(6) }));
    // Enable wheel zoom only after the user interacts with the map, so page scrolling is not hijacked.
    map.on('focus click', () => map.scrollWheelZoom.enable());
    map.on('blur mouseout', () => map.scrollWheelZoom.disable());
    mapRef.current = map;

    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(container.current);
    return () => {
      observer.disconnect();
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // Render conditions.
  useEffect(() => {
    const map = mapRef.current;
    const layer = conditionLayer.current;
    if (!map || !layer) return;
    layer.clearLayers();
    const bounds = L.latLngBounds([]);

    conditions.forEach((c) => {
      const color = ROAD_STATUS_COLOR[c.status];
      const selected = c.id === selectedId;
      const latlngs = c.coordinates.map((p) => L.latLng(p.lat, p.lng));
      latlngs.forEach((p) => bounds.extend(p));
      const opacity = c.active ? 1 : 0.45;
      const shape = c.geometryType === 'LINE'
        ? L.polyline(latlngs, { color, weight: selected ? 9 : 6, opacity, lineCap: 'round', dashArray: c.active ? undefined : '6 8' })
        : L.circleMarker(latlngs[0], { radius: selected ? 12 : 9, color: '#ffffff', weight: 3, fillColor: color, fillOpacity: opacity });
      shape.bindPopup(popupContent(c));
      shape.on('click', () => selectRef.current?.(c.id));
      shape.addTo(layer);
      if (selected) shape.openPopup();
    });

    if (fittedFor.current !== fitKey && bounds.isValid()) {
      map.fitBounds(bounds, { padding: [32, 32], maxZoom: 12 });
      fittedFor.current = fitKey;
    }
  }, [conditions, selectedId, fitKey]);

  // Render the driving route and fit the view to it.
  useEffect(() => {
    const map = mapRef.current;
    const layer = routeLayer.current;
    if (!map || !layer) return;
    layer.clearLayers();
    if (!route || route.coordinates.length < 2) return;
    const latlngs = route.coordinates.map((p) => L.latLng(p.lat, p.lng));
    L.polyline(latlngs, { color: '#ffffff', weight: 10, opacity: 0.9, interactive: false }).addTo(layer);
    L.polyline(latlngs, { color: '#2563eb', weight: 6, opacity: 0.9, interactive: false }).addTo(layer);
    const end = (place: DrivingRoute['from'], color: string, label: string) =>
      L.circleMarker([place.lat, place.lng], { radius: 9, color: '#ffffff', weight: 3, fillColor: color, fillOpacity: 1 })
        .bindTooltip(label, { permanent: true, direction: 'top', offset: [0, -8], className: 'route-tip' })
        .addTo(layer);
    end(route.from, '#0f766e', `Start: ${route.from.label}`);
    end(route.to, '#2563eb', route.to.label);
    map.fitBounds(L.latLngBounds(latlngs), { padding: [36, 36] });
  }, [route]);

  // Render the draft geometry while editing.
  useEffect(() => {
    const layer = draftLayer.current;
    if (!layer) return;
    layer.clearLayers();
    if (!draft || draft.coordinates.length === 0) return;
    const latlngs = draft.coordinates.map((p) => L.latLng(p.lat, p.lng));
    if (draft.geometryType === 'LINE' && latlngs.length > 1) {
      L.polyline(latlngs, { color: '#2b8ad8', weight: 5, dashArray: '8 8' }).addTo(layer);
    }
    latlngs.forEach((p, i) => L.circleMarker(p, {
      radius: draft.geometryType === 'POINT' ? 10 : 6, color: '#ffffff', weight: 2,
      fillColor: i === latlngs.length - 1 ? '#2b8ad8' : '#1a5f8f', fillOpacity: 1,
    }).addTo(layer));
  }, [draft]);

  return (
    <div
      ref={container}
      className={`road-map ${onMapClick ? 'road-map-editing' : ''}`}
      style={{ height }}
      role="region"
      aria-label="Road conditions map"
    />
  );
}

export function RoadLegend() {
  return (
    <ul className="road-legend" aria-label="Map legend">
      {(Object.keys(ROAD_STATUS_COLOR) as RoadStatus[]).map((s) => (
        <li key={s}>
          <span className="road-swatch" style={{ background: ROAD_STATUS_COLOR[s] }} />
          {humanize(s)}
        </li>
      ))}
    </ul>
  );
}
