package com.example.ian.detect;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * this class is to do the paper detection part
 */


public class PaperDetection {

    private final static String TAG = "PaperDetection";

    /**
     * meanshift denoise is buged don't use it
     */
    private final static int MEAN_SHIFT = 0;
    private final static int GAUSSIAN_BLUR = 1;


    /**
     * the original image file path
     */
    private String filepath;

    /**
     * the original bitmap image
     */

    private Bitmap mBitmap;

    /**
     * tow flag that tells which type of the source
     */
    private boolean ReadFromFile;
    private boolean ReadFromBitmap;


    /**
     * Storing each stage's image
     */
    private Mat src;
    private Mat denoised;
    private Mat gray;
    private Mat binary;
    private Mat perspective;

    /**
     * constructor
     * @param filepath the original image file path
     */
    public PaperDetection(String filepath){
        this.filepath = filepath;
        ReadFromBitmap = false;
        ReadFromFile = true;
    }


    /**
     * constructor
     * @param bitmap source file is a bitmap
     */
    public PaperDetection(Bitmap bitmap){
        mBitmap = bitmap;
        ReadFromFile = false;
        ReadFromBitmap = true;
    }

    /**
     * return a new instance easy to use
     * @param filepath the original image file path
     * @return a new instance of PaperDetection
     */
    public static PaperDetection newInstance(String filepath){
        PaperDetection instance = new PaperDetection(filepath);
        return instance;
    }

    public static PaperDetection newInstance(Bitmap bitmap){
        PaperDetection instance = new PaperDetection(bitmap);
        return instance;
    }


    /**
     * Function that use to process the detection
     * @return a bitmap that shows only the paper area
     */
    public PaperDetection run(){
        if(ReadFromFile){
            // read the file from filepath
            src = Imgcodecs.imread(filepath,-1);
//            Log.d(TAG, "type of the src:\t"+ CvType.typeToString(src.type()));
        }
        if(ReadFromBitmap){
            src = new Mat(mBitmap.getWidth(),mBitmap.getHeight(),CvType.CV_8UC3);
            Utils.bitmapToMat(mBitmap,src);
        }

        //Because mat is BGR and Bitmap is RGBA, so need transfer first
        Imgproc.cvtColor(src,src,Imgproc.COLOR_BGR2RGBA);

        denoised = denoise(src,GAUSSIAN_BLUR);
        //make the color gray
        gray = getGrayImage(denoised);
        //make only black and white
        binary = threshold(gray,150);

        //find contours
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);

        //find longest contour is the paper size
        MatOfPoint longest = getMaxContour(contours);

        //draw the contour of the paper with red
        drawPoints(longest.toArray(),gray,new Scalar(255,0,0,255),20);
        Log.d(TAG, "longest: " +longest.toArray().length);

        //change format of the longest contour to MatOfPoint2f
        MatOfPoint2f matOfPoint2f_longest = new MatOfPoint2f();
        matOfPoint2f_longest.fromArray(longest.toArray());
        MatOfPoint2f approx = new MatOfPoint2f();
        //get the corners
        Imgproc.approxPolyDP(matOfPoint2f_longest, approx, Imgproc.arcLength(matOfPoint2f_longest, true)*0.02, true);

        //sort the corners
        approx = sort_corners(approx);

        //draw the corner of the paper with blue
        drawPoints(approx.toArray(),gray,new Scalar(0,0,255,255),50,10);

        Log.d(TAG, "approx :" + approx.toArray().length);

        if(approx.toArray().length != 4){
            Log.d(TAG, "didn't get the 4 corners");
            return null;
        }
        perspective = perspectiveTransformation(approx,src);
        return this;
    }


    /**
     * @return the cut paper area
     */
    public Bitmap getPaperArea(){
        return Mat2Bitmap(perspective);
    }

    /**
     * @return the gray pic
     */
    public Bitmap getGray(){
        return Mat2Bitmap(gray);
    }

    /**
     * @return the binary pic
     */
    public Bitmap getBinary(){
        return Mat2Bitmap(binary);
    }

    /**
     * @return the denoised pic
     */
    public Bitmap getDenoised() {
        return Mat2Bitmap(denoised);
    }

    /**
     *  transfer mat to bitmap
     * @param mat
     * @return
     */
    private Bitmap Mat2Bitmap(Mat mat){
        Bitmap tbitmap = Bitmap.createBitmap(mat.cols(),mat.rows(),Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat,tbitmap);
        return tbitmap;
    }


    /**
     * make perspective transformation
     * @param src_corners the 4 corners of the area that need to do perspective transformation
     * @param dst_mat_format the format of the dst,(output width,size,type are all the same with this one)
     * @return the transformed Mat image
     */
    private Mat perspectiveTransformation(MatOfPoint2f src_corners,Mat dst_mat_format){
        //set the transfer image as the src image
        Mat warpPerspective_dst = Mat.zeros(dst_mat_format.rows(),dst_mat_format.cols(),dst_mat_format.type());
        //set the dst matrix
        MatOfPoint2f warpcorners = new MatOfPoint2f(new Point(0,0),new Point(dst_mat_format.cols()-1,0),new Point(0,dst_mat_format.rows()-1),new Point(dst_mat_format.cols()-1,dst_mat_format.rows()-1));
        //get the transfer matrix
        Mat trans_matrix = Imgproc.getPerspectiveTransform(src_corners,warpcorners);
        //perspective transfer
        Imgproc.warpPerspective(src,warpPerspective_dst,trans_matrix,warpPerspective_dst.size());
        return warpPerspective_dst;
    }

    /**
     * draw points on Mat
     * @param pts Points array
     * @param dst destination Mat
     * @param size the size of the point
     * @param decrease the decrease size
     */
    private void drawPoints(Point[] pts,Mat dst,Scalar color,int size,int decrease){
        for(Point pt : pts){
            Imgproc.circle(dst,pt,size,color,-1);
            size = size - decrease;
        }
    }


    /**
     * draw points on Mat
     * @param pts Points array
     * @param dst destination Mat
     * @param size the size of the point
     */
    private void drawPoints(Point[] pts,Mat dst,Scalar color,int size){
        for(Point pt : pts){
            Imgproc.circle(dst,pt,size,color,-1);
        }
    }


    /**
     * sort the 4 corners in order: topleft, topright, bottomleft bottemright
     * @param src 4 corners
     * @return 4 corners in order
     */
    private MatOfPoint2f sort_corners(MatOfPoint2f src){
        MatOfPoint2f result = new MatOfPoint2f();
        Point[] pts = src.toArray();
        //find two upper side corner
        for(int i = 0; i < pts.length; i++){
            for(int j = i+1; j < pts.length;j++){
                if(pts[j].y < pts[i].y){
                    //swap two points
                    double x = pts[i].x;
                    double y = pts[i].y;
                    pts[i].x = pts[j].x;
                    pts[i].y = pts[j].y;
                    pts[j].x = x;
                    pts[j].y = y;
                }
            }
        }
        if(pts[1].x < pts[0].x){
            //swap two points
            double x = pts[0].x;
            double y = pts[0].y;
            pts[0].x = pts[1].x;
            pts[0].y = pts[1].y;
            pts[1].x = x;
            pts[1].y = y;
        }
        if(pts[3].x < pts[2].x){
            //swap two points
            double x = pts[2].x;
            double y = pts[2].y;
            pts[2].x = pts[3].x;
            pts[2].y = pts[3].y;
            pts[3].x = x;
            pts[3].y = y;
        }
        result.fromArray(pts);
        return result;
    }





    /**
     * denoise the image
     * Mean shift denoise is buged don't use it
     * @param source source image
     * @param typeofdenoise the type of the denoise
     * @return
     */
    private Mat denoise(Mat source,int typeofdenoise){
        Mat denoised = new Mat();
        switch (typeofdenoise){
            case MEAN_SHIFT:
                //MeanShift denoise
                Imgproc.pyrMeanShiftFiltering(source,denoised,30,10);
                break;
            case GAUSSIAN_BLUR:
                //Gaussian Blur denoise
                Imgproc.GaussianBlur(source,denoised,new Size(3,3),2,2);
                break;
            default:
                //Gaussian Blur denoise
                Imgproc.GaussianBlur(source,denoised,new Size(3,3),2,2);
                break;
        }
        return denoised;
    }



    /**
     * make the image gray
     * @param source
     * @return
     */
    private Mat getGrayImage(Mat source){
        Mat gray = new Mat();
        Imgproc.cvtColor(source,gray,Imgproc.COLOR_BGR2GRAY);
        return gray;
    }

    /**
     * make the image only black and white
     * @param
     */
    private Mat threshold(Mat source,int thresh){
        Mat binary = new Mat();
        Imgproc.threshold(source,binary,thresh,255,Imgproc.THRESH_TOZERO);
        return binary;
    }

    /**
     * find the contour that is the longest
     * @param contours
     * @return
     */
    private MatOfPoint getMaxContour(List<MatOfPoint> contours){
        int max = 0;
        int result = 0;
        for(int i = 0; i < contours.size(); i++){
            MatOfPoint tmp = contours.get(i);
            int length = tmp.toArray().length;
            if(length >= max){
                max = length;
                result=i;
            }
        }
        return contours.get(result);
    }

    private int getmin(double a,double b, double c, double d){
        return (int)Math.min(Math.min(a, b),Math.min(c,d));
    }

    private int getmax(double a,double b, double c, double d){
        return (int)Math.max(Math.max(a, b),Math.max(c,d));
    }

}
