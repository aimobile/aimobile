package com.ai.mobile.aimobile;

import android.graphics.Bitmap;
import android.media.Image;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class AiMobile {
    ByteBuffer protoBuf;
    int protoBufSize;
    ByteBuffer modelBuf;
    int modelBufSize;

    public AiMobile(String protoPath, String modelPath) {
        File protoFile = new File(protoPath);
        File modelFile = new File(modelPath);
        try {
            FileChannel protofc = new FileInputStream(protoFile).getChannel();
            protoBufSize = (int) protoFile.length();
            protoBuf = ByteBuffer.allocateDirect((int) protoBufSize);

            FileChannel modelfc = new FileInputStream(modelFile).getChannel();
            modelBufSize = (int) modelFile.length();
            modelBuf = ByteBuffer.allocateDirect((int) modelBufSize);
            protofc.read(protoBuf);
            modelfc.read(modelBuf);
            System.out.println("aimobile pbsize:"+protoBufSize);
            System.out.println("aimobile msize:"+modelBufSize);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public native void setNumThreads(int numThreads);
    public native void convertYUV420ToARGB8888(byte[] y,
                                               byte[] u,
                                               byte[] v,
                                               int[] output,
                                               int width,
                                               int height,
                                               int yRowStride,
                                               int uvRowStride,
                                               int uvPixelStride,
                                               boolean halfSize);

    public native float[] predictImage(Object protoBuf, int protoBufSize, Object modelBuf, int modelBufSize, int[] bgr, int k);
//    public void setNumThread(int numThreads){
//        setNumThreads(numThreads);
//    }
    public float[] predictImage(int[] bgr, int k) {
        System.out.println("aimobile protoBufSize:"+protoBufSize);
        System.out.println("aimobile modelBufSize:"+modelBufSize);
        return predictImage(protoBuf, protoBufSize, modelBuf, modelBufSize, bgr, 5);
    }
    private void convertimg(){
//        Image.Plane[] plane = img.getPlanes();
//        byte[][] mYUVBytes = new byte[plane.length][];
//        for (int i = 0; i < plane.length; ++i) {
//            mYUVBytes[i] = new byte[plane[i].getBuffer().capacity()];
//        }
//        int[] mRGBBytes = new int[width * height];
//
//        for (int i = 0; i < plane.length; ++i) {
//            plane[i].getBuffer().get(mYUVBytes[i]);
//        }
//
//        final int yRowStride = plane[0].getRowStride();
//        final int uvRowStride = plane[1].getRowStride();
//        final int uvPixelStride = plane[1].getPixelStride();
//
//        aiMobile.convertYUV420ToARGB8888(
//                mYUVBytes[0],
//                mYUVBytes[1],
//                mYUVBytes[2],
//                mRGBBytes,
//                width,
//                height,
//                yRowStride,
//                uvRowStride,
//                uvPixelStride,
//                false);
//        Bitmap yuvbm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//        yuvbm.setPixels(mRGBBytes, 0,width, 0, 0, width,height);
//        pixels = mRGBBytes;
//        Log.i(TAG,"get rgb end");
//        Bitmap  bitmap=BitmapFactory.decodeByteArray(bitmapByte , 0, bitmapByte.length);
        //            Bitmap yuvbm =  Bitmap.createBitmap(mRGBBytes, 0, 640, 640, 480, Bitmap.Config.ARGB_8888);
////            Bitmap yuvbm = Bitmap.createBitmap(img.getWidth(), img.getHeight(), Bitmap.Config.ARGB_8888);
////            yuvbm.setPixels(mRGBBytes, 0,img.getWidth(), 0, 0, img.getWidth(),img.getHeight());
//            Log.i(TAG,"convert end");
//            Log.i(TAG,"resize start");
//            Bitmap forward_bm = Bitmap.createScaledBitmap(yuvbm,width,height,false);
//            forward_bm.getPixels(pixels,0,width,0,0,width,height);
//            Log.i(TAG,"resize end");
//            Log.i(TAG,"submean start");
//            for(int i = 0; i < pixels.length; i++){
//                int clr = pixels[i];
//                int  red   = (clr & 0x00ff0000) >> 16;  //取高两位
//                int  green = (clr & 0x0000ff00) >> 8; //取中两位
//                int  blue  =  clr & 0x000000ff; //取低两位
//                bgr[i] = blue - meanValues[0];
//                bgr[i+width*height] = green - meanValues[1];
//                bgr[i+2*width*height] = red - meanValues[2];
//            }
//            Log.i(TAG,"submean end");
    }

}

