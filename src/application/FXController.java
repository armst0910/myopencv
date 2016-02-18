package application;



import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

public class FXController {
	@FXML
	private Button start_btn;
	@FXML
	private ImageView currentFrame;
	@FXML
	private Button load_btn;
	
	private ScheduledExecutorService timer;
	
	private VideoCapture capture = new VideoCapture();
	
	private boolean cameraActive = false;
	
	final FileChooser fileChooser = new FileChooser();
	
	private Queue<List<Point>> queue2 = new LinkedList<>();

	
	@FXML
	protected void startCamera(ActionEvent event){
		if(!this.cameraActive){
			if(this.capture.isOpened()){
				
				this.cameraActive = true;
				
				Runnable frameGrabber = new Runnable() {
					
					@Override
					public void run() {
						
						Image imageToShow = grabFrame();
						currentFrame.setImage(imageToShow);
					}
				};
				
				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 60, TimeUnit.MILLISECONDS);
				
				this.start_btn.setText("Pause Video");
			} else{
				System.err.println("Impossible to open the camera connection...");
			}
			
		} else{
			this.cameraActive = false;
			
			this.start_btn.setText("Play Video");
			
			
			try {
				
				this.timer.shutdown();
				this.timer.awaitTermination(60, TimeUnit.MILLISECONDS);
				
			} catch (InterruptedException  e) {
				System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
			}
			
			//this.capture.release();
			
			//this.currentFrame.setImage(null);
		}
	}
	@FXML
	protected void browVideo(ActionEvent event) {
		File file = fileChooser.showOpenDialog(null);
		if (file != null) {
			this.capture.open(file.getAbsolutePath());
		}
	}
	
	
	
	//สร้าง frame ที่แสดง video
	private Image grabFrame(){
		Image imageToShow = null;
		Mat frame = new Mat();
		
		if(this.capture.isOpened()){
			try {
				this.capture.read(frame);
				if(!frame.empty()){
					Mat blurredImage = new Mat();
					Mat hsvImage = new Mat();
					Mat mask = new Mat();
					Mat morphOutput = new Mat();
					
					Imgproc.blur(frame, blurredImage, new Size(10, 10));
					Imgproc.cvtColor(blurredImage, hsvImage, Imgproc.COLOR_BGR2HSV);
					
					//set color
					Scalar minValue = new Scalar(162, 161, 86);
					
					Scalar maxValue = new Scalar(180, 255, 255);
					
					Core.inRange(hsvImage, minValue, maxValue, mask);
					
					Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(24, 24));
					Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12));
					
					Imgproc.erode(mask, morphOutput, erodeElement);
					Imgproc.erode(mask, morphOutput, erodeElement);
					
					Imgproc.dilate(mask, morphOutput, dilateElement);
					Imgproc.dilate(mask, morphOutput, dilateElement);	
					
					frame = this.findAndDrawContour(morphOutput, frame);
					
					imageToShow = mat2Image(frame);
					
					
				}
				
			} catch (Exception e) {
				System.err.println("Exception during the image elaboration: " + e);
			}
		}
		
		
		return imageToShow;
	}
//แปลง Mat เป็น Image
	private Image mat2Image(Mat frame){
		
		MatOfByte buffer = new MatOfByte();
		
		Imgcodecs.imencode(".png", frame, buffer);
		
		
		return new Image(new ByteArrayInputStream(buffer.toArray()));
	}

	private List<Point> findCentroid(MatOfPoint2f[] contours){
		
		List<Point> centroid = new ArrayList<>();
		
		for (int i = 0; i < contours.length; i++) {
			Moments m = Imgproc.moments(contours[i]);
			
			int cx = (int) (m.m10/m.m00);
			int cy = (int) (m.m01/m.m00);
			
			centroid.add(new Point(cx, cy));
		}
		
		return centroid;
	}
	
	private Mat drawPath(MatOfPoint2f[] contours, Mat frame){
		List<Point> centroid = findCentroid(contours);
		//this.queue.add(centroid);
		this.queue2.add(centroid);
		Point last = null; int count = 1;

		
		for (List<Point> current : this.queue2) {
			if(count % 30 == 0){ this.queue2.remove(); }
			for (int i = 0; i < current.size(); i++) {
				if(last != null && current.get(i) != null){
					Imgproc.line(frame, current.get(i), last, new Scalar(255, 0, 0),2);
				}
				last = current.get(i);
			}
			count++;
		}
		
		return frame;
	}
	
	private Mat findAndDrawContour(Mat maskedImage, Mat frame){
		List<MatOfPoint> contours = new ArrayList<>();
		Mat hierarchy = new Mat();
		
		
		
		Imgproc.findContours(maskedImage, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
		
		Rect[] boundRect = new Rect[contours.size()];
		MatOfPoint2f[] contours_poly = new MatOfPoint2f[contours.size()];
		Point[] center = new Point[contours.size()];
		float[] radius = new float[contours.size()];
		
		
		
		
		for (int i = 0; i < contours.size(); i++) {
			contours_poly[i] = new MatOfPoint2f();
			center[i] = new Point();
			MatOfPoint2f curve = new MatOfPoint2f(contours.get(i).toArray());
			boundRect[i] = Imgproc.boundingRect(new MatOfPoint(curve.toArray()));
			Imgproc.approxPolyDP(curve, contours_poly[i], 3.0, true);
			Imgproc.minEnclosingCircle(contours_poly[i], center[i], radius);
			
		}
		
		frame = drawPath(contours_poly, frame);
		
		
		for (int i = 0; i < contours_poly.length; i++)
		{
			//if(objectBoundingRectangle.area()>500)
			//Imgproc.drawContours(frame, contours, i,  new Scalar(0,255,0));
			//Imgproc.circle(frame, center[i], (int)radius[i], new Scalar(0,255,0), 2);
			//Imgproc.line(frame, center[i], center[i],  new Scalar(255,0,0));
			if(boundRect[i].area()>500)
			Imgproc.rectangle(frame, boundRect[i].tl(), boundRect[i].br(), new Scalar(0,255,0));
			
			
			
		}

		
		return frame;
	}
}
