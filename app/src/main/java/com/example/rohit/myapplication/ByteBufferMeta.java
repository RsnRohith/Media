package com.example.rohit.myapplication;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * Created by rohit on 17/11/17.
 */

public class ByteBufferMeta {

    private MediaCodec.BufferInfo bufferinfo;
    private ByteBuffer byteBuffer;

    public ByteBufferMeta(MediaCodec.BufferInfo bufferInfo,ByteBuffer byteBuffer){
        this.byteBuffer = byteBuffer;
        this.bufferinfo = bufferInfo;
    }

    public ByteBufferMeta(){

    }

    public MediaCodec.BufferInfo getBufferinfo() {
        return bufferinfo;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public void setBufferinfo(MediaCodec.BufferInfo bufferinfo) {
        this.bufferinfo = bufferinfo;
    }

    public void setByteBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }
}
