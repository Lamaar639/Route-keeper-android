import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { DEFAULT_MAP_PREFERENCES } from '../constants/mapStyles';
import type {
  DeliveryStop,
  GeocodeCache,
  MapPreferences,
  RouteDraft,
  SavedRoute,
} from '../types/route';

const STORAGE_KEY = 'routekeeper:local-data:v1';

interface StoredRouteData {
  savedRoutes: SavedRoute[];
  draft: RouteDraft | null;
  geocodeCache: GeocodeCache;
  preferences: MapPreferences;
}

const EMPTY_DATA: StoredRouteData = {
  savedRoutes: [],
  draft: null,
  geocodeCache: {},
  preferences: DEFAULT_MAP_PREFERENCES,
};

interface RouteDataContextValue extends StoredRouteData {
  isReady: boolean;
  storageError: string | null;
  setDraft: (stops: DeliveryStop[], sourceName: string, name?: string) => void;
  updateDraft: (updates: Partial<RouteDraft>) => void;
  updateDraftStop: (id: string, updates: Partial<DeliveryStop>) => void;
  updateSavedRouteStop: (routeId: string, stopId: string, updates: Partial<DeliveryStop>) => void;
  updateSavedRoute: (routeId: string, updates: Partial<SavedRoute>) => void;
  toggleStopDelivered: (stopId: string, delivered: boolean, routeId?: string) => void;
  setGeocodeCache: (cache: GeocodeCache) => void;
  updatePreferences: (updates: Partial<MapPreferences>) => void;
  saveDraft: () => SavedRoute | null;
  clearDraft: () => void;
  deleteSavedRoute: (id: string) => void;
}

const RouteDataContext = createContext<RouteDataContextValue | null>(null);

function makeId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function RouteDataProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<StoredRouteData>(EMPTY_DATA);
  const [isReady, setIsReady] = useState(false);
  const [canPersist, setCanPersist] = useState(false);
  const [storageError, setStorageError] = useState<string | null>(null);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const parsed = JSON.parse(saved) as Partial<StoredRouteData>;
        setData({
          ...EMPTY_DATA,
          ...parsed,
          preferences: {
            ...DEFAULT_MAP_PREFERENCES,
            ...parsed.preferences,
          },
        });
      }
      setCanPersist(true);
    } catch {
      setStorageError('Saved routes could not be loaded. New work will stay available for this session.');
    } finally {
      setIsReady(true);
    }
  }, []);

  useEffect(() => {
    if (!isReady || !canPersist) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
      setStorageError(null);
    } catch {
      setStorageError('Your latest changes could not be saved to local storage.');
    }
  }, [data, isReady, canPersist]);

  const setDraft = useCallback(
    (stops: DeliveryStop[], sourceName: string, name?: string) => {
      setData((current) => ({
        ...current,
        draft: {
          id: makeId(),
          name: name?.trim() || `Route · ${new Date().toLocaleDateString('en-GB')}`,
          sourceName,
          createdAt: new Date().toISOString(),
          stops,
          path: [],
          routingMode: 'not-planned',
        },
      }));
    },
    [],
  );

  const updateDraft = useCallback((updates: Partial<RouteDraft>) => {
    setData((current) =>
      current.draft
        ? { ...current, draft: { ...current.draft, ...updates } }
        : current,
    );
  }, []);

  const updateDraftStop = useCallback((id: string, updates: Partial<DeliveryStop>) => {
    setData((current) => {
      if (!current.draft) return current;
      return {
        ...current,
        draft: {
          ...current.draft,
          stops: current.draft.stops.map((stop) =>
            stop.id === id ? { ...stop, ...updates } : stop,
          ),
        },
      };
    });
  }, []);

  const updateSavedRouteStop = useCallback(
    (routeId: string, stopId: string, updates: Partial<DeliveryStop>) => {
      setData((current) => ({
        ...current,
        savedRoutes: current.savedRoutes.map((route) => {
          if (route.id !== routeId) return route;
          return {
            ...route,
            stops: route.stops.map((stop) =>
              stop.id === stopId ? { ...stop, ...updates } : stop,
            ),
          };
        }),
      }));
    },
    [],
  );

  const updateSavedRoute = useCallback(
    (routeId: string, updates: Partial<SavedRoute>) => {
      setData((current) => ({
        ...current,
        savedRoutes: current.savedRoutes.map((route) => {
          if (route.id !== routeId) return route;
          return {
            ...route,
            ...updates,
          };
        }),
      }));
    },
    [],
  );

  const toggleStopDelivered = useCallback(
    (stopId: string, delivered: boolean, routeId?: string) => {
      const deliveredAt = delivered ? new Date().toISOString() : undefined;
      const updates = { delivered, deliveredAt };

      setData((current) => {
        let newDraft = current.draft;
        if (newDraft && newDraft.stops.some((s) => s.id === stopId)) {
          newDraft = {
            ...newDraft,
            stops: newDraft.stops.map((s) => (s.id === stopId ? { ...s, ...updates } : s)),
          };
        }

        const newSaved = current.savedRoutes.map((route) => {
          if (routeId && route.id !== routeId) return route;
          if (!route.stops.some((s) => s.id === stopId)) return route;
          return {
            ...route,
            stops: route.stops.map((s) => (s.id === stopId ? { ...s, ...updates } : s)),
          };
        });

        return {
          ...current,
          draft: newDraft,
          savedRoutes: newSaved,
        };
      });
    },
    [],
  );

  const setGeocodeCache = useCallback((cache: GeocodeCache) => {
    setData((current) => ({
      ...current,
      geocodeCache: { ...current.geocodeCache, ...cache },
    }));
  }, []);

  const updatePreferences = useCallback((updates: Partial<MapPreferences>) => {
    setData((current) => ({
      ...current,
      preferences: { ...current.preferences, ...updates },
    }));
  }, []);

  const saveDraft = useCallback((): SavedRoute | null => {
    const draft = data.draft;
    if (!draft || draft.stops.length === 0) return null;
    const savedRoute: SavedRoute = {
      ...draft,
      savedAt: new Date().toISOString(),
    };
    setData((current) => {
      const savedRoutes = current.savedRoutes.some((route) => route.id === savedRoute.id)
        ? current.savedRoutes.map((route) =>
            route.id === savedRoute.id ? savedRoute : route,
          )
        : [savedRoute, ...current.savedRoutes];
      return { ...current, savedRoutes };
    });
    return savedRoute;
  }, [data.draft]);

  const clearDraft = useCallback(() => {
    setData((current) => ({ ...current, draft: null }));
  }, []);

  const deleteSavedRoute = useCallback((id: string) => {
    setData((current) => ({
      ...current,
      savedRoutes: current.savedRoutes.filter((r) => r.id !== id),
    }));
  }, []);

  const value = useMemo(
    () => ({
      ...data,
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
    }),
    [
      data,
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
    ],
  );

  return <RouteDataContext.Provider value={value}>{children}</RouteDataContext.Provider>;
}

export function useRouteData(): RouteDataContextValue {
  const context = useContext(RouteDataContext);
  if (!context) throw new Error('useRouteData must be used inside RouteDataProvider.');
  return context;
}
