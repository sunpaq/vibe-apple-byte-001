# Apple Byte 003 - Wound Detection POC Specification

## 1. Project Overview
- **Project Name**: apple-byte-003
- **Type**: Android Application (POC)
- **Core Functionality**: Detect wound area and depth on a bitten apple using SfM (Structure from Motion) algorithm with CameraX, ArUco markers as scale reference

## 2. Technology Stack & Choices

### Framework & Language
- **Language**: Kotlin 1.9.x
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34

### Key Libraries/Dependencies
- **CameraX**: 1.3.1 (camera preview and image capture)
- **OpenCV**: 4.8.0 (ArUco marker detection, SfM algorithms, image processing)
- **OpenCV Extra**: 4.8.0 (SFM module)
- **Kotlin Coroutines**: 1.7.3 (async operations)
- **Material Components**: 1.11.0 (UI)

### Architecture
- **Pattern**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Manual (simple POC)

### State Management
- StateFlow for reactive UI updates

## 3. Feature List

### 1. Multi-Photo Capture UI
- Full-screen camera preview using CameraX
- Step-by-step guide overlay showing capture progress
- Visual indicators for optimal framing
- Capture 6-10 photos from different angles
- Preview thumbnails of captured photos

### 2. ArUco Marker Detection
- Detect ArUco marker (DICT_4X4_50) as reference scale
- Calculate marker dimensions for real-world scale calibration
- Display marker detection status in UI

### 3. SfM Depth Estimation
- Implement Structure from Motion using OpenCV
- Feature detection (ORB/SIFT) and matching
- Camera pose estimation
- Sparse point cloud generation
- Depth map estimation from multiple views

### 4. Wound Area Detection
- Color-based segmentation to detect damaged (brown/rotten) areas
- Contour detection to outline wound region
- Calculate wound area in mm² using ArUco scale

### 5. Wound Depth Estimation
- Use SfM point cloud to estimate depth within wound region
- Compare wound depth to surrounding apple surface
- Calculate wound depth in mm

### 6. Results Display
- Display wound area (mm²)
- Display wound depth (mm)
- Visual overlay showing wound contour on reference image

## 4. UI/UX Design Direction

### Visual Style
- Material Design 3
- Dark theme for better camera visualization

### Color Scheme
- Primary: Deep Blue (#1565C0)
- Secondary: Teal (#00897B)
- Warning: Orange (#FF6D00) for wound detection
- Background: Dark gray (#121212)

### Layout Approach
- **Capture Mode**: Full-screen camera with overlay guide
- **Processing Mode**: Progress indicator with step descriptions
- **Results Mode**: Bottom sheet with measurements and visualization
- Floating action button for capture
- Gallery strip showing captured photos

### User Flow
1. Show ArUco marker placement instructions
2. Guide user to capture photos from multiple angles (circular motion)
3. Show capture progress (X/10 photos)
4. Process images with SfM algorithm
5. Display wound area and depth results
