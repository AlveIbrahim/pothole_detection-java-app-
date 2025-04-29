package com.example.potholedetector;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.potholedetector.utils.PotholeDetector;
import com.example.potholedetector.utils.ReportGenerator;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VideoProcessorActivity extends AppCompatActivity {

    private ProgressBar processingProgressBar;
    private TextView processingStatusTextView;
    private TextView detectedPotholesTextView;
    private TextView framesProcessedTextView;
    private TextView processingTimeTextView;
    private ImageView currentFrameImageView;
    private Button cancelButton;

    private Uri videoUri;
    private VideoProcessingTask processingTask;
    private PotholeDetector potholeDetector;
    private boolean isCancelled = false;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_processor);

        // Initialize UI components
        processingProgressBar = findViewById(R.id.processingProgressBar);
        processingStatusTextView = findViewById(R.id.processingStatusTextView);
        detectedPotholesTextView = findViewById(R.id.detectedPotholesTextView);
        framesProcessedTextView = findViewById(R.id.framesProcessedTextView);
        processingTimeTextView = findViewById(R.id.processingTimeTextView);
        currentFrameImageView = findViewById(R.id.currentFrameImageView);
        cancelButton = findViewById(R.id.cancelButton);

        // Get video URI from intent
        String videoUriString = getIntent().getStringExtra("VIDEO_URI");
        if (videoUriString != null) {
            videoUri = Uri.parse(videoUriString);
        } else {
            Toast.makeText(this, "Error: No video selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize the pothole detector
        initializePotholeDetector();

        // Set cancel button listener
        cancelButton.setOnClickListener(v -> {
            isCancelled = true;
            if (processingTask != null) {
                processingTask.cancel(true);
            }
            finish();
        });

        // Start processing video
        processingTask = new VideoProcessingTask();
        processingTask.execute(videoUri);
    }

    private void initializePotholeDetector() {
        try {
            // Copy model from assets to storage
            File modelFile = new File(getFilesDir(), "best_02.torchscript");
            if (!modelFile.exists()) {
                try (InputStream is = getAssets().open("best_02.torchscript");
                     FileOutputStream os = new FileOutputStream(modelFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }

            // Create pothole detector
            potholeDetector = new PotholeDetector(modelFile.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing model: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private class VideoProcessingTask extends AsyncTask<Uri, ProcessingUpdate, ProcessingResult> {

        @Override
        protected void onPreExecute() {
            processingStatusTextView.setText("Preparing video for processing...");
        }

        @Override
        protected ProcessingResult doInBackground(Uri... uris) {
            ProcessingResult result = new ProcessingResult();
            result.success = false;

            try {
                // Initialize video retriever
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(getApplicationContext(), uris[0]);

                // Get video duration
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long duration = Long.parseLong(durationStr);
                int totalFrames = (int) (duration / 1000 * 30); // Estimate 30fps

                // Initialize progress tracking
                processingProgressBar.setMax(totalFrames);
                long startTime = System.currentTimeMillis();

                // Process every 3rd frame (like in the Python code)
                int frameCount = 0;
                int processedCount = 0;

                // Storage for analytics
                Map<String, Integer> potholeCounts = new HashMap<>();
                potholeCounts.put("Small", 0);
                potholeCounts.put("Medium", 0);
                potholeCounts.put("Large", 0);
                List<Double> detectedAreas = new ArrayList<>();
                Map<String, Integer> riskLevels = new HashMap<>();
                riskLevels.put("Low", 0);
                riskLevels.put("Medium", 0);
                riskLevels.put("High", 0);
                List<PotholeDetector.PotholeInfo> allPotholes = new ArrayList<>();

                // Create heatmap matrix
                Mat heatmapHistory = new Mat();

                // Process frames
                for (int i = 0; i < totalFrames; i += 3) {
                    if (isCancelled) {
                        break;
                    }

                    long frameTime = i * 1000000 / 30; // Convert to microseconds
                    Bitmap bitmap = retriever.getFrameAtTime(frameTime, MediaMetadataRetriever.OPTION_CLOSEST);

                    if (bitmap != null) {
                        // Convert to Mat for OpenCV processing
                        Mat frame = new Mat();
                        Utils.bitmapToMat(bitmap, frame);

                        // Resize frame to match Python code dimensions
                        Imgproc.resize(frame, frame, new org.opencv.core.Size(1020, 500));

                        // Process frame with pothole detector
                        PotholeDetector.DetectionResult detectionResult = potholeDetector.processFrame(frame);

                        // Update analytics
                        potholeCounts.put("Small", potholeCounts.get("Small") + detectionResult.smallCount);
                        potholeCounts.put("Medium", potholeCounts.get("Medium") + detectionResult.mediumCount);
                        potholeCounts.put("Large", potholeCounts.get("Large") + detectionResult.largeCount);

                        detectedAreas.addAll(detectionResult.areas);

                        riskLevels.put("Low", riskLevels.get("Low") + detectionResult.lowRiskCount);
                        riskLevels.put("Medium", riskLevels.get("Medium") + detectionResult.mediumRiskCount);
                        riskLevels.put("High", riskLevels.get("High") + detectionResult.highRiskCount);

                        allPotholes.addAll(detectionResult.detectedPotholes);

                        // Update heatmap
                        if (heatmapHistory.empty()) {
                            heatmapHistory = detectionResult.heatmapUpdate.clone();
                        } else {
                            // Add new detections to heatmap
                            if (!detectionResult.heatmapUpdate.empty()) {
                                org.opencv.core.Core.add(heatmapHistory, detectionResult.heatmapUpdate, heatmapHistory);
                            }
                        }

                        // Convert processed frame to bitmap for display
                        Bitmap processedBitmap = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(detectionResult.processedFrame, processedBitmap);

                        // Update UI
                        processedCount++;
                        PublishProgressUpdate(
                                processedCount,
                                frameCount,
                                processedBitmap,
                                potholeCounts.get("Small") + potholeCounts.get("Medium") + potholeCounts.get("Large"),
                                System.currentTimeMillis() - startTime
                        );

                        bitmap.recycle();
                    }

                    frameCount++;
                    publishProgress(new ProcessingUpdate(frameCount, processedCount, null,
                            potholeCounts.get("Small") + potholeCounts.get("Medium") + potholeCounts.get("Large"),
                            System.currentTimeMillis() - startTime));
                }

                // Clean up
                retriever.release();

                // Generate report
                File reportFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                        "pothole_report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt");

                ReportGenerator reportGenerator = new ReportGenerator();
                boolean reportSuccess = reportGenerator.generateReport(
                        reportFile,
                        videoUri.getLastPathSegment(),
                        duration / 1000.0,
                        processedCount,
                        potholeCounts,
                        riskLevels,
                        detectedAreas,
                        allPotholes
                );

                result.success = true;
                result.reportPath = reportFile.getAbsolutePath();
                result.processingTimeMs = System.currentTimeMillis() - startTime;
                result.framesProcessed = processedCount;
                result.totalPotholes = potholeCounts.get("Small") + potholeCounts.get("Medium") + potholeCounts.get("Large");

            } catch (Exception e) {
                e.printStackTrace();
                result.errorMessage = e.getMessage();
            }

            return result;
        }

        private void PublishProgressUpdate(int processedCount, int frameCount, Bitmap currentFrame,
                                           int potholeCount, long elapsedTime) {
            publishProgress(new ProcessingUpdate(frameCount, processedCount, currentFrame, potholeCount, elapsedTime));
        }

        @Override
        protected void onProgressUpdate(ProcessingUpdate... values) {
            if (values.length > 0) {
                ProcessingUpdate update = values[0];

                // Update progress bar
                processingProgressBar.setProgress(update.frameCount);

                // Update status text
                processingStatusTextView.setText("Processing frame " + update.frameCount);

                // Update statistics
                detectedPotholesTextView.setText("Detected potholes: " + update.potholeCount);
                framesProcessedTextView.setText("Frames processed: " + update.processedCount);
                processingTimeTextView.setText("Processing time: " + (update.elapsedTimeMs / 1000) + " seconds");

                // Update frame preview
                if (update.currentFrame != null) {
                    currentFrameImageView.setImageBitmap(update.currentFrame);
                }
            }
        }

        @Override
        protected void onPostExecute(ProcessingResult result) {
            if (result.success) {
                Toast.makeText(VideoProcessorActivity.this, "Processing completed successfully", Toast.LENGTH_SHORT).show();

                // Launch result activity
                Intent intent = new Intent(VideoProcessorActivity.this, ResultActivity.class);
                intent.putExtra("REPORT_PATH", result.reportPath);
                intent.putExtra("PROCESSING_TIME", result.processingTimeMs);
                intent.putExtra("FRAMES_PROCESSED", result.framesProcessed);
                intent.putExtra("TOTAL_POTHOLES", result.totalPotholes);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(VideoProcessorActivity.this,
                        "Error processing video: " + result.errorMessage,
                        Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onCancelled() {
            Toast.makeText(VideoProcessorActivity.this, "Processing cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    private static class ProcessingUpdate {
        int frameCount;
        int processedCount;
        Bitmap currentFrame;
        int potholeCount;
        long elapsedTimeMs;

        ProcessingUpdate(int frameCount, int processedCount, Bitmap currentFrame, int potholeCount, long elapsedTimeMs) {
            this.frameCount = frameCount;
            this.processedCount = processedCount;
            this.currentFrame = currentFrame;
            this.potholeCount = potholeCount;
            this.elapsedTimeMs = elapsedTimeMs;
        }
    }

    private static class ProcessingResult {
        boolean success;
        String reportPath;
        String errorMessage;
        long processingTimeMs;
        int framesProcessed;
        int totalPotholes;
    }
}