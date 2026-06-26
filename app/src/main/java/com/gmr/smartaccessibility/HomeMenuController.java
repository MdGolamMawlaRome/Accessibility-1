package com.gmr.smartaccessibility;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

public class HomeMenuController {

    private final Context context;
    private final UpdateManager updateManager;
    private PopupWindow popupWindow;

    public HomeMenuController(Context context, UpdateManager updateManager) {
        this.context = context;
        this.updateManager = updateManager;
    }

    public void showMenu(View anchorView) {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
            return;
        }

        View menuView = LayoutInflater.from(context).inflate(R.layout.layout_home_menu, null);
        
        popupWindow = new PopupWindow(
                menuView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(16f);

        // Logic for Settings Row
        View rowSettings = menuView.findViewById(R.id.rowSettings);
        if (rowSettings != null) {
            rowSettings.setOnClickListener(v -> {
                popupWindow.dismiss();
                Intent intent = new Intent(context, SettingsActivity.class);
                context.startActivity(intent);
            });
        }

        // Logic for Check for Updates Row
        View rowCheckUpdate = menuView.findViewById(R.id.rowCheckUpdate);
        if (rowCheckUpdate != null) {
            rowCheckUpdate.setOnClickListener(v -> {
                popupWindow.dismiss();
                if (updateManager != null) {
                    // Passed "true" so it ignores the 6-hour interval and checks instantly
                    updateManager.checkForUpdates(true);
                }
            });
        }

        // Logic for App Info Row
        View rowAppInfo = menuView.findViewById(R.id.rowAppInfo);
        if (rowAppInfo != null) {
            rowAppInfo.setOnClickListener(v -> {
                popupWindow.dismiss();
                Intent intent = new Intent(context, AppInfoActivity.class);
                context.startActivity(intent);
            });
        }

        popupWindow.showAsDropDown(anchorView, 0, 0);
    }
}
