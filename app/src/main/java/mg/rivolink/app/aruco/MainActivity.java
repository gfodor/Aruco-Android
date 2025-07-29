package mg.rivolink.app.aruco;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.DialogInterface;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import android.view.WindowManager;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import mg.rivolink.app.aruco.renderer.Renderer3D;
import mg.rivolink.app.aruco.utils.CameraParameters;
import mg.rivolink.app.aruco.view.PortraitCameraView;
import mg.rivolink.app.aruco.view.PortraitCameraLayout;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.OpenCVLoader;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;
import android.util.Log;
import java.io.File;
import android.content.pm.ApplicationInfo;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import org.rajawali3d.view.SurfaceView;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2, PortraitCameraView.CameraIntrinsicsListener {

	public static final float SIZE = 0.04f;
	private static final int CAMERA_PERMISSION_REQUEST = 1;
	
	private Mat cameraMatrix;
	private MatOfDouble distCoeffs;

	private Mat rgb;
	private Mat gray;

	private Mat rvecs;
	private Mat tvecs;

	private MatOfInt ids;
	private List<Mat> corners;
	private Dictionary dictionary;
	private DetectorParameters parameters;
	private ArucoDetector arucoDetector;

	private Renderer3D renderer;
	private CameraBridgeViewBase camera;
	
	private void checkCameraPermissionAndInitialize() {
		Log.d("ArucoDebug", "checkCameraPermissionAndInitialize() called");
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			Log.d("ArucoDebug", "Camera permission not granted, requesting...");
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
		} else {
			Log.d("ArucoDebug", "Camera permission already granted, initializing OpenCV");
			initializeOpenCV();
		}
	}

	private void initializeOpenCV() {
		Log.d("ArucoDebug", "initializeOpenCV() called");
		try {
			cameraMatrix = Mat.eye(3, 3, CvType.CV_64FC1);
			Log.d("ArucoDebug", "Camera matrix created successfully");
			distCoeffs = new MatOfDouble(Mat.zeros(5, 1, CvType.CV_64FC1));
			Log.d("ArucoDebug", "Distortion coefficients created successfully");
			
			if (camera instanceof PortraitCameraView) {
				((PortraitCameraView) camera).setCameraIntrinsicsListener(MainActivity.this);
				Log.d("ArucoDebug", "Camera intrinsics listener set");
			}
			
			Log.d("ArucoDebug", "About to call camera.enableView()");
			camera.enableView();
			Log.d("ArucoDebug", "Camera view enabled successfully");
			
			// Add some debugging to see camera state
			Log.d("ArucoDebug", "Camera visibility: " + camera.getVisibility());
			Log.d("ArucoDebug", "Camera class: " + camera.getClass().getName());
			
			// Try to force camera connection after a short delay
			camera.post(new Runnable() {
				@Override
				public void run() {
					Log.d("ArucoDebug", "Attempting to force camera view layout and connection...");
					camera.requestLayout();
					camera.invalidate();
					
					// Check if the view has dimensions
					Log.d("ArucoDebug", "Camera view dimensions: " + camera.getWidth() + "x" + camera.getHeight());
				}
			});
		} catch (Exception e) {
			Log.e("ArucoDebug", "Error in initializeOpenCV(): " + e.getMessage(), e);
		}
	}

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main_layout);

        camera = ((PortraitCameraLayout)findViewById(R.id.camera_layout)).getCamera();
        camera.setVisibility(SurfaceView.VISIBLE);
        camera.setCvCameraViewListener(this);

		renderer = new Renderer3D(this);

		SurfaceView surface = (SurfaceView)findViewById(R.id.main_surface);
		surface.setTransparent(true);
		surface.setSurfaceRenderer(renderer);

	}

	@Override
	public void onCameraIntrinsicsAvailable(Mat cameraMatrix, MatOfDouble distCoeffs) {
		this.cameraMatrix = cameraMatrix;
		this.distCoeffs = distCoeffs;
	}

	private void logNativeLibraries() {
		try {
			ApplicationInfo appInfo = getApplicationInfo();
			String nativeLibDir = appInfo.nativeLibraryDir;
			Log.d("ArucoDebug", "Native library directory: " + nativeLibDir);
			
			File libDir = new File(nativeLibDir);
			if (libDir.exists() && libDir.isDirectory()) {
				File[] libs = libDir.listFiles();
				if (libs != null) {
					Log.d("ArucoDebug", "Found " + libs.length + " native libraries:");
					for (File lib : libs) {
						Log.d("ArucoDebug", "  - " + lib.getName() + " (" + lib.length() + " bytes)");
					}
				} else {
					Log.w("ArucoDebug", "Native library directory is empty");
				}
			} else {
				Log.e("ArucoDebug", "Native library directory does not exist or is not a directory");
			}
		} catch (Exception e) {
			Log.e("ArucoDebug", "Error listing native libraries: " + e.getMessage(), e);
		}
	}

	@Override
    public void onResume(){
        super.onResume();
        
        logNativeLibraries();

		Log.d("ArucoDebug", "=== STARTING OPENCV INITIALIZATION ===");
		Log.d("ArucoDebug", "OpenCV Version: " + OpenCVLoader.OPENCV_VERSION);
		Log.d("ArucoDebug", "Architecture: " + System.getProperty("os.arch"));
		Log.d("ArucoDebug", "Primary ABI: " + android.os.Build.CPU_ABI);
		Log.d("ArucoDebug", "Secondary ABI: " + android.os.Build.CPU_ABI2);
		Log.d("ArucoDebug", "Supported ABIs: " + java.util.Arrays.toString(android.os.Build.SUPPORTED_ABIS));
		Log.d("ArucoDebug", "Device model: " + android.os.Build.MODEL);
		Log.d("ArucoDebug", "Android version: " + android.os.Build.VERSION.RELEASE);
		
		// Try to manually load the library first to get better error info
		try {
			Log.d("ArucoDebug", "Manually attempting to load opencv_java4...");
			System.loadLibrary("opencv_java4");
			Log.d("ArucoDebug", "Manual load of opencv_java4 SUCCESS");
		} catch (UnsatisfiedLinkError e) {
			Log.e("ArucoDebug", "Manual load of opencv_java4 FAILED: " + e.getMessage(), e);
		} catch (Exception e) {
			Log.e("ArucoDebug", "Manual load exception: " + e.getMessage(), e);
		}
		
		try {
			boolean initResult = OpenCVLoader.initLocal();
			Log.d("ArucoDebug", "OpenCVLoader.initLocal() returned: " + initResult);
			
			if(initResult) {
				Log.d("ArucoDebug", "OpenCV initialization SUCCESS, checking camera permissions");
				checkCameraPermissionAndInitialize();
			} else {
				Log.e("ArucoDebug", "OpenCV initialization FAILED");
				Toast.makeText(this, getString(R.string.error_native_lib), Toast.LENGTH_LONG).show();
			}
		} catch (Exception e) {
			Log.e("ArucoDebug", "Exception during OpenCV initialization: " + e.getMessage(), e);
			Toast.makeText(this, "OpenCV init exception: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
    }

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		Log.d("ArucoDebug", "onRequestPermissionsResult called for request: " + requestCode);
		
		if (requestCode == CAMERA_PERMISSION_REQUEST) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Log.d("ArucoDebug", "Camera permission granted, initializing OpenCV");
				initializeOpenCV();
			} else {
				Log.e("ArucoDebug", "Camera permission denied");
				Toast.makeText(this, "Camera permission is required for ArUco detection", Toast.LENGTH_LONG).show();
			}
		}
	}
	
	@Override
    public void onPause(){
        super.onPause();

        if(camera != null)
            camera.disableView();
    }

	@Override
    public void onDestroy(){
        super.onDestroy();

        if (camera != null)
            camera.disableView();
    }

	@Override
	public void onCameraViewStarted(int width, int height){
		Log.d("ArucoDebug", "onCameraViewStarted called: " + width + "x" + height);
		try {
			rgb = new Mat();
			Log.d("ArucoDebug", "RGB Mat created");
			corners = new LinkedList<>();
			Log.d("ArucoDebug", "Corners list created");
			parameters = new DetectorParameters();
			Log.d("ArucoDebug", "Detector parameters created");
			dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_6X6_50);
			Log.d("ArucoDebug", "Dictionary created");
			arucoDetector = new ArucoDetector(dictionary, parameters);
			Log.d("ArucoDebug", "ArUco detector created successfully");
		} catch (Exception e) {
			Log.e("ArucoDebug", "Error in onCameraViewStarted: " + e.getMessage(), e);
		}
	}

	@Override
	public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
		try {
			if(cameraMatrix == null || distCoeffs == null){
				Log.w("ArucoDebug", "Camera matrix or distCoeffs is null, skipping frame");
				return inputFrame.rgba();
			}
			
			// Ensure all required objects are initialized
			if (rgb == null) {
				Log.w("ArucoDebug", "RGB Mat was null, creating new one");
				rgb = new Mat();
			}
			if (corners == null) {
				Log.w("ArucoDebug", "Corners list was null, creating new one");
				corners = new LinkedList<>();
			}
			if (arucoDetector == null) {
				Log.w("ArucoDebug", "ArUco detector was null, recreating");
				parameters = new DetectorParameters();
				dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_6X6_50);
				arucoDetector = new ArucoDetector(dictionary, parameters);
			}
			
			Imgproc.cvtColor(inputFrame.rgba(), rgb, Imgproc.COLOR_RGBA2RGB);
			gray = inputFrame.gray();

			ids = new MatOfInt();
			corners.clear();

			arucoDetector.detectMarkers(gray, corners, ids);

			if(corners.size()>0){
				Objdetect.drawDetectedMarkers(rgb, corners, ids);

				// Estimate pose for each detected marker using solvePnP
				for(int i = 0; i < corners.size(); i++) {
					try {
						Mat markerCorners = corners.get(i);
						estimateMarkerPose(markerCorners, cameraMatrix, distCoeffs, rgb, i);
					} catch (Exception e) {
						Log.e("ArucoDebug", "Error estimating pose for marker " + i + ": " + e.getMessage());
					}
				}
			}

			return rgb;
		} catch (Exception e) {
			Log.e("ArucoDebug", "Error in onCameraFrame: " + e.getMessage(), e);
			return inputFrame.rgba();
		}
	}

	@Override
	public void onCameraViewStopped(){
		Log.d("ArucoDebug", "onCameraViewStopped called");
		try {
			if (rgb != null) {
				rgb.release();
				Log.d("ArucoDebug", "RGB Mat released");
			}
		} catch (Exception e) {
			Log.e("ArucoDebug", "Error in onCameraViewStopped: " + e.getMessage(), e);
		}
	}
	
	private void estimateMarkerPose(Mat markerCorners, Mat cameraMatrix, MatOfDouble distCoeffs, Mat frame, int markerIndex) {
		try {
			// Create 3D object points for a marker (SIZE x SIZE square at z=0)
			double halfSize = SIZE / 2.0;
			List<Point3> objectPoints = new ArrayList<>();
			objectPoints.add(new Point3(-halfSize, -halfSize, 0));
			objectPoints.add(new Point3(halfSize, -halfSize, 0));
			objectPoints.add(new Point3(halfSize, halfSize, 0));
			objectPoints.add(new Point3(-halfSize, halfSize, 0));
			
			MatOfPoint3f objectPointsMat = new MatOfPoint3f();
			objectPointsMat.fromList(objectPoints);
			
			// Convert marker corners to the right format
			// markerCorners is a Mat with 4 corner points (1x4x2 CV_32FC2)
			Point[] cornerPoints = new Point[4];
			for (int j = 0; j < 4; j++) {
				double[] pt = markerCorners.get(0, j);
				cornerPoints[j] = new Point(pt[0], pt[1]);
			}
			MatOfPoint2f imagePointsMat = new MatOfPoint2f(cornerPoints);
			
			// Solve PnP to get pose
			Mat rvec = new Mat();
			Mat tvec = new Mat();
			
			boolean success = Calib3d.solvePnP(objectPointsMat, imagePointsMat, cameraMatrix, distCoeffs, rvec, tvec);
			
			if (success) {
				// Draw 3D axis
				drawAxis(frame, cameraMatrix, distCoeffs, rvec, tvec, SIZE);
				
				// Draw 3D cube
				draw3dCube(frame, cameraMatrix, distCoeffs, rvec, tvec, new Scalar(0, 255, 0));
				
				// Send pose to 3D renderer
				transformModel(tvec, rvec);
			}
			
			// Clean up
			rvec.release();
			tvec.release();
			objectPointsMat.release();
			imagePointsMat.release();
			
		} catch (Exception e) {
			Log.e("ArucoDebug", "Exception in estimateMarkerPose: " + e.getMessage(), e);
		}
	}
	
	private void drawAxis(Mat frame, Mat cameraMatrix, MatOfDouble distCoeffs, Mat rvec, Mat tvec, float length) {
		try {
			// Define 3D axis points
			List<Point3> axisPoints = new ArrayList<>();
			axisPoints.add(new Point3(0, 0, 0));        // origin
			axisPoints.add(new Point3(length, 0, 0));   // X axis (red)
			axisPoints.add(new Point3(0, length, 0));   // Y axis (green)  
			axisPoints.add(new Point3(0, 0, -length));  // Z axis (blue)
			
			MatOfPoint3f axisPoints3D = new MatOfPoint3f();
			axisPoints3D.fromList(axisPoints);
			
			// Project to 2D
			MatOfPoint2f axisPoints2D = new MatOfPoint2f();
			Calib3d.projectPoints(axisPoints3D, rvec, tvec, cameraMatrix, distCoeffs, axisPoints2D);
			
			Point[] pts = axisPoints2D.toArray();
			if (pts.length >= 4) {
				// Draw X axis in red
				Imgproc.line(frame, pts[0], pts[1], new Scalar(0, 0, 255), 3);
				// Draw Y axis in green  
				Imgproc.line(frame, pts[0], pts[2], new Scalar(0, 255, 0), 3);
				// Draw Z axis in blue
				Imgproc.line(frame, pts[0], pts[3], new Scalar(255, 0, 0), 3);
			}
			
			axisPoints3D.release();
			axisPoints2D.release();
		} catch (Exception e) {
			Log.e("ArucoDebug", "Error drawing axis: " + e.getMessage());
		}
	}

	public void draw3dCube(Mat frame, Mat cameraMatrix, MatOfDouble distCoeffs, Mat rvec, Mat tvec, Scalar color){
		double halfSize = SIZE/2.0;

		List<Point3> points = new ArrayList<Point3>();
		points.add(new Point3(-halfSize, -halfSize, 0));
		points.add(new Point3(-halfSize,  halfSize, 0));
		points.add(new Point3( halfSize,  halfSize, 0));
		points.add(new Point3( halfSize, -halfSize, 0));
		points.add(new Point3(-halfSize, -halfSize, SIZE));
		points.add(new Point3(-halfSize,  halfSize, SIZE));
		points.add(new Point3( halfSize,  halfSize, SIZE));
		points.add(new Point3( halfSize, -halfSize, SIZE));

		MatOfPoint3f cubePoints = new MatOfPoint3f();
		cubePoints.fromList(points);

		MatOfPoint2f projectedPoints = new MatOfPoint2f();
		Calib3d.projectPoints(cubePoints, rvec, tvec, cameraMatrix, distCoeffs, projectedPoints);

		List<Point> pts = projectedPoints.toList();

	    for(int i=0; i<4; i++){
	        Imgproc.line(frame, pts.get(i), pts.get((i+1)%4), color, 2);
	        Imgproc.line(frame, pts.get(i+4), pts.get(4+(i+1)%4), color, 2);
	        Imgproc.line(frame, pts.get(i), pts.get(i+4), color, 2);
	    }	        
	}
	
	private void transformModel(final Mat tvec, final Mat rvec){
		try {
			// Check if Mats are valid and have data
			if (tvec == null || rvec == null || tvec.empty() || rvec.empty()) {
				Log.w("ArucoDebug", "transformModel: tvec or rvec is null/empty");
				return;
			}
			
			// Get the translation and rotation data safely
			double[] tvecData = tvec.get(0, 0);
			double[] rvecData = rvec.get(0, 0);
			
			if (tvecData == null || rvecData == null || 
				tvecData.length < 3 || rvecData.length < 3) {
				Log.w("ArucoDebug", "transformModel: invalid data arrays");
				return;
			}
			
			runOnUiThread(new Runnable(){
				@Override
				public void run(){
					try {
						double[] tvecData = tvec.get(0, 0);
						double[] rvecData = rvec.get(0, 0);
						
						if (tvecData != null && rvecData != null && 
							tvecData.length >= 3 && rvecData.length >= 3) {
							renderer.transform(
								tvecData[0]*50,
								-tvecData[1]*50,
								-tvecData[2]*50,
							
								rvecData[2], //yaw
								rvecData[1], //pitch
								rvecData[0] //roll
							);
						}
					} catch (Exception e) {
						Log.e("ArucoDebug", "Error in transformModel UI thread: " + e.getMessage());
					}
				}
			});
		} catch (Exception e) {
			Log.e("ArucoDebug", "Error in transformModel: " + e.getMessage());
		}
	}
	
}


