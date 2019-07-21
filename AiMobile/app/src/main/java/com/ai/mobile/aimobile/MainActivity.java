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
import java.util.Arrays;

import static java.lang.Math.round;


public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private String[] permissions = {Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.DISABLE_KEYGUARD};

    private static final String TAG = "aimobile";
    private static final String MYCAMERA2 = "MYCAMERA2";
    private static final String CAMERAID = "1";
    File sdcard = Environment.getExternalStorageDirectory();
    String modelDir = sdcard.getAbsolutePath() + "/aimobile";
    String modelProto = modelDir + "/aimobile_ssd.pt";
    String modelBinary = modelDir + "/aimobile_ssd.caffemodel";
    private TextureView mTextureView;
    private TextView tv;
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
    float[] meanValues = {104f, 117f, 123f};
    int[] pixels = new int[width*height];
    float[] bgr = new float[channel*width*height];
    Bitmap preview_bm;
    boolean nnrun_flag=false;
    private Thread nnthread = new Thread(new Runnable() {
        @Override
        public void run() {
            while (true){
               if(nnrun_flag) {
                   float[] result = aiMobile.predictImage(bgr, 5);
                   if (result[2] != -1) {
                       System.out.println("result_ori:" + result[3] + "," + result[4] + "," + result[5] + "," + result[6]);
                       float xmin = round(result[3] * preview_bm.getWidth());
                       float ymin = round(result[4] * preview_bm.getHeight());
                       float xmax = round(result[5] * preview_bm.getWidth());
                       float ymax = round(result[6] * preview_bm.getHeight());
//                System.out.println("result:" + xmin + "," + ymin + "," + xmax + "," + ymax);
                       Canvas canvas = new Canvas(preview_bm);
                       //图像上画矩形
                       Paint paint = new Paint();
                       paint.setColor(Color.RED);
                       paint.setStyle(Paint.Style.STROKE);//不填充
                       paint.setStrokeWidth(10);  //线的宽度
                       canvas.drawRect(xmin, ymin, xmax, ymax, paint);
                   }
                   Message msg = new Message();
                   msg.what = 1;
                   msg.obj = preview_bm;
                   ivhandler.sendMessage(msg);
               }
            }
        }
    });
    private Handler ivhandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            start_handler=System.currentTimeMillis(); //获取结束时间

            System.out.println("time_handler1： "+(start_handler-end_handler)+"ms");
            float fps = (1000.0f/(start_handler-end_handler));
            fps = (float) ((int)(fps*100)/100f);
            switch (msg.what){
                case 1:
                    Bitmap bm = (Bitmap)msg.obj;
                    iv_preview.setImageBitmap(bm);
                    tv.setText("FPS:"+fps);
                    break;
                default:
                    break;
            }
            end_handler=System.currentTimeMillis(); //获取结束时间

            System.out.println("time_handler： "+(end_handler-start_handler)+"ms");


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
                Log.e("TAG",
                        "------- size.getWidth()===" + size.getWidth() + ",size.getHeight()===" + size.getHeight());
                if (size.getWidth() * size.getHeight() > sizeMax.getWidth() * sizeMax.getHeight()) {
                    sizeMax = size;
                }
            }
        }
        Log.e("TAG",
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

        // 设置适当的大小和格式去匹配相机设备的可支持的大小和格式
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Log.e("TAG", "mPreviewSize:" + mPreviewSize.getWidth() + "*" + mPreviewSize.getHeight());
        Surface surface = new Surface(texture);
        try {
            mPreviewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // 设置预览数据传输到TextureView或SurfaceView
//        mPreviewBuilder.addTarget(surface);
        Log.i("time","start");
        long startTime=System.currentTimeMillis();
        //设置预览数据输出到ImageReader
        mPreviewBuilder.addTarget(mImageReader.getSurface());
//        long endTime=System.currentTimeMillis(); //获取结束时间
//
//        System.out.println("time： "+(endTime-startTime)+"ms");
        Log.i("time","end");

        // 预览输出JPEG方向设置
//        mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270);
        // 创建由相机设备的输出surface组成的拍照会话
        camera.createCaptureSession(Arrays.asList(mImageReader.getSurface()), mSessionStateCallback, mHandler);
        long endTime=System.currentTimeMillis(); //获取结束时间

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
    public static Bitmap getPicFromBytes(byte[] bytes,
                                         BitmapFactory.Options opts) {
        if (bytes != null)
            if (opts != null)
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length,
                        opts);
            else
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        return null;
    }

    public static Bitmap yuv420spToBitmap(byte[] data, int width, int height) {
        Bitmap bitmap = null;
        YuvImage image = new YuvImage(data, ImageFormat.NV21, width, height, null);
        if (image != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, width, height), 100, stream);
            bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return bitmap;
    }
    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    private static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        Log.v(TAG, "get data from " + planes.length + " planes");
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
//            Log.v(TAG, "pixelStride " + pixelStride);
//            Log.v(TAG, "rowStride " + rowStride);
//            Log.v(TAG, "width " + width);
//            Log.v(TAG, "height " + height);
//            Log.v(TAG, "buffer size " + buffer.remaining());
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }

        }
        return data;
    }

    long endTime = 0;
    long startTime = 0;
    // -----------------------获得数据监听，用于保存拍照数据-------------------------
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        // 获得数据时回调
        @Override
        public void onImageAvailable(ImageReader reader) {



            startTime =System.currentTimeMillis();

//            System.out.println("time3： "+(startTime-endTime)+"ms");

//            Log.e("imageData", "mImageReader-----onImageAvailable");
//            Log.e("imageData", "reader:" + reader.getWidth() + "*" + reader.getHeight());
            //获取捕获的照片数据
            Image img = reader.acquireNextImage();
//            Log.e("imageData", "reader.getMaxImages():" + reader.getMaxImages());
//            Log.e("imageData", "image:" + img.getWidth() + "*" + img.getHeight());
            long endTime1=System.currentTimeMillis(); //获取结束时间

            System.out.println("time1： "+(endTime1-startTime)+"ms");


            byte[] data=getDataFromImage(img,COLOR_FormatNV21);
//            Log.e("imageData", "data.length:" +data[100]+":"+data.length);
            Bitmap yuvbm =  yuv420spToBitmap(data,img.getWidth(),img.getHeight());
            Bitmap mybitmap = rotaingImageView(270,yuvbm);
            Matrix m = new Matrix();
            m.setScale(-1, 1);//水平翻转
//            m.setScale(1, -1);//垂直翻转
            //生成的翻转后的bitmap
//            Log.e("imageData", "bitmap length:"+mybitmap.getWidth()+","+mybitmap.getHeight());
            preview_bm = Bitmap.createBitmap(mybitmap, 0, 0,mybitmap.getWidth(),mybitmap.getHeight(), m, true);

            Bitmap resizebmp = Bitmap.createScaledBitmap(preview_bm,width,height,false);
            resizebmp.getPixels(pixels,0,width,0,0,width,height);

            for(int i = 0; i < pixels.length; i++){
                int clr = pixels[i];
                int  red   = (clr & 0x00ff0000) >> 16;  //取高两位
                int  green = (clr & 0x0000ff00) >> 8; //取中两位
                int  blue  =  clr & 0x000000ff; //取低两位
                bgr[i] = blue - meanValues[0];
                bgr[i+width*height] = green - meanValues[1];
                bgr[i+2*width*height] = red - meanValues[2];
//                System.out.println("r="+red+",g="+green+",b="+blue);
            }

            float[] result = aiMobile.predictImage(bgr, 5);

            if (result[2] != -1) {
                System.out.println("result_ori:" + result[3] + "," + result[4] + "," + result[5] + "," + result[6]);
                float xmin = round(result[3] * preview_bm.getWidth());
                float ymin = round(result[4] * preview_bm.getHeight());
                float xmax = round(result[5] * preview_bm.getWidth());
                float ymax = round(result[6] * preview_bm.getHeight());
//                System.out.println("result:" + xmin + "," + ymin + "," + xmax + "," + ymax);
                Canvas canvas = new Canvas(preview_bm);
                //图像上画矩形
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.STROKE);//不填充
                paint.setStrokeWidth(10);  //线的宽度
                canvas.drawRect(xmin, ymin, xmax, ymax, paint);
            }
            else {
            }
            Message msg = new Message();
            msg.what = 1;
            msg.obj = preview_bm;
            ivhandler.sendMessage(msg);
            endTime=System.currentTimeMillis(); //获取结束时间

            System.out.println("time2： "+(endTime-startTime)+"ms");

            img.close();
        }

    };
}