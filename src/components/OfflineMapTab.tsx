import React, { useState, useEffect, useRef } from 'react';
import L from 'leaflet';
import {
  Download,
  HardDrive,
  MapPin,
  Check,
  AlertTriangle,
  X,
  Trash2,
  Navigation,
  Globe,
  WifiOff,
  Wifi,
  Sparkles,
  Layers,
  RefreshCw,
  LocateFixed,
  Maximize2,
  Compass,
  Search,
} from 'lucide-react';
import {
  computeBoundingBoxTiles,
  downloadTerritoryOfflinePack,
  getOfflineStorageStats,
  deleteOfflinePackRecord,
  clearAllOfflineTiles,
  getBoundingBox,
  type OfflineMapPack,
  type DownloadProgress,
  type BoundingBox,
} from '../services/offlineTileManager';
import type { Coordinate, DeliveryStop } from '../types/route';

interface OfflineMapTabProps {
  stops?: DeliveryStop[];
  initialCenter?: Coordinate;
  onNavigateToRoute?: () => void;
}

export const OfflineMapTab: React.FC<OfflineMapTabProps> = ({
  stops = [],
  initialCenter,
  onNavigateToRoute,
}) => {
  // Map container & instance for the interactive area selector
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const selectionRectRef = useRef<L.Rectangle | null>(null);
  const centerMarkerRef = useRef<L.Marker | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);

  // Selected Service
  const [selectedService, setSelectedService] = useState<'osm' | 'topo' | 'satellite'>('osm');

  // Selected Area Bounding Box (Default 10 km around center or initialCenter)
  const [bbox, setBbox] = useState<BoundingBox>(() => {
    const baseLat = initialCenter?.latitude ?? 51.5074;
    const baseLon = initialCenter?.longitude ?? -0.1278;
    return getBoundingBox(baseLat, baseLon, 8);
  });

  const [areaRadiusKm, setAreaRadiusKm] = useState<number>(8);
  const areaRadiusKmRef = useRef<number>(8);
  useEffect(() => {
    areaRadiusKmRef.current = areaRadiusKm;
  }, [areaRadiusKm]);
  const [packCustomName, setPackCustomName] = useState<string>('Delivery Area (Zoom 5 - 17)');
  const [feedbackToast, setFeedbackToast] = useState<string | null>(null);

  // Stats and packs
  const [storageStats, setStorageStats] = useState<{
    tileCount: number;
    sizeMB: number;
    packs: OfflineMapPack[];
  }>({ tileCount: 0, sizeMB: 0, packs: [] });

  // Download state
  const [isDownloading, setIsDownloading] = useState(false);
  const [progress, setProgress] = useState<DownloadProgress | null>(null);
  const [downloadSuccess, setDownloadSuccess] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  // Search state
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchingLocation, setIsSearchingLocation] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  // Simulated offline test switch
  const [isSimulatedOffline, setIsSimulatedOffline] = useState(() => {
    return typeof window !== 'undefined' && localStorage.getItem('routekeeper:simulated-offline') === 'true';
  });

  const loadStats = async () => {
    try {
      const stats = await getOfflineStorageStats();
      setStorageStats(stats);
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    loadStats();
  }, []);

  // Calculate center of current bounding box
  const centerLat = (bbox.minLat + bbox.maxLat) / 2;
  const centerLon = (bbox.minLon + bbox.maxLon) / 2;

  // Compute width and height in km
  const heightKm = Math.abs(bbox.maxLat - bbox.minLat) * 110.574;
  const cosLat = Math.cos((centerLat * Math.PI) / 180);
  const widthKm = Math.abs(bbox.maxLon - bbox.minLon) * (111.32 * Math.max(0.2, Math.abs(cosLat)));
  const areaKm2 = (widthKm * heightKm).toFixed(1);

  // Automatically calculate tiles from Zoom 5 to 17 (fixed range per user request)
  const calculatedTiles = computeBoundingBoxTiles(bbox, 5, 17, selectedService);

  // Tile breakdown metrics
  const overviewTilesCount = calculatedTiles.filter((t) => t.z >= 5 && t.z <= 11).length;
  const regionalTilesCount = calculatedTiles.filter((t) => t.z >= 12 && t.z <= 14).length;
  const streetTilesCount = calculatedTiles.filter((t) => t.z >= 15 && t.z <= 17).length;
  const estimatedSizeMB = (calculatedTiles.length * 0.0184).toFixed(1);

  const showToast = (msg: string) => {
    setFeedbackToast(msg);
    setTimeout(() => setFeedbackToast(null), 3000);
  };

  const getLayerUrl = (srv: string) => {
    if (srv === 'satellite') {
      return 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
    }
    if (srv === 'topo') {
      return 'https://tile.opentopomap.org/{z}/{x}/{y}.png';
    }
    return 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
  };

  // Initialize interactive Leaflet map
  useEffect(() => {
    if (!mapContainerRef.current) return;

    if (!mapInstanceRef.current) {
      const initialMapCenter: [number, number] = [centerLat, centerLon];
      const map = L.map(mapContainerRef.current, {
        center: initialMapCenter,
        zoom: 11,
        zoomControl: true,
        attributionControl: false,
      });

      tileLayerRef.current = L.tileLayer(getLayerUrl(selectedService), {
        maxZoom: 19,
      }).addTo(map);

      // Add selection rectangle
      const bounds = L.latLngBounds([bbox.minLat, bbox.minLon], [bbox.maxLat, bbox.maxLon]);
      selectionRectRef.current = L.rectangle(bounds, {
        color: '#10B981',
        weight: 3,
        dashArray: '6, 6',
        fillColor: '#10B981',
        fillOpacity: 0.18,
      }).addTo(map);

      // Center crosshair marker
      const pinIcon = L.divIcon({
        className: 'selector-center-pin',
        html: `
          <div style="width: 24px; height: 24px; border-radius: 50%; background: #1E453B; border: 2.5px solid #FFFFFF; box-shadow: 0 2px 10px rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; color: #D5EA83; font-weight: bold; font-size: 13px;">
            ✛
          </div>
        `,
        iconSize: [24, 24],
        iconAnchor: [12, 12],
      });

      centerMarkerRef.current = L.marker(initialMapCenter, {
        icon: pinIcon,
        interactive: false,
      }).addTo(map);

      // Tap on map to reposition selection area
      map.on('click', (e: L.LeafletMouseEvent) => {
        const radius = areaRadiusKmRef.current;
        const newBbox = getBoundingBox(e.latlng.lat, e.latlng.lng, radius);
        setBbox(newBbox);
        setPackCustomName(`Area near ${e.latlng.lat.toFixed(3)}, ${e.latlng.lng.toFixed(3)}`);
        showToast('✓ Repositioned selection area to tapped point');
      });

      mapInstanceRef.current = map;

      // Fit initially
      setTimeout(() => {
        if (mapInstanceRef.current) {
          mapInstanceRef.current.invalidateSize();
          mapInstanceRef.current.fitBounds(bounds, { padding: [30, 30], maxZoom: 14 });
        }
      }, 200);
    }

    const resizeObserver = new ResizeObserver(() => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.invalidateSize();
      }
    });
    if (mapContainerRef.current) {
      resizeObserver.observe(mapContainerRef.current);
    }

    return () => {
      resizeObserver.disconnect();
    };
  }, []);

  // Update map layer when selectedService changes
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
    }

    tileLayerRef.current = L.tileLayer(getLayerUrl(selectedService), {
      maxZoom: 19,
    }).addTo(map);
    tileLayerRef.current.bringToBack();
  }, [selectedService]);

  // Sync rectangle & marker when bbox changes
  useEffect(() => {
    if (!mapInstanceRef.current || !selectionRectRef.current) return;
    const bounds = L.latLngBounds([bbox.minLat, bbox.minLon], [bbox.maxLat, bbox.maxLon]);
    selectionRectRef.current.setBounds(bounds);
    if (centerMarkerRef.current) {
      centerMarkerRef.current.setLatLng([centerLat, centerLon]);
    }
  }, [bbox, centerLat, centerLon]);

  // Adjust radius and re-center
  const handleRadiusChange = (radiusKm: number) => {
    setAreaRadiusKm(radiusKm);
    const newBbox = getBoundingBox(centerLat, centerLon, radiusKm);
    setBbox(newBbox);
    if (mapInstanceRef.current) {
      const bounds = L.latLngBounds([newBbox.minLat, newBbox.minLon], [newBbox.maxLat, newBbox.maxLon]);
      mapInstanceRef.current.fitBounds(bounds, { padding: [30, 30], maxZoom: 15 });
    }
    showToast(`Area adjusted to ${radiusKm * 2} km span`);
  };

  // Selector Action: Frame whatever is currently visible on the map view
  const handleFrameVisibleMapView = () => {
    const map = mapInstanceRef.current;
    if (!map) {
      showToast('Map is still initializing. Please try again.');
      return;
    }
    const bounds = map.getBounds();
    const newBbox: BoundingBox = {
      minLat: bounds.getSouth(),
      maxLat: bounds.getNorth(),
      minLon: bounds.getWest(),
      maxLon: bounds.getEast(),
    };
    setBbox(newBbox);
    setPackCustomName(`Custom View Area (${bounds.getNorth().toFixed(2)}N, ${bounds.getWest().toFixed(2)}W)`);
    showToast('✓ Area selection set to current map view!');
  };

  // Selector Action: Fit to active route stops (if any)
  const handleFitToActiveRoute = () => {
    const locatedStops = stops.filter((s) => s.latitude !== null && s.longitude !== null);
    if (locatedStops.length === 0) {
      showToast('No active route stops available to fit.');
      return;
    }

    let minLat = Infinity;
    let maxLat = -Infinity;
    let minLon = Infinity;
    let maxLon = -Infinity;

    locatedStops.forEach((s) => {
      if (s.latitude! < minLat) minLat = s.latitude!;
      if (s.latitude! > maxLat) maxLat = s.latitude!;
      if (s.longitude! < minLon) minLon = s.longitude!;
      if (s.longitude! > maxLon) maxLon = s.longitude!;
    });

    // Add 2.5 km buffer around outer stops
    const latBuffer = 2.5 / 110.574;
    const lonBuffer = 2.5 / (111.32 * Math.cos(((minLat + maxLat) / 2) * (Math.PI / 180)));

    const newBbox: BoundingBox = {
      minLat: Math.max(-85, minLat - latBuffer),
      maxLat: Math.min(85, maxLat + latBuffer),
      minLon: Math.max(-180, minLon - lonBuffer),
      maxLon: Math.min(180, maxLon + lonBuffer),
    };

    setBbox(newBbox);
    setPackCustomName(`Route Territory (${locatedStops.length} Stops)`);

    if (mapInstanceRef.current) {
      const bounds = L.latLngBounds([newBbox.minLat, newBbox.minLon], [newBbox.maxLat, newBbox.maxLon]);
      mapInstanceRef.current.fitBounds(bounds, { padding: [35, 35] });
    }
    showToast(`✓ Fitted to route (${locatedStops.length} stops)`);
  };

  // Selector Action: My GPS location
  const handleCenterOnMyLocation = () => {
    if (!navigator.geolocation) {
      setSearchError('Geolocation is not supported by your browser.');
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lon = pos.coords.longitude;
        const newBbox = getBoundingBox(lat, lon, areaRadiusKm);
        setBbox(newBbox);
        setPackCustomName('Current GPS Location Area');
        if (mapInstanceRef.current) {
          const bounds = L.latLngBounds([newBbox.minLat, newBbox.minLon], [newBbox.maxLat, newBbox.maxLon]);
          mapInstanceRef.current.fitBounds(bounds, { padding: [30, 30], maxZoom: 14 });
        }
        showToast('✓ Map centered on GPS location');
      },
      (err) => {
        setSearchError(`Unable to retrieve GPS location: ${err.message}`);
      },
      { timeout: 8000 }
    );
  };

  // Selector Action: Search city or postcode
  const handleSearchLocation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;

    setIsSearchingLocation(true);
    setSearchError(null);

    try {
      const cleanQuery = searchQuery.trim();
      const isUkPostcode = /^[A-Z]{1,2}\d[A-Z\d]? ?\d[A-Z]{2}$/i.test(cleanQuery);

      if (isUkPostcode) {
        const res = await fetch(`https://api.postcodes.io/postcodes/${encodeURIComponent(cleanQuery.replace(/\s+/g, ''))}`);
        if (res.ok) {
          const data = await res.json();
          if (data.result?.latitude && data.result?.longitude) {
            const lat = data.result.latitude;
            const lon = data.result.longitude;
            const newBbox = getBoundingBox(lat, lon, areaRadiusKm);
            setBbox(newBbox);
            setPackCustomName(`Postcode ${cleanQuery.toUpperCase()}`);
            if (mapInstanceRef.current) {
              const bounds = L.latLngBounds([newBbox.minLat, newBbox.minLon], [newBbox.maxLat, newBbox.maxLon]);
              mapInstanceRef.current.fitBounds(bounds, { padding: [30, 30], maxZoom: 14 });
            }
            setIsSearchingLocation(false);
            showToast(`✓ Located ${cleanQuery.toUpperCase()}`);
            return;
          }
        }
      }

      // Nominatim search
      const nomRes = await fetch(
        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(cleanQuery)}&format=json&limit=1`,
        { headers: { 'User-Agent': 'RouteKeeper-App/1.0' } }
      );
      if (nomRes.ok) {
        const results = await nomRes.json();
        if (results && results.length > 0) {
          const lat = parseFloat(results[0].lat);
          const lon = parseFloat(results[0].lon);
          const newBbox = getBoundingBox(lat, lon, areaRadiusKm);
          setBbox(newBbox);
          const title = results[0].display_name.split(',')[0];
          setPackCustomName(title);
          if (mapInstanceRef.current) {
            const bounds = L.latLngBounds([newBbox.minLat, newBbox.minLon], [newBbox.maxLat, newBbox.maxLon]);
            mapInstanceRef.current.fitBounds(bounds, { padding: [30, 30], maxZoom: 14 });
          }
          setIsSearchingLocation(false);
          showToast(`✓ Located ${title}`);
          return;
        }
      }

      setSearchError(`No location coordinates found for "${searchQuery}".`);
    } catch {
      setSearchError('Search failed. Please check your connection and try again.');
    } finally {
      setIsSearchingLocation(false);
    }
  };

  // Start Batch Download (Automatically Zoom 5 to 17)
  const handleStartDownload = async () => {
    setIsDownloading(true);
    setDownloadSuccess(null);
    setDownloadError(null);
    setProgress(null);

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    try {
      const finalName = packCustomName.trim() || `Selected Area (Zoom 5-17)`;
      const pack = await downloadTerritoryOfflinePack({
        name: finalName,
        bbox,
        zoomMin: 5,
        zoomMax: 17,
        mapService: selectedService,
        signal: abortController.signal,
        onProgress: (p) => setProgress(p),
      });

      setDownloadSuccess(
        `✓ Successfully downloaded ${pack.tileCount.toLocaleString()} tiles (${(pack.sizeBytes / (1024 * 1024)).toFixed(1)} MB) covering Zoom 5 through 17! Ready for zero-signal offline routing.`
      );
      await loadStats();
      window.dispatchEvent(new Event('storage'));
    } catch (err: any) {
      if (err.message && err.message.includes('aborted')) {
        setDownloadError('Download cancelled.');
      } else {
        setDownloadError(`Download failed: ${err.message || 'Unknown network error'}`);
      }
    } finally {
      setIsDownloading(false);
      abortControllerRef.current = null;
    }
  };

  const handleCancelDownload = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
  };

  const handleDeletePack = (packId: string) => {
    deleteOfflinePackRecord(packId);
    loadStats();
    window.dispatchEvent(new Event('storage'));
    showToast('Offline pack removed');
  };

  const handleClearAll = async () => {
    if (confirm('Clear all downloaded offline map tiles and saved area packs?')) {
      await clearAllOfflineTiles();
      await loadStats();
      window.dispatchEvent(new Event('storage'));
      showToast('All offline map data cleared');
    }
  };

  const handleShowPackOnMap = (pack: OfflineMapPack) => {
    if (!mapInstanceRef.current || !pack.bbox) return;
    setBbox(pack.bbox);
    setSelectedService(pack.mapService || 'osm');
    const bounds = L.latLngBounds([pack.bbox.minLat, pack.bbox.minLon], [pack.bbox.maxLat, pack.bbox.maxLon]);
    mapInstanceRef.current.fitBounds(bounds, { padding: [35, 35] });
    showToast(`Showing "${pack.name}" on map`);
  };

  const toggleSimulatedOffline = () => {
    const next = !isSimulatedOffline;
    setIsSimulatedOffline(next);
    localStorage.setItem('routekeeper:simulated-offline', next ? 'true' : 'false');
    window.dispatchEvent(new Event('routekeeper:simulated-offline-change'));
    showToast(next ? '⚡ Test Offline Mode ACTIVE' : '✓ Normal Online Mode restored');
  };

  const locatedStopsCount = stops.filter((s) => s.latitude !== null && s.longitude !== null).length;

  return (
    <div className="flex flex-col gap-4 max-w-3xl mx-auto pb-24">
      {/* Toast Notification */}
      {feedbackToast && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 bg-[#1E453B] text-white px-4 py-2 rounded-2xl shadow-xl text-xs font-bold animate-in fade-in slide-in-from-top-2 border border-[#365349]">
          {feedbackToast}
        </div>
      )}

      {/* Screen Header */}
      <div className="border-b border-[#DDE3D7] pb-3 pt-1">
        <div className="flex items-center justify-between">
          <div>
            <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase block">
              OFFLINE STORAGE & TERRITORIES
            </span>
            <h2 className="text-2xl font-bold tracking-tight text-[#1A2E27] mt-0.5">
              Offline Maps
            </h2>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-extrabold uppercase px-2.5 py-1 rounded-full bg-[#E7ECE2] text-[#1E453B]">
              Fixed Zoom 5 → 17
            </span>
          </div>
        </div>
        <p className="text-xs text-[#748078] mt-1 leading-relaxed">
          Frame your delivery area on the interactive map selector. Downloads all tiles from national overview (Zoom 5) to street level (Zoom 17) for zero-signal routes and summaries.
        </p>
      </div>

      {/* Quick Search & Frame Toolbar */}
      <div className="flex flex-col sm:flex-row items-center gap-2">
        <form onSubmit={handleSearchLocation} className="relative flex-1 w-full">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#748078]" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search town, city or postcode..."
            className="w-full pl-9 pr-20 py-2.5 rounded-xl border border-[#DDE3D7] bg-white text-xs font-medium text-[#1A2E27] focus:outline-none focus:border-[#1E453B] shadow-xs"
          />
          <button
            type="submit"
            disabled={isSearchingLocation}
            className="absolute right-1.5 top-1/2 -translate-y-1/2 px-2.5 py-1 rounded-lg bg-[#1E453B] text-white text-[11px] font-bold hover:bg-[#152F29] disabled:opacity-50 cursor-pointer"
          >
            {isSearchingLocation ? 'Finding...' : 'Jump'}
          </button>
        </form>

        <div className="flex items-center gap-1.5 w-full sm:w-auto shrink-0">
          <button
            type="button"
            onClick={handleCenterOnMyLocation}
            className="flex-1 sm:flex-none px-3 py-2.5 rounded-xl bg-white border border-[#DDE3D7] hover:bg-[#FAFBF7] text-[#1E453B] font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-xs"
            title="Center on device GPS"
          >
            <LocateFixed className="w-3.5 h-3.5" />
            <span>GPS</span>
          </button>

          {locatedStopsCount > 0 && (
            <button
              type="button"
              onClick={handleFitToActiveRoute}
              className="flex-1 sm:flex-none px-3 py-2.5 rounded-xl bg-[#D5EA83] text-[#1E453B] hover:brightness-95 font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-xs"
              title="Fit bounding box around all stops on current route"
            >
              <Compass className="w-3.5 h-3.5" />
              <span>Fit Route</span>
            </button>
          )}

          <button
            type="button"
            onClick={handleFrameVisibleMapView}
            className="flex-1 sm:flex-none px-3.5 py-2.5 rounded-xl bg-[#1E453B] text-white hover:bg-[#152F29] font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-xs"
            title="Set download area to currently visible map bounds"
          >
            <Maximize2 className="w-3.5 h-3.5 text-[#D5EA83]" />
            <span>Use Map View</span>
          </button>
        </div>
      </div>

      {searchError && (
        <div className="p-2.5 bg-amber-50 border border-amber-200 text-amber-900 rounded-xl text-xs flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
          <span>{searchError}</span>
        </div>
      )}

      {/* Interactive Map Area Selector Container */}
      <div className="relative rounded-2xl overflow-hidden border border-[#DDE3D7] bg-[#FAFBF7] shadow-sm">
        <div ref={mapContainerRef} className="h-[360px] sm:h-[420px] w-full z-0" />

        {/* Floating Layer Switcher Pills */}
        <div className="absolute top-2.5 right-2.5 z-[400] bg-white/95 backdrop-blur-md p-1 rounded-xl border border-[#DDE3D7] shadow-md flex items-center gap-1 text-xs">
          <button
            type="button"
            onClick={() => setSelectedService('osm')}
            className={`px-2 py-1 rounded-lg font-bold text-[11px] transition cursor-pointer ${
              selectedService === 'osm' ? 'bg-[#1E453B] text-white' : 'text-[#1A2E27] hover:bg-black/5'
            }`}
          >
            Street (OSM)
          </button>
          <button
            type="button"
            onClick={() => setSelectedService('satellite')}
            className={`px-2 py-1 rounded-lg font-bold text-[11px] transition cursor-pointer ${
              selectedService === 'satellite' ? 'bg-[#1E453B] text-white' : 'text-[#1A2E27] hover:bg-black/5'
            }`}
          >
            Satellite
          </button>
          <button
            type="button"
            onClick={() => setSelectedService('topo')}
            className={`px-2 py-1 rounded-lg font-bold text-[11px] transition cursor-pointer ${
              selectedService === 'topo' ? 'bg-[#1E453B] text-white' : 'text-[#1A2E27] hover:bg-black/5'
            }`}
          >
            Terrain Topo
          </button>
        </div>

        {/* Floating Boundary Helper Tag */}
        <div className="absolute top-2.5 left-2.5 z-[400] bg-white/95 backdrop-blur-md px-3 py-1.5 rounded-xl border border-[#DDE3D7] text-[11px] font-bold text-[#1E453B] shadow-md flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-[#10B981] animate-pulse" />
          <span>Green Box = Tiles to Download</span>
        </div>

        {/* Bottom Floating Stats Tag */}
        <div className="absolute bottom-2.5 left-2.5 right-2.5 z-[400] bg-white/95 backdrop-blur-md px-3.5 py-2 rounded-xl border border-[#DDE3D7] text-xs text-[#1A2E27] shadow-md flex items-center justify-between flex-wrap gap-2">
          <span className="font-semibold text-[#53635d]">
            Area Coverage: <strong className="text-[#1E453B]">{widthKm.toFixed(1)} km × {heightKm.toFixed(1)} km</strong> ({areaKm2} km²)
          </span>
          <span className="text-[11px] text-[#748078] font-mono">
            {centerLat.toFixed(3)}°N, {centerLon.toFixed(3)}°W
          </span>
        </div>
      </div>

      {/* Boundary Span / Radius Slider */}
      <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 shadow-xs flex flex-col gap-2.5">
        <div className="flex items-center justify-between text-xs">
          <span className="font-bold text-[#1A2E27] flex items-center gap-1.5">
            <span>Area Boundary Span:</span>
            <span className="text-[#1E453B] font-extrabold">{areaRadiusKm * 2} km span ({areaRadiusKm} km radius)</span>
          </span>
          <div className="flex items-center gap-1">
            {[5, 8, 12, 16, 20].map((r) => (
              <button
                key={r}
                type="button"
                onClick={() => handleRadiusChange(r)}
                className={`px-2 py-0.5 rounded-lg text-[10px] font-bold cursor-pointer transition ${
                  areaRadiusKm === r
                    ? 'bg-[#1E453B] text-white'
                    : 'bg-[#F8FAFC] border border-[#CBD5E1] text-[#475569] hover:bg-[#E7ECE2]'
                }`}
              >
                {r * 2}km
              </button>
            ))}
          </div>
        </div>
        <input
          type="range"
          min={3}
          max={25}
          step={1}
          value={areaRadiusKm}
          onChange={(e) => handleRadiusChange(Number(e.target.value))}
          className="w-full accent-[#1E453B] cursor-pointer h-2 bg-[#E7ECE2] rounded-lg"
        />
        <div className="flex items-center justify-between text-[11px] text-[#748078]">
          <span>Pan or zoom map, then click "Use Map View" to capture custom bounds</span>
          <button
            type="button"
            onClick={handleFrameVisibleMapView}
            className="text-[#1E453B] font-bold hover:underline cursor-pointer"
          >
            Snap to Current View
          </button>
        </div>
      </div>

      {/* Automatic Download Card (Zoom 5 - 17) */}
      <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 shadow-xs flex flex-col gap-3">
        <div className="flex items-center justify-between border-b border-[#F3F5ED] pb-2.5">
          <div className="flex items-center gap-2">
            <span className="w-8 h-8 rounded-xl bg-[#E7ECE2] text-[#1E453B] flex items-center justify-center font-bold text-sm">
              ⚡
            </span>
            <div>
              <h3 className="font-bold text-sm text-[#1A2E27]">
                Automatic Download Configuration
              </h3>
              <p className="text-[11px] text-[#748078]">
                Fixed Zoom Range: <strong>Zoom 5 → 17</strong> (Continental overview down to street detail)
              </p>
            </div>
          </div>
          <span className="text-sm text-[#1E453B] font-mono font-extrabold">
            {calculatedTiles.length.toLocaleString()} tiles (~{estimatedSizeMB} MB)
          </span>
        </div>

        {/* Live Tile Breakdown Matrix */}
        <div className="grid grid-cols-3 gap-2 text-xs bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl p-3">
          <div className="flex flex-col">
            <span className="text-[10px] text-[#748078] uppercase font-bold">Route Overviews</span>
            <span className="text-xs font-bold text-[#1A2E27] mt-0.5">Zoom 5 – 11</span>
            <span className="text-[10px] text-[#53635d] font-mono">{overviewTilesCount} tiles (~0.9 MB)</span>
          </div>
          <div className="flex flex-col">
            <span className="text-[10px] text-[#748078] uppercase font-bold">City & Districts</span>
            <span className="text-xs font-bold text-[#1A2E27] mt-0.5">Zoom 12 – 14</span>
            <span className="text-[10px] text-[#53635d] font-mono">{regionalTilesCount} tiles</span>
          </div>
          <div className="flex flex-col">
            <span className="text-[10px] text-[#748078] uppercase font-bold">Street Level</span>
            <span className="text-xs font-bold text-[#1A2E27] mt-0.5">Zoom 15 – 17</span>
            <span className="text-[10px] text-[#53635d] font-mono">{streetTilesCount} tiles</span>
          </div>
        </div>

        {/* Custom Pack Name */}
        <div className="flex flex-col gap-1">
          <label className="text-[11px] font-bold text-[#748078] uppercase tracking-wider">
            Area Pack Label
          </label>
          <input
            type="text"
            value={packCustomName}
            onChange={(e) => setPackCustomName(e.target.value)}
            placeholder="e.g. South West Delivery Area"
            className="w-full px-3 py-2 rounded-xl border border-[#DDE3D7] bg-[#FAFBF7] text-xs font-semibold text-[#1A2E27] focus:outline-none focus:border-[#1E453B]"
          />
        </div>

        {/* Download Progress Bar */}
        {isDownloading && progress && (
          <div className="bg-[#1E453B] text-white rounded-2xl p-4 flex flex-col gap-2.5 shadow-lg animate-in fade-in">
            <div className="flex items-center justify-between text-xs">
              <span className="font-bold flex items-center gap-2">
                <RefreshCw className="w-3.5 h-3.5 animate-spin text-[#D5EA83]" />
                <span>Downloading Tiles (Zoom {progress.currentZoom || 5} of 17)...</span>
              </span>
              <span className="font-mono text-[#D5EA83] font-bold">
                {progress.downloaded} / {progress.total} ({progress.percent}%)
              </span>
            </div>

            <div className="w-full h-2.5 bg-black/30 rounded-full overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-[#D5EA83] to-[#10B981] transition-all duration-150"
                style={{ width: `${progress.percent}%` }}
              />
            </div>

            <div className="flex items-center justify-between text-[11px] text-white/80">
              <span>Transferred: {(progress.sizeBytes / (1024 * 1024)).toFixed(1)} MB</span>
              <button
                type="button"
                onClick={handleCancelDownload}
                className="px-2.5 py-1 rounded-lg bg-white/20 hover:bg-white/30 text-white font-bold text-xs cursor-pointer"
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        {/* Success Banner */}
        {downloadSuccess && (
          <div className="p-3 bg-[#D1FAE5] border border-[#A7F3D0] text-[#065F46] rounded-2xl text-xs flex items-center gap-2.5">
            <Check className="w-4 h-4 shrink-0 text-[#059669]" />
            <span>{downloadSuccess}</span>
          </div>
        )}

        {/* Error Banner */}
        {downloadError && (
          <div className="p-3 bg-rose-50 border border-rose-200 text-rose-800 rounded-2xl text-xs flex items-center gap-2.5">
            <AlertTriangle className="w-4 h-4 shrink-0 text-rose-600" />
            <span>{downloadError}</span>
          </div>
        )}

        {/* Main Download Button */}
        {!isDownloading && (
          <button
            type="button"
            onClick={handleStartDownload}
            className="w-full py-3.5 px-4 rounded-2xl bg-[#1E453B] hover:bg-[#152F29] text-white font-bold text-sm flex items-center justify-center gap-2 shadow-md hover:shadow-lg transition cursor-pointer"
          >
            <Download className="w-4 h-4 text-[#D5EA83]" />
            <span>Download Area Tiles ({calculatedTiles.length.toLocaleString()} Tiles · ~{estimatedSizeMB} MB)</span>
          </button>
        )}
      </div>

      {/* Saved Offline Packs Manager */}
      <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 shadow-xs flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <HardDrive className="w-4 h-4 text-[#1E453B]" />
            <h3 className="font-bold text-sm text-[#1A2E27]">
              Saved Offline Area Packs ({storageStats.packs.length})
            </h3>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-[11px] text-[#748078]">
              {storageStats.sizeMB} MB ({storageStats.tileCount.toLocaleString()} tiles stored)
            </span>
            {storageStats.packs.length > 0 && (
              <button
                type="button"
                onClick={handleClearAll}
                className="text-[11px] font-bold text-[#A94F3E] hover:underline cursor-pointer"
              >
                Clear All
              </button>
            )}
          </div>
        </div>

        {storageStats.packs.length === 0 ? (
          <div className="p-4 bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl text-center text-xs text-[#748078] italic">
            No offline areas downloaded yet. Select an area on the map above and tap "Download Area Tiles".
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {storageStats.packs.map((p) => (
              <div
                key={p.id}
                className="p-3 bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl flex items-center justify-between gap-2 text-xs"
              >
                <div className="min-w-0">
                  <div className="font-bold text-[#1A2E27] truncate flex items-center gap-2">
                    <span>{p.name}</span>
                    <span className="text-[10px] font-bold px-1.5 py-0.2 rounded bg-[#E7ECE2] text-[#1E453B] uppercase">
                      {p.mapService.toUpperCase()} · Z{p.zoomMin}-{p.zoomMax}
                    </span>
                  </div>
                  <div className="text-[11px] text-[#748078] mt-0.5">
                    {p.tileCount.toLocaleString()} tiles · {(p.sizeBytes / (1024 * 1024)).toFixed(1)} MB ·{' '}
                    Downloaded {new Date(p.downloadedAt).toLocaleDateString('en-GB')}
                  </div>
                </div>

                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    type="button"
                    onClick={() => handleShowPackOnMap(p)}
                    className="px-2.5 py-1.5 rounded-lg bg-white border border-[#CBD5E1] hover:bg-[#E7ECE2] text-[#1E453B] text-xs font-bold cursor-pointer transition shadow-2xs"
                    title="View this area bounding box on the map"
                  >
                    View on Map
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDeletePack(p.id)}
                    className="p-1.5 rounded-lg hover:bg-rose-100 text-rose-700 cursor-pointer transition"
                    title="Delete pack"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Offline Simulation Testing Toggle */}
      <div className="p-4 bg-white border border-[#DDE3D7] rounded-2xl flex items-center justify-between gap-3 shadow-xs">
        <div className="flex items-center gap-3">
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold ${
            isSimulatedOffline ? 'bg-amber-500 text-white' : 'bg-[#E7ECE2] text-[#1E453B]'
          }`}>
            {isSimulatedOffline ? <WifiOff className="w-5 h-5" /> : <Wifi className="w-5 h-5" />}
          </div>
          <div>
            <h4 className="font-bold text-sm text-[#1A2E27]">
              Test Offline Mode: {isSimulatedOffline ? 'ACTIVE (Disconnected)' : 'Normal (Connected)'}
            </h4>
            <p className="text-xs text-[#748078] mt-0.5">
              Simulates zero cellular signal to test route overviews, summaries, and navigation using purely cached map tiles
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={toggleSimulatedOffline}
          className={`px-3.5 py-2 rounded-xl font-bold text-xs cursor-pointer transition shrink-0 ${
            isSimulatedOffline
              ? 'bg-amber-500 hover:bg-amber-600 text-white shadow-xs'
              : 'bg-white border border-[#CBD5E1] text-[#334155] hover:bg-[#F1F5F9]'
          }`}
        >
          {isSimulatedOffline ? 'Disable Test Mode' : 'Simulate Offline'}
        </button>
      </div>
    </div>
  );
};

export default OfflineMapTab;
