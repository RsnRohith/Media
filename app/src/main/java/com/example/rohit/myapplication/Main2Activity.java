package com.example.rohit.myapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Main2Activity extends AppCompatActivity {

    MediaMuxer muxer;
    private int KEY_FRAME_RATE;
    private String filepath;
    private boolean inputEOS;
    private  int frame_count;
    private double duration;
    private int video_track_index;
    MediaCodec.BufferInfo bufferInfo;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        frame_count = 0;
        filepath = Environment.getExternalStorageDirectory().getPath()+"/Hike"+"/sample4.mp4";

        decodeFile(filepath);

    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void decodeFile(String filepath){

        MediaExtractor extractor = new MediaExtractor();



        try {
            extractor.setDataSource(filepath);
            int numTracks = extractor.getTrackCount();

            printmediaTracks(extractor, numTracks);
            selectrequiredTrack(extractor, numTracks);
            ByteBuffer inputBuffer = ByteBuffer.allocate(1024 * 1024);
            muxer = new MediaMuxer(Environment.getExternalStorageDirectory().getPath()+"/Hike"+"/resample4.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaFormat mediaFormat = extractor.getTrackFormat(video_track_index);
            //arrangeHeight(mediaFormat);

            video_track_index = muxer.addTrack(mediaFormat);

            bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.offset = 0;
            muxer.start();

            while (!inputEOS) {

                int sampleData = extractor.readSampleData(inputBuffer,0);
                Log.d("sampleData",""+sampleData);


                if(sampleData != -1){
                    frame_count++;
                    //byte[] frame = new byte[sampleData];
                    //inputBuffer.get(frame,0,sampleData);
                    bufferInfo.size = sampleData;
                    bufferInfo.presentationTimeUs = extractor.getSampleTime();
                    bufferInfo.flags = extractor.getSampleFlags();



                    Log.d("codecFlags",""+bufferInfo.flags+" "+frame_count);
                    muxer.writeSampleData(video_track_index, inputBuffer, bufferInfo);

                }

                if(sampleData < 0){
                    inputEOS = true;
                }
                Log.d("PTIME"," "+extractor.getSampleTime());

                if(!inputEOS) {
                    extractor.advance();
                }

            }

            extractor.release();
            muxer.stop();
            muxer.release();

            extractor = null;
            muxer = null;

            /*
            How to get key frames of video file in android

            bufferinfo sync frame
             */



            Log.d("Result ","duration "+duration);
            Log.d("Result ","no. of frames "+frame_count);
            Log.d("Result ","frames per second "+(frame_count/duration));
            Log.d("Result ","KEY_FRAME_RATE "+KEY_FRAME_RATE);

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void arrangeHeight(MediaFormat mediaFormat) {
        int height,width;
        height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);

        if(height < width){
            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT,width);
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH,height);
        }
    }


    private void selectrequiredTrack(MediaExtractor extractor, int numTracks) {
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if(format.containsKey(MediaFormat.KEY_FRAME_RATE)){
                    KEY_FRAME_RATE = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                }
                if(format.containsKey(MediaFormat.KEY_DURATION)){
                    duration = format.getLong(MediaFormat.KEY_DURATION);
                    duration = duration / Math.pow(10,6);
                }
                video_track_index = i;
                if(format.containsKey(MediaFormat.KEY_I_FRAME_INTERVAL))
                    Log.d("Result ","key frame rate "+format.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));
                extractor.selectTrack(i);
                return;
            }
        }
    }

    private void printmediaTracks(MediaExtractor extractor, int numTracks) {
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Log.d("mime"," "+mime);
        }
        extractor.getSampleTime();
    }



}
