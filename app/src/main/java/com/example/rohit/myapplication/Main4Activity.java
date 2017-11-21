package com.example.rohit.myapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class Main4Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main4);
    }


    void doProcess(){

        ByteBuffer b = ByteBuffer.allocate(10);

    }
}
