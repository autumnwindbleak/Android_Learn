package com.example.ian.detect.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * this class is used to create the shadow
 * the default area of the A4 paper:    bottom Y is 14/20 of the height, X is from left edge to right edge,
 *                                      top Y is 8/20 of the height, X is from 1/4 width to 3/4 width
 * the default area of the A4 paper:    bottom Y is 9/10 of the height, X is from left edge to right edge,
 *                                      top Y is 1/2 of the height, X is from 1/4 width to 3/4 width
 */
public class CreateShadow {
    private Bitmap mBitmap;
    private int mHeight;
    private int mWidth;

    private final int A4 = 1;
    private final int A5 = 0;
    private final int black = Color.argb(255,0,0,0);
    private final int nocolor = Color.argb(0,0,0,0);

    /**
     *
     * @param width
     * @param height
     */
    public CreateShadow(int width,int height){
        mHeight = height;
        mWidth = width;
    }

    /**
     *
     * @param A bottom left point
     * @param B top left point
     * @param C top right point
     * @param D bottom right point
     * @return bitmap
     */
    public Bitmap get_shadow(Point A, Point B, Point C, Point D){
        Log.d("autumnwindbleak", "get_shadow: ");
        create_bitmap(A,B,C,D);
        return mBitmap;
    }

    /**
     *
     * @param A bottom left point
     * @param B top left point
     * @param C top right point
     * @param D bottom right point
     * @return
     */
    private void create_bitmap(Point A, Point B, Point C, Point D){
        mBitmap = Bitmap.createBitmap(mWidth,mHeight,Bitmap.Config.ARGB_8888);
        for(int i = 0; i < mWidth; i++){
            for(int j = 0; j < mHeight; j++){
                if(isPointInRect(new Point(i,j),A,B,C,D)){
                    mBitmap.setPixel(i,j,nocolor);
                }else{
                    mBitmap.setPixel(i,j,black);
                }
            }
        }
    }

    /**
     *
     * @param X the point that need to be checked
     * @param A bottom left point
     * @param B top left point
     * @param C top right point
     * @param D bottom right point
     * @return
     */
    private boolean isPointInRect(Point X,Point A, Point B, Point C, Point D){
        int x = X.x;
        int y = X.y;
        final int a = (B.x - A.x)*(y - A.y) - (B.y - A.y)*(x - A.x);
        final int b = (C.x - B.x)*(y - B.y) - (C.y - B.y)*(x - B.x);
        final int c = (D.x - C.x)*(y - C.y) - (D.y - C.y)*(x - C.x);
        final int d = (A.x - D.x)*(y - D.y) - (A.y - D.y)*(x - D.x);
        if((a > 0 && b > 0 && c > 0 && d > 0) || (a < 0 && b < 0 && c < 0 && d < 0)) {
            return true;
        }
        return false;
    }

    /**
     *
     * @param version 0 for A5, 1 for A4
     */
    public void saveCreatedShadeByDefault(int version){
        int width = 1440;
        int height = 2392;
        Bitmap bitmap = null;
        int papersize = 0;
        if(version == 0){
            // a5 version
            bitmap = new CreateShadow(width,height).get_shadow(new Point(0,height*9/10),new Point(width/4,height/2),new Point(width*3/4,height/2),new Point(width,height*9/10));
            papersize = 5;
        }else if(version == 1){
            //a4 version
            bitmap = new CreateShadow(width,height).get_shadow(new Point(0,height*14/20),new Point(width/4,height*8/20),new Point(width*3/4,height*8/20),new Point(width,height*14/20));
            papersize = 4;
        }

        File output = new File(Environment.getExternalStorageDirectory(),"Pictures/a"+ papersize +"shadow.png");
        Log.d("autumnwindbleak", "onWindowFocusChanged: save begin" + output.getAbsolutePath());
        try {
            FileOutputStream outputStream = new FileOutputStream(output);
            bitmap.compress(Bitmap.CompressFormat.PNG,100,outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }


}
