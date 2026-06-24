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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

public class SmartAccessibilityService extends AccessibilityService {

    private PopupUIController popupUIController;
    private AudioController audioController;
    private ContentObserver brightnessObserver;
    private BroadcastReceiver systemReceiver;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        audioController = new AudioController(this);
        popupUIController = new PopupUIController(this, audioController);
        
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
                        popupUIController.toggleMenu();
                    }
                }
            );
        }
    }

    private void registerSystemObservers() {
        brightnessObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                if (popupUIController.isShowing()) {
                    popupUIController.updateBrightnessUI();
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
                    // ভলিউম পরিবর্তন হলে পপআপের স্লাইডার আপডেট হবে
                    if (popupUIController.isShowing()) {
                        popupUIController.updateAllVolumeSliders();
                    }
                } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action) || Intent.ACTION_SCREEN_OFF.equals(action)) {
                    popupUIController.hideMenu();
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (popupUIController.isShowing()) {
            popupUIController.hideMenu();
            popupUIController.showMenu();
        }
    }

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (popupUIController != null) {
            popupUIController.hideMenu();
        }
        if (brightnessObserver != null) getContentResolver().unregisterContentObserver(brightnessObserver);
        if (systemReceiver != null) unregisterReceiver(systemReceiver);
    }
}
