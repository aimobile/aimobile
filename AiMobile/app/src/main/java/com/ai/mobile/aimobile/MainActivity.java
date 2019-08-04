package com.ai.mobile.aimobile;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import static java.lang.Math.round;


public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private String[] permissions = {Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.DISABLE_KEYGUARD};

    private static final String TAG = "aimobile_app";
    private static final String TAGMAIN = "aimobile_app_main";
    private static final String MYCAMERA2 = "MYCAMERA2";
    private static final String CAMERAID = "1";
    File sdcard = Environment.getExternalStorageDirectory();
    String modelDir = sdcard.getAbsolutePath() + "/aimobile";
    String modelProto = modelDir + "/aimobile_ssd.pt";
    String modelBinary = modelDir + "/aimobile_ssd.caffemodel";
    private TextureView mTextureView;
    private TextView tv;
    private TextView tv_debug;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private CameraDevice mCameraDevice;
    private ImageReader mImageReader;
    private Size mPreviewSize;
    private ImageView iv_preview;
    private CaptureRequest.Builder mPreviewBuilder;
    private CaptureRequest.Builder mCaptureBuilder;
    private CameraCaptureSession mSession;
    private AiMobile aiMobile = null;
    long start_handler=0;
    long end_handler=0;
    int channel = 3;
    int height = 300;
    int width = 300;
    int preview_height = 720;
    int preview_width  = 960;
    float[] meanValues = {104f, 117f, 123f};
    int[] pixels = new int[width*height];
    float[] bgr = new float[channel*width*height];
    List<ROIData> imgqueue = new Vector<ROIData>();
    Bitmap preview_bm;
    boolean nnrun_flag=false;

    private Handler ivhandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            start_handler=System.currentTimeMillis(); //获取结束时间

            System.out.println("time_handler1： "+(start_handler-end_handler)+"ms");
            float fps = (1000.0f/(start_handler-end_handler));
            fps = (float) ((int)(fps*100)/100f);
            end_handler=System.currentTimeMillis(); //获取结束时间
            switch (msg.what){
                case 1:
                    ROIData roidata = (ROIData)msg.obj;
                    float[]  result = roidata.getRoidata();
                    float xmin=0;
                    float ymin=0;
                    float xmax=0;
                    float ymax=0;
                    int[] rgb_buffer = roidata.getrgb();

                    Bitmap ori_bm = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
                    ori_bm.setPixels(rgb_buffer, 0,640, 0, 0, 640,480);
                    roidata = null;
                    if (result[2] != -1) {
                        Log.i(TAGMAIN,"get ROI image start");
                        Log.i(TAGMAIN,"result_ori:" + result[2] + ","+ result[3] + "," + result[4] + "," + result[5] + "," + result[6]);
                        xmin = round(result[3] * ori_bm.getWidth());
                        ymin = round(result[4] * ori_bm.getHeight());
                        xmax = round(result[5] * ori_bm.getWidth());
                        ymax = round(result[6] * ori_bm.getHeight());
                        Canvas canvas = new Canvas(ori_bm);
                        //图像上画矩形
                        Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);//不填充
                        paint.setStrokeWidth(8);  //线的宽度
                        canvas.drawRect(xmin, ymin, xmax, ymax, paint);
                        Log.i(TAGMAIN,"get ROI image end");
                    }
                    Log.i(TAGMAIN,"rotaingImageView start1");
                    Bitmap mybitmap = rotaingImageView(270,ori_bm);
                    ori_bm = null;
                    Log.i(TAGMAIN,"rotaingImageView end1");
                    Log.i(TAGMAIN,"rotaingImageView start2");
                    Matrix m = new Matrix();
                    m.setScale(-1, 1);//水平翻转
////            m.setScale(1, -1);//垂直翻转
//            //生成的翻转后的bitmap
                    Bitmap bm1 = Bitmap.createBitmap(mybitmap, 0, 0,mybitmap.getWidth(),mybitmap.getHeight(), m, true);

                    Bitmap preview_bm_after = bm1.createScaledBitmap(bm1,preview_height,preview_width,false);
                    mybitmap = null;
                    bm1 = null;
                    iv_preview.setImageBitmap(preview_bm_after);
                    String debug_info = "prob="+result[2]+
                                        "\n"+"xmin="+xmin+ " ymin="+ ymin+
                                        "\n"+"xmax="+xmax+" ymax="+ymax+
                                        "\n"+"人脸宽高比="+(xmax-xmin)/(ymax-ymin);
                    tv_debug.setText(debug_info);
                    tv.setText("FPS:"+fps);

                    Log.i(TAGMAIN,"rotaingImageView end2");
                    break;
                default:
                    break;
            }




        }
    };
    static {
        System.loadLibrary("aimobile");
        System.loadLibrary("aimobile_jni");
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // 无标题栏
        setContentView(R.layout.activity_main);
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{permission}, 321);
            }
        }
        System.out.println("aimobilemodel:"+modelProto+","+modelBinary);
//        AssetManager am = this.getAssets();

        try {
            aiMobile = new AiMobile(modelProto,modelBinary);
        } catch (Exception e) {
            e.printStackTrace();
        }

        aiMobile.setNumThreads(4);
        initLooper();
        mTextureView = (TextureView) findViewById(R.id.tv_previewView);
        tv = (TextView) findViewById(R.id.tv_fps);
        tv_debug = (TextView) findViewById(R.id.tv_debug);
        iv_preview = (ImageView)findViewById(R.id.iv_preview);
        mTextureView.setSurfaceTextureListener(this);
    }

    //很多过程都变成了异步的了，所以这里需要一个子线程的looper
    // 创建一个会话是要花费几百毫秒的耗时操作，因为它需要配置相机设备的内置管道并且还要配置存储缓冲区来给需要图片数据的目标发送图片数据。因此,开启新线程。
    private void initLooper() {
        mHandlerThread = new HandlerThread(MYCAMERA2);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "----------onSurfaceTextureAvailable()----------");
        try {
            //获得CameraManager
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            //获得属性CameraCharacteristics：CameraDevice的属性描述类,描述相机硬件设备支持可用的和输出的参数
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(CAMERAID);
            //获取摄像头支持的配置属性
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
            // 获取最大的预览尺寸
            mPreviewSize = getMaxSize(outputSizes);
            Log.e(TAG, "mPreviewSize======" + mPreviewSize);
            // 创建一个ImageReader对象，用于获取摄像头的图像数据
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);
            //打开相机
            cameraManager.openCamera(CAMERAID, mCameraDeviceStateCallback, mHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // -----------------------获取最大的预览尺寸--------------------
    private Size getMaxSize(Size[] outputSizes) {
        Size sizeMax = null;
        if (outputSizes != null) {
            sizeMax = outputSizes[0];
            for (Size size : outputSizes) {
                Log.i(TAG,
                        "------- size.getWidth()===" + size.getWidth() + ",size.getHeight()===" + size.getHeight());
                if (size.getWidth() * size.getHeight() > sizeMax.getWidth() * sizeMax.getHeight()) {
                    sizeMax = size;
                }
            }
        }
        Log.i(TAG,
                "------- sizeMax.getWidth()===" + sizeMax.getWidth() + ",sizeMax.getHeight()===" + sizeMax.getHeight());
//        return sizeMax;
        return  new Size(640,480);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "--------onSurfaceTextureSizeChanged-------------");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "--------onSurfaceTextureDestroyed-------------");
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.e(TAG, "--------onSurfaceTextureUpdated-------------");
    }

    // --------------第一次回调，打开相机后回调，可执行Camera.createCaptureSession()------------------------
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.e("TAG", "CameraDevice-----onOpened()");
            try {
                mCameraDevice = camera;
                startPreview(mCameraDevice);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        //摄像头断开连接时激发该方法
        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.e("TAG", "CameraDevice-----onDisconnected()");
            camera.close();
            mCameraDevice = null;
            mImageReader.close();
            mImageReader = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e("TAG", "CameraDevice-----onError():" + error);
            camera.close();
            mCameraDevice = null;
            mImageReader.close();
            mImageReader = null;
            MainActivity.this.finish();
        }
    };

    private void startPreview(CameraDevice camera) throws CameraAccessException {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            Log.i(TAG,"startPreview start");
            // 设置适当的大小和格式去匹配相机设备的可支持的大小和格式
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Log.i(TAG, "mPreviewSize:" + mPreviewSize.getWidth() + "*" + mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            try {
                mPreviewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            // 设置预览数据传输到TextureView或SurfaceView
    //        mPreviewBuilder.addTarget(surface);

            long startTime=System.currentTimeMillis();
            //设置预览数据输出到ImageReader
            mPreviewBuilder.addTarget(mImageReader.getSurface());
    //        long endTime=System.currentTimeMillis(); //获取结束时间
    //
    //        System.out.println("time： "+(endTime-startTime)+"ms");


            // 预览输出JPEG方向设置
    //        mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270);
            // 创建由相机设备的输出surface组成的拍照会话
            camera.createCaptureSession(Arrays.asList(mImageReader.getSurface()), mSessionStateCallback, mHandler);
            long endTime=System.currentTimeMillis(); //获取结束时间
            Log.i(TAG,"startPreview end");
            System.out.println("time_setting： "+(endTime-startTime)+"ms");
    }

    // --------------第二次回调，创建Session后回调，可执行CameraCaptureSession.setRepeatingRequest()------------------------
    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            Log.e("TAG", "CameraCaptureSession-----onConfigured()");
            // 开启红外灯光
          /*  if (!isLedOn) {
                InfraredLedManager.setInfraredLedOn();
                isLedOn = !isLedOn;
            }
*/
            Log.i(TAG,"get img start");
            long start=System.currentTimeMillis(); //获取结束时间

            try {
                mSession = session;
                // 通过mPreviewBuilder.set(key, value)方法，设置拍照参数
                // 设置预览为黑白效果
                // mPreviewBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_MONO);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                // mPreviewBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK,true);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 开启预览
                mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            long end=System.currentTimeMillis(); //获取结束时间
            Log.i(TAG,"get img end");
            Log.i(TAG,"time_camera： "+(end-start)+"ms");
            System.out.println("time_camera： "+(end-start)+"ms");
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.e("TAG", "CameraCaptureSession-----onConfigureFailed()");
            mSession = session;
            mSession.close();
            mSession = null;
            mCameraDevice.close();
            mCameraDevice = null;
            mImageReader.close();
            mImageReader = null;
        }
    };

    // ------------------------第三次回调，拍照后回调-------------------------
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        // 拍照完成后回调
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            Log.e("completed", "mSessionCaptureCallback, onCaptureCompleted");
            long start=System.currentTimeMillis(); //获取结束时间
            try {

                //设置自动对焦
                mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                //设置自动曝光模式
                mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                // 重新打开预览
                session.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            long end=System.currentTimeMillis(); //获取结束时间

            System.out.println("time_repeate： "+(end-start)+"ms");
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult partialResult) {
            Log.e("TAG", "mSessionCaptureCallback, onCaptureProgressed");
        }

        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                    android.hardware.camera2.CaptureFailure failure) {
            Log.e("TAG", "mSessionCaptureCallback, onCaptureFailed" + request + ":" + failure);
            mSession.close();
            mSession = null;
            mCameraDevice.close();
            mCameraDevice = null;
            mImageReader.close();
            mImageReader = null;
        }
    };

//    // -------------------------拍摄，照相功能---------------------------------------------
//    public void onCapture(View view) {
//        try {
//            Log.e("TAG", "onCapture");
//            if (mCameraDevice == null) {
//                Log.e("TAG", "mCameraDevice==null");
//                return;
//            }
//
//            mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//            //设置拍照数据输出到ImageReader
//            mCaptureBuilder.addTarget(mImageReader.getSurface());
//            //设置自动对焦
//            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//            //设置自动曝光模式
//            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
//            //停止连续取景
//            mSession.stopRepeating();
//            //设置拍照数据JPEG方向
////            mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
//            //拍照方法
//            mSession.capture(mCaptureBuilder.build(), mSessionCaptureCallback, mHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
    public static Bitmap rotaingImageView(int angle , Bitmap bitmap) {
        //旋转图片 动作
        Matrix matrix = new Matrix();;
        matrix.postRotate(angle);
        System.out.println("angle2=" + angle);
        // 创建新的图片
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return resizedBitmap;
    }

    public class ROIData{
        float[] roiData;
        int[] rgbBuffer;
        public ROIData(){

        }
        public void setROIData(float[] roi_data,int[] rgb){
            roiData = roi_data;
            rgbBuffer = rgb;
        }
        public float[] getRoidata(){
            return roiData;
        }
        public int[] getrgb(){
            return rgbBuffer;
        }

    }

    // -----------------------获得数据监听，用于保存拍照数据-------------------------
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        // 获得数据时回调
        @Override
        public void onImageAvailable(ImageReader reader) {
            //获取捕获的照片数据
            Log.i(TAG,"acquireNextImage start");
            Image img = reader.acquireNextImage();
            Log.i(TAG,"image width="+img.getWidth()+"image height="+img.getHeight());
            Log.i(TAG,"acquireNextImage end");
            Log.i(TAG,"convert start");
            Image.Plane[] plane = img.getPlanes();
            byte[][] mYUVBytes = new byte[plane.length][];
            for (int i = 0; i < plane.length; ++i) {
                mYUVBytes[i] = new byte[plane[i].getBuffer().capacity()];
            }
            int[] mRGBBytes = new int[img.getWidth() * img.getHeight()];

            for (int i = 0; i < plane.length; ++i) {
                plane[i].getBuffer().get(mYUVBytes[i]);
            }
            final int yRowStride = plane[0].getRowStride();
            final int uvRowStride = plane[1].getRowStride();
            final int uvPixelStride = plane[1].getPixelStride();
            Log.i(TAG,"convert start1");
            aiMobile.convertYUV420ToARGB8888(
                mYUVBytes[0],
                mYUVBytes[1],
                mYUVBytes[2],
                mRGBBytes,
                img.getWidth(),
                img.getHeight(),
                yRowStride,
                uvRowStride,
                uvPixelStride,
                false);
            Log.i(TAG,"convert end1");
            float[] result;
            ROIData roidata = new ROIData();
            Log.i(TAG,"predictImage start");
            result = aiMobile.predictImage(mRGBBytes, 5);
            Log.i(TAG,"predictImage end");

            roidata.setROIData(result,mRGBBytes);

            Log.i(TAG,"send msg start");
            Message msg = new Message();
            msg.what = 1;
            msg.obj = roidata;
            ivhandler.sendMessage(msg);
            Log.i(TAG,"send msg end");

            img.close();
        }

    };
}