package com.gmr.smartaccessibility;

import android.content.Context;
import android.media.AudioManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;

public class PopupUIController {
    private Context context;
    private WindowManager windowManager;
    private View popupView;
    private AudioManager audioManager;
    private boolean isShowing = false;

    public PopupUIController(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (!isShowing) {
            popupView = LayoutInflater.from(context).inflate(R.layout.accessibility_popup, null);
            // এখানে স্লাইডার সেটআপ এবং লিসেনার যোগ করুন
            updateAllSliders();
            // উইন্ডো ম্যানেজার প্যারামস যোগ করে view যোগ করুন
            isShowing = true;
        }
    }

    public void updateAllSliders() {
        if (popupView != null) {
            // প্রতিটি স্ট্রিমের জন্য লজিক আপডেট
            // উদাহরণ: int ringVol = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        }
    }

    public void hide() {
        if (isShowing && popupView != null) {
            windowManager.removeView(popupView);
            isShowing = false;
        }
    }
}
