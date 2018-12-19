package com.example.ian.detect.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

public class ErrorDialog extends DialogFragment {
    // save the passed message
    private String Message;

    //Create a new ErrorDialog with a message
    public  static ErrorDialog newInstance(String message){
        ErrorDialog dialog = new ErrorDialog();
        dialog.Message = message;
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Fragment parents = getParentFragment();

        //create Builder for AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                getActivity().finish();
            }
        };
        if(Message == null) Message = "ERROR!";
        builder.setMessage(Message);
        builder.setPositiveButton(android.R.string.ok,listener);
        return  builder.create();
    }
}
