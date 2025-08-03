# Child Screen Time - Parent App

A parent control application for managing and monitoring children's screen time usage.

## Overview

This Android application allows parents to monitor and control their children's device usage. It provides features for tracking screen time, managing app access, and maintaining communication between parent and child devices.

## Features

- **Screen Time Monitoring**: Track and monitor child device usage
- **App Management**: Control access to applications on child devices
- **Secure Communication**: Encrypted communication between parent and child devices
- **Real-time Updates**: Receive real-time status updates from child devices
- **Device Management**: Manage multiple child devices from a single parent app

## Technical Details

- **Language**: Java
- **Platform**: Android
- **Minimum SDK**: API 28 (Android 9.0)
- **Target SDK**: API 34 (Android 14)
- **Build System**: Gradle with Kotlin DSL

## Project Structure

```
app/
├── src/main/java/io/github/childscreentime/parent/
│   ├── core/                   # Core functionality and encryption
│   ├── service/               # Background services
│   ├── ui/activities/         # User interface activities
│   └── utils/                 # Utility classes
└── src/main/res/             # Android resources
```

## Dependencies

- AndroidX AppCompat
- Material Design Components
- OkHttp3 for network communication
- WorkManager for background tasks
- Navigation Components

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Sync the project with Gradle
4. Build and run the application

## Building

To build the project:

```bash
./gradlew build
```

To build a release APK:

```bash
./gradlew assembleRelease
```

## Development

This project uses:
- Android Studio for development
- Gradle Kotlin DSL for build configuration
- Java 8 language features
- ViewBinding for UI interactions

## License

This project is part of the Child Screen Time control system.

## Contributing

Please ensure that any contributions maintain the security and privacy standards required for parental control applications.
