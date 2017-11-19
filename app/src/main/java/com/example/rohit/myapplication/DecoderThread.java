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
        filepath = Environment.getExternalStorageDirectory().getPath() + "/Hike" + "/sample12.mp4";

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
                    //MainActivity.deocodeoutputBuffers = mediaCodecDecoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d("DecodeActivity", "New format " + mediaCodecDecoder.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
                    break;
                default:
/*
                        ByteBuffer byteBuffers = decodeoutputBuffers[decodeOutputIndex];
                        byteBuffers.position(decodedBufferInfo.offset);
                        byteBuffers.limit(decodedBufferInfo.offset+decodedBufferInfo.size);

                        byte[] temp = new byte[byteBuffers.remaining()];
                        byteBuffers.get(temp);

                        ByteBuffer last = ByteBuffer.wrap(temp);
                        last.position(decodedBufferInfo.offset);
                        last.limit(decodedBufferInfo.offset+decodedBufferInfo.size);


                        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                        info.set(decodedBufferInfo.offset,decodedBufferInfo.size,decodedBufferInfo.presentationTimeUs,decodedBufferInfo.flags);

                        MainActivity.buffer_info_temp.add(new ByteBufferMeta(info, last));
                        mediaCodecDecoder.releaseOutputBuffer(decodeOutputIndex, false);

                        if (MainActivity.buffer_info_temp.size() == MainActivity.KEY_FRAME_RATE) {
                            MainActivity.consumingIndex = 0;
                            reverse(endOfDecoding);
                            MainActivity.isQueueEmptying = true;
                        }

                        if ((decodedBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            MainActivity.consumingIndex = 0;
                            endOfDecoding = true;
                            reverse(endOfDecoding);
                            MainActivity.isQueueEmptying = true;
                        }


                        */

                    ByteBuffer byteBuffers = decodeoutputBuffers[decodeOutputIndex];
                    byteBuffers.position(decodedBufferInfo.offset);
                    byteBuffers.limit(decodedBufferInfo.offset + decodedBufferInfo.size);

                    byte[] temp = new byte[byteBuffers.remaining()];
                    byteBuffers.get(temp);

                    ByteBuffer last = ByteBuffer.wrap(temp);
                    last.position(decodedBufferInfo.offset);
                    last.limit(decodedBufferInfo.offset + decodedBufferInfo.size);

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    info.set(decodedBufferInfo.offset, decodedBufferInfo.size, decodedBufferInfo.presentationTimeUs, decodedBufferInfo.flags);

                    MainActivity.decoded_buffer_info.add(new ByteBufferMeta(info, last));
                    mediaCodecDecoder.releaseOutputBuffer(decodeOutputIndex, false);

                    if ((decodedBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        endOfDecoding = true;
                    }

                    break;
            }

        }

        MainActivity.total_frames = frame_count;

        mediaExtractor.release();

        ByteBufferMeta last = MainActivity.decoded_buffer_info.get(MainActivity.decoded_buffer_info.size()-1);
        MainActivity.decoded_buffer_info.remove(MainActivity.decoded_buffer_info.size()-1);

        reverse();

        MainActivity.decoded_buffer_info.add(last);

        Log.d("Result ", "duration " + duration);
        Log.d("Result ", "no. of frames " + frame_count);
        Log.d("Result ", "frames per second " + (frame_count / duration));
        Log.d("Result ", "KEY_FRAME_RATE " + KEY_FRAME_RATE);

        Log.d("SAMPLETIME", "" + duration);

        Thread encoder = new Thread(encoderThread);
        encoder.start();

    }

    private void reverse() {

        int size = (MainActivity.decoded_buffer_info.size());
        ;

        for (int i = 0; i < size/2; i++) {
            ByteBuffer tempBuffer = MainActivity.decoded_buffer_info.get(i).getByteBuffer();
            MainActivity.decoded_buffer_info.get(i).setByteBuffer(MainActivity.decoded_buffer_info.get(size - i - 1).getByteBuffer());
            MainActivity.decoded_buffer_info.get(size - i - 1).setByteBuffer(tempBuffer);
        }
    }

    private void selectrequiredTrack() {
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            mediaFormat = mediaExtractor.getTrackFormat(i);
            mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    KEY_FRAME_RATE = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
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


    public void setEncoderThread(EncoderThread encoderThread) {
        this.encoderThread = encoderThread;
    }



}
