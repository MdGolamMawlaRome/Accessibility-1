package com.gmr.smartaccessibility;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import java.util.HashSet;
import java.util.Set;

public class VolumeCalibrator {
    private final Context context;
    private final AudioManager audioManager;
    private static final String PREF_NAME = "VolumeConfig";
    private static final String KEY_DETECTED_STREAMS = "detected_streams";

    public VolumeCalibrator(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void runCalibration() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // যদি ইতিমধ্যে ক্যালিব্রেশন করা থাকে, তবে নতুন করে করার প্রয়োজন নেই
        if (prefs.contains(KEY_DETECTED_STREAMS)) return;

        Set<String> activeStreams = new HashSet<>();
        int[] streamTypes = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_SYSTEM
        };

        for (int stream : streamTypes) {
            try {
                // চেক করা হচ্ছে এই স্ট্রিমটি এই ফোনে কাজ করে কি না
                int currentVol = audioManager.getStreamVolume(stream);
                int maxVol = audioManager.getStreamMaxVolume(stream);
                
                // ভলিউম চেক করে যদি রেসপন্স পাওয়া যায়, তবে এটি একটি ভ্যালিড স্ট্রিম
                if (maxVol > 0) {
                    activeStreams.add(String.valueOf(stream));
                }
            } catch (Exception e) {
                // কিছু ডিভাইসে নির্দিষ্ট স্ট্রিম সাপোর্ট না করলে এখানে আসবে
            }
        }

        // ক্যালিব্রেশনের রেজাল্ট অ্যাপের নিজস্ব ইন্টারনাল স্টোরেজে সেভ করা হলো
        prefs.edit().putStringSet(KEY_DETECTED_STREAMS, activeStreams).apply();
    }

    // অন্য যেকোনো জায়গা থেকে এই লিস্টটি নিতে চাইলে এটি ব্যবহার করুন
    public static Set<String> getDetectedStreams(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_DETECTED_STREAMS, new HashSet<>());
    }
}
