
// MogCog.java
// Andrew Davison, September 2013, ad@fivedots.coe.psu.ac.th

/* Detect the foreground movement in the grabbed image, storing
   it as a B&W difference image, and find its center-of-gravity (COG).

   Display the original image with the COG marked as a circle, and
   the current difference image in two CanvasFrames. 

   The foreground movement is extracted using OpenCV's implementation of the
   Gaussian Mixture-based Background/Foreground Segmentation algorithm
   (BackgroundSubtractorMOG2)
   Docs at  http://docs.opencv.org/modules/video/doc/motion_analysis_and_object_tracking.html
                    #BackgroundSubtractorMOG2

    Original paper:
       Zoran Zivkovic, "Improved Adaptive Gausian Mixture Model for Background Subtraction", 
       International Conference Pattern Recognition, UK, August, 2004, 
       http://www.zoranz.net/Publications/zivkovic2004ICPR.pdf
    and
       https://www.science.uva.nl/research/isla/pub/Zivkovic04icpr.pdf

    More info: http://personal.ee.surrey.ac.uk/Personal/R.Bowden/publications/avbs01/avbs01.pdf

   Example of its use:
     "Background detection with OpenCV", Mateusz Stankiewicz,
     http://mateuszstankiewicz.eu/?p=189
*/

import java.awt.*;
import java.io.*;

import com.googlecode.javacv.*;
import com.googlecode.javacv.cpp.*;
import com.googlecode.javacpp.*;
import com.googlecode.javacv.cpp.opencv_core.*;

import com.googlecode.javacv.cpp.opencv_video.BackgroundSubtractorMOG2;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_highgui.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import static com.googlecode.javacv.cpp.opencv_objdetect.*;



public class MogCog 
{
  private static final int DELAY = 100;    // ms

  private static final int MIN_PIXELS = 100;   
          // minimum number of non-black pixels needed for COG calculation


  private static CvMemStorage contourStorage;



  public static void main(String[] args) throws Exception 
  {
    // Preload the opencv_objdetect module to work around a known
    Loader.load(opencv_objdetect.class);

    contourStorage = CvMemStorage.create();

    System.out.println("Initializing frame grabber...");
    OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(CV_CAP_ANY);
    grabber.start();

    IplImage grab = grabber.grab();
    int width  = grab.width();
    int height = grab.height();

    IplImage fgMask = IplImage.create(width, height, IPL_DEPTH_8U, 1);
              /* b&w version of the grabbed image, with movement shown in white,
                 the rest in black, based on the BackgroundSubtractorMOG2 algorithm */

    IplImage background = IplImage.create(width, height, IPL_DEPTH_8U, 3);
              /* the background of the grabbed image as determined by the
                 BackgroundSubtractorMOG2 algorithm */

    CanvasFrame grabCanvas = new CanvasFrame("Camera");
    grabCanvas.setLocation(0, 0);
    CanvasFrame mogCanvas = new CanvasFrame("MOG Info");
    mogCanvas.setLocation(width+5, 0);

    BackgroundSubtractorMOG2 mog = new BackgroundSubtractorMOG2(300, 16, false);
        // a larger motion history tends to create larger blobs
          // motion history, var Threshold, Shadow Detection
    mog.set("nmixtures", 3);    // was 5
    System.out.println("MOG num. mixtures: " + mog.getInt("nmixtures"));
    System.out.println("MOG shadow detection: " + mog.getBool("detectShadows"));
          // in OpenCV version 2.4.2 only detectShadows, history and nmixtures can be set/get;
          // fixed in v.2.4.6
         /* other params: backgroundRatio, varThresholdGen,
                          fVarInit, fVarMin, fVarMax, etc
            explained (a little) in opencv\build\include\opencv2\video\background_segm.hpp
         */
    try {
      System.out.println("MOG background ratio: " + mog.getDouble("backgroundRatio"));
      System.out.println("MOG var threshold gen: " + mog.getDouble("varThresholdGen"));
      System.out.println("MOG fVar init: " + mog.getDouble("fVarInit") + ", min: " +
                   mog.getDouble("fVarMin") + ", max: " + mog.getDouble("fVarMax") );
    }
    catch (RuntimeException e)
    {  System.out.println(e);  }

    // process the grabbed camera image
    while (grabCanvas.isVisible() && mogCanvas.isVisible()) {
      long startTime = System.currentTimeMillis();
      grab = grabber.grab();
      if (grab == null) {
        System.out.println("Image grab failed");
        break;
      }

      //  create a binary mask of foreground objects (and update the background)
      mog.apply(grab, fgMask, 0.005);  // -1);    
                  // learning rate; set close to 0 if initial background varies little
                  // so start out with only the background and wait a few seconds
      mog.getBackgroundImage(background);

      // opening: erosion then dilation to reduce noise
      cvErode(fgMask, fgMask, null, 5);
      cvDilate(fgMask, fgMask, null, 5);
      cvSmooth(fgMask, fgMask, CV_BLUR, 5);  // more noise reduction
      cvThreshold(fgMask, fgMask, 128, 255, CV_THRESH_BINARY);   // make b&w 

      mogCanvas.showImage(fgMask);
      // mogCanvas.showImage(background);

      Point pt = findCOG(fgMask);
      if (pt != null)    // only update COG point if there is a new point
        cvCircle(grab, cvPoint(pt.x, pt.y), 16,      // radius of circle
                            CvScalar.BLUE, CV_FILLED, CV_AA, 0);
      grabCanvas.showImage(grab);

      long duration = System.currentTimeMillis() - startTime;
      System.out.println("Processing time: " + duration);
      if (duration < DELAY) {
        try {
          Thread.sleep(DELAY - duration); 
        }
        catch(InterruptedException e) {}
      }
    }

    grabber.stop();
    grabCanvas.dispose();
    mogCanvas.dispose();
  }  // end of main()



  private static Point findCOG(IplImage maskImg)
  /*  If there are enough non-black pixels in the mask image
      (white means movement), then calculate the moments,
      and use them to calculate the (x,y) center of the white areas.
      These values are returned as a Point object. */
  {
    Point pt = null;

    int numPixels = cvCountNonZero(maskImg);   // non-zero (non-black) means motion
    if (numPixels > MIN_PIXELS) {
      CvMoments moments = new CvMoments();
      cvMoments(maskImg, moments, 1);    // 1 == treat image as binary (0,255) --> (0,1)
      double m00 = cvGetSpatialMoment(moments, 0, 0) ; 
      double m10 = cvGetSpatialMoment(moments, 1, 0) ; 
      double m01 = cvGetSpatialMoment(moments, 0, 1); 

      if (m00 != 0) {   // create COG Point
        int xCenter = (int) Math.round(m10/m00); 
        int yCenter = (int) Math.round(m01/m00);
        pt = new Point(xCenter, yCenter);
      }
    }
    return pt;
  }  // end of findCOG()



}  // end of MogCog class
