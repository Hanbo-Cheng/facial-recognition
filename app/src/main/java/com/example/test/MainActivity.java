package com.example.test;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private JavaCameraView cameraView;
    private Button btn1;
    private boolean isBack = true;
    private Mat mRgba;
    private CascadeClassifier classifierFace, classifierEye;
    private int mAbsoluteFaceSize = 0;
    private Scalar faceRectColor = new Scalar(255, 0, 0, 255);
    private int fps = 3;
    private List<Rect> facesCache = new ArrayList<>();
    private SensorManager sensorManager=null;
    private Sensor sensor=null;
    private float x,y,z;



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                    Mat imageMat=new Mat();
                } break;
                default:
                {
                    try {
                        super.onManagerConnected(status);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        sensorManager = (SensorManager)this.getSystemService(SENSOR_SERVICE);
        sensor=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (!OpenCVLoader.initDebug())
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        else {
            try {
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//        String str = "x=" + x + "; y=" + y + "; z=" + z;
//        Toast.makeText(getApplicationContext(), str, Toast.LENGTH_LONG).show();
        //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        initWindowSettings();
        setContentView(R.layout.activity_main);
        initView();
        initPermission();
        initClassifierFace();
    }

    private SensorEventListener lsn  = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            x = event.values[SensorManager.DATA_X];
            y = event.values[SensorManager.DATA_Y];
            z = event.values[SensorManager.DATA_Z];
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub
        }
    };

    /**
     * 人脸
     * 初始化级联分类器
     */
    public void initClassifierFace() {
        try {
            //读取存放在raw的文件
            InputStream is = getResources()
                    .openRawResource(R.raw.lbpcascade_frontalface_improved);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface_improved.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            //通过classifier来操作人脸检测， 在外部定义一个CascadeClassifier classifier
//            classifierFace.load( cascadeFile.getAbsolutePath() );
            String str=cascadeFile.getAbsolutePath();
            classifierFace = new CascadeClassifier(cascadeFile.getAbsolutePath());
            cascadeFile.delete();
            cascadeDir.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 初始化视图
     */
    private void initView() {
        cameraView = findViewById(R.id.camera);
        //添加监听
        cameraView.setCvCameraViewListener(cvListener);
        btn1 = findViewById(R.id.btn1);
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBack) {
                    cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
                    isBack = false;
                } else {
                    cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
                    isBack = true;
                }

                if (cameraView != null) {
                    cameraView.disableView();
                    cameraView.enableView();
                }
            }
        });
    }

    /**
     * 隐藏标题栏和全屏
     */
    private void initWindowSettings() {
        //隐藏ActionBar
        getSupportActionBar().hide();
        //全屏显示
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //屏幕常量
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //强制横屏显示
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    /**
     * 权限申请
     */
    private void initPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1234);
        } else {
            if (cameraView != null) {
                cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
                cameraView.enableView();
            }
        }
    }


    /**
     * 摄像头数据监听
     */
    private CameraBridgeViewBase.CvCameraViewListener2 cvListener = new CameraBridgeViewBase.CvCameraViewListener2() {
        @Override
        public void onCameraViewStarted(int width, int height) {
            mRgba = new Mat();
        }

        @Override
        public void onCameraViewStopped() {
            mRgba.release();
        }

        /*
            实时接收摄像头数据
            然后调用classifier人脸检测
            对视频每一帧进行处理
         */
        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
            mRgba = inputFrame.rgba();
            boolean isrotate=false;
            sensorManager.registerListener(lsn, sensor, SensorManager.SENSOR_DELAY_GAME);
//            String str = "x=" + x + "; y=" + y + "; z=" + z;
//            Toast.makeText(getApplicationContext(), str, Toast.LENGTH_LONG).show();
            // 旋转输入帧
//            if (isBack) {
//                Core.rotate(mRgba, mRgba, Core.ROTATE_90_COUNTERCLOCKWISE);
//                Core.flip(mRgba, mRgba, 1);
//            } else {
//                Core.rotate(mRgba, mRgba, Core.ROTATE_90_CLOCKWISE);
//            }

            if(x<y){
                isrotate=true;
                Core.rotate(mRgba, mRgba, Core.ROTATE_90_COUNTERCLOCKWISE);
            }
            Core.flip(mRgba, mRgba, 1);
            //隔3帧进行一次人脸检测
            if (fps == 3) {
                float mRelativeFaceSize = 0.2f;

                int height = mRgba.rows();
                if (Math.round(height * mRelativeFaceSize) > 0) {
                    mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
                }
            }
            MatOfRect faces = new MatOfRect();
            if (classifierFace != null) {
                classifierFace.detectMultiScale(mRgba, faces, 1.05, 2, 2,
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
            }
            //把检测到的人脸坐标存在全局变量
            facesCache = faces.toList();
            //标识归0
            fps = 0;



            //使用缓存的人脸坐标信息进行绘制
            for (Rect rect : facesCache) {
                Imgproc.rectangle(mRgba, rect.tl(), rect.br(), faceRectColor, 4);
            }
            //标识进1
            fps++;

            if(isrotate){
                Core.rotate(mRgba, mRgba, Core.ROTATE_90_COUNTERCLOCKWISE);
            }

            return mRgba;
        }
    };
    private String getOrientation(){
        if(this.getResources().getConfiguration().orientation== Configuration.ORIENTATION_PORTRAIT){
            return "portrait";
        }
        else{
            return "landscape";
        }
    }
    @Override
    public void onResume(){
        //3.注册SensorEventListener，调用registerListener()方法来注册,第三个参数是检测的灵敏精确度,
        sensorManager.registerListener(lsn, sensor, SensorManager.SENSOR_DELAY_GAME);
        super.onResume();
    }
    @Override
    public void onPause(){
        sensorManager.unregisterListener(lsn);
        super.onPause();
    }


}
