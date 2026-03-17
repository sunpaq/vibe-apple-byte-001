# Apple Byte Wound Detection POC - Specification

## 1. Project Overview
- **Project Name**: apple-byte-002
- **Type**: Android AR Application (POC)
- **Core Functionality**: Detect wound area and depth on a byte apple using ARCore depth detection, CameraX, and ArUco markers as reference

## 2. Technology Stack & Choices

### Framework & Language
- **Language**: Kotlin 1.9.x
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34

### Key Libraries/Dependencies
- **ARCore**: 1.40.0 (depth detection API)
- **CameraX**: 1.3.1 (camera preview and image analysis)
- **OpenCV**: 4.8.0 (ArUco marker detection and image processing)
- **SceneView**: 2.0.3 (AR rendering)
- **Kotlin Coroutines**: 1.7.3

### Architecture
- **Pattern**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Manual (simple POC)

### State Management
- StateFlow for reactive UI updates

## 3. Feature List

1. **AR Camera Preview**
   - Real-time camera preview using CameraX
   - ARCore session management

2. **ArUco Marker Detection**
   - Detect ArUco marker (DICT_4X4_50) as reference scale
   - Calculate marker pose for depth calibration

3. **Wound Area Detection**
   - Color-based segmentation to detect damaged (brown/rotten) areas on apple
   - Contour detection to outline wound region
   - Calculate wound area in pixels

4. **Depth Measurement**
   - Use ARCore Depth API to get depth map
   - Calculate average depth within wound area
   - Calculate wound depth relative to apple surface

5. **UI Display**
   - Display wound area (mm²)
   - Display wound depth (mm)
   - Visual overlay showing wound contour

## 4. UI/UX Design Direction

### Visual Style
- Material Design 3
- Dark theme for better AR visualization

### Color Scheme
- Primary: Deep Blue (#1565C0)
- Secondary: Teal (#00897B)
- Warning: Orange (#FF6D00) for wound detection
- Background: Dark gray (#121212)

### Layout Approach
- Single full-screen AR camera view
- Bottom sheet with measurement results
- Overlay controls for capture/reset
