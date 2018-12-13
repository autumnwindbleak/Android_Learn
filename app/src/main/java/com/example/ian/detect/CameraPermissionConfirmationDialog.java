package com.example.ian.detect;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

/**
 * Creating a class that pop up dialog window for user to grant their permission for the camera.
 */
public class CameraPermissionConfirmationDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        //get the parent fragment.
        final Fragment parents = getParentFragment();
        //create a builder for AlertDialog
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        //set the message
        alertDialog.setMessage("This app need camera permission.");

        //create the listener for the ok button if ok is selected grant permission
        DialogInterface.OnClickListener listener1 = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //requestCode 1 means ask for permission? ( haven't check but seems it is)
                parents.requestPermissions(new String[] {Manifest.permission.CAMERA},1);
            }
        };
        //create the  OK button
        alertDialog.setPositiveButton(android.R.string.ok,listener1);

        //create the listener for the cancel button if cancel is selected quit app
        DialogInterface.OnClickListener listener2 = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Activity activity = parents.getActivity();
                if(activity != null){
                    activity.finish();
                }
            }
        };
        //create the Cancel button
        alertDialog.setNegativeButton(android.R.string.cancel, listener2);
        return alertDialog.create();
    }
}
