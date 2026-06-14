package com.example.smartaccessibility;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.Toast;

public class SmartAccessibilityService extends AccessibilityService {

    private View popupView;
    private WindowManager windowManager;
    private AudioManager audioManager;
    private WindowManager.LayoutParams params;
    private ContentObserver brightnessObserver;
    private BroadcastReceiver systemReceiver;

    private static boolean isRecording = false;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        registerSystemObservers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "ACTION_START_RECORDING".equals(intent.getAction())) {
            int resultCode = intent.getIntExtra("RESULT_CODE", 0);
            Intent data = intent.getParcelableExtra("DATA");
            if (data != null) {
                startScreenRecording(resultCode, data);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onAccessibilityButtonClicked() {
        super.onAccessibilityButtonClicked();
        if (popupView != null) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private void showMenu() {
        if (popupView != null) return;

        popupView = LayoutInflater.from(this).inflate(R.layout.accessibility_popup, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        View fullscreenRoot = popupView.findViewById(R.id.fullscreenRoot);
        fullscreenRoot.setOnClickListener(v -> hideMenu());

        View popupRoot = popupView.findViewById(R.id.popupRoot);
        popupRoot.setOnClickListener(v -> { });

        setupSliders();
        setupButtons();
        updateLayoutForOrientation();

        windowManager.addView(popupView, params);
    }

    private void hideMenu() {
        if (popupView != null && windowManager != null) {
            try {
                windowManager.removeView(popupView);
            } catch (Exception e) {}
            popupView = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (popupView != null) {
            updateLayoutForOrientation();
        }
    }

    private void updateLayoutForOrientation() {
        if (popupView == null || windowManager == null || params == null) return;

        int rotation = windowManager.getDefaultDisplay().getRotation();
        LinearLayout rootLayout = popupView.findViewById(R.id.popupRoot);
        LinearLayout buttonsLayout = popupView.findViewById(R.id.buttonsLayout);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) rootLayout.getLayoutParams();

        if (rotation == Surface.ROTATION_90) {
            layoutParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            rootLayout.setMinimumWidth(dpToPx(280));
            layoutParams.width = dpToPx(280);
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.bottomMargin = 0;
            layoutParams.setMargins(0, 16, 16, 16);
            if (buttonsLayout != null) buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        } else if (rotation == Surface.ROTATION_270) {
            layoutParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            rootLayout.setMinimumWidth(dpToPx(280));
            layoutParams.width = dpToPx(280);
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.bottomMargin = 0;
            layoutParams.setMargins(16, 16, 0, 16);
            if (buttonsLayout != null) buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        } else {
            layoutParams.gravity = Gravity.BOTTOM;
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.setMargins(16, 0, 16, 6);
            if (buttonsLayout != null) buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        }

        rootLayout.setLayoutParams(layoutParams);
        try {
            windowManager.updateViewLayout(popupView, params);
        } catch (Exception e) {}
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void setupSliders() {
        SeekBar brightnessSlider = popupView.findViewById(R.id.brightnessSlider);
        TextView brightnessText = popupView.findViewById(R.id.brightnessPercentText);
        
        int currentBrightness = getCurrentSystemBrightness();
        brightnessSlider.setProgress(currentBrightness);
        brightnessText.setText(currentBrightness + "%");

        brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessText.setText(progress + "%");
                if (fromUser) setSystemBrightness(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        SeekBar volumeSlider = popupView.findViewById(R.id.volumeSlider);
        TextView volumeText = popupView.findViewById(R.id.volumePercentText);
        updateVolumeSlider(volumeSlider, volumeText);

        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeText.setText(progress + "%");
                if (fromUser) {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int targetVol = (progress * maxVol) / 100;
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateVolumeSlider(SeekBar volumeSlider, TextView volumeText) {
        if (volumeSlider == null || volumeText == null) return;
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int volPercentage = maxVol > 0 ? (curVol * 100) / maxVol : 0;
        volumeSlider.setProgress(volPercentage);
        volumeText.setText(volPercentage + "%");
    }

    private void setupButtons() {
        TextView btnRecordText = popupView.findViewById(R.id.btnRecordText);
        
        if (isRecording) {
            btnRecordText.setText("Stop Rec");
        } else {
            btnRecordText.setText("Record");
        }

        // স্ক্রিন রেকর্ড (0ms ডিলে - সাথে সাথে মেনু হাইড হবে)
        popupView.findViewById(R.id.btnRecord).setOnClickListener(v -> {
            hideMenu();
            if (!isRecording) {
                Intent intent = new Intent(SmartAccessibilityService.this, ScreenRecordPermissionActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                stopScreenRecording();
            }
        });

        // স্ক্রিনশট (0ms ডিলে - সাথে সাথে মেনু হাইড হবে)
        popupView.findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            hideMenu();
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        });

        // লক স্ক্রিন (0ms ডিলে - সাথে সাথে মেনু হাইড হবে)
        popupView.findViewById(R.id.btnLock).setOnClickListener(v -> {
            hideMenu();
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        });
    }

    // অ্যান্ড্রয়েড ১৪ কমপ্লায়েন্ট মিডিয়াপ্রজেকশন + পারমিশনের পর ৩ সেকেন্ড ডিলে মেকানিজম
    private void startScreenRecording(int resultCode, Intent data) {
        if (isRecording) return;

        String channelId = "screen_record_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Screen Record", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = new Notification.Builder(this, channelId)
                .setContentTitle("Screen Recording")
                .setContentText("Recording will start in 3 seconds...")
                .setSmallIcon(android.R.drawable.ic_media_play);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(102, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(102, builder.build());
        }

        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);

        if (mediaProjection == null) {
            stopForeground(true);
            return;
        }

        Toast.makeText(this, "৩ সেকেন্ড পর রেকর্ডিং শুরু হচ্ছে...", Toast.LENGTH_SHORT).show();

        // পারমিশন পাওয়ার পর ৩ সেকেন্ড ওয়েট করবে
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            initializeAndStartRecorder();
        }, 3000);
    }

    private void initializeAndStartRecorder() {
        if (mediaProjection == null) return;

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        if (width % 2 != 0) width--;
        if (height % 2 != 0) height--;

        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, "ScreenRec_" + System.currentTimeMillis() + ".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SmartAccessibility");
            Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "rw");
            mediaRecorder.setOutputFile(pfd.getFileDescriptor());

            mediaRecorder.setVideoSize(width, height);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(12 * 1024 * 1024);
            mediaRecorder.setVideoFrameRate(60);

            mediaRecorder.prepare();

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "SmartScreenCapture", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(), null, null);

            mediaRecorder.start();
            isRecording = true;

            updateNotificationToRecording();
            Toast.makeText(this, "রেকর্ডিং শুরু হয়েছে (60 FPS, Max Resolution)", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "রেকর্ডিং শুরু করা যায়নি: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopScreenRecording();
        }
    }

    private void updateNotificationToRecording() {
        String channelId = "screen_record_channel";
        Notification.Builder builder = new Notification.Builder(this, channelId)
                .setContentTitle("Screen Recording Active")
                .setContentText("Recording in Ultra Quality (60 FPS)...")
                .setSmallIcon(android.R.drawable.ic_media_play);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(102, builder.build());
        }
    }

    private void stopScreenRecording() {
        if (!isRecording) return;
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        isRecording = false;
        stopForeground(true);
        Toast.makeText(this, "রেকর্ডিং গ্যালারির Movies ফোল্ডারে সেভ হয়েছে!", Toast.LENGTH_SHORT).show();
    }

    private int getCurrentSystemBrightness() {
        try {
            int value = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return (value * 100) / 255;
        } catch (Exception e) { return 50; }
    }

    private void setSystemBrightness(int percent) {
        if (Settings.System.canWrite(this)) {
            int value = (percent * 255) / 100;
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
        }
    }

    private void registerSystemObservers() {
        brightnessObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                if (popupView != null) {
                    SeekBar brightnessSlider = popupView.findViewById(R.id.brightnessSlider);
                    TextView brightnessText = popupView.findViewById(R.id.brightnessPercentText);
                    if (brightnessSlider != null && brightnessText != null) {
                        int curBrightness = getCurrentSystemBrightness();
                        brightnessSlider.setProgress(curBrightness);
                        brightnessText.setText(curBrightness + "%");
                    }
                }
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false, brightnessObserver);

        systemReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                    if (popupView != null) {
                        SeekBar volumeSlider = popupView.findViewById(R.id.volumeSlider);
                        TextView volumeText = popupView.findViewById(R.id.volumePercentText);
                        updateVolumeSlider(volumeSlider, volumeText);
                    }
                } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action) || Intent.ACTION_SCREEN_OFF.equals(action)) {
                    hideMenu();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(systemReceiver, filter);
    }

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScreenRecording();
        hideMenu();
        if (brightnessObserver != null) getContentResolver().unregisterContentObserver(brightnessObserver);
        if (systemReceiver != null) unregisterReceiver(systemReceiver);
    }
}
