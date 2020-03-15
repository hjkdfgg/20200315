package com.hitsz.autotrack;

import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
//import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceInfo;
import com.arcsoft.face.enums.DetectMode;
import com.guo.android_extend.tools.CameraHelper;
import com.guo.android_extend.widget.CameraFrameData;
import com.guo.android_extend.widget.CameraGLSurfaceView;
import com.guo.android_extend.widget.CameraSurfaceView;
import com.guo.android_extend.widget.CameraSurfaceView.OnCameraListener;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.arcsoft.face.enums.DetectFaceOrientPriority.ASF_OP_ALL_OUT;

public class DetecterActivity extends Fragment implements OnCameraListener, View.OnTouchListener, Camera.AutoFocusCallback {
	private final String TAG = this.getClass().getSimpleName();

	private int mWidth, mHeight, mFormat;
	private CameraSurfaceView mSurfaceView;
	private CameraGLSurfaceView mGLSurfaceView;
	private Camera mCamera;

	MediaRecorder mMediaRecorder = new MediaRecorder();
	private boolean recording=false;
	//AFT_FSDKVersion version = new AFT_FSDKVersion();
	//AFT_FSDKEngine engine = new AFT_FSDKEngine();
	//List<AFT_FSDKFace> result = new ArrayList<>();//在3.0中，取FaceInfo中的值
	List<FaceInfo> result = new ArrayList<>();
	private FaceEngine faceEngine;
	private int afCode = -1;

	Button button1;
	Button button2;
	byte[] ret = new byte[4147200];

	byte[] mImageNV21 = null;
	FaceInfo mAFT_FSDKFace = null;
    MainActivity mainActivity;
	Handler mHandler;
	Handler blueHandler;
    private int right;
    private int bottom;
    private Rect rectangle;
    private String horizon;
    private String vertical;
    //long mill1;
    //long mill2;

	Runnable hide = new Runnable() {
		@Override
		public void run() {
		}
	};

	private  TextView mTextView2;
	//ByteBuffer  globalBuffer = ByteBuffer.allocate(1024);


	@Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState){
        //Toast.makeText(getActivity(), "hhhh", Toast.LENGTH_SHORT).show();
	    return inflater.inflate(R.layout.activity_camera,container,false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mainActivity = (MainActivity) getActivity();
        //Toast.makeText(getActivity(), screenHeight+","+screenWidth, Toast.LENGTH_SHORT).show();
        mHandler = mainActivity.getUiHandler();
        blueHandler = mainActivity.getbHandler();
        mGLSurfaceView = (CameraGLSurfaceView) view.findViewById(R.id.glsurfaceView);
        mGLSurfaceView.setOnTouchListener(this);
        mSurfaceView = (CameraSurfaceView) view.findViewById(R.id.surfaceView);
        mSurfaceView.setOnCameraListener(this);
        mSurfaceView.setupGLSurafceView(mGLSurfaceView, true, true, 270);
        mSurfaceView.debug_print_fps(true, false);
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        mTextView2 = (TextView) view.findViewById(R.id.textView2);
        mWidth = 1280;
        mHeight = 960;
        mFormat = ImageFormat.NV21;

		initEngine();

		button1=(Button)view.findViewById(R.id.bt_start_record);
		button1.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onRecordStart();
			}
		});

		button2=(Button)view.findViewById(R.id.bt_stop_record);
		button2.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onRecordStop();
			}
		});
		//onRecordStart();
		 //record();
      // AFT_FSDKError err = engine.AFT_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.ft_key, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, 16, 5);
       // Log.d(TAG, "AFT_FSDK_InitialFaceEngine =" + err.getCode());
        //err = engine.AFT_FSDK_GetVersion(version);
        //Log.d(TAG, "AFT_FSDK_GetVersion:" + version.toString() + "," + err.getCode());


	}

	private void initEngine() {
		faceEngine = new FaceEngine();
		afCode = faceEngine.init(getActivity().getApplicationContext(), DetectMode.ASF_DETECT_MODE_VIDEO, ASF_OP_ALL_OUT,
				16, 20, FaceEngine.ASF_FACE_DETECT | FaceEngine.ASF_AGE | FaceEngine.ASF_FACE3DANGLE | FaceEngine.ASF_GENDER | FaceEngine.ASF_LIVENESS);
		Log.i(TAG, "initEngine:  init: " + afCode);
		if (afCode != ErrorInfo.MOK) {
			//showToast( getString(R.string.init_failed, afCode));
		}
	}

    @Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		unInitEngine();
		//stopRecord();
		super.onDestroy();
		//onRecordStop();

	}
	private void unInitEngine() {

		if (afCode == 0) {
			afCode = faceEngine.unInit();
			Log.i(TAG, "unInitEngine: " + afCode);
		}
	}
	@Override
	public Camera setupCamera() {
		// TODO Auto-generated method stub

		try {
			Camera.Parameters parameters = mCamera.getParameters();
			parameters.setPreviewSize(mWidth, mHeight);
			parameters.setPreviewFormat(mFormat);

			for( Camera.Size size : parameters.getSupportedPreviewSizes()) {
				Log.d(TAG, "SIZE:" + size.width + "x" + size.height);
			}
			for( Integer format : parameters.getSupportedPreviewFormats()) {
				Log.d(TAG, "FORMAT:" + format);
			}

			List<int[]> fps = parameters.getSupportedPreviewFpsRange();
			for(int[] count : fps) {
				Log.d(TAG, "T:");
				for (int data : count) {
					Log.d(TAG, "V=" + data);
				}
			}
			mCamera.setParameters(parameters);
			//buffer = new byte[mWidth * mHeight * 3 >> 1];
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (mCamera != null) {
			mWidth = mCamera.getParameters().getPreviewSize().width;
			mHeight = mCamera.getParameters().getPreviewSize().height;
		}
		return mCamera;
	}

	@Override
	public void setupChanged(int format, int width, int height) {

	}

	@Override
	public boolean startPreviewLater() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object onPreview(byte[] data, int width, int height, int format, long timestamp) {

		int code = faceEngine.detectFaces(data, width, height, FaceEngine.CP_PAF_NV21, result);
		Log.d(TAG, "AFT_FSDK_FaceFeatureDetect =" + code);
		Log.d(TAG, "Face=" + result.size());

		/*for (AFT_FSDKFace face : result) {
			Log.d(TAG, "Face:" + face.toString());
		}*/
		if (mImageNV21 == null) {
			if (!result.isEmpty()) {
				mAFT_FSDKFace = result.get(0).clone();

				mImageNV21 = data.clone();
			} else {
				mHandler.postDelayed(hide, 500);
			}
		}
		//nv21ToI420(data, width, height, buffer);
		//nv21ToI420(data,mWidth,mHeight);
		//mMediaRecorder.addFrame(nv21ToI420(data,mWidth,mHeight));
		//System.out.println("data.length="+data.length);
		//for (int i=mImageNV21.length-6;i<mImageNV21.length;i++){
		//	System.out.println("mImageNV21"+mImageNV21[i]);
		//}
		NV21ToNV12(mImageNV21,ret,mWidth ,mHeight);

		//for (int i=mImageNV21.length-6;i<mImageNV21.length;i++){
		//	System.out.println("ret"+mImageNV21[i]);
		//}

		//byte[] num=new byte[4147200];;
		mMediaRecorder.addFrame(ret);
		//copy rects
		Rect[] rects = new Rect[result.size()];
		/*mill1 = mill2;
        mill2 = System.currentTimeMillis();
        Log.d(TAG, "onPreview: "+(mill2-mill1));*/
		for (int i = 0; i < result.size(); i++) {
			rects[i] = new Rect(result.get(i).getRect());
		}
		if(result.size()!=0) {
            rectangle = result.get(0).getRect();
            right = (rectangle.left + rectangle.right) / 2;
            bottom = (rectangle.top + rectangle.bottom) / 2;
            mHandler.removeCallbacks(hide);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTextView2.setText("坐标值：" + right + "," + bottom);
                }
            });
            if(bottom > 510){
                if(bottom > 750){
                    horizon = "i";
                }else if(bottom >650){
                    horizon = "h";
                }else if(bottom > 550){
                    horizon = "g";
                }else{
                    horizon = "f";
                }
            }else if(bottom < 390){
                if(bottom < 150){
                    horizon = "e";
                }else if(bottom < 250){
                    horizon = "d";
                }else if(bottom < 350){
                    horizon = "c";
                }else {
                    horizon = "b";
                }
            }else{
                horizon = "a";
            }
            if(right > 660){
                if(right > 1000){
                    vertical = "o";
                }else if(right > 900){
                    vertical = "n";
                }else if(right > 750){
                    vertical = "m";
                }else {
                    vertical = "l";
                }
            }else if(right < 540){
                if(right < 200){
                    vertical = "u";
                }else if(right < 300){
                    vertical = "t";
                }else if(right < 450){
                    vertical = "s";
                }else {
                    vertical = "r";
                }
            }else{
                vertical = "a";
            }
            Message message = new Message();
            message.what = Params.DETECT_CONNECT;
            message.obj = horizon+""+vertical;
            blueHandler.sendMessage(message);
        }
		//clear result.
		result.clear();
		//return the rects for render.
		return rects;

	}

	@Override
	public void onBeforeRender(CameraFrameData data) {

	}

	@Override
	public void onAfterRender(CameraFrameData data) {
		mGLSurfaceView.getGLES2Render().draw_rect((Rect[])data.getParams(), Color.GREEN, 2);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		CameraHelper.touchFocus(mCamera, event, v, this);
		return false;
	}

	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		if (success) {
			Log.d(TAG, "Camera Focus SUCCESS!");
		}
	}

	public void onRecordStart() {
		Log.d(TAG, "onRecordStart SUCCESS!");
		 try{
			mMediaRecorder.setOutputFile("/sdcard/a.mp4");
			mMediaRecorder.setVideoSize(mWidth,
					mHeight);
			mMediaRecorder.prepare();
			mMediaRecorder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
/*转换方法1*/
	public byte[] nv21ToI420(byte[] data, int width, int height) {
		byte[] ret = new byte[4147200];
		int total = width * height;

		ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
		ByteBuffer bufferU = ByteBuffer.wrap(ret, total, total / 4);
		ByteBuffer bufferV = ByteBuffer.wrap(ret, total + total / 4, total / 4);

		bufferY.put(data, 0, total);
		for (int i=total; i<data.length; i+=2) {
			bufferV.put(data[i]);
			bufferU.put(data[i+1]);
		}

		return ret;
	}

	/*转换方法2*/
	private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height){
		if(nv21 == null || nv12 == null)return;
		int framesize = width*height;
		int i = 0,j = 0;
		System.arraycopy(nv21, 0, nv12, 0, framesize);
		for(i = 0; i < framesize; i++){
			nv12[i] = nv21[i];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
            nv12[framesize + j-1] = nv21[j+framesize];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
 			nv12[framesize + j] = nv21[j+framesize-1];
		}
	}

	public void onRecordStop() {
		mMediaRecorder.stop();
	}
}
