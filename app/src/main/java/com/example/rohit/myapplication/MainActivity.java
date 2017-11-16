package com.example.rohit.myapplication;


import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

public class MainActivity extends AppCompatActivity {


    DecoderThread decoderThread;
    EncoderThread encoderThread;

    static int consumingIndex = 0;
    static boolean isQueueEmptying = false;
    static ByteBuffer[] deocodeoutputBuffers;
    static ArrayList<MediaCodec.BufferInfo> bufferInfo = new ArrayList<>();
    static ArrayList<Integer> decoderOutputBufferIndex = new ArrayList<>();
    static MediaMuxer muxer;
    static long duration;



    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        decoderThread = new DecoderThread();



        encoderThread = new EncoderThread(decoderThread.getDecoderMediaCodec(),this);


        encoderThread.setInitialData(decoderThread.getVideo_track_index(),decoderThread.getMediaFormat(),decoderThread.getMime());

        Thread decoder = new Thread(decoderThread);
        decoder.start();

        Thread encoder = new Thread(encoderThread);
        encoder.start();

    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    void stopMuxer(){

    }





}
