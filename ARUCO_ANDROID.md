# ArUco Marker Tracking on Android

This document provides a comprehensive guide for implementing ArUco marker tracking in Android applications using OpenCV.

## Overview

ArUco markers are synthetic square markers composed of a wide black border and an inner binary matrix which determines its identifier. This implementation uses OpenCV's ArUco module to detect markers in real-time camera feeds and estimate their 3D pose.

## Library Dependencies

### OpenCV ArUco Module

The ArUco functionality is included in the `opencv344-contrib` module which contains OpenCV 3.4.4 with contributed modules including ArUco support.

**Build Configuration:**
```gradle
// In app/build.gradle
dependencies {
    implementation project(':opencv344-contrib')
}

// In settings.gradle
include ':opencv344-contrib'
```

### Key OpenCV Classes Used

- `org.opencv.aruco.Aruco` - Main ArUco detection functions
- `org.opencv.aruco.Dictionary` - Predefined marker dictionaries
- `org.opencv.aruco.DetectorParameters` - Detection algorithm parameters
- `org.opencv.calib3d.Calib3d` - Camera calibration and 3D pose estimation

## Camera Integration

### Camera View Setup

The implementation uses a custom `PortraitCameraView` that extends OpenCV's `JavaCameraView`:

```java
public class PortraitCameraView extends JavaCameraView {
    public interface CameraIntrinsicsListener {
        void onCameraIntrinsicsAvailable(Mat cameraMatrix, MatOfDouble distCoeffs);
    }
    
    public void setCameraIntrinsicsListener(CameraIntrinsicsListener listener);
    public Mat getCameraMatrix();
    public MatOfDouble getDistortionCoefficients();
}
```

### Camera Intrinsics

Camera intrinsics (focal length, principal point, distortion coefficients) are essential for accurate 3D pose estimation. The current implementation provides default intrinsic parameters:

```java
// Default camera matrix with reasonable estimates
double fx = 800.0; // focal length x
double fy = 800.0; // focal length y  
double cx = 320.0; // principal point x
double cy = 240.0; // principal point y

// Distortion coefficients set to zero
Mat distCoeffs = Mat.zeros(5, 1, CvType.CV_64FC1);
```

## ArUco Detection API

### Basic Detection Setup

```java
public class MainActivity extends AppCompatActivity implements CvCameraViewListener2, 
    PortraitCameraView.CameraIntrinsicsListener {

    // ArUco detection variables
    private Mat cameraMatrix;
    private MatOfDouble distCoeffs;
    private MatOfInt ids;
    private List<Mat> corners;
    private Dictionary dictionary;
    private DetectorParameters parameters;
    
    // 3D pose estimation
    private Mat rvecs; // rotation vectors
    private Mat tvecs; // translation vectors
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize ArUco components
        ids = new MatOfInt();
        corners = new LinkedList<>();
        dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_6X6_50);
        parameters = DetectorParameters.create();
        
        rvecs = new Mat();
        tvecs = new Mat();
    }
}
```

### Camera Intrinsics Callback

```java
@Override
public void onCameraIntrinsicsAvailable(Mat cameraMatrix, MatOfDouble distCoeffs) {
    this.cameraMatrix = cameraMatrix;
    this.distCoeffs = distCoeffs;
}
```

### Frame Processing Pipeline

```java
@Override
public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
    Mat rgba = inputFrame.rgba();
    Mat gray = inputFrame.gray();
    
    // Clear previous detection results
    corners.clear();
    ids = new MatOfInt();
    
    // Detect ArUco markers
    Aruco.detectMarkers(gray, dictionary, corners, ids, parameters);
    
    if (corners.size() > 0) {
        // Draw detected markers
        Aruco.drawDetectedMarkers(rgba, corners, ids);
        
        // Estimate 3D pose if camera is calibrated
        if (cameraMatrix != null && distCoeffs != null) {
            Aruco.estimatePoseSingleMarkers(corners, MARKER_SIZE, 
                cameraMatrix, distCoeffs, rvecs, tvecs);
            
            // Draw 3D coordinate axes on each marker
            for (int i = 0; i < ids.toArray().length; i++) {
                Aruco.drawAxis(rgba, cameraMatrix, distCoeffs, 
                    rvecs.row(i), tvecs.row(i), AXIS_LENGTH);
            }
        }
    }
    
    return rgba;
}
```

## ArUco Dictionary Types

The implementation uses `DICT_6X6_50` which provides 50 unique 6x6 bit markers. Other available dictionaries include:

- `DICT_4X4_50` - 4x4 markers, 50 unique IDs
- `DICT_5X5_250` - 5x5 markers, 250 unique IDs  
- `DICT_6X6_250` - 6x6 markers, 250 unique IDs
- `DICT_7X7_250` - 7x7 markers, 250 unique IDs
- `DICT_APRILTAG_16h5` - AprilTag format markers

## 3D Pose Estimation

### Marker Size Configuration

```java
public static final float MARKER_SIZE = 0.04f; // 4cm markers
```

The marker size must match the physical size of printed markers for accurate pose estimation.

### Pose Data Access

```java
// Get pose for specific marker
if (rvecs.rows() > markerIndex) {
    Mat rvec = rvecs.row(markerIndex); // rotation vector
    Mat tvec = tvecs.row(markerIndex); // translation vector
    
    // Convert to more usable formats if needed
    double[] rotation = new double[3];
    double[] translation = new double[3];
    rvec.get(0, 0, rotation);
    tvec.get(0, 0, translation);
}
```

## 3D Rendering Integration

### Rajawali 3D Engine Integration

The project includes Rajawali 3D engine for rendering 3D objects on detected markers:

```gradle
implementation 'org.rajawali3d:rajawali:1.1.970'
```

### Renderer Setup

```java
public class Renderer3D extends RajawaliRenderer {
    public void updateMarkerPose(Mat rvec, Mat tvec) {
        // Convert OpenCV pose to 3D engine coordinates
        // Update 3D object positions/rotations
    }
}
```

## Detection Parameters

### Optimizing Detection Performance

```java
DetectorParameters params = DetectorParameters.create();

// Adjust these parameters based on your use case:
params.set_minMarkerPerimeterRate(0.03);  // Minimum marker size relative to image
params.set_maxMarkerPerimeterRate(4.0);   // Maximum marker size relative to image
params.set_polygonalApproxAccuracyRate(0.05); // Contour approximation accuracy
params.set_minCornerDistanceRate(0.01);   // Minimum distance between corners
params.set_minOtsuStdDev(5.0);           // Adaptive thresholding parameter
```

## Image Processing for Static Images

The `ImageActivity` class demonstrates ArUco detection on static images:

```java
// Load image and convert to OpenCV format
Mat rgba = new Mat();
Utils.bitmapToMat(bitmap, rgba);

Mat gray = new Mat();
Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);

// Detect markers
Aruco.detectMarkers(gray, dictionary, corners, ids, parameters);

// Process results similar to camera feed
if (corners.size() > 0) {
    Aruco.drawDetectedMarkers(rgba, corners, ids);
    // ... pose estimation and rendering
}
```

## Best Practices

### Lighting Conditions
- Ensure adequate lighting for clear marker visibility
- Avoid strong shadows or reflections on markers
- Use consistent lighting when possible

### Marker Design
- Print markers with high contrast (black on white)
- Ensure markers are perfectly flat when mounted
- Use appropriate marker sizes for your detection distance
- Leave white border around markers when printing

### Performance Optimization
- Process frames at appropriate resolution (not necessarily full camera resolution)
- Consider downsampling images for faster detection if accuracy permits
- Use appropriate detection parameters for your specific use case
- Implement frame skipping if real-time performance is not critical

### Camera Calibration
- For high-precision applications, perform proper camera calibration
- Use calibration patterns to obtain accurate intrinsic parameters
- Account for lens distortion in your specific camera setup

## Troubleshooting

### Common Issues

1. **No markers detected**: Check lighting, marker print quality, and detection parameters
2. **Inaccurate pose estimation**: Verify camera calibration and marker size parameters
3. **Poor performance**: Reduce image resolution or adjust detection parameters
4. **Unstable tracking**: Implement smoothing/filtering on pose estimates

### Debug Logging

Enable detailed logging to troubleshoot detection issues:

```java
private static final String TAG = "ArUcoDetection";

Log.d(TAG, "Detected " + corners.size() + " markers");
if (ids.toArray().length > 0) {
    Log.d(TAG, "Marker IDs: " + Arrays.toString(ids.toArray()));
}
```

## Example Applications

This implementation can be used for various applications:

- **Augmented Reality**: Overlay 3D objects on detected markers
- **Robot Navigation**: Use markers as reference points for positioning
- **Camera Pose Estimation**: Determine camera position relative to known markers
- **Object Tracking**: Track objects with attached markers
- **Calibration**: Use markers for camera or system calibration

## Future Enhancements

Potential improvements to this implementation:

1. **Camera2 API Integration**: Enhanced camera intrinsics extraction using modern Android Camera2 API
2. **Multi-marker Tracking**: Simultaneous tracking of multiple marker types
3. **Marker Board Support**: Detection of marker boards for improved accuracy
4. **Custom Dictionary Support**: Create application-specific marker dictionaries
5. **Pose Filtering**: Implement Kalman filtering for smoother pose estimates