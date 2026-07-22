# Implementation Plan - Fix Camera Analysis Crash

The app is crashing when the camera starts because ML Kit's `InputImage.fromMediaImage` does not support the `RGBA_8888` format currently configured in `DetectorScreen.kt`.

## User Review Required

> [!IMPORTANT]
> I will change the `ImageAnalysis` configuration to use the default image format (`YUV_420_888`), which is supported by ML Kit and more efficient for real-time analysis.

## Proposed Changes

### UI Layer

#### [MODIFY] [DetectorScreen.kt](file:///C:/Users/smart/AndroidStudioProjects/test/app/src/main/java/com/example/test/ui/DetectorScreen.kt)
- Remove `.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)` from the `ImageAnalysis.Builder` in both the `factory` and `update` blocks of the `AndroidView`.
- Clean up any unused imports or redundant qualifiers if found.

## Verification Plan

### Automated Tests
- Run `gradle_build` to ensure the project compiles.
- Run `connectedDebugAndroidTest` (if possible) or ask the user to verify the camera preview no longer crashes.

### Manual Verification
- Launch the app and go to the Detector screen.
- Verify that the camera preview starts and bird detection works without crashing.
