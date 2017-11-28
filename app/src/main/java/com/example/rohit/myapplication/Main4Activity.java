package com.example.rohit.myapplication;

import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class Main4Activity extends AppCompatActivity {

    String filepath;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main4);
        filepath = Environment.getExternalStorageDirectory().getPath() + "/sample22.mp4";
        Boomerang boomerang = new Boomerang(filepath);
        boomerang.start();

    }
}
