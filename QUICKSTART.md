# Quick Start Guide - WorkItOut

## 🚀 Getting Started

### 1. Open in Android Studio
- Open Android Studio
- Select "Open an Existing Project"
- Navigate to the `WorkItOut` folder
- Wait for Gradle sync to complete

### 2. Build the Project
```bash
./gradlew build
```

### 3. Run on Device
- Connect your Android device via USB (BLE testing requires a real device, not emulator)
- Enable Developer Options and USB Debugging on your device
- Click "Run" in Android Studio or use:
```bash
./gradlew installDebug
```

## 📱 Testing the App

### Before Testing
1. **Enable Bluetooth** on your Android device
2. Make sure your **Heart Rate Monitor** and/or **Power Meter** are:
   - Turned on
   - Not connected to other devices
   - In pairing mode (if required)

### Testing Steps

#### Screen 1: Connection Screen
1. Launch the app
2. Grant Bluetooth permissions when prompted
3. Tap "**Scan for Devices**"
4. Wait for your devices to appear in the list
5. Tap on a device to connect
6. Connected devices will show at the top with a green indicator
7. Tap the **Play button** (▶️) in the top-right to go to Live Data

#### Screen 2: Live Data Screen
1. View real-time metrics from your connected sensors:
   - **Heart Rate** (bpm) - from HR monitor
   - **Power** (watts) - from power meter
   - **Cadence** (rpm) - from power meter
2. Data updates in real-time as you ride/move
3. Tap the **Back button** (←) to return to Connection Screen

## 🔧 Troubleshooting

### Devices Not Appearing?
- Ensure Bluetooth is enabled
- Check that Location permission is granted (required for BLE on Android 10-11)
- Make sure device is not connected to another app (Zwift, TrainerRoad, etc.)
- Try turning the sensor off and on again

### Connection Failed?
- Move closer to the sensor (within 10 meters)
- Restart the sensor
- Try disconnecting and reconnecting

### No Data Showing?
- Check if the sensor is actually sending data (try moving/pedaling)
- Disconnect and reconnect the device
- Check battery level on the sensor

## 📝 Project Structure

```
app/src/main/java/com/cycling/workitout/
├── ble/                          # Bluetooth Low Energy logic
│   ├── BleConstants.kt           # Service/Characteristic UUIDs
│   └── BleManager.kt             # Central BLE handler
├── data/                         # Data models
│   ├── BleDevice.kt              # Device representation
│   └── SensorData.kt             # Sensor data models
├── ui/                           # User Interface
│   ├── connection/               # Connection Screen
│   ├── livedata/                 # Live Data Screen
│   ├── navigation/               # Navigation setup
│   └── theme/                    # Material 3 theming
└── MainActivity.kt               # Entry point
```

## 🎯 Next Steps (Phase 2)

Once you've tested the basic connectivity and live data display, you can start building:
1. **Workout Builder** - Create custom intervals
2. **Workout Execution** - Follow workouts in real-time
3. **Smart Trainer Control** - Adjust resistance via FTMS
4. **Import/Export** - Support .zwo, .mrc, TrainingPeaks files

## 💡 Tips for Development

### BLE Debugging
- Use Android Studio's Logcat with filter: `BleManager`
- Look for connection states and characteristic notifications
- Check UUID values match your device specs

### Testing Without Physical Sensors
- Use a BLE simulator app like "nRF Connect" to create virtual sensors
- Or use another phone running a BLE peripheral simulator

### Common BLE Issues
1. **Android 12+ Permissions**: Need BLUETOOTH_SCAN and BLUETOOTH_CONNECT
2. **Location Permission**: Still required on Android 10-11 for BLE scanning
3. **Background Scanning**: Not implemented yet (will drain battery)
4. **Multiple Connections**: Keep GATT connections to minimum (currently 2)

## 🔐 Permissions Explained

| Permission | Android Version | Purpose |
|------------|----------------|---------|
| BLUETOOTH | ≤ 11 | Legacy Bluetooth access |
| BLUETOOTH_ADMIN | ≤ 11 | Legacy BLE scanning |
| ACCESS_FINE_LOCATION | 10-11 | Required for BLE scan |
| BLUETOOTH_SCAN | ≥ 12 | New granular BLE scan |
| BLUETOOTH_CONNECT | ≥ 12 | New granular BLE connect |

## 🚴‍♂️ Enjoy Your Ride!

Happy cycling! Feel free to extend this app with your own features.
