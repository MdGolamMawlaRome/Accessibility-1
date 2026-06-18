package com.example.smartaccessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.FrameLayout;

public class SmartAccessibilityService extends AccessibilityService {

    private View popupView;
    private WindowManager windowManager;
    private AudioManager audioManager;
    private WindowManager.LayoutParams params;
    private ContentObserver brightnessObserver;
    private BroadcastReceiver systemReceiver;

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

        applyThemeStyles(popupRoot);
        setupSliders();
        setupButtons();

        // 1/11 Dynamic height placement from the screen baseline
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int screenHeight = metrics.heightPixels;
        int bottomGap = screenHeight / 11;

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) popupRoot.getLayoutParams();
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.setMargins(dpToPx(16), 0, dpToPx(16), bottomGap);
        popupRoot.setLayoutParams(layoutParams);

        windowManager.addView(popupView, params);
    }

    private void applyThemeStyles(LinearLayout popupRoot) {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

        int panelBgColor = isDarkTheme ? Color.parseColor("#E6121212") : Color.parseColor("#E6F5F5F7");
        int primaryTextColor = isDarkTheme ? Color.parseColor("#FFFFFF") : Color.parseColor("#1C1C1E");
        int actionTextColor = isDarkTheme ? Color.parseColor("#E0E0E0") : Color.parseColor("#555555");
        int iconTint = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");

        GradientDrawable backgroundShape = (GradientDrawable) popupRoot.getBackground();
        if (backgroundShape != null) {
            backgroundShape.setColor(panelBgColor);
        }

        ImageView imgBrightness = popupView.findViewById(R.id.imgBrightness);
        ImageView imgVolume = popupView.findViewById(R.id.imgVolume);
        ImageView imgPower = popupView.findViewById(R.id.imgPower);
        ImageView imgScreenshot = popupView.findViewById(R.id.imgScreenshot);
        ImageView imgLock = popupView.findViewById(R.id.imgLock);

        if (imgBrightness != null) imgBrightness.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        if (imgVolume != null) imgVolume.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        if (imgPower != null) imgPower.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        if (imgScreenshot != null) imgScreenshot.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        if (imgLock != null) imgLock.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);

        TextView brightnessPercentText = popupView.findViewById(R.id.brightnessPercentText);
        TextView volumePercentText = popupView.findViewById(R.id.volumePercentText);
        TextView btnPowerText = popupView.findViewById(R.id.btnPowerText);
        TextView btnScreenshotText = popupView.findViewById(R.id.btnScreenshotText);
        TextView btnLockText = popupView.findViewById(R.id.btnLockText);

        if (brightnessPercentText != null) brightnessPercentText.setTextColor(primaryTextColor);
        if (volumePercentText != null) volumePercentText.setTextColor(primaryTextColor);
        if (btnPowerText != null) btnPowerText.setTextColor(actionTextColor);
        if (btnScreenshotText != null) btnScreenshotText.setTextColor(actionTextColor);
        if (btnLockText != null) btnLockText.setTextColor(actionTextColor);
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
        int brightnessProgress = (currentBrightness * 100) / 255;
        brightnessSlider.setMax(100);
        brightnessSlider.setProgress(brightnessProgress);
        brightnessText.setText(brightnessProgress + "%");

        brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessText.setText(progress + "%");
                if (fromUser) {
                    int systemVal = (progress * 255) / 100;
                    setSystemBrightness(systemVal);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        SeekBar volumeSlider = popupView.findViewById(R.id.volumeSlider);
        TextView volumeText = popupView.findViewById(R.id.volumePercentText);
        
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        
        volumeSlider.setMax(100);
        int volProgress = maxVol > 0 ? (curVol * 100) / maxVol : 0;
        volumeSlider.setProgress(volProgress);
        volumeText.setText(volProgress + "%");

        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeText.setText(progress + "%");
                if (fromUser) {
                    int systemVol = maxVol > 0 ? Math.round((float) progress * maxVol / 100f) : 0;
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVol, 0);
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
        int volProgress = maxVol > 0 ? (curVol * 100) / maxVol : 0;
        volumeSlider.setProgress(volProgress);
        volumeText.setText(volProgress + "%");
    }

    private void setupButtons() {
        popupView.findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            hideMenu();
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        });

        popupView.findViewById(R.id.btnPower).setOnClickListener(v -> {
            hideMenu();
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        });

        popupView.findViewById(R.id.btnLock).setOnClickListener(v -> {
            hideMenu();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            }
        });
    }

    private int getCurrentSystemBrightness() {
        try {
            return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) { return 128; }
    }

    private void setSystemBrightness(int value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
            }
        } else {
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
                        int progress = (curBrightness * 100) / 255;
                        brightnessSlider.setProgress(progress);
                        brightnessText.setText(progress + "%");
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
        hideMenu();
        if (brightnessObserver != null) getContentResolver().unregisterContentObserver(brightnessObserver);
        if (systemReceiver != null) unregisterReceiver(systemReceiver);
    }
}
