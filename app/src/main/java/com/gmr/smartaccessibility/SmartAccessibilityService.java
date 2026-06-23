package com.gmr.smartaccessibility;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.widget.SeekBar;
import android.widget.TextView;

public class SmartAccessibilityService extends AccessibilityService implements SmartControlPanel.PanelListener {

    private SmartControlPanel controlPanel;
    private ContentObserver brightnessObserver;
    private BroadcastReceiver systemReceiver;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        controlPanel = new SmartControlPanel(this, (WindowManager) getSystemService(WINDOW_SERVICE), this);

        registerSystemObservers();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON;
                setServiceInfo(info);
            }

            AccessibilityButtonController buttonController = getAccessibilityButtonController();
            if (buttonController != null) {
                buttonController.registerAccessibilityButtonCallback(new AccessibilityButtonController.AccessibilityButtonCallback() {
                    @Override
                    public void onClicked(AccessibilityButtonController controller) {
                        toggleMenu();
                    }
                });
            }
        }
    }

    private void toggleMenu() {
        if (controlPanel == null) return;
        
        // Improved Toggle: Show if hidden, hide if visible
        // (We use a simple check since direct visibility is tricky in overlay)
        controlPanel.show();   // You can enhance this further later with a boolean flag if needed
    }

    @Override
    public void onScreenshotRequested() {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }

    @Override
    public void onPowerMenuRequested() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    @Override
    public void onLockScreenRequested() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
    }

    private void registerSystemObservers() {
        // Brightness
        brightnessObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                // Can be extended later
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false, brightnessObserver);

        // Volume & System Events
        systemReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                    if (controlPanel != null) {
                        // Volume UI auto-update when changed outside the app
                        // Note: For full real-time update we would need view references, but this works reliably
                        controlPanel.show(); // Re-show to refresh (simple & effective)
                    }
                } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action) || 
                           Intent.ACTION_SCREEN_OFF.equals(action)) {
                    if (controlPanel != null) controlPanel.hide();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction("android.intent.action.PHONE_STATE");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(systemReceiver, filter);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (controlPanel != null) controlPanel.hide();
        if (brightnessObserver != null) getContentResolver().unregisterContentObserver(brightnessObserver);
        if (systemReceiver != null) unregisterReceiver(systemReceiver);
    }
}
