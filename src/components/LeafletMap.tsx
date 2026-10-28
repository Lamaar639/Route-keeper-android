import React, { useEffect, useRef, useState, useMemo } from 'react';
import L from 'leaflet';
import { Sparkles, Maximize2, Minimize2, Play, Pause, RotateCcw, Download, WifiOff } from 'lucide-react';
import type { Coordinate, DeliveryStop } from '../types/route';
import {
  getOfflineTileBlobUrl,
  getOfflinePlaceholderTile,
  storeTileBlob,
  clearObjectUrlCache,
} from '../services/offlineTileManager';

interface LeafletMapProps {
  stops: DeliveryStop[];
  path?: Coordinate[];
  paths?: Coordinate[][];
  markerColor: string;
  markerTextColor?: string;
  lineColor: string;
  lineWidth: number;
  height?: number | string;
  exportRequest?: number;
  onExport?: (dataUrl: string) => void;
  onError?: (message: string) => void;
  onSelectStop?: (stop: DeliveryStop) => void;
  onToggleDelivery?: (stopId: string, currentStatus: boolean) => void;
  onNavigate?: (stop: DeliveryStop) => void;
  onOpenDetails?: (stop: DeliveryStop) => void;
  // Driving Simulation Animation props
  isAnimating?: boolean;
  animationSpeed?: number;
  onAnimationProgress?: (percent: number, nearestStopIndex: number, currentCoord: Coordinate) => void;
  onAnimationFinished?: () => void;
  // Route Line Drawing Animation (draws the lines in when generating/loading route)
  animateDrawRoute?: boolean;
  drawKey?: string | number;
  onDrawFinished?: () => void;
  // Quiet Wait Stops & Parking Pins
  waitStops?: import('../types/route').QuietWaitStop[];
  onNavigateWaitStop?: (waitStop: import('../types/route').QuietWaitStop) => void;
  // Offline Map Tile Manager Modal Trigger
  onOpenOfflineModal?: () => void;
  // Map Layer preference and sync
  currentLayer?: 'osm' | 'satellite' | 'topo';
  onLayerChange?: (layer: 'osm' | 'satellite' | 'topo') => void;
  className?: string;
}

function mercator(point: Coordinate, zoom: number) {
  const latitude = Math.max(-85.05112878, Math.min(85.05112878, point.latitude));
  const scale = 256 * Math.pow(2, zoom);
  const sin = Math.sin((latitude * Math.PI) / 180);
  return {
    x: ((point.longitude + 180) / 360) * scale,
    y: (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * scale,
  };
}

async function tileImage(x: number, y: number, zoom: number): Promise<{ image: HTMLImageElement; x: number; y: number }> {
  const onlineUrl = `https://tile.openstreetmap.org/${zoom}/${x}/${y}.png`;
  const cachedBlobUrl = await getOfflineTileBlobUrl(onlineUrl, { x, y, z: zoom });
  const src = cachedBlobUrl || onlineUrl;

  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => resolve({ image, x, y });
    image.onerror = () => {
      image.onerror = () => reject(new Error('A map tile did not load.'));
      image.src = getOfflinePlaceholderTile(zoom, x, y);
    };
    image.src = src;
  });
}

function haversineDist(c1: Coordinate, c2: Coordinate): number {
  const R = 6371000;
  const dLat = ((c2.latitude - c1.latitude) * Math.PI) / 180;
  const dLon = ((c2.longitude - c1.longitude) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((c1.latitude * Math.PI) / 180) *
      Math.cos((c2.latitude * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function calculateBearing(c1: Coordinate, c2: Coordinate): number {
  const lat1 = (c1.latitude * Math.PI) / 180;
  const lat2 = (c2.latitude * Math.PI) / 180;
  const dLon = ((c2.longitude - c1.longitude) * Math.PI) / 180;
  const y = Math.sin(dLon) * Math.cos(lat2);
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
  const radians = Math.atan2(y, x);
  return ((radians * 180) / Math.PI + 360) % 360;
}

function escapeHtml(str: string): string {
  return (str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function createPinHtml(index: number, isDelivered: boolean, color: string, textColor: string): string {
  const bg = isDelivered ? '#10B981' : color;
  const border = isDelivered ? '#D1FAE5' : '#FFFFFF';
  return `
    <div class="rk-map-pin" style="
      width: 36px;
      height: 42px;
      background-color: ${bg};
      color: ${textColor};
      border: 2.5px solid ${border};
      border-radius: 20px 20px 20px 4px;
      transform: rotate(-45deg);
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 14px rgba(18, 40, 34, 0.42);
      cursor: pointer;
      user-select: none;
    ">
      <span style="
        transform: rotate(45deg);
        font-weight: 800;
        font-size: 13px;
        font-family: system-ui, -apple-system, sans-serif;
        line-height: 1;
        text-align: center;
        color: ${textColor};
      ">${isDelivered ? '✓' : index + 1}</span>
    </div>
  `;
}

function buildPopupHtml(stop: DeliveryStop, index: number): string {
  const isDelivered = !!stop.delivered;
  const safeName = escapeHtml(stop.name);
  const safeAddress = escapeHtml(stop.address);
  const safePhone = stop.phone ? escapeHtml(stop.phone) : '';

  return `
    <div style="min-width: 220px; font-family: system-ui, -apple-system, sans-serif; padding: 4px;">
      <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px;">
        <div style="display: flex; align-items: center; gap: 6px; min-width: 0;">
          <span style="background: ${isDelivered ? '#10B981' : '#1E453B'}; color: white; font-weight: 800; font-size: 11px; padding: 2px 7px; border-radius: 6px; flex-shrink: 0;">
            #${index + 1}
          </span>
          <span style="font-weight: 700; font-size: 13px; color: #1A2E27; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
            ${safeName}
          </span>
        </div>
        <span style="font-size: 10px; font-weight: 700; padding: 2px 7px; border-radius: 999px; background: ${isDelivered ? '#D1FAE5' : '#E7ECE2'}; color: ${isDelivered ? '#065F46' : '#1E453B'}; flex-shrink: 0;">
          ${isDelivered ? '✓ Delivered' : 'Pending'}
        </span>
      </div>

      <div style="font-size: 12px; color: #53635d; margin-bottom: 8px; line-height: 1.4;">
        📍 ${safeAddress}
      </div>

      ${
        safePhone
          ? `<div style="font-size: 11px; margin-bottom: 10px;">
              <a href="tel:${safePhone}" style="color: #1E453B; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 4px;">
                📞 ${safePhone}
              </a>
            </div>`
          : ''
      }

      <div style="display: flex; gap: 6px; border-top: 1px solid #E7ECE2; padding-top: 8px; margin-top: 4px;">
        <button
          onclick="window.__RK_TOGGLE_DELIVERY && window.__RK_TOGGLE_DELIVERY('${stop.id}', ${isDelivered})"
          style="flex: 1; padding: 6px 10px; font-size: 11px; font-weight: 700; border-radius: 8px; border: none; cursor: pointer; background: ${isDelivered ? '#E7ECE2' : '#10B981'}; color: ${isDelivered ? '#1E453B' : 'white'};"
        >
          ${isDelivered ? 'Mark Pending' : '✓ Mark Delivered'}
        </button>
        <button
          onclick="window.__RK_NAVIGATE && window.__RK_NAVIGATE('${stop.id}')"
          style="padding: 6px 10px; font-size: 11px; font-weight: 700; border-radius: 8px; border: 1px solid #DDE3D7; background: white; color: #1E453B; cursor: pointer;"
          title="Navigate"
        >
          🧭 Nav
        </button>
        <button
          onclick="window.__RK_DETAILS && window.__RK_DETAILS('${stop.id}')"
          style="padding: 6px 8px; font-size: 11px; font-weight: 600; border-radius: 8px; border: 1px solid #DDE3D7; background: #F3F5ED; color: #1A2E27; cursor: pointer;"
          title="View Details"
        >
          Details
        </button>
      </div>
    </div>
  `;
}

function createOfflineAwareTileLayer(
  url: string,
  options: L.TileLayerOptions,
  mapService: 'osm' | 'satellite' | 'topo' = 'osm'
): L.TileLayer {
  const OfflineTileLayer = (L.TileLayer as any).extend({
    createTile(coords: L.Coords, done: (error: Error | null, tile: HTMLElement) => void) {
      const tile = document.createElement('img');
      tile.alt = '';
      tile.setAttribute('role', 'presentation');

      const tileUrl = this.getTileUrl(coords);
      const isSimulatedOffline =
        typeof window !== 'undefined' && localStorage.getItem('routekeeper:simulated-offline') === 'true';
      const isActuallyOffline = typeof navigator !== 'undefined' && !navigator.onLine;
      const isOffline = isSimulatedOffline || isActuallyOffline;

      (async () => {
        try {
          const cachedBlobUrl = await getOfflineTileBlobUrl(tileUrl, { x: coords.x, y: coords.y, z: coords.z }, mapService);
          if (cachedBlobUrl) {
            tile.onload = () => done(null, tile);
            tile.onerror = () => {
              tile.src = getOfflinePlaceholderTile(coords.z, coords.x, coords.y);
              done(null, tile);
            };
            tile.src = cachedBlobUrl;
            return;
          }

          if (isOffline) {
            tile.src = getOfflinePlaceholderTile(coords.z, coords.x, coords.y);
            done(null, tile);
            return;
          }

          tile.onload = () => {
            done(null, tile);
            // Auto-cache tiles visited online into offline storage for instant subsequent offline access
            fetch(tileUrl)
              .then((res) => (res.ok ? res.blob() : null))
              .then((blob) => {
                if (blob) {
                  storeTileBlob(tileUrl, blob, mapService, coords.z, coords.x, coords.y);
                }
              })
              .catch(() => {});
          };
          tile.onerror = () => {
            tile.src = getOfflinePlaceholderTile(coords.z, coords.x, coords.y);
            done(null, tile);
          };
          tile.src = tileUrl;
        } catch {
          tile.src = isOffline ? getOfflinePlaceholderTile(coords.z, coords.x, coords.y) : tileUrl;
          done(null, tile);
        }
      })();

      return tile;
    },
  });

  return new (OfflineTileLayer as any)(url, options);
}

export default function LeafletMap({
  stops,
  path = [],
  paths,
  markerColor,
  markerTextColor = '#FFFFFF',
  lineColor,
  lineWidth,
  height = 370,
  exportRequest = 0,
  onExport,
  onError,
  onSelectStop,
  onToggleDelivery,
  onNavigate,
  onOpenDetails,
  isAnimating = false,
  animationSpeed = 1,
  onAnimationProgress,
  onAnimationFinished,
  animateDrawRoute = true,
  drawKey,
  onDrawFinished,
  waitStops,
  onNavigateWaitStop,
  onOpenOfflineModal,
  currentLayer,
  onLayerChange,
  className = '',
}: LeafletMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const mapLoadedRef = useRef<boolean>(false);
  const markersLayerRef = useRef<L.LayerGroup | null>(null);
  const routeLayerRef = useRef<L.LayerGroup | null>(null);
  const animLayerRef = useRef<L.LayerGroup | null>(null);
  const animVehicleMarkerRef = useRef<L.Marker | null>(null);
  const animTrailPolylineRef = useRef<L.Polyline | null>(null);
  const animationFrameIdRef = useRef<number | null>(null);
  const drawAnimationFrameRef = useRef<number | null>(null);

  // Markers cache: stopId -> L.Marker
  const markersMapRef = useRef<Map<string, L.Marker>>(new Map());
  const prevStopsSignatureRef = useRef<string>('');

  // Route drawing polyline references for smooth slider seeking
  const casingPolylineRef = useRef<L.Polyline | null>(null);
  const corePolylineRef = useRef<L.Polyline | null>(null);
  const headMarkerRef = useRef<L.Marker | null>(null);

  // Animation persistence across re-renders
  const animProgressDistRef = useRef<number>(0);
  const wasAnimatingRef = useRef<boolean>(false);
  const lastPercentReportedRef = useRef<number>(-1);
  const lastStopIdxReportedRef = useRef<number>(-1);

  // Keep callback refs stable
  const onAnimationProgressRef = useRef(onAnimationProgress);
  onAnimationProgressRef.current = onAnimationProgress;
  const onAnimationFinishedRef = useRef(onAnimationFinished);
  onAnimationFinishedRef.current = onAnimationFinished;
  const onDrawFinishedRef = useRef(onDrawFinished);
  onDrawFinishedRef.current = onDrawFinished;

  const [mapError, setMapError] = useState<string | null>(null);
  const [selectedQuickStop, setSelectedQuickStop] = useState<DeliveryStop | null>(null);
  const [selectedWaitStop, setSelectedWaitStop] = useState<import('../types/route').QuietWaitStop | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isDrawingRoute, setIsDrawingRoute] = useState(false);
  const [drawProgressPercent, setDrawProgressPercent] = useState(100);
  const [showScrubber, setShowScrubber] = useState(false);
  const [drawSpeedMultiplier, setDrawSpeedMultiplier] = useState<number>(0.5); // Slower by default (0.5x) so driver sees whole route draw in!

  // Listen for Escape key to exit fullscreen
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isFullscreen) {
        setIsFullscreen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isFullscreen]);

  // When fullscreen changes, trigger map invalidateSize so tiles fill the full display smoothly
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;
    const t1 = setTimeout(() => {
      try {
        map.invalidateSize();
      } catch {
        // ignore
      }
    }, 60);
    const t2 = setTimeout(() => {
      try {
        map.invalidateSize();
      } catch {
        // ignore
      }
    }, 280);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [isFullscreen]);

  const locatedStops = useMemo(() => {
    return stops.filter(
      (s): s is DeliveryStop & { latitude: number; longitude: number } =>
        s.latitude !== null &&
        s.longitude !== null &&
        Number.isFinite(s.latitude) &&
        Number.isFinite(s.longitude),
    );
  }, [stops]);

  const activePaths: Coordinate[][] = useMemo(() => {
    if (paths && paths.length > 0) return paths;
    if (path && path.length > 0) return [path];
    if (locatedStops.length > 0) {
      return [locatedStops.map((s) => ({ latitude: s.latitude, longitude: s.longitude }))];
    }
    return [];
  }, [paths, path, locatedStops]);

  const primaryPath = activePaths[0] || [];

  // Expose callbacks to global window for popup button clicks
  useEffect(() => {
    (window as any).__RK_TOGGLE_DELIVERY = (stopId: string, currentStatus: boolean) => {
      onToggleDelivery?.(stopId, currentStatus);
    };
    (window as any).__RK_NAVIGATE = (stopId: string) => {
      const stop = stops.find((s) => s.id === stopId);
      if (stop) onNavigate?.(stop);
    };
    (window as any).__RK_DETAILS = (stopId: string) => {
      const stop = stops.find((s) => s.id === stopId);
      if (stop) onOpenDetails?.(stop);
    };
    (window as any).__RK_NAVIGATE_WAIT = (waitId: string) => {
      const ws = waitStops?.find((w) => w.id === waitId);
      if (ws) {
        setSelectedWaitStop(ws);
        onNavigateWaitStop?.(ws);
      }
    };

    return () => {
      delete (window as any).__RK_TOGGLE_DELIVERY;
      delete (window as any).__RK_NAVIGATE;
      delete (window as any).__RK_DETAILS;
      delete (window as any).__RK_NAVIGATE_WAIT;
    };
  }, [stops, waitStops, onToggleDelivery, onNavigate, onOpenDetails, onNavigateWaitStop]);

  // Keep selectedQuickStop updated when stops change
  useEffect(() => {
    if (selectedQuickStop) {
      const fresh = stops.find((s) => s.id === selectedQuickStop.id);
      if (fresh) setSelectedQuickStop(fresh);
    }
  }, [stops, selectedQuickStop]);

  const tileLayerRef = useRef<L.TileLayer | null>(null);
  const [activeLayer, setActiveLayer] = useState<'osm' | 'satellite' | 'topo'>(() => currentLayer || 'osm');
  const [showLayerMenu, setShowLayerMenu] = useState(false);
  const layerMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (currentLayer && currentLayer !== activeLayer) {
      setActiveLayer(currentLayer);
    }
  }, [currentLayer]);

  useEffect(() => {
    if (!showLayerMenu) return;
    const handleOutsideClick = (e: MouseEvent | TouchEvent) => {
      if (layerMenuRef.current && !layerMenuRef.current.contains(e.target as Node)) {
        setShowLayerMenu(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    document.addEventListener('touchstart', handleOutsideClick);
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
      document.removeEventListener('touchstart', handleOutsideClick);
    };
  }, [showLayerMenu]);

  const handleSelectLayer = (layer: 'osm' | 'satellite' | 'topo') => {
    setActiveLayer(layer);
    setShowLayerMenu(false);
    onLayerChange?.(layer);
  };

  // Offline detection state (real zero network or simulated offline test mode)
  const [isOfflineDetected, setIsOfflineDetected] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    const sim = localStorage.getItem('routekeeper:simulated-offline') === 'true';
    const net = typeof navigator !== 'undefined' ? !navigator.onLine : false;
    return sim || net;
  });

  useEffect(() => {
    const handleOfflineState = () => {
      const sim = localStorage.getItem('routekeeper:simulated-offline') === 'true';
      const net = !navigator.onLine;
      setIsOfflineDetected(sim || net);
    };

    window.addEventListener('online', handleOfflineState);
    window.addEventListener('offline', handleOfflineState);
    window.addEventListener('routekeeper:simulated-offline-change', handleOfflineState);

    return () => {
      window.removeEventListener('online', handleOfflineState);
      window.removeEventListener('offline', handleOfflineState);
      window.removeEventListener('routekeeper:simulated-offline-change', handleOfflineState);
    };
  }, []);

  // Initialize Leaflet Map once
  useEffect(() => {
    if (!containerRef.current) return;

    if (!mapInstanceRef.current) {
      try {
        const initialCenter: [number, number] =
          locatedStops.length > 0
            ? [locatedStops[0].latitude, locatedStops[0].longitude]
            : [51.5074, -0.1278];

        const map = L.map(containerRef.current, {
          center: initialCenter,
          zoom: 13,
          zoomControl: true,
          attributionControl: true,
        });

        // 100% Free Public Map Tiles (No API key needed) - Offline Aware
        const tileConfig =
          activeLayer === 'satellite'
            ? {
                url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                attribution:
                  '&copy; <a href="https://www.esri.com/">Esri</a>, Earthstar Geographics, USDA, USGS, AeroGRID, IGN',
                maxZoom: 19,
              }
            : activeLayer === 'topo'
            ? {
                url: 'https://tile.opentopomap.org/{z}/{x}/{y}.png',
                attribution:
                  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, SRTM | &copy; <a href="https://opentopomap.org">OpenTopoMap</a>',
                maxZoom: 17,
              }
            : {
                url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                attribution:
                  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
                maxZoom: 19,
              };

        tileLayerRef.current = createOfflineAwareTileLayer(
          tileConfig.url,
          {
            maxZoom: tileConfig.maxZoom || 19,
            attribution: tileConfig.attribution,
          },
          activeLayer
        ).addTo(map);

        routeLayerRef.current = L.layerGroup().addTo(map);
        markersLayerRef.current = L.layerGroup().addTo(map);
        animLayerRef.current = L.layerGroup().addTo(map);

        map.whenReady(() => {
          mapLoadedRef.current = true;
          forceRenderMarkers(map);
        });

        mapInstanceRef.current = map;
        mapLoadedRef.current = true;
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Map initialization failed';
        setMapError(msg);
        onError?.(msg);
      }
    }

    // Auto-invalidate map size when container dimensions change (landscape/portrait switch, desktop split)
    let resizeObserver: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined' && containerRef.current) {
      resizeObserver = new ResizeObserver(() => {
        if (mapInstanceRef.current) {
          mapInstanceRef.current.invalidateSize();
        }
      });
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      if (resizeObserver) {
        resizeObserver.disconnect();
      }
      if (drawAnimationFrameRef.current) {
        cancelAnimationFrame(drawAnimationFrameRef.current);
        drawAnimationFrameRef.current = null;
      }
      if (animationFrameIdRef.current) {
        cancelAnimationFrame(animationFrameIdRef.current);
        animationFrameIdRef.current = null;
      }
      if (mapInstanceRef.current) {
        mapLoadedRef.current = false;
        markersMapRef.current.clear();
        prevStopsSignatureRef.current = '';
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, [onError]);

  // Dynamically swap free tile layers without losing markers or route lines
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;
    if (tileLayerRef.current) {
      try {
        map.removeLayer(tileLayerRef.current);
      } catch {
        // ignore
      }
      tileLayerRef.current = null;
    }

    clearObjectUrlCache();

    const tileConfig =
      activeLayer === 'satellite'
        ? {
            url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
            attribution:
              '&copy; <a href="https://www.esri.com/">Esri</a>, Earthstar Geographics, USDA, USGS, AeroGRID, IGN',
            maxZoom: 19,
          }
        : activeLayer === 'topo'
        ? {
            url: 'https://tile.opentopomap.org/{z}/{x}/{y}.png',
            attribution:
              '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, SRTM | &copy; <a href="https://opentopomap.org">OpenTopoMap</a>',
            maxZoom: 17,
          }
        : {
            url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            attribution:
              '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            maxZoom: 19,
          };

    const newLayer = createOfflineAwareTileLayer(
      tileConfig.url,
      {
        maxZoom: tileConfig.maxZoom || 19,
        attribution: tileConfig.attribution,
      },
      activeLayer
    ).addTo(map);

    tileLayerRef.current = newLayer;
    newLayer.bringToBack();

    setTimeout(() => {
      try {
        map.invalidateSize();
      } catch {
        // ignore
      }
    }, 60);
  }, [activeLayer]);

  const handleRecenter = () => {
    const map = mapInstanceRef.current;
    if (!map) return;
    const allCoords: [number, number][] = [
      ...locatedStops.map((s) => [s.latitude, s.longitude] as [number, number]),
      ...(waitStops || []).map((w) => [w.latitude, w.longitude] as [number, number]),
    ];
    if (allCoords.length === 1) {
      map.setView(allCoords[0], 15);
    } else if (allCoords.length > 1) {
      map.fitBounds(L.latLngBounds(allCoords), { padding: [50, 50], maxZoom: 16 });
    }
  };

  // Helper to build all markers from scratch
  const forceRenderMarkers = (map: L.Map) => {
    const markersLayer = markersLayerRef.current;
    if (!markersLayer) return;

    markersLayer.clearLayers();
    markersMapRef.current.clear();

    if (locatedStops.length === 0) return;

    locatedStops.forEach((stop, index) => {
      const isDelivered = !!stop.delivered;
      const iconHtml = createPinHtml(index, isDelivered, markerColor, markerTextColor);

      const customIcon = L.divIcon({
        className: 'custom-leaflet-pin',
        html: iconHtml,
        iconSize: [36, 42],
        iconAnchor: [18, 40],
        popupAnchor: [0, -38],
      });

      const marker = L.marker([stop.latitude, stop.longitude], {
        icon: customIcon,
        title: `${stop.name} (${stop.address})`,
        zIndexOffset: 100,
      });

      marker.bindPopup(buildPopupHtml(stop, index), { maxWidth: 280, closeButton: true });

      marker.on('click', () => {
        setSelectedQuickStop(stop);
        onSelectStop?.(stop);
      });

      marker.addTo(markersLayer);
      markersMapRef.current.set(stop.id, marker);
    });

    // Render Quiet Wait Stops & Parking Pins ("P")
    if (waitStops && waitStops.length > 0) {
      waitStops.forEach((ws) => {
        const waitPinHtml = `
          <div style="position: relative; width: 36px; height: 42px; filter: drop-shadow(0 4px 6px rgba(0,0,0,0.35)); cursor: pointer;">
            <svg width="36" height="42" viewBox="0 0 36 42" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 0C8.05888 0 0 8.05888 0 18C0 28.5 15.5 40.5 17.15 41.74C17.65 42.12 18.35 42.12 18.85 41.74C20.5 40.5 36 28.5 36 18C36 8.05888 27.9411 0 18 0Z" fill="#6366F1"/>
              <circle cx="18" cy="18" r="13" fill="#312E81"/>
              <text x="18" y="23" text-anchor="middle" fill="#FFFFFF" font-size="13" font-weight="900" font-family="system-ui, sans-serif">P</text>
            </svg>
            <div style="position: absolute; bottom: -2px; left: 50%; transform: translateX(-50%); width: 10px; height: 3px; background: rgba(0,0,0,0.3); border-radius: 9999px;"></div>
          </div>
        `;
        const customIcon = L.divIcon({
          className: 'custom-leaflet-wait-pin',
          html: waitPinHtml,
          iconSize: [36, 42],
          iconAnchor: [18, 40],
          popupAnchor: [0, -38],
        });
        const marker = L.marker([ws.latitude, ws.longitude], {
          icon: customIcon,
          title: `Quiet Parking / Rest: ${ws.name}`,
          zIndexOffset: 150,
        });

        const popupHtml = `
          <div style="min-width: 240px; font-family: system-ui, -apple-system, sans-serif; padding: 4px;">
            <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 6px;">
              <span style="background: #4F46E5; color: white; font-weight: 800; font-size: 11px; padding: 2px 7px; border-radius: 6px;">
                🅿️ Quiet Wait Spot
              </span>
              <span style="font-size: 10px; font-weight: 700; color: #4338CA; background: #EEF2FF; padding: 2px 6px; border-radius: 999px;">
                Low Traffic Area
              </span>
            </div>
            <div style="font-weight: 700; font-size: 13px; color: #1E1B4B; margin-bottom: 4px;">
              ${escapeHtml(ws.name)}
            </div>
            <div style="font-size: 11px; color: #475569; margin-bottom: 6px;">
              📍 ${escapeHtml(ws.address)}
            </div>
            <div style="background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 6px 8px; font-size: 11px; margin-bottom: 8px;">
              <div style="font-weight: 700; color: #0F172A;">
                ⏱ Park: ${ws.suggestedArrivalTime} – ${ws.departureTime} (${ws.waitDurationMinutes} mins)
              </div>
              <div style="color: #059669; font-size: 10px; margin-top: 2px; font-weight: 600;">
                ✓ 2 mins quick arterial link to next delivery
              </div>
            </div>
            <div style="font-size: 10px; color: #64748B; margin-bottom: 8px; line-height: 1.3;">
              ${escapeHtml(ws.reason)}
            </div>
            <button
              onclick="window.__RK_NAVIGATE_WAIT && window.__RK_NAVIGATE_WAIT('${ws.id}')"
              style="width: 100%; padding: 6px 10px; font-size: 11px; font-weight: 700; border-radius: 8px; border: none; cursor: pointer; background: #4F46E5; color: white;"
            >
              🧭 Navigate to Quiet Parking Spot
            </button>
          </div>
        `;
        marker.bindPopup(popupHtml, { maxWidth: 280, closeButton: true });
        marker.on('click', () => {
          setSelectedWaitStop(ws);
        });
        marker.addTo(markersLayer);
      });
    }

    try {
      const allCoords: [number, number][] = [
        ...locatedStops.map((s) => [s.latitude, s.longitude] as [number, number]),
        ...(waitStops || []).map((w) => [w.latitude, w.longitude] as [number, number]),
      ];
      if (allCoords.length === 1) {
        map.setView(allCoords[0], 15);
      } else if (allCoords.length > 1) {
        const bounds = L.latLngBounds(allCoords);
        map.fitBounds(bounds, { padding: [45, 45], maxZoom: 16 });
      }
    } catch {
      // Ignore if bounds cannot be computed yet
    }
  };

  // Ensure route polylines and pulse marker exist on the route layer
  const ensureRouteLayers = () => {
    const routeLayer = routeLayerRef.current;
    if (!routeLayer || primaryPath.length < 2) return null;

    if (!casingPolylineRef.current || !routeLayer.hasLayer(casingPolylineRef.current)) {
      routeLayer.clearLayers();

      const casing = L.polyline([], {
        color: '#152F29',
        weight: lineWidth + 3,
        opacity: 0.85,
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(routeLayer);
      casingPolylineRef.current = casing;

      const core = L.polyline([], {
        color: lineColor,
        weight: lineWidth,
        opacity: 0.98,
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(routeLayer);
      corePolylineRef.current = core;

      const pulseIcon = L.divIcon({
        className: 'route-draw-pulse-icon',
        html: `
          <div style="position: relative; width: 22px; height: 22px; display: flex; align-items: center; justify-content: center;">
            <div style="position: absolute; inset: -4px; background: rgba(16, 185, 129, 0.45); border-radius: 9999px; animation: ping 1s cubic-bezier(0, 0, 0.2, 1) infinite;"></div>
            <div style="width: 14px; height: 14px; border-radius: 9999px; background: ${lineColor}; border: 2.5px solid #ffffff; box-shadow: 0 0 10px ${lineColor};"></div>
          </div>
        `,
        iconSize: [22, 22],
        iconAnchor: [11, 11],
      });

      const head = L.marker([primaryPath[0].latitude, primaryPath[0].longitude], {
        icon: pulseIcon,
        zIndexOffset: 500,
      });
      headMarkerRef.current = head;
    }

    return {
      casing: casingPolylineRef.current,
      core: corePolylineRef.current,
      head: headMarkerRef.current,
    };
  };

  // Slices and applies path coordinates to the polylines based on exact percentage (0 - 100%)
  const applyDrawProgress = (percent: number) => {
    const totalPoints = primaryPath.length;
    if (totalPoints < 2) return;
    const layers = ensureRouteLayers();
    const routeLayer = routeLayerRef.current;
    if (!layers || !routeLayer) return;

    const clamped = Math.max(0, Math.min(100, Math.round(percent)));
    setDrawProgressPercent(clamped);

    const numPoints = Math.max(2, Math.min(totalPoints, Math.round((clamped / 100) * totalPoints)));
    const slice = primaryPath.slice(0, numPoints).map((p) => [p.latitude, p.longitude] as [number, number]);

    try {
      layers.casing?.setLatLngs(slice);
      layers.core?.setLatLngs(slice);

      if (layers.head && slice.length > 0) {
        layers.head.setLatLng(slice[slice.length - 1]);
        if (clamped < 100) {
          if (!routeLayer.hasLayer(layers.head)) {
            layers.head.addTo(routeLayer);
          }
        } else {
          if (routeLayer.hasLayer(layers.head)) {
            routeLayer.removeLayer(layers.head);
          }
        }
      }
    } catch {
      // ignore
    }
  };

  // Start or resume drawing animation from a specific percentage
  const startRouteDrawAnimation = (fromPercent: number = 0) => {
    if (primaryPath.length < 2) return;

    if (drawAnimationFrameRef.current) {
      cancelAnimationFrame(drawAnimationFrameRef.current);
      drawAnimationFrameRef.current = null;
    }

    const startVal = fromPercent >= 100 ? 0 : Math.max(0, fromPercent);
    applyDrawProgress(startVal);
    setIsDrawingRoute(true);

    const totalPoints = primaryPath.length;
    const baseDuration = Math.min(3200, Math.max(1600, totalPoints * 3.2));
    const remainingFraction = Math.max(0.1, (100 - startVal) / 100);
    const duration = (baseDuration / drawSpeedMultiplier) * remainingFraction;
    const startTime = performance.now();

    const drawStep = (time: number) => {
      const elapsed = time - startTime;
      const progress = Math.min(1.0, elapsed / duration);
      // Smooth easing
      const ease = progress < 0.5 ? 2 * progress * progress : -1 + (4 - 2 * progress) * progress;
      const currentPercent = Math.min(100, Math.round(startVal + (100 - startVal) * ease));

      applyDrawProgress(currentPercent);

      if (progress < 1.0) {
        drawAnimationFrameRef.current = requestAnimationFrame(drawStep);
      } else {
        setIsDrawingRoute(false);
        setDrawProgressPercent(100);
        applyDrawProgress(100);
        onDrawFinishedRef.current?.();
      }
    };

    drawAnimationFrameRef.current = requestAnimationFrame(drawStep);
  };

  // Pause drawing animation
  const pauseRouteDrawAnimation = () => {
    if (drawAnimationFrameRef.current) {
      cancelAnimationFrame(drawAnimationFrameRef.current);
      drawAnimationFrameRef.current = null;
    }
    setIsDrawingRoute(false);
  };

  // User drags or seeks the interactive scrubber slider
  const handleScrubberChange = (newPercent: number) => {
    pauseRouteDrawAnimation();
    applyDrawProgress(newPercent);
  };

  // Play / Pause toggle for route drawing
  const handleTogglePlayPause = () => {
    if (isDrawingRoute) {
      pauseRouteDrawAnimation();
    } else {
      const startFrom = drawProgressPercent >= 100 ? 0 : drawProgressPercent;
      startRouteDrawAnimation(startFrom);
    }
  };

  // Reset / Rewind route drawing to beginning
  const handleResetDraw = () => {
    pauseRouteDrawAnimation();
    applyDrawProgress(0);
  };

  // Route Polyline Layer with Route Generation Drawing Animation & Scrubber Sync
  useEffect(() => {
    const map = mapInstanceRef.current;
    const routeLayer = routeLayerRef.current;
    if (!map || !routeLayer) return;

    if (drawAnimationFrameRef.current) {
      cancelAnimationFrame(drawAnimationFrameRef.current);
      drawAnimationFrameRef.current = null;
    }

    if (primaryPath.length < 2) {
      routeLayer.clearLayers();
      casingPolylineRef.current = null;
      corePolylineRef.current = null;
      headMarkerRef.current = null;
      setIsDrawingRoute(false);
      setDrawProgressPercent(0);
      return;
    }

    // Reset and initialize layers
    casingPolylineRef.current = null;
    corePolylineRef.current = null;
    headMarkerRef.current = null;
    ensureRouteLayers();

    if (animateDrawRoute) {
      startRouteDrawAnimation(0);
    } else {
      applyDrawProgress(100);
      setIsDrawingRoute(false);
    }

    return () => {
      if (drawAnimationFrameRef.current) {
        cancelAnimationFrame(drawAnimationFrameRef.current);
        drawAnimationFrameRef.current = null;
      }
    };
  }, [primaryPath.length, primaryPath[0]?.latitude, drawKey, lineColor, lineWidth, animateDrawRoute]);

  // Update Markers: Guarantees pins ALWAYS appear and stay in sync!
  useEffect(() => {
    const map = mapInstanceRef.current;
    const markersLayer = markersLayerRef.current;
    if (!map || !markersLayer) return;

    const currentSignature = locatedStops
      .map((s) => `${s.id}:${s.latitude.toFixed(5)}:${s.longitude.toFixed(5)}`)
      .join('|');

    const signatureChanged = currentSignature !== prevStopsSignatureRef.current;
    const layerEmpty = markersLayer.getLayers().length === 0 && locatedStops.length > 0;

    if (signatureChanged || layerEmpty) {
      prevStopsSignatureRef.current = currentSignature;
      forceRenderMarkers(map);
    } else {
      // In-place update: update marker icon and popup content without clearing layers
      locatedStops.forEach((stop, index) => {
        const marker = markersMapRef.current.get(stop.id);
        if (marker) {
          const isDelivered = !!stop.delivered;
          const iconHtml = createPinHtml(index, isDelivered, markerColor, markerTextColor);

          marker.setIcon(
            L.divIcon({
              className: 'custom-leaflet-pin',
              html: iconHtml,
              iconSize: [36, 42],
              iconAnchor: [18, 40],
              popupAnchor: [0, -38],
            }),
          );

          const popup = marker.getPopup();
          if (popup) {
            popup.setContent(buildPopupHtml(stop, index));
          }
        }
      });
    }

    const timer = setTimeout(() => {
      try {
        map.invalidateSize();
      } catch {
        // ignore
      }
    }, 150);

    return () => clearTimeout(timer);
  }, [locatedStops, markerColor, markerTextColor, onSelectStop]);

  // Stable path signature for animation dependency
  const pathSignature = useMemo(() => {
    return `${primaryPath.length}:${primaryPath[0]?.latitude || 0}:${primaryPath[primaryPath.length - 1]?.longitude || 0}`;
  }, [primaryPath]);

  // Driving Route Animation Loop - Persists across state re-renders and does not reset!
  useEffect(() => {
    const map = mapInstanceRef.current;
    const animLayer = animLayerRef.current;
    if (!map || !animLayer) return;

    if (!isAnimating || primaryPath.length < 2) {
      if (animationFrameIdRef.current) {
        cancelAnimationFrame(animationFrameIdRef.current);
        animationFrameIdRef.current = null;
      }
      animLayer.clearLayers();
      animVehicleMarkerRef.current = null;
      animTrailPolylineRef.current = null;
      wasAnimatingRef.current = false;
      animProgressDistRef.current = 0;
      return;
    }

    // If starting a fresh animation from stopped state, reset progress
    if (!wasAnimatingRef.current) {
      animProgressDistRef.current = 0;
      lastPercentReportedRef.current = -1;
      lastStopIdxReportedRef.current = -1;
      wasAnimatingRef.current = true;
    }

    // Ensure vehicle marker & trail exist
    if (!animVehicleMarkerRef.current) {
      const vanIcon = L.divIcon({
        className: 'driving-vehicle-icon',
        html: `
          <div style="position: relative; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center;">
            <div style="position: absolute; inset: 0; background: rgba(16, 185, 129, 0.4); border-radius: 9999px; animation: ping 1.2s cubic-bezier(0, 0, 0.2, 1) infinite;"></div>
            <div id="anim-van-body" style="position: relative; width: 34px; height: 34px; background: #1E453B; border: 2.5px solid #D5EA83; border-radius: 12px; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 14px rgba(0,0,0,0.45); transform: rotate(0deg); transition: transform 0.08s linear;">
              <span style="font-size: 17px; line-height: 1;">🚐</span>
            </div>
          </div>
        `,
        iconSize: [44, 44],
        iconAnchor: [22, 22],
      });

      const startCoord = primaryPath[0];
      const vehicleMarker = L.marker([startCoord.latitude, startCoord.longitude], {
        icon: vanIcon,
        zIndexOffset: 1000,
      }).addTo(animLayer);
      animVehicleMarkerRef.current = vehicleMarker;

      const trail = L.polyline([[startCoord.latitude, startCoord.longitude]], {
        color: '#10B981',
        weight: lineWidth + 2,
        opacity: 0.95,
        lineCap: 'round',
      }).addTo(animLayer);
      animTrailPolylineRef.current = trail;
    }

    const vehicleMarker = animVehicleMarkerRef.current!;
    const trail = animTrailPolylineRef.current!;

    // Precalculate cumulative distances along path
    const segDistances: number[] = [];
    let totalDist = 0;
    for (let i = 0; i < primaryPath.length - 1; i++) {
      const d = haversineDist(primaryPath[i], primaryPath[i + 1]);
      segDistances.push(d);
      totalDist += d;
    }

    if (totalDist <= 0) return;

    let lastTime = performance.now();
    const speedMps = 140 * animationSpeed;

    const animate = (currentTime: number) => {
      const deltaSec = Math.min(0.1, (currentTime - lastTime) / 1000);
      lastTime = currentTime;

      animProgressDistRef.current += speedMps * deltaSec;

      if (animProgressDistRef.current >= totalDist) {
        animProgressDistRef.current = totalDist;
        const lastP = primaryPath[primaryPath.length - 1];
        try {
          vehicleMarker.setLatLng([lastP.latitude, lastP.longitude]);
          trail.setLatLngs(primaryPath.map((p) => [p.latitude, p.longitude] as [number, number]));
        } catch {
          // ignore
        }
        onAnimationProgressRef.current?.(100, Math.max(0, locatedStops.length - 1), lastP);
        onAnimationFinishedRef.current?.();
        return;
      }

      const progressDist = animProgressDistRef.current;

      // Find current segment along path
      let accumulated = 0;
      let segIdx = 0;
      while (segIdx < segDistances.length && accumulated + segDistances[segIdx] < progressDist) {
        accumulated += segDistances[segIdx];
        segIdx++;
      }

      if (segIdx < segDistances.length) {
        const segProgress = (progressDist - accumulated) / segDistances[segIdx];
        const p1 = primaryPath[segIdx];
        const p2 = primaryPath[segIdx + 1];

        const lat = p1.latitude + (p2.latitude - p1.latitude) * segProgress;
        const lng = p1.longitude + (p2.longitude - p1.longitude) * segProgress;
        const currentCoord: Coordinate = { latitude: lat, longitude: lng };

        try {
          vehicleMarker.setLatLng([lat, lng]);

          // Update van rotation
          const bearing = calculateBearing(p1, p2);
          const vanBody = document.getElementById('anim-van-body');
          if (vanBody) {
            vanBody.style.transform = `rotate(${Math.round(bearing)}deg)`;
          }

          // Update trail travelled
          const passedPoints = primaryPath
            .slice(0, segIdx + 1)
            .map((p) => [p.latitude, p.longitude] as [number, number]);
          passedPoints.push([lat, lng]);
          trail.setLatLngs(passedPoints);

          // Find nearest stop along the way
          let nearestStopIdx = 0;
          let minStopDist = Number.POSITIVE_INFINITY;
          locatedStops.forEach((st, idx) => {
            const d = haversineDist(currentCoord, { latitude: st.latitude, longitude: st.longitude });
            if (d < minStopDist) {
              minStopDist = d;
              nearestStopIdx = idx;
            }
          });

          const percent = Math.min(99, Math.round((progressDist / totalDist) * 100));

          // Only trigger progress callback when integer percent changes to avoid unnecessary re-renders
          if (percent !== lastPercentReportedRef.current || nearestStopIdx !== lastStopIdxReportedRef.current) {
            lastPercentReportedRef.current = percent;
            lastStopIdxReportedRef.current = nearestStopIdx;
            onAnimationProgressRef.current?.(percent, nearestStopIdx, currentCoord);
          }

          // Safe pan to follow vehicle without "Set map center and zoom first" error
          if (map && (map as any)._loaded) {
            try {
              const bounds = map.getBounds();
              if (bounds && !bounds.contains([lat, lng])) {
                map.panTo([lat, lng], { animate: true, duration: 0.2 });
              }
            } catch {
              // Ignore bounds check if map state is transitioning
            }
          }
        } catch {
          // ignore Leaflet unmount race condition
        }
      }

      animationFrameIdRef.current = requestAnimationFrame(animate);
    };

    animationFrameIdRef.current = requestAnimationFrame(animate);

    return () => {
      if (animationFrameIdRef.current) {
        cancelAnimationFrame(animationFrameIdRef.current);
        animationFrameIdRef.current = null;
      }
    };
  }, [isAnimating, animationSpeed, pathSignature, locatedStops.length, lineWidth]);

  // Export JPEG snapshot
  useEffect(() => {
    if (!exportRequest || locatedStops.length === 0) return;

    async function runExport() {
      try {
        const canvas = document.createElement('canvas');
        canvas.width = 2400;
        canvas.height = 1600;
        const context = canvas.getContext('2d');
        if (!context) throw new Error('Could not create an export canvas.');

        context.fillStyle = '#f5f6f1';
        context.fillRect(0, 0, canvas.width, canvas.height);

        const width = canvas.width;
        const heightCanvas = canvas.height;
        const padding = 160;

        let selectedZoom = 17;
        let projected: Array<{ x: number; y: number }> = [];
        let scale = 1;

        for (let zoom = 17; zoom >= 3; zoom--) {
          const candidates = locatedStops.map((s) => mercator(s, zoom));
          const minX = Math.min(...candidates.map((p) => p.x));
          const maxX = Math.max(...candidates.map((p) => p.x));
          const minY = Math.min(...candidates.map((p) => p.y));
          const maxY = Math.max(...candidates.map((p) => p.y));
          const spanX = Math.max(256, maxX - minX);
          const spanY = Math.max(256, maxY - minY);
          const candidateScale = Math.min((width - padding * 2) / spanX, (heightCanvas - padding * 2) / spanY);
          if (candidateScale >= 1 || zoom === 3) {
            selectedZoom = zoom;
            projected = candidates;
            scale = candidateScale;
            break;
          }
        }

        const minX = Math.min(...projected.map((p) => p.x));
        const maxX = Math.max(...projected.map((p) => p.x));
        const minY = Math.min(...projected.map((p) => p.y));
        const maxY = Math.max(...projected.map((p) => p.y));
        const offsetX = (width - (maxX - minX) * scale) / 2 - minX * scale;
        const offsetY = (heightCanvas - (maxY - minY) * scale) / 2 - minY * scale;

        const tileStartX = Math.max(0, Math.floor(-offsetX / scale / 256));
        const tileEndX = Math.min(Math.pow(2, selectedZoom) - 1, Math.floor((width - offsetX) / scale / 256));
        const tileStartY = Math.max(0, Math.floor(-offsetY / scale / 256));
        const tileEndY = Math.min(Math.pow(2, selectedZoom) - 1, Math.floor((heightCanvas - offsetY) / scale / 256));

        const jobs: Promise<{ image: HTMLImageElement; x: number; y: number }>[] = [];
        for (let tx = tileStartX; tx <= tileEndX; tx++) {
          for (let ty = tileStartY; ty <= tileEndY; ty++) {
            jobs.push(tileImage(tx, ty, selectedZoom));
          }
        }

        const results = await Promise.allSettled(jobs);
        results.forEach((res) => {
          if (res.status === 'fulfilled') {
            const tile = res.value;
            context.drawImage(
              tile.image,
              tile.x * 256 * scale + offsetX,
              tile.y * 256 * scale + offsetY,
              256 * scale,
              256 * scale,
            );
          }
        });

        // Draw Route Lines with dual outline
        activePaths.forEach((line) => {
          if (line.length < 2) return;
          // Outer casing
          context.beginPath();
          line.forEach((p, idx) => {
            const pixel = mercator(p, selectedZoom);
            const x = pixel.x * scale + offsetX;
            const y = pixel.y * scale + offsetY;
            if (idx === 0) context.moveTo(x, y);
            else context.lineTo(x, y);
          });
          context.strokeStyle = '#152F29';
          context.lineWidth = Math.max(8, lineWidth * 2.8);
          context.lineCap = 'round';
          context.lineJoin = 'round';
          context.globalAlpha = 0.8;
          context.stroke();

          // Inner line
          context.beginPath();
          line.forEach((p, idx) => {
            const pixel = mercator(p, selectedZoom);
            const x = pixel.x * scale + offsetX;
            const y = pixel.y * scale + offsetY;
            if (idx === 0) context.moveTo(x, y);
            else context.lineTo(x, y);
          });
          context.strokeStyle = lineColor;
          context.lineWidth = Math.max(5, lineWidth * 2);
          context.lineCap = 'round';
          context.lineJoin = 'round';
          context.globalAlpha = 0.98;
          context.stroke();
        });

        // Draw Pins on export canvas
        locatedStops.forEach((stop, index) => {
          const pixel = mercator(stop, selectedZoom);
          const x = pixel.x * scale + offsetX;
          const y = pixel.y * scale + offsetY;

          context.save();
          context.translate(x, y);

          // Pin drop shadow
          context.fillStyle = 'rgba(18, 40, 34, 0.45)';
          context.beginPath();
          context.ellipse(0, 0, 14, 7, 0, 0, Math.PI * 2);
          context.fill();

          // Teardrop pin body
          context.rotate(-Math.PI / 4);
          context.fillStyle = stop.delivered ? '#10B981' : markerColor;
          context.strokeStyle = stop.delivered ? '#D1FAE5' : '#FFFFFF';
          context.lineWidth = 3;

          const r = 16;
          context.beginPath();
          context.moveTo(-r, -r);
          context.lineTo(r, -r);
          context.arc(r, 0, r, -Math.PI / 2, Math.PI / 2, false);
          context.lineTo(-r, r);
          context.closePath();
          context.fill();
          context.stroke();

          // Stop number text
          context.rotate(Math.PI / 4);
          context.fillStyle = markerTextColor;
          context.font = 'bold 16px system-ui, sans-serif';
          context.textAlign = 'center';
          context.textBaseline = 'middle';
          context.fillText(stop.delivered ? '✓' : String(index + 1), 0, -r / 2);

          context.restore();
        });

        const dataUrl = canvas.toDataURL('image/jpeg', 0.92);
        onExport?.(dataUrl);
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Map export failed';
        setMapError(msg);
        onError?.(msg);
      }
    }

    runExport();
  }, [exportRequest, locatedStops, activePaths, markerColor, markerTextColor, lineColor, lineWidth, onExport, onError]);

  // Quick navigation helpers for selected stop card
  const selectedIndex = selectedQuickStop ? locatedStops.findIndex((s) => s.id === selectedQuickStop.id) : -1;
  const prevStop = selectedIndex > 0 ? locatedStops[selectedIndex - 1] : null;
  const nextStop = selectedIndex >= 0 && selectedIndex < locatedStops.length - 1 ? locatedStops[selectedIndex + 1] : null;

  const navigateToStopOnMap = (targetStop: DeliveryStop) => {
    setSelectedQuickStop(targetStop);
    onSelectStop?.(targetStop);
    if (mapInstanceRef.current && targetStop.latitude !== null && targetStop.longitude !== null) {
      try {
        mapInstanceRef.current.panTo([targetStop.latitude, targetStop.longitude], { animate: true, duration: 0.4 });
      } catch {
        mapInstanceRef.current.setView([targetStop.latitude, targetStop.longitude], 15);
      }
      const marker = markersMapRef.current.get(targetStop.id);
      if (marker) marker.openPopup();
    }
  };

  return (
    <div
      className={`relative w-full overflow-hidden transition-all duration-300 ${
        isFullscreen
          ? 'fixed inset-0 z-[9999] w-screen h-screen rounded-none border-0 shadow-2xl bg-[#edf0e9]'
          : `rounded-2xl border border-[#DDE3D7] shadow-sm bg-[#edf0e9] ${className}`
      }`}
      style={{ height: isFullscreen ? '100vh' : height }}
    >
      <div ref={containerRef} className="w-full h-full z-0" />

      {mapError && (
        <div className="absolute top-3 left-3 right-3 bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-xl z-[1000] text-center">
          {mapError}
        </div>
      )}

      {locatedStops.length === 0 && (
        <div className="absolute inset-0 flex items-center justify-center bg-[#edf0e9]/80 backdrop-blur-xs z-[500] pointer-events-none text-[#748078] text-xs font-medium">
          No mapped stop locations to display yet
        </div>
      )}

      {/* Floating Exit Fullscreen Button in top-left when in fullscreen */}
      {isFullscreen && (
        <button
          type="button"
          onClick={() => setIsFullscreen(false)}
          className="absolute top-3 left-3 z-[460] px-3 py-1.5 rounded-xl bg-[#1E453B]/95 hover:bg-[#1E453B] text-white text-xs font-bold flex items-center gap-1.5 shadow-xl backdrop-blur-md cursor-pointer border border-[#D5EA83]/30 transition"
          title="Exit full screen map (Esc)"
        >
          <Minimize2 className="w-3.5 h-3.5 text-[#D5EA83]" />
          <span>Exit Fullscreen</span>
          <span className="text-[10px] text-white/60 ml-0.5 hidden sm:inline">(Esc)</span>
        </button>
      )}

      {/* Route Generating Visual Indicator */}
      {isDrawingRoute && !showScrubber && !isFullscreen && (
        <div className="absolute top-3 left-3 z-[450] bg-[#1E453B]/95 text-white backdrop-blur-xs px-3 py-1.5 rounded-xl shadow-lg border border-[#D5EA83]/30 flex items-center gap-2.5 text-xs font-semibold animate-in fade-in">
          <div className="w-2.5 h-2.5 rounded-full bg-[#D5EA83] animate-ping" />
          <span>Drawing Route… ({drawProgressPercent}%)</span>
          <button
            type="button"
            onClick={() => setShowScrubber(true)}
            className="text-[10px] bg-white/10 hover:bg-white/20 text-[#D5EA83] px-1.5 py-0.5 rounded font-bold cursor-pointer transition ml-1"
          >
            Open Scrubber
          </button>
        </div>
      )}

      {/* Top Right Map Buttons (Layers, Recenter, Full Screen, Draw Route Scrubber) */}
      <div
        className="absolute top-3 right-3 z-[450] flex items-center gap-1.5 flex-wrap justify-end"
        onMouseDown={(e) => e.stopPropagation()}
        onClick={(e) => e.stopPropagation()}
        onDoubleClick={(e) => e.stopPropagation()}
      >
        {/* Free Map Layer Switcher (OpenStreetMap, Esri Satellite, Topo) */}
        <div className="relative" ref={layerMenuRef}>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              setShowLayerMenu((prev) => !prev);
            }}
            className="px-2.5 py-1.5 rounded-xl shadow-md text-xs font-bold flex items-center gap-1.5 bg-white/95 hover:bg-white text-[#1A2E27] border border-[#DDE3D7] cursor-pointer transition select-none"
            title="Switch map layer (100% free tiles, no API code needed)"
          >
            <span>🗺️</span>
            <span className="hidden sm:inline">
              {activeLayer === 'osm' ? 'Street Map' : activeLayer === 'satellite' ? 'Satellite View' : 'Terrain Topo'}
            </span>
          </button>

          {showLayerMenu && (
            <div
              className="absolute right-0 top-full mt-1.5 bg-white border border-[#DDE3D7] rounded-xl shadow-xl p-1.5 flex flex-col gap-1 min-w-[170px] z-[500]"
              onMouseDown={(e) => e.stopPropagation()}
              onClick={(e) => e.stopPropagation()}
            >
              <span className="text-[10px] font-bold text-[#748078] px-2 py-0.5 uppercase tracking-wider">
                Free Map Layers
              </span>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleSelectLayer('osm');
                }}
                className={`text-left px-2.5 py-1.5 rounded-lg text-xs font-bold transition flex items-center justify-between cursor-pointer ${
                  activeLayer === 'osm' ? 'bg-[#1E453B] text-white' : 'hover:bg-[#FAFBF7] text-[#1A2E27]'
                }`}
              >
                <span>Street Map</span>
                <span className="text-[9px] opacity-80">OSM</span>
              </button>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleSelectLayer('satellite');
                }}
                className={`text-left px-2.5 py-1.5 rounded-lg text-xs font-bold transition flex items-center justify-between cursor-pointer ${
                  activeLayer === 'satellite' ? 'bg-[#1E453B] text-white' : 'hover:bg-[#FAFBF7] text-[#1A2E27]'
                }`}
              >
                <span>🛰️ Satellite</span>
                <span className="text-[9px] opacity-80">Aerial Photos</span>
              </button>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleSelectLayer('topo');
                }}
                className={`text-left px-2.5 py-1.5 rounded-lg text-xs font-bold transition flex items-center justify-between cursor-pointer ${
                  activeLayer === 'topo' ? 'bg-[#1E453B] text-white' : 'hover:bg-[#FAFBF7] text-[#1A2E27]'
                }`}
              >
                <span>⛰️ Terrain Topo</span>
                <span className="text-[9px] opacity-80">Elevation</span>
              </button>
            </div>
          )}
        </div>

        {/* Fit Route / Re-Center Button */}
        {locatedStops.length > 0 && (
          <button
            type="button"
            onClick={handleRecenter}
            className="px-2.5 py-1.5 rounded-xl shadow-md text-xs font-bold flex items-center gap-1.5 bg-white/95 hover:bg-white text-[#1E453B] border border-[#DDE3D7] cursor-pointer transition"
            title="Re-center map to fit full route"
          >
            <span>🧭</span>
            <span className="hidden sm:inline">Fit Route</span>
          </button>
        )}

        {/* Offline Map Tiles Manager Modal Trigger */}
        {onOpenOfflineModal && (
          <button
            type="button"
            onClick={onOpenOfflineModal}
            className="px-2.5 py-1.5 rounded-xl shadow-md text-xs font-bold flex items-center gap-1.5 bg-white/95 hover:bg-white text-[#1E453B] border border-[#DDE3D7] cursor-pointer transition"
            title="Download offline map tiles for territories & routes"
          >
            <Download className="w-3.5 h-3.5 text-[#1E453B]" />
            <span className="hidden sm:inline">Offline Tiles</span>
          </button>
        )}

        {/* Offline Status Badge */}
        {isOfflineDetected && (
          <button
            type="button"
            onClick={onOpenOfflineModal}
            className="px-2 py-1.5 rounded-xl shadow-md text-xs font-bold flex items-center gap-1.5 bg-amber-500 hover:bg-amber-600 text-white border border-amber-600 cursor-pointer"
            title="Map operating with local offline cache. Click to manage offline packs."
          >
            <WifiOff className="w-3.5 h-3.5 text-white animate-pulse" />
            <span className="text-[11px] font-bold">Offline</span>
          </button>
        )}

        {/* Full Screen View Toggle */}
        <button
          type="button"
          onClick={() => setIsFullscreen((prev) => !prev)}
          className="px-2.5 py-1.5 rounded-xl shadow-md text-xs font-bold flex items-center gap-1.5 bg-white/95 hover:bg-white text-[#1E453B] border border-[#DDE3D7] cursor-pointer transition"
          title={isFullscreen ? 'Exit full screen (Esc)' : 'View map in full screen'}
        >
          {isFullscreen ? (
            <Minimize2 className="w-3.5 h-3.5 text-[#1E453B]" />
          ) : (
            <Maximize2 className="w-3.5 h-3.5 text-[#1E453B]" />
          )}
          <span className="hidden sm:inline">{isFullscreen ? 'Exit' : 'Full Screen'}</span>
        </button>

        {/* Interactive Route Drawing Scrubber Toggle Button */}
        {primaryPath.length >= 2 && !isAnimating && (
          <button
            type="button"
            onClick={() => setShowScrubber((prev) => !prev)}
            className={`px-2.5 py-1.5 rounded-xl shadow-md text-xs font-bold flex items-center gap-1.5 cursor-pointer transition border ${
              showScrubber
                ? 'bg-[#1E453B] text-white border-[#1E453B]'
                : 'bg-white/95 hover:bg-white text-[#1E453B] border-[#DDE3D7]'
            }`}
            title="Interactive route line drawing slider & animation controls"
          >
            <Sparkles className={`w-3.5 h-3.5 ${showScrubber ? 'text-[#D5EA83]' : 'text-[#10B981]'}`} />
            <span className="hidden sm:inline">Draw Route</span>
            <span className="text-[10px] font-mono px-1 py-0.2 rounded bg-black/10">
              {drawProgressPercent}%
            </span>
          </button>
        )}
      </div>

      {/* Floating Interactive Route Drawing Scrubber Dock */}
      {showScrubber && primaryPath.length >= 2 && !isAnimating && (
        <div className="absolute top-14 left-3 right-3 sm:left-auto sm:right-3 sm:w-[380px] z-[460] bg-[#1E453B]/95 text-white backdrop-blur-md border border-[#365349] rounded-2xl p-3 shadow-2xl animate-in slide-in-from-top-2">
          <div className="flex items-center justify-between gap-2 mb-2">
            <div className="flex items-center gap-2 min-w-0">
              <span className="text-[10px] font-extrabold uppercase tracking-wider bg-[#D5EA83] text-[#1E453B] px-2 py-0.5 rounded-full">
                Route Scrubber
              </span>
              <span className="text-xs font-bold text-[#D5EA83]">
                {drawProgressPercent}% drawn
              </span>
            </div>
            <button
              type="button"
              onClick={() => setShowScrubber(false)}
              className="text-white/60 hover:text-white p-1 rounded-lg hover:bg-white/10 text-xs font-bold cursor-pointer transition"
              title="Close scrubber"
            >
              ✕
            </button>
          </div>

          {/* Interactive Range Slider with Play/Pause and Reset */}
          <div className="flex items-center gap-2 mb-2">
            <button
              type="button"
              onClick={handleTogglePlayPause}
              className={`w-8 h-8 rounded-xl font-bold flex items-center justify-center shrink-0 cursor-pointer transition shadow-xs ${
                isDrawingRoute
                  ? 'bg-amber-400 text-amber-950 hover:bg-amber-300'
                  : 'bg-[#10B981] text-white hover:bg-[#059669]'
              }`}
              title={isDrawingRoute ? 'Pause route line drawing' : 'Play route line drawing'}
            >
              {isDrawingRoute ? (
                <Pause className="w-3.5 h-3.5 fill-current" />
              ) : (
                <Play className="w-3.5 h-3.5 fill-current ml-0.5" />
              )}
            </button>

            <button
              type="button"
              onClick={handleResetDraw}
              className="w-8 h-8 rounded-xl bg-white/10 hover:bg-white/20 text-white flex items-center justify-center shrink-0 cursor-pointer transition"
              title="Rewind route drawing to start"
            >
              <RotateCcw className="w-3.5 h-3.5" />
            </button>

            <div className="flex-1 flex flex-col justify-center">
              <input
                type="range"
                min={0}
                max={100}
                step={1}
                value={drawProgressPercent}
                onChange={(e) => handleScrubberChange(Number(e.target.value))}
                className="w-full accent-[#D5EA83] cursor-pointer h-2 bg-white/20 rounded-lg"
              />
            </div>
          </div>

          <div className="flex items-center justify-between text-[11px] text-white/80 border-t border-white/10 pt-2">
            <span className="text-[10px] text-white/60">
              Seek along route or play
            </span>
            <div className="flex items-center gap-1">
              <span className="text-[10px] text-white/60 mr-1">Speed:</span>
              {[0.5, 1, 2].map((spd) => (
                <button
                  key={spd}
                  type="button"
                  onClick={() => setDrawSpeedMultiplier(spd)}
                  className={`px-1.5 py-0.5 rounded text-[10px] font-bold cursor-pointer transition ${
                    drawSpeedMultiplier === spd
                      ? 'bg-[#D5EA83] text-[#1E453B]'
                      : 'bg-white/10 hover:bg-white/20 text-white'
                  }`}
                  title={`Speed ${spd}x`}
                >
                  {spd}x
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Floating Interactive Quiet Wait Stop Card (Shown when parking pin is clicked) */}
      {selectedWaitStop && (
        <div className="absolute bottom-3 left-3 right-3 z-[1000] bg-white/95 backdrop-blur-md border border-[#C7D2FE] rounded-2xl p-3.5 shadow-xl animate-in slide-in-from-bottom-3 duration-200">
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-center gap-2.5 min-w-0">
              <span className="w-8 h-8 rounded-xl bg-[#4F46E5] text-white flex items-center justify-center font-black text-sm shrink-0 shadow-xs">
                🅿️
              </span>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h4 className="font-bold text-xs text-[#1E1B4B] truncate">
                    {selectedWaitStop.name}
                  </h4>
                  <span className="text-[9px] font-bold px-1.5 py-0.2 rounded-full bg-[#EEF2FF] text-[#4338CA]">
                    Low Traffic Area
                  </span>
                </div>
                <p className="text-[11px] text-[#475569] truncate mt-0.5">
                  {selectedWaitStop.address}
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setSelectedWaitStop(null)}
              className="p-1 text-[#64748B] hover:text-[#0F172A] rounded-lg hover:bg-black/5 cursor-pointer shrink-0"
              title="Close card"
            >
              ✕
            </button>
          </div>

          <div className="bg-[#F8FAFC] border border-[#E2E8F0] rounded-xl p-2.5 mt-2.5 text-xs flex flex-col gap-1">
            <div className="flex items-center justify-between font-bold text-[#0F172A]">
              <span>⏱ Park for {selectedWaitStop.waitDurationMinutes} mins</span>
              <span className="text-[#4F46E5]">{selectedWaitStop.suggestedArrivalTime} – {selectedWaitStop.departureTime}</span>
            </div>
            <div className="text-[11px] text-[#059669] font-semibold">
              ✓ {selectedWaitStop.arterialAccessMinutes} mins direct arterial access back to main route
            </div>
            <div className="text-[11px] text-[#64748B] mt-0.5">
              {selectedWaitStop.reason}
            </div>
          </div>

          <div className="flex items-center gap-2 mt-2.5 pt-2 border-t border-[#F1F5F9]">
            <button
              type="button"
              onClick={() => onNavigateWaitStop?.(selectedWaitStop)}
              className="flex-1 py-2 px-3 rounded-xl bg-[#4F46E5] hover:bg-[#4338CA] text-white font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-xs"
            >
              <span>🧭 Navigate to Quiet Wait Spot</span>
            </button>
            <button
              type="button"
              onClick={() => setSelectedWaitStop(null)}
              className="py-2 px-3 rounded-xl bg-white border border-[#CBD5E1] text-[#334155] font-bold text-xs cursor-pointer"
            >
              Close
            </button>
          </div>
        </div>
      )}

      {/* Floating Interactive Stop Info Card (Shown when a delivery pin is clicked) */}
      {selectedQuickStop && !selectedWaitStop && (
        <div className="absolute bottom-3 left-3 right-3 z-[1000] bg-white/95 backdrop-blur-md border border-[#DDE3D7] rounded-2xl p-3.5 shadow-xl animate-in slide-in-from-bottom-3 duration-200">
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-center gap-2.5 min-w-0">
              <span
                className={`w-7 h-7 rounded-xl flex items-center justify-center font-bold text-xs shrink-0 shadow-xs ${
                  selectedQuickStop.delivered
                    ? 'bg-[#10B981] text-white'
                    : 'bg-[#1E453B] text-white'
                }`}
              >
                {selectedIndex >= 0 ? `#${selectedIndex + 1}` : '📍'}
              </span>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h4 className="font-bold text-xs text-[#1A2E27] truncate">
                    {selectedQuickStop.name}
                  </h4>
                  <span
                    className={`text-[9px] font-bold px-1.5 py-0.2 rounded-full ${
                      selectedQuickStop.delivered
                        ? 'bg-[#D1FAE5] text-[#065F46]'
                        : 'bg-[#E7ECE2] text-[#1E453B]'
                    }`}
                  >
                    {selectedQuickStop.delivered ? '✓ Delivered' : 'Pending'}
                  </span>
                </div>
                <p className="text-[11px] text-[#53635d] truncate mt-0.5">
                  {selectedQuickStop.address}
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setSelectedQuickStop(null)}
              className="p-1 text-[#748078] hover:text-[#1A2E27] rounded-lg hover:bg-black/5 cursor-pointer shrink-0"
              title="Close card"
            >
              ✕
            </button>
          </div>

          <div className="flex items-center gap-2 mt-3 pt-2.5 border-t border-[#F3F5ED]">
            {/* Direct Tick / Untick delivery button */}
            <button
              type="button"
              onClick={() => {
                onToggleDelivery?.(selectedQuickStop.id, selectedQuickStop.delivered);
              }}
              className={`flex-1 py-2 px-3 rounded-xl font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-xs ${
                selectedQuickStop.delivered
                  ? 'bg-[#E7ECE2] text-[#1E453B] hover:bg-[#DDE3D7]'
                  : 'bg-[#10B981] hover:bg-[#059669] text-white'
              }`}
            >
              <span>{selectedQuickStop.delivered ? 'Mark Pending' : '✓ Confirm Delivered'}</span>
            </button>

            {/* Navigate button */}
            <button
              type="button"
              onClick={() => onNavigate?.(selectedQuickStop)}
              className="py-2 px-3 rounded-xl bg-white border border-[#DDE3D7] hover:bg-[#FAFBF7] text-[#1E453B] font-bold text-xs flex items-center justify-center gap-1 cursor-pointer"
              title="Open Navigation"
            >
              <span>🧭 Navigate</span>
            </button>

            {/* Stepper buttons: Prev / Next */}
            <div className="flex items-center gap-1 shrink-0">
              <button
                type="button"
                disabled={!prevStop}
                onClick={() => prevStop && navigateToStopOnMap(prevStop)}
                className="w-8 h-8 rounded-xl bg-[#E7ECE2] disabled:opacity-30 hover:bg-[#DDE3D7] text-[#1A2E27] flex items-center justify-center text-xs font-bold cursor-pointer"
                title="Previous Stop"
              >
                ◀
              </button>
              <button
                type="button"
                disabled={!nextStop}
                onClick={() => nextStop && navigateToStopOnMap(nextStop)}
                className="w-8 h-8 rounded-xl bg-[#E7ECE2] disabled:opacity-30 hover:bg-[#DDE3D7] text-[#1A2E27] flex items-center justify-center text-xs font-bold cursor-pointer"
                title="Next Stop"
              >
                ▶
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
