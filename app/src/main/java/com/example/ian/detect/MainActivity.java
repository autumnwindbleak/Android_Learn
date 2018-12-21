package com.example.ian.detect;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {
    public static String MESSAGE = "com.example.ian.detect.MESSAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /**
     * call when user do click on the "Send" Button
     * @param view
     */
    public void sendMessage(View view){
        Intent intent = new Intent(this,DisplayMessageActivity.class);
        EditText editText = (EditText) findViewById(R.id.editText);
        String message = editText.getText().toString();
        intent.putExtra(MESSAGE,message);
        startActivity(intent);
    }

    /**
     * call when user click on "Camera" button
     * @param view
     */
    public void CameraActivity(View view){
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
    }


}
