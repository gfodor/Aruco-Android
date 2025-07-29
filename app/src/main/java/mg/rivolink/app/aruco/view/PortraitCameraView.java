package mg.rivolink.app.aruco.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.AttributeSet;
import android.util.Log;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;

@TargetApi(21)
public class PortraitCameraView extends JavaCameraView {

    private static final String TAG = "PortraitCameraView";
    
    private Mat mCameraMatrix;
    private MatOfDouble mDistCoeffs;
    
    public interface CameraIntrinsicsListener {
        void onCameraIntrinsicsAvailable(Mat cameraMatrix, MatOfDouble distCoeffs);
    }
    
    private CameraIntrinsicsListener mIntrinsicsListener;

    public PortraitCameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public PortraitCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public void setCameraIntrinsicsListener(CameraIntrinsicsListener listener) {
        mIntrinsicsListener = listener;
    }

    @Override
    protected boolean connectCamera(int width, int height) {
        Log.d("ArucoDebug", "PortraitCameraView connectCamera called - " + width + "x" + height);
        
        // Call parent connection first
        boolean result = super.connectCamera(width, height);
        Log.d("ArucoDebug", "super.connectCamera() returned: " + result);
        
        if (result) {
            // For now, create default camera intrinsics
            // We'll add Camera2 intrinsics extraction later once the basic camera works
            createDefaultIntrinsics();
        }
        
        return result;
    }
    
    private void createDefaultIntrinsics() {
        Log.d("ArucoDebug", "Creating default camera intrinsics");
        
        // Create a default camera matrix - we'll improve this later
        mCameraMatrix = Mat.eye(3, 3, CvType.CV_64FC1);
        
        // Use reasonable default values for a phone camera
        double fx = 800.0; // rough estimate for focal length
        double fy = 800.0;
        double cx = 320.0; // rough estimate for principal point
        double cy = 240.0;
        
        mCameraMatrix.put(0, 0, fx);
        mCameraMatrix.put(1, 1, fy);
        mCameraMatrix.put(0, 2, cx);
        mCameraMatrix.put(1, 2, cy);
        
        Log.d("ArucoDebug", "Using default camera intrinsics: fx=" + fx + ", fy=" + fy + 
                ", cx=" + cx + ", cy=" + cy);
        
        // Set distortion coefficients to zero
        mDistCoeffs = new MatOfDouble(Mat.zeros(5, 1, CvType.CV_64FC1));
        
        if (mIntrinsicsListener != null) {
            mIntrinsicsListener.onCameraIntrinsicsAvailable(mCameraMatrix, mDistCoeffs);
        }
    }
    
    public Mat getCameraMatrix() {
        return mCameraMatrix;
    }
    
    public MatOfDouble getDistortionCoefficients() {
        return mDistCoeffs;
    }
    
    @Override
    public void enableView() {
        Log.d("ArucoDebug", "PortraitCameraView enableView() called");
        super.enableView();
        Log.d("ArucoDebug", "PortraitCameraView enableView() completed");
    }
    
    @Override
    public void disableView() {
        Log.d("ArucoDebug", "PortraitCameraView disableView() called");
        super.disableView();
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        
        // Force camera connection if layout is complete and camera isn't connected yet
        if (changed && (r-l) > 0 && (b-t) > 0) {
            post(new Runnable() {
                @Override
                public void run() {
                    // Force a camera connection attempt
                    connectCamera(getWidth(), getHeight());
                }
            });
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}