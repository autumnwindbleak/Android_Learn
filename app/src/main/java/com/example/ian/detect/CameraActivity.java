package com.example.ian.detect;

import android.content.Context;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.OrientationEventListener;
import android.widget.ImageButton;


public class CameraActivity extends AppCompatActivity {

    private VideoFragment fragment;
    private ImageButton imageButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        imageButton = findViewById(R.id.imageButton);
        OrientationListener orientationListener = new OrientationListener(this,SensorManager.SENSOR_DELAY_NORMAL);
        if(orientationListener.canDetectOrientation()){
            orientationListener.enable();
        }else {
            Log.d("autumnwindbleak", "onCreate: orientation sensor is not working");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    /**
     * set a orientation listener to check the orientation and set the right orientation of the button
     */

    private class OrientationListener extends OrientationEventListener{
        public OrientationListener(Context context) {
            super(context);
        }
        public OrientationListener(Context context, int delay){
            super(context,delay);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if(orientation == OrientationEventListener.ORIENTATION_UNKNOWN){
                return;
            }
            int newOrientation = (orientation % 360 + 45) / 90 * 90;
            if(newOrientation == 360){
                newOrientation = 0;
            }
            int buttonOritation = ((int) imageButton.getRotation() % 360 + 45)/90 * 90;
//            Log.d("autumnwindbleak", "onOrientationChanged: neworientation = " + newOrientation);
            int rotation = newOrientation - buttonOritation;
            if(rotation != 0) {
//                Log.d("autumnwindbleak", "onOrientationChanged: rotation = " + rotation
//                        + "\tbutton rotation = " + buttonOritation + "\torientation = " + newOrientation );
                imageButton.animate().rotation(0 - newOrientation).start();
            }
        }
    }


}
