package com.gmr.smartaccessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
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
        if (popupView != null) hideMenu();
        else showMenu();
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

        popupView.findViewById(R.id.fullscreenRoot).setOnClickListener(v -> hideMenu());
        LinearLayout popupRoot = popupView.findViewById(R.id.popupRoot);
        popupRoot.setOnClickListener(v -> {});

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int bottomGap = metrics.heightPixels / 11;
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) popupRoot.getLayoutParams();
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.setMargins(dpToPx(16), 0, dpToPx(16), bottomGap);
        popupRoot.setLayoutParams(layoutParams);

        applyThemeStyles(popupRoot);
        setupButtonsAndViews();
        setupBrightnessSlider();
        updateAllVolumeSliders(); // প্রথমবার ওপেন হতেই সব ভলিউম সিঙ্ক হবে

        windowManager.addView(popupView, params);
    }

    public void hideMenu() {
        if (popupView != null && windowManager != null) {
            try { windowManager.removeView(popupView); } catch (Exception e) {}
            popupView = null;
        }
    }

    private void setupButtonsAndViews() {
        LinearLayout mainLayout = popupView.findViewById(R.id.mainControlsLayout);
        LinearLayout expandedLayout = popupView.findViewById(R.id.expandedVolumeLayout);

        popupView.findViewById(R.id.btnExpand).setOnClickListener(v -> {
            mainLayout.setVisibility(View.GONE);
            expandedLayout.setVisibility(View.VISIBLE);
        });

        popupView.findViewById(R.id.btnVolumeBack).setOnClickListener(v -> {
            expandedLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
        });

        popupView.findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            hideMenu(); service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
        });
        popupView.findViewById(R.id.btnPower).setOnClickListener(v -> {
            hideMenu(); service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
        });
        popupView.findViewById(R.id.btnLock).setOnClickListener(v -> {
            hideMenu();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        });
    }

    // এই ফাংশনটি ব্রডকাস্ট রিসিভার থেকে কল হবে, যেন রিয়েল-টাইম আপডেট হয়
    public void updateAllVolumeSliders() {
        if (popupView == null) return;
        
        // মেইন প্যানেল আপডেট
        int activeStream = audioController.getActiveStream();
        setupSingleSlider(popupView.findViewById(R.id.mainVolumeSlider), popupView.findViewById(R.id.mainVolumePercentText), activeStream);
        
        // এক্সপ্যান্ডেড প্যানেল আপডেট
        bindRow(R.id.rowMedia, R.drawable.ic_volume_media, AudioManager.STREAM_MUSIC);
        bindRow(R.id.rowRing, R.drawable.ic_volume_ring, AudioManager.STREAM_RING);
        bindRow(R.id.rowSystem, R.drawable.ic_volume_system, AudioManager.STREAM_SYSTEM);
        bindRow(R.id.rowCall, R.drawable.ic_volume_call, AudioManager.STREAM_VOICE_CALL);
        bindRow(R.id.rowAlarm, R.drawable.ic_volume_alarm, AudioManager.STREAM_ALARM);

        // স্মার্ট লজিক: নোটিফিকেশন মার্জ করা থাকলে লেআউট থেকে গায়েব করে দেওয়া হবে
        View rowNotif = popupView.findViewById(R.id.rowNotification);
        if (audioController.isNotificationMergedWithRing()) {
            rowNotif.setVisibility(View.GONE);
        } else {
            rowNotif.setVisibility(View.VISIBLE);
            bindRow(R.id.rowNotification, R.drawable.ic_volume_notification, AudioManager.STREAM_NOTIFICATION);
        }
    }

    private void bindRow(int rowId, int iconResId, int streamType) {
        View row = popupView.findViewById(rowId);
        if (row == null) return;
        
        ImageView icon = row.findViewById(R.id.rowIcon);
        SeekBar slider = row.findViewById(R.id.rowSlider);
        TextView text = row.findViewById(R.id.rowPercentText);

        icon.setImageResource(iconResId);
        int iconTint = (service.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.parseColor("#1C1C1E");
        icon.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);

        setupSingleSlider(slider, text, streamType);
    }

    private void setupSingleSlider(SeekBar slider, TextView textView, int streamType) {
        int max = audioController.getMaxVolume(streamType);
        int cur = audioController.getCurrentVolume(streamType);
        slider.setMax(100);
        int progress = max > 0 ? (cur * 100) / max : 0;
        slider.setProgress(progress);
        textView.setText(progress + "%");

        slider.setOnSeekBarChangeListener(null);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int prog, boolean fromUser) {
                if (fromUser) {
                    textView.setText(prog + "%");
                    int vol = max > 0 ? Math.round((prog / 100f) * max) : 0;
                    audioController.setVolume(streamType, vol);
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
                if (fromUser) {
                    brightnessText.setText(progress + "%");
                    int systemVal = (progress * 255) / 100;
                    if (Settings.System.canWrite(service)) {
                        Settings.System.putInt(service.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, systemVal);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private int getCurrentSystemBrightness() {
        try { return Settings.System.getInt(service.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS); } 
        catch (Exception e) { return 128; }
    }

    private int dpToPx(int dp) {
        return (int) (dp * service.getResources().getDisplayMetrics().density);
    }

    private void applyThemeStyles(LinearLayout popupRoot) {
        boolean isDarkTheme = (service.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int panelBgColor = isDarkTheme ? Color.parseColor("#E6121212") : Color.parseColor("#E6F5F5F7");
        int primaryTextColor = isDarkTheme ? Color.parseColor("#FFFFFF") : Color.parseColor("#1C1C1E");
        int iconTint = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");

        GradientDrawable backgroundShape = (GradientDrawable) popupRoot.getBackground();
        if (backgroundShape != null) backgroundShape.setColor(panelBgColor);

        int[] iconIds = {R.id.imgBrightness, R.id.imgMainVolume, R.id.btnExpand, R.id.btnVolumeBack, R.id.imgScreenshot, R.id.imgPower, R.id.imgLock};
        for (int id : iconIds) {
            ImageView img = popupView.findViewById(id);
            if (img != null) img.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        }

        int[] textIds = {R.id.brightnessPercentText, R.id.mainVolumePercentText};
        for (int id : textIds) {
            TextView txt = popupView.findViewById(id);
            if (txt != null) txt.setTextColor(primaryTextColor);
        }
    }
}
