# RouteKeeper - Android Delivery Route Planner & Driver Tools Suite

RouteKeeper is a native Android application built with **Kotlin** and **Jetpack Compose**, designed for delivery drivers and couriers to plan multi-stop delivery routes, track stops, and capture Proof of Delivery (POD) on the road with zero external subscriptions.

## Features

- **Delivery Manifest Import & Parser**:
  - Automatically parses delivery notes, customer names, phone numbers, UK addresses, postcodes, and delivery time slots.
  - Built-in Central Metro sample manifest for instant one-tap testing.
- **Pre-Route Tuning & Optimization**:
  - Depot departure time configuration.
  - Average stop service dwell time slider (5 to 8 mins, default 6.5m).
  - Quiet layby buffer rest stops (1 to 4 laybys) placed strategically before time windows.
  - 2-Opt TSP optimization solving the shortest road sequence.
- **Driver Shift Pace & ETA HUD**:
  - Live stops-per-hour velocity meter (e.g. 18 stops/hr).
  - Real-time projected shift completion time based on travel speed and stop dwell durations.
  - Quick-Finder search bar to immediately jump to any package by customer name, address, or order ID.
- **Interactive Route Map & List Views**:
  - Custom Compose Canvas map rendering waypoints, road lines, and numbered drop pins.
  - Alternative route modes: Fastest Flow, Shortest Path, and Arterial Dual-Carriageway routes.
- **Proof of Delivery (POD) & Dispositions**:
  - In-app customer touch signature pad on canvas.
  - Doorstep photo confirmation.
  - Delivery disposition logging: *Handed to Recipient*, *Front Porch*, *Safe Place*, *Neighbor*, and *Gate Locked / Access Issue*.
- **Native Android Intents**:
  - 1-tap phone dialing (`tel:`) to call recipients.
  - 1-tap turn-by-turn navigation launch into Google Maps, Waze, or OpenStreetMap.
- **Route History**:
  - Complete log of past delivery runs with completion percentages, dates, and total odometer distances.

## Architecture

- **UI**: Jetpack Compose, Material 3 Design Tokens, Edge-to-Edge display with `enableEdgeToEdge()`.
- **Architecture**: MVVM with `RouteViewModel`, `RouteRepository`, and Kotlin Coroutines / StateFlow.
- **Build System**: Gradle Kotlin DSL (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`).
- **Target SDK**: Android SDK 36 (Android 15), Min SDK 26.
