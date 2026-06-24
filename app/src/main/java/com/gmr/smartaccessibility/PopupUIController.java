package com.gmr.smartaccessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class PopupUIController {

    private final AccessibilityService service;
    private final WindowManager windowManager;
    private final AudioController audioController;
    private View popupView;
    private WindowManager.LayoutParams params;

    public PopupUIController(AccessibilityService service, AudioController audioController) {
        this.service = service;
        this.audioController = audioController;
        this.windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    public void toggleMenu() {
        if (popupView != null) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    public boolean isShowing() {
        return popupView != null;
    }

    public void showMenu() {
        if (popupView != null) return;

        popupView = LayoutInflater.from(service).inflate(R.layout.accessibility_popup, null);

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
        setupBrightnessSlider();
        updateVolumeUI(); 
        setupButtons();

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

    public void hideMenu() {
        if (popupView != null && windowManager != null) {
            try {
                windowManager.removeView(popupView);
            } catch (Exception e) {}
            popupView = null;
        }
    }

    // এই মেথডটি সিস্টেম স্টেট ট্র্যাক করে সঠিক আইকন ও ভলিউম লেভেল বসায়
    public void updateVolumeUI() {
        if (popupView == null) return;

        SeekBar volumeSlider = popupView.findViewById(R.id.volumeSlider);
        TextView volumeText = popupView.findViewById(R.id.volumePercentText);
        ImageView imgVolume = popupView.findViewById(R.id.imgVolume);

        if (volumeSlider == null || volumeText == null || imgVolume == null) return;

        int activeStream = audioController.getActiveStream();
        imgVolume.setImageResource(audioController.getIconForStream(activeStream));

        int maxVol = audioController.getMaxVolume(activeStream);
        int curVol = audioController.getCurrentVolume(activeStream);
        
        volumeSlider.setMax(100);
        int volProgress = maxVol > 0 ? (curVol * 100) / maxVol : 0;
        volumeSlider.setProgress(volProgress);
        volumeText.setText(volProgress + "%");

        volumeSlider.setOnSeekBarChangeListener(null); 
        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeText.setText(progress + "%");
                if (fromUser) {
                    int systemVol = maxVol > 0 ? Math.round((float) progress * maxVol / 100f) : 0;
                    audioController.setVolume(activeStream, systemVol);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    public void updateBrightnessUI() {
        if (popupView == null) return;
        SeekBar brightnessSlider = popupView.findViewById(R.id.brightnessSlider);
        TextView brightnessText = popupView.findViewById(R.id.brightnessPercentText);
        if (brightnessSlider != null && brightnessText != null) {
            int curBrightness = getCurrentSystemBrightness();
            int progress = (curBrightness * 100) / 255;
            brightnessSlider.setProgress(progress);
            brightnessText.setText(progress + "%");
        }
    }

    private void setupBrightnessSlider() {
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
    }

    private void applyThemeStyles(LinearLayout popupRoot) {
        int nightModeFlags = service.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;

        int panelBgColor = isDarkTheme ? Color.parseColor("#E6121212") : Color.parseColor("#E6F5F5F7");
        int primaryTextColor = isDarkTheme ? Color.parseColor("#FFFFFF") : Color.parseColor("#1C1C1E");
        int actionTextColor = isDarkTheme ? Color.parseColor("#E0E0E0") : Color.parseColor("#555555");
        int iconTint = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");

        GradientDrawable backgroundShape = (GradientDrawable) popupRoot.getBackground();
        if (backgroundShape != null) {
            backgroundShape.setColor(panelBgColor);
        }

        ImageView[] icons = {
            popupView.findViewById(R.id.imgBrightness),
            popupView.findViewById(R.id.imgVolume),
            popupView.findViewById(R.id.imgPower),
            popupView.findViewById(R.id.imgScreenshot),
            popupView.findViewById(R.id.imgLock)
        };
        for (ImageView icon : icons) {
            if (icon != null) icon.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        }

        TextView[] primaryTexts = {
            popupView.findViewById(R.id.brightnessPercentText),
            popupView.findViewById(R.id.volumePercentText)
        };
        for (TextView text : primaryTexts) {
            if (text != null) text.setTextColor(primaryTextColor);
        }

        TextView[] actionTexts = {
            popupView.findViewById(R.id.btnPowerText),
            popupView.findViewById(R.id.btnScreenshotText),
            popupView.findViewById(R.id.btnLockText)
        };
        for (TextView text : actionTexts) {
            if (text != null) text.setTextColor(actionTextColor);
        }
    }

    private void setupButtons() {
        popupView.findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            hideMenu();
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
        });

        popupView.findViewById(R.id.btnPower).setOnClickListener(v -> {
            hideMenu();
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
        });

        popupView.findViewById(R.id.btnLock).setOnClickListener(v -> {
            hideMenu();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
            }
        });
    }

    private int getCurrentSystemBrightness() {
        try {
            return Settings.System.getInt(service.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) { return 128; }
    }

    private void setSystemBrightness(int value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.System.canWrite(service)) {
                Settings.System.putInt(service.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
            }
        } else {
            Settings.System.putInt(service.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * service.getResources().getDisplayMetrics().density);
    }
          }
  
