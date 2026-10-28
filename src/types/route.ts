export interface Coordinate {
  latitude: number;
  longitude: number;
}

export type GeocodeStatus = 'pending' | 'located' | 'not-found';
export type RoutingMode = 'not-planned' | 'road-network' | 'approximate';

export interface RouteStep {
  instruction: string;
  distanceMeters: number;
  durationSeconds: number;
  name?: string;
}

export interface DeliveryTimeWindow {
  startTime: string; // e.g. "09:00"
  endTime: string;   // e.g. "10:00" or "13:00"
  durationHours: 1 | 4 | 'custom';
  label?: string;    // e.g. "Morning Slot (1h)"
}

export interface QuietWaitStop {
  id: string;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  trafficProfile: 'quiet-side-street' | 'designated-layby' | 'park-and-wait';
  arrivingFromStopIndex: number;
  nextStopIndex: number;
  suggestedArrivalTime: string; // e.g. "13:10"
  departureTime: string;        // e.g. "13:48"
  waitDurationMinutes: number;  // e.g. 38
  arterialAccessMinutes: number; // e.g. 2 min quick arterial access
  reason: string;
}

export interface DeliveryStop {
  id: string;
  name: string;
  phone: string;
  address: string;
  orderId?: string;
  sourceSequence?: number;
  dropNumber?: number | string; // delivery/drop number from dispatch paperwork
  latitude: number | null;
  longitude: number | null;
  geocodeStatus: GeocodeStatus;
  geocodeError?: string;
  geocodedBy?: string; // e.g. "OpenStreetMap (Nominatim)", "Photon (Fuzzy OSM)", "Postcodes.io"
  delivered: boolean;
  deliveredAt?: string;
  // Delivery Time Slot & Service Dwell Time (5-8 mins)
  timeWindow?: DeliveryTimeWindow;
  serviceTimeMinutes?: number; // default 6.5 (5-8 mins)
  estimatedArrival?: string;   // e.g. "09:22"
  estimatedDeparture?: string; // e.g. "09:29"
  windowStatus?: 'on-time' | 'early' | 'late' | 'flexible';
}

export interface AlternativeRouteOption {
  id: 'fastest' | 'shortest' | 'alternative';
  title: string;
  badge: string;
  description: string;
  stops: DeliveryStop[];
  path: Coordinate[];
  distanceMeters: number;
  durationSeconds: number;
  waitStops?: QuietWaitStop[];
  steps?: RouteStep[];
}

export interface SolverStepSnapshot {
  iteration: number;
  path: Coordinate[];
  distanceMeters: number;
  description: string;
  improved: boolean;
}

export interface RouteDraft {
  id: string;
  name: string;
  sourceName: string;
  createdAt: string;
  stops: DeliveryStop[];
  path: Coordinate[];
  routingMode: RoutingMode;
  distanceMeters?: number;
  durationSeconds?: number;
  steps?: RouteStep[];
  // Time slots, quiet wait stops & alternative routes
  departureStartTime?: string; // e.g. "09:00"
  defaultDwellMinutes?: number; // 5-8 mins (default 6.5)
  restStopCount?: number; // 1 to 4 rest stops (default 2)
  waitStops?: QuietWaitStop[];
  alternativeRoutes?: AlternativeRouteOption[];
  selectedAlternativeId?: 'fastest' | 'shortest' | 'alternative';
}

export interface SavedRoute extends RouteDraft {
  savedAt: string;
}

export interface GeocodeCache {
  [normalizedAddress: string]: Coordinate;
}

export type NavAppChoice = 'ask' | 'google' | 'apple' | 'waze' | 'citymapper' | 'osm';
export type MapLayerChoice = 'osm' | 'satellite' | 'topo';
export type VehicleAvatarChoice = 'van' | 'car' | 'arrow';

export interface TurnManeuver {
  type: 'turn-left' | 'turn-right' | 'straight' | 'roundabout' | 'arrive';
  instruction: string;
  streetName?: string;
  distanceMeters: number;
}

export interface MapPreferences {
  markerColor: string;
  lineColor: string;
  lineWidth: number;
  showAllTimeMap: boolean;
  defaultNavApp?: NavAppChoice;
  defaultDwellMinutes?: number;
  mapLayer?: MapLayerChoice;
  vehicleAvatar?: VehicleAvatarChoice;
  defaultRestStopCount?: number;
}
