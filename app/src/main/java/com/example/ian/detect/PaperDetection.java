package com.example.ian.detect;

import android.graphics.Bitmap;
import android.provider.ContactsContract;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.LineSegmentDetector;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private Mat drawing;
    private Mat ROI;

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

        //read src image
        if(ReadFromFile){
            // read the file from filepath
            src = Imgcodecs.imread(filepath,-1);
            Log.d(TAG, "type of the src:\t"+ CvType.typeToString(src.type()));
        }
        if(ReadFromBitmap){
            src = new Mat(mBitmap.getWidth(),mBitmap.getHeight(),CvType.CV_8UC3);
            Utils.bitmapToMat(mBitmap,src);
        }

        //Because mat is BGR and Bitmap is RGBA, so need transfer first
        Imgproc.cvtColor(src,src,Imgproc.COLOR_BGR2RGBA);
        //find the Rect of Interest area
        ROI = get_ROI(src);
        //clone a drawing part for debugging
        drawing = ROI.clone();

        //denoise using gaussian blur
        denoised = denoise(ROI,GAUSSIAN_BLUR);
        //make the color gray
        gray = getGrayImage(denoised);
        if(gray == null) {
            Log.d(TAG, "gray is");
        }
        //make only black and white using Canny
        binary = canny(gray,3,150,3);
        //find contours
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
        //find the paper contour
        MatOfPoint longest = getPaperContours(contours);

        //draw the contour of the paper with red
        drawPoints(longest.toArray(),drawing,new Scalar(255,0,0,255),20);
        Log.d(TAG, "longest: " +longest.toArray().length);

        //change format of the longest contour to MatOfPoint2f
        MatOfPoint2f matOfPoint2f_longest = new MatOfPoint2f();
        matOfPoint2f_longest.fromArray(longest.toArray());
        MatOfPoint2f approx = new MatOfPoint2f();
        //try to convert into a polygon
        Imgproc.approxPolyDP(matOfPoint2f_longest, approx, Imgproc.arcLength(matOfPoint2f_longest, true)*0.01, true);
        Log.d(TAG, "approx number are " + approx.toArray().length);

        //chage format for using convex hull
        MatOfPoint approx_transfer = new MatOfPoint();
        approx_transfer.fromArray(approx.toArray());
        MatOfInt hull = new MatOfInt();
        //find hull of the polygon
        Imgproc.convexHull(approx_transfer,hull);
        //get the hull points from hull index
        MatOfPoint2f hullpoints = getHullPoints(approx,hull);

        if(hullpoints.toArray().length == 0){
            return null;
        }

        //get the 4 corners
        MatOfPoint2f corners = new MatOfPoint2f();
        if(hullpoints.toArray().length > 4){
            //if there are more than 4 points in hullpoints
            corners = get4corners(hullpoints);
        }else if(hullpoints.toArray().length < 4){
            //shouldn't get less than 4
            corners = null;
        }else {
            //so lucky that we get just 4 of them
            corners = hullpoints;
        }
        //if less than 4 corner points
        if(corners == null){
            Log.d(TAG, "didn't get enough corners. hullpoints length is " + hullpoints.toArray().length);
            return null;
        }

        //draw the corner of the paper with blue
        drawPoints(corners.toArray(),drawing,new Scalar(0,0,255,255),50);

        //sort the corners
        corners = sort_corners(corners);
        //get the perspective transformation
        perspective = perspectiveTransformation(ROI,corners,src);
        //turn around the picture so it top to be show on top
        Core.flip(perspective,perspective,-1);
        return this;
    }

    /**
     * After get hull index, get the points that index point to
     * @param mat_pts source points array
     * @param hull the index of the hull in source points array
     * @return
     */
    private  MatOfPoint2f getHullPoints(MatOfPoint2f mat_pts, MatOfInt hull){
        Point[] pts = mat_pts.toArray();
        int[] index = hull.toArray();
        Point[] resultpts = new Point[index.length];
        for(int i = 0; i < index.length; i++){
            resultpts[i] = pts[index[i]].clone();
        }
        MatOfPoint2f result = new MatOfPoint2f();
        result.fromArray(resultpts);
        return result;
    }

    /**
     * find the 4 corners from the hull points array
     * @param src
     * @return
     */
    private MatOfPoint2f get4corners(MatOfPoint2f src){
        Point[] pts = src.toArray();
        //create list to store the line information int[length,index_of_start_point]
        //please note that the hull is in clockwise, so the end point of the line is the next point of start point
        ArrayList<int[]> lines = new ArrayList<int[]>();
        //search for each line (the start point of each line)
        for(int i = 0; i< pts.length-1; i++){
            //create a new line
            int[] line = new int[2];
            //calculate the distance between start and end point
            line[0] = distance(pts[i],pts[i+1]);
            //record the index of start point
            line[1] = i;
            //add to lines for later sorting
            lines.add(line);
        }
        // special case: start point is the last point and the end point is the first point
        int[] line = new int[2];
        line[0] = distance(pts[0],pts[pts.length-1]);
        line[1] = pts.length-1;
        lines.add(line);

        // sort the lines by its length, longest at index 0
        Collections.sort(lines, new Comparator<int[]>() {
            @Override
            public int compare(int[] o1, int[] o2) {
                if(o1[0]>o2[0]){
                    return -1;
                }else if (o1[0]>o2[0]){
                    return 1;
                }
                return 0;
            }
        });

        //get the 4 edges of the polygon(Quadrilateral) in clockwise order
        Point[] four_edges = sortEdges(pts,lines);
        MatOfPoint2f result = new MatOfPoint2f();
        //get the 4 cross point by each pair of neighbour edge
        result.fromArray(get_cross_points_from_4_edge(four_edges));
        return result;
    }

    /**
     * get the cross point of each pair of neighbour edge
     * @param edges a 8 length Point which i*2 and i*2+1 are the ith pair of edge
     * @return four cross points of the four edge
     */
    private Point[] get_cross_points_from_4_edge(Point[] edges){
        Point[] result = new Point[4];
        for(int i = 0; i<4;i++){
            for(int j = i; j < 4; j++){

            }
            Point p1 = edges[i*2];
            Point p2 = edges[i*2 +1];
            Point p3 = edges[0];
            Point p4 = edges[1];
            if(i != 3){
                p3 = edges[(i+1)*2];
                p4 = edges[(i+1)*2+1];
            }
            double x1 = p1.x - p2.x;
            double y1 = p1.y - p2.y;
            double x2 = p3.x - p4.x;
            double y2 = p3.y - p4.y;
            double a = x1 * p1.y - y1 * p1.x;
            double b = x2 * p3.y - y2 * p3.x;
            double x = (b * x1 - a * x2) / (y1 * x2 - y2 * x1);
            double y = (a * y2 - b * y1) / (x1 * y2 - x2 * y1);
            result[i] = new Point(x,y);
        }
        return result;
    }

    /**
     * sort the 4 most longest edges in all edges, put them in clockwise order.
     * @param pts the points array that include all start and end point
     * @param lines the sorted by distance lines, each line are stored like this:int[length,index_of_start_point]
     * @return 4 longest edged in clockwise order in Point[] structure, 8 length Point[], i*2 is the ith line's start point and i*2+1 is the ith line's end point
     */
    private Point[] sortEdges(Point[] pts, ArrayList<int[]> lines){
        ArrayList<int[]> sortedbyindex= new ArrayList<int[]>();
        //create a new array that only have the 4 longest edges
        for(int i = 0; i< 4; i++){
            sortedbyindex.add(lines.get(i));
        }
        //sort by index of the start point(clockwise)
        Collections.sort(sortedbyindex, new Comparator<int[]>() {
            @Override
            public int compare(int[] o1, int[] o2) {
                if(o1[1]>o2[1]){
                    return 1;
                }else if (o1[1]<o2[1]){
                    return -1;
                }
                return 0;
            }
        });

        //create the point structure edges. 8 length Point[], i*2 is the ith line's start point and i*2+1 is the ith line's end point
        Point[] edges = new Point[8];
        for(int i = 0; i<4; i++){
            int index1 = sortedbyindex.get(i)[1];
            int index2 = index1 + 1;
            if (index1 == pts.length-1){
                index2 = 0;
            }
            edges[i*2] = pts[index1].clone();
            edges[i*2+1] = pts[index2].clone();
        }
        return edges;
    }


    /**
     * return the distance between point a and point b
     * @param a start point
     * @param b end point
     * @return distance in int
     */
    private int distance(Point a,Point b){
        return (int) Math.sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y));
    }


    /**
     * get the Rect of Interest
     * @param src source map
     * @return
     */
    private Mat get_ROI(Mat src){
        int height = src.rows();
        //this area is fit for A4 and A5
        Rect rect = new Rect(0,height*2/5,src.cols(),height/2);
        return new Mat(src,rect);
    }


    /**
     * get the contours of paper in the ROI that have the most wide height and width
     * @param contours
     * @return contours that at the shadow part
     */
    private MatOfPoint getPaperContours(List<MatOfPoint> contours){
        MatOfPoint result = new MatOfPoint();
        double maxdiffer = 0;

        for(MatOfPoint matOfPoint:contours){
            double maxx = 0;
            double minx = Double.MAX_VALUE;
            double maxy = 0;
            double miny = Double.MAX_VALUE;
            //search for each point in a contour to find the min and max value of x y
            for(Point x : matOfPoint.toArray()){
                maxx = Math.max(maxx,x.x);
                minx = Math.min(minx,x.x);
                maxy = Math.max(maxy,x.y);
                miny = Math.min(miny,x.y);
            }
            //define a differ that measure the width and height in total
            double differ = maxx - minx + maxy -miny;
            //record the most largest area
            if(differ>maxdiffer){
                result = matOfPoint;
                maxdiffer = differ;
            }
        }
        return result;
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
     * @return the drawing pic
     */
    public Bitmap getDrawing() {
        return Mat2Bitmap(drawing);
    }

    /**
     *  transfer mat to bitmap
     * @param mat
     * @return
     */
    private Bitmap Mat2Bitmap(Mat mat){
        if(mat == null){
            return null;
        }
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
    private Mat perspectiveTransformation(Mat source,MatOfPoint2f src_corners,Mat dst_mat_format){
        //set the transfer image as the src image
        Mat warpPerspective_dst = Mat.zeros(dst_mat_format.rows(),dst_mat_format.cols(),dst_mat_format.type());
        //set the dst matrix
        MatOfPoint2f warpcorners = new MatOfPoint2f(new Point(0,0),new Point(dst_mat_format.cols()-1,0),new Point(0,dst_mat_format.rows()-1),new Point(dst_mat_format.cols()-1,dst_mat_format.rows()-1));
        //get the transfer matrix
        Mat trans_matrix = Imgproc.getPerspectiveTransform(src_corners,warpcorners);
        //perspective transfer
        Imgproc.warpPerspective(source,warpPerspective_dst,trans_matrix,warpPerspective_dst.size());
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
        //put two upper side corner in index 0 and 1, put downer ones in 2,3
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
        //find which is at left and which is at right
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
     * use Canny to get a binary image(only black and white boarders
     * @param source the source image (can be color but better in gray)
     * @param thresholdlow the lower threshold use to connect the lines
     * @param thresholdhigh the hight threshold to find the rough boundaries
     * @param aperturesize default 3 (no idea how to use it)
     * @return a binary map showing contours
     */
    private Mat canny(Mat source,int thresholdlow,int thresholdhigh,int aperturesize){
        Mat canny = new Mat();
        Imgproc.Canny(source,canny,thresholdlow,thresholdhigh,aperturesize,false);
        return canny;
    }
}
