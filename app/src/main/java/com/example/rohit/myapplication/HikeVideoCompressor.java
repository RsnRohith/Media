package com.example.rohit.myapplication;

/**
 * Created by rohit on 14/11/17.
 */

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;


public class HikeVideoCompressor {

    private final static String MIME_TYPE = "video/avc";
    private static String TAG = "HikeVideoCompressor";

    private long startTime;
    private long endTime;
    private int resultWidth;
    private int resultHeight;
    private int rotationValue;
    private int originalWidth;
    private int originalHeight;
    private int bitrate;
    private int rotateRender;
    private File cacheFile;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public File compressVideo(final File inputFile, final VideoEditedInfo videoEditedInfo) {
        return compressVideo(inputFile, videoEditedInfo, null, false);
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public File compressVideo(final File inputFile, final VideoEditedInfo videoEditedInfo, Bitmap overlayBitmap, boolean muteAudio) {
        if (!inputFile.canRead()) {
            return null;
        }

        initParam(videoEditedInfo);

        long videoStartTime = startTime;

        long time = System.currentTimeMillis();

        if (resultWidth > 0 && resultHeight > 0) {
            MediaMuxer mediaMuxer = null;
            MediaExtractor extractor = null;

            try {
                //noinspection NewApi
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                //noinspection NewApi
                extractor = new MediaExtractor();
                //noinspection NewApi
                extractor.setDataSource(inputFile.toString());


                if ((resultWidth != originalWidth || resultHeight != originalHeight) || overlayBitmap != null) {
                    int videoIndex;
                    videoIndex = selectTrack(extractor, false);
                    if (videoIndex >= 0) {
                        MediaCodec decoder = null;
                        MediaCodec encoder = null;

                        try {
                            long videoTime = -1;
                            boolean outputDone = false;
                            boolean inputDone = false;
                            boolean decoderDone = false;
                            int videoTrackIndex = -5;

                            int colorFormat;
                            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
                            Log.d(TAG, "colorFormat = " + colorFormat);
                            //noinspection NewApi
                            extractor.selectTrack(videoIndex);
                            if (startTime > 0) {
                                //noinspection NewApi
                                extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            } else {
                                //noinspection NewApi
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            }
                            //noinspection NewApi
                            MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);
                            //noinspection NewApi
                            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                            //noinspection NewApi
                            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                            //noinspection NewApi
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate != 0 ? bitrate : 921600);
                            //noinspection NewApi
                            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
                            //noinspection NewApi
                            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
                            //noinspection NewApi
                            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                            //noinspection NewApi
                            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

                            //noinspection NewApi
                            encoder.start();
                            //noinspection NewApi
                            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));

                            //noinspection NewApi
                            decoder.configure(inputFormat, null, null, 0);
                            //noinspection NewApi
                            decoder.start();

                            mediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory().getPath() + "/Hike" + "/resample4.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                            videoTrackIndex = mediaMuxer.addTrack(outputFormat);
                            mediaMuxer.start();

                            final int TIMEOUT_USEC = 2500;
                            ByteBuffer[] decoderInputBuffers = null;
                            ByteBuffer[] encoderOutputBuffers = null;

                            //noinspection NewApi
                            decoderInputBuffers = decoder.getInputBuffers();
                            //noinspection NewApi
                            encoderOutputBuffers = encoder.getOutputBuffers();


                            while (!outputDone) {
                                if (!inputDone) {
                                    boolean eof = false;

                                    //noinspection NewApi
                                    int index = extractor.getSampleTrackIndex();

                                    if (index == videoIndex) {
                                        //noinspection NewApi
                                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                        if (inputBufIndex >= 0) {
                                            ByteBuffer inputBuf;

                                            inputBuf = decoderInputBuffers[inputBufIndex];

                                            //noinspection NewApi
                                            int chunkSize = extractor.readSampleData(inputBuf, 0);
                                            if (chunkSize < 0) {
                                                //noinspection NewApi
                                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                inputDone = true;
                                            } else {
                                                //noinspection NewApi
                                                decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                                //noinspection NewApi
                                                extractor.advance();
                                            }
                                        }
                                    } else if (index == -1) {
                                        eof = true;
                                    }
                                    if (eof) {
                                        //noinspection NewApi
                                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                        if (inputBufIndex >= 0) {
                                            //noinspection NewApi
                                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                            inputDone = true;
                                        }
                                    }
                                }

                                boolean decoderOutputAvailable = !decoderDone;
                                boolean encoderOutputAvailable = true;
                                while (decoderOutputAvailable || encoderOutputAvailable) {
                                    //noinspection NewApi
                                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        encoderOutputAvailable = false;
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                        //noinspection NewApi
                                        encoderOutputBuffers = encoder.getOutputBuffers();
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                        //noinspection NewApi
                                        MediaFormat newFormat = encoder.getOutputFormat();
                                        if (videoTrackIndex == -5) {
                                            videoTrackIndex = mediaMuxer.addTrack(newFormat);
                                        }
                                    } else if (encoderStatus < 0) {
                                        throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                                    } else {
                                        ByteBuffer encodedData;
                                        encodedData = encoderOutputBuffers[encoderStatus];

                                        if (encodedData == null) {
                                            throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                                        }
                                        if (info.size > 1) {
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                                mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info);
                                            } else if (videoTrackIndex == -5) {
                                                byte[] csd = new byte[info.size];
                                                encodedData.limit(info.offset + info.size);
                                                encodedData.position(info.offset);
                                                encodedData.get(csd);
                                                ByteBuffer sps = null;
                                                ByteBuffer pps = null;
                                                for (int a = info.size - 1; a >= 0; a--) {
                                                    if (a > 3) {
                                                        if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                                            sps = ByteBuffer.allocate(a - 3);
                                                            pps = ByteBuffer.allocate(info.size - (a - 3));
                                                            sps.put(csd, 0, a - 3).position(0);
                                                            pps.put(csd, a - 3, info.size - (a - 3)).position(0);
                                                            break;
                                                        }
                                                    } else {
                                                        break;
                                                    }
                                                }
                                                //noinspection NewApi
                                                MediaFormat newFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                                                if (sps != null && pps != null) {
                                                    //noinspection NewApi
                                                    newFormat.setByteBuffer("csd-0", sps);
                                                    //noinspection NewApi
                                                    newFormat.setByteBuffer("csd-1", pps);
                                                }
                                                videoTrackIndex = mediaMuxer.addTrack(newFormat);
                                            }
                                        }
                                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                        //noinspection NewApi
                                        encoder.releaseOutputBuffer(encoderStatus, false);
                                    }
                                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        continue;
                                    }

                                    if (!decoderDone) {
                                        //noinspection NewApi
                                        int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                            decoderOutputAvailable = false;
                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                            //ToDo
                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                            //ToDo
                                        } else if (decoderStatus < 0) {
                                            throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                                        } else {

                                            if (endTime > 0 && info.presentationTimeUs >= endTime) {
                                                inputDone = true;
                                                decoderDone = true;
                                                info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                            }
                                            if (startTime > 0 && videoTime == -1) {
                                                if (info.presentationTimeUs < startTime) {
                                                    Log.d(TAG, "drop frame startTime = " + startTime + " present time = " + info.presentationTimeUs);
                                                } else {
                                                    videoTime = info.presentationTimeUs;
                                                }
                                            }
                                            //noinspection NewApi
                                            decoder.releaseOutputBuffer(decoderStatus, false);


                                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                                decoderOutputAvailable = false;
                                                Log.d(TAG, "decoder stream end");

                                                //noinspection NewApi
                                                int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                                if (inputBufIndex >= 0) {
                                                    //noinspection NewApi
                                                    encoder.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                }

                                            }
                                        }
                                    }
                                }
                            }
                            if (videoTime != -1) {
                                videoStartTime = videoTime;
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "Exception2 : " + e);
                            e.printStackTrace();
                            /*
                             * If any exception occur while compressing video, need to send the original video. So returning null which means compression failed.
                             */
                            return null;
                        }
                        //noinspection NewApi
                        extractor.unselectTrack(videoIndex);

                        if (decoder != null) {
                            //noinspection NewApi
                            decoder.stop();
                            //noinspection NewApi
                            decoder.release();
                        }
                        if (encoder != null) {
                            //noinspection NewApi
                            encoder.stop();
                            //noinspection NewApi
                            encoder.release();
                        }

                    }
                } else {
                    long videoTime = readAndWriteTrack(extractor, mediaMuxer, info, startTime, endTime, cacheFile, false);
                    if (videoTime != -1) {
                        videoStartTime = videoTime;
                    }
                }
                if (!muteAudio)
                    readAndWriteTrack(extractor, mediaMuxer, info, videoStartTime, endTime, cacheFile, true);
            } catch (final Exception e) {
                cacheFile = null;
                Log.d(TAG, "Exception3 : " + e);
                e.printStackTrace();
            } finally {
                if (extractor != null) {
                    //noinspection NewApi
                    extractor.release();
                }
                if (mediaMuxer != null) {
                    try {
                        mediaMuxer.stop();
                    } catch (Exception e) {
                        Log.d(TAG, "Exception4 : " + e);
                        e.printStackTrace();
                    }
                    mediaMuxer = null;
                }
                Log.d(TAG, "time = " + (System.currentTimeMillis() - time));
            }
        } else {
            return null;
        }
        return cacheFile;
    }


    private void initParam(VideoEditedInfo editor) {
        startTime = editor.startTime;
        endTime = editor.endTime;
        resultWidth = editor.resultWidth;
        resultHeight = editor.resultHeight;
        rotationValue = editor.rotationValue;
        originalWidth = editor.originalWidth;
        originalHeight = editor.originalHeight;
        bitrate = editor.bitrate;
        rotateRender = 0;
        cacheFile = editor.destFile;

        if (Build.VERSION.SDK_INT > 20) {
            if (rotationValue == 90) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 270;
            } else if (rotationValue == 180) {
                rotateRender = 180;
                rotationValue = 0;
            } else if (rotationValue == 270) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 90;
            }
        }
    }

    @TargetApi(16)
    private int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private long readAndWriteTrack(MediaExtractor extractor, MediaMuxer mediaMuxer, MediaCodec.BufferInfo info, long start, long end, File file, boolean isAudio) throws Exception {
        int trackIndex = selectTrack(extractor, isAudio);
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(trackIndex);
            int muxerTrackIndex = mediaMuxer.addTrack(trackFormat);
            int maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            boolean inputDone = false;
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
            long startTime = -1;


            while (!inputDone) {

                boolean eof = false;
                int index = extractor.getSampleTrackIndex();
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0);

                    if (info.size < 0) {
                        info.size = 0;
                        eof = true;
                    } else {
                        info.presentationTimeUs = extractor.getSampleTime();
                        if (start > 0 && startTime == -1) {
                            startTime = info.presentationTimeUs;
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0;
                            info.flags = extractor.getSampleFlags();
                            mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info);
                            extractor.advance();
                        } else {
                            eof = true;
                        }
                    }
                } else if (index == -1) {
                    eof = true;
                }
                if (eof) {
                    inputDone = true;
                }
            }

            extractor.unselectTrack(trackIndex);
            return startTime;
        }
        return -1;
    }


    public static class VideoEditedInfo {
        public long startTime;
        public long endTime;
        public int rotationValue;
        public int originalWidth;
        public int originalHeight;
        public int resultWidth;
        public int resultHeight;
        public int bitrate;
        public String originalPath;
        public File destFile;
        public boolean isCompRequired;

        public String getString() {
            return String.format(Locale.US, "-1_%d_%d_%d_%d_%d_%d_%d_%d_%s",
                    startTime, endTime, rotationValue, originalWidth,
                    originalHeight, bitrate, resultWidth, resultHeight,
                    originalPath);
        }

        public void parseString(String string) {
            if (string.length() < 6) {
                return;
            }
            String args[] = string.split("_");
            if (args.length >= 10) {
                startTime = Long.parseLong(args[1]);
                endTime = Long.parseLong(args[2]);
                rotationValue = Integer.parseInt(args[3]);
                originalWidth = Integer.parseInt(args[4]);
                originalHeight = Integer.parseInt(args[5]);
                bitrate = Integer.parseInt(args[6]);
                resultWidth = Integer.parseInt(args[7]);
                resultHeight = Integer.parseInt(args[8]);
                for (int a = 9; a < args.length; a++) {
                    if (originalPath == null) {
                        originalPath = args[a];
                    } else {
                        originalPath += "_" + args[a];
                    }
                }
            }
        }

        public String toString()
        {
            return "VideoEditorInfo{ " + " ,startTime =" + startTime  + " ,endTime =" + endTime  + " ,rotationValue =" + rotationValue  + " ,originalWidth =" + originalWidth
                    + " ,originalHeight =" + originalHeight  + " ,bitrate =" + bitrate  + " ,resultWidth =" + resultWidth  + " ,resultHeight =" + resultHeight + "}";
        }
    }
}
