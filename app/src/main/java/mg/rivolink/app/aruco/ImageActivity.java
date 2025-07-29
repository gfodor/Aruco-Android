package mg.rivolink.app.aruco;

import android.app.Activity;
import android.content.Intent;

import android.os.Bundle;
import android.net.Uri;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.widget.Toast;
import android.widget.ImageView;

import java.io.InputStream;
import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;

import org.opencv.android.Utils;
import org.opencv.android.OpenCVLoader;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.CvType;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Objdetect;
import android.util.Log;

import mg.rivolink.app.aruco.utils.CameraParameters;

public class ImageActivity extends Activity {

	private static Mat cameraMatrix;
	private static Mat distCoeffs;

	private ImageView imageView;
	private Bitmap originalBMP;

	private void initializeOpenCV() {
		String message = "";

		if(loadCameraParams()){
			message = getString(R.string.info_detecting_markers);
			detectMarkersAsync();
		}
		else {
			message = getString(R.string.error_camera_params);
		}

		Toast.makeText(ImageActivity.this,  message,  Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.image_layout);

		imageView = findViewById(R.id.image_view);

		if(getIntent().getData() != null) try {
			Uri uri=getIntent().getData();
			InputStream is = getContentResolver().openInputStream(uri);
			imageView.setImageBitmap(originalBMP = BitmapFactory.decodeStream(is));
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}

	@Override
	protected void onResume(){
		super.onResume();

		Log.d("ArucoDebug", "ImageActivity: Attempting to initialize OpenCV...");
		try {
			boolean initResult = OpenCVLoader.initLocal();
			Log.d("ArucoDebug", "ImageActivity: OpenCVLoader.initLocal() returned: " + initResult);
			
			if(initResult) {
				Log.d("ArucoDebug", "ImageActivity: OpenCV initialization SUCCESS");
				initializeOpenCV();
			} else {
				Log.e("ArucoDebug", "ImageActivity: OpenCV initialization FAILED");
				Toast.makeText(this, getString(R.string.error_native_lib), Toast.LENGTH_LONG).show();
			}
		} catch (Exception e) {
			Log.e("ArucoDebug", "ImageActivity: Exception during OpenCV initialization: " + e.getMessage(), e);
			Toast.makeText(this, "OpenCV init exception: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private boolean loadCameraParams(){
		int width = originalBMP != null ? originalBMP.getWidth() : 640;
		int height = originalBMP != null ? originalBMP.getHeight() : 480;
		
		cameraMatrix = CameraParameters.createDefaultCameraMatrix(width, height);
		distCoeffs = Mat.zeros(5, 1, CvType.CV_64FC1);
		return true;
	}

	private void detectMarkersAsync(){
		new Thread(){

			Bitmap bitmap;

			@Override
			public void run() {
				bitmap = detectMarkers(originalBMP);

				ImageActivity.this.runOnUiThread(new Runnable(){
					@Override
					public void run() {
						if(bitmap != null)
							imageView.setImageBitmap(bitmap);
						else
							Toast.makeText(ImageActivity.this, getString(R.string.info_no_marker), Toast.LENGTH_SHORT).show();
					}
				});
			}
		}.start();
	}

	private static Bitmap detectMarkers(Bitmap original){
		Bitmap bitmap = null;

		Mat rgba = new Mat();
		Utils.bitmapToMat(original, rgba);

		Mat rgb = new Mat();
		Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);

		Mat gray = new Mat();
		Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);

		MatOfInt ids = new MatOfInt();
		List<Mat> corners = new LinkedList<>();
		Dictionary dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_6X6_50);
		DetectorParameters parameters = new DetectorParameters();
		ArucoDetector arucoDetector = new ArucoDetector(dictionary, parameters);

		arucoDetector.detectMarkers(gray, corners, ids);

		if(corners.size() > 0){
			Objdetect.drawDetectedMarkers(rgb, corners, ids);
			bitmap=Bitmap.createBitmap(rgb.width(), rgb.height(), Bitmap.Config.RGB_565);
			Utils.matToBitmap(rgb, bitmap);
		}

		rgba.release();
		rgb.release();
		gray.release();
		ids.release();

		corners.clear();

		return bitmap;
	}

}
