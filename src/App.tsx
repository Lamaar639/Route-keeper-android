import React, { useMemo, useState, useRef, useEffect } from 'react';
import {
  Compass,
  MapPin,
  CheckCircle2,
  Circle,
  Archive,
  Settings,
  Plus,
  ArrowRight,
  ArrowUpRight,
  ArrowLeft,
  Upload,
  FileText,
  Navigation,
  RefreshCw,
  Phone,
  Layers,
  Shield,
  Download,
  AlertTriangle,
  Info,
  ChevronRight,
  Copy,
  Check,
  Trash2,
  FileCode,
  Edit2,
  ExternalLink,
  X,
  Play,
  Pause,
  RotateCcw,
  BarChart3,
  Clock,
  Gauge,
  Milestone,
  Video,
} from 'lucide-react';
import LeafletMap from './components/LeafletMap';
import { PWAInstallButton } from './components/PWAInstallButton';
import { OfflineIndicator } from './components/OfflineIndicator';
import OfflineMapModal from './components/OfflineMapModal';
import OfflineMapTab from './components/OfflineMapTab';
import { getOfflineStorageStats } from './services/offlineTileManager';
import { useDeviceLayout } from './hooks/useDeviceLayout';
import { MAP_LINE_COLORS, MAP_MARKER_COLORS } from './constants/mapStyles';
import { useRouteData } from './context/RouteDataContext';
import {
  parseDeliveryListText,
  readDeliveryFile,
  SAMPLE_DELIVERY_NOTE_TEXT,
} from './services/deliveryParser';
import {
  geocodeStops,
  optimizeRoute,
  formatDistance,
  formatDuration,
  calculatePathDistanceMeters,
  solveAlternativeRoutes,
  calculateScheduleWithWindowsAndDwell,
  parseTimeToMinutes,
  formatMinutesToTime,
} from './services/routing';
import { exportRouteAnimationVideo } from './services/videoExporter';
import type {
  DeliveryStop,
  SavedRoute,
  NavAppChoice,
  Coordinate,
  RouteDraft,
  AlternativeRouteOption,
  QuietWaitStop,
  DeliveryTimeWindow,
} from './types/route';

type AppScreen =
  | 'home'
  | 'offline-maps'
  | 'history'
  | 'settings'
  | 'import'
  | 'pre-route-review'
  | 'route'
  | 'history-route'
  | 'stop-detail'
  | 'all-time-map';

type MainTab = 'home' | 'offline-maps' | 'history' | 'settings';

interface NavAppOption {
  id: 'google' | 'apple' | 'waze' | 'citymapper' | 'osm';
  name: string;
  desc: string;
  badge: string;
  iconBg: string;
  getUrl: (stop: DeliveryStop) => string;
}

const NAV_APPS: NavAppOption[] = [
  {
    id: 'google',
    name: 'Google Maps',
    desc: 'Turn-by-turn driving, live traffic, satellite views',
    badge: 'Popular',
    iconBg: '#EA4335',
    getUrl: (stop) =>
      stop.latitude !== null && stop.longitude !== null
        ? `https://www.google.com/maps/dir/?api=1&destination=${stop.latitude},${stop.longitude}`
        : `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(stop.address)}`,
  },
  {
    id: 'apple',
    name: 'Apple Maps',
    desc: 'Native navigation for iOS, iPadOS, and macOS',
    badge: 'Apple',
    iconBg: '#007AFF',
    getUrl: (stop) =>
      stop.latitude !== null && stop.longitude !== null
        ? `https://maps.apple.com/?daddr=${stop.latitude},${stop.longitude}`
        : `https://maps.apple.com/?daddr=${encodeURIComponent(stop.address)}`,
  },
  {
    id: 'waze',
    name: 'Waze',
    desc: 'Real-time hazard warnings, speed traps, road closures',
    badge: 'Live Alerts',
    iconBg: '#33CCFF',
    getUrl: (stop) =>
      stop.latitude !== null && stop.longitude !== null
        ? `https://www.waze.com/ul?ll=${stop.latitude},${stop.longitude}&navigate=yes`
        : `https://www.waze.com/ul?q=${encodeURIComponent(stop.address)}&navigate=yes`,
  },
  {
    id: 'citymapper',
    name: 'Citymapper',
    desc: 'Optimized for dense urban centers and metropolitan streets',
    badge: 'Metro',
    iconBg: '#00C379',
    getUrl: (stop) =>
      stop.latitude !== null && stop.longitude !== null
        ? `https://citymapper.com/directions?endcoord=${stop.latitude}%2C${stop.longitude}&endname=${encodeURIComponent(stop.name)}&endaddress=${encodeURIComponent(stop.address)}`
        : `https://citymapper.com/directions?endaddress=${encodeURIComponent(stop.address)}`,
  },
  {
    id: 'osm',
    name: 'OpenStreetMap',
    desc: 'Open-source car navigation and routing directory',
    badge: 'Open Source',
    iconBg: '#7EBC6F',
    getUrl: (stop) =>
      stop.latitude !== null && stop.longitude !== null
        ? `https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=%3B${stop.latitude}%2C${stop.longitude}`
        : `https://www.openstreetmap.org/search?query=${encodeURIComponent(stop.address)}`,
  },
];

function formatDate(value: string, includeTime = false): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    ...(includeTime ? { hour: '2-digit', minute: '2-digit' } : {}),
  });
}

export default function App() {
  const {
    savedRoutes,
    draft,
    geocodeCache,
    preferences,
    isReady,
    storageError,
    setDraft,
    updateDraft,
    updateDraftStop,
    updateSavedRouteStop,
    updateSavedRoute,
    toggleStopDelivered,
    setGeocodeCache,
    updatePreferences,
    saveDraft,
    clearDraft,
    deleteSavedRoute,
  } = useRouteData();

  const [screen, setScreen] = useState<AppScreen>('home');
  const [previousDetailScreen, setPreviousDetailScreen] = useState<AppScreen>('route');
  const [selectedStop, setSelectedStop] = useState<DeliveryStop | null>(null);
  const [selectedRoute, setSelectedRoute] = useState<SavedRoute | null>(null);
  const [mainTab, setMainTab] = useState<MainTab>('home');

  // Route summary modal state
  const [isSummaryModalOpen, setIsSummaryModalOpen] = useState(false);

  // Route to delete confirmation state
  const [routeToDelete, setRouteToDelete] = useState<SavedRoute | null>(null);

  // Route generation line drawing trigger
  const [routeDrawTrigger, setRouteDrawTrigger] = useState<number>(Date.now());

  // Route driving animation state
  const [isAnimatingRoute, setIsAnimatingRoute] = useState(false);
  const [animationSpeed, setAnimationSpeed] = useState<number>(1);
  const [animationPercent, setAnimationPercent] = useState<number>(0);
  const [animationActiveStopIdx, setAnimationActiveStopIdx] = useState<number>(0);

  // Navigation app modal state
  const [navModalStop, setNavModalStop] = useState<DeliveryStop | null>(null);
  const [rememberNavPreference, setRememberNavPreference] = useState(false);

  // Toast feedback state
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Pre-downloaded Offline Map Tiles Modal State
  const [isOfflineModalOpen, setIsOfflineModalOpen] = useState(false);
  const [offlinePackCount, setOfflinePackCount] = useState<number>(0);

  useEffect(() => {
    let mounted = true;
    const refreshOfflineStats = async () => {
      try {
        const stats = await getOfflineStorageStats();
        if (mounted) setOfflinePackCount(stats.packs.length);
      } catch {
        // ignore
      }
    };
    refreshOfflineStats();
    window.addEventListener('storage', refreshOfflineStats);
    window.addEventListener('routekeeper:simulated-offline-change', refreshOfflineStats);
    return () => {
      mounted = false;
      window.removeEventListener('storage', refreshOfflineStats);
      window.removeEventListener('routekeeper:simulated-offline-change', refreshOfflineStats);
    };
  }, [isOfflineModalOpen]);

  // Import state
  const [parsedStops, setParsedStops] = useState<DeliveryStop[]>([]);
  const [sourceName, setSourceName] = useState('');
  const [pastedText, setPastedText] = useState('');
  const [activeImportMode, setActiveImportMode] = useState<'upload' | 'paste'>('upload');
  const [importError, setImportError] = useState<string | null>(null);
  const [isReadingFile, setIsReadingFile] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Device & layout responsiveness
  const device = useDeviceLayout();
  const [restStopCount, setRestStopCount] = useState<number>(2);
  const [currentCockpitStopIndex, setCurrentCockpitStopIndex] = useState<number>(0);

  // Routing state
  const [routeError, setRouteError] = useState<string | null>(null);
  const [isPlanningRoute, setIsPlanningRoute] = useState(false);
  const [planningProgress, setPlanningProgress] = useState({ completed: 0, total: 0 });
  const [routeViewMode, setRouteViewMode] = useState<'map' | 'list' | 'summary'>('list');
  const [departureStartTime, setDepartureStartTime] = useState<string>('09:00');
  const [defaultDwellMinutes, setDefaultDwellMinutes] = useState<number>(6.5);

  // Export state
  const [exportRequest, setExportRequest] = useState(0);
  const [isExporting, setIsExporting] = useState(false);
  const [mapMessage, setMapMessage] = useState<string | null>(null);
  const [copiedStopId, setCopiedStopId] = useState<string | null>(null);

  // Stop editing state
  const [isEditingAddress, setIsEditingAddress] = useState(false);
  const [editedAddress, setEditedAddress] = useState('');
  const [inlineEditingStopId, setInlineEditingStopId] = useState<string | null>(null);
  const [inlineAddressText, setInlineAddressText] = useState('');

  // Video Animation Export state
  const [isExportingVideo, setIsExportingVideo] = useState(false);
  const [videoExportProgress, setVideoExportProgress] = useState(0);
  const [videoExportStage, setVideoExportStage] = useState('');
  const [videoExportError, setVideoExportError] = useState<string | null>(null);

  const activeStops = draft?.stops ?? [];
  const locatedHistory = useMemo(
    () =>
      savedRoutes
        .flatMap((route) => route.stops)
        .filter((stop) => stop.latitude !== null && stop.longitude !== null),
    [savedRoutes],
  );

  const historicalPaths = useMemo(
    () =>
      savedRoutes
        .map((route) =>
          route.path.length > 0
            ? route.path
            : route.stops.flatMap((stop) =>
                stop.latitude === null || stop.longitude === null
                  ? []
                  : [{ latitude: stop.latitude, longitude: stop.longitude }],
              ),
        )
        .filter((path) => path.length > 0),
    [savedRoutes],
  );

  const nextPendingStop = useMemo(() => {
    return activeStops.find((s) => !s.delivered) || activeStops[0];
  }, [activeStops]);

  const nextPendingIndex = useMemo(() => {
    if (!nextPendingStop) return 0;
    const idx = activeStops.findIndex((s) => s.id === nextPendingStop.id);
    return idx >= 0 ? idx : 0;
  }, [activeStops, nextPendingStop]);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage(null);
    }, 2400);
  };

  const navTo = (tab: MainTab) => {
    setMainTab(tab);
    setScreen(tab);
    setIsAnimatingRoute(false);
  };

  const goBack = () => {
    setIsAnimatingRoute(false);
    if (screen === 'stop-detail') {
      setScreen(previousDetailScreen);
    } else if (screen === 'history-route') {
      setScreen('history');
    } else if (screen === 'all-time-map') {
      setScreen('settings');
    } else if (screen === 'pre-route-review') {
      setScreen('import');
    } else if (screen === 'route') {
      setScreen('pre-route-review');
    } else {
      setScreen('home');
      setMainTab('home');
    }
  };

  const openNewRoute = () => {
    setParsedStops([]);
    setSourceName('');
    setPastedText('');
    setImportError(null);
    setScreen('import');
  };

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    if (file.size > 40 * 1024 * 1024) {
      setImportError('This file is over 40 MB. Choose a smaller delivery note.');
      return;
    }

    setImportError(null);
    setIsReadingFile(true);
    try {
      const text = await readDeliveryFile(file);
      const foundStops = parseDeliveryListText(text);
      if (foundStops.length === 0) {
        setImportError(
          'No complete customer rows were found. Make sure the file has customer names, phone numbers, and addresses. Scanned image-only PDFs are not supported.',
        );
        return;
      }
      setParsedStops(foundStops);
      setSourceName(file.name);
      // Automatically synchronize departure time with the earliest delivery slot from paperwork
      const windowStarts = foundStops
        .filter((s) => s.timeWindow)
        .map((s) => s.timeWindow!.startTime)
        .sort();
      if (windowStarts.length > 0) {
        setDepartureStartTime(windowStarts[0]);
      }
    } catch (err) {
      setImportError(
        err instanceof Error
          ? `Could not read file: ${err.message}`
          : 'Could not read file. Try a text-based PDF or plain-text file.',
      );
    } finally {
      setIsReadingFile(false);
      if (event.target) event.target.value = '';
    }
  };

  const loadSampleNote = () => {
    setImportError(null);
    const foundStops = parseDeliveryListText(SAMPLE_DELIVERY_NOTE_TEXT);
    setParsedStops(foundStops);
    setSourceName('Sample Delivery Note (Central London)');
    setPastedText(SAMPLE_DELIVERY_NOTE_TEXT);
    const windowStarts = foundStops
      .filter((s) => s.timeWindow)
      .map((s) => s.timeWindow!.startTime)
      .sort();
    if (windowStarts.length > 0) {
      setDepartureStartTime(windowStarts[0]);
    }
  };

  const parsePastedContent = () => {
    if (!pastedText.trim()) return;
    const foundStops = parseDeliveryListText(pastedText);
    if (foundStops.length === 0) {
      setImportError(
        'No customer stops recognized in pasted text. Format as: Customer Name, Phone, and Address.',
      );
      return;
    }
    setParsedStops(foundStops);
    setSourceName('Pasted delivery note');
    setImportError(null);
    const windowStarts = foundStops
      .filter((s) => s.timeWindow)
      .map((s) => s.timeWindow!.startTime)
      .sort();
    if (windowStarts.length > 0) {
      setDepartureStartTime(windowStarts[0]);
    }
  };

  const continueWithImportedStops = async () => {
    if (parsedStops.length === 0) return;
    setRouteError(null);
    setScreen('pre-route-review');

    // Pre-geocode addresses so unlocated ones are immediately identified in Pre-Route Review
    const hasPending = parsedStops.some((s) => s.geocodeStatus === 'pending');
    if (hasPending) {
      setIsPlanningRoute(true);
      setPlanningProgress({ completed: 0, total: parsedStops.length });

      try {
        const geocoded = await geocodeStops(parsedStops, geocodeCache, (completed, total) => {
          setPlanningProgress({ completed, total });
        });
        setGeocodeCache(geocoded.cache);
        setParsedStops(geocoded.stops);
      } catch {
        // continue to review anyway
      } finally {
        setIsPlanningRoute(false);
      }
    }
  };

  const planRouteFromReview = async () => {
    if (parsedStops.length === 0) return;
    setRouteError(null);
    setIsPlanningRoute(true);
    setPlanningProgress({ completed: 0, total: parsedStops.length });

    try {
      // 1. Geocode any pending/unlocated stops
      const geocoded = await geocodeStops(parsedStops, geocodeCache, (completed, total) => {
        setPlanningProgress({ completed, total });
      });
      setGeocodeCache(geocoded.cache);

      // 2. Solve 3 alternative route options (Fastest, Shortest, Alternative Order)
      // and calculate schedules with 5-8 min dwell times and 1-4 balanced rest stops
      const alternatives = await solveAlternativeRoutes(
        geocoded.stops,
        departureStartTime,
        defaultDwellMinutes,
        restStopCount,
      );
      const fastest = alternatives.find((a) => a.id === 'fastest') || alternatives[0];

      // 3. Initialize and update draft
      setDraft(fastest.stops, sourceName || 'Delivery list');
      updateDraft({
        stops: fastest.stops,
        path: fastest.path,
        routingMode: 'road-network',
        distanceMeters: fastest.distanceMeters,
        durationSeconds: fastest.durationSeconds,
        steps: fastest.steps,
        departureStartTime,
        defaultDwellMinutes,
        restStopCount,
        waitStops: fastest.waitStops,
        alternativeRoutes: alternatives,
        selectedAlternativeId: 'fastest',
      });

      // 5. Trigger line-drawing route generation animation on map & summary view
      setRouteDrawTrigger(Date.now());
      setRouteViewMode('summary');
      setScreen('route');

      if (geocoded.failedCount > 0) {
        setRouteError(
          `${geocoded.failedCount} ${geocoded.failedCount === 1 ? 'address' : 'addresses'} could not be mapped. You can edit them at any time from the stop list or summary.`,
        );
      }
    } catch (err) {
      setRouteError(err instanceof Error ? err.message : 'Could not plan route.');
    } finally {
      setIsPlanningRoute(false);
    }
  };

  const handleSelectAlternativeRoute = (altId: 'fastest' | 'shortest' | 'alternative') => {
    if (!draft || !draft.alternativeRoutes) return;
    const choice = draft.alternativeRoutes.find((a) => a.id === altId);
    if (!choice) return;

    updateDraft({
      selectedAlternativeId: altId,
      stops: choice.stops,
      path: choice.path,
      distanceMeters: choice.distanceMeters,
      durationSeconds: choice.durationSeconds,
      waitStops: choice.waitStops,
      steps: choice.steps,
    });

    setRouteDrawTrigger(Date.now());
    showToast(`✓ Switched to ${choice.title}! Route preview updated.`);
  };

  // Dynamically update the exact number of rest/parking stops created on the route
  const handleRestStopCountChange = (newCount: number) => {
    const clamped = Math.max(1, Math.min(4, Math.round(newCount)));
    setRestStopCount(clamped);

    // If review parsed stops exist, update them with the new break schedule
    if (parsedStops.length > 0) {
      const scheduled = calculateScheduleWithWindowsAndDwell(
        parsedStops,
        departureStartTime,
        defaultDwellMinutes,
        clamped,
      );
      setParsedStops(scheduled.stops);
    }

    // If active draft exists, immediately recalculate the breaks and update draft waitStops
    if (draft && draft.stops.length > 0) {
      const scheduled = calculateScheduleWithWindowsAndDwell(
        draft.stops,
        draft.departureStartTime || departureStartTime,
        draft.defaultDwellMinutes || defaultDwellMinutes,
        clamped,
      );

      let updatedAlternatives = draft.alternativeRoutes;
      if (draft.alternativeRoutes && draft.alternativeRoutes.length > 0) {
        updatedAlternatives = draft.alternativeRoutes.map((alt) => {
          const altScheduled = calculateScheduleWithWindowsAndDwell(
            alt.stops,
            draft.departureStartTime || departureStartTime,
            draft.defaultDwellMinutes || defaultDwellMinutes,
            clamped,
          );
          return {
            ...alt,
            stops: altScheduled.stops,
            waitStops: altScheduled.waitStops,
          };
        });
      }

      updateDraft({
        restStopCount: clamped,
        stops: scheduled.stops,
        waitStops: scheduled.waitStops,
        alternativeRoutes: updatedAlternatives,
      });

      showToast(`✓ Scheduled exactly ${scheduled.waitStops.length} break ${scheduled.waitStops.length === 1 ? 'stop' : 'stops'} on route`);
    }
  };

  const planRoute = async () => {
    if (!draft || activeStops.length === 0) return;
    setRouteError(null);
    setIsPlanningRoute(true);
    setPlanningProgress({ completed: 0, total: activeStops.length });

    try {
      const geocoded = await geocodeStops(activeStops, geocodeCache, (completed, total) => {
        setPlanningProgress({ completed, total });
      });
      setGeocodeCache(geocoded.cache);

      const alternatives = await solveAlternativeRoutes(
        geocoded.stops,
        draft.departureStartTime || departureStartTime,
        draft.defaultDwellMinutes || defaultDwellMinutes,
        draft.restStopCount || restStopCount,
      );
      const fastest = alternatives.find((a) => a.id === 'fastest') || alternatives[0];

      updateDraft({
        stops: fastest.stops,
        path: fastest.path,
        routingMode: 'road-network',
        distanceMeters: fastest.distanceMeters,
        durationSeconds: fastest.durationSeconds,
        steps: fastest.steps,
        waitStops: fastest.waitStops,
        alternativeRoutes: alternatives,
        selectedAlternativeId: 'fastest',
      });

      setRouteDrawTrigger(Date.now());
      setRouteViewMode('summary');

      if (geocoded.failedCount > 0) {
        setRouteError(
          `${geocoded.failedCount} ${geocoded.failedCount === 1 ? 'address could' : 'addresses could'} not be matched on OpenStreetMap.`,
        );
      }
    } catch (err) {
      setRouteError(err instanceof Error ? err.message : 'We could not plan this route.');
    } finally {
      setIsPlanningRoute(false);
    }
  };

  const finishAndSaveRoute = () => {
    const saved = saveDraft();
    if (!saved) return;
    clearDraft();
    setScreen('home');
    setMainTab('home');
    showToast(`Run "${saved.name}" saved to Old Routes!`);
  };

  const openActiveStop = (stop: DeliveryStop) => {
    setSelectedStop(stop);
    setSelectedRoute(null);
    setPreviousDetailScreen('route');
    setIsEditingAddress(false);
    setEditedAddress(stop.address);
    setScreen('stop-detail');
  };

  const openHistoryRoute = (route: SavedRoute, autoAnimate = false) => {
    setSelectedRoute(route);
    setIsAnimatingRoute(autoAnimate);
    setScreen('history-route');
  };

  const openHistoryStop = (stop: DeliveryStop) => {
    setSelectedStop(stop);
    setPreviousDetailScreen('history-route');
    setIsEditingAddress(false);
    setEditedAddress(stop.address);
    setScreen('stop-detail');
  };

  // Launch navigation app
  const triggerNavigation = (stop: DeliveryStop, appChoice?: NavAppChoice) => {
    const chosenApp = appChoice || preferences.defaultNavApp;

    if (!chosenApp || chosenApp === 'ask') {
      setNavModalStop(stop);
      return;
    }

    const appConfig = NAV_APPS.find((a) => a.id === chosenApp);
    if (appConfig) {
      const url = appConfig.getUrl(stop);
      window.open(url, '_blank');
    }
  };

  const handleSelectNavApp = (appId: NavAppOption['id']) => {
    if (!navModalStop) return;

    if (rememberNavPreference) {
      updatePreferences({ defaultNavApp: appId });
    }

    const appConfig = NAV_APPS.find((a) => a.id === appId);
    if (appConfig) {
      const url = appConfig.getUrl(navModalStop);
      window.open(url, '_blank');
    }

    setNavModalStop(null);
  };

  // Robust Tick Toggle Handler
  const handleToggleStop = (stopId: string, currentStatus?: boolean, routeId?: string) => {
    const targetStop =
      (routeId ? savedRoutes.find((r) => r.id === routeId)?.stops : draft?.stops)?.find(
        (s) => s.id === stopId,
      ) ??
      selectedRoute?.stops.find((s) => s.id === stopId) ??
      (selectedStop?.id === stopId ? selectedStop : null);

    const isCurrentlyDelivered =
      currentStatus !== undefined ? currentStatus : !!targetStop?.delivered;
    const nextStatus = !isCurrentlyDelivered;

    toggleStopDelivered(stopId, nextStatus, routeId);

    // Update local state if viewing this stop or this route
    if (selectedStop?.id === stopId) {
      setSelectedStop((prev) =>
        prev
          ? {
              ...prev,
              delivered: nextStatus,
              deliveredAt: nextStatus ? new Date().toISOString() : undefined,
            }
          : null,
      );
    }

    if (selectedRoute && (!routeId || selectedRoute.id === routeId)) {
      setSelectedRoute((prev) =>
        prev
          ? {
              ...prev,
              stops: prev.stops.map((s) =>
                s.id === stopId
                  ? {
                      ...s,
                      delivered: nextStatus,
                      deliveredAt: nextStatus ? new Date().toISOString() : undefined,
                    }
                  : s,
              ),
            }
          : null,
      );
    }

    const stopName = targetStop?.name ? ` "${targetStop.name}"` : '';
    showToast(nextStatus ? `✓ Stop${stopName} marked completed!` : `Delivery${stopName} marked pending.`);
  };

  const copyToClipboard = (text: string, stopId: string) => {
    navigator.clipboard.writeText(text);
    setCopiedStopId(stopId);
    setTimeout(() => setCopiedStopId(null), 2000);
  };

  const handleSaveEditedAddress = async (
    targetStop?: DeliveryStop,
    overrideAddress?: string,
  ) => {
    const stopToEdit = targetStop || selectedStop;
    const newAddr = (overrideAddress !== undefined ? overrideAddress : editedAddress).trim();
    if (!stopToEdit || !newAddr) return;
    setIsPlanningRoute(true);

    try {
      const singleGeocode = await geocodeStops(
        [{ ...stopToEdit, address: newAddr }],
        geocodeCache,
      );
      const updated = singleGeocode.stops[0];
      setGeocodeCache(singleGeocode.cache);

      const updates: Partial<DeliveryStop> = {
        address: newAddr,
        latitude: updated.latitude,
        longitude: updated.longitude,
        geocodeStatus: updated.geocodeStatus,
        geocodedBy: updated.geocodedBy,
        geocodeError: updated.geocodeError,
      };

      // 0. Update in parsedStops (for Pre-Route Review screen)
      setParsedStops((prev) =>
        prev.map((s) => (s.id === stopToEdit.id ? { ...s, ...updates } : s)),
      );

      // 1. Update in draft if stop exists in draft
      if (draft && draft.stops.some((s) => s.id === stopToEdit.id)) {
        updateDraftStop(stopToEdit.id, updates);

        const updatedStops = draft.stops.map((s) =>
          s.id === stopToEdit.id ? { ...s, ...updates } : s,
        );
        const mappedStops = updatedStops.filter(
          (s) => s.latitude !== null && s.longitude !== null,
        );

        if (mappedStops.length > 1) {
          try {
            const alternatives = await solveAlternativeRoutes(
              updatedStops,
              draft.departureStartTime || departureStartTime,
              draft.defaultDwellMinutes || defaultDwellMinutes,
              draft.restStopCount || restStopCount,
            );
            const activeAlt =
              alternatives.find((a) => a.id === draft.selectedAlternativeId) || alternatives[0];

            updateDraft({
              stops: activeAlt.stops,
              path: activeAlt.path,
              routingMode: 'road-network',
              distanceMeters: activeAlt.distanceMeters,
              durationSeconds: activeAlt.durationSeconds,
              steps: activeAlt.steps,
              waitStops: activeAlt.waitStops,
              alternativeRoutes: alternatives,
            });
          } catch {
            const fallback = mappedStops.map((s) => ({
              latitude: s.latitude as number,
              longitude: s.longitude as number,
            }));
            const dist = calculatePathDistanceMeters(fallback);
            updateDraft({
              stops: updatedStops,
              path: fallback,
              routingMode: 'approximate',
              distanceMeters: dist,
              durationSeconds: Math.round((dist / 11.1) * 1.3),
            });
          }
        } else if (mappedStops.length === 1) {
          updateDraft({
            stops: updatedStops,
            path: [{ latitude: mappedStops[0].latitude!, longitude: mappedStops[0].longitude! }],
            distanceMeters: 0,
          });
        }
      }

      // 2. Update in saved routes if stop exists in any saved route
      savedRoutes.forEach((route) => {
        if (route.stops.some((s) => s.id === stopToEdit.id)) {
          updateSavedRouteStop(route.id, stopToEdit.id, updates);
        }
      });

      // 3. Update in selectedRoute state if viewing history route
      if (selectedRoute && selectedRoute.stops.some((s) => s.id === stopToEdit.id)) {
        const updatedSavedStops = selectedRoute.stops.map((s) =>
          s.id === stopToEdit.id ? { ...s, ...updates } : s,
        );
        const mappedSavedStops = updatedSavedStops.filter(
          (s) => s.latitude !== null && s.longitude !== null,
        );

        if (mappedSavedStops.length > 1) {
          try {
            const alternatives = await solveAlternativeRoutes(
              updatedSavedStops,
              selectedRoute.departureStartTime || departureStartTime,
              selectedRoute.defaultDwellMinutes || defaultDwellMinutes,
              selectedRoute.restStopCount || restStopCount,
            );
            const activeAlt =
              alternatives.find((a) => a.id === selectedRoute.selectedAlternativeId) || alternatives[0];

            const newSaved: SavedRoute = {
              ...selectedRoute,
              stops: activeAlt.stops,
              path: activeAlt.path,
              routingMode: 'road-network',
              distanceMeters: activeAlt.distanceMeters,
              durationSeconds: activeAlt.durationSeconds,
              steps: activeAlt.steps,
              waitStops: activeAlt.waitStops,
              alternativeRoutes: alternatives,
            };
            setSelectedRoute(newSaved);
            updateSavedRoute(selectedRoute.id, newSaved);
          } catch {
            const fallback = mappedSavedStops.map((s) => ({
              latitude: s.latitude as number,
              longitude: s.longitude as number,
            }));
            const dist = calculatePathDistanceMeters(fallback);
            const newSaved: SavedRoute = {
              ...selectedRoute,
              stops: updatedSavedStops,
              path: fallback,
              routingMode: 'approximate',
              distanceMeters: dist,
              durationSeconds: Math.round((dist / 11.1) * 1.3),
            };
            setSelectedRoute(newSaved);
            updateSavedRoute(selectedRoute.id, newSaved);
          }
        } else {
          const newSaved: SavedRoute = {
            ...selectedRoute,
            stops: updatedSavedStops,
          };
          setSelectedRoute(newSaved);
          updateSavedRoute(selectedRoute.id, newSaved);
        }
      }

      // 4. Update in selectedStop state if currently selected
      if (selectedStop && selectedStop.id === stopToEdit.id) {
        setSelectedStop({
          ...selectedStop,
          ...updates,
        });
      }

      setIsEditingAddress(false);
      setInlineEditingStopId(null);
      setRouteDrawTrigger(Date.now());

      if (updated.latitude !== null && updated.longitude !== null) {
        showToast(`✓ Address located & pinned! Route preview updated.`);
      } else {
        showToast(`Address saved, but street coordinates could not be found.`);
      }
    } finally {
      setIsPlanningRoute(false);
    }
  };

  const handleExportRouteVideo = async (targetRoute: RouteDraft | SavedRoute) => {
    if (!targetRoute) return;
    const locatedStops = targetRoute.stops.filter(
      (s) => s.latitude !== null && s.longitude !== null,
    );
    if (locatedStops.length < 2) {
      showToast('Need at least 2 mapped delivery stops to export an animation video.');
      return;
    }

    setIsExportingVideo(true);
    setVideoExportProgress(5);
    setVideoExportStage('Preparing map tiles and animation capture...');
    setVideoExportError(null);

    try {
      const result = await exportRouteAnimationVideo({
        routeName: targetRoute.name,
        stops: targetRoute.stops,
        path: targetRoute.path || [],
        markerColor: preferences.markerColor,
        lineColor: preferences.lineColor,
        onProgress: (pct, stage) => {
          setVideoExportProgress(pct);
          setVideoExportStage(stage);
        },
      });

      const url = URL.createObjectURL(result.blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = result.filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setTimeout(() => URL.revokeObjectURL(url), 6000);

      showToast(`Exported ${result.filename}!`);
      setTimeout(() => {
        setIsExportingVideo(false);
      }, 1000);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Animation video export failed';
      setVideoExportError(msg);
      showToast(`Video export failed: ${msg}`);
    }
  };

  const exportAllTimeMap = (dataUrl: string) => {
    setIsExporting(true);
    try {
      const link = document.createElement('a');
      link.href = dataUrl;
      link.download = `routekeeper-delivery-map-${new Date().toISOString().slice(0, 10)}.jpg`;
      link.click();
      setMapMessage('High-resolution map image downloaded successfully!');
    } catch {
      setMapMessage('Export download failed. Please try again.');
    } finally {
      setIsExporting(false);
    }
  };

  const requestExport = () => {
    if (locatedHistory.length === 0) {
      setMapMessage('Save at least one route with located stops to export an overview map.');
      return;
    }
    setMapMessage(null);
    setExportRequest((c) => c + 1);
  };

  if (!isReady) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-[#F3F5ED] text-[#1E453B]">
        <div className="w-8 h-8 border-3 border-[#1E453B] border-t-transparent rounded-full animate-spin mb-3" />
        <span className="text-sm font-semibold tracking-wide">Opening RouteKeeper…</span>
      </div>
    );
  }

  const isMainTab =
    screen === 'home' ||
    screen === 'offline-maps' ||
    screen === 'history' ||
    screen === 'settings' ||
    screen === 'route' ||
    screen === 'history-route';
  const activeRouteLocated = activeStops.filter((s) => s.latitude !== null && s.longitude !== null).length;
  const unlocatedStopsCount = activeStops.filter((s) => s.geocodeStatus === 'not-found').length;
  const deliveredCount = activeStops.filter((s) => s.delivered).length;

  // Active or Current displayed route for summary metrics
  const currentSummaryRoute = screen === 'history-route' && selectedRoute ? selectedRoute : draft;
  const summaryStops = currentSummaryRoute?.stops ?? [];
  const summaryDeliveredCount = summaryStops.filter((s) => s.delivered).length;
  const summaryPercent = summaryStops.length > 0 ? Math.round((summaryDeliveredCount / summaryStops.length) * 100) : 0;
  const summaryDistance =
    currentSummaryRoute?.distanceMeters ??
    (currentSummaryRoute?.path ? calculatePathDistanceMeters(currentSummaryRoute.path) : undefined);
  const summaryDuration = currentSummaryRoute?.durationSeconds;

  return (
    <div className="min-h-screen bg-[#F3F5ED] flex flex-col items-center text-[#1A2E27]">
      {/* Toast Notification Banner */}
      {toastMessage && (
        <div className="fixed top-4 z-[9999] bg-[#1E453B] text-[#D5EA83] font-bold text-xs px-4 py-2.5 rounded-full shadow-lg flex items-center gap-2 border border-[#D5EA83]/40 animate-in fade-in slide-in-from-top-4 duration-200">
          <Check className="w-4 h-4 stroke-[3]" />
          <span>{toastMessage}</span>
        </div>
      )}

      <main
        className={
          device.isDashmountLandscape && (screen === 'route' || screen === 'history-route')
            ? 'w-full h-screen p-2.5 flex flex-col gap-2 overflow-hidden'
            : device.isDesktop
            ? (screen === 'route' || screen === 'history-route'
                ? 'w-full max-w-7xl px-6 py-5 pb-12 flex flex-col gap-4'
                : 'w-full max-w-5xl px-6 py-6 pb-12 flex flex-col gap-5')
            : 'w-full max-w-xl px-4 py-5 pb-24 md:pb-12 flex flex-col gap-4'
        }
      >
        {/* Storage error warning */}
        {storageError && screen !== 'settings' && (
          <div className="bg-[#F8E9E4] border border-[#EBCAC0] text-[#A94F3E] text-xs px-3.5 py-2.5 rounded-xl flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{storageError}</span>
          </div>
        )}

        {/* HOME SCREEN */}
        {screen === 'home' && (
          <>
            {/* Brand Header */}
            <header className="flex items-center justify-between border-b border-[#DDE3D7] pb-3 pt-1">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-[#1E453B] flex items-center justify-center text-[#D5EA83] shadow-xs">
                  <Compass className="w-5 h-5 stroke-[2.2]" />
                </div>
                <div>
                  <h1 className="font-bold text-base tracking-tight text-[#1A2E27] leading-none">
                    RouteKeeper
                  </h1>
                  <span className="text-[10px] tracking-widest font-bold text-[#748078] uppercase mt-0.5 block">
                    DELIVERY ROUTES
                  </span>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <PWAInstallButton />
                <button
                  type="button"
                  onClick={() => navTo('settings')}
                  className="w-9 h-9 rounded-xl border border-[#DDE3D7] bg-white flex items-center justify-center text-[#1A2E27] hover:bg-[#E7ECE2] transition-colors cursor-pointer"
                  title="Settings"
                >
                  <Settings className="w-4 h-4" />
                </button>
              </div>
            </header>

            {/* Intro Header */}
            <div className="pt-2">
              <span className="text-[11px] font-bold tracking-widest text-[#1E453B] uppercase">
                {new Date().toLocaleDateString('en-GB', {
                  weekday: 'long',
                  day: 'numeric',
                  month: 'long',
                })}
              </span>
              <h2 className="text-3xl font-extrabold tracking-tight text-[#1A2E27] mt-1 leading-tight">
                Your day,<br />well routed.
              </h2>
              <p className="text-sm text-[#748078] mt-1.5 leading-relaxed">
                Keep every delivery in the optimal driving order, mark completed stops with 1-click, and navigate using your preferred map app.
              </p>
            </div>

            {/* New Route Hero Card */}
            <button
              type="button"
              onClick={openNewRoute}
              className="text-left w-full rounded-2xl p-5 bg-gradient-to-br from-[#1E453B] to-[#152F29] text-white shadow-md hover:shadow-lg transition-all transform active:scale-[0.99] group cursor-pointer"
            >
              <div className="flex items-start justify-between">
                <div className="w-10 h-10 rounded-xl bg-[#D5EA83] text-[#273626] flex items-center justify-center shadow-xs">
                  <Plus className="w-6 h-6 stroke-[2.5]" />
                </div>
                <ArrowUpRight className="w-6 h-6 text-white/80 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-transform" />
              </div>
              <div className="mt-4">
                <span className="text-[10px] font-bold tracking-widest text-[#D5EA83] uppercase block">
                  START HERE
                </span>
                <h3 className="text-2xl font-bold tracking-tight text-white mt-0.5">
                  New Route
                </h3>
                <p className="text-xs text-white/80 mt-1 max-w-xs">
                  Import a PDF delivery note or paste stops, optimize road sequence, and start delivering.
                </p>
              </div>
            </button>

            {/* Active Draft Banner */}
            {draft && (
              <div
                onClick={() => setScreen('route')}
                className="w-full bg-[#D5EA83] border border-[#C2D86C] text-[#273626] p-4 rounded-2xl flex items-center justify-between cursor-pointer hover:brightness-98 transition shadow-xs"
              >
                <div>
                  <span className="text-[10px] font-bold tracking-widest uppercase opacity-80 block">
                    CURRENT IN-PROGRESS ROUTE
                  </span>
                  <div className="font-bold text-sm">{draft.name}</div>
                  <div className="text-xs opacity-90 mt-0.5">
                    {draft.stops.length} stops · {deliveredCount} completed · {formatDistance(draft.distanceMeters)}
                  </div>
                </div>
                <ArrowRight className="w-5 h-5 shrink-0" />
              </div>
            )}

            {/* Offline Maps Quick Access Bar */}
            <div
              onClick={() => navTo('offline-maps')}
              className="w-full bg-[#FAFBF7] border border-[#DDE3D7] hover:border-[#1E453B] rounded-2xl p-3.5 flex items-center justify-between cursor-pointer transition shadow-2xs group"
            >
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-[#E7ECE2] text-[#1E453B] flex items-center justify-center font-bold text-sm shrink-0 shadow-xs">
                  <Download className="w-4 h-4 stroke-[2.2]" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-xs text-[#1A2E27] group-hover:text-[#1E453B] transition-colors">
                      Offline Maps & Territories
                    </span>
                    <span className="text-[9px] font-bold px-1.5 py-0.2 rounded-full bg-[#E7ECE2] text-[#1E453B]">
                      Auto Z5-17
                    </span>
                  </div>
                  <p className="text-[11px] text-[#748078] mt-0.5">
                    {offlinePackCount > 0
                      ? `${offlinePackCount} territory pack${offlinePackCount > 1 ? 's' : ''} saved · Ready for zero cell signal`
                      : 'Select area on map selector to pre-download before shift'}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-1 text-xs font-bold text-[#1E453B] shrink-0 group-hover:translate-x-0.5 transition-transform">
                <span className="hidden sm:inline">Manage</span>
                <ChevronRight className="w-4 h-4" />
              </div>
            </div>

            {/* Old Routes Section */}
            <div>
              <div className="flex items-center justify-between mb-2.5 px-0.5">
                <div className="flex items-center gap-2">
                  <Archive className="w-4 h-4 text-[#1E453B]" />
                  <h4 className="font-bold text-sm tracking-tight text-[#1A2E27]">Old Routes</h4>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[10px] tracking-wider font-semibold text-[#748078] uppercase">
                    {savedRoutes.length} {savedRoutes.length === 1 ? 'SAVED RUN' : 'SAVED RUNS'}
                  </span>
                  {savedRoutes.length > 3 && (
                    <button
                      type="button"
                      onClick={() => navTo('history')}
                      className="text-xs font-bold text-[#1E453B] hover:underline cursor-pointer ml-1"
                    >
                      View All →
                    </button>
                  )}
                </div>
              </div>

              {savedRoutes.length === 0 ? (
                <div className="bg-white border border-[#DDE3D7] rounded-2xl p-5 flex items-center gap-3.5 shadow-xs text-left">
                  <div className="w-10 h-10 rounded-xl bg-[#E7ECE2] text-[#1E453B] flex items-center justify-center shrink-0">
                    <Archive className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="font-bold text-sm text-[#1A2E27]">No Old Routes Yet</div>
                    <div className="text-xs text-[#748078] mt-0.5 leading-relaxed">
                      Saved delivery runs appear here so you can review them or delete any you no longer need.
                    </div>
                  </div>
                </div>
              ) : (
                <div className={device.isDesktop ? "grid grid-cols-2 lg:grid-cols-3 gap-3" : "flex flex-col gap-2.5"}>
                  {savedRoutes.map((route) => {
                    const completed = route.stops.filter((s) => s.delivered).length;
                    const mapped = route.stops.filter((s) => s.latitude !== null).length;
                    const dist = route.distanceMeters ?? (route.path ? calculatePathDistanceMeters(route.path) : undefined);
                    return (
                      <div
                        key={route.id}
                        onClick={() => openHistoryRoute(route)}
                        className="bg-white border border-[#DDE3D7] rounded-2xl p-4 flex flex-col gap-2.5 hover:bg-[#FAFBF7] transition shadow-xs cursor-pointer text-left group"
                      >
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0 flex-1">
                            <h3 className="font-bold text-sm text-[#1A2E27] truncate group-hover:text-[#1E453B] transition">
                              {route.name}
                            </h3>
                            <span className="text-[11px] text-[#748078]">
                              {formatDate(route.savedAt || route.createdAt, true)}
                            </span>
                          </div>
                          <div className="flex items-center gap-1 shrink-0">
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setRouteToDelete(route);
                              }}
                              className="p-2 rounded-xl text-[#A94F3E] hover:bg-red-50 hover:text-[#8F3C2C] transition cursor-pointer"
                              title={`Delete ${route.name}`}
                              aria-label={`Delete route ${route.name}`}
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                            <ChevronRight className="w-4 h-4 text-[#748078] group-hover:translate-x-0.5 transition-transform" />
                          </div>
                        </div>

                        <div className="flex items-center justify-between pt-2 border-t border-[#F3F5ED] flex-wrap gap-2 text-[11px] text-[#748078]">
                          <div className="flex items-center gap-3 flex-wrap">
                            <span className="flex items-center gap-1 font-semibold text-[#1A2E27]">
                              <MapPin className="w-3 h-3 text-[#1E453B]" /> {route.stops.length} stops
                            </span>
                            {dist && dist > 0 && (
                              <span className="font-medium text-[#1E453B]">
                                📍 {formatDistance(dist)}
                              </span>
                            )}
                            <span className="flex items-center gap-1">
                              <CheckCircle2 className="w-3 h-3 text-[#10B981]" /> {completed} delivered
                            </span>
                          </div>

                          {mapped > 1 && (
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                openHistoryRoute(route, true);
                              }}
                              className="py-1 px-2.5 rounded-lg bg-[#E7ECE2] hover:bg-[#DDE3D7] text-[#1E453B] text-[11px] font-bold flex items-center gap-1 transition cursor-pointer"
                              title="Preview driving route animation"
                            >
                              <Play className="w-3 h-3 fill-current" />
                              <span>Preview</span>
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* All-Time Map Shortcut */}
            {preferences.showAllTimeMap && savedRoutes.length > 0 && (
              <button
                type="button"
                onClick={() => setScreen('all-time-map')}
                className="w-full bg-white border border-[#DDE3D7] rounded-2xl p-3.5 flex items-center justify-between hover:bg-[#FAFBF7] transition shadow-xs cursor-pointer text-left"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-xl bg-[#E7ECE2] text-[#1E453B] flex items-center justify-center">
                    <Layers className="w-4 h-4" />
                  </div>
                  <span className="font-semibold text-xs text-[#1A2E27]">
                    Open all-time delivery map ({locatedHistory.length} stops)
                  </span>
                </div>
                <ChevronRight className="w-4 h-4 text-[#748078]" />
              </button>
            )}
          </>
        )}

        {/* IMPORT SCREEN */}
        {screen === 'import' && (
          <>
            <div className="flex items-center gap-3 border-b border-[#DDE3D7] pb-3">
              <button
                type="button"
                onClick={goBack}
                className="w-9 h-9 rounded-xl bg-[#E7ECE2] flex items-center justify-center text-[#1A2E27] hover:bg-[#DDE3D7] transition cursor-pointer"
              >
                <ArrowLeft className="w-4 h-4" />
              </button>
              <div>
                <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase">
                  NEW ROUTE
                </span>
                <h2 className="text-xl font-bold tracking-tight text-[#1A2E27]">
                  Import Delivery Note
                </h2>
              </div>
            </div>

            <p className="text-xs text-[#748078] leading-relaxed">
              Upload a digital delivery note (PDF or TXT) or paste customer details. RouteKeeper automatically detects customer names, phone numbers, and addresses.
            </p>

            {/* Import Mode Switcher */}
            <div className="flex bg-[#E7ECE2] p-1 rounded-xl text-xs font-semibold">
              <button
                type="button"
                onClick={() => setActiveImportMode('upload')}
                className={`flex-1 py-1.5 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer ${
                  activeImportMode === 'upload' ? 'bg-white text-[#1E453B] shadow-xs' : 'text-[#748078]'
                }`}
              >
                <Upload className="w-3.5 h-3.5" /> Upload File
              </button>
              <button
                type="button"
                onClick={() => setActiveImportMode('paste')}
                className={`flex-1 py-1.5 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer ${
                  activeImportMode === 'paste' ? 'bg-white text-[#1E453B] shadow-xs' : 'text-[#748078]'
                }`}
              >
                <FileCode className="w-3.5 h-3.5" /> Paste Text
              </button>
            </div>

            {/* Upload File Card */}
            {activeImportMode === 'upload' && (
              <div className="flex flex-col gap-3">
                <input
                  type="file"
                  ref={fileInputRef}
                  onChange={handleFileUpload}
                  accept=".pdf,.txt,text/plain,application/pdf"
                  className="hidden"
                />

                <div
                  onClick={() => fileInputRef.current?.click()}
                  className="border-2 border-dashed border-[#1E453B]/50 hover:border-[#1E453B] bg-white rounded-2xl p-8 flex flex-col items-center justify-center text-center gap-2 cursor-pointer transition"
                >
                  <div className="w-12 h-12 rounded-2xl bg-[#E7ECE2] text-[#1E453B] flex items-center justify-center mb-1">
                    {isReadingFile ? (
                      <div className="w-6 h-6 border-2 border-[#1E453B] border-t-transparent rounded-full animate-spin" />
                    ) : (
                      <Upload className="w-6 h-6" />
                    )}
                  </div>
                  <span className="font-bold text-sm text-[#1A2E27]">
                    {isReadingFile ? 'Reading delivery note…' : 'Choose PDF or TXT File'}
                  </span>
                  <span className="text-xs text-[#748078]">
                    Click or drag & drop · files stay local on your browser
                  </span>
                  <div className="flex items-center gap-2 mt-2 px-3 py-1 bg-[#E7ECE2] rounded-full text-[10px] font-bold text-[#1E453B]">
                    <FileText className="w-3 h-3" /> PDF · TXT
                  </div>
                </div>

                {/* Quick Sample Note Button */}
                <button
                  type="button"
                  onClick={loadSampleNote}
                  className="w-full py-2.5 px-4 rounded-xl border border-[#DDE3D7] bg-white text-xs font-semibold text-[#1E453B] hover:bg-[#FAFBF7] flex items-center justify-center gap-2 transition cursor-pointer"
                >
                  <FileText className="w-3.5 h-3.5 text-[#1E453B]" />
                  Load Sample London Delivery Note (6 Stops)
                </button>
              </div>
            )}

            {/* Paste Text Area */}
            {activeImportMode === 'paste' && (
              <div className="flex flex-col gap-2.5">
                <textarea
                  value={pastedText}
                  onChange={(e) => setPastedText(e.target.value)}
                  placeholder="Paste customer delivery note here...&#10;&#10;Customer Name: Alice Johnson&#10;Phone: +44 20 7946 0192&#10;Address: 10 Downing Street, London"
                  rows={7}
                  className="w-full bg-white border border-[#DDE3D7] rounded-xl p-3 text-xs font-mono text-[#1A2E27] focus:outline-none focus:ring-2 focus:ring-[#1E453B]"
                />
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={loadSampleNote}
                    className="flex-1 py-2 rounded-xl border border-[#DDE3D7] bg-white text-xs font-semibold text-[#1E453B] hover:bg-[#FAFBF7] transition cursor-pointer"
                  >
                    Load Sample
                  </button>
                  <button
                    type="button"
                    onClick={parsePastedContent}
                    disabled={!pastedText.trim()}
                    className="flex-1 py-2 rounded-xl bg-[#1E453B] text-white text-xs font-bold disabled:opacity-50 hover:bg-[#152F29] transition cursor-pointer"
                  >
                    Parse Stops
                  </button>
                </div>
              </div>
            )}

            {/* Error Message */}
            {importError && (
              <div className="bg-[#F8E9E4] border border-[#EBCAC0] text-[#A94F3E] text-xs p-3 rounded-xl flex items-start gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{importError}</span>
              </div>
            )}

            {/* Parsed Stops Preview */}
            {parsedStops.length > 0 && (
              <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 flex flex-col gap-3 shadow-xs">
                <div className="flex items-center justify-between border-b border-[#DDE3D7] pb-2">
                  <div>
                    <h3 className="font-bold text-sm text-[#1A2E27]">
                      {parsedStops.length} delivery stops detected
                    </h3>
                    <div className="flex items-center gap-2 mt-0.5 flex-wrap">
                      <span className="text-[11px] text-[#748078]">{sourceName}</span>
                      {parsedStops.filter((s) => s.timeWindow).length > 0 && (
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-[#D1FAE5] text-[#065F46] border border-[#A7F3D0]">
                          🕒 {parsedStops.filter((s) => s.timeWindow).length} timeslots detected
                        </span>
                      )}
                    </div>
                  </div>
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-[#E7ECE2] text-[#1E453B]">
                    READY
                  </span>
                </div>

                <div className="flex flex-col gap-2 max-h-64 overflow-y-auto pr-1">
                  {parsedStops.map((stop, idx) => (
                    <div
                      key={stop.id}
                      className="flex items-start gap-2.5 text-xs py-2 border-b border-[#F3F5ED] last:border-0"
                    >
                      <span className="px-2 py-0.5 rounded-md bg-[#E7ECE2] text-[#1E453B] font-bold text-[11px] shrink-0 mt-0.5">
                        {stop.dropNumber ? `Drop #${stop.dropNumber}` : `Drop #${idx + 1}`}
                      </span>
                      <div className="flex-1 min-w-0">
                        <div className="font-semibold text-[#1A2E27] truncate flex items-center gap-2 flex-wrap">
                          <span>{stop.name}</span>
                          {stop.orderId && (
                            <span className="text-[10px] text-[#748078] font-normal">
                              (Order ID: {stop.orderId})
                            </span>
                          )}
                          {stop.phone && (
                            <span className="text-[10px] text-[#748078] font-normal">
                              📞 {stop.phone}
                            </span>
                          )}
                        </div>
                        <div className="text-[11px] text-[#53635d] truncate mt-0.5">{stop.address}</div>
                        {stop.timeWindow && (
                          <div className="mt-1">
                            <span className="inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded-full bg-[#D1FAE5] text-[#065F46] border border-[#A7F3D0]">
                              🕒 Deliver between: {stop.timeWindow.startTime} – {stop.timeWindow.endTime} ({stop.timeWindow.durationHours === 'custom' ? 'Slot' : `${stop.timeWindow.durationHours}h slot`})
                            </span>
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                <button
                  type="button"
                  onClick={continueWithImportedStops}
                  disabled={isReadingFile || parsedStops.length === 0}
                  className="w-full py-3 rounded-xl bg-[#1E453B] text-white font-bold text-sm hover:bg-[#152F29] transition flex items-center justify-center gap-2 shadow-xs cursor-pointer disabled:opacity-50"
                >
                  Review Stops & Build Route <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            )}

            {/* Privacy Note */}
            <div className="flex items-start gap-2 text-[11px] text-[#748078] pt-2">
              <Shield className="w-3.5 h-3.5 shrink-0 mt-0.5 text-[#1E453B]" />
              <span>
                Your files remain completely private in your browser. OpenStreetMap receives street addresses for geocoding coordinates, and OSRM generates driving route geometry.
              </span>
            </div>
          </>
        )}

        {/* PRE-ROUTE REVIEW SCREEN (Dedicated Triage for Failed Addresses, Time Windows & Dwell Time) */}
        {screen === 'pre-route-review' && (
          <div className="flex flex-col gap-4">
            {/* Header */}
            <div className="flex items-center justify-between border-b border-[#DDE3D7] pb-3">
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => setScreen('import')}
                  className="w-9 h-9 rounded-xl bg-[#E7ECE2] flex items-center justify-center text-[#1A2E27] hover:bg-[#DDE3D7] transition cursor-pointer"
                  title="Back to Import"
                >
                  <ArrowLeft className="w-4 h-4" />
                </button>
                <div>
                  <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase block">
                    STEP 2 OF 3 · PRE-ROUTE REVIEW
                  </span>
                  <h2 className="text-xl font-bold tracking-tight text-[#1A2E27]">
                    Review & Fix Addresses
                  </h2>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-[#1E453B] bg-[#E7ECE2] px-2.5 py-1 rounded-full">
                  {parsedStops.length} Total Stops
                </span>
              </div>
            </div>

            {/* UNLOCATED ADDRESSES ALERT BANNER & QUICK FIXER */}
            {(() => {
              const unlocated = parsedStops.filter((s) => s.geocodeStatus !== 'located');
              const locatedCount = parsedStops.length - unlocated.length;

              if (unlocated.length > 0) {
                return (
                  <div className="bg-amber-50 border-2 border-amber-300 rounded-2xl p-4 flex flex-col gap-3 shadow-xs animate-in fade-in">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2.5">
                        <div className="w-8 h-8 rounded-xl bg-amber-500 text-white flex items-center justify-center shrink-0">
                          <AlertTriangle className="w-4 h-4" />
                        </div>
                        <div>
                          <h3 className="font-bold text-sm text-amber-950">
                            {unlocated.length} {unlocated.length === 1 ? 'Address' : 'Addresses'} Failed to Geolocate
                          </h3>
                          <p className="text-xs text-amber-800 mt-0.5">
                            OpenStreetMap couldn't pinpoint exact street coordinates. Fix typos or strip flat/unit prefixes below so they get pinned to the route.
                          </p>
                        </div>
                      </div>
                      <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-amber-200 text-amber-900 shrink-0">
                        Needs Attention
                      </span>
                    </div>

                    <div className="divide-y divide-amber-200/80 bg-white/80 rounded-xl border border-amber-200 overflow-hidden">
                      {unlocated.map((stop) => {
                        const isEditingThis = inlineEditingStopId === stop.id;
                        return (
                          <div key={stop.id} className="p-3 flex flex-col gap-2">
                            <div className="flex items-start justify-between gap-2">
                              <div className="min-w-0">
                                <div className="font-bold text-xs text-[#1A2E27] flex items-center gap-2">
                                  <span>{stop.name}</span>
                                  {stop.phone && (
                                    <span className="text-[11px] text-[#748078] font-normal">
                                      📞 {stop.phone}
                                    </span>
                                  )}
                                </div>
                                <div className="text-xs text-amber-900 font-mono mt-0.5 break-all">
                                  📍 {stop.address}
                                </div>
                              </div>
                              <span className="text-[10px] font-bold text-amber-800 bg-amber-100 px-2 py-0.5 rounded-full shrink-0">
                                ⚠️ Not located
                              </span>
                            </div>

                            {/* Inline Address Editor for unlocated stop */}
                            <div className="flex items-center gap-2 mt-1">
                              <input
                                type="text"
                                value={isEditingThis ? inlineAddressText : stop.address}
                                onFocus={() => {
                                  setInlineEditingStopId(stop.id);
                                  setInlineAddressText(stop.address);
                                }}
                                onChange={(e) => {
                                  setInlineEditingStopId(stop.id);
                                  setInlineAddressText(e.target.value);
                                }}
                                placeholder="Edit street address (e.g. 10 High Street, London)"
                                className="flex-1 bg-white border border-[#CBD5E1] rounded-xl px-3 py-1.5 text-xs text-[#1A2E27] focus:outline-none focus:ring-2 focus:ring-[#1E453B]"
                              />

                              {/* Clean Address Button (strips flat/unit prefixes) */}
                              <button
                                type="button"
                                onClick={() => {
                                  const cleaned = stop.address
                                    .replace(/\b(?:flat|unit|apt|apartment|suite|room|floor)\s*[a-z0-9#-]+\s*,?/gi, '')
                                    .replace(/^[,\s]+|[,\s]+$/g, '')
                                    .trim();
                                  setInlineEditingStopId(stop.id);
                                  setInlineAddressText(cleaned);
                                  handleSaveEditedAddress(stop, cleaned);
                                }}
                                className="px-2.5 py-1.5 rounded-xl bg-white border border-amber-300 hover:bg-amber-100 text-amber-900 text-xs font-semibold cursor-pointer shrink-0 transition"
                                title="Strip flat/unit prefixes which often confuse geocoders"
                              >
                                Clean
                              </button>

                              {/* Save & Geolocate button */}
                              <button
                                type="button"
                                disabled={isPlanningRoute}
                                onClick={() => {
                                  const textToSave = isEditingThis ? inlineAddressText : stop.address;
                                  handleSaveEditedAddress(stop, textToSave);
                                }}
                                className="px-3 py-1.5 rounded-xl bg-[#1E453B] hover:bg-[#152F29] text-white text-xs font-bold cursor-pointer shrink-0 shadow-xs transition"
                              >
                                {isPlanningRoute ? 'Checking…' : 'Save & Pin'}
                              </button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              }

              return (
                <div className="bg-[#ECFDF5] border border-[#A7F3D0] rounded-2xl p-4 flex items-center justify-between gap-3 shadow-xs">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-xl bg-[#10B981] text-white flex items-center justify-center shrink-0">
                      <Check className="w-4 h-4" />
                    </div>
                    <div>
                      <h3 className="font-bold text-sm text-[#065F46]">
                        All {locatedCount} Addresses Successfully Located
                      </h3>
                      <p className="text-xs text-[#047857] mt-0.5">
                        Street coordinates have been verified on OpenStreetMap and are ready for road optimization.
                      </p>
                    </div>
                  </div>
                  <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-[#D1FAE5] text-[#065F46] shrink-0">
                    Ready to Route
                  </span>
                </div>
              );
            })()}

            {/* ROUTE SETTINGS: DWELL TIME & DEPARTURE TIME */}
            <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 flex flex-col gap-3 shadow-xs">
              <div className="flex items-center justify-between">
                <span className="font-bold text-xs text-[#1A2E27] flex items-center gap-1.5">
                  <Clock className="w-4 h-4 text-[#1E453B]" />
                  <span>Route Timing & Delivery Completion Buffers</span>
                </span>
                <span className="text-[11px] text-[#748078]">Factored into customer windows</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                {/* Departure Time */}
                <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl p-3 flex flex-col gap-1.5">
                  <label className="text-xs font-bold text-[#1A2E27] flex items-center justify-between">
                    <span>Route Departure Start Time</span>
                    <span className="text-[11px] font-normal text-[#748078]">e.g. 09:00</span>
                  </label>
                  <input
                    type="time"
                    value={departureStartTime}
                    onChange={(e) => setDepartureStartTime(e.target.value)}
                    className="bg-white border border-[#CBD5E1] rounded-lg px-2.5 py-1.5 text-xs font-bold text-[#1A2E27]"
                  />
                  <span className="text-[10px] text-[#748078]">
                    Calculates estimated arrival times for each stop from this start hour.
                  </span>
                </div>

                {/* Delivery Dwell Time (5 - 8 mins) */}
                <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl p-3 flex flex-col gap-1.5">
                  <label className="text-xs font-bold text-[#1A2E27] flex items-center justify-between">
                    <span>Delivery Dwell Duration (5–8 mins)</span>
                    <span className="text-xs font-extrabold text-[#1E453B]">{defaultDwellMinutes} mins</span>
                  </label>
                  <div className="flex items-center gap-1">
                    {[5, 6, 6.5, 7, 8].map((mins) => (
                      <button
                        key={mins}
                        type="button"
                        onClick={() => setDefaultDwellMinutes(mins)}
                        className={`flex-1 py-1.5 rounded-lg text-xs font-bold cursor-pointer transition ${
                          defaultDwellMinutes === mins
                            ? 'bg-[#1E453B] text-white shadow-xs'
                            : 'bg-white border border-[#CBD5E1] text-[#748078] hover:bg-[#FAFBF7]'
                        }`}
                      >
                        {mins}m
                      </button>
                    ))}
                  </div>
                  <span className="text-[10px] text-[#748078]">
                    Average time to park, unload, doorstep handoff, and depart.
                  </span>
                </div>
              </div>

              {/* Planned Rest Stops Slider (1 to 4 stops) */}
              <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl p-3 flex flex-col gap-2.5 mt-1">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-bold text-[#1A2E27] flex items-center gap-1.5">
                    <span className="text-sm">🅿️</span>
                    <span>Driver Rest & Parking Stops (1–4)</span>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-[#EEF2FF] text-[#4338CA] border border-[#C7D2FE]">
                      {restStopCount} {restStopCount === 1 ? 'Stop' : 'Stops'}
                    </span>
                  </label>
                  <span className="text-xs font-extrabold text-[#1E453B]">
                    {restStopCount === 1
                      ? '1 Mid-Shift Break (30m)'
                      : restStopCount === 2
                      ? '2 Breaks (Morning 20m + Lunch 30m)'
                      : restStopCount === 3
                      ? '3 Breaks (15m, 30m, 15m)'
                      : '4 Breaks (Spaced Evenly)'}
                  </span>
                </div>

                <div className="flex flex-col gap-1">
                  <input
                    type="range"
                    min={1}
                    max={4}
                    step={1}
                    value={restStopCount}
                    onChange={(e) => handleRestStopCountChange(Number(e.target.value))}
                    className="w-full accent-[#1E453B] cursor-pointer h-2 bg-[#E7ECE2] rounded-lg"
                  />
                  <div className="flex justify-between text-[10px] font-bold text-[#748078] px-0.5">
                    <span className={restStopCount === 1 ? 'text-[#1E453B]' : ''}>1 Stop</span>
                    <span className={restStopCount === 2 ? 'text-[#1E453B]' : ''}>2 Stops (Recommended)</span>
                    <span className={restStopCount === 3 ? 'text-[#1E453B]' : ''}>3 Stops</span>
                    <span className={restStopCount === 4 ? 'text-[#1E453B]' : ''}>4 Stops</span>
                  </div>
                </div>

                <div className="text-[11px] text-[#475569] leading-relaxed bg-[#EEF2FF]/60 border border-[#C7D2FE]/60 rounded-lg p-2.5">
                  <span className="font-bold text-[#1E1B4B]">Balanced shift intervals: </span>
                  Quiet parking laybys away from traffic fumes with quick 2-minute arterial bypass links. Each rest stop is inserted directly into your route overview with exact arrival and departure times so you know when and how to get there.
                </div>
              </div>
            </div>

            {/* CUSTOMER TIME SLOTS */}
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between px-1">
                <span className="font-bold text-xs text-[#1A2E27] flex items-center gap-1.5">
                  <Milestone className="w-4 h-4 text-[#1E453B]" />
                  <span>Customer Delivery Time Windows ({parsedStops.length} stops)</span>
                </span>
                <span className="text-[11px] text-[#748078]">Timeslots extracted & applied</span>
              </div>

              {parsedStops.some((s) => s.timeWindow) && (
                <div className="bg-[#D1FAE5] border border-[#A7F3D0] rounded-xl p-3 flex items-start gap-2.5">
                  <span className="text-base text-[#065F46] shrink-0 font-bold">✓</span>
                  <div className="text-xs text-[#065F46]">
                    <span className="font-bold">Extracted {parsedStops.filter((s) => s.timeWindow).length} customer delivery slots from paperwork: </span>
                    All timeslots are automatically locked into the route optimization. Deliveries will be scheduled in designated windows without having to manually set them.
                  </div>
                </div>
              )}

              <div className="divide-y divide-[#F3F5ED] border border-[#DDE3D7] rounded-2xl bg-white p-2 shadow-xs">
                {parsedStops.map((stop, idx) => {
                  const currentWindow = stop.timeWindow;
                  const isUnlocated = stop.geocodeStatus !== 'located';

                  const setStopWindow = (window?: DeliveryTimeWindow) => {
                    setParsedStops((prev) =>
                      prev.map((s) => (s.id === stop.id ? { ...s, timeWindow: window } : s)),
                    );
                  };

                  return (
                    <div key={stop.id} className="p-3 flex flex-col gap-2">
                      <div className="flex items-start justify-between gap-2 text-xs">
                        <div className="flex items-center gap-2.5 min-w-0">
                          <span className="px-2 py-0.5 rounded-lg bg-[#E7ECE2] text-[#1E453B] font-bold text-xs flex items-center justify-center shrink-0">
                            {stop.dropNumber ? `Drop #${stop.dropNumber}` : `Drop #${idx + 1}`}
                          </span>
                          <div className="min-w-0">
                            <div className="font-bold text-[#1A2E27] truncate flex items-center gap-2 flex-wrap">
                              <span>{stop.name}</span>
                              {stop.orderId && (
                                <span className="text-[10px] text-[#748078] font-normal">
                                  (Order ID: {stop.orderId})
                                </span>
                              )}
                              {stop.phone && (
                                <span className="text-[10px] text-[#748078] font-normal">
                                  📞 {stop.phone}
                                </span>
                              )}
                            </div>
                            <div className="text-[11px] text-[#53635d] truncate mt-0.5">
                              {stop.address}
                            </div>
                          </div>
                        </div>

                        <div className="flex items-center gap-1.5 shrink-0">
                          {isUnlocated ? (
                            <span className="text-[10px] font-bold text-amber-800 bg-amber-100 px-2 py-0.5 rounded-full">
                              ⚠️ Unlocated
                            </span>
                          ) : (
                            <span
                              className="text-[10px] font-bold text-[#065F46] bg-[#D1FAE5] px-2 py-0.5 rounded-full"
                              title={stop.geocodedBy ? `Geolocated via ${stop.geocodedBy}` : 'Geolocated successfully'}
                            >
                              ✓ {stop.geocodedBy ? stop.geocodedBy.split(' ')[0] : 'Mapped'}
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Time slot: Deliver Between */}
                      <div className="flex items-center gap-2 flex-wrap pt-1.5 border-t border-[#F3F5ED]">
                        <span className="text-[11px] font-bold text-[#1E453B] shrink-0 flex items-center gap-1">
                          🕒 Deliver Between:
                        </span>
                        {currentWindow ? (
                          <span className="px-2 py-0.5 rounded-md text-[11px] font-bold bg-[#D1FAE5] text-[#065F46] border border-[#A7F3D0] flex items-center gap-1 shadow-2xs">
                            ✓ {currentWindow.startTime} – {currentWindow.endTime} (Paperwork)
                          </span>
                        ) : (
                          <span className="text-[10px] text-[#748078] italic">
                            Flexible
                          </span>
                        )}

                        {/* None / Flexible */}
                        <button
                          type="button"
                          onClick={() => setStopWindow(undefined)}
                          className={`px-2 py-0.5 rounded-md text-[11px] font-semibold cursor-pointer transition ${
                            !currentWindow
                              ? 'bg-[#1E453B] text-white shadow-2xs'
                              : 'bg-[#F3F5ED] text-[#53635d] hover:bg-[#E7ECE2]'
                          }`}
                        >
                          Flexible
                        </button>

                        {/* Extracted Paperwork Window */}
                        {currentWindow &&
                          ![
                            '09:00-10:00',
                            '10:00-11:00',
                            '11:00-12:00',
                            '13:00-14:00',
                            '14:00-15:00',
                            '15:00-16:00',
                            '09:00-13:00',
                            '13:00-17:00',
                          ].includes(`${currentWindow.startTime}-${currentWindow.endTime}`) && (
                            <button
                              type="button"
                              onClick={() => setStopWindow(currentWindow)}
                              className="px-2 py-0.5 rounded-md text-[11px] font-bold cursor-pointer transition bg-[#10B981] text-white shadow-2xs"
                            >
                              ✓ {currentWindow.label || `${currentWindow.startTime}–${currentWindow.endTime}`}
                            </button>
                          )}

                        {/* Common 1-Hour Windows */}
                        {[
                          { start: '09:00', end: '10:00' },
                          { start: '10:00', end: '11:00' },
                          { start: '11:00', end: '12:00' },
                          { start: '13:00', end: '14:00' },
                          { start: '14:00', end: '15:00' },
                          { start: '15:00', end: '16:00' },
                        ].map((w) => {
                          const isMatch =
                            currentWindow?.startTime === w.start && currentWindow?.endTime === w.end;
                          return (
                            <button
                              key={`${w.start}-${w.end}`}
                              type="button"
                              onClick={() =>
                                setStopWindow({
                                  startTime: w.start,
                                  endTime: w.end,
                                  durationHours: 1,
                                  label: `${w.start}–${w.end} (1h)`,
                                })
                              }
                              className={`px-2 py-0.5 rounded-md text-[11px] font-semibold cursor-pointer transition ${
                                isMatch
                                  ? 'bg-[#10B981] text-white shadow-2xs'
                                  : 'bg-[#F3F5ED] text-[#53635d] hover:bg-[#E7ECE2]'
                              }`}
                            >
                              {w.start}–{w.end} (1h)
                            </button>
                          );
                        })}

                        {/* 4-Hour Windows */}
                        {[
                          { start: '09:00', end: '13:00' },
                          { start: '13:00', end: '17:00' },
                        ].map((w) => {
                          const isMatch =
                            currentWindow?.startTime === w.start && currentWindow?.endTime === w.end;
                          return (
                            <button
                              key={`${w.start}-${w.end}`}
                              type="button"
                              onClick={() =>
                                setStopWindow({
                                  startTime: w.start,
                                  endTime: w.end,
                                  durationHours: 4,
                                  label: `${w.start}–${w.end} (4h)`,
                                })
                              }
                              className={`px-2 py-0.5 rounded-md text-[11px] font-semibold cursor-pointer transition ${
                                isMatch
                                  ? 'bg-[#6366F1] text-white shadow-2xs'
                                  : 'bg-[#F3F5ED] text-[#53635d] hover:bg-[#E7ECE2]'
                              }`}
                            >
                              {w.start}–{w.end} (4h)
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* ACTION BUTTONS */}
            <div className="flex items-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => setScreen('import')}
                className="py-3 px-4 rounded-xl border border-[#DDE3D7] bg-white text-[#1A2E27] font-bold text-sm hover:bg-[#FAFBF7] transition cursor-pointer"
              >
                ← Back to Import
              </button>

              <button
                type="button"
                disabled={isPlanningRoute || parsedStops.length === 0}
                onClick={planRouteFromReview}
                className="flex-1 py-3 px-4 rounded-xl bg-[#1E453B] hover:bg-[#152F29] text-white font-bold text-sm transition flex items-center justify-center gap-2 shadow-md cursor-pointer disabled:opacity-50"
              >
                {isPlanningRoute ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    <span>Planning Route Alternatives ({planningProgress.completed}/{planningProgress.total})…</span>
                  </>
                ) : (
                  <>
                    <span>Finalize & Generate Route</span>
                    <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </button>
            </div>
          </div>
        )}

        {/* ACTIVE ROUTE SCREEN */}
        {screen === 'route' &&
          (device.isDashmountLandscape ? (
            <div className="flex flex-col h-full w-full gap-2 min-h-0 overflow-hidden">
              {/* Ultra-compact Top Cockpit Navigation Bar */}
              <div className="flex items-center justify-between bg-[#1E453B] text-white px-3 py-1.5 rounded-xl shrink-0 shadow-xs">
                <div className="flex items-center gap-2 min-w-0">
                  <button
                    type="button"
                    onClick={goBack}
                    className="w-8 h-8 rounded-lg bg-white/10 hover:bg-white/20 flex items-center justify-center text-white cursor-pointer shrink-0"
                    title="Back"
                  >
                    <ArrowLeft className="w-4 h-4" />
                  </button>
                  <div className="truncate min-w-0">
                    <span className="font-extrabold text-xs text-white truncate block">{draft?.name || 'Active Route'}</span>
                    <span className="text-[10px] text-[#D5EA83]">
                      {deliveredCount}/{activeStops.length} ({summaryPercent}%) · {summaryDistance ? formatDistance(summaryDistance) : ''}
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    type="button"
                    onClick={() => setIsAnimatingRoute((prev) => !prev)}
                    className={`px-2.5 py-1 rounded-lg text-xs font-bold flex items-center gap-1 cursor-pointer transition ${
                      isAnimatingRoute ? 'bg-[#D5EA83] text-[#1E453B]' : 'bg-white/10 text-white hover:bg-white/20'
                    }`}
                  >
                    {isAnimatingRoute ? <Pause className="w-3 h-3" /> : <Play className="w-3 h-3 fill-current" />}
                    <span>{isAnimatingRoute ? 'Pause' : 'Drive'}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => draft && handleExportRouteVideo(draft)}
                    disabled={isExportingVideo}
                    className="px-2.5 py-1 rounded-lg bg-[#D5EA83] text-[#1E453B] text-xs font-bold flex items-center gap-1 cursor-pointer disabled:opacity-50"
                    title="Export 25-second cinematic route video"
                  >
                    <Video className="w-3 h-3" />
                    <span>25s MP4</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsSummaryModalOpen(true)}
                    className="px-2.5 py-1 rounded-lg bg-white/15 text-white hover:bg-white/25 text-xs font-bold cursor-pointer"
                    title="Open Route Summary Stats"
                  >
                    <BarChart3 className="w-3 h-3 inline mr-1" />
                    Stats
                  </button>
                </div>
              </div>

              {/* Dual-pane CarPlay style layout: 65% Map on left, 35% Action Console on right */}
              <div className="flex-1 grid grid-cols-12 gap-2 min-h-0 overflow-hidden">
                {/* Left 7 cols: Map */}
                <div className="col-span-7 h-full relative rounded-2xl overflow-hidden border border-[#DDE3D7]">
                  <LeafletMap
                    stops={activeStops}
                    path={draft?.path}
                    markerColor={preferences.markerColor}
                    lineColor={preferences.lineColor}
                    lineWidth={preferences.lineWidth}
                    currentLayer={preferences.mapLayer || 'osm'}
                    onLayerChange={(layer) => updatePreferences({ mapLayer: layer })}
                    height="100%"
                    className="h-full w-full"
                    animateDrawRoute={true}
                    drawKey={routeDrawTrigger}
                    waitStops={draft?.waitStops}
                    onNavigateWaitStop={(ws) => {
                      window.open(`https://www.google.com/maps/dir/?api=1&destination=${ws.latitude},${ws.longitude}`, '_blank');
                    }}
                    isAnimating={isAnimatingRoute}
                    animationSpeed={animationSpeed}
                    onAnimationProgress={(percent, nearestIdx) => {
                      setAnimationPercent(percent);
                      setAnimationActiveStopIdx(nearestIdx);
                    }}
                    onAnimationFinished={() => {
                      showToast('Driving preview animation complete!');
                      setIsAnimatingRoute(false);
                    }}
                    onToggleDelivery={(id, currentStatus) => handleToggleStop(id, currentStatus)}
                    onNavigate={(stop) => triggerNavigation(stop)}
                    onOpenDetails={(stop) => openActiveStop(stop)}
                    onSelectStop={(stop) => setSelectedStop(stop)}
                    onOpenOfflineModal={() => navTo('offline-maps')}
                  />
                </div>

                {/* Right 5 cols: Action Console with Next Stop Hero + Scrollable Stops List */}
                <div className="col-span-5 h-full flex flex-col gap-2 min-h-0 overflow-hidden">
                  {/* Next Stop Hero Card */}
                  {nextPendingStop ? (
                    <div className="bg-white border-2 border-[#1E453B] rounded-xl p-2.5 shadow-xs shrink-0 flex items-center justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <span className="text-[9px] font-black tracking-widest text-[#1E453B] uppercase">NEXT STOP</span>
                        <div className="font-extrabold text-xs text-[#1A2E27] truncate">
                          #{nextPendingIndex + 1} · {nextPendingStop.name}
                        </div>
                        <div className="text-[10px] text-[#748078] truncate mt-0.5">{nextPendingStop.address}</div>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleToggleStop(nextPendingStop.id, nextPendingStop.delivered)}
                        className="w-11 h-11 rounded-xl bg-[#10B981] hover:bg-[#059669] text-white flex flex-col items-center justify-center font-bold text-[9px] shadow-xs shrink-0 cursor-pointer active:scale-95 transition"
                        title="Confirm delivery complete"
                      >
                        <Check className="w-5 h-5 stroke-[3.5]" />
                        <span>Done</span>
                      </button>
                    </div>
                  ) : (
                    <div className="bg-[#ECFDF5] border border-[#A7F3D0] rounded-xl p-2.5 text-center text-xs font-bold text-[#065F46] shrink-0">
                      🎉 All {activeStops.length} deliveries complete!
                    </div>
                  )}

                  {/* Scrollable Stop List & Scheduled Rest Stops */}
                  <div className="flex-1 overflow-y-auto bg-white border border-[#DDE3D7] rounded-xl divide-y divide-[#F3F5ED] min-h-0 shadow-xs">
                    {activeStops.map((stop, index) => {
                      const waitStop = draft?.waitStops?.find((w) => w.nextStopIndex === index);
                      return (
                        <React.Fragment key={stop.id}>
                          {waitStop && (
                            <div className="p-2 bg-[#EEF2FF] border-b border-[#C7D2FE] flex items-center justify-between gap-2 text-[11px]">
                              <div className="min-w-0">
                                <span className="font-bold text-[#1E1B4B]">🅿️ {waitStop.name}</span>
                                <div className="text-[10px] text-[#4F46E5]">
                                  ⏱ Park {waitStop.suggestedArrivalTime}–{waitStop.departureTime} ({waitStop.waitDurationMinutes}m) · 2m bypass link
                                </div>
                              </div>
                              <button
                                type="button"
                                onClick={() => {
                                  window.open(`https://www.google.com/maps/dir/?api=1&destination=${waitStop.latitude},${waitStop.longitude}`, '_blank');
                                }}
                                className="px-2 py-0.5 rounded-lg bg-[#4F46E5] text-white text-[10px] font-bold shrink-0 cursor-pointer"
                              >
                                Nav
                              </button>
                            </div>
                          )}
                          <div className={`p-2 flex items-center justify-between gap-2 text-xs transition ${stop.delivered ? 'bg-[#F9FAF6]' : 'hover:bg-[#FAFBF7]'}`}>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleToggleStop(stop.id, stop.delivered);
                              }}
                              className={`w-7 h-7 rounded-lg flex items-center justify-center transition-all cursor-pointer shadow-xs active:scale-95 shrink-0 ${
                                stop.delivered
                                  ? 'bg-[#10B981] text-white'
                                  : 'bg-white border-2 border-[#CBD5E1] hover:border-[#10B981]'
                              }`}
                            >
                              {stop.delivered ? <Check className="w-3.5 h-3.5 stroke-[3.5]" /> : <div className="w-2.5 h-2.5 rounded-full border-2 border-[#94A3B8]" />}
                            </button>
                            <div onClick={() => openActiveStop(stop)} className="min-w-0 flex-1 cursor-pointer">
                              <div className="font-bold text-[11px] text-[#1A2E27] truncate">
                                <span className={stop.delivered ? 'line-through text-[#748078]' : ''}>
                                  #{index + 1} {stop.name}
                                </span>
                              </div>
                              <div className="text-[10px] text-[#748078] truncate">{stop.address}</div>
                            </div>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                triggerNavigation(stop);
                              }}
                              className="p-1 rounded-lg bg-[#E7ECE2] hover:bg-[#DDE3D7] text-[#1E453B] cursor-pointer shrink-0"
                            >
                              <Navigation className="w-3 h-3" />
                            </button>
                          </div>
                        </React.Fragment>
                      );
                    })}
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <>
              {/* Header */}
              <div className="flex items-center justify-between border-b border-[#DDE3D7] pb-3">
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={goBack}
                  className="w-9 h-9 rounded-xl bg-[#E7ECE2] flex items-center justify-center text-[#1A2E27] hover:bg-[#DDE3D7] transition cursor-pointer"
                >
                  <ArrowLeft className="w-4 h-4" />
                </button>
                <div>
                  <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase truncate block max-w-xs">
                    {draft?.name || 'ROUTE'}
                  </span>
                  <h2 className="text-xl font-bold tracking-tight text-[#1A2E27]">
                    Route Stops
                  </h2>
                </div>
              </div>

              {/* Summary Modal Button Trigger */}
              <div className="flex items-center gap-2">
                <PWAInstallButton />
                <button
                  type="button"
                  onClick={() => setIsSummaryModalOpen(true)}
                  className="py-1.5 px-3 rounded-xl bg-[#1E453B] text-[#D5EA83] text-xs font-bold hover:bg-[#152F29] transition flex items-center gap-1.5 shadow-xs cursor-pointer"
                  title="Open Route Summary Modal"
                >
                  <BarChart3 className="w-4 h-4" />
                  <span>Summary</span>
                </button>
              </div>
            </div>

            {/* Summary & Progress Bar */}
            <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 shadow-xs flex flex-col gap-3">
              <div className="flex items-center justify-between text-xs flex-wrap gap-1">
                <div className="flex items-center gap-2">
                  <span className="font-bold text-[#1A2E27]">
                    {activeStops.length} stops ({activeRouteLocated} mapped)
                  </span>
                  {summaryDistance && summaryDistance > 0 && (
                    <span className="text-[11px] font-bold text-[#1E453B] bg-[#E7ECE2] px-2 py-0.5 rounded-full">
                      📍 {formatDistance(summaryDistance)}
                    </span>
                  )}
                  {unlocatedStopsCount > 0 && (
                    <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-900 border border-amber-300 font-bold text-[10px]">
                      <AlertTriangle className="w-3 h-3 text-amber-700" />
                      {unlocatedStopsCount} unlocated
                    </span>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => setIsSummaryModalOpen(true)}
                  className="text-xs font-bold text-[#1E453B] hover:underline flex items-center gap-1 cursor-pointer"
                >
                  {deliveredCount} / {activeStops.length} ({summaryPercent}%)
                </button>
              </div>

              <div className="w-full bg-[#E7ECE2] h-2.5 rounded-full overflow-hidden">
                <div
                  className="bg-[#1E453B] h-full rounded-full transition-all duration-300"
                  style={{
                    width: `${activeStops.length > 0 ? (deliveredCount / activeStops.length) * 100 : 0}%`,
                  }}
                />
              </div>
            </div>

            {/* Mode Banner & Animation Toggle */}
            {draft?.routingMode && draft.routingMode !== 'not-planned' && (
              <div className="bg-[#E7ECE2] text-[#1E453B] text-xs px-3.5 py-2 rounded-xl flex items-center justify-between font-medium flex-wrap gap-2">
                <div className="flex items-center gap-2">
                  <Navigation className="w-4 h-4 text-[#1E453B]" />
                  <span>
                    {draft.routingMode === 'road-network'
                      ? `Road route (${formatDistance(draft.distanceMeters)}${draft.durationSeconds ? ` · ~${formatDuration(draft.durationSeconds)}` : ''})`
                      : 'Approximate sequence · straight-line proximity order'}
                  </span>
                </div>

                {activeRouteLocated > 1 && (
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <button
                      type="button"
                      onClick={() => {
                        setRouteViewMode('map');
                        setIsAnimatingRoute((prev) => !prev);
                      }}
                      className={`px-2.5 py-1 rounded-lg text-xs font-bold flex items-center gap-1.5 transition cursor-pointer ${
                        isAnimatingRoute
                          ? 'bg-[#1E453B] text-white shadow-xs'
                          : 'bg-white text-[#1E453B] border border-[#DDE3D7] hover:bg-[#FAFBF7]'
                      }`}
                    >
                      {isAnimatingRoute ? <Pause className="w-3 h-3" /> : <Play className="w-3 h-3 fill-current" />}
                      <span>{isAnimatingRoute ? 'Stop Animation' : 'Preview Drive'}</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => draft && handleExportRouteVideo(draft)}
                      disabled={isExportingVideo}
                      className="px-2.5 py-1 rounded-lg text-xs font-bold flex items-center gap-1.5 bg-[#1E453B] hover:bg-[#152F29] text-[#D5EA83] transition cursor-pointer shadow-xs disabled:opacity-50"
                      title="Export 25-second cinematic MP4 video with overview and vehicle follow camera zoom"
                    >
                      <Video className="w-3.5 h-3.5" />
                      <span>Export 25s Video</span>
                    </button>
                  </div>
                )}
              </div>
            )}

            {/* Animation Control Bar (if animating) */}
            {isAnimatingRoute && (
              <div className="bg-[#1E453B] text-white rounded-2xl p-3 shadow-md flex items-center justify-between gap-3 animate-in fade-in duration-200">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-[#D5EA83] text-[#273626] flex items-center justify-center font-bold text-xs shrink-0">
                    🚐
                  </div>
                  <div className="text-xs">
                    <div className="font-bold text-[#D5EA83]">
                      Driving Preview ({animationPercent}%)
                    </div>
                    <div className="text-[11px] opacity-80 truncate max-w-[190px]">
                      Stop #{animationActiveStopIdx + 1}: {activeStops[animationActiveStopIdx]?.name || 'Next stop'}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-1.5">
                  {[1, 2, 5].map((spd) => (
                    <button
                      key={spd}
                      type="button"
                      onClick={() => setAnimationSpeed(spd)}
                      className={`px-2 py-0.5 rounded text-[10px] font-bold transition cursor-pointer ${
                        animationSpeed === spd
                          ? 'bg-[#D5EA83] text-[#273626]'
                          : 'bg-white/10 text-white hover:bg-white/20'
                      }`}
                    >
                      {spd}x
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() => draft && handleExportRouteVideo(draft)}
                    disabled={isExportingVideo}
                    className="px-2 py-0.5 rounded text-[10px] font-bold bg-[#D5EA83] text-[#273626] hover:bg-[#c6de6a] flex items-center gap-1 transition cursor-pointer ml-1"
                    title="Export video MP4"
                  >
                    <Download className="w-3 h-3" />
                    <span>MP4</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsAnimatingRoute(false)}
                    className="p-1 rounded-lg hover:bg-white/10 text-white/80 cursor-pointer ml-1"
                    title="Close animation"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}

            {/* Alternative Routes Profile Selector */}
            {draft?.alternativeRoutes && draft.alternativeRoutes.length > 1 && (
              <div className="bg-white border border-[#DDE3D7] rounded-2xl p-3 flex flex-col gap-2 shadow-xs">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-[#1A2E27] flex items-center gap-1.5">
                    <Compass className="w-3.5 h-3.5 text-[#1E453B]" />
                    <span>Select Alternative Route</span>
                  </span>
                  <span className="text-[11px] text-[#748078]">
                    {draft.alternativeRoutes.length} route options
                  </span>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  {draft.alternativeRoutes.map((alt) => {
                    const isSelected = (draft.selectedAlternativeId || 'fastest') === alt.id;
                    return (
                      <button
                        key={alt.id}
                        type="button"
                        onClick={() => handleSelectAlternativeRoute(alt.id)}
                        className={`p-2 rounded-xl text-left border transition cursor-pointer flex flex-col justify-between ${
                          isSelected
                            ? 'border-[#1E453B] bg-[#F0FDF4] shadow-xs ring-1 ring-[#1E453B]'
                            : 'border-[#DDE3D7] bg-[#FAFBF7] hover:bg-white text-[#748078]'
                        }`}
                      >
                        <div className="flex items-center justify-between gap-1 mb-1">
                          <span
                            className={`text-[9px] font-extrabold uppercase px-1.5 py-0.2 rounded-md ${
                              isSelected
                                ? 'bg-[#1E453B] text-white'
                                : 'bg-[#E7ECE2] text-[#1E453B]'
                            }`}
                          >
                            {alt.badge}
                          </span>
                          {isSelected && (
                            <span className="text-[#10B981] text-[10px] font-bold">✓ Active</span>
                          )}
                        </div>
                        <div className="font-bold text-xs text-[#1A2E27] truncate">
                          {alt.title}
                        </div>
                        <div className="text-[11px] font-semibold text-[#1E453B] mt-0.5">
                          {formatDistance(alt.distanceMeters)} · {formatDuration(alt.durationSeconds)}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* View Toggle */}
            <div className="flex bg-[#E7ECE2] p-1 rounded-xl text-xs font-semibold">
              <button
                type="button"
                onClick={() => setRouteViewMode('list')}
                className={`flex-1 py-1.5 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer ${
                  routeViewMode === 'list' ? 'bg-white text-[#1E453B] shadow-xs' : 'text-[#748078]'
                }`}
              >
                <FileText className="w-3.5 h-3.5" /> Stop List ({activeStops.length})
              </button>
              <button
                type="button"
                onClick={() => setRouteViewMode('map')}
                className={`flex-1 py-1.5 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer ${
                  routeViewMode === 'map' ? 'bg-white text-[#1E453B] shadow-xs' : 'text-[#748078]'
                }`}
              >
                <Compass className="w-3.5 h-3.5" /> Map View
              </button>
              <button
                type="button"
                onClick={() => setRouteViewMode('summary')}
                className={`flex-1 py-1.5 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer ${
                  routeViewMode === 'summary' ? 'bg-white text-[#1E453B] shadow-xs' : 'text-[#748078]'
                }`}
              >
                <BarChart3 className="w-3.5 h-3.5" /> Summary View
              </button>
            </div>

            {/* Map View */}
            {routeViewMode === 'map' && (
              <div className="flex flex-col gap-3">
                {activeRouteLocated > 0 ? (
                  <div className="flex flex-col gap-2">
                    <LeafletMap
                      stops={activeStops}
                      path={draft?.path}
                      markerColor={preferences.markerColor}
                      lineColor={preferences.lineColor}
                      lineWidth={preferences.lineWidth}
                      currentLayer={preferences.mapLayer || 'osm'}
                      onLayerChange={(layer) => updatePreferences({ mapLayer: layer })}
                      height={390}
                      animateDrawRoute={true}
                      drawKey={routeDrawTrigger}
                      waitStops={draft?.waitStops}
                      onNavigateWaitStop={(ws) => {
                        window.open(
                          `https://www.google.com/maps/dir/?api=1&destination=${ws.latitude},${ws.longitude}`,
                          '_blank',
                        );
                      }}
                      isAnimating={isAnimatingRoute}
                      animationSpeed={animationSpeed}
                      onAnimationProgress={(percent, nearestIdx) => {
                        setAnimationPercent(percent);
                        setAnimationActiveStopIdx(nearestIdx);
                      }}
                      onAnimationFinished={() => {
                        showToast('Driving preview animation complete!');
                        setIsAnimatingRoute(false);
                      }}
                      onToggleDelivery={(id, currentStatus) => handleToggleStop(id, currentStatus)}
                      onNavigate={(stop) => triggerNavigation(stop)}
                      onOpenDetails={(stop) => openActiveStop(stop)}
                      onSelectStop={(stop) => setSelectedStop(stop)}
                      onOpenOfflineModal={() => navTo('offline-maps')}
                    />
                    <div className="text-[11px] text-[#748078] flex items-center justify-between px-1">
                      <span>💡 Click any pin on the map for quick stop info & 1-tap confirmation</span>
                      <button
                        type="button"
                        onClick={() => setIsSummaryModalOpen(true)}
                        className="text-[#1E453B] font-bold hover:underline"
                      >
                        View Full Stats
                      </button>
                    </div>

                    {/* Quick Deliveries list also accessible below the map */}
                    <div className="bg-white border border-[#DDE3D7] rounded-2xl divide-y divide-[#F3F5ED] overflow-hidden shadow-xs mt-1">
                      <div className="px-3.5 py-2.5 bg-[#FAFBF7] border-b border-[#DDE3D7] flex items-center justify-between">
                        <span className="font-bold text-xs text-[#1A2E27]">
                          Delivery Stops ({deliveredCount}/{activeStops.length} completed)
                        </span>
                        <span className="text-[11px] font-semibold text-[#1E453B]">
                          {summaryPercent}% Done
                        </span>
                      </div>
                      <div className="max-h-60 overflow-y-auto divide-y divide-[#F3F5ED]">
                        {activeStops.map((stop, index) => {
                          const isUnlocated =
                            stop.geocodeStatus === 'not-found' ||
                            (stop.latitude === null && draft?.routingMode !== 'not-planned');
                          return (
                            <div
                              key={stop.id}
                              className={`p-3 flex items-center justify-between gap-3 transition ${
                                stop.delivered ? 'bg-[#F9FAF6]' : 'hover:bg-[#FAFBF7]'
                              }`}
                            >
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  e.preventDefault();
                                  handleToggleStop(stop.id, stop.delivered);
                                }}
                                className={`w-8 h-8 rounded-xl flex items-center justify-center transition-all cursor-pointer shadow-xs active:scale-95 shrink-0 group ${
                                  stop.delivered
                                    ? 'bg-[#10B981] hover:bg-[#059669] text-white ring-2 ring-[#D1FAE5]'
                                    : 'bg-white hover:bg-[#F0FDF4] border-2 border-[#CBD5E1] hover:border-[#10B981]'
                                }`}
                                title={stop.delivered ? '✓ Delivered · Click to mark pending' : 'Click to confirm delivery'}
                              >
                                {stop.delivered ? (
                                  <Check className="w-4 h-4 stroke-[3.5]" />
                                ) : (
                                  <div className="w-3 h-3 rounded-full border-2 border-[#94A3B8] group-hover:border-[#10B981] transition-colors" />
                                )}
                              </button>
                              <div
                                onClick={() => openActiveStop(stop)}
                                className="flex items-center gap-2 flex-1 min-w-0 cursor-pointer"
                              >
                                <span className="w-5 text-center font-bold text-xs text-[#1E453B]">
                                  #{index + 1}
                                </span>
                                <div className="min-w-0 flex-1">
                                  <div className="font-bold text-xs text-[#1A2E27] truncate">
                                    <span className={stop.delivered ? 'line-through text-[#748078]' : ''}>
                                      {stop.name}
                                    </span>
                                    {isUnlocated && (
                                      <span className="ml-1.5 inline-flex items-center gap-0.5 text-amber-700 bg-amber-100 border border-amber-300 text-[9px] px-1 py-0.2 rounded font-bold">
                                        Not Located
                                      </span>
                                    )}
                                  </div>
                                  <div className="text-[11px] text-[#748078] truncate">{stop.address}</div>
                                </div>
                              </div>
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  triggerNavigation(stop);
                                }}
                                className="p-1.5 rounded-lg bg-[#E7ECE2] hover:bg-[#DDE3D7] text-[#1E453B] cursor-pointer"
                                title="Navigate"
                              >
                                <Navigation className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="h-64 rounded-2xl bg-white border border-[#DDE3D7] flex flex-col items-center justify-center p-6 text-center gap-2">
                    <MapPin className="w-8 h-8 text-[#748078]" />
                    <span className="font-bold text-sm text-[#1A2E27]">
                      Stops not yet mapped
                    </span>
                    <span className="text-xs text-[#748078] max-w-xs">
                      Click "Map & Optimize Route" below to find street locations on OpenStreetMap.
                    </span>
                  </div>
                )}
              </div>
            )}

            {/* Stop List View */}
            {routeViewMode === 'list' && (
              <div className="bg-white border border-[#DDE3D7] rounded-2xl divide-y divide-[#F3F5ED] overflow-hidden shadow-xs">
                {activeStops.map((stop, index) => {
                  const isUnlocated =
                    stop.geocodeStatus === 'not-found' ||
                    (stop.latitude === null && draft?.routingMode !== 'not-planned');

                  return (
                    <div key={stop.id} className="flex flex-col">
                      {/* Quiet Wait Stop (if scheduled right before this delivery) */}
                      {(() => {
                        const waitStop = draft?.waitStops?.find((w) => w.nextStopIndex === index);
                        if (!waitStop) return null;
                        return (
                          <div className="w-full p-3 bg-[#EEF2FF] border-b border-[#C7D2FE] flex items-start justify-between gap-3 shadow-2xs">
                            <div className="flex items-start gap-2.5 min-w-0">
                              <span className="w-7 h-7 rounded-lg bg-[#4F46E5] text-white flex items-center justify-center font-black text-xs shrink-0 shadow-xs mt-0.5">
                                🅿️
                              </span>
                              <div className="min-w-0">
                                <div className="flex items-center gap-2 flex-wrap">
                                  <span className="font-bold text-xs text-[#1E1B4B]">
                                    Quiet Rest & Parking Spot · Low Traffic Area
                                  </span>
                                  <span className="text-[10px] font-bold px-1.5 py-0.2 rounded bg-white text-[#4338CA] border border-[#C7D2FE]">
                                    Park for {waitStop.waitDurationMinutes} mins
                                  </span>
                                </div>
                                <div className="text-[11px] text-[#475569] mt-0.5 truncate">
                                  📍 {waitStop.name} · {waitStop.address}
                                </div>
                                <div className="text-[10px] text-[#4F46E5] font-semibold mt-0.5">
                                  ⏱ Park {waitStop.suggestedArrivalTime} – {waitStop.departureTime} · Depart at {waitStop.departureTime} to arrive at Stop #{index + 1} for delivery window
                                </div>
                                <div className="text-[10px] text-[#059669] font-medium">
                                  ✓ {waitStop.arterialAccessMinutes} mins direct arterial link back to main road
                                </div>
                              </div>
                            </div>
                            <button
                              type="button"
                              onClick={() => {
                                window.open(
                                  `https://www.google.com/maps/dir/?api=1&destination=${waitStop.latitude},${waitStop.longitude}`,
                                  '_blank',
                                );
                              }}
                              className="px-2.5 py-1.5 rounded-xl bg-[#4F46E5] hover:bg-[#4338CA] text-white text-[11px] font-bold shrink-0 transition shadow-2xs cursor-pointer flex items-center gap-1"
                              title="Navigate to Quiet Wait Spot"
                            >
                              <Navigation className="w-3 h-3" />
                              <span>Nav</span>
                            </button>
                          </div>
                        );
                      })()}

                      <div
                        className={`p-3.5 flex items-center justify-between gap-3 transition ${
                          stop.delivered ? 'bg-[#F9FAF6]' : 'hover:bg-[#FAFBF7]'
                        }`}
                      >

                      {/* Left: Dedicated Tick Button to mark completed */}
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          e.preventDefault();
                          handleToggleStop(stop.id, stop.delivered);
                        }}
                        className={`w-9 h-9 rounded-xl flex items-center justify-center transition-all cursor-pointer shadow-xs active:scale-95 shrink-0 group ${
                          stop.delivered
                            ? 'bg-[#10B981] hover:bg-[#059669] text-white ring-2 ring-[#D1FAE5]'
                            : 'bg-white hover:bg-[#F0FDF4] border-2 border-[#CBD5E1] hover:border-[#10B981]'
                        }`}
                        title={
                          stop.delivered
                            ? '✓ Delivered · Click to mark as pending'
                            : 'Click to confirm delivery completed'
                        }
                      >
                        {stop.delivered ? (
                          <Check className="w-5 h-5 stroke-[3.5]" />
                        ) : (
                          <div className="w-3.5 h-3.5 rounded-full border-2 border-[#94A3B8] group-hover:border-[#10B981] transition-colors" />
                        )}
                      </button>

                      {/* Middle: Stop details & Geolocation failure badge */}
                      <div
                        onClick={() => openActiveStop(stop)}
                        className="flex items-center gap-2.5 flex-1 min-w-0 cursor-pointer"
                      >
                        <div
                          className={`w-6 h-6 rounded-lg flex items-center justify-center font-bold text-xs shrink-0 ${
                            isUnlocated
                              ? 'bg-amber-100 text-amber-900 border border-amber-300'
                              : 'bg-[#D5EA83] text-[#273626]'
                          }`}
                        >
                          {index + 1}
                        </div>

                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span
                              className={`font-bold text-xs text-[#1A2E27] truncate ${
                                stop.delivered ? 'line-through text-[#748078]' : ''
                              }`}
                            >
                              {stop.name}
                            </span>

                            {/* Customer Time Slot badge */}
                            {stop.timeWindow && (
                              <span className="text-[10px] font-bold px-1.5 py-0.2 rounded bg-[#EEF2FF] text-[#4338CA] border border-[#C7D2FE]">
                                🕒 {stop.timeWindow.label || `${stop.timeWindow.startTime}–${stop.timeWindow.endTime}`}
                              </span>
                            )}

                            {/* Geolocation Failure Warning Icon + Fix Button */}
                            {isUnlocated && (
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setInlineEditingStopId(inlineEditingStopId === stop.id ? null : stop.id);
                                  setInlineAddressText(stop.address);
                                }}
                                className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-amber-100 hover:bg-amber-200 text-amber-900 border border-amber-300 font-bold text-[10px] tracking-tight shrink-0 shadow-2xs cursor-pointer transition"
                                title="Click to fix/edit this address and re-locate"
                              >
                                <AlertTriangle className="w-3 h-3 text-amber-700 animate-pulse" />
                                <span>Not Located · Fix</span>
                              </button>
                            )}

                            {stop.delivered && (
                              <span className="text-[10px] font-semibold text-[#065F46] bg-[#D1FAE5] px-1.5 py-0.2 rounded">
                                Done
                              </span>
                            )}
                          </div>

                          <div className="text-[11px] text-[#748078] truncate mt-0.5">
                            {stop.address}
                          </div>

                          {/* Schedule / Window status row */}
                          {stop.estimatedArrival && (
                            <div className="text-[10px] text-[#53635d] flex items-center gap-1.5 mt-0.5 flex-wrap font-medium">
                              <span>Arrive ~{stop.estimatedArrival}</span>
                              <span>·</span>
                              <span>Depart ~{stop.estimatedDeparture} ({stop.serviceTimeMinutes || 6.5}m dwell)</span>
                              {stop.windowStatus === 'on-time' && (
                                <span className="text-[#059669] font-bold bg-[#ECFDF5] px-1 py-0.2 rounded">✓ On Time</span>
                              )}
                              {stop.windowStatus === 'early' && (
                                <span className="text-[#2563EB] font-bold bg-[#EFF6FF] px-1 py-0.2 rounded">Early Buffer</span>
                              )}
                              {stop.windowStatus === 'late' && (
                                <span className="text-amber-800 font-bold bg-amber-100 px-1 py-0.2 rounded">⚠️ Late</span>
                              )}
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Right: Quick Navigate icon button + Chevron */}
                      <div className="flex items-center gap-1.5 shrink-0">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            triggerNavigation(stop);
                          }}
                          className="w-8 h-8 rounded-xl bg-[#E7ECE2] hover:bg-[#DDE3D7] text-[#1E453B] flex items-center justify-center transition-all cursor-pointer shadow-2xs"
                          title="Navigate to stop with selected app"
                        >
                          <Navigation className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => openActiveStop(stop)}
                          className="p-1 text-[#748078] hover:text-[#1A2E27] cursor-pointer"
                        >
                          <ChevronRight className="w-4 h-4" />
                        </button>
                      </div>
                    </div>

                    {/* Inline Address Editor */}
                    {inlineEditingStopId === stop.id && (
                      <div className="p-3 bg-amber-50/95 border-b border-amber-200 flex flex-col gap-2 animate-in fade-in duration-150">
                        <div className="flex items-center justify-between text-[11px] font-bold text-amber-950">
                          <span>Edit address for {stop.name}:</span>
                          <span className="text-[10px] font-normal text-amber-800">
                            Remove flat/apt numbers if OSM cannot locate building
                          </span>
                        </div>
                        <div className="flex gap-2">
                          <input
                            type="text"
                            value={inlineAddressText}
                            onChange={(e) => setInlineAddressText(e.target.value)}
                            className="flex-1 bg-white border border-[#DDE3D7] rounded-xl px-3 py-1.5 text-xs text-[#1A2E27] focus:outline-none focus:ring-2 focus:ring-[#1E453B]"
                            placeholder="Enter correct street address..."
                            autoFocus
                          />
                          <button
                            type="button"
                            disabled={isPlanningRoute}
                            onClick={() => handleSaveEditedAddress(stop, inlineAddressText)}
                            className="px-3 py-1.5 rounded-xl bg-[#1E453B] text-white text-xs font-bold hover:bg-[#152F29] disabled:opacity-50 cursor-pointer shrink-0"
                          >
                            {isPlanningRoute ? 'Locating…' : 'Save & Locate'}
                          </button>
                          <button
                            type="button"
                            onClick={() => setInlineEditingStopId(null)}
                            className="px-2.5 py-1.5 rounded-xl border border-[#DDE3D7] text-xs font-semibold text-[#748078] hover:bg-white cursor-pointer shrink-0"
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                  );
                })}
              </div>
            )}

            {/* Route Summary View */}
            {routeViewMode === 'summary' && (
              <div className="flex flex-col gap-4">
                {/* Route Map & Line Generation Visualizer */}
                {activeRouteLocated > 0 ? (
                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center justify-between text-xs px-1">
                      <span className="font-bold text-[#1A2E27] flex items-center gap-1.5">
                        <Compass className="w-3.5 h-3.5 text-[#1E453B]" />
                        <span>Route Generation Preview</span>
                      </span>
                      <span className="text-[11px] text-[#748078]">
                        {activeRouteLocated} of {activeStops.length} mapped stops
                      </span>
                    </div>
                    <LeafletMap
                      stops={activeStops}
                      path={draft?.path}
                      markerColor={preferences.markerColor}
                      lineColor={preferences.lineColor}
                      lineWidth={preferences.lineWidth}
                      currentLayer={preferences.mapLayer || 'osm'}
                      onLayerChange={(layer) => updatePreferences({ mapLayer: layer })}
                      height={320}
                      animateDrawRoute={true}
                      drawKey={routeDrawTrigger}
                      waitStops={draft?.waitStops}
                      onNavigateWaitStop={(ws) => {
                        window.open(
                          `https://www.google.com/maps/dir/?api=1&destination=${ws.latitude},${ws.longitude}`,
                          '_blank',
                        );
                      }}
                      isAnimating={isAnimatingRoute}
                      animationSpeed={animationSpeed}
                      onAnimationProgress={(percent, nearestIdx) => {
                        setAnimationPercent(percent);
                        setAnimationActiveStopIdx(nearestIdx);
                      }}
                      onAnimationFinished={() => {
                        showToast('Driving preview animation complete!');
                        setIsAnimatingRoute(false);
                      }}
                      onToggleDelivery={(id, currentStatus) => handleToggleStop(id, currentStatus)}
                      onNavigate={(stop) => triggerNavigation(stop)}
                      onOpenDetails={(stop) => openActiveStop(stop)}
                      onSelectStop={(stop) => setSelectedStop(stop)}
                      onOpenOfflineModal={() => navTo('offline-maps')}
                    />
                  </div>
                ) : (
                  <div className="h-48 rounded-2xl bg-white border border-[#DDE3D7] flex flex-col items-center justify-center p-6 text-center gap-2">
                    <MapPin className="w-8 h-8 text-[#748078]" />
                    <span className="font-bold text-sm text-[#1A2E27]">
                      Route Not Yet Mapped
                    </span>
                    <span className="text-xs text-[#748078] max-w-xs">
                      Click "Map & Optimize Route Sequence" below to calculate turn-by-turn road geometry.
                    </span>
                  </div>
                )}

                {/* Key Metrics Grid */}
                <div className="grid grid-cols-2 gap-3">
                  <div className="bg-white border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-1 shadow-2xs">
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-[#748078]">
                      <Milestone className="w-4 h-4 text-[#1E453B]" /> Total Stops
                    </div>
                    <div className="text-2xl font-extrabold text-[#1A2E27]">
                      {activeStops.length}
                    </div>
                    <span className="text-[11px] text-[#748078]">
                      {activeRouteLocated} mapped on road
                    </span>
                  </div>

                  <div className="bg-white border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-1 shadow-2xs">
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-[#748078]">
                      <Navigation className="w-4 h-4 text-[#1E453B]" /> Total Distance
                    </div>
                    <div className="text-2xl font-extrabold text-[#1A2E27]">
                      {summaryDistance && summaryDistance > 0
                        ? (summaryDistance * 0.000621371).toFixed(1) + ' mi'
                        : 'N/A'}
                    </div>
                    <span className="text-[11px] text-[#748078]">
                      {summaryDistance && summaryDistance > 0
                        ? `${(summaryDistance / 1000).toFixed(1)} km road network`
                        : 'Awaiting route'}
                    </span>
                  </div>

                  <div className="bg-white border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-1 shadow-2xs">
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-[#748078]">
                      <Gauge className="w-4 h-4 text-[#10B981]" /> Completion
                    </div>
                    <div className="text-2xl font-extrabold text-[#10B981]">
                      {summaryPercent}%
                    </div>
                    <span className="text-[11px] text-[#748078]">
                      {deliveredCount} of {activeStops.length} delivered
                    </span>
                  </div>

                  <div className="bg-white border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-1 shadow-2xs">
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-[#748078]">
                      <Clock className="w-4 h-4 text-[#1E453B]" /> Est. Drive Time
                    </div>
                    <div className="text-2xl font-extrabold text-[#1A2E27]">
                      {summaryDuration ? formatDuration(summaryDuration) : 'Calculating'}
                    </div>
                    <span className="text-[11px] text-[#748078]">
                      Turn-by-turn driving
                    </span>
                  </div>
                </div>

                {/* Driver Rest & Parking Stops Slider (Live adjustment on active route) */}
                <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-2.5 shadow-2xs">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-bold text-[#1A2E27] flex items-center gap-1.5">
                      <span className="text-sm">🅿️</span>
                      <span>Adjust Rest & Parking Stops (1–4)</span>
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-[#EEF2FF] text-[#4338CA] border border-[#C7D2FE]">
                        {draft?.waitStops?.length ?? restStopCount} {(draft?.waitStops?.length ?? restStopCount) === 1 ? 'Stop' : 'Stops'}
                      </span>
                    </label>
                    <span className="text-xs font-extrabold text-[#1E453B]">
                      {(draft?.restStopCount ?? restStopCount) === 1
                        ? '1 Break (30m)'
                        : (draft?.restStopCount ?? restStopCount) === 2
                        ? '2 Breaks (20m + 30m)'
                        : (draft?.restStopCount ?? restStopCount) === 3
                        ? '3 Breaks (15m, 30m, 15m)'
                        : '4 Breaks (Evenly Spaced)'}
                    </span>
                  </div>

                  <div className="flex flex-col gap-1">
                    <input
                      type="range"
                      min={1}
                      max={4}
                      step={1}
                      value={draft?.restStopCount ?? restStopCount}
                      onChange={(e) => handleRestStopCountChange(Number(e.target.value))}
                      className="w-full accent-[#1E453B] cursor-pointer h-2 bg-[#E7ECE2] rounded-lg"
                    />
                    <div className="flex justify-between text-[10px] font-bold text-[#748078] px-0.5">
                      <span className={(draft?.restStopCount ?? restStopCount) === 1 ? 'text-[#1E453B]' : ''}>1 Stop</span>
                      <span className={(draft?.restStopCount ?? restStopCount) === 2 ? 'text-[#1E453B]' : ''}>2 Stops</span>
                      <span className={(draft?.restStopCount ?? restStopCount) === 3 ? 'text-[#1E453B]' : ''}>3 Stops</span>
                      <span className={(draft?.restStopCount ?? restStopCount) === 4 ? 'text-[#1E453B]' : ''}>4 Stops</span>
                    </div>
                  </div>
                </div>

                {/* Scheduled Quiet Parking & Driver Rest Locations */}
                {draft?.waitStops && draft.waitStops.length > 0 && (
                  <div className="bg-[#EEF2FF] border border-[#C7D2FE] rounded-2xl p-4 flex flex-col gap-3 shadow-xs">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="w-7 h-7 rounded-lg bg-[#4F46E5] text-white flex items-center justify-center font-black text-xs shrink-0">
                          🅿️
                        </span>
                        <div>
                          <h4 className="font-bold text-xs text-[#1E1B4B]">
                            Scheduled Quiet Rest & Parking Stops ({draft.waitStops.length})
                          </h4>
                          <p className="text-[11px] text-[#475569]">
                            Peaceful laybys scheduled for transit gaps &gt; 30 mins with 2-min arterial access
                          </p>
                        </div>
                      </div>
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-white text-[#4338CA] border border-[#C7D2FE]">
                        Low Traffic
                      </span>
                    </div>

                    <div className="divide-y divide-[#C7D2FE]/60 bg-white rounded-xl border border-[#C7D2FE] overflow-hidden">
                      {draft.waitStops.map((ws, i) => (
                        <div key={ws.id} className="p-3 flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="font-bold text-xs text-[#1E1B4B] flex items-center gap-1.5">
                              <span>{ws.name}</span>
                              <span className="text-[10px] font-semibold text-[#4F46E5] bg-[#EEF2FF] px-1.5 py-0.2 rounded">
                                {ws.waitDurationMinutes} min wait
                              </span>
                            </div>
                            <div className="text-[11px] text-[#475569] mt-0.5 truncate">
                              📍 {ws.address}
                            </div>
                            <div className="text-[11px] text-[#4F46E5] font-semibold mt-1">
                              ⏱ Schedule: Park {ws.suggestedArrivalTime} – {ws.departureTime} · Depart at {ws.departureTime}
                            </div>
                            <div className="text-[10px] text-[#059669] font-medium mt-0.5">
                              ✓ {ws.arterialAccessMinutes} mins direct arterial link back to main road
                            </div>
                          </div>
                          <button
                            type="button"
                            onClick={() => {
                              window.open(
                                `https://www.google.com/maps/dir/?api=1&destination=${ws.latitude},${ws.longitude}`,
                                '_blank',
                              );
                            }}
                            className="px-2.5 py-1.5 rounded-xl bg-[#4F46E5] hover:bg-[#4338CA] text-white text-[11px] font-bold shrink-0 transition shadow-2xs cursor-pointer flex items-center gap-1"
                          >
                            <Navigation className="w-3 h-3" />
                            <span>Nav</span>
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Progress bar */}
                <div className="bg-white border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-2 shadow-2xs">
                  <div className="flex items-center justify-between text-xs font-bold text-[#1A2E27]">
                    <span>Delivery Progress</span>
                    <span className="text-[#10B981]">{summaryPercent}% Done</span>
                  </div>
                  <div className="w-full bg-[#E7ECE2] h-3 rounded-full overflow-hidden">
                    <div
                      className="bg-[#10B981] h-full rounded-full transition-all duration-300"
                      style={{ width: `${summaryPercent}%` }}
                    />
                  </div>
                  <div className="flex items-center justify-between text-[11px] text-[#748078]">
                    <span>✓ {deliveredCount} Delivered</span>
                    <span>⏳ {activeStops.length - deliveredCount} Remaining</span>
                  </div>
                </div>

                {/* Breakdown List with Inline Address Editor */}
                <div className="flex flex-col gap-1">
                  <span className="text-xs font-bold text-[#1A2E27] px-1">
                    Stops Status & Location Breakdown ({activeStops.length} stops)
                  </span>
                  <div className="divide-y divide-[#F3F5ED] border border-[#DDE3D7] rounded-2xl bg-white px-3 py-1 shadow-2xs">
                    {activeStops.map((stop, idx) => {
                      const isUnlocated =
                        stop.geocodeStatus === 'not-found' ||
                        (stop.latitude === null && draft?.routingMode !== 'not-planned');

                      return (
                        <div key={stop.id} className="py-2.5 flex flex-col gap-2">
                          <div className="flex items-center justify-between gap-2.5 text-xs">
                            <div className="flex items-center gap-2.5 min-w-0">
                              <button
                                type="button"
                                onClick={() => handleToggleStop(stop.id, stop.delivered)}
                                className={`w-7 h-7 rounded-lg flex items-center justify-center transition-all cursor-pointer shadow-2xs active:scale-90 shrink-0 group ${
                                  stop.delivered
                                    ? 'bg-[#10B981] text-white ring-2 ring-[#D1FAE5]'
                                    : 'bg-white hover:bg-[#F0FDF4] border-2 border-[#CBD5E1] hover:border-[#10B981]'
                                }`}
                              >
                                {stop.delivered ? (
                                  <Check className="w-3.5 h-3.5 stroke-[3.5]" />
                                ) : (
                                  <div className="w-2.5 h-2.5 rounded-full border border-[#94A3B8] group-hover:border-[#10B981]" />
                                )}
                              </button>
                              <div className="truncate">
                                <div className="flex items-center gap-1.5 flex-wrap">
                                  <span
                                    className={`font-semibold ${
                                      stop.delivered ? 'line-through text-[#748078]' : 'text-[#1A2E27]'
                                    }`}
                                  >
                                    #{idx + 1} {stop.name}
                                  </span>
                                  {isUnlocated && (
                                    <button
                                      type="button"
                                      onClick={() => {
                                        setInlineEditingStopId(inlineEditingStopId === stop.id ? null : stop.id);
                                        setInlineAddressText(stop.address);
                                      }}
                                      className="text-[9px] font-bold text-amber-800 bg-amber-100 hover:bg-amber-200 border border-amber-300 px-1.5 py-0.2 rounded shrink-0 cursor-pointer transition flex items-center gap-0.5"
                                    >
                                      <AlertTriangle className="w-2.5 h-2.5 text-amber-700" />
                                      <span>Not Located · Fix</span>
                                    </button>
                                  )}
                                </div>
                                <span className="text-[11px] text-[#748078] block truncate">
                                  {stop.address}
                                </span>
                              </div>
                            </div>

                            <button
                              type="button"
                              onClick={() => handleToggleStop(stop.id, stop.delivered)}
                              className={`text-[10px] font-bold px-2 py-0.5 rounded-full shrink-0 cursor-pointer transition ${
                                stop.delivered
                                  ? 'bg-[#D1FAE5] text-[#065F46] hover:bg-emerald-200'
                                  : 'bg-[#E7ECE2] text-[#1E453B] hover:bg-[#DDE3D7]'
                              }`}
                            >
                              {stop.delivered ? '✓ Done' : 'Pending'}
                            </button>
                          </div>

                          {/* Inline Address Editor inside Summary */}
                          {inlineEditingStopId === stop.id && (
                            <div className="p-2.5 bg-amber-50 rounded-xl border border-amber-200 flex flex-col gap-1.5 animate-in fade-in duration-150">
                              <span className="text-[10px] font-bold text-amber-950">
                                Edit delivery address:
                              </span>
                              <div className="flex gap-1.5">
                                <input
                                  type="text"
                                  value={inlineAddressText}
                                  onChange={(e) => setInlineAddressText(e.target.value)}
                                  className="flex-1 bg-white border border-[#DDE3D7] rounded-lg px-2.5 py-1 text-xs text-[#1A2E27] focus:outline-none focus:ring-1 focus:ring-[#1E453B]"
                                  placeholder="e.g. 10 Downing Street, London"
                                  autoFocus
                                />
                                <button
                                  type="button"
                                  disabled={isPlanningRoute}
                                  onClick={() => handleSaveEditedAddress(stop, inlineAddressText)}
                                  className="px-2.5 py-1 rounded-lg bg-[#1E453B] text-white text-xs font-bold hover:bg-[#152F29] disabled:opacity-50 cursor-pointer shrink-0"
                                >
                                  {isPlanningRoute ? 'Locating…' : 'Save & Locate'}
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setInlineEditingStopId(null)}
                                  className="px-2 py-1 rounded-lg border border-[#DDE3D7] text-xs font-semibold text-[#748078] hover:bg-white cursor-pointer shrink-0"
                                >
                                  ✕
                                </button>
                              </div>
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Export Animation MP4 Button */}
                {activeRouteLocated > 1 && (
                  <button
                    type="button"
                    onClick={() => draft && handleExportRouteVideo(draft)}
                    disabled={isExportingVideo}
                    className="w-full py-3 rounded-xl bg-white border border-[#DDE3D7] hover:bg-[#FAFBF7] text-[#1E453B] font-bold text-xs flex items-center justify-center gap-2 shadow-xs cursor-pointer transition disabled:opacity-50"
                  >
                    <Video className="w-4 h-4 text-[#1E453B]" />
                    <span>Export Delivery Route Video (MP4)</span>
                  </button>
                )}
              </div>
            )}

            {/* Planning Progress Bar */}
            {isPlanningRoute && (
              <div className="bg-white border border-[#DDE3D7] rounded-xl p-3.5 flex items-center gap-3">
                <div className="w-5 h-5 border-2 border-[#1E453B] border-t-transparent rounded-full animate-spin" />
                <div className="text-xs">
                  <div className="font-bold text-[#1A2E27]">Locating addresses on map…</div>
                  <div className="text-[#748078]">
                    {planningProgress.completed} of {planningProgress.total} checked
                  </div>
                </div>
              </div>
            )}

            {/* Error Message */}
            {routeError && (
              <div className="bg-[#F8E9E4] border border-[#EBCAC0] text-[#A94F3E] text-xs p-3 rounded-xl flex items-start gap-2">
                <Info className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{routeError}</span>
              </div>
            )}

            {/* Actions */}
            <div className="flex flex-col gap-2 pt-1">
              {draft?.routingMode === 'not-planned' ? (
                <button
                  type="button"
                  onClick={planRoute}
                  disabled={isPlanningRoute}
                  className="w-full py-3 rounded-xl bg-[#1E453B] text-white font-bold text-sm hover:bg-[#152F29] disabled:opacity-50 transition flex items-center justify-center gap-2 shadow-xs cursor-pointer"
                >
                  <Navigation className="w-4 h-4" /> Map & Optimize Route Sequence
                </button>
              ) : (
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={planRoute}
                    disabled={isPlanningRoute}
                    className="flex-1 py-3 rounded-xl bg-[#E7ECE2] text-[#1E453B] font-semibold text-xs hover:bg-[#DDE3D7] transition flex items-center justify-center gap-1.5 cursor-pointer"
                  >
                    <RefreshCw className="w-3.5 h-3.5" /> Re-optimize
                  </button>
                  <button
                    type="button"
                    onClick={finishAndSaveRoute}
                    className="flex-1 py-3 rounded-xl bg-[#1E453B] text-white font-bold text-xs hover:bg-[#152F29] transition flex items-center justify-center gap-1.5 shadow-xs cursor-pointer"
                  >
                    <Archive className="w-3.5 h-3.5" /> Finish & Save Run
                  </button>
                </div>
              )}
            </div>
          </>
        ))}

        {/* STOP DETAIL SCREEN */}
        {screen === 'stop-detail' && selectedStop && (
          <>
            <div className="flex items-center gap-3 border-b border-[#DDE3D7] pb-3">
              <button
                type="button"
                onClick={goBack}
                className="w-9 h-9 rounded-xl bg-[#E7ECE2] flex items-center justify-center text-[#1A2E27] hover:bg-[#DDE3D7] transition cursor-pointer"
              >
                <ArrowLeft className="w-4 h-4" />
              </button>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase">
                    {previousDetailScreen === 'history-route' ? 'SAVED DELIVERY' : 'ACTIVE STOP'}
                  </span>
                  {selectedStop.geocodeStatus === 'not-found' && (
                    <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md bg-amber-100 text-amber-900 border border-amber-300 font-bold text-[10px]">
                      <AlertTriangle className="w-3 h-3 text-amber-700" /> Not Located
                    </span>
                  )}
                </div>
                <h2 className="text-xl font-bold tracking-tight text-[#1A2E27] truncate">
                  {selectedStop.name}
                </h2>
              </div>
            </div>

            {/* Geolocation Warning Banner if not found */}
            {selectedStop.geocodeStatus === 'not-found' && (
              <div className="bg-amber-50 border border-amber-200 text-amber-900 rounded-xl p-3 flex items-start gap-2.5">
                <AlertTriangle className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" />
                <div className="text-xs">
                  <strong className="block font-bold">Address not located on OpenStreetMap</strong>
                  <span className="text-[11px] text-amber-800">
                    We couldn't pin this exact address. You can edit the address text below to fix typos, or launch navigation to search by text.
                  </span>
                </div>
              </div>
            )}

            {/* Map on this stop */}
            {selectedStop.latitude !== null && selectedStop.longitude !== null ? (
              <LeafletMap
                stops={[selectedStop]}
                markerColor={preferences.markerColor}
                lineColor={preferences.lineColor}
                lineWidth={preferences.lineWidth}
                currentLayer={preferences.mapLayer || 'osm'}
                onLayerChange={(layer) => updatePreferences({ mapLayer: layer })}
                height={220}
                onOpenOfflineModal={() => setIsOfflineModalOpen(true)}
              />
            ) : (
              <div className="h-40 rounded-2xl bg-white border border-[#DDE3D7] flex flex-col items-center justify-center p-4 text-center gap-1">
                <div className="w-9 h-9 rounded-xl bg-amber-100 flex items-center justify-center text-amber-800 mb-1">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <span className="font-bold text-xs text-[#1A2E27]">No map pin coordinates</span>
                <span className="text-[11px] text-[#748078]">
                  Click "Edit Address" below to correct and re-locate this address.
                </span>
              </div>
            )}

            {/* Details Card */}
            <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 flex flex-col gap-3 shadow-xs">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="font-extrabold text-lg text-[#1A2E27]">{selectedStop.name}</h3>
                  {selectedStop.orderId && (
                    <span className="text-[11px] font-semibold text-[#748078]">
                      Order #{selectedStop.orderId}
                    </span>
                  )}
                </div>

                {/* Status Pill */}
                <div
                  className={`px-2.5 py-1 rounded-full text-xs font-bold flex items-center gap-1 ${
                    selectedStop.delivered
                      ? 'bg-[#10B981] text-white'
                      : 'bg-[#E7ECE2] text-[#748078]'
                  }`}
                >
                  <Check className="w-3.5 h-3.5" />
                  {selectedStop.delivered ? 'Completed' : 'Pending'}
                </div>
              </div>

              {/* Address row */}
              <div className="border-t border-[#F3F5ED] pt-3 flex flex-col gap-1">
                <span className="text-[10px] font-bold tracking-wider text-[#748078] uppercase">
                  DELIVERY ADDRESS
                </span>
                {isEditingAddress ? (
                  <div className="flex flex-col gap-2 mt-1">
                    <input
                      type="text"
                      value={editedAddress}
                      onChange={(e) => setEditedAddress(e.target.value)}
                      className="border border-[#DDE3D7] rounded-xl px-3 py-2 text-xs text-[#1A2E27] focus:outline-none focus:ring-2 focus:ring-[#1E453B]"
                    />
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => setIsEditingAddress(false)}
                        className="flex-1 py-1.5 rounded-lg border border-[#DDE3D7] text-xs font-semibold text-[#748078] cursor-pointer"
                      >
                        Cancel
                      </button>
                      <button
                        type="button"
                        onClick={() => handleSaveEditedAddress()}
                        className="flex-1 py-1.5 rounded-lg bg-[#1E453B] text-white text-xs font-bold cursor-pointer"
                      >
                        Save & Geocode
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-medium text-[#1A2E27] leading-relaxed">
                      {selectedStop.address}
                    </span>
                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        type="button"
                        onClick={() => copyToClipboard(selectedStop.address, selectedStop.id)}
                        className="p-1.5 text-[#748078] hover:text-[#1E453B] transition cursor-pointer"
                        title="Copy address"
                      >
                        {copiedStopId === selectedStop.id ? (
                          <Check className="w-3.5 h-3.5 text-green-600" />
                        ) : (
                          <Copy className="w-3.5 h-3.5" />
                        )}
                      </button>
                      <button
                        type="button"
                        onClick={() => setIsEditingAddress(true)}
                        className="p-1.5 text-[#748078] hover:text-[#1E453B] transition cursor-pointer"
                        title="Edit address"
                      >
                        <Edit2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                )}
                {selectedStop.geocodedBy && selectedStop.geocodeStatus === 'located' && (
                  <div className="text-[10px] font-semibold text-[#065F46] bg-[#ECFDF5] border border-[#A7F3D0] px-2.5 py-1 rounded-lg w-fit mt-1 flex items-center gap-1">
                    <span>🛰️</span>
                    <span>Geolocated via {selectedStop.geocodedBy}</span>
                  </div>
                )}
              </div>

              {/* Phone row */}
              <div className="border-t border-[#F3F5ED] pt-3 flex flex-col gap-1">
                <span className="text-[10px] font-bold tracking-wider text-[#748078] uppercase">
                  PHONE
                </span>
                {selectedStop.phone ? (
                  <div className="flex items-center justify-between">
                    <a
                      href={`tel:${selectedStop.phone}`}
                      className="text-xs font-semibold text-[#1E453B] hover:underline flex items-center gap-1.5"
                    >
                      <Phone className="w-3.5 h-3.5" /> {selectedStop.phone}
                    </a>
                    <button
                      type="button"
                      onClick={() => copyToClipboard(selectedStop.phone, `phone-${selectedStop.id}`)}
                      className="p-1 text-[#748078] hover:text-[#1E453B] cursor-pointer"
                      title="Copy phone"
                    >
                      {copiedStopId === `phone-${selectedStop.id}` ? (
                        <Check className="w-3 h-3 text-green-600" />
                      ) : (
                        <Copy className="w-3 h-3" />
                      )}
                    </button>
                  </div>
                ) : (
                  <span className="text-xs text-[#748078]">No phone number listed</span>
                )}
              </div>
            </div>

            {/* Actions: Navigation & Tick Completed */}
            <div className="flex flex-col gap-2">
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => triggerNavigation(selectedStop)}
                  className="flex-1 py-3 rounded-xl bg-[#1E453B] text-white font-bold text-xs hover:bg-[#152F29] transition flex items-center justify-center gap-2 shadow-xs cursor-pointer"
                >
                  <Navigation className="w-4 h-4" />
                  <span>
                    Navigate
                    {preferences.defaultNavApp && preferences.defaultNavApp !== 'ask'
                      ? ` with ${NAV_APPS.find((a) => a.id === preferences.defaultNavApp)?.name || 'Maps'}`
                      : '…'}
                  </span>
                  <ExternalLink className="w-3.5 h-3.5 opacity-80" />
                </button>

                <button
                  type="button"
                  onClick={() => setNavModalStop(selectedStop)}
                  className="py-3 px-3 rounded-xl border border-[#DDE3D7] bg-white text-[#1E453B] font-semibold text-xs hover:bg-[#FAFBF7] transition cursor-pointer"
                  title="Choose navigation app"
                >
                  Choose App
                </button>
              </div>

              {/* Tick Button works on active AND saved routes */}
              <button
                type="button"
                onClick={() =>
                  handleToggleStop(
                    selectedStop.id,
                    selectedStop.delivered,
                    selectedRoute?.id,
                  )
                }
                className={`w-full py-3 rounded-xl text-xs font-bold transition flex items-center justify-center gap-2 shadow-xs cursor-pointer ${
                  selectedStop.delivered
                    ? 'bg-[#E7ECE2] text-[#1E453B] hover:bg-[#DDE3D7]'
                    : 'bg-[#10B981] text-white hover:bg-[#059669]'
                }`}
              >
                <Check className="w-4 h-4 stroke-[3]" />
                {selectedStop.delivered ? 'Completed · Click to Mark Pending' : 'Mark Delivery Completed ✓'}
              </button>
            </div>
          </>
        )}

        {/* OFFLINE MAPS DEDICATED TAB */}
        {screen === 'offline-maps' && (
          <OfflineMapTab
            stops={activeStops}
            onNavigateToRoute={() => {
              if (selectedRoute || draft) {
                setScreen('route');
              } else {
                setScreen('home');
              }
            }}
          />
        )}

        {/* OLD ROUTES (HISTORY) SCREEN */}
        {screen === 'history' && (
          <>
            <div className="flex items-center justify-between border-b border-[#DDE3D7] pb-3">
              <div>
                <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase">
                  SAVED LOG
                </span>
                <h2 className="text-2xl font-bold tracking-tight text-[#1A2E27]">Old Routes</h2>
              </div>
              <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-[#E7ECE2] text-[#1E453B]">
                {savedRoutes.length} saved
              </span>
            </div>

            <p className="text-xs text-[#748078]">
              All completed runs are saved locally in your browser. Tap any past route to review its driving animation, delivery sequence, and map.
            </p>

            {savedRoutes.length === 0 ? (
              <div className="bg-white border border-[#DDE3D7] rounded-2xl p-8 flex flex-col items-center justify-center text-center gap-2">
                <Archive className="w-10 h-10 text-[#748078]/60" />
                <span className="font-bold text-sm text-[#1A2E27]">No saved routes yet</span>
                <span className="text-xs text-[#748078] max-w-xs">
                  When you complete and save a route, it will be stored here for future reference.
                </span>
              </div>
            ) : (
              <div className="flex flex-col gap-3">
                {savedRoutes.map((route) => {
                  const completed = route.stops.filter((s) => s.delivered).length;
                  const mapped = route.stops.filter((s) => s.latitude !== null).length;
                  const dist = route.distanceMeters ?? (route.path ? calculatePathDistanceMeters(route.path) : undefined);
                  return (
                    <div
                      key={route.id}
                      onClick={() => openHistoryRoute(route)}
                      className="bg-white border border-[#DDE3D7] rounded-2xl p-4 flex flex-col gap-2.5 hover:bg-[#FAFBF7] transition shadow-xs cursor-pointer text-left"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0 flex-1">
                          <h3 className="font-bold text-sm text-[#1A2E27] truncate">{route.name}</h3>
                          <span className="text-[11px] text-[#748078]">
                            {formatDate(route.savedAt || route.createdAt, true)}
                          </span>
                        </div>
                        <div className="flex items-center gap-1 shrink-0">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              setRouteToDelete(route);
                            }}
                            className="p-2 rounded-xl text-[#A94F3E] hover:bg-red-50 hover:text-[#8F3C2C] transition cursor-pointer"
                            title={`Delete ${route.name}`}
                            aria-label={`Delete route ${route.name}`}
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                          <ChevronRight className="w-4 h-4 text-[#748078]" />
                        </div>
                      </div>
                      <div className="flex items-center justify-between pt-2.5 border-t border-[#F3F5ED] flex-wrap gap-2">
                        <div className="flex items-center gap-3 text-[11px] text-[#748078] flex-wrap">
                          <span className="flex items-center gap-1 font-semibold text-[#1A2E27]">
                            <MapPin className="w-3 h-3 text-[#1E453B]" /> {route.stops.length} stops
                          </span>
                          {dist && dist > 0 && (
                            <span className="font-medium text-[#1E453B]">
                              📍 {formatDistance(dist)}
                            </span>
                          )}
                          <span className="flex items-center gap-1">
                            <CheckCircle2 className="w-3 h-3 text-[#10B981]" /> {completed} delivered
                          </span>
                        </div>

                        {mapped > 1 && (
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              openHistoryRoute(route, true);
                            }}
                            className="py-1.5 px-3 rounded-xl bg-[#1E453B] hover:bg-[#152F29] text-[#D5EA83] text-xs font-bold flex items-center gap-1.5 shadow-2xs transition cursor-pointer"
                            title="Preview driving route animation"
                          >
                            <Play className="w-3.5 h-3.5 fill-current" />
                            <span>Preview Route Animation</span>
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </>
        )}

        {/* HISTORY ROUTE DETAIL */}
        {screen === 'history-route' && selectedRoute &&
          (device.isDashmountLandscape ? (
            <div className="flex flex-col h-full w-full gap-2 min-h-0 overflow-hidden">
              {/* Ultra-compact Top Bar */}
              <div className="flex items-center justify-between bg-[#1E453B] text-white px-3 py-1.5 rounded-xl shrink-0 shadow-xs">
                <div className="flex items-center gap-2 min-w-0">
                  <button
                    type="button"
                    onClick={goBack}
                    className="w-8 h-8 rounded-lg bg-white/10 hover:bg-white/20 flex items-center justify-center text-white cursor-pointer shrink-0"
                    title="Back"
                  >
                    <ArrowLeft className="w-4 h-4" />
                  </button>
                  <div className="truncate min-w-0">
                    <span className="font-extrabold text-xs text-white truncate block">{selectedRoute.name}</span>
                    <span className="text-[10px] text-[#D5EA83]">
                      {selectedRoute.stops.filter((s) => s.delivered).length}/{selectedRoute.stops.length} completed
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    type="button"
                    onClick={() => setIsAnimatingRoute((prev) => !prev)}
                    className={`px-2.5 py-1 rounded-lg text-xs font-bold flex items-center gap-1 cursor-pointer transition ${
                      isAnimatingRoute ? 'bg-[#D5EA83] text-[#1E453B]' : 'bg-white/10 text-white hover:bg-white/20'
                    }`}
                  >
                    {isAnimatingRoute ? <Pause className="w-3 h-3" /> : <Play className="w-3 h-3 fill-current" />}
                    <span>{isAnimatingRoute ? 'Pause' : 'Drive'}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => handleExportRouteVideo(selectedRoute)}
                    disabled={isExportingVideo}
                    className="px-2.5 py-1 rounded-lg bg-[#D5EA83] text-[#1E453B] text-xs font-bold flex items-center gap-1 cursor-pointer disabled:opacity-50"
                    title="Export 25-second cinematic route video"
                  >
                    <Video className="w-3 h-3" />
                    <span>25s MP4</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsSummaryModalOpen(true)}
                    className="px-2.5 py-1 rounded-lg bg-white/15 text-white hover:bg-white/25 text-xs font-bold cursor-pointer"
                  >
                    Stats
                  </button>
                  <button
                    type="button"
                    onClick={() => setRouteToDelete(selectedRoute)}
                    className="p-1 rounded-lg text-[#F87171] hover:bg-red-500/20 cursor-pointer"
                    title="Delete route"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {/* Dual-pane CarPlay style layout */}
              <div className="flex-1 grid grid-cols-12 gap-2 min-h-0 overflow-hidden">
                <div className="col-span-7 h-full relative rounded-2xl overflow-hidden border border-[#DDE3D7]">
                  <LeafletMap
                    stops={selectedRoute.stops}
                    path={selectedRoute.path}
                    markerColor={preferences.markerColor}
                    lineColor={preferences.lineColor}
                    lineWidth={preferences.lineWidth}
                    currentLayer={preferences.mapLayer || 'osm'}
                    onLayerChange={(layer) => updatePreferences({ mapLayer: layer })}
                    height="100%"
                    className="h-full w-full"
                    animateDrawRoute={true}
                    drawKey={routeDrawTrigger}
                    isAnimating={isAnimatingRoute}
                    animationSpeed={animationSpeed}
                    onAnimationProgress={(percent, nearestIdx) => {
                      setAnimationPercent(percent);
                      setAnimationActiveStopIdx(nearestIdx);
                    }}
                    onAnimationFinished={() => {
                      showToast('Driving preview animation complete!');
                      setIsAnimatingRoute(false);
                    }}
                    onToggleDelivery={(id, currentStatus) => handleToggleStop(id, currentStatus, selectedRoute.id)}
                    onNavigate={(stop) => triggerNavigation(stop)}
                    onOpenDetails={(stop) => openHistoryStop(stop)}
                    onSelectStop={(stop) => setSelectedStop(stop)}
                    onOpenOfflineModal={() => navTo('offline-maps')}
                  />
                </div>

                <div className="col-span-5 h-full flex flex-col gap-1.5 min-h-0 overflow-hidden">
                  <div className="flex-1 overflow-y-auto bg-white border border-[#DDE3D7] rounded-xl divide-y divide-[#F3F5ED] min-h-0 shadow-xs">
                    {selectedRoute.stops.map((stop, index) => (
                      <div
                        key={stop.id}
                        className={`p-2 flex items-center justify-between gap-2 text-xs transition ${
                          stop.delivered ? 'bg-[#F9FAF6]' : 'hover:bg-[#FAFBF7]'
                        }`}
                      >
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleToggleStop(stop.id, stop.delivered, selectedRoute.id);
                          }}
                          className={`w-7 h-7 rounded-lg flex items-center justify-center transition-all cursor-pointer shadow-xs active:scale-95 shrink-0 ${
                            stop.delivered
                              ? 'bg-[#10B981] text-white'
                              : 'bg-white border-2 border-[#CBD5E1] hover:border-[#10B981]'
                          }`}
                        >
                          {stop.delivered ? <Check className="w-3.5 h-3.5 stroke-[3.5]" /> : <div className="w-2.5 h-2.5 rounded-full border-2 border-[#94A3B8]" />}
                        </button>
                        <div onClick={() => openHistoryStop(stop)} className="min-w-0 flex-1 cursor-pointer">
                          <div className="font-bold text-[11px] text-[#1A2E27] truncate">
                            <span className={stop.delivered ? 'line-through text-[#748078]' : ''}>
                              #{index + 1} {stop.name}
                            </span>
                          </div>
                          <div className="text-[10px] text-[#748078] truncate">{stop.address}</div>
                        </div>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            triggerNavigation(stop);
                          }}
                          className="p-1 rounded-lg bg-[#E7ECE2] hover:bg-[#DDE3D7] text-[#1E453B] cursor-pointer shrink-0"
                        >
                          <Navigation className="w-3 h-3" />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <>
            <div className="flex items-center justify-between border-b border-[#DDE3D7] pb-3">
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={goBack}
                  className="w-9 h-9 rounded-xl bg-[#E7ECE2] flex items-center justify-center text-[#1A2E27] hover:bg-[#DDE3D7] transition cursor-pointer"
                >
                  <ArrowLeft className="w-4 h-4" />
                </button>
                <div>
                  <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase truncate block max-w-xs">
                    {formatDate(selectedRoute.savedAt || selectedRoute.createdAt)}
                  </span>
                  <h2 className="text-xl font-bold tracking-tight text-[#1A2E27]">
                    {selectedRoute.name}
                  </h2>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {/* Summary Modal Button */}
                <button
                  type="button"
                  onClick={() => setIsSummaryModalOpen(true)}
                  className="py-1.5 px-3 rounded-xl bg-[#E7ECE2] hover:bg-[#DDE3D7] text-[#1E453B] text-xs font-bold transition flex items-center gap-1.5 cursor-pointer"
                  title="Route Summary Stats"
                >
                  <BarChart3 className="w-3.5 h-3.5" />
                  <span>Stats</span>
                </button>

                <button
                  type="button"
                  onClick={() => setRouteToDelete(selectedRoute)}
                  className="text-[#A94F3E] p-2 hover:bg-red-50 rounded-lg transition cursor-pointer"
                  title="Delete run"
                  aria-label="Delete saved route"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Animation Toggle Button for Saved Route */}
            <div className="flex items-center justify-between bg-white border border-[#DDE3D7] rounded-xl p-3 shadow-xs">
              <div className="text-xs">
                <span className="font-bold text-[#1A2E27] block">
                  {selectedRoute.stops.length} stops ({selectedRoute.stops.filter((s) => s.delivered).length} delivered)
                </span>
                <span className="text-[#748078] text-[11px]">
                  {formatDistance(selectedRoute.distanceMeters ?? (selectedRoute.path ? calculatePathDistanceMeters(selectedRoute.path) : undefined))} total route
                </span>
              </div>

              {selectedRoute.stops.filter((s) => s.latitude !== null).length > 1 && (
                <div className="flex items-center gap-2 flex-wrap">
                  <button
                    type="button"
                    onClick={() => setIsAnimatingRoute((prev) => !prev)}
                    className={`py-2 px-3.5 rounded-xl text-xs font-bold flex items-center gap-2 transition cursor-pointer shadow-xs ${
                      isAnimatingRoute
                        ? 'bg-[#1E453B] text-[#D5EA83]'
                        : 'bg-[#10B981] hover:bg-[#059669] text-white'
                    }`}
                  >
                    {isAnimatingRoute ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5 fill-current" />}
                    <span>{isAnimatingRoute ? 'Pause Animation' : 'Preview Drive'}</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => handleExportRouteVideo(selectedRoute)}
                    disabled={isExportingVideo}
                    className="py-2 px-3.5 rounded-xl text-xs font-bold flex items-center gap-1.5 bg-[#1E453B] hover:bg-[#152F29] text-[#D5EA83] transition cursor-pointer shadow-xs disabled:opacity-50"
                    title="Export high-definition MP4 video of this route animation"
                  >
                    <Video className="w-3.5 h-3.5" />
                    <span>Export MP4</span>
                  </button>
                </div>
              )}
            </div>

            {/* Animation Progress Bar */}
            {isAnimatingRoute && (
              <div className="bg-[#1E453B] text-white rounded-2xl p-3.5 shadow-md flex items-center justify-between gap-3 animate-in fade-in duration-200">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-[#D5EA83] text-[#273626] flex items-center justify-center font-bold text-xs">
                    🚐
                  </div>
                  <div className="text-xs">
                    <div className="font-bold text-[#D5EA83]">
                      Route Drive Animation: {animationPercent}%
                    </div>
                    <div className="text-[11px] opacity-80 truncate max-w-[200px]">
                      Stop #{animationActiveStopIdx + 1}: {selectedRoute.stops[animationActiveStopIdx]?.name}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-1.5">
                  {[1, 2, 5].map((spd) => (
                    <button
                      key={spd}
                      type="button"
                      onClick={() => setAnimationSpeed(spd)}
                      className={`px-2 py-1 rounded text-[10px] font-bold transition cursor-pointer ${
                        animationSpeed === spd
                          ? 'bg-[#D5EA83] text-[#273626]'
                          : 'bg-white/10 text-white hover:bg-white/20'
                      }`}
                    >
                      {spd}x
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() => handleExportRouteVideo(selectedRoute)}
                    disabled={isExportingVideo}
                    className="px-2 py-1 rounded text-[10px] font-bold bg-[#D5EA83] text-[#273626] hover:bg-[#c6de6a] flex items-center gap-1 transition cursor-pointer ml-1"
                    title="Export video MP4"
                  >
                    <Download className="w-3 h-3" />
                    <span>MP4</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsAnimatingRoute(false)}
                    className="p-1 rounded-lg hover:bg-white/10 text-white/80 cursor-pointer ml-1"
                    title="Stop animation"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}

            {/* Interactive Map */}
            {selectedRoute.stops.some((s) => s.latitude !== null) ? (
              <div className="flex flex-col gap-2">
                <LeafletMap
                  stops={selectedRoute.stops}
                  path={selectedRoute.path}
                  markerColor={preferences.markerColor}
                  lineColor={preferences.lineColor}
                  lineWidth={preferences.lineWidth}
                  currentLayer={preferences.mapLayer || 'osm'}
                  onLayerChange={(layer) => updatePreferences({ mapLayer: layer })}
                  height={320}
                  animateDrawRoute={true}
                  drawKey={routeDrawTrigger}
                  isAnimating={isAnimatingRoute}
                  animationSpeed={animationSpeed}
                  onAnimationProgress={(percent, nearestIdx) => {
                    setAnimationPercent(percent);
                    setAnimationActiveStopIdx(nearestIdx);
                  }}
                  onAnimationFinished={() => {
                    showToast('Route animation preview complete!');
                    setIsAnimatingRoute(false);
                  }}
                  onToggleDelivery={(id, currentStatus) =>
                    handleToggleStop(id, currentStatus, selectedRoute.id)
                  }
                  onNavigate={(stop) => triggerNavigation(stop)}
                  onOpenDetails={(stop) => openHistoryStop(stop)}
                  onOpenOfflineModal={() => navTo('offline-maps')}
                />
                <span className="text-[11px] text-[#748078] px-1">
                  💡 Click any pin above to view quick address details, navigate, or mark completed
                </span>
              </div>
            ) : (
              <div className="h-44 rounded-2xl bg-white border border-[#DDE3D7] flex flex-col items-center justify-center text-center p-4">
                <span className="text-xs text-[#748078]">No map coordinates saved for this run</span>
              </div>
            )}

            {/* Stops sequence with working Tick button */}
            <div>
              <h4 className="font-bold text-xs tracking-tight text-[#1A2E27] mb-2 px-1">
                Delivery Sequence ({selectedRoute.stops.length} stops)
              </h4>
              <div className="bg-white border border-[#DDE3D7] rounded-2xl divide-y divide-[#F3F5ED] overflow-hidden shadow-xs">
                {selectedRoute.stops.map((stop, index) => {
                  const isUnlocated = stop.latitude === null;
                  return (
                    <div
                      key={stop.id}
                      className={`p-3 flex items-center justify-between gap-3 transition ${
                        stop.delivered ? 'bg-[#F9FAF6]' : 'hover:bg-[#FAFBF7]'
                      }`}
                    >
                      {/* Dedicated Tick button on historical routes */}
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          e.preventDefault();
                          handleToggleStop(stop.id, stop.delivered, selectedRoute.id);
                        }}
                        className={`w-8 h-8 rounded-xl flex items-center justify-center transition-all cursor-pointer shadow-xs active:scale-95 shrink-0 group ${
                          stop.delivered
                            ? 'bg-[#10B981] hover:bg-[#059669] text-white ring-2 ring-[#D1FAE5]'
                            : 'bg-white hover:bg-[#F0FDF4] border-2 border-[#CBD5E1] hover:border-[#10B981]'
                        }`}
                        title={stop.delivered ? '✓ Delivered · Click to mark pending' : 'Click to confirm delivery'}
                      >
                        {stop.delivered ? (
                          <Check className="w-4 h-4 stroke-[3.5]" />
                        ) : (
                          <div className="w-3 h-3 rounded-full border-2 border-[#94A3B8] group-hover:border-[#10B981] transition-colors" />
                        )}
                      </button>

                      <div
                        onClick={() => openHistoryStop(stop)}
                        className="flex items-center gap-2.5 flex-1 min-w-0 cursor-pointer"
                      >
                        <div
                          className={`w-6 h-6 rounded-md flex items-center justify-center font-bold text-xs shrink-0 ${
                            isUnlocated
                              ? 'bg-amber-100 text-amber-900 border border-amber-300'
                              : 'bg-[#D5EA83] text-[#273626]'
                          }`}
                        >
                          {index + 1}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span
                              className={`font-semibold text-xs text-[#1A2E27] truncate ${
                                stop.delivered ? 'line-through text-[#748078]' : ''
                              }`}
                            >
                              {stop.name}
                            </span>
                            {isUnlocated && (
                              <span className="inline-flex items-center gap-1 px-1.5 py-0.2 rounded-md bg-amber-100 text-amber-900 border border-amber-300 font-bold text-[9px]">
                                <AlertTriangle className="w-2.5 h-2.5 text-amber-700" /> Not Located
                              </span>
                            )}
                            {stop.delivered && (
                              <span className="text-[10px] font-semibold text-[#065F46] bg-[#D1FAE5] px-1.5 py-0.2 rounded">
                                Done
                              </span>
                            )}
                          </div>
                          <div className="text-[11px] text-[#748078] truncate">{stop.address}</div>
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 shrink-0">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            triggerNavigation(stop);
                          }}
                          className="w-8 h-8 rounded-xl bg-[#E7ECE2] hover:bg-[#DDE3D7] text-[#1E453B] flex items-center justify-center transition cursor-pointer"
                          title="Navigate"
                        >
                          <Navigation className="w-3.5 h-3.5" />
                        </button>
                        <ChevronRight
                          onClick={() => openHistoryStop(stop)}
                          className="w-4 h-4 text-[#748078] cursor-pointer"
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </>
        ))}

        {/* ALL-TIME DELIVERY MAP */}
        {screen === 'all-time-map' && (
          <>
            <div className="flex items-center justify-between border-b border-[#DDE3D7] pb-3">
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={goBack}
                  className="w-9 h-9 rounded-xl bg-[#E7ECE2] flex items-center justify-center text-[#1A2E27] hover:bg-[#DDE3D7] transition cursor-pointer"
                >
                  <ArrowLeft className="w-4 h-4" />
                </button>
                <div>
                  <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase">
                    ALL-TIME OVERVIEW
                  </span>
                  <h2 className="text-xl font-bold tracking-tight text-[#1A2E27]">
                    Delivery Map
                  </h2>
                </div>
              </div>
              <button
                type="button"
                onClick={requestExport}
                disabled={locatedHistory.length === 0 || isExporting}
                className="py-1.5 px-3 rounded-xl bg-[#1E453B] text-white text-xs font-bold hover:bg-[#152F29] disabled:opacity-50 transition flex items-center gap-1.5 shadow-xs cursor-pointer"
              >
                <Download className="w-3.5 h-3.5" />
                {isExporting ? 'Exporting…' : 'Export JPEG'}
              </button>
            </div>

            {mapMessage && (
              <div className="bg-[#E7ECE2] text-[#1E453B] text-xs p-3 rounded-xl flex items-center gap-2">
                <Info className="w-4 h-4 shrink-0" />
                <span>{mapMessage}</span>
              </div>
            )}

            {locatedHistory.length > 0 ? (
              <LeafletMap
                stops={locatedHistory}
                paths={historicalPaths}
                markerColor={preferences.markerColor}
                lineColor={preferences.lineColor}
                lineWidth={preferences.lineWidth}
                currentLayer={preferences.mapLayer || 'osm'}
                onLayerChange={(layer) => updatePreferences({ mapLayer: layer })}
                height={480}
                exportRequest={exportRequest}
                onExport={exportAllTimeMap}
                onError={(err) => setMapMessage(`Export error: ${err}`)}
                onToggleDelivery={(id, currentStatus) => handleToggleStop(id, currentStatus)}
                onNavigate={(stop) => triggerNavigation(stop)}
                onOpenOfflineModal={() => navTo('offline-maps')}
              />
            ) : (
              <div className="h-64 rounded-2xl bg-white border border-[#DDE3D7] flex flex-col items-center justify-center text-center p-6 gap-2">
                <Layers className="w-8 h-8 text-[#748078]" />
                <span className="font-bold text-sm text-[#1A2E27]">No located visits yet</span>
                <span className="text-xs text-[#748078] max-w-xs">
                  Save a delivery route with mapped addresses to visualize all your past delivery stops on this map.
                </span>
              </div>
            )}

            <div className="bg-white border border-[#DDE3D7] rounded-xl p-3 flex items-center gap-4 text-xs font-medium text-[#1A2E27]">
              <div className="flex items-center gap-1.5">
                <div
                  className="w-3 h-3 rounded-full"
                  style={{ backgroundColor: preferences.markerColor }}
                />
                <span>{locatedHistory.length} Visited Addresses</span>
              </div>
              <div className="flex items-center gap-1.5">
                <div
                  className="w-4 h-1 rounded-sm"
                  style={{ backgroundColor: preferences.lineColor }}
                />
                <span>{historicalPaths.length} Completed Routes</span>
              </div>
            </div>

            <p className="text-[11px] text-[#748078] leading-relaxed">
              Export creates a high-resolution 2600 × 1800 JPEG image with OpenStreetMap tiles, route trails, and stop markers.
            </p>
          </>
        )}

        {/* SETTINGS SCREEN */}
        {screen === 'settings' && (
          <>
            <div className="border-b border-[#DDE3D7] pb-3">
              <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase">
                PREFERENCES
              </span>
              <h2 className="text-2xl font-bold tracking-tight text-[#1A2E27]">Settings</h2>
            </div>

            {/* 1. Offline & Local Storage Section */}
            <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 flex flex-col gap-3 shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-[#E7ECE2] text-[#1E453B] flex items-center justify-center font-bold text-base shrink-0 shadow-xs">
                  <Download className="w-5 h-5 text-[#1E453B]" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="font-bold text-sm text-[#1A2E27]">Offline Maps & Storage</h3>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-[#E7ECE2] text-[#1E453B]">
                      Dedicated Tab
                    </span>
                  </div>
                  <p className="text-xs text-[#748078]">
                    Interactive map selector & pre-downloaded territories
                  </p>
                </div>
              </div>

              <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl p-3.5 text-xs text-[#53635d] flex flex-col gap-2.5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-[#10B981]" />
                    <span className="font-semibold text-[#1A2E27]">
                      {offlinePackCount > 0
                        ? `${offlinePackCount} Saved Offline Area Pack${offlinePackCount > 1 ? 's' : ''}`
                        : 'No offline areas saved yet'}
                    </span>
                  </div>
                  <span className="text-[10px] font-bold text-[#1E453B] bg-[#E7ECE2] px-2 py-0.5 rounded-full">
                    Auto Z5–17
                  </span>
                </div>
                <p className="text-[11px] leading-relaxed text-[#748078]">
                  Offline map downloading now has its own dedicated tab on the bottom bar. Select any area with the interactive map selector to download all tiles from regional overview (Zoom 5) to building level (Zoom 17) for zero-signal driving.
                </p>
                <div className="flex flex-col sm:flex-row gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => navTo('offline-maps')}
                    className="flex-1 py-2.5 px-3 rounded-xl bg-[#1E453B] hover:bg-[#152F29] text-white font-bold text-xs flex items-center justify-center gap-2 transition cursor-pointer shadow-xs"
                  >
                    <Download className="w-4 h-4" />
                    <span>Go to Offline Maps Tab</span>
                  </button>
                  <div className="sm:w-auto">
                    <PWAInstallButton variant="full" />
                  </div>
                </div>
              </div>
            </div>

            {/* 2. Navigation App Preference */}
            <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 flex flex-col gap-3 shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-[#E7ECE2] text-[#1E453B] flex items-center justify-center shrink-0">
                  <Navigation className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-sm text-[#1A2E27]">Preferred Navigation App</h3>
                  <p className="text-xs text-[#748078]">
                    Default map application when tapping Navigate on any stop
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 pt-1">
                {[
                  { id: 'ask' as const, label: 'Always Ask Me', badge: 'Prompt' },
                  { id: 'google' as const, label: 'Google Maps', badge: 'Popular' },
                  { id: 'apple' as const, label: 'Apple Maps', badge: 'iOS / Mac' },
                  { id: 'waze' as const, label: 'Waze', badge: 'Traffic' },
                  { id: 'citymapper' as const, label: 'Citymapper', badge: 'Metro' },
                  { id: 'osm' as const, label: 'OpenStreetMap', badge: 'FOSS' },
                ].map((item) => {
                  const isSelected =
                    (preferences.defaultNavApp || 'ask') === item.id;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => updatePreferences({ defaultNavApp: item.id })}
                      className={`p-2.5 rounded-xl border text-left flex flex-col justify-between transition cursor-pointer ${
                        isSelected
                          ? 'border-[#1E453B] bg-[#E7ECE2] text-[#1E453B] ring-1 ring-[#1E453B]'
                          : 'border-[#DDE3D7] bg-white hover:bg-[#FAFBF7] text-[#1A2E27]'
                      }`}
                    >
                      <div className="flex items-center justify-between w-full">
                        <span className="text-[10px] font-bold px-1.5 py-0.2 rounded bg-black/5">
                          {item.badge}
                        </span>
                        {isSelected && <Check className="w-3.5 h-3.5 text-[#1E453B]" />}
                      </div>
                      <span className="font-bold text-xs mt-2">{item.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 3. Map Appearance & Display Settings */}
            <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 flex flex-col gap-4 shadow-xs">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-[#E7ECE2] text-[#1E453B] flex items-center justify-center shrink-0">
                    <Layers className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-[#1A2E27]">Map Appearance</h3>
                    <p className="text-xs text-[#748078]">
                      Customize pins, route trails, and all-time history overlay
                    </p>
                  </div>
                </div>
              </div>

              {/* All Time Map Toggle */}
              <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl p-3 flex items-center justify-between">
                <div>
                  <span className="font-bold text-xs text-[#1A2E27] block">Show All-Time Map</span>
                  <span className="text-[11px] text-[#748078]">
                    Combine every visited delivery stop in one comprehensive map
                  </span>
                </div>
                <label className="relative inline-flex items-center cursor-pointer shrink-0 ml-3">
                  <input
                    type="checkbox"
                    checked={preferences.showAllTimeMap}
                    onChange={(e) => updatePreferences({ showAllTimeMap: e.target.checked })}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-[#DDE3D7] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-[#DDE3D7] after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#1E453B]"></div>
                </label>
              </div>

              {/* Default Map Style / Layer */}
              <div className="border-t border-[#F3F5ED] pt-3">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-semibold text-[#748078]">Default Map Style</span>
                  <span className="text-xs font-bold text-[#1A2E27] capitalize">
                    {preferences.mapLayer === 'satellite'
                      ? 'Satellite'
                      : preferences.mapLayer === 'topo'
                      ? 'Terrain Topo'
                      : 'Street Map'}
                  </span>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  {[
                    { id: 'osm', label: 'Street Map', desc: 'Standard OSM' },
                    { id: 'satellite', label: 'Satellite', desc: 'ArcGIS Aerial' },
                    { id: 'topo', label: 'Terrain', desc: 'OpenTopo Contour' },
                  ].map((layer) => {
                    const isSelected = (preferences.mapLayer || 'osm') === layer.id;
                    return (
                      <button
                        key={layer.id}
                        type="button"
                        onClick={() => updatePreferences({ mapLayer: layer.id as 'osm' | 'satellite' | 'topo' })}
                        className={`p-2.5 rounded-xl border text-left transition cursor-pointer flex flex-col gap-0.5 ${
                          isSelected
                            ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                            : 'bg-[#FAFBF7] hover:bg-[#F3F5ED] text-[#1A2E27] border-[#DDE3D7]'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-xs">{layer.label}</span>
                          {isSelected && <Check className="w-3.5 h-3.5 text-[#D5EA83]" />}
                        </div>
                        <span className={`text-[10px] ${isSelected ? 'text-white/80' : 'text-[#748078]'}`}>
                          {layer.desc}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Marker Colors */}
              <div>
                <span className="text-xs font-semibold text-[#748078] block mb-2">
                  Stop Marker Color
                </span>
                <div className="flex items-center gap-3">
                  {MAP_MARKER_COLORS.map((opt) => (
                    <button
                      key={opt.value}
                      type="button"
                      onClick={() => updatePreferences({ markerColor: opt.value })}
                      className="w-8 h-8 rounded-xl flex items-center justify-center border-2 transition cursor-pointer"
                      style={{
                        backgroundColor: opt.value,
                        borderColor:
                          preferences.markerColor === opt.value ? '#1A2E27' : 'transparent',
                      }}
                      title={opt.label}
                    >
                      {preferences.markerColor === opt.value && (
                        <Check className="w-4 h-4 text-white" />
                      )}
                    </button>
                  ))}
                </div>
              </div>

              {/* Route Line Color */}
              <div className="border-t border-[#F3F5ED] pt-3">
                <span className="text-xs font-semibold text-[#748078] block mb-2">
                  Route Trail Color
                </span>
                <div className="flex items-center gap-3">
                  {MAP_LINE_COLORS.map((opt) => (
                    <button
                      key={opt.value}
                      type="button"
                      onClick={() => updatePreferences({ lineColor: opt.value })}
                      className="w-8 h-8 rounded-xl flex items-center justify-center border-2 transition cursor-pointer"
                      style={{
                        backgroundColor: opt.value,
                        borderColor:
                          preferences.lineColor === opt.value ? '#1A2E27' : 'transparent',
                      }}
                      title={opt.label}
                    >
                      {preferences.lineColor === opt.value && (
                        <Check className="w-4 h-4 text-[#1A2E27]" />
                      )}
                    </button>
                  ))}
                </div>
              </div>

              {/* Line Weight */}
              <div className="border-t border-[#F3F5ED] pt-3">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-semibold text-[#748078]">Line Thickness</span>
                  <span className="text-xs font-bold text-[#1A2E27]">{preferences.lineWidth}px</span>
                </div>
                <div className="flex gap-2">
                  {[3, 4, 6].map((w) => (
                    <button
                      key={w}
                      type="button"
                      onClick={() => updatePreferences({ lineWidth: w })}
                      className={`flex-1 py-2 rounded-xl border flex items-center justify-center transition cursor-pointer ${
                        preferences.lineWidth === w
                          ? 'border-[#1E453B] bg-[#E7ECE2]'
                          : 'border-[#DDE3D7] bg-white'
                      }`}
                    >
                      <div
                        className="w-8 rounded-full"
                        style={{ height: w, backgroundColor: preferences.lineColor }}
                      />
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* 4. Automated Geocoder & Privacy Information */}
            <div className="bg-white border border-[#DDE3D7] rounded-2xl p-4 flex flex-col gap-3 shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-[#E7ECE2] text-[#1E453B] flex items-center justify-center shrink-0">
                  <Shield className="w-5 h-5 text-[#1E453B]" />
                </div>
                <div>
                  <h3 className="font-bold text-sm text-[#1A2E27]">Privacy & Free Open Data</h3>
                  <p className="text-xs text-[#748078]">
                    Zero tracking, zero external databases, 100% on-device
                  </p>
                </div>
              </div>

              <div className="text-xs text-[#748078] leading-relaxed bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl p-3 flex flex-col gap-2">
                <p>
                  Customer names, phone numbers, and delivery notes remain stored exclusively in your local browser storage. No proprietary external APIs or tracking scripts are loaded.
                </p>
                <div className="border-t border-[#DDE3D7] pt-2">
                  <span className="font-bold text-[#1A2E27] block mb-1.5 text-[11px]">
                    Automatic Multi-Service Fallbacks:
                  </span>
                  <div className="grid grid-cols-2 gap-1.5 text-[10px]">
                    <span className="bg-white px-2 py-1 rounded-lg border border-[#DDE3D7] font-semibold text-[#1E453B]">
                      1. OpenStreetMap (Nominatim)
                    </span>
                    <span className="bg-white px-2 py-1 rounded-lg border border-[#DDE3D7] font-semibold text-[#1E453B]">
                      2. Komoot Photon (Fuzzy)
                    </span>
                    <span className="bg-white px-2 py-1 rounded-lg border border-[#DDE3D7] font-semibold text-[#1E453B]">
                      3. Postcodes.io (UK Exact)
                    </span>
                    <span className="bg-white px-2 py-1 rounded-lg border border-[#DDE3D7] font-semibold text-[#1E453B]">
                      4. Open-Meteo (Towns)
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <div className="text-center text-[11px] text-[#748078] pt-2">
              RouteKeeper · Powered by OpenStreetMap & OSRM
            </div>
          </>
        )}
      </main>

      {/* ROUTE SUMMARY MODAL (Requested feature #1) */}
      {isSummaryModalOpen && currentSummaryRoute && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-md rounded-3xl p-6 shadow-2xl flex flex-col gap-4 animate-in zoom-in-95 duration-200 max-h-[90vh] overflow-y-auto">
            {/* Header */}
            <div className="flex items-start justify-between border-b border-[#F3F5ED] pb-3">
              <div>
                <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase">
                  DELIVERY OVERVIEW
                </span>
                <h3 className="font-extrabold text-xl text-[#1A2E27] mt-0.5">
                  Route Summary
                </h3>
                <p className="text-xs text-[#748078] mt-0.5">
                  {currentSummaryRoute.name} · {summaryStops.length} Total Deliveries
                </p>
              </div>
              <button
                type="button"
                onClick={() => setIsSummaryModalOpen(false)}
                className="p-1.5 rounded-full hover:bg-[#E7ECE2] text-[#748078] cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Route Generation & Map Visualizer */}
            {summaryStops.filter((s) => s.latitude !== null).length > 0 && (
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between text-xs px-1">
                  <span className="font-bold text-[#1A2E27] flex items-center gap-1.5">
                    <Compass className="w-3.5 h-3.5 text-[#1E453B]" />
                    <span>Route Generation Preview</span>
                  </span>
                  <span className="text-[11px] text-[#748078]">
                    {summaryStops.filter((s) => s.latitude !== null).length} mapped on route
                  </span>
                </div>
                <div className="rounded-2xl overflow-hidden border border-[#DDE3D7] shadow-xs">
                  <LeafletMap
                    stops={summaryStops}
                    path={currentSummaryRoute.path}
                    markerColor={preferences.markerColor}
                    lineColor={preferences.lineColor}
                    lineWidth={preferences.lineWidth}
                    currentLayer={preferences.mapLayer || 'osm'}
                    onLayerChange={(layer) => updatePreferences({ mapLayer: layer })}
                    height={220}
                    animateDrawRoute={true}
                    drawKey={routeDrawTrigger}
                    waitStops={currentSummaryRoute.waitStops}
                    onNavigateWaitStop={(ws) => {
                      window.open(
                        `https://www.google.com/maps/dir/?api=1&destination=${ws.latitude},${ws.longitude}`,
                        '_blank',
                      );
                    }}
                    onToggleDelivery={(id, status) =>
                      handleToggleStop(
                        id,
                        status,
                        currentSummaryRoute.id !== draft?.id ? currentSummaryRoute.id : undefined,
                      )
                    }
                    onNavigate={(stop) => triggerNavigation(stop)}
                    onOpenOfflineModal={() => {
                      setIsSummaryModalOpen(false);
                      navTo('offline-maps');
                    }}
                  />
                </div>
              </div>
            )}

            {/* Key Metrics Grid */}
            <div className="grid grid-cols-2 gap-3">
              {/* Total Stops */}
              <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-1">
                <div className="flex items-center gap-1.5 text-xs font-semibold text-[#748078]">
                  <Milestone className="w-4 h-4 text-[#1E453B]" /> Total Stops
                </div>
                <div className="text-2xl font-extrabold text-[#1A2E27]">
                  {summaryStops.length}
                </div>
                <span className="text-[11px] text-[#748078]">
                  {summaryStops.filter((s) => s.latitude !== null).length} mapped on route
                </span>
              </div>

              {/* Total Distance */}
              <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-1">
                <div className="flex items-center gap-1.5 text-xs font-semibold text-[#748078]">
                  <Navigation className="w-4 h-4 text-[#1E453B]" /> Total Distance
                </div>
                <div className="text-2xl font-extrabold text-[#1A2E27]">
                  {summaryDistance && summaryDistance > 0
                    ? (summaryDistance * 0.000621371).toFixed(1) + ' mi'
                    : 'N/A'}
                </div>
                <span className="text-[11px] text-[#748078]">
                  {summaryDistance && summaryDistance > 0
                    ? `${(summaryDistance / 1000).toFixed(1)} km road network`
                    : 'Awaiting route calculation'}
                </span>
              </div>

              {/* Completion Rate */}
              <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-1">
                <div className="flex items-center gap-1.5 text-xs font-semibold text-[#748078]">
                  <Gauge className="w-4 h-4 text-[#10B981]" /> Completion
                </div>
                <div className="text-2xl font-extrabold text-[#10B981]">
                  {summaryPercent}%
                </div>
                <span className="text-[11px] text-[#748078]">
                  {summaryDeliveredCount} of {summaryStops.length} delivered
                </span>
              </div>

              {/* Est. Drive Duration */}
              <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-1">
                <div className="flex items-center gap-1.5 text-xs font-semibold text-[#748078]">
                  <Clock className="w-4 h-4 text-[#1E453B]" /> Est. Drive Time
                </div>
                <div className="text-2xl font-extrabold text-[#1A2E27]">
                  {summaryDuration ? formatDuration(summaryDuration) : 'Calculating'}
                </div>
                <span className="text-[11px] text-[#748078]">
                  Optimal driving sequence
                </span>
              </div>
            </div>

            {/* Visual Progress Bar */}
            <div className="bg-white border border-[#DDE3D7] rounded-2xl p-3.5 flex flex-col gap-2">
              <div className="flex items-center justify-between text-xs font-bold text-[#1A2E27]">
                <span>Delivery Progress</span>
                <span className="text-[#10B981]">{summaryPercent}% Done</span>
              </div>
              <div className="w-full bg-[#E7ECE2] h-3 rounded-full overflow-hidden">
                <div
                  className="bg-[#10B981] h-full rounded-full transition-all duration-300"
                  style={{ width: `${summaryPercent}%` }}
                />
              </div>
              <div className="flex items-center justify-between text-[11px] text-[#748078]">
                <span>✓ {summaryDeliveredCount} Delivered</span>
                <span>⏳ {summaryStops.length - summaryDeliveredCount} Remaining</span>
              </div>
            </div>

            {/* Stops list breakdown */}
            <div className="flex flex-col gap-1">
              <span className="text-xs font-bold text-[#1A2E27] px-1">
                Stops Status Breakdown ({summaryStops.length} stops)
              </span>
              <div className="max-h-60 overflow-y-auto divide-y divide-[#F3F5ED] border border-[#DDE3D7] rounded-2xl bg-white px-3 py-1">
                {summaryStops.map((stop, idx) => {
                  const isUnlocated =
                    stop.geocodeStatus === 'not-found' ||
                    (stop.latitude === null && currentSummaryRoute?.routingMode !== 'not-planned');

                  return (
                    <div key={stop.id} className="py-2.5 flex flex-col gap-2">
                      <div className="flex items-center justify-between gap-2.5 text-xs">
                        <div className="flex items-center gap-2.5 min-w-0">
                          <button
                            type="button"
                            onClick={() =>
                              handleToggleStop(
                                stop.id,
                                stop.delivered,
                                currentSummaryRoute && currentSummaryRoute.id !== draft?.id
                                  ? currentSummaryRoute.id
                                  : undefined,
                              )
                            }
                            className={`w-7 h-7 rounded-lg flex items-center justify-center transition-all cursor-pointer shadow-2xs active:scale-90 shrink-0 group ${
                              stop.delivered
                                ? 'bg-[#10B981] text-white ring-2 ring-[#D1FAE5]'
                                : 'bg-white hover:bg-[#F0FDF4] border-2 border-[#CBD5E1] hover:border-[#10B981]'
                            }`}
                            title={stop.delivered ? '✓ Delivered · Click to mark pending' : 'Click to confirm delivery'}
                          >
                            {stop.delivered ? (
                              <Check className="w-3.5 h-3.5 stroke-[3.5]" />
                            ) : (
                              <div className="w-2.5 h-2.5 rounded-full border border-[#94A3B8] group-hover:border-[#10B981]" />
                            )}
                          </button>
                          <div className="truncate">
                            <div className="flex items-center gap-1.5 flex-wrap">
                              <span
                                className={`font-semibold ${
                                  stop.delivered ? 'line-through text-[#748078]' : 'text-[#1A2E27]'
                                }`}
                              >
                                #{idx + 1} {stop.name}
                              </span>
                              {isUnlocated && (
                                <button
                                  type="button"
                                  onClick={() => {
                                    setInlineEditingStopId(inlineEditingStopId === stop.id ? null : stop.id);
                                    setInlineAddressText(stop.address);
                                  }}
                                  className="text-[9px] font-bold text-amber-800 bg-amber-100 hover:bg-amber-200 border border-amber-300 px-1.5 py-0.2 rounded shrink-0 cursor-pointer transition flex items-center gap-0.5"
                                  title="Click to edit and locate on map"
                                >
                                  <AlertTriangle className="w-2.5 h-2.5 text-amber-700" />
                                  <span>Not Located · Fix</span>
                                </button>
                              )}
                            </div>
                            <span className="text-[11px] text-[#748078] block truncate">
                              {stop.address}
                            </span>
                          </div>
                        </div>

                        <button
                          type="button"
                          onClick={() =>
                            handleToggleStop(
                              stop.id,
                              stop.delivered,
                              currentSummaryRoute && currentSummaryRoute.id !== draft?.id
                                ? currentSummaryRoute.id
                                : undefined,
                            )
                          }
                          className={`text-[10px] font-bold px-2 py-0.5 rounded-full shrink-0 cursor-pointer transition ${
                            stop.delivered
                              ? 'bg-[#D1FAE5] text-[#065F46] hover:bg-emerald-200'
                              : 'bg-[#E7ECE2] text-[#1E453B] hover:bg-[#DDE3D7]'
                          }`}
                        >
                          {stop.delivered ? '✓ Delivered' : 'Pending'}
                        </button>
                      </div>

                      {/* Inline Address Editor inside Summary Modal */}
                      {inlineEditingStopId === stop.id && (
                        <div className="p-2.5 bg-amber-50 rounded-xl border border-amber-200 flex flex-col gap-1.5 animate-in fade-in duration-150">
                          <span className="text-[10px] font-bold text-amber-950">
                            Edit delivery address:
                          </span>
                          <div className="flex gap-1.5">
                            <input
                              type="text"
                              value={inlineAddressText}
                              onChange={(e) => setInlineAddressText(e.target.value)}
                              className="flex-1 bg-white border border-[#DDE3D7] rounded-lg px-2.5 py-1 text-xs text-[#1A2E27] focus:outline-none focus:ring-1 focus:ring-[#1E453B]"
                              placeholder="e.g. 10 Downing Street, London"
                              autoFocus
                            />
                            <button
                              type="button"
                              disabled={isPlanningRoute}
                              onClick={() => handleSaveEditedAddress(stop, inlineAddressText)}
                              className="px-2.5 py-1 rounded-lg bg-[#1E453B] text-white text-xs font-bold hover:bg-[#152F29] disabled:opacity-50 cursor-pointer shrink-0"
                            >
                              {isPlanningRoute ? 'Locating…' : 'Save & Locate'}
                            </button>
                            <button
                              type="button"
                              onClick={() => setInlineEditingStopId(null)}
                              className="px-2 py-1 rounded-lg border border-[#DDE3D7] text-xs font-semibold text-[#748078] hover:bg-white cursor-pointer shrink-0"
                            >
                              ✕
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Modal Action Buttons: Export Video & Close */}
            <div className="flex gap-2">
              {currentSummaryRoute.stops.filter((s) => s.latitude !== null).length > 1 && (
                <button
                  type="button"
                  onClick={() => handleExportRouteVideo(currentSummaryRoute)}
                  disabled={isExportingVideo}
                  className="flex-1 py-3 rounded-xl bg-[#FAFBF7] border border-[#DDE3D7] hover:bg-white text-[#1E453B] font-bold text-xs flex items-center justify-center gap-1.5 transition shadow-2xs cursor-pointer disabled:opacity-50"
                  title="Export MP4 of the driving route animation"
                >
                  <Video className="w-4 h-4 text-[#1E453B]" />
                  <span>Export Video MP4</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => {
                  setInlineEditingStopId(null);
                  setIsSummaryModalOpen(false);
                }}
                className="flex-1 py-3 rounded-xl bg-[#1E453B] text-white font-bold text-xs hover:bg-[#152F29] transition shadow-xs cursor-pointer"
              >
                Close Summary
              </button>
            </div>
          </div>
        </div>
      )}

      {/* NAVIGATION APP SELECTION MODAL */}
      {navModalStop && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-end sm:items-center justify-center p-0 sm:p-4 animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-md rounded-t-3xl sm:rounded-3xl p-5 shadow-2xl flex flex-col gap-4 animate-in slide-in-from-bottom-6 sm:slide-in-from-bottom-2 duration-200 max-h-[90vh] overflow-y-auto">
            {/* Modal Header */}
            <div className="flex items-start justify-between border-b border-[#F3F5ED] pb-3">
              <div>
                <span className="text-[10px] font-bold tracking-widest text-[#748078] uppercase">
                  CHOOSE NAVIGATION APP
                </span>
                <h3 className="font-extrabold text-lg text-[#1A2E27] leading-tight mt-0.5">
                  Navigate to {navModalStop.name}
                </h3>
                <p className="text-xs text-[#748078] truncate max-w-xs mt-0.5">
                  {navModalStop.address}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setNavModalStop(null)}
                className="p-1.5 rounded-full hover:bg-[#E7ECE2] text-[#748078] cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Geocode warning if unlocated */}
            {navModalStop.latitude === null && (
              <div className="bg-amber-50 border border-amber-200 text-amber-900 text-xs p-2.5 rounded-xl flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 text-amber-700" />
                <span>
                  No GPS coordinates found — opening search in selected map by street address.
                </span>
              </div>
            )}

            {/* Navigation App Options */}
            <div className="flex flex-col gap-2">
              {NAV_APPS.map((app) => (
                <button
                  key={app.id}
                  type="button"
                  onClick={() => handleSelectNavApp(app.id)}
                  className="p-3.5 rounded-2xl border border-[#DDE3D7] hover:border-[#1E453B] bg-white hover:bg-[#FAFBF7] flex items-center justify-between text-left transition shadow-2xs group cursor-pointer"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className="w-10 h-10 rounded-xl flex items-center justify-center text-white font-bold text-sm shadow-xs"
                      style={{ backgroundColor: app.iconBg }}
                    >
                      <Navigation className="w-5 h-5" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-sm text-[#1A2E27] group-hover:text-[#1E453B]">
                          {app.name}
                        </span>
                        <span className="text-[10px] font-bold px-1.5 py-0.2 rounded-full bg-[#E7ECE2] text-[#1E453B]">
                          {app.badge}
                        </span>
                      </div>
                      <span className="text-[11px] text-[#748078] block mt-0.5">
                        {app.desc}
                      </span>
                    </div>
                  </div>
                  <ExternalLink className="w-4 h-4 text-[#748078] group-hover:text-[#1E453B]" />
                </button>
              ))}
            </div>

            {/* Remember Choice Checkbox */}
            <div className="pt-2 border-t border-[#F3F5ED] flex items-center justify-between text-xs text-[#748078]">
              <label className="flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={rememberNavPreference}
                  onChange={(e) => setRememberNavPreference(e.target.checked)}
                  className="rounded border-[#DDE3D7] text-[#1E453B] focus:ring-[#1E453B] w-4 h-4"
                />
                <span>Remember this app as my default</span>
              </label>
              <button
                type="button"
                onClick={() => setNavModalStop(null)}
                className="font-semibold hover:text-[#1A2E27] cursor-pointer"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* VIDEO ANIMATION EXPORT MODAL */}
      {isExportingVideo && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-in fade-in duration-200">
          <div className="bg-[#18382F] text-white w-full max-w-sm rounded-3xl p-6 shadow-2xl flex flex-col items-center text-center gap-4 border border-[#D5EA83]/20 animate-in zoom-in-95 duration-200">
            <div className="relative w-16 h-16 rounded-2xl bg-[#1E453B] border-2 border-[#D5EA83] flex items-center justify-center shadow-lg">
              <span className="text-3xl animate-bounce">🚐</span>
              <div className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-[#10B981] animate-ping" />
            </div>

            <div className="flex flex-col gap-1">
              <span className="text-[10px] font-bold tracking-widest text-[#D5EA83] uppercase">
                EXPORTING ANIMATION
              </span>
              <h3 className="text-lg font-bold">
                Rendering Route MP4
              </h3>
              <p className="text-xs text-[#A4B5AC] leading-relaxed max-w-xs">
                {videoExportStage || 'Capturing map frames and generating route driving video...'}
              </p>
            </div>

            {/* Progress Bar */}
            <div className="w-full flex flex-col gap-1.5">
              <div className="w-full bg-white/10 h-3 rounded-full overflow-hidden p-0.5 border border-white/10">
                <div
                  className="bg-gradient-to-r from-[#D5EA83] to-[#10B981] h-full rounded-full transition-all duration-300"
                  style={{ width: `${Math.max(5, videoExportProgress)}%` }}
                />
              </div>
              <div className="flex items-center justify-between text-[11px] text-[#D5EA83] font-bold px-1">
                <span>Rendering video</span>
                <span>{videoExportProgress}%</span>
              </div>
            </div>

            {videoExportError && (
              <div className="w-full bg-red-950/80 border border-red-500/50 text-red-200 text-xs p-2.5 rounded-xl">
                {videoExportError}
              </div>
            )}

            <p className="text-[10px] text-white/60">
              ⚡ High-definition 720p 30fps animation video download will start automatically once encoding finishes.
            </p>

            <button
              type="button"
              onClick={() => setIsExportingVideo(false)}
              className="py-2 px-5 rounded-xl bg-white/10 hover:bg-white/20 text-xs font-semibold text-white/80 transition cursor-pointer"
            >
              Cancel Export
            </button>
          </div>
        </div>
      )}

      {/* DELETE ROUTE CONFIRMATION MODAL */}
      {routeToDelete && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-200"
          onClick={() => setRouteToDelete(null)}
        >
          <div
            className="bg-white rounded-3xl max-w-sm w-full p-5 shadow-2xl flex flex-col gap-4 border border-[#DDE3D7] text-left animate-in zoom-in-95 duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <div className="w-10 h-10 rounded-2xl bg-red-50 text-[#A94F3E] flex items-center justify-center">
                <Trash2 className="w-5 h-5" />
              </div>
              <button
                type="button"
                onClick={() => setRouteToDelete(null)}
                className="w-8 h-8 rounded-full hover:bg-[#F3F5ED] text-[#748078] flex items-center justify-center transition cursor-pointer"
                title="Cancel"
                aria-label="Close dialog"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div>
              <h3 className="text-lg font-bold text-[#1A2E27]">Delete Old Route?</h3>
              <p className="text-xs text-[#748078] mt-1 leading-relaxed">
                Are you sure you want to delete <strong className="text-[#1A2E27]">"{routeToDelete.name}"</strong>? This will remove this old route from your device.
              </p>
              <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl p-2.5 mt-3 text-xs text-[#53635d] flex flex-col gap-1">
                <div className="font-semibold text-[#1A2E27] truncate">{routeToDelete.name}</div>
                <div className="text-[11px] text-[#748078]">
                  {formatDate(routeToDelete.savedAt || routeToDelete.createdAt, true)} · {routeToDelete.stops.length} stops
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2 pt-2">
              <button
                type="button"
                onClick={() => setRouteToDelete(null)}
                className="flex-1 py-2.5 px-3 rounded-xl border border-[#DDE3D7] text-[#1A2E27] text-xs font-bold hover:bg-[#FAFBF7] transition cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  const name = routeToDelete.name;
                  deleteSavedRoute(routeToDelete.id);
                  if (selectedRoute?.id === routeToDelete.id) {
                    setSelectedRoute(null);
                    setScreen('home');
                  }
                  setRouteToDelete(null);
                  showToast(`Route "${name}" deleted`);
                }}
                className="flex-1 py-2.5 px-3 rounded-xl bg-[#A94F3E] hover:bg-[#8F3C2C] text-white text-xs font-bold transition flex items-center justify-center gap-1.5 shadow-xs cursor-pointer"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Delete Route</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* BOTTOM TAB BAR */}
      {isMainTab && (
        <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-[#DDE3D7] flex items-center justify-around py-2.5 px-4 z-40 max-w-xl mx-auto shadow-md">
          <button
            type="button"
            onClick={() => {
              if (screen === 'route') {
                setScreen('home');
                setMainTab('home');
              } else if (draft && draft.stops.length > 0) {
                setScreen('route');
                setMainTab('home');
              } else {
                navTo('home');
              }
            }}
            className={`flex flex-col items-center gap-1 cursor-pointer transition ${
              mainTab === 'home' || screen === 'home' || screen === 'route' ? 'text-[#1E453B]' : 'text-[#748078]'
            }`}
          >
            <Compass className="w-5 h-5" />
            <span className="text-[10px] font-bold">
              {screen === 'route' ? 'Home' : draft && draft.stops.length > 0 ? 'Active Route' : 'Routes'}
            </span>
          </button>

          <button
            type="button"
            onClick={() => navTo('offline-maps')}
            className={`flex flex-col items-center gap-1 cursor-pointer transition relative ${
              mainTab === 'offline-maps' || screen === 'offline-maps' ? 'text-[#1E453B]' : 'text-[#748078]'
            }`}
          >
            <Download className="w-5 h-5" />
            <span className="text-[10px] font-bold">Offline Maps</span>
            {offlinePackCount > 0 && (
              <span className="absolute top-0 right-3 w-2 h-2 rounded-full bg-[#10B981]" />
            )}
          </button>

          <button
            type="button"
            onClick={() => navTo('history')}
            className={`flex flex-col items-center gap-1 cursor-pointer transition ${
              mainTab === 'history' || screen === 'history' || screen === 'history-route' ? 'text-[#1E453B]' : 'text-[#748078]'
            }`}
          >
            <Archive className="w-5 h-5" />
            <span className="text-[10px] font-bold">Old Routes</span>
          </button>

          <button
            type="button"
            onClick={() => navTo('settings')}
            className={`flex flex-col items-center gap-1 cursor-pointer transition ${
              mainTab === 'settings' || screen === 'settings' ? 'text-[#1E453B]' : 'text-[#748078]'
            }`}
          >
            <Settings className="w-5 h-5" />
            <span className="text-[10px] font-bold">Settings</span>
          </button>
        </nav>
      )}

      {/* Offline Map Tiles Manager Modal */}
      <OfflineMapModal
        isOpen={isOfflineModalOpen}
        onClose={() => setIsOfflineModalOpen(false)}
        stops={activeStops}
      />

      {/* Offline Status Indicator */}
      <OfflineIndicator />
    </div>
  );
}
