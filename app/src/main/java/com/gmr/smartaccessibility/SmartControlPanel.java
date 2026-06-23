package com.gmr.smartaccessibility;

import android.content.Context;
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

public class SmartControlPanel {

    public interface PanelListener {
        void onScreenshotRequested();
        void onPowerMenuRequested();
        void onLockScreenRequested();
    }

    private final Context context;
    private final WindowManager windowManager;
    private final AudioManager audioManager;
    private final PanelListener listener;
    private View popupView;
    private WindowManager.LayoutParams params;

    public SmartControlPanel(Context context, WindowManager windowManager, PanelListener listener) {
        this.context = context;
        this.windowManager = windowManager;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.listener = listener;
    }

    public void show() {
        if (popupView != null) return;

        popupView = LayoutInflater.from(context).inflate(R.layout.accessibility_popup, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        View fullscreenRoot = popupView.findViewById(R.id.fullscreenRoot);
        fullscreenRoot.setOnClickListener(v -> hide());

        LinearLayout popupRoot = popupView.findViewById(R.id.popupRoot);
        popupRoot.setOnClickListener(v -> {});

        applyThemeStyles(popupRoot);
        setupSliders();
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

    public void hide() {
        if (popupView != null && windowManager != null) {
            try {
                windowManager.removeView(popupView);
            } catch (Exception ignored) {}
            popupView = null;
        }
    }

    private void applyThemeStyles(LinearLayout popupRoot) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        int panelBgColor = isDarkTheme ? Color.parseColor("#E6121212") : Color.parseColor("#E6F5F5F7");
        int primaryTextColor = isDarkTheme ? Color.parseColor("#FFFFFF") : Color.parseColor("#1C1C1E");
        int actionTextColor = isDarkTheme ? Color.parseColor("#E0E0E0") : Color.parseColor("#555555");
        int iconTint = isDarkTheme ? Color.WHITE : Color.parseColor("#1C1C1E");

        GradientDrawable backgroundShape = (GradientDrawable) popupRoot.getBackground();
        if (backgroundShape != null) backgroundShape.setColor(panelBgColor);

        setIconTint(R.id.imgBrightness, iconTint);
        setIconTint(R.id.imgVolume, iconTint);
        setIconTint(R.id.imgPower, iconTint);
        setIconTint(R.id.imgScreenshot, iconTint);
        setIconTint(R.id.imgLock, iconTint);

        setTextColor(R.id.brightnessPercentText, primaryTextColor);
        setTextColor(R.id.volumePercentText, primaryTextColor);
        setTextColor(R.id.btnPowerText, actionTextColor);
        setTextColor(R.id.btnScreenshotText, actionTextColor);
        setTextColor(R.id.btnLockText, actionTextColor);
    }

    private void setIconTint(int id, int color) {
        ImageView iv = popupView.findViewById(id);
        if (iv != null) iv.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void setTextColor(int id, int color) {
        TextView tv = popupView.findViewById(id);
        if (tv != null) tv.setTextColor(color);
    }

    private int getActiveVolumeStream() {
        int mode = audioManager.getMode();
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_CALL_SCREENING) {
            return AudioManager.STREAM_VOICE_CALL;
        }
        if (audioManager.isMusicActive()) {  // Removed isMusicActiveByPlayer() - not available on all versions
            return AudioManager.STREAM_MUSIC;
        }
        if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL || 
            audioManager.getRingerMode() == AudioManager.RINGER_MODE_RINGING) {
            return AudioManager.STREAM_RING;
        }
        return AudioManager.STREAM_MUSIC;
    }

    private String getVolumeStreamName(int stream) {
        switch (stream) {
            case AudioManager.STREAM_VOICE_CALL: return "Call Volume";
            case AudioManager.STREAM_MUSIC: return "Media Volume";
            case AudioManager.STREAM_RING: return "Ring Volume";
            case AudioManager.STREAM_NOTIFICATION: return "Notification Volume";
            case AudioManager.STREAM_ALARM: return "Alarm Volume";
            default: return "Volume";
        }
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
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessText.setText(progress + "%");
                if (fromUser) setSystemBrightness((progress * 255) / 100);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        SeekBar volumeSlider = popupView.findViewById(R.id.volumeSlider);
        TextView volumeText = popupView.findViewById(R.id.volumePercentText);
        updateVolumeUI(volumeSlider, volumeText);

        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int stream = getActiveVolumeStream();
                String name = getVolumeStreamName(stream);
                volumeText.setText(name + " " + progress + "%");
                if (fromUser) {
                    int maxVol = audioManager.getStreamMaxVolume(stream);
                    int systemVol = maxVol > 0 ? Math.round((float) progress * maxVol / 100f) : 0;
                    audioManager.setStreamVolume(stream, systemVol, 0);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    public void updateVolumeUI(SeekBar volumeSlider, TextView volumeText) {
        if (volumeSlider == null || volumeText == null) return;
        int stream = getActiveVolumeStream();
        int maxVol = audioManager.getStreamMaxVolume(stream);
        int curVol = audioManager.getStreamVolume(stream);
        int progress = maxVol > 0 ? (curVol * 100) / maxVol : 0;
        volumeSlider.setProgress(progress);
        volumeText.setText(getVolumeStreamName(stream) + " " + progress + "%");
    }

    private void setupButtons() {
        popupView.findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            hide();
            if (listener != null) listener.onScreenshotRequested();
        });

        popupView.findViewById(R.id.btnPower).setOnClickListener(v -> {
            hide();
            if (listener != null) listener.onPowerMenuRequested();
        });

        popupView.findViewById(R.id.btnLock).setOnClickListener(v -> {
            hide();
            if (listener != null) listener.onLockScreenRequested();
        });
    }

    private int getCurrentSystemBrightness() {
        try {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            return 128;
        }
    }

    private void setSystemBrightness(int value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
            }
        } else {
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, value);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
