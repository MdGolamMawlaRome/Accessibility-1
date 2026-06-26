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
    public static final String KEY_IS_MERGED = "is_notification_merged";

    public VolumeCalibrator(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void runCalibration() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // একবার ক্যালিব্রেশন হয়ে গেলে আর চেক করবে না
        if (prefs.contains(KEY_DETECTED_STREAMS)) return;

        Set<String> activeStreams = new HashSet<>();
        int[] streamTypes = {
            AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM,
            AudioManager.STREAM_SYSTEM, AudioManager.STREAM_VOICE_CALL
        };

        // ১. কোন কোন ভলিউম কাজ করে তা বের করা
        for (int stream : streamTypes) {
            try {
                if (audioManager.getStreamMaxVolume(stream) > 0) {
                    activeStreams.add(String.valueOf(stream));
                }
            } catch (Exception e) {}
        }

        // ২. রিং এবং নোটিফিকেশন মার্চড (Merged) কি না, তার ফিজিক্যাল টেস্ট
        boolean isMerged = false;
        try {
            int origRing = audioManager.getStreamVolume(AudioManager.STREAM_RING);
            int origNotif = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            int maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);

            if (maxRing > 0) {
                // রিংটোনের ভলিউম ১ পয়েন্ট পরিবর্তন করা
                int testVol = (origRing < maxRing) ? origRing + 1 : origRing - 1;
                audioManager.setStreamVolume(AudioManager.STREAM_RING, testVol, 0);
                
                // চেক করা নোটিফিকেশনের ভলিউম নিজে নিজে পরিবর্তন হলো কি না
                int newNotif = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
                if (newNotif != origNotif) {
                    isMerged = true; // যদি পরিবর্তন হয়, তারমানে মার্চড
                }
                
                // টেস্ট শেষে আগের অবস্থায় ফিরিয়ে দেওয়া
                audioManager.setStreamVolume(AudioManager.STREAM_RING, origRing, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, origNotif, 0);
            }
        } catch (Exception e) {}

        // ডেটা ফাইলে সেভ করে দেওয়া হলো
        prefs.edit()
             .putStringSet(KEY_DETECTED_STREAMS, activeStreams)
             .putBoolean(KEY_IS_MERGED, isMerged)
             .apply();
    }

    public static Set<String> getDetectedStreams(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                      .getStringSet(KEY_DETECTED_STREAMS, new HashSet<>());
    }

    public static boolean isNotificationMerged(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                      .getBoolean(KEY_IS_MERGED, false);
    }
}
