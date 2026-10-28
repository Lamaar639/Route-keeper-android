# Driver Tools Suite & Proof of Delivery (POD)

Enhance RouteKeeper with a dedicated on-the-road driver utility toolkit, featuring full Proof of Delivery (photo capture and customer signature canvas), custom delivery dispositions (safe place, neighbor, access issue), audio arrival alerts, and a real-time driver pace dashboard.

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your selection to **keep current import options and focus on driver tools**, this plan prioritizes essential on-road delivery workflow features without adding unnecessary import clutter.

- **Confirmed Direction**: Focus exclusively on driver tools and in-field delivery utilities.
- **Proof of Delivery (POD)**: In-app device camera snapshot + touch signature pad with automatic timestamping and local offline storage.
- **Delivery Dispositions**: Standard courier statuses (*Delivered to Recipient*, *Safe Place / Porch*, *Neighbor*, *Gate Locked / Access Issue*).
- **Driver Audio Arrival Chime**: Optional Web Speech audio chime announcing next stop address upon arrival.
- **Stop Search / Package Scan**: Instant keyword/barcode filter to jump to any package stop instantly.

---

## 1. Overview & Core Concept

### What It Does
Equips delivery and courier drivers with a field-grade suite of delivery tools directly inside the active route screen:
1. **Proof of Delivery (POD) Modal**: Drivers can snap a delivery confirmation photo (package at doorstep), capture a customer's finger signature on an HTML5 canvas, and select delivery disposition notes.
2. **Delivery Dispositions & Stop Notes**: Expanded delivery statuses beyond a simple checkbox, enabling drivers to log where a package was placed or reason for delivery failure.
3. **Driver Pace & Shift HUD**: Compact on-screen metrics showing stops completed per hour, estimated shift finish time, and trip progress.
4. **Quick-Find Bar**: Instant search to look up package tracking numbers, customer names, or door codes in 1 second.
5. **Speech Audio Prompts**: Hands-free spoken arrival prompts ("Approaching Stop 4: 124 Oak Street - Gate Code 4821") via the browser's built-in Web Speech API.

### Target Audience & Persona
Delivery drivers, couriers, and field technicians operating on mobile or dashboard mounts who need fast, tactile tools that minimize friction at every stop.

### Key Value
Transforms RouteKeeper from a pure routing tool into an all-in-one delivery execution system that creates verifiable delivery receipts with zero external subscriptions.

---

## 2. User Experience & Visual Design

### Key User Flows

#### A. Marking a Delivery with Proof of Delivery (POD)
```
[ Stop Row / Map Pin ] ──( Tap Check / Deliver )──► [ Quick POD Action Sheet ]
                                                           │
          ┌────────────────────────────────────────────────┴──────────────────────────────┐
          ▼                                                ▼                              ▼
  [ 1-Tap Quick Mark ]                           [ Take Delivery Photo ]        [ Customer Signature ]
(Delivered to recipient)                           (Doorstep / safe place)        (Touch sign on canvas)
          │                                                │                              │
          └────────────────────────────────────────────────┼──────────────────────────────┘
                                                           ▼
                                            [ Saved to Stop & History ]
                                            (Visible in summary & receipts)
```

1. Driver arrives at stop and taps the completion button.
2. If POD is enabled, a sleek bottom sheet opens with 3 quick options:
   - **Quick Done**: Instant 1-tap completion with default "Handed to recipient".
   - **Photo POD**: Opens the device camera or photo picker to snap the package location.
   - **Signature POD**: Opens a clean touch-friendly signature pad for customer signature.
3. Driver selects disposition (*Front Porch*, *Side Gate*, *Mailroom*, *Neighbor*, *Handed to Customer*).
4. Stop is recorded with timestamp, photo thumbnail, and signature vector.

#### B. Driver Shift Pace & ETA HUD
A clean, minimal strip above the stops list showing:
- **Pace**: Current stops/hour rate (e.g., `18 stops/hr`).
- **Projected Finish**: Calculated ETA based on actual driving and drop-off velocity.
- **Door Code / Gate Badge**: Emphasized whenever the upcoming stop contains a gate/door code in the notes.

#### C. Instant Stop Quick-Finder
A search pill at the top of the stops list allowing the driver to type 2 letters or scan a barcode to immediately jump to and highlight that stop on both the list and the map.

### Visual Identity & Theme
- **Color Palette**: Deep Pine Forest (`#1E453B`), Crisp Mint Accent (`#D5EA83`), Warm Sand (`#FAFBF7`), Clean Border (`#DDE3D7`), Forest Emerald (`#10B981`) for completed deliveries, and Warning Amber (`#D97706`) for access issues.
- **Typography & Hierarchy**: Bold tabular figures for pace and stop counters, clean unboxed metadata with dot separators (`·`), and large tactile 44px+ touch targets for gloved or one-handed driving use.
- **Anti-Slop Restraint**: Zero decorative pills or floating gradient badges; tactile segmented buttons and clean native photo/canvas surfaces.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: HTML5 Canvas Signature Pad
- **Chosen Approach**: Native lightweight HTML5 `<canvas>` with touch and pointer event listeners.
- **Why**: Zero external npm dependencies, instant rendering, offline-friendly, and generates compact PNG data URLs.
- **Alternative Considered**: Heavy 3P signature libraries (rejected to avoid bundle bloat and maintain offline speed).

### Decision 2: Image Compression & Local Storage
- **Chosen Approach**: Compress delivery photos using an in-browser canvas downscaler (max 1024px, JPEG 0.75 quality) before saving into IndexedDB alongside the route record.
- **Why**: High enough quality for clear doorstep proof, but compact enough (50-100KB per image) to store hundreds of stops without exceeding device storage quotas.

### Decision 3: Audio Stop Prompts
- **Chosen Approach**: Browser native `window.speechSynthesis`.
- **Why**: Works 100% offline with zero cloud API keys, speaks customer delivery notes and door codes as driver pulls up to the address. Includes an easy mute toggle in the top bar.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              App Container                             │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌───────────────────────┐  ┌───────────────────────────────────────┐  │
│  │   Active Route View   │  │           Leaflet Map View            │  │
│  │  - Stop List & Pins   │  │  - Layer Switcher (OSM/Sat/Topo)      │  │
│  │  - Quick-Find Search  │  │  - Offline Tile Manager               │  │
│  │  - Driver Pace HUD    │  │  - Stop Location Pins                 │  │
│  └──────────┬────────────┘  └───────────────────────────────────────┘  │
│             │                                                          │
│             ▼                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                     ProofOfDeliveryModal                         │  │
│  │  - Camera Capture Input (<input type="file" capture="environment">) │
│  │  - Touch Signature Canvas (HTML5 Canvas + touchmove)              │  │
│  │  - Disposition Selector (Front door, safe place, neighbor, etc.) │  │
│  │  - Access Code & Delivery Notes Preview                          │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                      IndexedDB Storage Store                     │  │
│  │  - Route Keeper DB -> Stops with POD records (photo, sig, time)  │  │
│  │  - Offline Map Tiles DB                                          │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### Data Model Extensions

```typescript
export interface StopPOD {
  deliveredAt: string; // ISO timestamp
  disposition: 'customer' | 'front-door' | 'safe-place' | 'neighbor' | 'mailroom' | 'access-issue';
  photoDataUrl?: string; // Compressed JPEG thumbnail
  signatureDataUrl?: string; // Customer signature PNG
  driverNote?: string;
  recipientName?: string;
}

// Extended Stop interface with optional POD
export interface Stop {
  // Existing fields...
  id: string;
  name: string;
  address: string;
  latitude: number | null;
  longitude: number | null;
  delivered: boolean;
  notes?: string;
  doorCode?: string;
  phone?: string;
  pod?: StopPOD;
}
```

---

## 5. Execution Steps

1. **Proof of Delivery Component (`src/components/ProofOfDeliveryModal.tsx`)**:
   - Camera photo capture with automatic canvas downscaling.
   - Touch/mouse signature pad with clear, undo, and save.
   - Disposition buttons (*Handed to Customer*, *Porch/Doorstep*, *Safe Place*, *Neighbor*, *Access Issue*).
2. **Driver Pace & Shift HUD (`src/components/DriverPaceBar.tsx`)**:
   - Real-time pace calculation (stops per active hour).
   - Projected completion time based on remaining stops.
   - One-tap audio prompt mute toggle.
3. **Stop Quick-Finder Search Bar**:
   - Live search input at the top of the route list to filter stops by address, name, notes, or door code.
4. **Delivery Receipt Viewer in History & Stop Details**:
   - Allows tapping on any completed stop in route history to inspect photo proof and signature receipt.
5. **Settings Integration**:
   - Preference toggles for "Require POD on delivery", "Enable Voice Arrival Prompts", and "Show Driver Pace HUD".
