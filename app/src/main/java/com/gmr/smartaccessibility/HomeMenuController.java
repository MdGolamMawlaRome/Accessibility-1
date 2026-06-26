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
        popupWindow = new PopupWindow(menuView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(16f);

        View rowSettings = menuView.findViewById(R.id.rowSettings);
        if (rowSettings != null) {
            rowSettings.setOnClickListener(v -> {
                popupWindow.dismiss();
                context.startActivity(new Intent(context, SettingsActivity.class));
            });
        }

        View rowCheckUpdate = menuView.findViewById(R.id.rowCheckUpdate);
        if (rowCheckUpdate != null) {
            rowCheckUpdate.setOnClickListener(v -> {
                popupWindow.dismiss();
                context.startActivity(new Intent(context, CheckUpdateActivity.class));
            });
        }

        View rowAppInfo = menuView.findViewById(R.id.rowAppInfo);
        if (rowAppInfo != null) {
            rowAppInfo.setOnClickListener(v -> {
                popupWindow.dismiss();
                context.startActivity(new Intent(context, AppInfoActivity.class));
            });
        }

        popupWindow.showAsDropDown(anchorView, 0, 0);
    }
}
