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

public class SmartAccessibilityService extends AccessibilityService {

    private PopupUIController popupUIController;
    private AudioController audioController;
    private ContentObserver brightnessObserver;
    private BroadcastReceiver systemReceiver;

    // সিকিউরিটি চেকিংয়ের জন্য নতুন কোড
    private final Handler securityHandler = new Handler(Looper.getMainLooper());
    private final long CHECK_INTERVAL = 6 * 60 * 60 * 1000; // ৬ ঘণ্টা মিলিসেকেন্ডে

    private final Runnable securityRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                // AccessControlManager কে কল করে অথরাইজড কিনা চেক করা
                boolean isAuthorized = AccessControlManager.isDeviceAuthorized(SmartAccessibilityService.this);
                if (!isAuthorized) {
                    // অনুমোদিত না হলে সার্ভিসটি সাথে সাথে বন্ধ করা
                    disableSelf();
                }
            }).start();
            
            // ৬ ঘণ্টা পর পুনরায় রান করার জন্য
            securityHandler.postDelayed(this, CHECK_INTERVAL);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        audioController = new AudioController(this);
        popupUIController = new PopupUIController(this, audioController);

        // ব্যাকগ্রাউন্ড থ্রেডে অটো-ডিটেকশন ও ক্যালিব্রেশন রান করা হলো
        new Thread(new Runnable() {
            @Override
            public void run() {
                VolumeCalibrator calibrator = new VolumeCalibrator(SmartAccessibilityService.this);
                calibrator.runCalibration();
            }
        }).start();

        registerSystemObservers();
        setupAccessibilityButton();

        // সার্ভিস কানেক্ট হওয়ার সাথে সাথে প্রথমবার সিকিউরিটি চেক রান করা
        securityHandler.post(securityRunnable);
    }

    private void setupAccessibilityButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AccessibilityButtonController accessibilityButtonController = getAccessibilityButtonController();
            accessibilityButtonController.registerAccessibilityButtonCallback(
                new AccessibilityButtonController.AccessibilityButtonCallback() {
                    @Override
                    public void onClicked(AccessibilityButtonController controller) {
                        if (popupUIController != null) {
                            popupUIController.toggleMenu();
                        }
                    }
                }
            );
        }
    }

    private void registerSystemObservers() {
        // সিস্টেম ব্রাইটনেস পরিবর্তনের লিসেনার যা কন্ট্রোলারের ইউআই আপডেট করবে
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

        // সিস্টেম ভলিউম পরিবর্তন অথবা স্ক্রিন অফ হলে মেনু বন্ধ করার রিসিভার
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

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // সিকিউরিটি হ্যান্ডলার রিমুভ করা
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
