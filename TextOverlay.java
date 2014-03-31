package MotionDetection;
import java.awt.*;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.JPanel;

public class TextOverlay extends JPanel {

    public BufferedImage image;

    public TextOverlay() {
        try {
           // image = ImageIO.read(new File(
                //"Desert.jpg"));
Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
double width = screenSize.getWidth();
double height = screenSize.getHeight();

              // image =new BufferedImage((int)width, (int)height,BufferedImage.TYPE_INT_ARGB);
              image = ImageIO.read(new File("bd.jpg"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.setPreferredSize(new Dimension(
            image.getWidth(), image.getHeight()));
        image = process(image);
        File outputfile = new File("ot.jpg");
        try{
    ImageIO.write(image, "jpg", outputfile);
        }
        catch(Exception e){}
        //outputfile.close();
    }

     BufferedImage process(BufferedImage old) {
        int w = old.getWidth();
        int h = old.getHeight();
        BufferedImage img = new BufferedImage(
            w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.drawImage(old, 0, 0, null);
        g2d.setPaint(Color.BLACK);
        g2d.setFont(new Font("Quantico", Font.PLAIN, 20));
        GetUserTimeline g = new GetUserTimeline();
       String args[];
       String s = g.getFeed();
       
        String s1="";
        for(int i=0;i<s.length();i++)
        {
            if(i%140==0)
                s1+='\n';
            else
                s1+=s.charAt(i);
        }
        
          String st[] = s1.split("\n");
        FontMetrics fm = g2d.getFontMetrics();
        
        int x = img.getWidth() - 5;
        int y = fm.getHeight();
        
        for(int i=0;i<st.length;i++)
        {
           g2d.drawString(st[i], 30, y+i*30);
           // System.out.println(st[i]);
        }
       // g2d.drawString("sssssssssssss", 30, 30);
        g2d.dispose();
        return img;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, null);
    } 

    private static void create() {
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new TextOverlay());
        f.pack();
        f.setVisible(true);
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                create();
            }
        });
    }
}
