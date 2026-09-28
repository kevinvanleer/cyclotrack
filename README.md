# Cyclotrack

Cyclotrack is an Android cycling app for recording rides, reviewing ride history, and analyzing ride data. The app stores ride data locally in a Room database, supports Bluetooth Low Energy bike sensors, and includes optional integrations for Google Fit and Strava.

This repository contains:

- `app/`: the Android application
- `www/`: a small React site used for the app website and privacy policy

## What The App Does

Cyclotrack combines a live, in-ride dashboard with post-ride analysis and export features.

- Record rides with GPS in a foreground service.
- Show a live ride dashboard while a trip is in progress.
- Save ride history locally and review ride details later.
- Track bikes and associate rides with equipment.
- Link BLE heart rate, cadence, and speed sensors.
- Optionally auto-pause and auto-resume rides.
- Export rides to `XLSX` and `FIT`.
- Sync rides to Google Fit and Strava.
- Pull biometric inputs from Google Fit for calorie estimation when enabled.
- Search ride history by distance, speed, date, and text fields.

## Repository Layout

- `app/src/main/java/com/kvl/cyclotrack/`: app source
- `app/src/main/res/`: layouts, navigation graphs, strings, preferences, icons
- `app/src/test/`: JVM unit tests
- `app/src/androidTest/`: instrumentation and UI tests
- `app/schemas/`: exported Room schema snapshots and migration history
- `www/`: React website project

## High-Level Architecture

The Android app is a single-module Kotlin application built around standard Jetpack components.

- UI: Activities, Fragments, XML layouts, data binding
- State: `ViewModel`, `LiveData`
- Navigation: Jetpack Navigation
- Background work: `WorkManager`
- DI: Hilt
- Persistence: Room
- Networking/serialization: OkHttp and Moshi
- Maps/location: Google Maps and Play Services Location
- External services: Google Fit, Strava, Firebase Analytics, Firebase Crashlytics

Key flows in the app:

- `MainActivity`: hosts the main app surface, including analytics and ride summaries.
- `DashboardActivity`: hosts the in-ride dashboard.
- `TripInProgressService`: records trip progress in the foreground.
- `TripDetailsActivity`: shows ride detail, charts, sync actions, and export actions.
- `PreferencesActivity`: settings, integrations, bikes, sensors, autopause, and advanced options.

## Data Model

Cyclotrack persists ride data in a Room database currently at schema version `28`.

Main entities include:

- `Trip`
- `Measurements`
- `TimeState`
- `Split`
- `Bike`
- `ExternalSensor`
- `Weather`
- `HeartRateMeasurement`
- `CadenceSpeedMeasurement`
- `OnboardSensors`
- `Export`

If you change the data model, also update the Room schema exports under `app/schemas/`.

## Ride Search

Ride history search is more capable than a basic text filter. The parser supports:

- numeric shorthand like `20 miles`, `15 km`, `18 mph`
- date queries like `2024-05-05`, `April 2024`, `2024`
- comparison operators like `is`, `less than`, `greater than`, `before`, `after`, `between`
- compound expressions with `and`, `or`, and `not`
- text queries across title and notes

Examples:

```text
20 miles
15 km
distance greater than 10
speed between 15 and 17
2024-05-05
April 2024
date between 2023-10-01 and 2024-02-05
title contains "flat tire"
description contains "pain"
distance greater than 20 and speed is 18
not title contains "commute"
```

## Android Development Setup

### Prerequisites

- Android Studio with Android SDK `36`
- JDK `17`
- `JAVA_HOME` pointing at that JDK when using the Gradle wrapper directly

The project configures:

- `compileSdk 36`
- `targetSdkVersion 36`
- `minSdkVersion 26`
- Kotlin JVM toolchain `17`

### Important Local Files

#### `keystore.properties`

This file is required during Gradle configuration because `app/build.gradle` loads it unconditionally, even if you only intend to build `debug` or `dev`.

Create `keystore.properties` in the repository root:

```properties
storeFile=/absolute/path/to/keystore.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

For local-only development, pointing this at the default Android debug keystore is usually enough:

```properties
storeFile=/home/your-user/.android/debug.keystore
storePassword=android
keyAlias=androiddebugkey
keyPassword=android
```

#### `secure.properties`

This file is optional. If it is missing, the build falls back to empty strings for these values:

- `MAPS_API_KEY`
- `OPENWEATHER_DEV`
- `OPENWEATHER_PROD`
- `STRAVA_CLIENT_ID`
- `STRAVA_CLIENT_SECRET`

Example:

```properties
MAPS_API_KEY=
OPENWEATHER_DEV=
OPENWEATHER_PROD=
STRAVA_CLIENT_ID=
STRAVA_CLIENT_SECRET=
```

`app/google-services.json` is already present in this repository. If you use your own Firebase project, replace it with your own configuration.

## Build Variants

The app defines four build types:

- `debug`: debuggable, application id suffix `.debug`
- `dev`: debuggable, application id suffix `.dev`
- `release`: signed with `keystore.properties`
- `prod`: minified production build signed with `keystore.properties`

The codebase also uses `FeatureFlags` to treat `debug` and `dev` as development builds.

## Common Gradle Commands

From the repository root:

```bash
./gradlew assembleDebug
./gradlew assembleDev
./gradlew installDebug
./gradlew installDev
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

If Gradle fails immediately with a Java error, verify that `java` is installed and `JAVA_HOME` is set.

## Testing

There are both JVM and Android instrumentation tests in this repo.

- Unit tests live in `app/src/test/`
- Instrumentation tests live in `app/src/androidTest/`
- Sample ride files used in tests live in `app/src/test/resources/ride-data/`

Examples of covered areas:

- ride export helpers
- statistics and unit conversion
- calorie estimation helpers
- geometry and trip interval calculations
- search parsing and filtering
- trip summary and dashboard behavior

## Integrations And External Services

The app contains optional integrations with:

- Google Fit for activity sync and biometric inputs
- Strava for activity upload
- Google Maps for trip maps
- OpenWeather for ride weather snapshots
- Firebase Analytics and Crashlytics

Many of these features depend on local credentials or account sign-in.

## Website Subproject

The `www/` directory is a separate React application for the Cyclotrack website.

Typical commands:

```bash
cd www
yarn install
yarn start
yarn build
yarn test
```

The site also contains a deploy script:

```bash
yarn deploy
```

## Contributor Notes

- Room schemas are exported to `app/schemas/`.
- The project uses a checked-in Gradle wrapper.
- The Android app currently uses XML layouts and data binding rather than Jetpack Compose.
- The build script injects a Git hash into `BuildConfig.GIT_HASH`.
- Sync, export, delete, and cleanup operations are largely implemented as `WorkManager` jobs.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
