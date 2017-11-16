package com.example.rohit.myapplication;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by rohit on 10/11/17.
 */

public class DecoderThread implements Runnable {

    private final String filepath;
    private int KEY_FRAME_RATE;
    private boolean inputExtracted;
    private  int frame_count;
    private long duration;
    private int video_track_index;
    private MediaCodec mediaCodecDecoder;
    private boolean endOfDecoding;
    EncoderThread encoderThread;
    private MediaFormat mediaFormat;
    private MediaExtractor mediaExtractor;
    private String mime;




    public DecoderThread(){
        frame_count = 0;
        mediaExtractor = new MediaExtractor();
        filepath = Environment.getExternalStorageDirectory().getPath() + "/Hike" + "/sample10.mp4";

        try {
            mediaExtractor.setDataSource(filepath);
            printmediaTracks();
            selectrequiredTrack();
            configureMediaCodecs();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    int getVideo_track_index(){
        return video_track_index;
    }

    MediaFormat getMediaFormat(){
        return mediaFormat;
    }

    String getMime(){
        return mime;
    }


    @Override
    public void run() {



        ByteBuffer inputBuffer;

        MediaCodec.BufferInfo decodedBufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer[] decodeinputBuffers = mediaCodecDecoder.getInputBuffers();
        MainActivity.deocodeoutputBuffers = mediaCodecDecoder.getOutputBuffers();

        int flag;
        long presentationTime;

        while (!endOfDecoding) {
            if (!inputExtracted) {
                int decoderInputIndex = mediaCodecDecoder.dequeueInputBuffer(10000);
                if (decoderInputIndex >= 0) {
                    inputBuffer = decodeinputBuffers[decoderInputIndex];
                    int sampleData = mediaExtractor.readSampleData(inputBuffer, 0);

                    if(sampleData < 0) {
                        inputExtracted = true;
                        Log.d("DecodeActivity", "stopextractinginput");
                        mediaCodecDecoder.queueInputBuffer(decoderInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                    else{
                        frame_count++;

                        flag = mediaExtractor.getSampleFlags();
                        presentationTime = mediaExtractor.getSampleTime();


                        mediaCodecDecoder.queueInputBuffer(decoderInputIndex, 0, sampleData,presentationTime , flag);

                        Log.d("encodedBufferInfosize","********************"+sampleData);
                        Log.d("DecodeActivity","extractinginput");


                        Log.d("SAMPLETIME",""+frame_count +" "+flag+" "+presentationTime);

                        mediaExtractor.advance();
                    }
                }
            }

            if(!MainActivity.isQueueEmptying){
                int decodeOutputIndex = mediaCodecDecoder.dequeueOutputBuffer(decodedBufferInfo, 10000);

                switch (decodeOutputIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                        //MainActivity.deocodeoutputBuffers = mediaCodecDecoder.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d("DecodeActivity", "New format " + mediaCodecDecoder.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
                        break;
                    default:
                        MediaCodec.BufferInfo temp_buffer_Info = new MediaCodec.BufferInfo();

                        temp_buffer_Info.set(decodedBufferInfo.offset,decodedBufferInfo.size,decodedBufferInfo.presentationTimeUs,decodedBufferInfo.flags);

                        MainActivity.bufferInfo.add(temp_buffer_Info);
                        MainActivity.decoderOutputBufferIndex.add(decodeOutputIndex);

                        MainActivity.consumingIndex++;

                        if ((decodedBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d("DecodeActivity", "video decoder: EOS");
                            MainActivity.isQueueEmptying = true;
                            endOfDecoding  =true;
                        }

                        if(MainActivity.consumingIndex == 2){
                            Log.d("Decoderrrrrr",""+MainActivity.bufferInfo.get(0).presentationTimeUs+"   "+MainActivity.bufferInfo.get(1).presentationTimeUs);
                            Log.d("Decoderrrrrr",""+MainActivity.bufferInfo.get(0).flags+"   "+MainActivity.bufferInfo.get(1).flags);

                            MainActivity.isQueueEmptying = true;
                        }

                        Log.d("DecodeActivity", "buffer set" + frame_count);
                        Log.d("encodedBufferInfosize","***************"+ decodedBufferInfo.size+" "+ decodedBufferInfo.flags+" "+ decodedBufferInfo.presentationTimeUs+" "+ decodedBufferInfo.offset);

                        break;
                }
            }
        }

        mediaExtractor.release();


        Log.d("Result ", "duration " + duration);
        Log.d("Result ", "no. of frames " + frame_count);
        Log.d("Result ", "frames per second " + (frame_count / duration));
        Log.d("Result ", "KEY_FRAME_RATE " + KEY_FRAME_RATE);

        Log.d("SAMPLETIME",""+duration);

    }

    private void selectrequiredTrack() {
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            mediaFormat = mediaExtractor.getTrackFormat(i);
            mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if(mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)){
                    KEY_FRAME_RATE = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                }
                if(mediaFormat.containsKey(MediaFormat.KEY_DURATION)){
                    duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
                    MainActivity.duration = duration;
                    //duration = duration / Math.pow(10,6);
                }
                video_track_index = i;
                if(mediaFormat.containsKey(MediaFormat.KEY_I_FRAME_INTERVAL))
                    Log.d("Result ","key frame rate "+mediaFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));
                mediaExtractor.selectTrack(i);

                return;
            }
        }
    }

    private void configureMediaCodecs() {
        try {
            mediaCodecDecoder = MediaCodec.createDecoderByType(mime);
            // mediaCodecEncoder = MediaCodec.createEncoderByType(mime);

            mediaCodecDecoder.configure(mediaFormat,null,null,0);

            //mediaCodecEncoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

            mediaCodecDecoder.start();
            //mediaCodecEncoder.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void printmediaTracks() {
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Log.d("mime"," "+mime);
        }
    }


    private void stopDecoder(){
        mediaCodecDecoder.stop();
        mediaCodecDecoder.release();
        mediaCodecDecoder = null;
    }

    MediaCodec getDecoderMediaCodec(){
        return mediaCodecDecoder;
    }



}
