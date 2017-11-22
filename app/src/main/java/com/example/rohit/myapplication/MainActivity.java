package com.example.rohit.myapplication;


import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class MainActivity extends AppCompatActivity {


    DecoderThread decoderThread;
    EncoderThread encoderThread;


    static MediaMuxer muxer;
    static long duration;

    static int KEY_FRAME_RATE = 10;
    static int remove = 0;
    static ArrayList<ByteBufferMeta> decoded_buffer_info = new ArrayList();
    static int total_frames;
    static HashMap<Integer,Long> timeStamp = new HashMap<>();
    static ArrayList<Long> temp_time_stamp = new ArrayList();
    static ArrayList<Long> time_delta = new ArrayList<>();
    static ArrayList<Long> final_time_stamp = new ArrayList<>();




    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        decoderThread = new DecoderThread();


        encoderThread = new EncoderThread(decoderThread.getDecoderMediaCodec(), this);


        encoderThread.setInitialData(decoderThread.getVideo_track_index(), decoderThread.getMediaFormat(), decoderThread.getMime());

        decoderThread.setEncoderThread(encoderThread);

        Thread decoder = new Thread(decoderThread);
        decoder.start();

    }

}
