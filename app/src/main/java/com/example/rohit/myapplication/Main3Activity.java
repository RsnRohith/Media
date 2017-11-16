package com.example.rohit.myapplication;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Main3Activity extends AppCompatActivity {


    private static final long TIMEOUT_USEC = 2500;
    private static final String TAG = "Main3Activity";
    private MediaExtractor extractor;
    private String filePath;
    private MediaFormat inputFormat;
    private String mimeType;
    private int mWidth;
    private int mHeight;
    private MediaCodec encoder;
    private MediaCodec decoder;
    private boolean outputDone, inputDone;
    private int inputChunk;
    private int outputCount;
    private boolean decoderDone;
    MediaCodec.BufferInfo info;
    private ByteBuffer[] encoderOutputBuffers;
    private ByteBuffer[] decoderInputBuffers;
    private MediaMuxer muxer;
    private int track_index;


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);
        filePath = Environment.getExternalStorageDirectory().getPath() + "/Hike" + "/sample2.mp4";
        info = new MediaCodec.BufferInfo();
        doProcess_0();
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    void doProcess_0() {
        extractor = new MediaExtractor();
        try {
            extractor.setDataSource(filePath);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                inputFormat = extractor.getTrackFormat(i);
                String mime = inputFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    mimeType = mime;
                    break;
                }
            }
            mWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
            mHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
            // Create an encoder format that matches the input format.  (Might be able to just
            // re-use the format used to generate the video, since we want it to be the same.)
            MediaFormat outputFormat = MediaFormat.createVideoFormat(mimeType, mWidth, mHeight);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 921600);//921600 288000
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
            encoder = MediaCodec.createEncoderByType(mimeType);
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            // OutputSurface uses the EGL context created by InputSurface.
            decoder = MediaCodec.createDecoderByType(mimeType);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            decoderInputBuffers = decoder.getInputBuffers();
            encoderOutputBuffers = encoder.getOutputBuffers();

            muxer = new MediaMuxer(Environment.getExternalStorageDirectory().getPath() + "/Hike" + "/resample4.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            track_index = muxer.addTrack(outputFormat);
            muxer.start();


            doProcess_1();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    void doProcess_1() {

        outputDone = false;
        inputDone = false;
        decoderDone = false;


        while (!outputDone) {
            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer buffer = decoderInputBuffers[inputBufIndex];
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        inputChunk++;
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        // Copy a chunk of input to the decoder.  The first chunk should have
                        // the BUFFER_FLAG_CODEC_CONFIG flag set.
                        buffer.clear();
                        decoder.queueInputBuffer(inputBufIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();

                        inputChunk++;
                    }
                }
            }
            // Assume output is available.  Loop until both assumptions are false.
            boolean decoderOutputAvailable = !decoderDone;
            boolean encoderOutputAvailable = true;
            while (decoderOutputAvailable || encoderOutputAvailable) {
                // Start by draining any pending output from the encoder.  It's important to
                // do this before we try to stuff any more data in.
                int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);

                Log.d("Hello", "Hello");

                if (encoderStatus ==  MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                    encoderOutputAvailable = false;
                } else if (encoderStatus ==  MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus ==  MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    track_index = muxer.addTrack(newFormat);
                    Log.d(TAG, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                    Log.d(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        Log.d(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                    }
                    // Write the data to the output "file".
                    if (info.size != 0) {
                        Log.d("encoding", "encoding");
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        byte[] data = new byte[encodedData.remaining()];
                        encodedData.get(data);
                        //fStream.Write(data, 0, data.Length);
                        //muxer ..........
                        // outputData.addChunk(encodedData, (int)info.Flags, info.PresentationTimeUs);
                        muxer.writeSampleData(track_index, ByteBuffer.wrap(data), info);
                        outputCount++;
                        Log.d(TAG, "encoder output " + info.size + " bytes");
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(encoderStatus, false);
                    Log.d("encodingdone", "encodingdone");
                }
                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Continue attempts to drain output.
                    continue;
                }
                // Encoder is drained, check to see if we've got a new frame of output from
                // the decoder.  (The output is going to a Surface, rather than a ByteBuffer,
                // but we still get information through BufferInfo.)
                if (!decoderDone) {
                    int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (decoderStatus ==  MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        Log.d(TAG, "no output from decoder available");
                        decoderOutputAvailable = false;
                    } else if (decoderStatus == (int) MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        //decoderOutputBuffers = decoder.GetOutputBuffers();
                        Log.d(TAG, "decoder output buffers changed (we don't care)");
                    } else if (decoderStatus == (int) MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // expected before first buffer of data
                        MediaFormat newFormat = decoder.getOutputFormat();
                        Log.d(TAG, "decoder output format changed: " + newFormat);
                    } else if (decoderStatus < 0) {
                        Log.d(TAG, "unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                    } else { // decoderStatus >= 0
                        Log.d(TAG, "surface decoder given buffer " + decoderStatus + " (size=" + info.size + ")");
                        // The ByteBuffers are null references, but we still get a nonzero
                        // size for the decoded data.
                        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                        // to SurfaceTexture to convert to a texture.  The API doesn't
                        // guarantee that the texture will be available before the call
                        // returns, so we need to wait for the onFrameAvailable callback to
                        // fire.  If we don't wait, we risk rendering from the previous frame.
                        decoder.releaseOutputBuffer(decoderStatus, false);

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderOutputAvailable = false;
                            Log.d(TAG, "decoder stream end");
                            decoderDone = true;
                            //noinspection NewApi
                            int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                            if (inputBufIndex >= 0) {
                                //noinspection NewApi
                                //encoder.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            }
                        }
                    }
                }
            }
        }
        if (inputChunk != outputCount) {
            throw new RuntimeException("frame lost: " + inputChunk + " in, " +
                    outputCount + " out");
        }
        stop();
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    void stop() {
        decoder.stop();
        decoder.release();
        encoder.stop();
        encoder.release();
        muxer.stop();
        muxer.release();
        muxer = null;
    }


}
