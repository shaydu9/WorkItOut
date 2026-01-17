# WorkItOut - Architecture Overview

## 🏗️ Architecture Pattern: MVVM (Model-View-ViewModel)

This app follows the **MVVM architecture** pattern recommended by Google for Android apps, with a clean separation of concerns.

## 📊 Architecture Layers

```
┌─────────────────────────────────────────────┐
│            UI Layer (Compose)                │
│  ┌────────────┐         ┌────────────┐     │
│  │ Connection │         │  LiveData  │     │
│  │   Screen   │────────▶│   Screen   │     │
│  └────────────┘         └────────────┘     │
└──────────────┬───────────────┬──────────────┘
               │               │
┌──────────────▼───────────────▼──────────────┐
│          ViewModel Layer                     │
│  ┌──────────────┐    ┌──────────────┐      │
│  │  Connection  │    │   LiveData   │      │
│  │  ViewModel   │    │  ViewModel   │      │
│  └──────────────┘    └──────────────┘      │
└──────────────┬───────────────┬──────────────┘
               │               │
               └───────┬───────┘
┌──────────────────────▼──────────────────────┐
│          BLE Service Layer                   │
│         ┌────────────────┐                   │
│         │   BleManager   │                   │
│         └────────────────┘                   │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│       Android BLE APIs                       │
│    BluetoothLeScanner, BluetoothGatt        │
└─────────────────────────────────────────────┘
```

## 🔧 Component Breakdown

### 1. **UI Layer** (`ui/`)

#### Screens
- **ConnectionScreen**: Device scanning, connection management
- **LiveDataScreen**: Real-time metrics display

#### ViewModels
- **ConnectionViewModel**: Manages device discovery and connection state
- **LiveDataViewModel**: Aggregates sensor data for display

#### Navigation
- **Navigation.kt**: Jetpack Navigation Compose setup

#### Theme
- **Material 3** design system with dynamic colors

### 2. **BLE Service Layer** (`ble/`)

#### BleManager
The central hub for all Bluetooth operations:

**Responsibilities:**
- Device scanning with service filters
- GATT connection management
- Characteristic notification subscriptions
- Data parsing and publishing

**Key Features:**
- Uses **Kotlin StateFlow** for reactive data streams
- Handles both Heart Rate and Power Meter simultaneously
- Automatic cleanup on disconnect
- Thread-safe with Coroutines

#### BleConstants
- Standard BLE Service UUIDs (Heart Rate, Cycling Power, FTMS)
- Characteristic UUIDs
- Configuration constants

### 3. **Data Layer** (`data/`)

#### Models
- **BleDevice**: Represents discovered BLE devices
- **DeviceType**: Enum for device categories
- **HeartRateData**: HR measurements with timestamp
- **PowerData**: Power and cadence measurements
- **LiveMetrics**: Aggregated metrics for UI display

## 🔄 Data Flow

### Connection Flow
```
User taps "Scan"
    ↓
ConnectionViewModel.startScan()
    ↓
BleManager.startScan()
    ↓
Android BLE Scanner discovers devices
    ↓
ScanCallback adds to discoveredDevices StateFlow
    ↓
ConnectionScreen observes and displays devices
    ↓
User taps device
    ↓
ConnectionViewModel.connectDevice()
    ↓
BleManager.connectHeartRateMonitor() or connectPowerMeter()
    ↓
GATT connection established
    ↓
Services discovered
    ↓
Enable characteristic notifications
    ↓
Data starts flowing...
```

### Real-time Data Flow
```
Sensor sends BLE notification
    ↓
GattCallback.onCharacteristicChanged()
    ↓
Parse data (parseHeartRate or parsePowerMeasurement)
    ↓
Update StateFlow (_heartRateData or _powerData)
    ↓
LiveDataViewModel combines flows
    ↓
LiveDataScreen observes and recomposes
    ↓
UI updates with new values
```

## 🧩 Key Technologies

### Jetpack Compose
- **Declarative UI**: UI is a function of state
- **State hoisting**: ViewModels hold state, Screens are stateless
- **Recomposition**: UI automatically updates when StateFlows emit

### Kotlin Coroutines & Flow
- **StateFlow**: Hot stream for reactive state management
- **Coroutines**: Async/await pattern for BLE operations
- **viewModelScope**: Automatic cancellation on ViewModel cleared

### Android BLE APIs
- **BluetoothLeScanner**: Low-energy device discovery
- **BluetoothGatt**: Generic Attribute Profile for data exchange
- **GattCallback**: Asynchronous BLE event handling

## 📱 Bluetooth Low Energy Concepts

### GATT Profile Hierarchy
```
Device (e.g., Heart Rate Monitor)
  └─ Service (Heart Rate Service: 0x180D)
      └─ Characteristic (HR Measurement: 0x2A37)
          └─ Descriptor (Client Config: 0x2902)
```

### Standard Cycling Services

#### Heart Rate Service (0x180D)
- **Characteristic**: Heart Rate Measurement (0x2A37)
- **Data**: Heart rate in BPM
- **Frequency**: Typically 1 Hz (every second)

#### Cycling Power Service (0x1818)
- **Characteristic**: Cycling Power Measurement (0x2A63)
- **Data**: 
  - Instantaneous power (watts)
  - Cadence (rpm) - optional field
- **Frequency**: Typically 1-4 Hz

#### Fitness Machine Service (0x1826) - Future
- For smart trainer control
- Can set resistance/target power

## 🎨 UI/UX Design Principles

### Connection Screen
- **Scan button**: Primary action, clear state (scanning/idle)
- **Status cards**: Always visible connection state at top
- **Device list**: Grouped by type, shows signal strength
- **Permissions**: Graceful handling with explanatory messages

### Live Data Screen
- **Large metrics**: Easy to read while cycling
- **Color coding**: Red for HR, Blue for Power, Green for Cadence
- **Connection indicators**: Gray out disconnected sensors
- **Minimal navigation**: Single back button

## 🔐 Permission Handling

### Android Versions Support
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    // Android 12+: Granular BLE permissions
    BLUETOOTH_SCAN
    BLUETOOTH_CONNECT
} else {
    // Android 11 and below: Legacy permissions
    BLUETOOTH
    BLUETOOTH_ADMIN
    ACCESS_FINE_LOCATION // Required for BLE scanning
}
```

### Runtime Permission Flow
1. Check if permissions granted
2. Request permissions on first launch
3. Show explanatory message if denied
4. Disable scan button until granted

## 🚀 Future Enhancements (Phase 2+)

### Workout Engine
```
WorkoutManager
  └─ WorkoutExecutor
      ├─ IntervalTimer
      ├─ TargetPowerController
      └─ WorkoutRecorder
```

### Smart Trainer Control
```
TrainerService
  └─ FTMSController
      ├─ setTargetPower(watts)
      ├─ setResistance(level)
      └─ setSimulationMode(grade, crr)
```

### Data Persistence
```
Room Database
  ├─ WorkoutEntity
  ├─ ActivityEntity
  └─ MetricsEntity
```

### File Import/Export
- **.zwo** (Zwift Workout)
- **.mrc** (TrainerRoad)
- **TrainingPeaks API** integration

## 📈 Performance Considerations

### BLE Best Practices
1. **Scan timeout**: 10 seconds max to save battery
2. **Connection limit**: Max 2-3 simultaneous GATT connections
3. **Notification rate**: 1-4 Hz is sufficient for cycling metrics
4. **Background restrictions**: No background scanning (Android 8+)

### Compose Optimization
1. **StateFlow**: Only recomposes when values actually change
2. **remember**: Caches values across recompositions
3. **LazyColumn**: Efficient list rendering
4. **collectAsStateWithLifecycle**: Stops collecting when screen not visible

## 🐛 Debugging Tips

### Logcat Filters
```
BleManager        - All BLE operations
ConnectionState   - Device connection changes
CharacteristicChanged - Data notifications
```

### Common Issues
1. **Connection timeout**: Usually device already paired elsewhere
2. **No notifications**: Check descriptor was written correctly
3. **Wrong data parsing**: Verify byte order (little-endian for BLE)
4. **Permissions denied**: Check Android version-specific permissions

## 📚 Standards & References

- [Bluetooth SIG Specifications](https://www.bluetooth.com/specifications/)
- [Heart Rate Service Spec](https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/)
- [Cycling Power Service Spec](https://www.bluetooth.com/specifications/specs/cycling-power-service-1-1/)
- [Android BLE Guide](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)

---

**Built with ❤️ for cycling enthusiasts**
