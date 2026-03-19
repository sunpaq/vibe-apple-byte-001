# Apple Byte 004 - Specification Document

## 1. Project Overview
- **Project Name**: apple-byte-004
- **Type**: Android Native Application (POC Demo)
- **Core Functionality**: Detect wound area and estimate depth of bitten apples using Structure-from-Motion (SfM) with ArUco marker as reference scale

## 2. Technology Stack & Choices

### Framework & Language
- **Language**: Kotlin 1.9.x
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34

### Key Libraries/Dependencies
- **CameraX**: 1.3.1 - Modern camera API for image capture
- **OpenCV**: 4.8.0 (Android) - Image processing and SfM
- **OpenCV Extra**: 4.8.0 (ArUco module) - Marker detection
- **Android Graphics**: For UI overlays
- **Kotlin Coroutines**: 1.7.3 - Async processing

### Architecture Pattern
- **MVVM** with Clean Architecture principles
- **Repository Pattern** for data layer
- **Use Cases** for business logic

## 3. Feature List

### Core Features
1. **Guided Photo Capture UI**
   - Step-by-step wizard guiding user to capture multiple images
   - Visual indicator showing capture progress (X/10 images)
   - Real-time ArUco marker detection preview
   - Audio/haptic feedback on successful capture

2. **ArUco Marker Detection**
   - Real-time marker detection in camera preview
   - 4x4 ArUco dictionary (DICT_4X4_50)
   - Scale reference calculation from marker size
   - draw marker contour use light green color line

3. **Structure-from-Motion Processing**
   - Feature extraction using ORB
   - Image matching between captures
   - Camera pose estimation
   - Sparse 3D point cloud generation
   - Depth estimation relative to ArUco marker

4. **Wound Detection & Analysis**
   - Color-based wound segmentation (brownish/discolored areas)
   - Contour detection for wound boundary
   - Depth estimation at wound locations
   - Wound area calculation in mm²
   - Wound depth calculation in mm

5. **Results Display**
   - 3D point cloud visualization, able to rotate the scene
   - Wound area overlay on reference image
   - Depth measurement display
   - Export/save functionality

## 4. UI/UX Design Direction

### Overall Visual Style
- **Material Design 3** with clean, medical/professional aesthetic
- Light theme with blue accent colors
- Clear, readable typography

### Color Scheme
- **Primary**: #1976D2 (Medical Blue)
- **Secondary**: #26A69A (Teal)
- **Background**: #FAFAFA (Light Gray)
- **Error/Wound**: #E57373 (Light Red)
- **Success**: #81C784 (Green)

### Layout Approach
- **Single Activity** with Fragment-based navigation
- **Bottom Sheet** for results display
- **Full-screen camera preview** with overlay guides

### Screen Flow
1. **Welcome Screen**: App introduction and instructions
2. **Capture Screen**: Guided multi-photo capture with real-time marker detection
3. **Processing Screen**: Progress indicator during SfM analysis
4. **Results Screen**: Wound area visualization and depth measurements

### User Guidance Elements
- Animated arrows showing capture positions
- Progress ring showing number of captures
- Status messages (e.g., "Move device left", "Keep marker in view")
- Success/error feedback indicators
