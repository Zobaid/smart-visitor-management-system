package fsktm.um.edu.my;

import fsktm.um.edu.my.util.ImageUtils;
import net.coobird.thumbnailator.filters.Caption;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.bytedeco.javacpp.opencv_core.Point;
import static org.bytedeco.javacpp.opencv_core.Scalar;
import static org.bytedeco.javacpp.opencv_imgproc.*;


public class SVMS {

    private static final Logger logger = LoggerFactory.getLogger(SVMS.class);

    private FFmpegFrameGrabber frameGrabber;
    private OpenCVFrameConverter.ToMat toMatConverter = new OpenCVFrameConverter.ToMat();
    private volatile boolean running = false;

    private HaarFaceDetector faceDetector = new HaarFaceDetector();
    private CNNAgeDetector ageDetector = new CNNAgeDetector();
    private CNNGenderDetector genderDetector = new CNNGenderDetector();

    private JFrame window;
    private JPanel videoPanel;

    public SVMS() {
        window = new JFrame();
        videoPanel = new JPanel();

        window.setLayout(new BorderLayout());
        window.setSize(new Dimension(680, 480));
        window.add(videoPanel, BorderLayout.CENTER);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stop();
            }
        });
    }

    /**
     * Starts the frame grabbers and then the frame processing. Grabbed and processed frames will be displayed in the
     * {@link #videoPanel}
     */
    public void start() {
        frameGrabber = new FFmpegFrameGrabber("/dev/video0");
        frameGrabber.setFormat("video4linux2");
        frameGrabber.setImageWidth(680);
        frameGrabber.setImageHeight(480);

        logger.debug("Starting frame grabber");
        try {
            frameGrabber.start();

            logger.debug("Started frame grabber with image width-height : {}-{}", frameGrabber.getImageWidth(), frameGrabber.getImageHeight());



        } catch (FrameGrabber.Exception e) {
            logger.error("Error when initializing the frame grabber", e);
            throw new RuntimeException("Unable to start the FrameGrabber", e);
        }

        SwingUtilities.invokeLater(() -> {
            window.setVisible(true);
        });

        process();


        logger.debug("Stopped frame grabbing.");
    }

    /**
     * Private method which will be called to star frame grabbing and carry on processing the grabbed frames
     */
    private void process() {
        running = true;
        while (running) {
            try {
                // Here we grab frames from our camera
                final Frame frame = frameGrabber.grab();

                //frame.image

                Map<CvRect, Mat> detectedFaces = faceDetector.detect(frame);
                Mat mat = toMatConverter.convert(frame);

                detectedFaces.entrySet().forEach(rectMatEntry -> {
                    String age = ageDetector.predictAge(rectMatEntry.getValue(), frame);
                    CNNGenderDetector.Gender gender = genderDetector.predictGender(rectMatEntry.getValue(), frame);

                    String caption = String.format("%s:[%s]", gender, age);

                    java.util.Date dt = new java.util.Date();

                    java.text.SimpleDateFormat sdf =
                            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    String currentTime = sdf.format(dt);



                    logger.debug("Face's caption : {}", caption);
                    //System.out.println("info is "+caption);

                    rectangle(mat, new Point(rectMatEntry.getKey().x(), rectMatEntry.getKey().y()),
                            new Point(rectMatEntry.getKey().width() + rectMatEntry.getKey().x(), rectMatEntry.getKey().height() + rectMatEntry.getKey().y()),
                            Scalar.RED, 2, CV_AA, 0);

                    int posX = Math.max(rectMatEntry.getKey().x() - 10, 0);
                    int posY = Math.max(rectMatEntry.getKey().y() - 10, 0);
                    putText(mat, caption, new Point(posX, posY), CV_FONT_HERSHEY_PLAIN, 1.0,
                            new Scalar(255, 255, 255, 2.0));

                    //File imageFile = saveImageIntoLocalDir(mat);

                    String splittedAge [] = age.split("-");

                    int ageMid = (Integer.parseInt(splittedAge[0]) + Integer.parseInt(splittedAge[1]) ) /2;

                    String ageGroup = "";

                    if(ageMid<=14 ) {
                       ageGroup = "Child";
                    }
                    else  {
                        ageGroup = "Adult";
                    }
                    Frame processedFrame = toMatConverter.convert(mat);

                    Graphics graphics = videoPanel.getGraphics();
                    BufferedImage resizedImage = ImageUtils.getResizedBufferedImage(processedFrame, videoPanel);
                    SwingUtilities.invokeLater(() -> {
                        graphics.drawImage(resizedImage, 0, 0, videoPanel);


                    });



                    //saveIntoDatabase(gender.name(), ageGroup, "null", currentTime,age);
                    //saveIntoDatabase(gender.name(), ageGroup, imageFile.getAbsolutePath(), currentTime);
                });

                //Thread.sleep(80);

            } catch (FrameGrabber.Exception e) {
                logger.error("Error when grabbing the frame", e);
            } catch (Exception e) {
                logger.error("Unexpected error occurred while grabbing and processing a frame", e);
            }
        }
    }

    /**
     * Stops and released resources attached to frame grabbing. Stops frame processing and,
     */
    public void stop() {
        running = false;
        try {
            logger.debug("Releasing and stopping FrameGrabber");
            frameGrabber.release();
            frameGrabber.stop();
        } catch (FrameGrabber.Exception e) {
            logger.error("Error occurred when stopping the FrameGrabber", e);
        }

        window.dispose();
    }

    /*private File saveImageIntoLocalDir(Mat mat) {
        // Show the processed mat in UI
        Frame processedFrame = toMatConverter.convert(mat);

        Graphics graphics = videoPanel.getGraphics();
        BufferedImage resizedImage = ImageUtils.getResizedBufferedImage(processedFrame, videoPanel);



        SwingUtilities.invokeLater(() -> {
            graphics.drawImage(resizedImage, 0, 0, videoPanel);
        });
        File imageFile = new File("/home/sk/Documents/images/image"+System.currentTimeMillis()+".jpg");

        try {
            ImageIO.write(resizedImage, "jpg", imageFile);

            Thread.sleep(000);
        }
        catch (Exception e) {

        }

        return imageFile;
    }
*/


    // todo : save the location of the image
    public  void saveIntoDatabase(String gender, String  agegroup, String imageFile, String currentTime,String estimatedage){

        try{
            String aurl= "jdbc:mysql://localhost:3306/sample1";
            String username="root";
            String password ="root";


            Connection myconnection = DriverManager.getConnection(aurl,username,password );

            Statement myStatement= myconnection.createStatement();

                //myStatement.executeUpdate("INSERT INTO s1(time,estimation)" +
                    //    "VALUES (date, info)");

            String query = " insert into s1 (gender, age_group, image_path, time, estimation)"
                    + " values (?, ?, ?, ?, ?)";
            PreparedStatement preparedStmt = myconnection.prepareStatement(query);
            preparedStmt.setString (1, gender);
            preparedStmt.setString (2, agegroup);
            preparedStmt.setString (3, imageFile);
            preparedStmt.setString (4, currentTime);
            preparedStmt.setString (5, estimatedage);

            preparedStmt.execute();



            ResultSet myResultset =myStatement.executeQuery("Select  * from s1");

            //date formatting




            while(myResultset.next()){

                System.out.println(" full dataset of detected face : " + myResultset.getString("time") +" "+ myResultset.getString("estimation")+" "+myResultset.getString("gender"));

            }
            myconnection.close();

            //
             Thread.sleep(2000);



        }catch (Exception e){

            System.out.println(e.getMessage());
        }





    }

    public static void main(String[] args) {
        SVMS javaCVExample = new SVMS();

        logger.info("Starting SVMS");
        new Thread(javaCVExample::start).start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Stopping SVMS");
            javaCVExample.stop();
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) { }
    }
}
