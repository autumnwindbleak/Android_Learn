package com.example.ian.detect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * Use the {@link ImageFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ImageFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match

    ImageView mImageView;

    private final static String TAG = "ImageFragment";

    static{
        if(OpenCVLoader.initDebug()){
            try {
                // the name of the actually is "libopencv_java4.so"
                System.loadLibrary("opencv_java4");
            }catch (Throwable e){
                Log.d(TAG, "static initializer: load lib fail");
            }
            Log.d(TAG, "static initializer: succeed");

        }else{
            Log.d(TAG, "static initializer: failed");
        }
    }

    public ImageFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ImageFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ImageFragment newInstance() {
        ImageFragment fragment = new ImageFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_image, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mImageView = view.findViewById(R.id.imageView);
        mImageView.setImageAlpha(0);
    }

    @Override
    public void onResume() {
        super.onResume();

        /**
         * recieve the image and then show it on image view
         */
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("VideoFragment");
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bitmap bitmap = intent.getParcelableExtra("image");
                if(SelfConfiguration.GAUSSIAN_BLUR){
                    bitmap = GaussianBlur(getContext(),bitmap,25,5);
                }
                if(SelfConfiguration.PAPER_DETECTION){
                    PaperDetection paperDetection = new PaperDetection(bitmap);
                    paperDetection.run();
                    bitmap = paperDetection.getDrawing();
//                    bitmap = paperDetection.getPaperArea();
//                    bitmap = PaperDetection.newInstance(bitmap).run().getGray();
                }
                if(bitmap == null){
                    return;
                }
                mImageView.setImageBitmap(bitmap);
                mImageView.setImageAlpha(255);
            }
        };
        localBroadcastManager.registerReceiver(broadcastReceiver,intentFilter);
    }

    /**
     * Guassian Blur of a picture
     * @param context
     * @param source bitmap source
     * @param radius blur radius (0-25)
     * @param scaleratio smaller the image then go blur
     * @return
     */
    private Bitmap GaussianBlur(Context context,Bitmap source, int radius,int scaleratio){
        Bitmap inputbitmap = Bitmap.createScaledBitmap(source,source.getWidth()/scaleratio,source.getHeight()/scaleratio,false);
        //create a renderscript
        RenderScript renderScript = RenderScript.create(context);
        //allocate memory for renderscript
        final Allocation input = Allocation.createFromBitmap(renderScript,inputbitmap);
        final Allocation output = Allocation.createTyped(renderScript,input.getType());
        //load blur script
        ScriptIntrinsicBlur scriptIntrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        //set input and parameters
        scriptIntrinsicBlur.setInput(input);
        scriptIntrinsicBlur.setRadius(radius);
        //start the script
        scriptIntrinsicBlur.forEach(output);
        //copy result
        output.copyTo(inputbitmap);
        //destroy the renderscript
        input.destroy();
        output.destroy();
        renderScript.destroy();
        scriptIntrinsicBlur.destroy();
        inputbitmap = Bitmap.createScaledBitmap(inputbitmap,source.getWidth(),source.getHeight(),false);
        return inputbitmap;
    }
}
