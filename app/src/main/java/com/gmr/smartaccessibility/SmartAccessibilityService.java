package com.gmr.smartaccessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Set;

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

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // অ্যাপ চালু হওয়ার সাথে সাথেই ব্যাকগ্রাউন্ড থ্রেডে অটো-ডিটেকশন ও ক্যালিব্রেশন রান হবে
        new Thread(new Runnable() {
            @Override
            public void run() {
                VolumeCalibrator calibrator = new VolumeCalibrator(SmartAccessibilityService.this);
                calibrator.runCalibration();
            }
        }).start();

        registerSystemObservers();
        setupAccessibilityButton();
    }

    private void setupAccessibilityButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AccessibilityButtonController accessibilityButtonController = getAccessibilityButtonController();
            accessibilityButtonController.registerAccessibilityButtonCallback(
                new AccessibilityButtonController.AccessibilityButtonCallback() {
                    @Override
                    public void onClicked(AccessibilityButtonController controller) {
                        if (popupView == null) {
                            showMenu();
                        } else {
                            hideMenu();
                        }
                    }
                }
            );
        }
    }

    private void registerSystemObservers() {
        brightnessObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                if (popupView != null) {
                    SeekBar brightnessSlider = popupView.findViewById(R.id.brightnessSlider);
                    TextView brightnessText = popupView.findViewById(R.id.brightnessPercentText);
                    updateBrightnessSlider(brightnessSlider, brightnessText);
                }
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false,
                brightnessObserver
        );

        systemReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                    if (popupView != null) {
                        // এখানে পুরোনো আইডিগুলোর পরিবর্তে সঠিক মেইন আইডি বসানো হয়েছে
                        SeekBar volumeSlider = popupView.findViewById(R.id.mainVolumeSlider);
                        TextView volumeText = popupView.findViewById(R.id.mainVolumePercentText);
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

    private void showMenu() {
        if (popupView != null) return;

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        popupView = inflater.inflate(R.layout.accessibility_popup, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        windowManager.addView(popupView, params);

        // এখানেও পুরোনো আইডিগুলোর পরিবর্তে সঠিক মেইন আইডি বসানো হয়েছে
        SeekBar volumeSlider = popupView.findViewById(R.id.mainVolumeSlider);
        TextView volumeText = popupView.findViewById(R.id.mainVolumePercentText);
        SeekBar brightnessSlider = popupView.findViewById(R.id.brightnessSlider);
        TextView brightnessText = popupView.findViewById(R.id.brightnessPercentText);

        if (volumeSlider != null && volumeText != null) {
            updateVolumeSlider(volumeSlider, volumeText);
            volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        Set<String> streams = VolumeCalibrator.getDetectedStreams(SmartAccessibilityService.this);
                        if (streams.isEmpty()) {
                            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            int vol = (progress * max) / 100;
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                        } else {
                            // ক্যালিব্রেশনে পাওয়া সকল ভ্যালিড ভলিউম স্ট্রিম একসাথে কন্ট্রোল হবে
                            for (String streamStr : streams) {
                                try {
                                    int streamType = Integer.parseInt(streamStr);
                                    int max = audioManager.getStreamMaxVolume(streamType);
                                    int vol = (progress * max) / 100;
                                    audioManager.setStreamVolume(streamType, vol, 0);
                                } catch (Exception e) {
                                    // ইগনোর এরর
                                }
                            }
                        }
                        volumeText.setText(progress + "%");
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (brightnessSlider != null && brightnessText != null) {
            updateBrightnessSlider(brightnessSlider, brightnessText);
            brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int brightness = (progress * 255) / 100;
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightness);
                        brightnessText.setText(progress + "%");
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        View fullscreenRoot = popupView.findViewById(R.id.fullscreenRoot);
        if (fullscreenRoot != null) {
            fullscreenRoot.setOnClickListener(v -> hideMenu());
        }

        View btnLock = popupView.findViewById(R.id.btnLock);
        if (btnLock != null) {
            btnLock.setOnClickListener(v -> {
                hideMenu();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                }
            });
        }

        View btnPower = popupView.findViewById(R.id.btnPower);
        if (btnPower != null) {
            btnPower.setOnClickListener(v -> {
                hideMenu();
                performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            });
        }

        View btnScreenshot = popupView.findViewById(R.id.btnScreenshot);
        if (btnScreenshot != null) {
            btnScreenshot.setOnClickListener(v -> {
                hideMenu();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                }
            });
        }
    }

    private void hideMenu() {
        if (popupView != null && windowManager != null) {
            windowManager.removeView(popupView);
            popupView = null;
        }
    }

    private void updateVolumeSlider(SeekBar volumeSlider, TextView volumeText) {
        if (volumeSlider == null || volumeText == null) return;
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (max > 0) {
            int progress = (current * 100) / max;
            volumeSlider.setProgress(progress);
            volumeText.setText(progress + "%");
        }
    }

    private void updateBrightnessSlider(SeekBar brightnessSlider, TextView brightnessText) {
        if (brightnessSlider == null || brightnessText == null) return;
        try {
            int brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            int progress = (brightness * 100) / 255;
            brightnessSlider.setProgress(progress);
            brightnessText.setText(progress + "%");
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
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
