package MotionDetection;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import java.util.*;
import java.io.*;
import java.util.List;


public class GetUserTimeline {
    /**
     * Usage: java twitter4j.examples.timeline.GetUserTimeline
     *
     * @param args String[]
     */
        Twitter twitter = new TwitterFactory().getInstance();
    public static String feed="";
   public static void main(String[] args) {
        // gets Twitter instance with default credentials
       GetUserTimeline g = new GetUserTimeline();
       String str = g.getFeed();
      // System.out.println(str);
       
   }
    public String getFeed()
    {
     
       try {
            List<Status> statuses;
            String user="";
           // if (args.length == 0) {
            
            Scanner s = new Scanner(new File("twitter_user.txt"));
            
                String temp = s.nextLine();
                char first = temp.charAt(0);
                for(int i=1;i<temp.length();i++)
                    user+=temp.charAt(i);
                statuses = twitter.getUserTimeline(user);
            
               // user = twitter.verifyCredentials().getScreenName();
               // statuses = twitter.getUserTimeline();
            
          //  System.out.println("Showing @" + user + "'s user timeline.");
            for (Status status : statuses) {
               feed+= first + status.getUser().getScreenName() + " - " + status.getText();
            }
           // System.out.print(feed);
        } catch (Exception te) {
            te.printStackTrace();
            //System.out.println("Failed to get timeline: " + te.getMessage());
            System.exit(-1);
        }
       return feed;
    }
}
