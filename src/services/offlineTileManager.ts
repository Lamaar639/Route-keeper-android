import type { Coordinate } from '../types/route';

export const OFFLINE_CACHE_NAME = 'map-free-tiles-cache';
const IDB_NAME = 'routekeeper-offline-tiles-db';
const IDB_STORE = 'tiles';
const IDB_VERSION = 2;
const PACKS_STORAGE_KEY = 'routekeeper:offline-packs:v1';

export interface BoundingBox {
  minLat: number;
  maxLat: number;
  minLon: number;
  maxLon: number;
}

export interface OfflineMapPack {
  id: string;
  name: string;
  bbox: BoundingBox;
  center?: Coordinate;
  radiusKm?: number;
  zoomMin: number;
  zoomMax: number;
  tileCount: number;
  sizeBytes: number;
  mapService: 'osm' | 'topo' | 'satellite';
  downloadedAt: string;
}

export interface DownloadProgress {
  downloaded: number;
  total: number;
  failed: number;
  percent: number;
  sizeBytes: number;
  currentZoom?: number;
  currentTile?: string;
  currentUrl?: string;
}

export interface TileTarget {
  x: number;
  y: number;
  z: number;
  url: string;
}

// Open IndexedDB database for resilient tile blob storage with coordKey index
function openTileDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      return reject(new Error('IndexedDB not supported in this environment'));
    }
    const req = indexedDB.open(IDB_NAME, IDB_VERSION);
    req.onupgradeneeded = (e: any) => {
      const db: IDBDatabase = req.result;
      let store: IDBObjectStore;
      if (!db.objectStoreNames.contains(IDB_STORE)) {
        store = db.createObjectStore(IDB_STORE, { keyPath: 'url' });
      } else {
        store = (e.currentTarget as any).transaction.objectStore(IDB_STORE);
      }
      if (!store.indexNames.contains('coordKey')) {
        store.createIndex('coordKey', 'coordKey', { unique: false });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

// Convert Lat/Lon to Slippy tile numbers
export function latLonToTile(lat: number, lon: number, zoom: number): { x: number; y: number } {
  const clampedLat = Math.max(-85.05112878, Math.min(85.05112878, lat));
  const latRad = (clampedLat * Math.PI) / 180;
  const n = Math.pow(2, zoom);
  const x = Math.floor(((lon + 180) / 360) * n);
  const y = Math.floor((1 - Math.asinh(Math.tan(latRad)) / Math.PI) / 2 * n);
  return {
    x: Math.max(0, Math.min(n - 1, x)),
    y: Math.max(0, Math.min(n - 1, y)),
  };
}

// Convert tile coordinate to Tile URL for chosen service
export function getTileUrl(x: number, y: number, z: number, mapService: 'osm' | 'topo' | 'satellite' = 'osm'): string {
  if (mapService === 'satellite') {
    return `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/${z}/${y}/${x}`;
  }
  if (mapService === 'topo') {
    return `https://tile.opentopomap.org/${z}/${x}/${y}.png`;
  }
  // OpenStreetMap standard community tiles (100% free open-source, no API keys)
  return `https://tile.openstreetmap.org/${z}/${x}/${y}.png`;
}

// Calculate bounding box from center and radius
export function getBoundingBox(lat: number, lon: number, radiusKm: number): BoundingBox {
  const latDelta = radiusKm / 110.574;
  const cosLat = Math.cos((lat * Math.PI) / 180);
  const lonDelta = radiusKm / (111.32 * Math.max(0.2, Math.abs(cosLat)));
  return {
    minLat: Math.max(-85.0511, lat - latDelta),
    maxLat: Math.min(85.0511, lat + latDelta),
    minLon: Math.max(-180, lon - lonDelta),
    maxLon: Math.min(180, lon + lonDelta),
  };
}

/**
 * Compute all required tiles for a selected map bounding box across a zoom range.
 * Default ranges from zoom 5 (continental/regional overview) to zoom 18 (street/lane detail)
 */
export function computeBoundingBoxTiles(
  bbox: BoundingBox,
  zoomMin: number = 5,
  zoomMax: number = 17,
  mapService: 'osm' | 'topo' | 'satellite' = 'osm'
): TileTarget[] {
  const tiles: TileTarget[] = [];
  const seen = new Set<string>();

  const effectiveZoomMax = mapService === 'topo' ? Math.min(17, zoomMax) : zoomMax;

  for (let z = zoomMin; z <= effectiveZoomMax; z++) {
    const topLeft = latLonToTile(bbox.maxLat, bbox.minLon, z);
    const bottomRight = latLonToTile(bbox.minLat, bbox.maxLon, z);

    const minX = Math.min(topLeft.x, bottomRight.x);
    const maxX = Math.max(topLeft.x, bottomRight.x);
    const minY = Math.min(topLeft.y, bottomRight.y);
    const maxY = Math.max(topLeft.y, bottomRight.y);

    for (let x = minX; x <= maxX; x++) {
      for (let y = minY; y <= maxY; y++) {
        const key = `${z}/${x}/${y}`;
        if (!seen.has(key)) {
          seen.add(key);
          tiles.push({
            x,
            y,
            z,
            url: getTileUrl(x, y, z, mapService),
          });
        }
      }
    }
  }

  return tiles;
}

/**
 * Generate tile targets from center & radius
 */
export function computeAreaTiles(
  center: Coordinate,
  radiusKm: number,
  zoomMin: number = 5,
  zoomMax: number = 18,
  mapService: 'osm' | 'topo' | 'satellite' = 'osm'
): TileTarget[] {
  const bbox = getBoundingBox(center.latitude, center.longitude, radiusKm);
  return computeBoundingBoxTiles(bbox, zoomMin, zoomMax, mapService);
}

// In-memory cache of object URLs for instant synchronous tile retrieval
const objectUrlCache = new Map<string, string>();

/**
 * Flush in-memory object URL cache (called when switching map layers)
 */
export function clearObjectUrlCache(): void {
  objectUrlCache.clear();
}

/**
 * Detect map layer service from tile URL
 */
export function detectMapServiceFromUrl(url: string): 'osm' | 'satellite' | 'topo' {
  if (url.includes('arcgisonline.com') || url.includes('World_Imagery')) {
    return 'satellite';
  }
  if (url.includes('opentopomap.org')) {
    return 'topo';
  }
  return 'osm';
}

/**
 * Retrieve offline tile blob as an Object URL from CacheStorage or IndexedDB
 * Supports URL match and service-scoped coordinate index match.
 */
export async function getOfflineTileBlobUrl(
  url: string,
  coords?: { x: number; y: number; z: number },
  mapService?: string
): Promise<string | null> {
  const expectedService = mapService || detectMapServiceFromUrl(url);

  // 1. Check in-memory object URL cache first
  if (objectUrlCache.has(url)) {
    return objectUrlCache.get(url)!;
  }

  // 2. Check Cache Storage API
  if (typeof caches !== 'undefined') {
    try {
      const cache = await caches.open(OFFLINE_CACHE_NAME);
      const match = await cache.match(url);
      if (match) {
        const blob = await match.blob();
        if (blob && blob.size > 0) {
          const objUrl = URL.createObjectURL(blob);
          objectUrlCache.set(url, objUrl);
          return objUrl;
        }
      }
    } catch {
      // ignore
    }
  }

  // 3. Check IndexedDB fallback (by direct URL or composite coordKey)
  try {
    const db = await openTileDB();

    // Check by exact URL
    let blob: Blob | null = await new Promise<Blob | null>((resolve) => {
      const tx = db.transaction(IDB_STORE, 'readonly');
      const store = tx.objectStore(IDB_STORE);
      const req = store.get(url);
      req.onsuccess = () => {
        if (req.result && req.result.blob) {
          const resultService = req.result.mapService || detectMapServiceFromUrl(req.result.url || '');
          if (resultService === expectedService) {
            resolve(req.result.blob);
            return;
          }
        }
        resolve(null);
      };
      req.onerror = () => resolve(null);
    });

    // If not found by exact URL, check service-scoped coordKey index
    if (!blob && coords) {
      const maxCoord = Math.pow(2, coords.z);
      const normX = ((coords.x % maxCoord) + maxCoord) % maxCoord;
      const normY = Math.max(0, Math.min(maxCoord - 1, coords.y));
      const scopedCoordKey = `${expectedService}:${coords.z}/${normX}/${normY}`;
      const legacyCoordKey = `${coords.z}/${normX}/${normY}`;

      blob = await new Promise<Blob | null>((resolve) => {
        try {
          const tx = db.transaction(IDB_STORE, 'readonly');
          const store = tx.objectStore(IDB_STORE);
          if (store.indexNames.contains('coordKey')) {
            const index = store.index('coordKey');
            
            // First check scoped key
            const reqScoped = index.get(scopedCoordKey);
            reqScoped.onsuccess = () => {
              if (reqScoped.result && reqScoped.result.blob) {
                const resultService = reqScoped.result.mapService || detectMapServiceFromUrl(reqScoped.result.url || '');
                if (resultService === expectedService) {
                  resolve(reqScoped.result.blob);
                  return;
                }
              }

              // Fallback check legacy key ONLY IF the stored tile belongs to the expected service
              const reqLegacy = index.get(legacyCoordKey);
              reqLegacy.onsuccess = () => {
                if (reqLegacy.result && reqLegacy.result.blob) {
                  const resultService = reqLegacy.result.mapService || detectMapServiceFromUrl(reqLegacy.result.url || '');
                  if (resultService === expectedService) {
                    resolve(reqLegacy.result.blob);
                    return;
                  }
                }
                resolve(null);
              };
              reqLegacy.onerror = () => resolve(null);
            };
            reqScoped.onerror = () => resolve(null);
          } else {
            resolve(null);
          }
        } catch {
          resolve(null);
        }
      });
    }

    if (blob && blob.size > 0) {
      const objUrl = URL.createObjectURL(blob);
      objectUrlCache.set(url, objUrl);
      return objUrl;
    }
  } catch {
    // ignore
  }

  return null;
}

/**
 * Store a tile blob in both CacheStorage and IndexedDB (with service-scoped coordKey for resilient retrieval)
 */
export async function storeTileBlob(
  url: string,
  blob: Blob,
  mapService?: string,
  z?: number,
  x?: number,
  y?: number
): Promise<void> {
  const service = mapService || detectMapServiceFromUrl(url);

  // Store in CacheStorage
  if (typeof caches !== 'undefined') {
    try {
      const cache = await caches.open(OFFLINE_CACHE_NAME);
      const response = new Response(blob, {
        headers: {
          'Content-Type': blob.type || 'image/png',
          'Cache-Control': 'public, max-age=2592000', // 30 days
        },
      });
      await cache.put(url, response);
    } catch {
      // ignore
    }
  }

  // Store in IndexedDB
  try {
    const db = await openTileDB();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(IDB_STORE, 'readwrite');
      const store = tx.objectStore(IDB_STORE);

      // Compute service-scoped coordKey if z, x, y are available
      let coordKey: string | undefined;
      if (z !== undefined && x !== undefined && y !== undefined) {
        const maxCoord = Math.pow(2, z);
        const normX = ((x % maxCoord) + maxCoord) % maxCoord;
        const normY = Math.max(0, Math.min(maxCoord - 1, y));
        coordKey = `${service}:${z}/${normX}/${normY}`;
      } else {
        const match = url.match(/\/(\d+)\/(\d+)\/(\d+)(?:\.png|\.jpg|\/tile)/);
        if (match) {
          coordKey = `${service}:${match[1]}/${match[2]}/${match[3]}`;
        }
      }

      store.put({
        url,
        coordKey: coordKey || `${service}:${url}`,
        z,
        x,
        y,
        mapService: service,
        blob,
        mimeType: blob.type || 'image/png',
        timestamp: Date.now(),
      });
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // ignore
  }
}

/**
 * Generate a clean offline placeholder tile data URL when tile is not downloaded
 */
export function getOfflinePlaceholderTile(z: number, x: number, y: number): string {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">
      <rect width="256" height="256" fill="#EDEFE8" stroke="#DDE3D7" stroke-width="1"/>
      <line x1="0" y1="0" x2="256" y2="256" stroke="#DDE3D7" stroke-width="0.75" stroke-dasharray="4,4"/>
      <line x1="256" y1="0" x2="0" y2="256" stroke="#DDE3D7" stroke-width="0.75" stroke-dasharray="4,4"/>
      <rect x="24" y="96" width="208" height="64" rx="12" fill="#FFFFFF" fill-opacity="0.92" stroke="#CBD5E1" stroke-width="1"/>
      <text x="128" y="122" font-family="system-ui, sans-serif" font-size="11" font-weight="700" fill="#1E453B" text-anchor="middle">
        Offline Map Tile
      </text>
      <text x="128" y="142" font-family="system-ui, sans-serif" font-size="9" font-weight="600" fill="#64748B" text-anchor="middle">
        Tile z:${z} x:${x} y:${y}
      </text>
    </svg>
  `;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg.trim())}`;
}

/**
 * Batch download tiles for a selected map area ranging from Zoom 5 to the highest available zoom
 */
export async function downloadTerritoryOfflinePack(options: {
  name: string;
  bbox?: BoundingBox;
  center?: Coordinate;
  radiusKm?: number;
  zoomMin?: number;
  zoomMax?: number;
  mapService?: 'osm' | 'topo' | 'satellite';
  onProgress?: (progress: DownloadProgress) => void;
  signal?: AbortSignal;
}): Promise<OfflineMapPack> {
  const {
    name,
    bbox: customBbox,
    center,
    radiusKm = 10,
    zoomMin = 5,
    zoomMax = 17,
    mapService = 'osm',
    onProgress,
    signal,
  } = options;

  const bbox: BoundingBox = customBbox || (center ? getBoundingBox(center.latitude, center.longitude, radiusKm) : {
    minLat: 51.40,
    maxLat: 51.60,
    minLon: -0.30,
    maxLon: 0.10,
  });

  const effectiveZoomMax = mapService === 'topo' ? Math.min(17, zoomMax) : zoomMax;
  const tiles = computeBoundingBoxTiles(bbox, zoomMin, effectiveZoomMax, mapService);
  const total = tiles.length;

  let downloaded = 0;
  let failed = 0;
  let sizeBytes = 0;

  // Concurrency limit to avoid overwhelming open-source servers and respect usage guidelines
  const CONCURRENCY = 6;

  const downloadTile = async (tile: TileTarget) => {
    if (signal?.aborted) throw new Error('Download aborted by user');

    // Check if already in cache
    const existing = await getOfflineTileBlobUrl(tile.url, { x: tile.x, y: tile.y, z: tile.z }, mapService);
    if (existing) {
      downloaded++;
      sizeBytes += 18400; // approximate 18KB
      onProgress?.({
        downloaded,
        total,
        failed,
        percent: Math.round((downloaded / total) * 100),
        sizeBytes,
        currentZoom: tile.z,
        currentTile: tile.url,
      });
      return;
    }

    try {
      const response = await fetch(tile.url, {
        mode: 'cors',
        signal,
        headers: {
          Accept: 'image/png,image/jpeg,image/*;q=0.8',
        },
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const blob = await response.blob();
      sizeBytes += blob.size;
      await storeTileBlob(tile.url, blob, mapService, tile.z, tile.x, tile.y);
      downloaded++;
    } catch (err: any) {
      if (signal?.aborted) throw err;
      failed++;
    } finally {
      onProgress?.({
        downloaded: downloaded + failed,
        total,
        failed,
        percent: Math.min(100, Math.round(((downloaded + failed) / total) * 100)),
        sizeBytes,
        currentZoom: tile.z,
        currentTile: tile.url,
      });
    }
  };

  // Process in small batches with slight pacing
  for (let i = 0; i < tiles.length; i += CONCURRENCY) {
    if (signal?.aborted) throw new Error('Download aborted by user');
    const batch = tiles.slice(i, i + CONCURRENCY);
    await Promise.all(batch.map((t) => downloadTile(t)));
    // Polite 15ms pause between batches
    await new Promise((r) => setTimeout(r, 15));
  }

  const derivedCenter: Coordinate = center || {
    latitude: (bbox.minLat + bbox.maxLat) / 2,
    longitude: (bbox.minLon + bbox.maxLon) / 2,
  };

  const pack: OfflineMapPack = {
    id: `pack-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    name,
    bbox,
    center: derivedCenter,
    radiusKm,
    zoomMin,
    zoomMax: effectiveZoomMax,
    tileCount: downloaded,
    sizeBytes,
    mapService,
    downloadedAt: new Date().toISOString(),
  };

  saveOfflinePackRecord(pack);
  return pack;
}

/**
 * Save offline pack metadata to local storage
 */
export function saveOfflinePackRecord(pack: OfflineMapPack) {
  try {
    const raw = localStorage.getItem(PACKS_STORAGE_KEY);
    const packs: OfflineMapPack[] = raw ? JSON.parse(raw) : [];
    // Replace if same name
    const filtered = packs.filter((p) => p.name !== pack.name);
    filtered.unshift(pack);
    localStorage.setItem(PACKS_STORAGE_KEY, JSON.stringify(filtered));
  } catch {
    // ignore
  }
}

/**
 * List all saved offline territory packs
 */
export function getSavedOfflinePacks(): OfflineMapPack[] {
  try {
    const raw = localStorage.getItem(PACKS_STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

/**
 * Delete an offline territory pack record
 */
export function deleteOfflinePackRecord(packId: string) {
  try {
    const packs = getSavedOfflinePacks().filter((p) => p.id !== packId);
    localStorage.setItem(PACKS_STORAGE_KEY, JSON.stringify(packs));
  } catch {
    // ignore
  }
}

/**
 * Get total offline storage statistics
 */
export async function getOfflineStorageStats(): Promise<{
  tileCount: number;
  sizeMB: number;
  packs: OfflineMapPack[];
}> {
  const packs = getSavedOfflinePacks();
  let tileCount = 0;
  let totalBytes = 0;

  // Count CacheStorage
  if (typeof caches !== 'undefined') {
    try {
      const cache = await caches.open(OFFLINE_CACHE_NAME);
      const keys = await cache.keys();
      tileCount = keys.length;
      totalBytes = tileCount * 18400;
    } catch {
      // ignore
    }
  }

  // Fallback to IndexedDB count if caches was 0
  if (tileCount === 0) {
    try {
      const db = await openTileDB();
      const count = await new Promise<number>((resolve) => {
        const tx = db.transaction(IDB_STORE, 'readonly');
        const store = tx.objectStore(IDB_STORE);
        const req = store.count();
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => resolve(0);
      });
      tileCount = count;
      totalBytes = count * 18400;
    } catch {
      // ignore
    }
  }

  // Also include recorded packs size
  const packBytes = packs.reduce((acc, p) => acc + (p.sizeBytes || 0), 0);
  const effectiveBytes = Math.max(totalBytes, packBytes);
  const sizeMB = Number((effectiveBytes / (1024 * 1024)).toFixed(1));

  return {
    tileCount,
    sizeMB,
    packs,
  };
}

/**
 * Clear all offline map tiles and packs
 */
export async function clearAllOfflineTiles(): Promise<void> {
  // Clear CacheStorage
  if (typeof caches !== 'undefined') {
    try {
      await caches.delete(OFFLINE_CACHE_NAME);
    } catch {
      // ignore
    }
  }

  // Clear IndexedDB
  try {
    const db = await openTileDB();
    await new Promise<void>((resolve) => {
      const tx = db.transaction(IDB_STORE, 'readwrite');
      const store = tx.objectStore(IDB_STORE);
      store.clear();
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
    });
  } catch {
    // ignore
  }

  // Clear Object URLs
  objectUrlCache.forEach((objUrl) => URL.revokeObjectURL(objUrl));
  objectUrlCache.clear();

  // Clear localStorage records
  try {
    localStorage.removeItem(PACKS_STORAGE_KEY);
  } catch {
    // ignore
  }
}
