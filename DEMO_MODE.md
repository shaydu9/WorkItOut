# Demo Mode - WorkItOut

## Overview
WorkItOut includes a built-in **Demo Mode** that simulates realistic cycling workout data without requiring physical BLE devices. This is perfect for:
- Testing the app UI/UX
- Demonstrating features to potential users
- Developing and debugging without hardware
- Training on how to use the app

## Features

### Realistic Data Simulation
The demo mode generates dynamic, realistic cycling data including:
- **Heart Rate**: 60-190 BPM with natural variations
- **Power**: 0-450W with realistic fluctuations and coasting
- **Cadence**: 0-120 RPM that correlates with power output

### Workout Phases
The demo simulates a structured interval workout with four phases that cycle every 30 seconds:

1. **Recovery** (Easy)
   - HR: ~110 BPM
   - Power: ~150W
   - Cadence: ~75 RPM

2. **Endurance** (Moderate)
   - HR: ~135 BPM
   - Power: ~220W
   - Cadence: ~88 RPM

3. **Threshold** (Hard Interval)
   - HR: ~165 BPM
   - Power: ~300W
   - Cadence: ~95 RPM

4. **Sprint** (Max Effort)
   - HR: ~175 BPM
   - Power: ~380W
   - Cadence: ~105 RPM

### Natural Variations
- **Sine wave variations**: Smooth, gradual changes
- **Random noise**: Realistic power/HR fluctuations
- **Coasting simulation**: Occasional drops to near-zero power
- **Fatigue modeling**: Slight HR drift up and power down over time

### Power Smoothing
Demo mode data respects the user's configured power smoothing setting (1-10 seconds), just like real device data.

## How to Use

### Activating Demo Mode

1. **Navigate to Profiles**
   - Open the navigation drawer
   - Select "Profiles"

2. **Find the Demo Profile**
   - Look for the profile named "🎮 Demo Mode"
   - It's automatically created on first app launch

3. **Activate the Demo Profile**
   - Tap the "..." menu on the Demo Mode profile
   - Select "Set Active"
   - The app will immediately start generating mock data

### Viewing Live Data

1. **Go to Profile Detail**
   - Tap on the active Demo Mode profile
   - You'll see real-time updating metrics

2. **Demo Mode Banner**
   - A distinctive banner shows:
     - "🎮 Demo Mode Active"
     - Current workout phase (Recovery/Endurance/Threshold/Sprint)
     - Elapsed time

3. **Live Metrics**
   - Heart Rate card updates every second
   - Power card shows smoothed power data
   - Cadence updates dynamically

### Deactivating Demo Mode

1. **Option 1: Select Another Profile**
   - Activating any other profile automatically disables demo mode

2. **Option 2: Deactivate All Profiles**
   - In the Profiles screen
   - Use the option to deactivate all profiles

## Technical Details

### Implementation
- `MockDataGenerator.kt`: Core simulation engine
- `BleManager.enableDemoMode()`: Activates simulation
- `BleManager.disableDemoMode()`: Stops simulation
- Updates every 1 second with Kotlin Coroutines

### Data Generation Algorithm
```kotlin
// Base values change with workout phase
baseHeartRate = 110-175 BPM
basePower = 150-380W
baseCadence = 75-105 RPM

// Apply variations
sineVariation = sin(time/10) * 0.1
randomNoise = (random - 0.5) * 0.15
totalVariation = 1.0 + sineVariation + randomNoise

// Calculate final values
heartRate = baseHeartRate * totalVariation
power = basePower * powerVariation
cadence = baseCadence * cadenceVariation

// Apply fatigue over time
fatigue = (minutes * 0.002).coerceIn(0.0, 0.1)
heartRate *= (1.0 + fatigue)
power *= (1.0 - fatigue * 0.5)
```

### Profile ID
- Demo profile uses a reserved ID: `"demo_profile_00000000"`
- This ensures it's always recognizable and can't conflict with user profiles

## Best Practices

### For Developers
- Use demo mode for UI testing
- Validate metric display formatting
- Test power smoothing algorithms
- Debug state management issues

### For Demos
- Start demo mode before presentations
- Let it run for 2-3 minutes to show all phases
- Point out the different workout intensities
- Explain how it simulates real device data

### For Users
- Try demo mode to learn the app interface
- Understand what data you'll see during real rides
- Experiment with settings (theme, power smoothing) safely
- Get comfortable with the UI before connecting real devices

## Limitations

### What Demo Mode Does NOT Do
- ❌ Connect to actual BLE devices
- ❌ Record workouts (future feature)
- ❌ Save fitness data
- ❌ Sync with external services
- ❌ Simulate GPS/speed/distance

### What Demo Mode DOES Do
- ✅ Simulate HR, Power, Cadence
- ✅ Show realistic data variations
- ✅ Demonstrate UI/UX
- ✅ Allow setting changes
- ✅ Provide consistent demo experience

## Troubleshooting

### Demo Mode Not Starting
- Ensure you're activating the "🎮 Demo Mode" profile
- Check that no other profiles are active
- Restart the app if needed

### Data Not Updating
- Verify the demo mode banner is visible
- Check that you're on the Profile Detail screen
- Look for the elapsed time counter

### Demo Profile Missing
- The app creates it automatically on first launch
- If missing, clear app data and restart
- Check `ProfileRepository.ensureDemoProfileExists()`

## Future Enhancements

Potential improvements to demo mode:
- Custom workout scripts (e.g., FTP test, recovery ride)
- Adjustable intensity levels
- Simulate GPS coordinates for map testing
- Virtual course simulation (e.g., Alpe d'Huez)
- Multiple demo profiles (sprinter, climber, endurance)

## Support

If you encounter issues with demo mode:
1. Check this documentation
2. Review app logs for `MockDataGenerator` tag
3. Verify BleManager is properly initialized
4. File an issue on GitHub with reproduction steps

---

**Version**: 1.0  
**Last Updated**: January 2026  
**Component**: Demo Mode / Mock Data Generator
