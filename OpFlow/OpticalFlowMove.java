
// OpticalFlowMove.java
// Andrew Davison, September 2013, ad@fivedots.coe.psu.ac.th

/* Find 'corners' in an image (blocks with rapidly changing intensities in two
   orthogonal directions) using cvGoodFeaturesToTrack() (and maybe cvFindCornerSubPix()). 
   cvGoodFeaturesToTrack() implements the Shi and Tomasi algorithm for corner finding.

   Use (sparse) optical flow to track these corners from one frame to the next.
   Uses the pyramidal Lucas-Kanade (LK) method, which uses a level of detail pyramid (from
   lowest to highest) so small-to-large amounts of movement can be detected.
   Implemented in OpenCV by cvCalcOpticalFlowPyrLK()

   This example is based on the optical flow example in the OpenCV textbook, p.332-334 and 
   an example at
   http://www.ccs.neu.edu/course/cs7380/f10/HW2/examples/c/lkdemo.c

   The main new aspect of this code is the use of an array of lists to group detected 'directions'
   by their angle. (A direction is the vector between corresponding corners
   in consecutive frames.) The longest directions list is used to update a COG point.
   This approach assumes that the movement in the scene is primarily in a single direction.

   Other features of this code:
     * the drawing of directions as blue arrows;
     * the reuse of detected corner information from one frame grabbing iteration to
       the next, which roughly halves the processing time for an iteration


   OpenCV API docs:
     http://docs.opencv.org/modules/video/doc/motion_analysis_and_object_tracking.html

   Other good descriptions:
     http://robots.stanford.edu/cs223b05/notes/CS%20223-B%20T1%20stavens_opencv_optical_flow.pdf
   and
     http://dasl.mem.drexel.edu/~noahKuntz/openCVTut9.html
*/


import java.awt.*;
import java.io.*;
import java.util.*;

import com.googlecode.javacv.*;
import com.googlecode.javacv.cpp.*;
import com.googlecode.javacpp.*;
import com.googlecode.javacv.cpp.opencv_core.*;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_highgui.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import static com.googlecode.javacv.cpp.opencv_objdetect.*;
import static com.googlecode.javacv.cpp.opencv_video.*;



public class OpticalFlowMove
{
  private static final int DELAY = 100;    // ms

  private static final int WIN_SIZE = 10;   // size of search window (smaller means faster)
  private static final int MAX_CORNERS = 300;

  private static final int MIN_DIR_LENGTH = 15;

  // group directions into lists based on their angles
  private static final int ANGLE_RANGE = 20;     // degree range for each list
  private static final int NUM_ANGLE_LISTS = 360/ANGLE_RANGE;   // number of lists

  private static final int MIN_DIRS_IN_LIST = 5;  

  private static final int MAX_WITHOUT_DIRS = 30; // iterations before COG disappears

  private static int noDirsCount = 0;
  private static int width, height;   // of a grabbed image


  public static void main(String[] args) throws Exception 
  {
    // Preload the opencv_objdetect module to work around a known
    Loader.load(opencv_objdetect.class);

    System.out.println("Initializing frame grabber...");
    OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(CV_CAP_ANY);
    grabber.start();

    CanvasFrame grabCanvas = new CanvasFrame("Optical Flow");

    IplImage grab = grabber.grab();   
        // grab an image, that will be the initial 'previous' frame
    width = grab.width();
    height = grab.height();
    grabCanvas.showImage(grab);

    IplImage prevGray = toGray(grab);

    // create data structures for feature detection and optical flow
    IplImage eigenIm = IplImage.create(width, height, IPL_DEPTH_32F, 1);
    IplImage tempIm = IplImage.create(width, height, IPL_DEPTH_32F, 1);

    // (image) buffers for pyramids
    IplImage pyramidA = IplImage.create(width+8, height/3, IPL_DEPTH_32F, 1);
    IplImage pyramidB = IplImage.create(width+8, height/3, IPL_DEPTH_32F, 1);

    int[] cornerCount = { MAX_CORNERS };
    CvPoint2D32f cornersA = new CvPoint2D32f(MAX_CORNERS);     // used as an array in JavaCV
    CvPoint2D32f cornersB = new CvPoint2D32f(MAX_CORNERS);
    boolean findCorners = true;

    byte[] cornersFound = new byte[MAX_CORNERS];
    float[] trackErrs = new float[MAX_CORNERS];

    int plk_flags = 0;

    CvPoint cogBall = null;    
         // a null value means that no COG ball will be displayed on screen


    // create direction lists array
    @SuppressWarnings("unchecked")   // to avoid warnings about using generics in arrays
    ArrayList<Direction>[] angledDirs = new ArrayList[NUM_ANGLE_LISTS];    
    for (int i=0; i < NUM_ANGLE_LISTS; i++)
      angledDirs[i] = new ArrayList<Direction>();


    // process the grabbed camera image
    while (grabCanvas.isVisible() && ((grab = grabber.grab()) != null)) {
      long startTime = System.currentTimeMillis();
      IplImage grayGrab = toGray(grab);

      // store interesting corners in the cornersA 'array'
      cornersA.position(0);            // reset position in array
      if (findCorners) {
        cvGoodFeaturesToTrack(prevGray, eigenIm, tempIm, 
                 cornersA, cornerCount, 0.01, 5, null, 3, 0, 0.04);
           // min quality level, min distance between corners, 
           // mask (region of interest), block size, use Harris?, k param for Harris  (OpenCV, p.318)
        // System.out.println("No of corners to check: " + cornerCount[0]);

/*
        // improve corners to subpixel level in cornersA; useful when calculating distances
        cvFindCornerSubPix(prevGray, cornersA, cornerCount[0],
             cvSize(WIN_SIZE, WIN_SIZE),  cvSize(-1, -1),
             cvTermCriteria(CV_TERMCRIT_ITER|CV_TERMCRIT_EPS, 20, 0.3)); // OpenCV p.321
*/
      }

      cornersA.position(0);   // reset position in arrays
      cornersB.position(0);
      /* calculate the new positions of the corners (in cornersB) based on the change of the 
         corners in cornersA as they 'move' from
         the previous image (prevGray) to the current image (grayGrab)
      */
      cvCalcOpticalFlowPyrLK(prevGray, grayGrab, 
          pyramidA, pyramidB,
          cornersA, cornersB, cornerCount[0], 
          cvSize(WIN_SIZE,WIN_SIZE), 5,    // no of levels in the pyramids
          cornersFound, trackErrs,
          cvTermCriteria(CV_TERMCRIT_ITER|CV_TERMCRIT_EPS, 20, 0.3),
          plk_flags);    // OpenCV, p.330


      // store the corner pairs (A --> B) as 'directions'
      int numDirs = storeDirs(cornersA, cornersB, cornerCount[0], 
                                            cornersFound, angledDirs, grab);
      cogBall = updateCOG(cogBall, angledDirs, numDirs);
      if (cogBall != null)
        cvCircle(grab, cogBall, 10, CvScalar.RED, CV_FILLED, CV_AA, 0);

      grabCanvas.showImage(grab);
      prevGray = grayGrab;     // save image as previous for next time around

      if (numDirs > 0) {    // could use cornerCount[0]
        // swap corners and pyramids data to speed up next call to cvCalcOpticalFlowPyrLK()
        CvPoint2D32f swapCorners = cornersA;
        cornersA = cornersB;     // no need to recalculate cornersA next time around
        cornersB = swapCorners;
        findCorners = false;
        
        IplImage swapPyramid = pyramidA;
        pyramidA = pyramidB;    // use pyramidB as the 'A' pyramid next time around
        pyramidB = pyramidA;
        plk_flags |= CV_LKFLOW_PYR_A_READY;     // pyramid 'A' is ready
      }
      else {    // no directions found; so call cvGoodFeaturesToTrack() next time to try to improve matters
        findCorners = true;
        cornerCount[0] = MAX_CORNERS;    // reset max no. corners
        plk_flags = 0;
      }

      long duration = System.currentTimeMillis() - startTime;
      // System.out.println("  Processing time: " + duration);
            // ~40ms on average when no cvGoodFeaturesToTrack() call; ~80 ms with the call; 
            // ~100 ms when cvFindCornerSubPix() call also included

      if (duration < DELAY) {
        try {
          Thread.sleep(DELAY - duration); 
        }
        catch(InterruptedException e) {}
      }
    }

    grabber.stop();
    grabCanvas.dispose();
  }  // end of main()



  private static IplImage toGray(IplImage img)
  /* Conversion involves: blurring, converting color to grayscale, 
     and equalization */
  {
    // blur image to get reduce camera noise 
    cvSmooth(img, img, CV_BLUR, 3);  

    // convert to grayscale
    IplImage grayImg = IplImage.create(img.width(), img.height(), IPL_DEPTH_8U, 1);
    cvCvtColor(img, grayImg, CV_BGR2GRAY);  

	cvEqualizeHist(grayImg, grayImg);       // spread out the grayscale range 

    return grayImg;
  }  // end of toGray()



  private static int storeDirs(CvPoint2D32f cornersA, CvPoint2D32f cornersB,
                                        int cornerCount, byte[] cornersFound, 
                                        ArrayList<Direction>[] angledDirs,
                                        IplImage grab)
  /* Calculate directions using the corner info from consecutive frames:
     a direction is cornerA --> cornerB
     If a direction is within the 'good' length range, add it to the directions list array
     and draw it as an arrow on the grabbed image
  */
  {
    for (int i=0; i < NUM_ANGLE_LISTS; i++)    // clear array's lists
      angledDirs[i].clear();
    int numDirs = 0;

    cornersA.position(0);   // reset position in corner arrays
    cornersB.position(0);
    int numErrs = 0;
    int numLongs = 0;
    int numShorts = 0;

    // save corners as directions
    for(int i = 0; i < cornerCount; i++) {
      if (cornersFound[i] == 0)    // not found
        numErrs++;
      else {
        cornersA.position(i);
        cornersB.position(i);
        Direction dir = new Direction(cornersA, cornersB);

        /* if a direction's length is within a 'good' range, 
           then store and draw it */
        double lenDir = dir.getLength();
        if (lenDir > width/8)
          numLongs++;
        else if (lenDir < MIN_DIR_LENGTH)
          numShorts++;
        else {
          dir.drawArrow(grab); // draw direction on the colored grabbed image
          addDir(angledDirs, dir);
          numDirs++;
        }
      }
    }
/*
    if (numErrs > 0)
      System.out.println("No. of feature errors: " + numErrs);
    if (numLongs > 0)
      System.out.println("No. of too long dirs: " + numLongs);
    if (numShorts > 0)
      System.out.println("No. of too short dirs: " + numShorts);
*/
    return numDirs;
  }  // end of storeDirs()



  private static void addDir(ArrayList<Direction>[] angledDirs, Direction dir)
  /* based on the angle of the direction, add it to one of the lists in the
     angledDirs array  */
  {
    int angleIndex = (dir.getAngle()+180)/ANGLE_RANGE;    // angle in range -180 to 180
    if (angleIndex == NUM_ANGLE_LISTS)   // deal with 180 deg angle
      angleIndex = 0;
    angledDirs[angleIndex].add(dir);
  }  // end of addDir()



  private static CvPoint updateCOG(CvPoint cogBall, ArrayList<Direction>[] angledDirs, int numDirs)
  /* if there is a new mean direction then update the COG, otherwise leave it
     unchanged. If there haven't been any directions calculated for MAX_WITHOUT_DIRS iterations
     of the frame grabbing loop, then set the COG value to null to stop it being drawn
  */
  {
    if (numDirs == 0) {
      noDirsCount++;
      if (noDirsCount > MAX_WITHOUT_DIRS) {
        cogBall = null;    // means that COG will not be drawn
        noDirsCount = 0;
      }
    }
    else {  // there are some directions
      CvPoint meanPos = findMeanPos(angledDirs, numDirs);
      if (meanPos != null)
        cogBall = meanPos;
    }

    return cogBall;
  }  // end of updateCOG()




  private static CvPoint findMeanPos(ArrayList<Direction>[] angledDirs, int numDirs)
  /* A mean direction position is found in two steps: first the biggest list in
     the angledDirs array is found. This is the 'most popular' angle range in the detected directions.
     The mean midpoint of those directions is calculated, 
     and returned as a point that will be used to update the COG position.

     The result may be null if there are no directions, or the largest list isn't long enough
     (> MIN_DIRS_IN_LIST)
  */
  {
    CvPoint meanPos = null;

    if (numDirs > 0) {
      // find the largest group of directions
      int maxListSize = 0;
      int largestListIdx = -1;
      for (int i=0; i < NUM_ANGLE_LISTS; i++) {
        if (angledDirs[i].size() > maxListSize) {
          maxListSize = angledDirs[i].size();
          largestListIdx = i;
        }
      }

      if (largestListIdx != -1) {
        // find the mean midpoint in the largest list
        ArrayList<Direction> popDirs = angledDirs[largestListIdx];
        int listSize = popDirs.size();    // group must be at least MIN_DIRS_IN_LIST in size
        if (listSize > MIN_DIRS_IN_LIST) {
          int xTot = 0;
          int yTot = 0;
          for (Direction d : popDirs) {
            CvPoint midpt = d.getMid();
            xTot += midpt.x();
            yTot += midpt.y();
          }
          meanPos = new CvPoint(xTot/listSize, yTot/listSize);
        }
      }
    }
    return meanPos;
  }  // end of findMeanPos()


}  // end of OpticalFlowMove class
