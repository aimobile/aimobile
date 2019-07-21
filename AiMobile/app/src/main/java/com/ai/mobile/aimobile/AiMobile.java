package com.ai.mobile.aimobile;

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


    public native float[] predictImage(Object protoBuf, int protoBufSize, Object modelBuf, int modelBufSize, float[] bgr, int k);
    public void setNumThread(int numThreads){
        setNumThreads(numThreads);
    }
    public float[] predictImage(float[] bgr, int k) {
        System.out.println("aimobile protoBufSize:"+protoBufSize);
        System.out.println("aimobile modelBufSize:"+modelBufSize);
        return predictImage(protoBuf, protoBufSize, modelBuf, modelBufSize, bgr, 5);
    }

}

