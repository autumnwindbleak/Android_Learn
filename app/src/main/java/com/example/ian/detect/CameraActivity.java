package com.example.ian.detect;

import android.content.Context;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
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

////////////////////////send take photo to fragment////////////////////////////////////





    /**
     * when user click on the take photo imagebutton
     * @param view
     */
    public void takepicture(View view){
        //send click event to fragment
        if(mTakePhotoListener != null){
            mTakePhotoListener.onClick();
        }
    }

    //set the listener
    private TakePhotoListener mTakePhotoListener;



    public void registerTakePhotoListener(TakePhotoListener mTakePhotoListener){
        this.mTakePhotoListener = mTakePhotoListener;
    }

    // create a interface for fragment to implement
    public interface TakePhotoListener{
        public void onClick();
    }



////////////////////////send take photo to fragment////////////////////////////////////






    /**
     * set a orientation listener to check the orientation and set the right orientation of the button(only 4 directions)
     */
    private class OrientationListener extends OrientationEventListener{

        /**
         * set a flag to tell if the animation of the button is finished or not
         */
        private boolean finished;
        /**
         * record the last orientation to find out the rotation.
         */
        private int oldorientation;
        public OrientationListener(Context context) {
            super(context);
            finished = true;
            oldorientation = 0;
        }
        public OrientationListener(Context context, int delay){
            super(context,delay);
            finished = true;
            oldorientation = 0;
        }

        /**
         * when orientation changed this function is called
         * @param orientation
         */
        @Override
        public void onOrientationChanged(int orientation) {

            //if animation is not finished, do nothing
            if(!finished){
                return;
            }
            //if orientation is unknown stop it
            if(orientation == OrientationEventListener.ORIENTATION_UNKNOWN){
                return;
            }
            //only bigger than 45 degree orientation is count
            int newOrientation = (orientation % 360 + 45) / 90 * 90;
            if(newOrientation == 360){
                newOrientation = 0;
            }
            //find the rotation
            int rotation = oldorientation - newOrientation;
            //if need rotate 270 clockwise then go 90 anti-clockwise
            if(rotation == 270){
                rotation = -90;
            }
            //if need rotate 270 anti-clockwise then go 90 clockwise
            if(rotation == -270){
                rotation = 90;
            }
            //record the oldorientation
            oldorientation = newOrientation;
            //if rotation is 0 no need to do anything
            if(rotation != 0) {
                //rotate the button and when the animation stop set flag to true
                imageButton.animate().rotationBy(rotation).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        finished = true;
                }
                }).start();
                //set flag to false
                finished = false;
            }
        }
    }
}
