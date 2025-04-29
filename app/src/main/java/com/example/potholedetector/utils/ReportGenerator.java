package com.example.potholedetector.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opencv.core.Point;

public class ReportGenerator {

    public boolean generateReport(
            File reportFile,
            String videoName,
            double videoDuration,
            int framesProcessed,
            Map<String, Integer> potholeCounts,
            Map<String, Integer> riskLevels,
            List<Double> detectedAreas,
            List<PotholeDetector.PotholeInfo> allPotholes) {

        try (FileWriter writer = new FileWriter(reportFile)) {
            // Header
            writer.write("=".repeat(80) + "\n");
            writer.write("POTHOLE DETECTION ANALYSIS REPORT\n");
            writer.write("Generated on: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "\n");
            writer.write("=".repeat(80) + "\n\n");

            // Video information
            writer.write("VIDEO INFORMATION\n");
            writer.write("-".repeat(80) + "\n");
            writer.write("Filename: " + videoName + "\n");
            writer.write("Duration: " + String.format("%.2f", videoDuration) + " seconds\n");
            writer.write("Frames analyzed: " + framesProcessed + "\n");
            writer.write("Model used: YOLOv8-seg (best_02.pt)\n\n");

            // Summary statistics
            writer.write("SUMMARY STATISTICS\n");
            writer.write("-".repeat(80) + "\n");
            writer.write("Total unique potholes detected: " + allPotholes.size() + "\n");
            writer.write("Pothole size distribution:\n");
            writer.write("  - Small: " + potholeCounts.get("Small") + "\n");
            writer.write("  - Medium: " + potholeCounts.get("Medium") + "\n");
            writer.write("  - Large: " + potholeCounts.get("Large") + "\n\n");
            writer.write("Risk level distribution:\n");
            writer.write("  - Low risk: " + riskLevels.get("Low") + "\n");
            writer.write("  - Medium risk: " + riskLevels.get("Medium") + "\n");
            writer.write("  - High risk: " + riskLevels.get("High") + "\n\n");

            // Calculate average area
            double avgArea = 0;
            if (!detectedAreas.isEmpty()) {
                double sum = 0;
                for (double area : detectedAreas) {
                    sum += area;
                }
                avgArea = sum / detectedAreas.size();
            }
            writer.write("Average pothole area: " + String.format("%.2f", avgArea) + " square pixels\n");

            // Calculate severity rating (0-10)
            double severityRating = Math.min(10,
                    (riskLevels.get("Medium") * 0.5 + riskLevels.get("High") * 1.0) / Math.max(1, framesProcessed) * 10);
            writer.write("Overall road condition severity rating (0-10): " + String.format("%.1f", severityRating) + "\n\n");

            // Hotspot analysis
            writer.write("HOTSPOT ANALYSIS\n");
            writer.write("-".repeat(80) + "\n");

            // Find hotspots (areas with multiple potholes in close proximity)
            List<Map<String, Object>> hotspots = findHotspots(allPotholes);

            if (!hotspots.isEmpty()) {
                writer.write("Identified " + hotspots.size() + " hotspot areas with multiple potholes:\n");
                // Sort by count (highest first)
                hotspots.sort((h1, h2) -> ((Integer)h2.get("count")).compareTo((Integer)h1.get("count")));

                // List top 5 hotspots
                for (int i = 0; i < Math.min(5, hotspots.size()); i++) {
                    Map<String, Object> hotspot = hotspots.get(i);
                    Point center = (Point) hotspot.get("center");
                    int count = (Integer) hotspot.get("count");
                    writer.write(String.format("  %d. Location: x=%.0f, y=%.0f - %d potholes in proximity\n",
                            i+1, center.x, center.y, count));
                }
            } else {
                writer.write("No significant hotspots identified.\n");
            }
            writer.write("\n");

            // Detailed pothole information
            writer.write("DETAILED POTHOLE INFORMATION\n");
            writer.write("-".repeat(80) + "\n");

            // Sort potholes by risk level (High > Medium > Low)
            allPotholes.sort((p1, p2) -> {
                int risk1 = riskToValue(p1.risk);
                int risk2 = riskToValue(p2.risk);
                return Integer.compare(risk2, risk1);
            });

            for (int i = 0; i < allPotholes.size(); i++) {
                PotholeDetector.PotholeInfo pothole = allPotholes.get(i);
                writer.write("Pothole #" + (i+1) + ":\n");
                writer.write("  - Size category: " + pothole.size + "\n");
                writer.write("  - Area: " + String.format("%.2f", pothole.area) + " square pixels\n");
                writer.write("  - Risk level: " + pothole.risk + "\n");
                writer.write("  - Position: x=" + String.format("%.0f", pothole.centroid.x) +
                        ", y=" + String.format("%.0f", pothole.centroid.y) + "\n");
                writer.write("\n");
            }

            // Recommendations
            writer.write("RECOMMENDATIONS\n");
            writer.write("-".repeat(80) + "\n");
            if (severityRating >= 7) {
                writer.write("URGENT ATTENTION REQUIRED: The analyzed road section shows significant pothole damage that requires immediate repair.\n");
                writer.write("- Prioritize the identified hotspot areas for immediate patching.\n");
                writer.write("- Consider complete resurfacing for long-term solution.\n");
                writer.write("- Place warning signs for drivers about dangerous road conditions.\n");
            } else if (severityRating >= 4) {
                writer.write("MODERATE ATTENTION NEEDED: The analyzed road section shows moderate pothole damage that should be addressed soon.\n");
                writer.write("- Schedule repairs for high-risk potholes within the next maintenance cycle.\n");
                writer.write("- Monitor the identified hotspots for further deterioration.\n");
            } else {
                writer.write("MINOR ATTENTION SUGGESTED: The analyzed road section shows minimal pothole damage.\n");
                writer.write("- Address the few identified potholes during regular maintenance cycles.\n");
                writer.write("- Re-analyze the road after adverse weather conditions to monitor degradation.\n");
            }

            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Helper method to convert risk level to numeric value for sorting
    private int riskToValue(String risk) {
        switch (risk) {
            case "High": return 2;
            case "Medium": return 1;
            case "Low": return 0;
            default: return -1;
        }
    }

    // Method to find hotspots (areas with multiple potholes in close proximity)
    private List<Map<String, Object>> findHotspots(List<PotholeDetector.PotholeInfo> potholes) {
        List<Map<String, Object>> hotspots = new ArrayList<>();

        // If too few potholes, no hotspots
        if (potholes.size() < 3) {
            return hotspots;
        }

        // For each pothole, count how many other potholes are nearby
        for (PotholeDetector.PotholeInfo pothole : potholes) {
            int nearbyCount = 0;

            // Check distance to all other potholes
            for (PotholeDetector.PotholeInfo other : potholes) {
                if (pothole == other) continue;

                // Calculate distance
                double distance = Math.sqrt(
                        Math.pow(pothole.centroid.x - other.centroid.x, 2) +
                                Math.pow(pothole.centroid.y - other.centroid.y, 2));

                // If within 50 pixels, count as nearby
                if (distance < 50) {
                    nearbyCount++;
                }
            }

            // If at least 3 potholes are in proximity, consider it a hotspot
            if (nearbyCount >= 2) {
                // Check if this pothole's location is already in a hotspot
                boolean alreadyExists = false;
                for (Map<String, Object> existingHotspot : hotspots) {
                    Point center = (Point) existingHotspot.get("center");
                    double distance = Math.sqrt(
                            Math.pow(pothole.centroid.x - center.x, 2) +
                                    Math.pow(pothole.centroid.y - center.y, 2));

                    if (distance < 50) {
                        alreadyExists = true;
                        break;
                    }
                }

                // If not already a hotspot, add it
                if (!alreadyExists) {
                    Map<String, Object> hotspot = new HashMap<>();
                    hotspot.put("center", pothole.centroid);
                    hotspot.put("count", nearbyCount + 1); // +1 to include this pothole
                    hotspots.add(hotspot);
                }
            }
        }

        return hotspots;
    }
}