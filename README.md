# WorkItOut - Cycling Training App

A native Android app for connecting to cycling sensors and performing structured workouts.

## Features

### Phase 1 (Current)
- ✅ Connect to Heart Rate Monitors via Bluetooth LE
- ✅ Connect to Power Meters via Bluetooth LE
- ✅ Real-time display of:
  - Heart Rate (bpm)
  - Power (watts)
  - Cadence (rpm)

### Phase 2 (Planned)
- Custom workout creation
- Import workouts from files/TrainingPeaks
- Smart trainer control (resistance)
- Workout execution with interval tracking
- Graphical workout display

## Technology Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM
- **Bluetooth**: Android BLE APIs
- **Async**: Kotlin Coroutines & Flow
- **Navigation**: Navigation Compose

## BLE Device Support

The app supports standard Bluetooth Low Energy cycling sensors:

### Heart Rate Monitors
- **Service UUID**: `0x180D` (Heart Rate Service)
- **Characteristic**: `0x2A37` (Heart Rate Measurement)

### Power Meters
- **Service UUID**: `0x1818` (Cycling Power Service)
- **Characteristic**: `0x2A63` (Cycling Power Measurement)
- Provides: Power (watts) and Cadence (rpm)

### Smart Trainers (Future)
- **Service UUID**: `0x1826` (Fitness Machine Service)
- For resistance control

## Requirements

- Android 8.0 (API 26) or higher
- Bluetooth LE capable device
- Compatible cycling sensors (ANT+ sensors require ANT+ USB adapter)

## Permissions

The app requires the following permissions:
- **Bluetooth**: For BLE connectivity
- **Location** (Android 10-11): Required for BLE scanning
- **Bluetooth Scan/Connect** (Android 12+): New granular BLE permissions

## Project Structure

```
app/src/main/java/com/cycling/workitout/
├── ble/
│   ├── BleConstants.kt         # BLE service/characteristic UUIDs
│   └── BleManager.kt            # Central BLE connection handler
├── data/
│   ├── BleDevice.kt             # Device models
│   └── SensorData.kt            # Sensor data models
├── ui/
│   ├── connection/
│   │   ├── ConnectionScreen.kt
│   │   └── ConnectionViewModel.kt
│   ├── livedata/
│   │   ├── LiveDataScreen.kt
│   │   └── LiveDataViewModel.kt
│   ├── navigation/
│   │   └── Navigation.kt
│   └── theme/
│       ├── Theme.kt
│       └── Type.kt
└── MainActivity.kt
```

## Building

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Run on device or emulator (BLE testing requires real device)

## Usage

1. **Connection Screen**:
   - Tap "Scan for Devices" to discover nearby sensors
   - Tap on a discovered device to connect
   - Connected devices show in the status cards at the top

2. **Live Data Screen**:
   - Navigate via the play button (enabled when devices connected)
   - View real-time metrics from connected sensors
   - Use back button to return to connection screen

## Development Roadmap

- [x] Phase 1: Basic BLE connectivity and data display
- [ ] Phase 2: Workout creation and execution
- [ ] Phase 3: Smart trainer control
- [ ] Phase 4: Workout history and analysis
- [ ] Phase 5: iOS version

## License

MIT License - See LICENSE file for details

## Contributing

Contributions welcome! This is a personal project to celebrate cycling. 🚴‍♂️
