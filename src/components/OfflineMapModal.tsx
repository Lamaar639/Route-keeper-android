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

interface OfflineMapModalProps {
  isOpen: boolean;
  onClose: () => void;
  stops?: DeliveryStop[];
  initialCenter?: Coordinate;
}

export const OfflineMapModal: React.FC<OfflineMapModalProps> = ({
  isOpen,
  onClose,
  stops = [],
  initialCenter,
}) => {
  // Map container & instance for the interactive area selector
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const selectionRectRef = useRef<L.Rectangle | null>(null);
  const centerMarkerRef = useRef<L.Marker | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);

  // Selected Service
  const [selectedService, setSelectedService] = useState<'osm' | 'topo' | 'satellite'>('osm');

  // Max zoom level (user requested: ranging from zoom 5 to highest available)
  const [maxZoom, setMaxZoom] = useState<number>(17);

  // Selected Area Bounding Box
  const [bbox, setBbox] = useState<BoundingBox>(() => {
    // Default initial bounds (approx 10 km around center or initialCenter)
    const baseLat = initialCenter?.latitude ?? 51.5074;
    const baseLon = initialCenter?.longitude ?? -0.1278;
    return getBoundingBox(baseLat, baseLon, 8);
  });

  const [areaRadiusKm, setAreaRadiusKm] = useState<number>(8);
  const [packCustomName, setPackCustomName] = useState<string>('Delivery Area (Zoom 5 - 17)');

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
    if (isOpen) {
      loadStats();
      setDownloadSuccess(null);
      setDownloadError(null);
    }
  }, [isOpen]);

  // Adjust max zoom limit based on service
  useEffect(() => {
    if (selectedService === 'topo' && maxZoom > 17) {
      setMaxZoom(17);
    }
  }, [selectedService, maxZoom]);

  // Calculate center of current bounding box
  const centerLat = (bbox.minLat + bbox.maxLat) / 2;
  const centerLon = (bbox.minLon + bbox.maxLon) / 2;

  // Compute width and height in km
  const heightKm = Math.abs(bbox.maxLat - bbox.minLat) * 110.574;
  const cosLat = Math.cos((centerLat * Math.PI) / 180);
  const widthKm = Math.abs(bbox.maxLon - bbox.minLon) * (111.32 * Math.max(0.2, Math.abs(cosLat)));
  const areaKm2 = (widthKm * heightKm).toFixed(1);

  // Calculate tiles from Zoom 5 to maxZoom
  const effectiveMaxZoom = selectedService === 'topo' ? Math.min(17, maxZoom) : maxZoom;
  const calculatedTiles = computeBoundingBoxTiles(bbox, 5, effectiveMaxZoom, selectedService);

  // Tile breakdown metrics
  const overviewTilesCount = calculatedTiles.filter((t) => t.z >= 5 && t.z <= 11).length;
  const regionalTilesCount = calculatedTiles.filter((t) => t.z >= 12 && t.z <= 14).length;
  const streetTilesCount = calculatedTiles.filter((t) => t.z >= 15).length;
  const estimatedSizeMB = (calculatedTiles.length * 0.0184).toFixed(1);

  // Initialize and maintain modal Leaflet selector map
  useEffect(() => {
    if (!isOpen || !mapContainerRef.current) return;

    if (!mapInstanceRef.current) {
      const map = L.map(mapContainerRef.current, {
        center: [centerLat, centerLon],
        zoom: 11,
        zoomControl: true,
        attributionControl: false,
      });

      const getLayerUrl = (srv: string) => {
        if (srv === 'satellite') {
          return 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
        }
        if (srv === 'topo') {
          return 'https://tile.opentopomap.org/{z}/{x}/{y}.png';
        }
        return 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
      };

      tileLayerRef.current = L.tileLayer(getLayerUrl(selectedService), {
        maxZoom: 19,
      }).addTo(map);

      // Add selection rectangle
      const bounds = L.latLngBounds([bbox.minLat, bbox.minLon], [bbox.maxLat, bbox.maxLon]);
      selectionRectRef.current = L.rectangle(bounds, {
        color: '#10B981',
        weight: 2.5,
        dashArray: '6, 6',
        fillColor: '#10B981',
        fillOpacity: 0.16,
      }).addTo(map);

      // Center crosshair marker
      const pinIcon = L.divIcon({
        className: 'selector-center-pin',
        html: `
          <div style="width: 22px; height: 22px; border-radius: 50%; background: #1E453B; border: 2.5px solid #FFFFFF; box-shadow: 0 2px 8px rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; color: #D5EA83; font-weight: bold; font-size: 11px;">
            ✛
          </div>
        `,
        iconSize: [22, 22],
        iconAnchor: [11, 11],
      });

      centerMarkerRef.current = L.marker([centerLat, centerLon], {
        icon: pinIcon,
        interactive: false,
      }).addTo(map);

      mapInstanceRef.current = map;

      // Invalidate size once rendered
      setTimeout(() => {
        map.invalidateSize();
        map.fitBounds(bounds, { padding: [25, 25], maxZoom: 14 });
      }, 150);
    }

    return () => {
      // Keep instance until modal closes
    };
  }, [isOpen]);

  // Clean up map when modal closes
  useEffect(() => {
    if (!isOpen && mapInstanceRef.current) {
      mapInstanceRef.current.remove();
      mapInstanceRef.current = null;
      selectionRectRef.current = null;
      centerMarkerRef.current = null;
      tileLayerRef.current = null;
    }
  }, [isOpen]);

  // Update map layer when selectedService changes
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
    }

    const getLayerUrl = (srv: string) => {
      if (srv === 'satellite') {
        return 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
      }
      if (srv === 'topo') {
        return 'https://tile.opentopomap.org/{z}/{x}/{y}.png';
      }
      return 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
    };

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

  // Update area radius and compute new bounding box
  const handleRadiusChange = (radiusKm: number) => {
    setAreaRadiusKm(radiusKm);
    const newBbox = getBoundingBox(centerLat, centerLon, radiusKm);
    setBbox(newBbox);
    if (mapInstanceRef.current) {
      const bounds = L.latLngBounds([newBbox.minLat, newBbox.minLon], [newBbox.maxLat, newBbox.maxLon]);
      mapInstanceRef.current.fitBounds(bounds, { padding: [25, 25], maxZoom: 15 });
    }
  };

  // Selector Action: Frame whatever is currently visible on the map view
  const handleFrameVisibleMapView = () => {
    const map = mapInstanceRef.current;
    if (!map) return;
    const bounds = map.getBounds();
    const newBbox: BoundingBox = {
      minLat: bounds.getSouth(),
      maxLat: bounds.getNorth(),
      minLon: bounds.getWest(),
      maxLon: bounds.getEast(),
    };
    setBbox(newBbox);
    setPackCustomName(`Custom Area (${(bounds.getNorth() - bounds.getSouth() > 0.1 ? 'District' : 'Local')})`);
  };

  // Selector Action: Fit to active route stops (if any)
  const handleFitToActiveRoute = () => {
    const locatedStops = stops.filter((s) => s.latitude !== null && s.longitude !== null);
    if (locatedStops.length === 0) return;

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
    setPackCustomName(`Route Coverage (${locatedStops.length} Stops)`);

    if (mapInstanceRef.current) {
      const bounds = L.latLngBounds([newBbox.minLat, newBbox.minLon], [newBbox.maxLat, newBbox.maxLon]);
      mapInstanceRef.current.fitBounds(bounds, { padding: [30, 30] });
    }
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
          mapInstanceRef.current.fitBounds(bounds, { padding: [25, 25], maxZoom: 14 });
        }
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
              mapInstanceRef.current.fitBounds(bounds, { padding: [25, 25], maxZoom: 14 });
            }
            setIsSearchingLocation(false);
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
          setPackCustomName(results[0].display_name.split(',')[0]);
          if (mapInstanceRef.current) {
            const bounds = L.latLngBounds([newBbox.minLat, newBbox.minLon], [newBbox.maxLat, newBbox.maxLon]);
            mapInstanceRef.current.fitBounds(bounds, { padding: [25, 25], maxZoom: 14 });
          }
          setIsSearchingLocation(false);
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

  // Start Batch Download
  const handleStartDownload = async () => {
    setIsDownloading(true);
    setDownloadSuccess(null);
    setDownloadError(null);
    setProgress(null);

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    try {
      const finalName = packCustomName.trim() || `Selected Area (Zoom 5-${effectiveMaxZoom})`;
      const pack = await downloadTerritoryOfflinePack({
        name: finalName,
        bbox,
        zoomMin: 5,
        zoomMax: effectiveMaxZoom,
        mapService: selectedService,
        signal: abortController.signal,
        onProgress: (p) => setProgress(p),
      });

      setDownloadSuccess(
        `Downloaded ${pack.tileCount} tiles (${(pack.sizeBytes / (1024 * 1024)).toFixed(1)} MB) ranging from Zoom 5 to ${effectiveMaxZoom}. Ready for offline zero-signal usage.`
      );
      await loadStats();
      // Notify other components
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
  };

  const handleClearAll = async () => {
    if (confirm('Clear all downloaded offline map tiles and saved area packs?')) {
      await clearAllOfflineTiles();
      await loadStats();
      window.dispatchEvent(new Event('storage'));
    }
  };

  const handleShowPackOnMap = (pack: OfflineMapPack) => {
    if (!mapInstanceRef.current || !pack.bbox) return;
    setBbox(pack.bbox);
    setSelectedService(pack.mapService || 'osm');
    const bounds = L.latLngBounds([pack.bbox.minLat, pack.bbox.minLon], [pack.bbox.maxLat, pack.bbox.maxLon]);
    mapInstanceRef.current.fitBounds(bounds, { padding: [30, 30] });
  };

  const toggleSimulatedOffline = () => {
    const next = !isSimulatedOffline;
    setIsSimulatedOffline(next);
    localStorage.setItem('routekeeper:simulated-offline', next ? 'true' : 'false');
    window.dispatchEvent(new Event('routekeeper:simulated-offline-change'));
  };

  if (!isOpen) return null;

  const locatedStopsCount = stops.filter((s) => s.latitude !== null && s.longitude !== null).length;

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center p-3 z-[2000] overflow-y-auto">
      <div className="bg-white rounded-3xl max-w-2xl w-full p-4 sm:p-5 shadow-2xl flex flex-col gap-4 border border-[#DDE3D7] my-auto animate-in fade-in zoom-in-95 duration-200 max-h-[92vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-[#DDE3D7] pb-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[#1E453B] text-[#D5EA83] flex items-center justify-center font-bold text-lg shadow-xs">
              <Download className="w-5 h-5 stroke-[2.2]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold text-[#1A2E27]">Offline Map Tile Manager</h3>
                <span className="text-[10px] font-extrabold uppercase px-2 py-0.5 rounded-full bg-[#E7ECE2] text-[#1E453B]">
                  100% Free OSM
                </span>
              </div>
              <p className="text-xs text-[#748078] mt-0.5">
                Select any area on the map selector to download all tiles from Zoom 5 to {effectiveMaxZoom}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-full hover:bg-[#F3F5ED] text-[#748078] flex items-center justify-center transition cursor-pointer"
            title="Close"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Search & Location Bar */}
        <div className="flex flex-col sm:flex-row items-center gap-2">
          <form onSubmit={handleSearchLocation} className="relative flex-1 w-full">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#748078]" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search town, city or postcode to frame..."
              className="w-full pl-9 pr-20 py-2 rounded-xl border border-[#DDE3D7] bg-[#FAFBF7] text-xs font-medium text-[#1A2E27] focus:outline-none focus:border-[#1E453B]"
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
              className="flex-1 sm:flex-none px-2.5 py-2 rounded-xl bg-white border border-[#DDE3D7] hover:bg-[#FAFBF7] text-[#1E453B] font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer"
              title="Center on device GPS"
            >
              <LocateFixed className="w-3.5 h-3.5" />
              <span>GPS</span>
            </button>

            {locatedStopsCount > 0 && (
              <button
                type="button"
                onClick={handleFitToActiveRoute}
                className="flex-1 sm:flex-none px-2.5 py-2 rounded-xl bg-[#D5EA83] text-[#1E453B] hover:brightness-95 font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-xs"
                title="Fit bounding box around all stops on current route"
              >
                <Compass className="w-3.5 h-3.5" />
                <span>Fit Route</span>
              </button>
            )}

            <button
              type="button"
              onClick={handleFrameVisibleMapView}
              className="flex-1 sm:flex-none px-2.5 py-2 rounded-xl bg-[#1E453B] text-white hover:bg-[#152F29] font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-xs"
              title="Set download area to currently visible map bounds"
            >
              <Maximize2 className="w-3.5 h-3.5" />
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

        {/* Interactive Map Area Selector Canvas */}
        <div className="relative rounded-2xl overflow-hidden border border-[#DDE3D7] bg-[#FAFBF7] shadow-inner">
          <div ref={mapContainerRef} className="h-56 sm:h-64 w-full z-0" />

          {/* Map Overlay Indicator & Selector Helper */}
          <div className="absolute top-2 left-2 z-[400] bg-white/95 backdrop-blur-md px-2.5 py-1.5 rounded-xl border border-[#DDE3D7] text-[11px] font-bold text-[#1E453B] shadow-md flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-[#10B981] animate-pulse" />
            <span>Green Box = Area to Download</span>
          </div>

          <div className="absolute bottom-2 left-2 right-2 z-[400] bg-white/95 backdrop-blur-md px-3 py-1.5 rounded-xl border border-[#DDE3D7] text-[11px] text-[#1A2E27] shadow-md flex items-center justify-between">
            <span className="font-semibold text-[#53635d]">
              Coverage: <strong className="text-[#1E453B]">{widthKm.toFixed(1)} km × {heightKm.toFixed(1)} km</strong> ({areaKm2} km²)
            </span>
            <span className="text-[10px] text-[#748078] font-mono">
              Center: {centerLat.toFixed(3)}, {centerLon.toFixed(3)}
            </span>
          </div>
        </div>

        {/* Area Dimensions & Radius Slider */}
        <div className="flex flex-col gap-2 p-3 bg-[#FAFBF7] border border-[#DDE3D7] rounded-2xl">
          <div className="flex items-center justify-between text-xs">
            <span className="font-bold text-[#1A2E27] flex items-center gap-1.5">
              <span>Adjust Area Boundary Width:</span>
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
                      : 'bg-white border border-[#CBD5E1] text-[#475569] hover:bg-[#E7ECE2]'
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
        </div>

        {/* Service & Zoom Level Options */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {/* Map Tile Service Provider */}
          <div className="flex flex-col gap-1.5">
            <label className="text-[11px] font-bold text-[#748078] uppercase tracking-wider">
              Free Open-Source Tile Service
            </label>
            <div className="grid grid-cols-3 gap-1.5">
              <button
                type="button"
                onClick={() => setSelectedService('osm')}
                className={`py-2 px-2 rounded-xl text-xs font-bold transition flex flex-col items-center justify-center text-center cursor-pointer border ${
                  selectedService === 'osm'
                    ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                    : 'bg-white text-[#1A2E27] border-[#DDE3D7] hover:bg-[#FAFBF7]'
                }`}
              >
                <span>🗺️ Standard</span>
                <span className="text-[9px] opacity-80 mt-0.5">OpenStreetMap</span>
              </button>

              <button
                type="button"
                onClick={() => setSelectedService('satellite')}
                className={`py-2 px-2 rounded-xl text-xs font-bold transition flex flex-col items-center justify-center text-center cursor-pointer border ${
                  selectedService === 'satellite'
                    ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                    : 'bg-white text-[#1A2E27] border-[#DDE3D7] hover:bg-[#FAFBF7]'
                }`}
              >
                <span>🛰️ Satellite</span>
                <span className="text-[9px] opacity-80 mt-0.5">Free Imagery</span>
              </button>

              <button
                type="button"
                onClick={() => setSelectedService('topo')}
                className={`py-2 px-2 rounded-xl text-xs font-bold transition flex flex-col items-center justify-center text-center cursor-pointer border ${
                  selectedService === 'topo'
                    ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                    : 'bg-white text-[#1A2E27] border-[#DDE3D7] hover:bg-[#FAFBF7]'
                }`}
              >
                <span>⛰️ Terrain</span>
                <span className="text-[9px] opacity-80 mt-0.5">OpenTopoMap</span>
              </button>
            </div>
          </div>

          {/* Max Zoom Level (Zoom 5 to Highest Available) */}
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <label className="text-[11px] font-bold text-[#748078] uppercase tracking-wider">
                Zoom Range (Zoom 5 → Max)
              </label>
              <span className="text-[11px] font-bold text-[#1E453B]">
                Zoom 5 to {effectiveMaxZoom}
              </span>
            </div>
            <div className="grid grid-cols-3 gap-1.5">
              <button
                type="button"
                onClick={() => setMaxZoom(16)}
                className={`py-2 px-2 rounded-xl text-xs font-bold transition flex flex-col items-center justify-center text-center cursor-pointer border ${
                  effectiveMaxZoom === 16
                    ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                    : 'bg-white text-[#1A2E27] border-[#DDE3D7] hover:bg-[#FAFBF7]'
                }`}
              >
                <span>Zoom 16</span>
                <span className="text-[9px] opacity-80 mt-0.5">Street Roads</span>
              </button>

              <button
                type="button"
                onClick={() => setMaxZoom(17)}
                className={`py-2 px-2 rounded-xl text-xs font-bold transition flex flex-col items-center justify-center text-center cursor-pointer border ${
                  effectiveMaxZoom === 17
                    ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                    : 'bg-white text-[#1A2E27] border-[#DDE3D7] hover:bg-[#FAFBF7]'
                }`}
              >
                <span>Zoom 17 ★</span>
                <span className="text-[9px] opacity-80 mt-0.5">Building Outlines</span>
              </button>

              <button
                type="button"
                disabled={selectedService === 'topo'}
                onClick={() => setMaxZoom(18)}
                className={`py-2 px-2 rounded-xl text-xs font-bold transition flex flex-col items-center justify-center text-center cursor-pointer border ${
                  effectiveMaxZoom === 18
                    ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                    : 'bg-white text-[#1A2E27] border-[#DDE3D7] hover:bg-[#FAFBF7]'
                } ${selectedService === 'topo' ? 'opacity-40 cursor-not-allowed' : ''}`}
              >
                <span>Zoom 18</span>
                <span className="text-[9px] opacity-80 mt-0.5">Maximum Porch</span>
              </button>
            </div>
          </div>
        </div>

        {/* Live Tile & Storage Breakdown Banner */}
        <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs font-bold text-[#1A2E27]">
            <span className="flex items-center gap-1.5">
              <HardDrive className="w-4 h-4 text-[#1E453B]" />
              <span>Calculated Tiles (Zoom 5 to {effectiveMaxZoom}):</span>
            </span>
            <span className="text-sm text-[#1E453B] font-extrabold font-mono">
              {calculatedTiles.length.toLocaleString()} tiles (~{estimatedSizeMB} MB)
            </span>
          </div>

          <div className="grid grid-cols-3 gap-2 text-[11px] pt-1 border-t border-[#E7ECE2]">
            <div className="flex flex-col">
              <span className="text-[#748078]">Overviews (Z5-11):</span>
              <strong className="text-[#1A2E27] font-mono">{overviewTilesCount} tiles</strong>
            </div>
            <div className="flex flex-col">
              <span className="text-[#748078]">City Roads (Z12-14):</span>
              <strong className="text-[#1A2E27] font-mono">{regionalTilesCount} tiles</strong>
            </div>
            <div className="flex flex-col">
              <span className="text-[#748078]">Street Detail (Z15-{effectiveMaxZoom}):</span>
              <strong className="text-[#1A2E27] font-mono">{streetTilesCount} tiles</strong>
            </div>
          </div>
        </div>

        {/* Pack Name Field */}
        <div className="flex flex-col gap-1">
          <label className="text-[11px] font-bold text-[#748078] uppercase tracking-wider">
            Offline Pack Label
          </label>
          <input
            type="text"
            value={packCustomName}
            onChange={(e) => setPackCustomName(e.target.value)}
            placeholder="e.g. North London Shift Area"
            className="w-full px-3 py-2 rounded-xl border border-[#DDE3D7] bg-white text-xs font-semibold text-[#1A2E27] focus:outline-none focus:border-[#1E453B]"
          />
        </div>

        {/* Download Progress Display */}
        {isDownloading && progress && (
          <div className="bg-[#1E453B] text-white rounded-2xl p-4 flex flex-col gap-2.5 shadow-lg animate-in fade-in">
            <div className="flex items-center justify-between text-xs">
              <span className="font-bold flex items-center gap-2">
                <RefreshCw className="w-3.5 h-3.5 animate-spin text-[#D5EA83]" />
                <span>Downloading Tiles (Zoom {progress.currentZoom || 5} of {effectiveMaxZoom})...</span>
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

        {/* Success / Error Banners */}
        {downloadSuccess && (
          <div className="p-3 bg-[#D1FAE5] border border-[#A7F3D0] text-[#065F46] rounded-2xl text-xs flex items-center gap-2.5">
            <Check className="w-4 h-4 shrink-0 text-[#059669]" />
            <span>{downloadSuccess}</span>
          </div>
        )}

        {downloadError && (
          <div className="p-3 bg-rose-50 border border-rose-200 text-rose-800 rounded-2xl text-xs flex items-center gap-2.5">
            <AlertTriangle className="w-4 h-4 shrink-0 text-rose-600" />
            <span>{downloadError}</span>
          </div>
        )}

        {/* Primary Download Button */}
        {!isDownloading && (
          <button
            type="button"
            onClick={handleStartDownload}
            className="w-full py-3 px-4 rounded-2xl bg-[#1E453B] hover:bg-[#152F29] text-white font-bold text-sm flex items-center justify-center gap-2 shadow-md hover:shadow-lg transition cursor-pointer"
          >
            <Download className="w-4 h-4 text-[#D5EA83]" />
            <span>Download Selected Area ({calculatedTiles.length.toLocaleString()} Tiles · ~{estimatedSizeMB} MB)</span>
          </button>
        )}

        {/* Saved Packs Section */}
        <div className="border-t border-[#DDE3D7] pt-3 flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-[#1A2E27] flex items-center gap-1.5">
              <span>Saved Offline Packs ({storageStats.packs.length})</span>
              <span className="text-[10px] text-[#748078] font-normal">
                Total Storage: {storageStats.sizeMB} MB ({storageStats.tileCount.toLocaleString()} tiles)
              </span>
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

          {storageStats.packs.length === 0 ? (
            <p className="text-xs text-[#748078] italic bg-[#FAFBF7] p-3 rounded-xl border border-[#DDE3D7] text-center">
              No offline packs stored yet. Select an area above and click "Download Selected Area".
            </p>
          ) : (
            <div className="flex flex-col gap-2 max-h-40 overflow-y-auto pr-1">
              {storageStats.packs.map((p) => (
                <div
                  key={p.id}
                  className="p-2.5 bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl flex items-center justify-between gap-2 text-xs"
                >
                  <div className="min-w-0">
                    <div className="font-bold text-[#1A2E27] truncate flex items-center gap-2">
                      <span>{p.name}</span>
                      <span className="text-[10px] font-normal px-1.5 py-0.2 rounded bg-[#E7ECE2] text-[#1E453B] uppercase">
                        {p.mapService.toUpperCase()} · Z{p.zoomMin}-{p.zoomMax}
                      </span>
                    </div>
                    <div className="text-[11px] text-[#748078] mt-0.5">
                      {p.tileCount.toLocaleString()} tiles · {(p.sizeBytes / (1024 * 1024)).toFixed(1)} MB ·{' '}
                      {new Date(p.downloadedAt).toLocaleDateString('en-GB')}
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => handleShowPackOnMap(p)}
                      className="px-2 py-1 rounded-lg bg-white border border-[#CBD5E1] hover:bg-[#E7ECE2] text-[#1E453B] text-[11px] font-bold cursor-pointer transition"
                      title="Show area on map"
                    >
                      Show
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDeletePack(p.id)}
                      className="p-1 rounded-lg hover:bg-rose-100 text-rose-700 cursor-pointer transition"
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
        <div className="p-3 bg-[#F8FAFC] border border-[#E2E8F0] rounded-2xl flex items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-2.5">
            <div className={`w-8 h-8 rounded-xl flex items-center justify-center font-bold ${
              isSimulatedOffline ? 'bg-amber-500 text-white' : 'bg-slate-200 text-slate-700'
            }`}>
              {isSimulatedOffline ? <WifiOff className="w-4 h-4" /> : <Wifi className="w-4 h-4" />}
            </div>
            <div>
              <span className="font-bold text-[#0F172A] block">
                Test Offline Mode: {isSimulatedOffline ? 'ACTIVE (Disconnected)' : 'Normal (Connected)'}
              </span>
              <span className="text-[11px] text-[#64748B]">
                Forces map views and route summaries to rely purely on local offline cached tiles
              </span>
            </div>
          </div>

          <button
            type="button"
            onClick={toggleSimulatedOffline}
            className={`px-3 py-1.5 rounded-xl font-bold text-xs cursor-pointer transition ${
              isSimulatedOffline
                ? 'bg-amber-500 hover:bg-amber-600 text-white shadow-xs'
                : 'bg-white border border-[#CBD5E1] text-[#334155] hover:bg-[#F1F5F9]'
            }`}
          >
            {isSimulatedOffline ? 'Disable Test Mode' : 'Test Offline Mode'}
          </button>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end pt-2 border-t border-[#DDE3D7]">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-[#1E453B] hover:bg-[#152F29] text-white font-bold text-xs cursor-pointer transition shadow-xs"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};

export default OfflineMapModal;
