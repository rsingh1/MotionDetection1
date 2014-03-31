package MotionDetection;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;


public class MotionDetector extends JFrame 
{
  // GUI components
 public MotionPanel motionPanel;


  public MotionDetector()
  {
    super("University Of Bedfordshire");

    Container c = getContentPane();
    c.setLayout( new BorderLayout() );   

    motionPanel = new MotionPanel(); // the sequence of pictures appear here
    c.add( motionPanel, BorderLayout.CENTER);

    addWindowListener( new WindowAdapter() {
      public void windowClosing(WindowEvent e)
      { motionPanel.closeDown();    // stop snapping pics
        System.exit(0);
      }
    });
    System.out.println("This is gCogx"+motionPanel.gCogx);
    System.out.println("This is gCogy"+motionPanel.gCogy);
    setResizable(false);
    pack();  
    setLocationRelativeTo(null);
    setVisible(false);
  } // end of MotionDetector()

public int getCogx()
{
    int x = motionPanel.getCogx();
    return x;
}
  
public int getCogy()
{
    int x = motionPanel.getCogy();
    return x;
}
    

  // -------------------------------------------------------

  public static void main( String args[] )
  {  new MotionDetector();  }

} // end of MotionDetector class
