package com.example.potholedetector.utils;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PotholeDetector {

    private static final String TAG = "PotholeDetector";
    private Module model;

    // Define size thresholds (in pixels squared) - same as the Python code
    private final double smallThreshold = 5000;    // Areas below this are small potholes
    private final double largeThreshold = 15000;   // Areas above this are large potholes

    // Colors for different size categories (BGR format in OpenCV)
    private final Scalar smallColor = new Scalar(0, 255, 0);     // Green for small
    private final Scalar mediumColor = new Scalar(0, 165, 255);  // Orange for medium
    private final Scalar largeColor = new Scalar(0, 0, 255);     // Red for large

    public PotholeDetector(String modelPath) {
        // Load the YOLO model
        try {
            model = Module.load(modelPath);
            Log.d(TAG, "Model loaded successfully from: " + modelPath);
        } catch (Exception e) {
            Log.e(TAG, "Error loading PyTorch model: " + e.getMessage(), e);
            throw new RuntimeException("Error loading PyTorch model: " + e.getMessage());
        }
    }

    public DetectionResult processFrame(Mat frame) {
        DetectionResult result = new DetectionResult();

        try {
            // Convert the frame to the format expected by the model
            Mat inputFrame = frame.clone();

            // Convert BGR to RGB
            Imgproc.cvtColor(inputFrame, inputFrame, Imgproc.COLOR_BGR2RGB);

            // Convert to Bitmap for PyTorch processing
            Bitmap bitmap = Bitmap.createBitmap(inputFrame.cols(), inputFrame.rows(), Bitmap.Config.ARGB_8888);
            org.opencv.android.Utils.matToBitmap(inputFrame, bitmap);

            // Convert to Tensor
            FloatBuffer inputBuffer = Tensor.allocateFloatBuffer(3 * bitmap.getWidth() * bitmap.getHeight());
            float[] mean = {0.485f, 0.456f, 0.406f};
            float[] std = {0.229f, 0.224f, 0.225f};

            TensorImageUtils.bitmapToFloatBuffer(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    mean, std, inputBuffer, 0);

            Tensor inputTensor = Tensor.fromBlob(inputBuffer, new long[]{1, 3, bitmap.getHeight(), bitmap.getWidth()});

            // Run inference
            // Fixed the problematic line that used toDictionary()
            IValue output = model.forward(IValue.from(inputTensor));
            Map<String, IValue> outputs = new HashMap<>();

            // Handle the model output based on YOLOv8-seg format
            // This is a simplified approach - you'll need to adapt based on your exact model output structure
            if (output.isTuple()) {
                IValue[] tuple = output.toTuple();
                if (tuple.length > 0) {
                    outputs.put("detection", tuple[0]); // Detection results
                    if (tuple.length > 1) {
                        outputs.put("masks", tuple[1]); // Segmentation masks
                    }
                }
            } else {
                // Single output - store it with a generic key
                outputs.put("output", output);
            }

            // Create a copy of the original frame for drawing
            Mat processedFrame = frame.clone();
            Mat overlay = frame.clone();
            Mat heatmapUpdate = Mat.zeros(frame.size(), CvType.CV_8UC1);

            // Process outputs to get mask contours
            // Note: This is a simplified version as the full YOLOv8-seg output processing is complex
            // In a real implementation, you'd extract masks from model output
            List<MatOfPoint> contours = extractContoursFromOutput(outputs, frame);

            // Process each contour
            for (MatOfPoint contour : contours) {
                // Calculate area to determine size
                double area = Imgproc.contourArea(contour);

                // Store for report
                result.areas.add(area);

                // Get centroid
                Point centroid = calculateCentroid(contour);

                // Classify pothole size based on area
                String sizeCategory;
                Scalar color;
                int thickness;

                if (area < smallThreshold) {
                    sizeCategory = "Small";
                    color = smallColor;
                    thickness = 2;
                    result.smallCount++;
                } else if (area > largeThreshold) {
                    sizeCategory = "Large";
                    color = largeColor;
                    thickness = 4;
                    result.largeCount++;
                } else {
                    sizeCategory = "Medium";
                    color = mediumColor;
                    thickness = 3;
                    result.mediumCount++;
                }

                // Calculate risk level
                String riskLevel = calculateRiskLevel(sizeCategory, centroid, frame.height());
                Scalar riskColor;

                switch (riskLevel) {
                    case "High":
                        riskColor = new Scalar(0, 0, 255);
                        result.highRiskCount++;
                        break;
                    case "Medium":
                        riskColor = new Scalar(0, 165, 255);
                        result.mediumRiskCount++;
                        break;
                    default:
                        riskColor = new Scalar(0, 255, 0);
                        result.lowRiskCount++;
                        break;
                }

                // Add to detected potholes
                PotholeInfo potholeInfo = new PotholeInfo();
                potholeInfo.centroid = centroid;
                potholeInfo.area = area;
                potholeInfo.size = sizeCategory;
                potholeInfo.risk = riskLevel;
                result.detectedPotholes.add(potholeInfo);

                // Draw contour boundary
                Imgproc.drawContours(processedFrame, List.of(contour), -1, color, thickness);

                // Fill contour area in overlay
                Imgproc.drawContours(overlay, List.of(contour), -1, color, -1);

                // Add to heatmap
                Imgproc.drawContours(heatmapUpdate, List.of(contour), -1, new Scalar(255), 1);

                // Add label with size and risk
                String label = sizeCategory + " (Risk: " + riskLevel + ")";

                // Get bounding rectangle for text positioning
                org.opencv.core.Rect boundingRect = Imgproc.boundingRect(contour);
                Point textPosition = new Point(boundingRect.x, boundingRect.y - 10);

                Imgproc.putText(processedFrame, label, textPosition,
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 255), 2);

                // Draw centroid for tracking visualization
                Imgproc.circle(processedFrame, centroid, 4, new Scalar(255, 0, 255), -1);
            }

            // Blend overlay with original
            Core.addWeighted(overlay, 0.4, processedFrame, 0.6, 0, processedFrame);

            // Set processed frame in result
            result.processedFrame = processedFrame;
            result.heatmapUpdate = heatmapUpdate;

            // Clean up
            bitmap.recycle();
            inputFrame.release();
            overlay.release();

        } catch (Exception e) {
            Log.e(TAG, "Error processing frame: " + e.getMessage(), e);
            e.printStackTrace();
        }

        return result;
    }

    // Extract contours from model output
    private List<MatOfPoint> extractContoursFromOutput(Map<String, IValue> outputs, Mat frame) {
        // This method should extract segmentation masks from model output and convert to contours
        // For demonstration purposes, we'll use simple simulated contours
        // In a real implementation, you'd decode YOLO output tensors to get masks

        List<MatOfPoint> contours = new ArrayList<>();

        try {
            // If we have actual mask data from the model
            if (outputs.containsKey("masks") && outputs.get("masks") != null) {
                // Process actual model output to get masks
                // This would depend on your model's specific output format

                // For YOLOv8-seg, you'd typically:
                // 1. Get the detection tensor to find bounding boxes and classes
                // 2. Get the mask prototypes tensor
                // 3. For each detection, apply the corresponding mask coefficients
                // 4. Threshold the result to get a binary mask
                // 5. Find contours in the binary mask

                // Example placeholder:
                // Tensor masksTensor = outputs.get("masks").toTensor();
                // float[] masksData = masksTensor.getDataAsFloatArray();
                // Process mask data...
            } else {
                // Generate simulated contours (remove this in production)
                contours = simulateContours(frame);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting contours: " + e.getMessage(), e);
            // Fall back to simulated contours
            contours = simulateContours(frame);
        }

        return contours;
    }

    // Method to calculate centroid of a contour
    private Point calculateCentroid(MatOfPoint contour) {
        org.opencv.imgproc.Moments moments = Imgproc.moments(contour);
        if (moments.get_m00() != 0) {
            return new Point(moments.get_m10() / moments.get_m00(), moments.get_m01() / moments.get_m00());
        } else {
            org.opencv.core.Rect boundingRect = Imgproc.boundingRect(contour);
            return new Point(boundingRect.x + boundingRect.width / 2.0, boundingRect.y + boundingRect.height / 2.0);
        }
    }

    // Calculate risk level based on size and position
    private String calculateRiskLevel(String sizeCategory, Point position, int frameHeight) {
        // Determine risk level based on size and position (center of road is more severe)
        double roadCenter = frameHeight / 2.0;
        double centerFactor = 1 - (Math.abs(position.y - roadCenter) / roadCenter);  // 1 at center, 0 at edges

        double riskBase;
        if (sizeCategory.equals("Large")) {
            riskBase = 2;  // High base risk
        } else if (sizeCategory.equals("Medium")) {
            riskBase = 1;  // Medium base risk
        } else {  // Small
            riskBase = 0;  // Low base risk
        }

        // Adjust risk by position factor
        double riskAdjusted = riskBase + centerFactor;

        // Classify final risk
        if (riskAdjusted >= 2) {
            return "High";
        } else if (riskAdjusted >= 1) {
            return "Medium";
        } else {
            return "Low";
        }
    }

    // Note: This method simulates detecting contours for testing
    // In a real implementation, you'd extract masks from the YOLOv8-seg output
    private List<MatOfPoint> simulateContours(Mat frame) {
        // This is a placeholder for testing - in a real implementation
        // you would process the actual YOLOv8-seg outputs to get masks

        List<MatOfPoint> contours = new ArrayList<>();

        // Create a simple binary mask for demonstration
        Mat grayImage = new Mat();
        Imgproc.cvtColor(frame, grayImage, Imgproc.COLOR_BGR2GRAY);

        // Apply thresholding to create a binary mask
        Mat binaryMask = new Mat();
        Imgproc.threshold(grayImage, binaryMask, 100, 255, Imgproc.THRESH_BINARY);

        // Apply some morphological operations to create blob-like shapes
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(20, 20));
        Imgproc.morphologyEx(binaryMask, binaryMask, Imgproc.MORPH_CLOSE, kernel);

        // Find contours in the binary mask
        List<MatOfPoint> allContours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binaryMask, allContours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Filter contours by area - select a few random ones to simulate potholes
        for (MatOfPoint contour : allContours) {
            double area = Imgproc.contourArea(contour);
            if (area > 1000 && area < 20000 && Math.random() > 0.7) {
                contours.add(contour);
            }
        }

        // Clean up
        grayImage.release();
        binaryMask.release();
        hierarchy.release();

        return contours;
    }

    // Inner class to hold detection results
    public static class DetectionResult {
        public Mat processedFrame;
        public Mat heatmapUpdate;
        public int smallCount;
        public int mediumCount;
        public int largeCount;
        public int lowRiskCount;
        public int mediumRiskCount;
        public int highRiskCount;
        public List<Double> areas;
        public List<PotholeInfo> detectedPotholes;

        public DetectionResult() {
            processedFrame = new Mat();
            heatmapUpdate = new Mat();
            smallCount = 0;
            mediumCount = 0;
            largeCount = 0;
            lowRiskCount = 0;
            mediumRiskCount = 0;
            highRiskCount = 0;
            areas = new ArrayList<>();
            detectedPotholes = new ArrayList<>();
        }
    }

    // Inner class to hold pothole information
    public static class PotholeInfo {
        public Point centroid;
        public double area;
        public String size;
        public String risk;
    }
}