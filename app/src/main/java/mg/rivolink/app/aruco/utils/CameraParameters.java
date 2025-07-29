package mg.rivolink.app.aruco.utils;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;

public class CameraParameters {
	
	public static Mat createDefaultCameraMatrix(int width, int height) {
		Mat cameraMatrix = Mat.eye(3, 3, CvType.CV_64FC1);
		
		double fx = width * 0.8;
		double fy = height * 0.8;
		double cx = width / 2.0;
		double cy = height / 2.0;
		
		cameraMatrix.put(0, 0, fx);
		cameraMatrix.put(1, 1, fy);
		cameraMatrix.put(0, 2, cx);
		cameraMatrix.put(1, 2, cy);
		
		return cameraMatrix;
	}
	
	public static MatOfDouble createDefaultDistortionCoefficients() {
		return new MatOfDouble(Mat.zeros(5, 1, CvType.CV_64FC1));
	}
	
}
