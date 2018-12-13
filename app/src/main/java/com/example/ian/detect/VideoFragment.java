package com.example.ian.detect;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;


/**
 * A simple {@link Fragment} subclass.
 */
public class VideoFragment extends Fragment {

    TextureView mTextureview;

    /**
     * return an instance of VideoFragment
     * @return
     */
    public static VideoFragment newInstance(){
        return new VideoFragment();
    }


    public VideoFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_video, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mTextureview = view.findViewById(R.id.textureView);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }


    // surface texture for showing the preview for the camera
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width,height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    /**
     *  opening and initialize the camera
     *
     * @param width
     * @param height
     */
    private void openCamera(int width, int height){

        //check camera permission
        if(!isCameraPermissionGranted()){
            requestCameraPermission();
        }
        String frontCamera = getFrontCamera();
        if(frontCamera == null){
            ErrorDialog
        }



    }

    private String getFrontCamera(){
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraid : manager.getCameraIdList()){
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraid);
                //if the camera have facing characteristic and facing front
                if(characteristics.get(CameraCharacteristics.LENS_FACING)!= null
                        &&
                        characteristics.get(CameraCharacteristics.LENS_FACING)== CameraCharacteristics.LENS_FACING_FRONT){
                    return cameraid;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * request for camera permission
     */
    public void requestCameraPermission() {
        /* if we asked the permission before and the user reject it, it return true
            if the user reject it and select "Don't ask again" it return false
            if the hardware don't allow app to have this permission return false
        */
        //so the following code is to request the permission no matter if the user have reject it or not(WTF?????Then Why we need this if...else...?)
        if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
            //create a dialog to ask for permission
            new CameraPermissionConfirmationDialog().show(getChildFragmentManager(),"dialog");
        }else{
            //ask for permission anyway
            requestPermissions(new String[] {Manifest.permission.CAMERA},1);
        }

    }

    /**
     * check if the camera permission is granted
     * @return true for have permission false for not
     */
    private boolean isCameraPermissionGranted(){
        //getActivity() returns the current activity
        return ContextCompat.checkSelfPermission(getActivity(),Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }




}
