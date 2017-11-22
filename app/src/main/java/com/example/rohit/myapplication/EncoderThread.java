package com.example.rohit.myapplication;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

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
    int presentationTime = 0;
    int presentationTime_temp = 1;
    boolean once_done = false;
    int frame_index = 0;


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
        long previous_time_stamp = 0;
        long delta_time;

        while (!endOfEncoding) {

            if (decodedDataIndex < MainActivity.decoded_buffer_info.size()) {

                if(decodedDataIndex == (MainActivity.decoded_buffer_info.size()-2) && once_done){
                    decodedDataIndex++;
                    continue;
                }

                encoderInputIndex = mediaCodecEncoder.dequeueInputBuffer(10000);

                if (encoderInputIndex >= 0) {

                    ByteBuffer inputBuffer = encodeoinputBuffers[encoderInputIndex];
                    inputBuffer.clear();

                    ByteBufferMeta byteBufferMeta;

                    byteBufferMeta = MainActivity.decoded_buffer_info.get(decodedDataIndex);
                    inputBuffer.put(byteBufferMeta.getByteBuffer());
                    inputBuffer.position(0);
                    byteBufferMeta.getByteBuffer().position(0);

                    if (!once_done) {

                        MainActivity.final_time_stamp.add(MainActivity.timeStamp.get(presentationTime_temp));

                        mediaCodecEncoder.queueInputBuffer(encoderInputIndex, 0, byteBufferMeta.getBufferinfo().size, MainActivity.timeStamp.get(presentationTime_temp), byteBufferMeta.getBufferinfo().flags);

                        frame_index++;

                        previous_time_stamp = MainActivity.timeStamp.get(presentationTime_temp);

                        if (presentationTime_temp != 1) {
                            delta_time = previous_time_stamp - MainActivity.timeStamp.get(presentationTime_temp - 1);
                            MainActivity.time_delta.add(0, delta_time);
                        }

                        presentationTime_temp++;
                    } else {
                        if (decodedDataIndex == (MainActivity.decoded_buffer_info.size()-1)) {
                            previous_time_stamp = previous_time_stamp + 50000;
                        }
                        else{
                            previous_time_stamp = previous_time_stamp + MainActivity.time_delta.get(decodedDataIndex);
                        }
                        MainActivity.final_time_stamp.add(previous_time_stamp);
                        int flag = byteBufferMeta.getBufferinfo().flags;

                        if((flag == MediaCodec.BUFFER_FLAG_SYNC_FRAME) || (flag == 0)){
                            flag=  ((frame_index % MainActivity.KEY_FRAME_RATE) == 0)?MediaCodec.BUFFER_FLAG_SYNC_FRAME:0;
                        }

                        mediaCodecEncoder.queueInputBuffer(encoderInputIndex, 0, byteBufferMeta.getBufferinfo().size, previous_time_stamp,flag);
                        byteBufferMeta.setByteBuffer(null);
                        frame_index++;
                    }

                    decodedDataIndex++;

                    if ((decodedDataIndex == MainActivity.decoded_buffer_info.size() - 1) && !once_done) {
                        reverse();
                        decodedDataIndex = 0;
                        once_done = true;
                    }
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
                    MainActivity.muxer.setOrientationHint(270);
                    MainActivity.muxer.start();
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d("EncodeActivity", "dequeueOutputBuffer timed out!");
                    break;
                default:
                    Log.d("EncodeActivityinfo",""+encodedBufferInfo.offset+" "+encodedBufferInfo.flags+" "+encodedBufferInfo.presentationTimeUs+" "+encodedBufferInfo.size);
                    if (MainActivity.muxer == null) {
                        track_index = MainActivity.muxer.addTrack(mediaCodecEncoder.getOutputFormat());

                        MainActivity.muxer.setOrientationHint(0);
                        MainActivity.muxer.start();
                    }
                    if ((MediaCodec.BUFFER_FLAG_END_OF_STREAM & encodedBufferInfo.flags) != 0) {
                        endOfEncoding = true;
                        //mediaCodecEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                        MediaCodec.BufferInfo temp_info = new MediaCodec.BufferInfo();
                        Log.d("presentation_time", "" + presentationTime);
                        temp_info.set(encodedBufferInfo.offset, encodedBufferInfo.size, encodedBufferInfo.presentationTimeUs,encodedBufferInfo.flags);
                        MainActivity.muxer.writeSampleData(track_index, encodeoutputBuffers[encoderOutputIndex], temp_info);
                        mediaCodecEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                        break;
                    }
                    if ((MediaCodec.BUFFER_FLAG_CODEC_CONFIG & encodedBufferInfo.flags) != 0) {
                        mediaCodecEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                        break;
                    }
                    if (encodedBufferInfo.size != 0) {
                        MediaCodec.BufferInfo temp_info = new MediaCodec.BufferInfo();
                        Log.d("presentation_time", "" + presentationTime);
                        temp_info.set(encodedBufferInfo.offset, encodedBufferInfo.size, encodedBufferInfo.presentationTimeUs, encodedBufferInfo.flags);
                        presentationTime++;
                        MainActivity.temp_time_stamp.add(temp_info.presentationTimeUs);
                        MainActivity.muxer.writeSampleData(track_index, encodeoutputBuffers[encoderOutputIndex], temp_info);
                        mediaCodecEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                    }
                    break;
            }
        }
        //MediaCodec.BufferInfo final_frame_info= new MediaCodec.BufferInfo();
        //final_frame_info.presentationTimeUs = MainActivity.duration+50000;
        //final_frame_info.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;

        //final_frame_info.set(0,10, MainActivity.duration+50000,MediaCodec.BUFFER_FLAG_END_OF_STREAM);

        //MainActivity.muxer.writeSampleData(track_index,ByteBuffer.allocate(10),final_frame_info);


        stopEncoder();
    }


    private MediaFormat mediFormat;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void configureMediaCodecs() {
        try {
            int colorFormat;
            mediaCodecEncoder = MediaCodec.createEncoderByType("video/avc");

            mediFormat = MediaFormat.createVideoFormat("video/avc", mediaFormat.getInteger(MediaFormat.KEY_WIDTH), mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
            mediFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
            mediFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            mediFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, MainActivity.KEY_FRAME_RATE);

            MediaCodecInfo info = selectCodec("video/avc");

            colorFormat = selectColorFormat(info);


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

    private static int selectColorFormat(MediaCodecInfo codecInfo) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
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
            //case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
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


    private void reverse() {

        int size = (MainActivity.decoded_buffer_info.size() - 2);


        for (int i = 0; i < (size / 2); i++) {
            ByteBuffer tempBuffer = MainActivity.decoded_buffer_info.get(i).getByteBuffer();
            MainActivity.decoded_buffer_info.get(i).setByteBuffer(MainActivity.decoded_buffer_info.get(size - i - 1).getByteBuffer());
            MainActivity.decoded_buffer_info.get(size - i - 1).setByteBuffer(tempBuffer);
        }

    }

    private void reverse(boolean endOfDecoding) {

        int size = MainActivity.decoded_buffer_info.size()-2;


        for (int i = 0; i < size/2; i++) {
            if(i==size-i-1)
                break;


            ByteBufferMeta tempBuffer = MainActivity.decoded_buffer_info.get(i);
            ByteBufferMeta tempBuffer1 = MainActivity.decoded_buffer_info.get(size-i-2);

            MainActivity.decoded_buffer_info.set(i,tempBuffer1);
            MainActivity.decoded_buffer_info.set(size-i-2,tempBuffer);


            MediaCodec.BufferInfo tempBuffer2 = MainActivity.decoded_buffer_info.get(i).getBufferinfo();
            MediaCodec.BufferInfo tempBuffer3 = MainActivity.decoded_buffer_info.get(size-i-2).getBufferinfo();


            long presentationtime;
            int flag;

            presentationtime = tempBuffer2.presentationTimeUs;
            flag = tempBuffer2.flags;


            tempBuffer2.set(tempBuffer2.offset,tempBuffer2.size,tempBuffer3.presentationTimeUs,tempBuffer3.flags);
            tempBuffer3.set(tempBuffer3.offset,tempBuffer3.size,presentationtime,flag);

            /*

            ByteBufferMeta tempBuffer = MainActivity.buffer_info_temp.get(i);
            ByteBufferMeta tempBuffer1 = MainActivity.buffer_info_temp.get(size-i-1);

            ByteBuffer b_new = ByteBuffer.allocate(tempBuffer.getByteBuffer().remaining());
            ByteBuffer b1_new = ByteBuffer.allocate(tempBuffer1.getByteBuffer().remaining());

            //tempBuffer.getByteBuffer().put(b_new);
            b_new.put(tempBuffer.getByteBuffer());
            b1_new.put(tempBuffer1.getByteBuffer());
            //tempBuffer1.getByteBuffer().put(b1_new);

//
//            ByteBuffer b1 = tempBuffer.getByteBuffer();
//            ByteBuffer b2 = tempBuffer1.getByteBuffer();

            tempBuffer.setByteBuffer(b1_new);
            tempBuffer1.setByteBuffer(b_new);

            */

        }
    }

    public void setFormat(MediaFormat format) {
        this.mediaFormat = format;
    }
}


//   4951133


/*
4920244

4878566

4836933



 */