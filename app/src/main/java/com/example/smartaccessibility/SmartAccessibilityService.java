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
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        View fullscreenRoot = popupView.findViewById(R.id.fullscreenRoot);
        fullscreenRoot.setOnClickListener(v -> hideMenu());

        LinearLayout popupRoot = popupView.findViewById(R.id.popupRoot);
        popupRoot.setOnClickListener(v -> { });

        // Apply Dynamic Theme Lookups Programmatically
        applyThemeStyles(popupRoot);

        setupSliders();
        setupButtons();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) popupRoot.getLayoutParams();
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            popupView.setOnApplyWindowInsetsListener((v, insets) -> {
                int navBarHeight = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    navBarHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else {
                    navBarHeight = insets.getSystemWindowInsetBottom();
                }
                layoutParams.setMargins(dpToPx(16), 0, dpToPx(16), navBarHeight + 5);
                popupRoot.setLayoutParams(layoutParams);
                return insets;
            });
        } else {
            int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            int navBarHeight = (resourceId > 0) ? getResources().getDimensionPixelSize(resourceId) : 0;
            layoutParams.setMargins(dpToPx(16), 0, dpToPx(16), navBarHeight + 5);
            popupRoot.setLayoutParams(layoutParams);
        }

        windowManager.addView(popupView, params);
    }

    private void applyThemeStyles(LinearLayout popupRoot) {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

        // Set palette colors based on theme configuration state
        int panelBgColor = isDarkTheme ? Color.parseColor("#E6121212") : Color.parseColor("#E6F5F5F7");
        int primaryTextColor = isDarkTheme ? Color.parseColor("#FFFFFF") : Color.parseColor("#1C1C1E");
        int actionTextColor = isDarkTheme ? Color.parseColor("#E0E0E0") : Color.parseColor("#555555");
        int iconTint = isDarkTheme ? Color.white(1.0f) : Color.parseColor("#1C1C1E");
        int trackSliderBackground = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#26000000");

        // Mutate dynamic shape corner background color values safely
        GradientDrawable backgroundShape = (GradientDrawable) popupRoot.getBackground();
        if (backgroundShape != null) {
            backgroundShape.setColor(panelBgColor);
        }

        // Apply Icon Color Elements Filter
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

        // Apply Text Colors Elements
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

        // Apply Track Sliders Colors Tint Elements
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
        // If theme alters while menu container state is live, refresh layouts instantly
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
                .setContentTitle("Screen Recording Setup")
                .setContentText("Preparing internal systems...")
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

        showVisualCountdownOverlay(resultCode, data);
    }

    private void showVisualCountdownOverlay(int resultCode, Intent data) {
        countdownTextView = new TextView(this);
        countdownTextView.setTextSize(90);
        countdownTextView.setTextColor(Color.WHITE);
        countdownTextView.setGravity(Gravity.CENTER);
        countdownTextView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.BOLD));
        countdownTextView.setShadowLayer(15, 0, 0, Color.BLACK);

        countdownParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        countdownParams.gravity = Gravity.CENTER;

        windowManager.addView(countdownTextView, countdownParams);

        Handler handler = new Handler(Looper.getMainLooper());
        
        countdownTextView.setText("3");

        handler.postDelayed(() -> {
            if (countdownTextView != null) countdownTextView.setText("2");
        }, 1000);

        handler.postDelayed(() -> {
            if (countdownTextView != null) countdownTextView.setText("1");
        }, 2000);

        handler.postDelayed(() -> {
            if (countdownTextView != null) {
                countdownTextView.setTextSize(70);
                countdownTextView.setText("now");
            }
        }, 3000);

        handler.postDelayed(() -> {
            removeCountdownOverlay();
            initializeAndStartRecorder();
        }, 3800);
    }

    private void removeCountdownOverlay() {
        if (countdownTextView != null && windowManager != null) {
            try {
                windowManager.removeView(countdownTextView);
            } catch (Exception e) {}
            countdownTextView = null;
        }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }

            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, "ScreenRec_" + System.currentTimeMillis() + ".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SmartAccessibility");
            Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

            if (uri == null) throw new Exception("Failed to create MediaStore URI");

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
            Toast.makeText(this, "রেকর্ডিং শুরু হয়েছে (60 FPS)", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "রেকর্ডিং ব্যর্থ হয়েছে: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        Toast.makeText(this, "রেকর্ডিং Movies ফোল্ডারে সেভ হয়েছে!", Toast.LENGTH_SHORT).show();
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(systemReceiver, filter);
        }
    }

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        removeCountdownOverlay();
        stopScreenRecording();
        hideMenu();
        if (brightnessObserver != null) getContentResolver().unregisterContentObserver(brightnessObserver);
        if (systemReceiver != null) unregisterReceiver(systemReceiver);
    }
}
