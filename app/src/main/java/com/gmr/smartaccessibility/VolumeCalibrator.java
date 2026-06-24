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
        
        // যদি ইতিমধ্যে একবার ক্যালিব্রেশন করা থাকে, তবে আর রান করবে না
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
                int originalVol = audioManager.getStreamVolume(stream);
                int maxVol = audioManager.getStreamMaxVolume(stream);
                
                if (maxVol > 0) {
                    // ইউজারকে না জানিয়ে ব্যাকগ্রাউন্ডে হালকা বাড়িয়ে বা কমিয়ে টেস্ট করা হচ্ছে
                    int testVol = originalVol;
                    if (originalVol < maxVol) {
                        testVol = originalVol + 1;
                    } else if (originalVol > 0) {
                        testVol = originalVol - 1;
                    }
                    
                    // ভলিউম পরিবর্তন করা হলো (সাইলেন্টলি, কোনো ফ্ল্যাগ ছাড়া)
                    audioManager.setStreamVolume(stream, testVol, 0);
                    int checkVol = audioManager.getStreamVolume(stream);
                    
                    // যদি ভলিউম সফলভাবে পরিবর্তন হয়, তবে এটি একটি কার্যকর স্ট্রিম হিসেবে যুক্ত হবে
                    if (checkVol == testVol) {
                        activeStreams.add(String.valueOf(stream));
                    }
                    
                    // টেস্ট শেষে ভলিউম আবার আগের অবস্থায় ফিরিয়ে দেওয়া হলো
                    audioManager.setStreamVolume(stream, originalVol, 0);
                }
            } catch (Exception e) {
                // কোনো ডিভাইসে নির্দিষ্ট স্ট্রিম লক করা থাকলে এরর এড়াতে ট্রাই-ক্যাচ ব্যবহার করা হয়েছে
            }
        }

        // কার্যকর ভলিউম স্ট্রিমগুলোর লিস্ট হাইডেন স্পেস বা নিজস্ব ডাইরেক্টরিতে সেভ করা হলো
        prefs.edit().putStringSet(KEY_DETECTED_STREAMS, activeStreams).apply();
    }

    public static Set<String> getDetectedStreams(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_DETECTED_STREAMS, new HashSet<>());
    }
}
