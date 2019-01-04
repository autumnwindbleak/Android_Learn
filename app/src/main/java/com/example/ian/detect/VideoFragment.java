package com.example.ian.detect;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.ian.detect.util.CameraPermissionConfirmationDialog;
import com.example.ian.detect.util.ErrorDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * A simple {@link Fragment} subclass.
 */
public class VideoFragment extends Fragment {

    private String TAG = "autumnwindbleak";


    /**
     * asignal to block other thread for accessing
     */
    private Semaphore locker = new Semaphore(1);

    /**
     * a reference for camera device
     */
    private CameraDevice mCameraDevice;


    /**
     * handling tasks from background
     */
    private Handler mBackgroundHandler;

    /**
     * background thread
     */
    private HandlerThread mBackgroundThread;

    private void startBackgroundThread(){
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            locker.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            locker.release();
        }
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * all actions start from here
     */
    @Override
    public void onResume() {
        super.onResume();
        //register the take photo listener in camera activity
        ((CameraActivity)getActivity()).registerTakePhotoListener(mTakePhotoListener);
        startBackgroundThread();
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


    private String frontCameraId;

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
        //get frontCamera id
        frontCameraId = getFrontCamera();
        if (frontCameraId == null) {
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
            //open the camera and get the mCameraDevice instance for control the camera
            manager.openCamera(frontCameraId, mStateCallback, mBackgroundHandler);
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




    //////////////////////////////////////////////////Camera States////////////////////////////////////////

    private int CameraState = STATE_PREVIEW;

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_TAKE_PICTURE = 1;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 2;
    /////////////////////////////////////////////////Camera States/////////////////////////////////////////


    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     * also used in create preview
     */

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(@NonNull CaptureResult request){
            Log.d("camerastate", "process: state now are "+ CameraState);
            switch (CameraState){
                case STATE_PREVIEW:{
                    break;
                }
                case STATE_TAKE_PICTURE: {

                    Integer afState = request.get(CaptureResult.CONTROL_AF_STATE);
                    /**
                     * this part is to check the focus states
                     *
                     * null                                 some device the afState will return null after locked
                     * CONTROL_AF_STATE_FOCUSED_LOCKED      means focused and locked
                     * CONTROL_AF_STATE_NOT_FOCUSED_LOCKED  means not focused but locked
                     */

                    Log.d("camerastate", "process: afstate now are "+ afState);
                    if(afState == null || afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                            || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED){
                        Integer aeState = request.get(CaptureResult.CONTROL_AE_STATE);
                        Log.d("camerastate", "process: aestate now are "+ aeState);
                        /**
                         * this part is to check the exposure states
                         *
                         * null                         some device the aeState will return null after good exposure
                         * CONTROL_AE_STATE_CONVERGED   means the exposure is good now
                         * CONTROL_AE_STATE_PRECAPTURE  AE has been asked to do a precapture sequence and is currently executing it.
                         *                              Precapture can be triggered through setting android.control.aePrecaptureTrigger to START. Currently active and completed
                         *                              (if it causes camera device internal AE lock) precapture metering sequence can be canceled through setting android.control.aePrecaptureTrigger to CANCEL.
                         *                              Once PRECAPTURE completes, AE will transition to CONVERGED or FLASH_REQUIRED as appropriate. This is a transient state,
                         *                              the camera device may skip reporting this state in capture result.
                         */
                        if(aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED|| aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED|| aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE){
                            //if exposure is good
                            CameraState = STATE_PICTURE_TAKEN;
                            Log.d(TAG, "process: focus exposure good");
                            captureStillImage();
                        }else{
                            //try to set exposure
                            Log.d(TAG, "process: set Exposure, right now is:" + aeState);
                            setExposure();
                        }
                    }else{
                        //if focus is not locked try again
//                        Log.d(TAG, "Focus Locked failed, try again now. Focus states now are :" + afState);
                        lockFocus();
                    }
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
                    mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);
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

////////////////////////////////////////take picture///////////////////////////////
    /**
     * TextureView for put prview
     */
    TextureView mTextureview;

    /**
     * Image reader for capturing picture
     */
    ImageReader mImageReader;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable: acquire new image");
            Image image = reader.acquireNextImage();
            mBackgroundHandler.post(new ImageSaver(image));
        }
    };

    /**
     * send image to the broadcast so other fragment can receive
     * @param bytes
     */
    private void sendImageToBroadcast(byte[] bytes){
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);
        Intent intent = new Intent("VideoFragment");
        intent.putExtra("image",bitmap);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
    }


    private class ImageSaver implements Runnable{
        /**
         * the image for output
         */
        private Image mImage;

        ImageSaver(Image image){
            mImage =image;
        }

        /**
         * out put image to file
         */
        @Override
        public void run() {
            //check read and write permission,if don't have then request permission
            if(ContextCompat.checkSelfPermission(getActivity(),Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},1);
            }
            if(ContextCompat.checkSelfPermission(getActivity(),Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
            }

//            File outputFile = new File(Environment.getExternalStorageDirectory(),"Pictures/" + System.currentTimeMillis() + ".jpg");
            File outputFile = new File(Environment.getExternalStorageDirectory(),"Pictures/photo.jpg");
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            Log.d(TAG, "Image saved to :" + outputFile.getAbsolutePath());
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(outputFile);
                output.write(bytes);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                mImage.close();
                if(output != null){
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            sendImageToBroadcast(bytes);

        }
    }


    private final CameraActivity.TakePhotoListener mTakePhotoListener = new CameraActivity.TakePhotoListener() {
        @Override
        public void onClick() {
            Log.d(TAG, "Clicked! ");
            lockFocus();
        }
    };

    /**
     * Lock focus
     */
    private void lockFocus(){
        // this is the way to lock focus
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);
        //change the state then create capture session
        CameraState = STATE_TAKE_PICTURE;
        try {
            mCaptureSession.capture(mCaptureRequestBuilder.build(),mCaptureCallback,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * set exposure
     */
    private void setExposure(){
        //set the auto exposure
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        //try take picture again
        CameraState = STATE_TAKE_PICTURE;
        try {
            mCaptureSession.capture(mCaptureRequestBuilder.build(),mCaptureCallback,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * focus and exposure are good now take a picture
     */
    private void captureStillImage(){
        // if device is not ready(should not happened)
        if(mCameraDevice == null){
            return;
        }
        //create a call back for capture still image
        try {

            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // Orientation
            CameraManager manager = (CameraManager)getActivity().getSystemService(Context.CAMERA_SERVICE);
            int rotation = manager.getCameraCharacteristics(frontCameraId).get(CameraCharacteristics.SENSOR_ORIENTATION);
            // This is the CaptureRequest.Builder that we use to take a picture.

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,rotation);
            captureBuilder.addTarget(mImageReader.getSurface());

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    unlockFocusAndExposure();
                }
            };
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            //take a picture;
            mCaptureSession.capture(captureBuilder.build(),captureCallback,mBackgroundHandler);
            Log.d(TAG, "captureStillImage: done");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocusAndExposure(){
        Log.d(TAG, "unlockFocusAndExposure: ");
        //reset auto focus trigger
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        //reset auto exposure trigger
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
        try {
            mCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback,mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            CameraState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), mCaptureCallback,mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }





////////////////////////////////////////take picture///////////////////////////////






}
