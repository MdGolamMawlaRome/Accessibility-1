package com.example.smartaccessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ImageView;
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

    private TextView countdownTextView;
    private WindowManager.LayoutParams countdownParams;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        registerSystemObservers();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON;
                setServiceInfo(info);
            }

            AccessibilityButtonController buttonController = getAccessibilityButtonController();
            buttonController.registerAccessibilityButtonCallback(
                new AccessibilityButtonController.AccessibilityButtonCallback() {
                    @Override
                    public void onClicked(AccessibilityButtonController controller) {
                        toggleMenu();
                    }
                }
            );
        }
    }

    private void toggleMenu() {
        if (popupView != null) {
            hideMenu();
        } else {
            showMenu();
        }
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

    private void showMenu() {
        if (popupView != null) return;

        popupView = LayoutInflater.from(this).inflate(R.layout.accessibility_popup, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT, // WRAP_CONTENT ব্যবহার করা হয়েছে যাতে এটি ফিক্সড উচ্চতা পায়
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM;

        LinearLayout popupRoot = popupView.findViewById(R.id.popupRoot);
        applyThemeStyles(popupRoot);
        setupSliders();
        setupButtons();

        // নেভিগেশন বার ডিটেকশন ও পজিশনিং লজিক
        popupView.setOnApplyWindowInsetsListener((v, insets) -> {
            int navBarHeight = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                navBarHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                navBarHeight = insets.getSystemWindowInsetBottom();
            }
            
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) v.getLayoutParams();
            p.y = navBarHeight + 30; // নেভিগেশন বারের উচ্চতার সাথে অতিরিক্ত ৩০ পিক্সেল গ্যাপ
            windowManager.updateViewLayout(v, p);
            return insets;
        });

        windowManager.addView(popupView, params);
    }

    private void applyThemeStyles(LinearLayout popupRoot) {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

        int panelBgColor = isDarkTheme ? Color.parseColor("#E6121212") : Color.parseColor("#E6F5F5F7");
        int primaryTextColor = isDarkTheme ? Color.parseColor("#FFFFFF") : Color.parseColor("#1C1C1E");
        int actionTextColor = isDarkTheme ? Color.parseColor("#E0E0E0") : Color.parseColor("#555555");
        int iconTint = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");
        int trackSliderBackground = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#26000000");

        GradientDrawable backgroundShape = (GradientDrawable) popupRoot.getBackground();
        if (backgroundShape != null) {
            backgroundShape.setColor(panelBgColor);
        }

        ImageView imgBrightness = popupView.findViewById(R.id.imgBrightness);
        ImageView imgVolume = popupView.findViewById(R.id.imgVolume);
        ImageView imgRecord = popupView.findViewById(R.id.imgRecord);
        ImageView imgScreenshot = popupView.findViewById(R.id.imgScreenshot);
        ImageView imgLock = popupView.findViewById(R.id.imgLock);

        if (imgBrightness != null) imgBrightness.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        if (imgVolume != null) imgVolume.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        if (imgRecord != null) imgRecord.setColorFilter(isRecording ? Color.parseColor("#FF4444") : iconTint, PorterDuff.Mode.SRC_IN);
        if (imgScreenshot != null) imgScreenshot.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        if (imgLock != null) imgLock.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);

        TextView brightnessPercentText = popupView.findViewById(R.id.brightnessPercentText);
        TextView volumePercentText = popupView.findViewById(R.id.volumePercentText);
        TextView btnRecordText = popupView.findViewById(R.id.btnRecordText);
        TextView btnScreenshotText = popupView.findViewById(R.id.btnScreenshotText);
        TextView btnLockText = popupView.findViewById(R.id.btnLockText);

        if (brightnessPercentText != null) brightnessPercentText.setTextColor(primaryTextColor);
        if (volumePercentText != null) volumePercentText.setTextColor(primaryTextColor);
        if (btnRecordText != null) btnRecordText.setTextColor(actionTextColor);
        if (btnScreenshotText != null) btnScreenshotText.setTextColor(actionTextColor);
        if (btnLockText != null) btnLockText.setTextColor(actionTextColor);

        SeekBar brightnessSlider = popupView.findViewById(R.id.brightnessSlider);
        SeekBar volumeSlider = popupView.findViewById(R.id.volumeSlider);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList blueStateList = ColorStateList.valueOf(Color.parseColor("#FF0076FF"));
            ColorStateList backgroundStateList = ColorStateList.valueOf(trackSliderBackground);

            if (brightnessSlider != null) {
                brightnessSlider.setProgressTintList(blueStateList);
                brightnessSlider.setProgressBackgroundTintList(backgroundStateList);
            }
            if (volumeSlider != null) {
                volumeSlider.setProgressTintList(blueStateList);
                volumeSlider.setProgressBackgroundTintList(backgroundStateList);
            }
        }
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
            hideMenu();
            showMenu();
        }
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

        popupView.findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            hideMenu();
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        });

        popupView.findViewById(R.id.btnLock).setOnClickListener(v -> {
            hideMenu();
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        });
    }

    private void startScreenRecording(int resultCode, Intent data) {
        if (isRecording) return;
        String channelId = "screen_record_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Screen Record", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = new Notification.Builder(this, channelId)
                .setContentTitle("Screen Recording Active")
                .setContentText("Recording...")
                .setSmallIcon(android.R.drawable.ic_media_play);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(102, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(102, builder.build());
        }
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) { stopForeground(true); return; }
        showVisualCountdownOverlay(resultCode, data);
    }

    private void showVisualCountdownOverlay(int resultCode, Intent data) {
        countdownTextView = new TextView(this);
        countdownTextView.setTextSize(90);
        countdownTextView.setTextColor(Color.WHITE);
        countdownTextView.setGravity(Gravity.CENTER);
        countdownTextView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.BOLD));
        countdownParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        windowManager.addView(countdownTextView, countdownParams);
        Handler handler = new Handler(Looper.getMainLooper());
        countdownTextView.setText("3");
        handler.postDelayed(() -> { if (countdownTextView != null) countdownTextView.setText("2"); }, 1000);
        handler.postDelayed(() -> { if (countdownTextView != null) countdownTextView.setText("1"); }, 2000);
        handler.postDelayed(() -> { removeCountdownOverlay(); initializeAndStartRecorder(); }, 3000);
    }

    private void removeCountdownOverlay() {
        if (countdownTextView != null && windowManager != null) {
            try { windowManager.removeView(countdownTextView); } catch (Exception e) {}
            countdownTextView = null;
        }
    }

    private void initializeAndStartRecorder() {
        if (mediaProjection == null) return;
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, "Rec_" + System.currentTimeMillis() + ".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SmartAccessibility");
            Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "rw");
            mediaRecorder.setOutputFile(pfd.getFileDescriptor());
            mediaRecorder.setVideoSize(width, height);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoEncodingBitRate(8 * 1024 * 1024);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.prepare();
            virtualDisplay = mediaProjection.createVirtualDisplay("Capture", width, height, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
            mediaRecorder.start();
            isRecording = true;
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void stopScreenRecording() {
        if (!isRecording) return;
        try {
            if (mediaRecorder != null) { mediaRecorder.stop(); mediaRecorder.release(); }
            if (virtualDisplay != null) virtualDisplay.release();
            if (mediaProjection != null) mediaProjection.stop();
        } catch (Exception e) { e.printStackTrace(); }
        isRecording = false;
        stopForeground(true);
        Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show();
    }

    private int getCurrentSystemBrightness() {
        try { return (Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) * 100) / 255; } 
        catch (Exception e) { return 50; }
    }

    private void setSystemBrightness(int percent) {
        if (Settings.System.canWrite(this)) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, (percent * 255) / 100);
        }
    }

    private void registerSystemObservers() {
        brightnessObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                if (popupView != null) {
                    SeekBar slider = popupView.findViewById(R.id.brightnessSlider);
                    if (slider != null) slider.setProgress(getCurrentSystemBrightness());
                }
            }
        };
        getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver);
        
        systemReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction()) || Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    hideMenu();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(systemReceiver, filter);
    }

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { 
        super.onDestroy(); 
        hideMenu(); 
        if (brightnessObserver != null) getContentResolver().unregisterContentObserver(brightnessObserver);
        if (systemReceiver != null) unregisterReceiver(systemReceiver);
    }
}
