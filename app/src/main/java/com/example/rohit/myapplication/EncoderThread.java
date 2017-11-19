package com.example.rohit.myapplication;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Created by rohit on 10/11/17.
 */

public class EncoderThread implements Runnable {


    //private MediaMuxer muxer;
    private MediaCodec mediaCodecEncoder;
    private boolean endOfEncoding;
    private ByteBuffer decodedFrameBuffer;
    private MediaCodec.BufferInfo encodedBufferInfo;
    private int video_track_index;
    private DecoderThread decoderThread;
    private MediaFormat mediaFormat;
    private String mime;
    MediaCodec decoder;
    Context mContext;
    private int track_index;

    // requires bytrebuffer and bufferinfo


    public EncoderThread(MediaCodec decoder, MainActivity mainActivity) {
        this.decoder = decoder;
        this.mContext = mainActivity;
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    void setInitialData(int video_track_index, MediaFormat mediaFormat, String mime) {
        this.mime = mime;
        this.video_track_index = video_track_index;
        this.mediaFormat = mediaFormat;
        configureMediaCodecs();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void run() {


        ByteBuffer[] encodeoutputBuffers, encodeoinputBuffers;
        int encoderInputIndex, encoderOutputIndex;

        encodeoutputBuffers = mediaCodecEncoder.getOutputBuffers();
        encodeoinputBuffers = mediaCodecEncoder.getInputBuffers();



        int decodedDataIndex = 0;

        while (!endOfEncoding) {


            if(decodedDataIndex < MainActivity.decoded_buffer_info.size()) {

                encoderInputIndex = mediaCodecEncoder.dequeueInputBuffer(10000);

                if (encoderInputIndex >= 0) {

                    ByteBuffer inputBuffer = encodeoinputBuffers[encoderInputIndex];
                    inputBuffer.clear();
                    ByteBufferMeta byteBufferMeta = MainActivity.decoded_buffer_info.get(decodedDataIndex);

                    inputBuffer.put(byteBufferMeta.getByteBuffer());
                    mediaCodecEncoder.queueInputBuffer(encoderInputIndex, 0, byteBufferMeta.getBufferinfo().size, byteBufferMeta.getBufferinfo().presentationTimeUs, byteBufferMeta.getBufferinfo().flags);

                    decodedDataIndex++;
                }
            }

            MediaCodec.BufferInfo encodedBufferInfo = new MediaCodec.BufferInfo();
            encoderOutputIndex = mediaCodecEncoder.dequeueOutputBuffer(encodedBufferInfo, 10000);
            switch (encoderOutputIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d("EncodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                    encodeoutputBuffers = mediaCodecEncoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d("EncodeActivity", "New format " + mediaCodecEncoder.getOutputFormat());
                    track_index = MainActivity.muxer.addTrack(mediaCodecEncoder.getOutputFormat());
                    MainActivity.muxer.start();
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d("EncodeActivity", "dequeueOutputBuffer timed out!");
                    break;
                default:
                    if(MainActivity.muxer == null){
                        track_index = MainActivity.muxer.addTrack(mediaCodecEncoder.getOutputFormat());
                        MainActivity.muxer.start();
                    }
                    if ((MediaCodec.BUFFER_FLAG_END_OF_STREAM & encodedBufferInfo.flags) != 0) {
                        endOfEncoding = true;
                        break;
                    }
                    if (encodedBufferInfo.size != 0) {
                        MainActivity.muxer.writeSampleData(track_index,encodeoutputBuffers[encoderOutputIndex],encodedBufferInfo);
                        mediaCodecEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                    }
                    break;
            }


        }
        MediaCodec.BufferInfo final_frame_info= new MediaCodec.BufferInfo();
        final_frame_info.presentationTimeUs = MainActivity.duration+50000;


        MainActivity.muxer.writeSampleData(track_index,ByteBuffer.allocate(10),final_frame_info);



        stopEncoder();
    }




    private MediaFormat mediFormat;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void configureMediaCodecs() {
        try {
            int colorFormat;
            mediaCodecEncoder = MediaCodec.createEncoderByType("video/avc");
            mediFormat = MediaFormat.createVideoFormat("video/avc",  mediaFormat.getInteger(MediaFormat.KEY_WIDTH),mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
            mediFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
            mediFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            mediFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, MainActivity.KEY_FRAME_RATE);

            MediaCodecInfo info = selectCodec("video/avc");

            colorFormat = selectColorFormat(info, "video/avc");


            mediFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            mediaCodecEncoder.configure(mediFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            MainActivity.muxer = new MediaMuxer(Environment.getExternalStorageDirectory().getPath() + "/Hike" + "/resample4.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            //track_index = MainActivity.muxer.addTrack(mediaCodecEncoder.getOutputFormat());

            mediaCodecEncoder.start();
            //MainActivity.muxer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0;   // not reached
    }

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void stopEncoder() {
        mediaCodecEncoder.stop();
        mediaCodecEncoder.release();
        MainActivity.muxer.stop();
        MainActivity.muxer.release();
        mediaCodecEncoder = null;
        MainActivity.muxer = null;
    }


    // Missing codec specific data
    // java.lang.IllegalStateException: Failed to stop the muxer


}


//   4951133


/*
4920244

4878566

4836933



 */