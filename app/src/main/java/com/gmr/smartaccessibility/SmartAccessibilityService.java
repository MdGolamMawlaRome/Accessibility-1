package com.gmr.smartaccessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

public class SmartAccessibilityService extends AccessibilityService {

    private PopupUIController popupUIController;
    private AudioController audioController;
    private ContentObserver brightnessObserver;
    private BroadcastReceiver systemReceiver;
    private View floatingButtonView;
    private WindowManager windowManager;

    // সিকিউরিটি চেকিংয়ের জন্য কোড
    private final Handler securityHandler = new Handler(Looper.getMainLooper());
    private final long CHECK_INTERVAL = 5 * 60 * 1000; // ৫ মিনিট পর পর চেক করবে

    // বাটন ট্রান্সপারেন্সি কন্ট্রোল করার জন্য নতুন হ্যান্ডলার
    private final Handler fadeHandler = new Handler(Looper.getMainLooper());
    private final Runnable fadeRunnable = () -> {
        if (floatingButtonView != null) {
            floatingButtonView.setAlpha(0.3f); // ৭০% ট্রান্সপারেন্ট (৩০% দৃশ্যমান)
        }
    };

    private final Runnable securityRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                boolean isAuthorized = AccessControlManager.isDeviceAuthorized(SmartAccessibilityService.this);
                if (!isAuthorized) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (popupUIController != null) popupUIController.hideMenu();
                        disableSelf();
                    });
                }
            }).start();
            securityHandler.postDelayed(this, CHECK_INTERVAL);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        audioController = new AudioController(this);
        popupUIController = new PopupUIController(this, audioController);

        createFloatingButton();

        new Thread(() -> {
            VolumeCalibrator calibrator = new VolumeCalibrator(SmartAccessibilityService.this);
            calibrator.runCalibration();
        }).start();

        registerSystemObservers();
        setupAccessibilityButton();

        securityHandler.post(securityRunnable);
    }

    private void createFloatingButton() {
        ImageView fab = new ImageView(this);
        fab.setImageResource(R.drawable.ic_floating_icon);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                120, 120,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;

        // বাটনটি তৈরি হওয়ার ৩ সেকেন্ড পর ট্রান্সপারেন্ট করার টাইমার সেট করা
        fadeHandler.postDelayed(fadeRunnable, 3000);

        fab.setOnClickListener(v -> {
            // বাটনে ক্লিক করলে পুনরায় উজ্জ্বল করে দেয়া এবং টাইমার রিসেট করা
            fab.setAlpha(1.0f);
            fadeHandler.removeCallbacks(fadeRunnable);
            fadeHandler.postDelayed(fadeRunnable, 3000);

            if (!AccessControlManager.getCachedAuthState(SmartAccessibilityService.this)) {
                if (popupUIController != null) popupUIController.hideMenu();
                disableSelf();
                return;
            }
            if (popupUIController != null) {
                popupUIController.toggleMenu();
            }
        });

        windowManager.addView(fab, params);
        floatingButtonView = fab;
    }

    private void setupAccessibilityButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AccessibilityButtonController accessibilityButtonController = getAccessibilityButtonController();
            accessibilityButtonController.registerAccessibilityButtonCallback(
                    new AccessibilityButtonController.AccessibilityButtonCallback() {
                        @Override
                        public void onClicked(AccessibilityButtonController controller) {
                            if (!AccessControlManager.getCachedAuthState(SmartAccessibilityService.this)) {
                                if (popupUIController != null) popupUIController.hideMenu();
                                disableSelf();
                                return;
                            }

                            if (popupUIController != null) {
                                popupUIController.toggleMenu();
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
                if (popupUIController != null && popupUIController.isShowing()) {
                    popupUIController.updateBrightnessUI();
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
                    if (popupUIController != null && popupUIController.isShowing()) {
                        popupUIController.updateAllVolumeSliders();
                    }
                } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action) || Intent.ACTION_SCREEN_OFF.equals(action)) {
                    if (popupUIController != null) {
                        popupUIController.hideMenu();
                    }
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

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();

        // বাটন রিমুভ করার সময় টাইমার রিমুভ করা
        fadeHandler.removeCallbacks(fadeRunnable);

        if (floatingButtonView != null && windowManager != null) {
            try { windowManager.removeView(floatingButtonView); } catch (Exception e) {}
            floatingButtonView = null;
        }

        securityHandler.removeCallbacks(securityRunnable);

        if (popupUIController != null) {
            popupUIController.hideMenu();
        }
        if (brightnessObserver != null) {
            getContentResolver().unregisterContentObserver(brightnessObserver);
        }
        if (systemReceiver != null) {
            unregisterReceiver(systemReceiver);
        }
    }
}
