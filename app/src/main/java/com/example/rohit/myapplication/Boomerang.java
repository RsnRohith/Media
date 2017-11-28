package com.example.rohit.myapplication;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractSequentialList;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * Created by rohit on 22/11/17.
 */

public class Boomerang {

    private int WIDTH, HEIGHT;
    private int total_frame_count = 0;
    private static final int OUTPUT_FRAME_RATE = 30;
    private static final int KEY_FRAME_RATE = 10;
    private MediaFormat inputMediaFormat;
    private MediaExtractor mediaExtractor;
    private MediaCodec mDecoder;
    private MediaCodec mEncoder;
    private String filepath;
    private int VIDEO_TRACK_INDEX;
    private long VIDEO_DURATION;
    private static final String MIME_TYPE = "video/avc";


    private ArrayList<ByteBufferMeta> decoded_buffer_info = new ArrayList<>();
    private MediaMuxer mediaMuxer;
    private HashMap<Integer,Long> timeStamp = new HashMap<>();
    private ArrayList<Long> time_delta = new ArrayList<>();

    public Boomerang(String filepath) {
        this.filepath = filepath;
        initMediaExtractor();
    }

    private void initMediaExtractor() {
        mediaExtractor = new MediaExtractor();

        try {
            mediaExtractor.setDataSource(filepath);
            printmediaTracks();
            selectrequiredTrack();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initializeDecoder() {
        try {
            mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
            mDecoder.configure(inputMediaFormat, null, null, 0);
            mDecoder.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void start() {
        decodeVideo();
        encodeVideo();
    }

    private void decodeVideo() {

        initializeDecoder();

        int skipFrame = 3;
        int frame_counter = 0;

        boolean endOfDecoding = false;
        boolean inputExtracted = false;

        ByteBuffer inputBuffer;
        ByteBuffer[] decoderInputBuffers = mDecoder.getInputBuffers();
        ByteBuffer[] decoderOutputBuffers = mDecoder.getOutputBuffers();
        MediaCodec.BufferInfo decodedBufferInfo = new MediaCodec.BufferInfo();


        while (!endOfDecoding) {
            if (!inputExtracted) {
                int decoderInputIndex = mDecoder.dequeueInputBuffer(-1);
                if (decoderInputIndex >= 0) {
                    total_frame_count++;
                    inputBuffer = decoderInputBuffers[decoderInputIndex];
                    inputBuffer.clear();
                    int sampleData = mediaExtractor.readSampleData(inputBuffer, 0);
                    timeStamp.put(total_frame_count, mediaExtractor.getSampleTime());

                    if (sampleData < 0) {
                        inputExtracted = true;
                        mDecoder.queueInputBuffer(decoderInputIndex, 0, 0, VIDEO_DURATION + 50000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        int flag = mediaExtractor.getSampleFlags();
                        Log.d("FLAGGGG",""+mediaExtractor.getSampleTrackIndex()+" "+flag);
                        mDecoder.queueInputBuffer(decoderInputIndex, 0, sampleData, mediaExtractor.getSampleTime(), flag);
                        mediaExtractor.advance();
                    }
                }
            }

            int decodeOutputIndex = mDecoder.dequeueOutputBuffer(decodedBufferInfo, 10000);

            switch (decodeOutputIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    decoderOutputBuffers = mDecoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    //mediaCodecDecoder.getOutputFormat();
                    //encoderThread.setFormat( mediaCodecDecoder.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;
                default:
                    ByteBuffer byteBuffers = decoderOutputBuffers[decodeOutputIndex];
                    byteBuffers.position(decodedBufferInfo.offset);
                    byteBuffers.limit(decodedBufferInfo.offset + decodedBufferInfo.size);


                    ByteBuffer decoded_output_buffer = ByteBuffer.allocate(byteBuffers.remaining());
                    decoded_output_buffer.put(byteBuffers);
                    decoded_output_buffer.position(0);
                    //byteBuffers.position(decodedBufferInfo.offset);

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    info.set(0, decodedBufferInfo.size, decodedBufferInfo.presentationTimeUs, decodedBufferInfo.flags);


                    if ((decodedBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        frame_counter = 0;
                        endOfDecoding = true;
                    }

                    if (frame_counter == 0) {
                        //consider this frame for encoding
                        decoded_buffer_info.add(new ByteBufferMeta(info, decoded_output_buffer));
                    }

                    frame_counter = (frame_counter + 1) % skipFrame;


                    mDecoder.releaseOutputBuffer(decodeOutputIndex, false);


                    break;
            }

        }
        mediaExtractor.release();
        updateKeyFrameRate();
        //printInfo();
        stopDecoder();

    }

    private void printmediaTracks() {
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Log.d("mime", " " + mime);
        }
    }

    private void selectrequiredTrack() {
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            inputMediaFormat = mediaExtractor.getTrackFormat(i);
            String mime = inputMediaFormat.getString(MediaFormat.KEY_MIME);
            if (MIME_TYPE.equals(mime)) {
                if (inputMediaFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    VIDEO_DURATION = inputMediaFormat.getLong(MediaFormat.KEY_DURATION);
                }
                if (inputMediaFormat.containsKey(MediaFormat.KEY_WIDTH)) {
                    WIDTH = inputMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                    HEIGHT = inputMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                }

                VIDEO_TRACK_INDEX = i;
                mediaExtractor.selectTrack(i);
                return;
            }
        }
    }

    private void updateKeyFrameRate() {

        int frame_count = 0;

        for (int i = 0; i < decoded_buffer_info.size() - 1; i++) {
            MediaCodec.BufferInfo info = decoded_buffer_info.get(i).getBufferinfo();
            if ((frame_count % KEY_FRAME_RATE) == 0) {
                info.set(info.offset, info.size, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_SYNC_FRAME);
            } else {
                info.set(info.offset, info.size, info.presentationTimeUs, 0);
            }
            frame_count++;
        }

    }

    private void printInfo() {
        for (int i = 0; i < decoded_buffer_info.size(); i++) {
            MediaCodec.BufferInfo info = decoded_buffer_info.get(i).getBufferinfo();
            Log.d("AfterDecoded", "" + (i + 1) + " " + info.offset + " " + info.flags + " " + info.presentationTimeUs);
        }
    }


    private void stopDecoder() {
        mDecoder.stop();
        mDecoder.release();
        mDecoder = null;
    }

    private void stopEncoder() {
        mEncoder.stop();
        mEncoder.release();
        mEncoder = null;
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void encodeVideo() {

        initializeEncoder();

        int encoderInputIndex;
        int encoderOutputIndex;
        ByteBuffer[] encoderInputBuffers;
        ByteBuffer[] encoderOutputBuffers;

        encoderInputBuffers = mEncoder.getInputBuffers();
        encoderOutputBuffers = mEncoder.getOutputBuffers();

        boolean endOfEncoding = false;
        boolean once_done = false;
        int decodedDataIndex = 0;
        long previous_time_stamp = 0;
        long delta_time;
        int presentationTime_temp = 1;
        int frame_counter = 0;

        MediaCodec.BufferInfo encodedBufferInfo = new MediaCodec.BufferInfo();

        while (!endOfEncoding) {

            if (decodedDataIndex < decoded_buffer_info.size()) {

                if (decodedDataIndex == (decoded_buffer_info.size() - 2) && once_done) {
                    decodedDataIndex++;
                    continue;
                }

                encoderInputIndex = mEncoder.dequeueInputBuffer(10000);

                if (encoderInputIndex >= 0) {

                    ByteBuffer inputBuffer = encoderInputBuffers[encoderInputIndex];
                    inputBuffer.clear();

                    ByteBufferMeta byteBufferMeta;

                    byteBufferMeta = decoded_buffer_info.get(decodedDataIndex);
                    inputBuffer.put(byteBufferMeta.getByteBuffer());
                    inputBuffer.position(0);
                    byteBufferMeta.getByteBuffer().position(0);
                    
                    
                    if (!once_done) {


                        mEncoder.queueInputBuffer(encoderInputIndex, 0, byteBufferMeta.getBufferinfo().size, timeStamp.get(presentationTime_temp), byteBufferMeta.getBufferinfo().flags);

                        frame_counter++;

                        previous_time_stamp = timeStamp.get(presentationTime_temp);

                        if (presentationTime_temp != 1) {
                            delta_time = previous_time_stamp - timeStamp.get(presentationTime_temp - 1);
                            time_delta.add(0, delta_time);
                        }

                        presentationTime_temp++;
                    } else {

                        byteBufferMeta.setByteBuffer(null);
                        if (decodedDataIndex == (decoded_buffer_info.size() - 1)) {
                            previous_time_stamp = previous_time_stamp + 50000;
                        } else {
                            previous_time_stamp = previous_time_stamp + time_delta.get(decodedDataIndex);
                        }
                        int flag = byteBufferMeta.getBufferinfo().flags;

                        if ((flag == MediaCodec.BUFFER_FLAG_SYNC_FRAME) || (flag == 0)) {
                            flag = ((frame_counter % KEY_FRAME_RATE) == 0) ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0;
                        }

                        mEncoder.queueInputBuffer(encoderInputIndex, 0, byteBufferMeta.getBufferinfo().size, previous_time_stamp, flag);

                        frame_counter++;
                    }

                    decodedDataIndex++;

                    if ((decodedDataIndex == decoded_buffer_info.size() - 1) && !once_done) {
                        reverse();
                        decodedDataIndex = 0;
                        once_done = true;
                    }
                }
            }


            encoderOutputIndex = mEncoder.dequeueOutputBuffer(encodedBufferInfo, 10000);
            switch (encoderOutputIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d("EncodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                    encoderOutputBuffers = mEncoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d("EncodeActivity", "New format " + mEncoder.getOutputFormat());
                    initializeMuxer();
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d("EncodeActivity", "dequeueOutputBuffer timed out!");
                    break;
                default:
                    if(mediaMuxer == null) {
                        initializeMuxer();
                    }
                    if ((MediaCodec.BUFFER_FLAG_CODEC_CONFIG & encodedBufferInfo.flags) != 0) {
                        mEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                        break;
                    }
                    if ((MediaCodec.BUFFER_FLAG_END_OF_STREAM & encodedBufferInfo.flags) != 0) {
                        endOfEncoding = true;
                    }
                    if (encodedBufferInfo.size != 0) {
                        MediaCodec.BufferInfo temp_info = new MediaCodec.BufferInfo();
                        temp_info.set(encodedBufferInfo.offset, encodedBufferInfo.size, encodedBufferInfo.presentationTimeUs, encodedBufferInfo.flags);
                        mediaMuxer.writeSampleData(VIDEO_TRACK_INDEX, encoderOutputBuffers[encoderOutputIndex], temp_info);
                        mEncoder.releaseOutputBuffer(encoderOutputIndex, false);
                    }
            }
        }

        stopEncoder();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void initializeMuxer() {
        try {
            mediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory().getPath() + "/resample22.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        VIDEO_TRACK_INDEX = mediaMuxer.addTrack(mEncoder.getOutputFormat());
        mediaMuxer.setOrientationHint(90);
        mediaMuxer.start();
    }


    private void initializeEncoder() {
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            MediaFormat outputMediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT);
            outputMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 5000000/*calcBitRate()*/);
            outputMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_FRAME_RATE);
            outputMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_RATE);

            MediaCodecInfo info = selectCodec(MIME_TYPE);
            int colorFormat = selectColorFormat(info);

            if (colorFormat != 0) {
                outputMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            } else {
                // what to do?
            }
            mEncoder.configure(outputMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();
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


    private void reverse() {

        int size = (decoded_buffer_info.size() - 2);


        for (int i = 0; i < (size / 2); i++) {
            ByteBuffer tempBuffer = decoded_buffer_info.get(i).getByteBuffer();
            decoded_buffer_info.get(i).setByteBuffer(decoded_buffer_info.get(size - i - 1).getByteBuffer());
            decoded_buffer_info.get(size - i - 1).setByteBuffer(tempBuffer);
        }

    }


}
