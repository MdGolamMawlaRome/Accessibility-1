package com.gmr.smartaccessibility;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

public class HomeMenuController {

    private final Context context;
    private PopupWindow popupWindow;

    public HomeMenuController(Context context) {
        this.context = context;
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
