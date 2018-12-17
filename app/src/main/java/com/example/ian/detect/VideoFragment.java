package com.example.ian.detect;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * A simple {@link Fragment} subclass.
 */
public class VideoFragment extends Fragment {

    /**
     * TextureView for put prview
     */
    TextureView mTextureview;

    /**
     * Image reader for capturing picture
     */
    ImageReader mImageReader;

    /**
     * asignal to block other thread for accessing
     */
    private Semaphore locker = new Semaphore(1);

    /**
     * a reference for camera device
     */
    private CameraDevice mCameraDevice;



    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     * Which means it is called when the openCamera() is called
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            locker.release();
            mCameraDevice = camera;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            locker.release();
            camera.close();
            mCameraDevice=null;

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            locker.release();
            camera.close();
            mCameraDevice=null;
            if(getActivity()==null){
                getActivity().finish();;
            }

        }
    };


    /**
     * return an instance of VideoFragment
     * @return
     */
    public static VideoFragment newInstance() {
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
        if(mTextureview.isAvailable()){
            openCamera();
        }else{
            mTextureview.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }


    // surface texture for showing the preview for the camera
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
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
     */
    private void openCamera() {

        //check camera permission
        if(ContextCompat.checkSelfPermission(getActivity(),Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED){
            requestCameraPermission();
            return;
        }


        String frontCamera = getFrontCamera();
        if (frontCamera == null) {
            ErrorDialog.newInstance("Can't find Front Camera")
                    .show(getChildFragmentManager(), "Dialog");
        }
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            //use locker to give some time for camera to open,
            //  it will release after manager.openCamera() is called,check on mStateCallback parameters

            if (!locker.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout waiting for camera to open");
            }
            manager.openCamera(frontCamera, mStateCallback, mBackgroundHandler);
            createCameraPreview();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * CaptureRequest.Builder for create a preview
     */
    private CaptureRequest.Builder mCaptureRequestBuilder;



    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;


    /**
     * Capture request for camera preview
     */

    private CaptureRequest mCaptureRequest;


    /**
     * handling tasks from background
     */
    private Handler mBackgroundHandler;




    //////////////////////////////////////////////////Camera States////////////////////////////////////////

    private int CameraState = STATE_PREVIEW;

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;
    /////////////////////////////////////////////////Camera States/////////////////////////////////////////


    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     * also used in create preview
     */

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(@NonNull CaptureResult request){
            switch (CameraState){
                case STATE_PREVIEW:{
                    break;
                }
            }
        }




        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            process(result);
        }
    };

    /**
     * Create camera preview
     */
    private void createCameraPreview(){
        SurfaceTexture texture = mTextureview.getSurfaceTexture();
        //check if the texture is null
        assert texture != null;
        //set the size of the texture
        texture.setDefaultBufferSize(mTextureview.getWidth(),mTextureview.getHeight());



        Surface surface = new Surface(texture);
        try {
            // Set up a CaptureRequest.Builder with the output Surface.
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            //First create a callback
            CameraCaptureSession.StateCallback stateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    //if camera is closed
                    if (mCameraDevice == null){
                        return;
                    }
                    mCaptureSession = session;
                    // Auto focus should be continuous for camera preview.and auto exposure
                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    //build the request
                    mCaptureRequest = mCaptureRequestBuilder.build();
                    try {
                        mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    ErrorDialog.newInstance("Can't Create mCaptureSession")
                            .show(getChildFragmentManager(), "Dialog");
                }
            };
            //create CameraCaptureSession
            mCameraDevice.createCaptureSession(Arrays.asList(surface,mImageReader.getSurface()),stateCallback,null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    /**
     * Find front Camera
     * @return String CameraID or null if not found
     */
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

                    // https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap
                    //This is the authoritative list for all output formats (and sizes respectively for that format) that are supported by a camera device.
                    //contains the minimum frame durations and stall durations for each format/size combination that can be used to calculate effective frame rate when submitting multiple captures.
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if(map == null){
                        break;
                    }
                    Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),new CompareSizeByArea());

                    //Create ImageReader to capture image
                    // maximages: Maximum number of images that can be acquired from the ImageReader by any time (for example, with acquireNextImage()).
                    mImageReader = ImageReader.newInstance(largest.getWidth(),largest.getHeight(),ImageFormat.JPEG,2);
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
     * Comparator class for compare the Size of a output
     */
    static class CompareSizeByArea implements Comparator<Size>{
        @Override
        public int compare(Size o1, Size o2) {
            //use Long.signum to avoid get overflow of int after multiply
            return Long.signum((long)o1.getHeight()*o1.getWidth()-(long)o2.getHeight()*o2.getWidth());
        }
    }




}
