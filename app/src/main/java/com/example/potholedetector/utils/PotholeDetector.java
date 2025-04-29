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

    // Define size thresholds (in pixels squared) - SAME AS PYTHON CODE
    private final double smallThreshold = 5000;    // Areas below this are small potholes
    private final double largeThreshold = 15000;   // Areas above this are large potholes

    // Colors for different size categories (BGR format in OpenCV)
    private final Scalar smallColor = new Scalar(0, 255, 0);     // Green for small
    private final Scalar mediumColor = new Scalar(0, 165, 255);  // Orange for medium
    private final Scalar largeColor = new Scalar(0, 0, 255);     // Red for large

    // YOLOv8 input dimensions - YOLOv8 typically uses square inputs
    private final int YOLO_INPUT_SIZE = 640;

    // Normalization parameters for PyTorch - using same as YOLOv8
    private static final float[] NORM_MEAN = new float[]{0.485f, 0.456f, 0.406f};
    private static final float[] NORM_STD = new float[]{0.229f, 0.224f, 0.225f};

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

        // Initialize with a valid frame to avoid null/empty Mat issues
        result.processedFrame = frame.clone();
        result.heatmapUpdate = Mat.zeros(frame.size(), CvType.CV_8UC1);

        try {
            // ---------- Step 1: Prepare display frame ----------
            // The display frame size should match the Python code (1020x500)
            Mat displayFrame = frame.clone();
            if (displayFrame.cols() != 1020 || displayFrame.rows() != 500) {
                Imgproc.resize(displayFrame, displayFrame, new Size(1020, 500));
            }

            // ---------- Step 2: Prepare model input ----------
            // Clone and convert to RGB (YOLOv8 expects RGB)
            Mat inputFrame = frame.clone();
            Imgproc.cvtColor(inputFrame, inputFrame, Imgproc.COLOR_BGR2RGB);

            // Resize to square input - YOLOv8 standard
            Mat resizedFrame = new Mat();
            Imgproc.resize(inputFrame, resizedFrame, new Size(YOLO_INPUT_SIZE, YOLO_INPUT_SIZE));

            // Convert to Bitmap for PyTorch processing
            Bitmap bitmap = Bitmap.createBitmap(YOLO_INPUT_SIZE, YOLO_INPUT_SIZE, Bitmap.Config.ARGB_8888);
            org.opencv.android.Utils.matToBitmap(resizedFrame, bitmap);

            // Prepare tensor input
            FloatBuffer inputBuffer = Tensor.allocateFloatBuffer(3 * YOLO_INPUT_SIZE * YOLO_INPUT_SIZE);
            TensorImageUtils.bitmapToFloatBuffer(
                    bitmap,
                    0, 0,
                    YOLO_INPUT_SIZE,
                    YOLO_INPUT_SIZE,
                    NORM_MEAN,
                    NORM_STD,
                    inputBuffer,
                    0
            );

            Tensor inputTensor = Tensor.fromBlob(
                    inputBuffer,
                    new long[]{1, 3, YOLO_INPUT_SIZE, YOLO_INPUT_SIZE}
            );

            // ---------- Step 3: Run inference ----------
            boolean modelSuccessful = false;
            try {
                // Forward pass through the model
                IValue output = model.forward(IValue.from(inputTensor));

                // Process model output
                // For YOLOv8-seg, the output should contain both boxes and masks
                // This would need model-specific interpretation
                Log.d(TAG, "Model inference completed successfully");

                // TODO: Extract segmentation masks from model output
                // This would require model-specific processing

                modelSuccessful = true;

                // If successful, the real mask processing would go here
                // Since we can't directly parse the output without the exact model format,
                // we'll use simulation for now

            } catch (Exception e) {
                Log.e(TAG, "Model inference error: " + e.getMessage(), e);
                // Fall back to simulation
            }

            // ---------- Step 4: Process detections (simulated for now) ----------
            // Get contours (either from model output or simulated)
            List<MatOfPoint> contours = simulateContours(displayFrame);
            Mat overlay = displayFrame.clone();

            // Process each contour
            for (MatOfPoint contour : contours) {
                // Calculate area to determine size
                double area = Imgproc.contourArea(contour);

                // Store for report
                result.areas.add(area);

                // Get centroid for tracking & risk assessment
                Point centroid = calculateCentroid(contour);

                // Classify pothole size based on area - USING PYTHON CODE THRESHOLDS
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
                String riskLevel = calculateRiskLevel(sizeCategory, centroid, displayFrame.height());
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
                Imgproc.drawContours(displayFrame, List.of(contour), -1, color, thickness);

                // Fill contour area in overlay
                Imgproc.drawContours(overlay, List.of(contour), -1, color, -1);

                // Add to heatmap
                Imgproc.drawContours(result.heatmapUpdate, List.of(contour), -1, new Scalar(255), 1);

                // Add label with size and risk
                String label = sizeCategory + " (Risk: " + riskLevel + ")";

                // Get bounding rectangle for text positioning
                org.opencv.core.Rect boundingRect = Imgproc.boundingRect(contour);
                Point textPosition = new Point(boundingRect.x, boundingRect.y - 10);

                Imgproc.putText(displayFrame, label, textPosition,
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 255), 2);

                // Draw centroid for tracking visualization
                Imgproc.circle(displayFrame, centroid, 4, new Scalar(255, 0, 255), -1);
            }

            // Blend overlay with original (40% overlay, 60% original)
            Core.addWeighted(overlay, 0.4, displayFrame, 0.6, 0, displayFrame);

            // Set final result
            if (result.processedFrame != null && !result.processedFrame.empty()) {
                result.processedFrame.release();
            }
            result.processedFrame = displayFrame;

            // Clean up
            bitmap.recycle();
            inputFrame.release();
            resizedFrame.release();
            overlay.release();

            // Clean up contours
            for (MatOfPoint contour : contours) {
                contour.release();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing frame: " + e.getMessage(), e);
            e.printStackTrace();

            // Ensure we still have a valid frame even after an error
            if (result.processedFrame == null || result.processedFrame.empty() || result.processedFrame.dims() != 2) {
                if (result.processedFrame != null && !result.processedFrame.empty()) {
                    result.processedFrame.release();
                }
                result.processedFrame = frame.clone();
            }

            // Set up a blank heatmap if needed
            if (result.heatmapUpdate == null || result.heatmapUpdate.empty()) {
                result.heatmapUpdate = Mat.zeros(result.processedFrame.size(), CvType.CV_8UC1);
            }
        }

        return result;
    }

    // Method to calculate centroid of a contour - SAME AS PYTHON CODE
    private Point calculateCentroid(MatOfPoint contour) {
        org.opencv.imgproc.Moments moments = Imgproc.moments(contour);
        if (moments.get_m00() != 0) {
            return new Point(moments.get_m10() / moments.get_m00(), moments.get_m01() / moments.get_m00());
        } else {
            org.opencv.core.Rect boundingRect = Imgproc.boundingRect(contour);
            return new Point(boundingRect.x + boundingRect.width / 2.0, boundingRect.y + boundingRect.height / 2.0);
        }
    }

    // Calculate risk level based on size and position - SAME AS PYTHON CODE
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

    // Simulate contours for testing - based on the Python simulation approach
    private List<MatOfPoint> simulateContours(Mat frame) {
        // This is a placeholder for testing - in a real implementation
        // you would process the actual YOLOv8-seg outputs to get masks

        List<MatOfPoint> contours = new ArrayList<>();
        List<MatOfPoint> allContours = new ArrayList<>();
        Mat grayImage = new Mat();
        Mat binaryMask = new Mat();
        Mat hierarchy = new Mat();
        Mat kernel = null;

        try {
            // Use a better simulation approach that mimics the Python code
            // Convert to grayscale
            Imgproc.cvtColor(frame, grayImage, Imgproc.COLOR_BGR2GRAY);

            // Apply thresholding to create a binary mask
            Imgproc.threshold(grayImage, binaryMask, 100, 255, Imgproc.THRESH_BINARY);

            // Apply morphological operations to create blob-like shapes
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(20, 20));
            Imgproc.morphologyEx(binaryMask, binaryMask, Imgproc.MORPH_CLOSE, kernel);

            // Find contours in the binary mask
            Imgproc.findContours(binaryMask, allContours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // Filter contours by area - select a few random ones to simulate potholes
            for (MatOfPoint contour : allContours) {
                double area = Imgproc.contourArea(contour);
                // Create a more realistic distribution of potholes
                if (area > 1000 && area < 20000) {
                    // Generate more small potholes than large ones (simulate real distribution)
                    double threshold = area < smallThreshold ? 0.3 : 0.7;
                    if (Math.random() > threshold) {
                        contours.add(contour);
                    } else {
                        contour.release();
                    }
                } else {
                    contour.release();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in simulateContours: " + e.getMessage(), e);
            // Release all contours in case of error
            for (MatOfPoint contour : allContours) {
                if (!contours.contains(contour)) {
                    contour.release();
                }
            }
        } finally {
            // Clean up resources
            if (grayImage != null && !grayImage.empty()) grayImage.release();
            if (binaryMask != null && !binaryMask.empty()) binaryMask.release();
            if (hierarchy != null && !hierarchy.empty()) hierarchy.release();
            if (kernel != null && !kernel.empty()) kernel.release();
        }

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

//hello