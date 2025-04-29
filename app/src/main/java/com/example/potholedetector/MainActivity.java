package com.example.potholedetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_VIDEO_CAPTURE = 1;
    private static final int REQUEST_VIDEO_PICK = 2;
    private static final int REQUEST_PERMISSIONS = 100;

    private Button selectVideoButton;
    private Button recordVideoButton;
    private Button processVideoButton;
    private TextView selectedVideoTextView;

    private Uri videoUri;
    private File videoFile;

    private String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV!");
            Toast.makeText(this, "Error: Unable to load OpenCV library", Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "OpenCV loaded successfully!");
        }

        // Initialize UI components
        selectVideoButton = findViewById(R.id.selectVideoButton);
        recordVideoButton = findViewById(R.id.recordVideoButton);
        processVideoButton = findViewById(R.id.processVideoButton);
        selectedVideoTextView = findViewById(R.id.selectedVideoTextView);

        // Check for permissions
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS);
        }

        // Set click listeners
        selectVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectVideoFromGallery();
            }
        });

        recordVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordVideo();
            }
        });

        processVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processVideo();
            }
        });
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void selectVideoFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_VIDEO_PICK);
    }

    private void recordVideo() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            try {
                videoFile = createVideoFile();
                if (videoFile != null) {
                    videoUri = FileProvider.getUriForFile(this,
                            getApplicationContext().getPackageName() + ".fileprovider",
                            videoFile);
                    takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
                    startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
                }
            } catch (IOException ex) {
                Toast.makeText(this, "Error creating video file", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
        }
    }

    private File createVideoFile() throws IOException {
        // Create a unique filename with timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String videoFileName = "VIDEO_" + timeStamp + "_";
        File storageDir = getExternalFilesDir("PotholeVideos");

        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        return File.createTempFile(
                videoFileName,  // prefix
                ".mp4",         // suffix
                storageDir      // directory
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_VIDEO_CAPTURE) {
                // Video captured successfully
                if (videoUri != null) {
                    updateSelectedVideoUI(videoUri, videoFile.getName());
                }
            } else if (requestCode == REQUEST_VIDEO_PICK) {
                // Video selected from gallery
                if (data != null && data.getData() != null) {
                    videoUri = data.getData();
                    String videoName = getVideoNameFromUri(videoUri);
                    updateSelectedVideoUI(videoUri, videoName);
                }
            }
        }
    }

    private String getVideoNameFromUri(Uri uri) {
        String result = "Selected Video";
        String[] projection = {MediaStore.Video.Media.DISPLAY_NAME};

        if (uri != null) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        return result;
    }

    private void updateSelectedVideoUI(Uri uri, String videoName) {
        videoUri = uri;
        selectedVideoTextView.setText("Selected: " + videoName);
        selectedVideoTextView.setVisibility(View.VISIBLE);
        processVideoButton.setEnabled(true);
    }

    private void processVideo() {
        if (videoUri != null) {
            Intent intent = new Intent(MainActivity.this, VideoProcessorActivity.class);
            intent.putExtra("VIDEO_URI", videoUri.toString());
            startActivity(intent);
        } else {
            Toast.makeText(this, "Please select a video first", Toast.LENGTH_SHORT).show();
        }
    }
}