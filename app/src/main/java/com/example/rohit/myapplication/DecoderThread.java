package com.example.rohit.myapplication;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Created by rohit on 10/11/17.
 */

public class DecoderThread implements Runnable {

    private final String filepath;
    private int KEY_FRAME_RATE;
    private boolean inputExtracted;
    private int frame_count;
    private long duration;
    private int video_track_index;
    private MediaCodec mediaCodecDecoder;
    private boolean endOfDecoding;
    EncoderThread encoderThread;
    private MediaFormat mediaFormat;
    private MediaExtractor mediaExtractor;
    private String mime;


    DecoderThread() {
        frame_count = 0;
        mediaExtractor = new MediaExtractor();
        filepath = Environment.getExternalStorageDirectory().getPath() + "/Hike" + "/sample15.mp4";

        try {
            mediaExtractor.setDataSource(filepath);
            printmediaTracks();
            selectrequiredTrack();
            configureMediaCodecs();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    int getVideo_track_index() {
        return video_track_index;
    }

    MediaFormat getMediaFormat() {
        return mediaFormat;
    }

    String getMime() {
        return mime;
    }


    @Override
    public void run() {


        ByteBuffer inputBuffer;

        MediaCodec.BufferInfo decodedBufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer[] decodeinputBuffers = mediaCodecDecoder.getInputBuffers();
        ByteBuffer[] decodeoutputBuffers = mediaCodecDecoder.getOutputBuffers();

        int flag;
        long presentationTime;

        while (!endOfDecoding) {
            if (!inputExtracted) {
                int decoderInputIndex = mediaCodecDecoder.dequeueInputBuffer(10000);
                if (decoderInputIndex >= 0) {
                    inputBuffer = decodeinputBuffers[decoderInputIndex];
                    int sampleData = mediaExtractor.readSampleData(inputBuffer, 0);
                    frame_count++;
                    flag = mediaExtractor.getSampleFlags();
                    presentationTime = mediaExtractor.getSampleTime();
                    MainActivity.timeStamp.put(frame_count, presentationTime);

                    if (sampleData < 0) {
                        inputExtracted = true;
                        Log.d("DecodeActivity", "stopextractinginput");
                        mediaCodecDecoder.queueInputBuffer(decoderInputIndex, 0, 0, duration + 50000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        Log.d("SAMPLETIME", "LAST" + flag + " " + presentationTime);
                    } else {
                        mediaCodecDecoder.queueInputBuffer(decoderInputIndex, 0, sampleData, presentationTime, flag);

                        Log.d("encodedBufferInfosize", "********************" + sampleData);
                        Log.d("DecodeActivity", "extractinginput");
                        Log.d("SAMPLETIME", "" + frame_count + " " + flag + " " + presentationTime);

                        mediaExtractor.advance();//4951133
                    }
                }
            }

            int decodeOutputIndex = mediaCodecDecoder.dequeueOutputBuffer(decodedBufferInfo, 10000);

            Log.d("decoderOutput", "decoderOutput" + decodeOutputIndex);
            switch (decodeOutputIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                    decodeoutputBuffers = mediaCodecDecoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d("DecodeActivity", "New format " + mediaCodecDecoder.getOutputFormat());
                    //mediaCodecDecoder.getOutputFormat();
                    //encoderThread.setFormat( mediaCodecDecoder.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
                    break;
                default:
                    ByteBuffer byteBuffers = decodeoutputBuffers[decodeOutputIndex];
                    byteBuffers.position(decodedBufferInfo.offset);
                    //byteBuffers.limit(decodedBufferInfo.offset + decodedBufferInfo.size);


                    ByteBuffer last_buffer = ByteBuffer.allocate(byteBuffers.remaining());
                    last_buffer.put(byteBuffers);
                    last_buffer.position(0);
                    //byteBuffers.position(decodedBufferInfo.offset);

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    info.set(0, decodedBufferInfo.size, decodedBufferInfo.presentationTimeUs, decodedBufferInfo.flags);



                    if ((decodedBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        MainActivity.remove = 0;
                        endOfDecoding = true;
                    }

                    if(MainActivity.remove == 0)
                        MainActivity.decoded_buffer_info.add(new ByteBufferMeta(info, last_buffer));

                    MainActivity.remove = (MainActivity.remove + 1)%2;

                    mediaCodecDecoder.releaseOutputBuffer(decodeOutputIndex, false);


                    break;
            }

        }

        MainActivity.total_frames = frame_count;

        mediaExtractor.release();

        Log.d("Result ", "duration " + duration);
        Log.d("Result ", "no. of frames " + frame_count);
        Log.d("Result ", "frames per second " + (frame_count / duration));
        Log.d("Result ", "KEY_FRAME_RATE " + KEY_FRAME_RATE);

        Log.d("SAMPLETIME", "" + duration);

        //printInfo();

        exchangeIframeRate();

        printInfo();

        Thread encoder = new Thread(encoderThread);
        encoder.start();

    }

    private void exchangeIframeRate() {

        int frame_count = 0;

        for(int i=0;i<MainActivity.decoded_buffer_info.size()-1;i++){
            MediaCodec.BufferInfo info = MainActivity.decoded_buffer_info.get(i).getBufferinfo();
            if((frame_count % MainActivity.KEY_FRAME_RATE) == 0) {
                info.set(info.offset, info.size, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_SYNC_FRAME);
            }
            else{
                info.set(info.offset, info.size, info.presentationTimeUs,0);
            }


            frame_count++;
        }

    }

    private void printInfo() {
        for(int i=0;i<MainActivity.decoded_buffer_info.size();i++){
            MediaCodec.BufferInfo info = MainActivity.decoded_buffer_info.get(i).getBufferinfo();
            Log.d("AfterDecoded",""+(i+1)+" "+info.offset+" "+info.flags+" "+info.presentationTimeUs);
        }
    }

    private void selectrequiredTrack() {
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            mediaFormat = mediaExtractor.getTrackFormat(i);
            mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    KEY_FRAME_RATE = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                    MainActivity.KEY_FRAME_RATE = 10;
                }
                if (mediaFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
                    MainActivity.duration = duration;
                    //duration = duration / Math.pow(10,6);
                }
                video_track_index = i;
                if (mediaFormat.containsKey(MediaFormat.KEY_I_FRAME_INTERVAL))
                    Log.d("Result ", "key frame rate " + mediaFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));
                mediaExtractor.selectTrack(i);

                return;
            }
        }
    }

    private void configureMediaCodecs() {
        try {
            mediaCodecDecoder = MediaCodec.createDecoderByType(mime);
            // mediaCodecEncoder = MediaCodec.createEncoderByType(mime);

            mediaCodecDecoder.configure(mediaFormat, null, null, 0);

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
            Log.d("mime", " " + mime);
        }
    }


    private void stopDecoder() {
        mediaCodecDecoder.stop();
        mediaCodecDecoder.release();
        mediaCodecDecoder = null;
    }

    MediaCodec getDecoderMediaCodec() {
        return mediaCodecDecoder;
    }


    void setEncoderThread(EncoderThread encoderThread) {
        this.encoderThread = encoderThread;
    }



}
