package com.gmr.smartaccessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

public class SmartAccessibilityService extends AccessibilityService {
    private PopupUIController popupUIController;
    private BroadcastReceiver volumeReceiver;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        popupUIController = new PopupUIController(this);
        
        // ভলিউম পরিবর্তনের জন্য রিসিভার
        volumeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                    if (popupUIController != null) {
                        popupUIController.updateAllSliders();
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(volumeReceiver, filter);
        }
    }

    public void showPopup() {
        if (popupUIController != null) popupUIController.show();
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (volumeReceiver != null) unregisterReceiver(volumeReceiver);
        if (popupUIController != null) popupUIController.hide();
        super.onDestroy();
    }
}
