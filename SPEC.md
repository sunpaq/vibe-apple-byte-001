# Apple Wound Detection POC - Specification

## 1. Project Overview
- **Project Name**: AppleWoundDetector
- **Project Type**: Android Native Application
- **Core Functionality**: Detect wound areas and measure depth on bitten apples using ARCore depth detection, CameraX, and ArUco markers as reference objects.

## 2. Technology Stack & Choices

### Framework and Language
- **Language**: Kotlin 1.9.x
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34

### Key Libraries/Dependencies
- **ARCore**: 1.40.0 - For AR session management and depth detection
- **CameraX**: 1.3.1 - For camera preview and image analysis
- **OpenCV Android**: 4.8.0 - For ArUco marker detection and image processing
- **Sceneform**: 1.17.1 (or maintained fork) - For 3D rendering
- **ML Kit**: 17.0.5 - For potential image segmentation
- **Coroutines**: 1.7.3 - For async operations
- **ViewBinding**: Enabled

### State Management
- ViewModel + StateFlow for reactive UI state management

### Architecture Pattern
- MVVM (Model-View-ViewModel) with Clean Architecture layers:
  - **Presentation Layer**: Activities, Fragments, ViewModels
  - **Domain Layer**: Use Cases, Repository Interfaces
  - **Data Layer**: Repository Implementations, Data Sources

## 3. Feature List

### Core Features
1. **AR Session Management**
   - Initialize ARCore session with depth detection enabled
   - Handle ARCore availability and permissions

2. **Camera Preview**
   - CameraX integration for camera preview
   - Synchronize AR camera with CameraX preview

3. **ArUco Marker Detection**
   - Detect ArUco markers in real-time using OpenCV
   - Use marker as scale reference for measurements
   - Support ArUco dictionary DICT_4X4_50

4. **Wound Area Detection**
   - Detect bitten area on apple using color segmentation
   - Edge detection using Canny algorithm
   - Contour detection for wound boundary

5. **Depth Measurement**
   - Use ARCore Depth API for depth estimation
   - Measure depth at wound center point
   - Calculate wound depth relative to apple surface

6. **Measurement Display**
   - Real-time overlay showing wound area (in pixels/mm)
   - Display depth measurement in millimeters
   - Visual feedback on AR view

## 4. UI/UX Design Direction

### Overall Visual Style
- Material Design 3 with clean, minimal interface
- Dark theme optimized for AR viewing
- Semi-transparent overlays for measurement data

### Color Scheme
- Primary: Deep Blue (#1565C0)
- Secondary: Teal (#00897B)
- Accent: Orange (#FF6F00) for wound highlighting
- Surface: Dark Grey (#1E1E1E) for AR background

### Layout Approach
- Single full-screen AR view as main interface
- Floating action buttons for capture/measure
- Bottom sheet for measurement results
- Top app bar with settings and info

### Key UI Components
- AR Camera View (full screen)
- Measurement overlay (semi-transparent)
- Capture button (FAB)
- Results bottom sheet
- Status indicators (AR tracking, marker detected)
