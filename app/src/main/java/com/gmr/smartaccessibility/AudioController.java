package com.gmr.smartaccessibility;

import android.content.Context;
import android.media.AudioManager;

public class AudioController {
    private final AudioManager audioManager;

    public AudioController(Context context) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    // স্মার্ট লজিক: রিংটোন এবং নোটিফিকেশন মার্জ করা আছে কিনা তা চেক করা
    public boolean isNotificationAndRingMerged() {
        int ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
        int notifMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
        
        int ringCur = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        int notifCur = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);

        // যদি ম্যাক্সিমাম ভলিউম অথবা বর্তমান ভলিউম আলাদা হয়, তার মানে তারা কোনোভাবেই মার্জড নয়।
        if (ringMax != notifMax || ringCur != notifCur) {
            return false;
        }
        
        // যদি হুবহু মিলে যায়, তবে আমরা ধরে নেব তারা সিস্টেমে মার্জ করা আছে।
        return true;
    }

    // স্ট্রিম ফিল্টার: নোটিফিকেশন যদি মার্জড থাকে, তবে সেটিকে রিংটোনে কনভার্ট করে দেবে
    public int resolveStream(int streamType) {
        if (streamType == AudioManager.STREAM_NOTIFICATION) {
            if (isNotificationAndRingMerged()) {
                return AudioManager.STREAM_RING; // নোটিফিকেশন ভয়েড করে রিংটোন রিটার্ন করবে
            }
        }
        return streamType;
    }

    public int getActiveStream() {
        int mode = audioManager.getMode();
        int detectedStream;
        
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            detectedStream = AudioManager.STREAM_VOICE_CALL;
        } else if (mode == AudioManager.MODE_RINGTONE) {
            detectedStream = AudioManager.STREAM_RING;
        } else {
            detectedStream = AudioManager.STREAM_MUSIC;
        }
        
        return resolveStream(detectedStream);
    }

    public int getIconForStream(int streamType) {
        switch (streamType) {
            case AudioManager.STREAM_VOICE_CALL:
                return R.drawable.ic_volume_call;
            case AudioManager.STREAM_RING:
                return R.drawable.ic_volume_ring;
            case AudioManager.STREAM_ALARM:
                return R.drawable.ic_volume_alarm;
            case AudioManager.STREAM_NOTIFICATION:
                return R.drawable.ic_volume_notification;
            case AudioManager.STREAM_MUSIC:
            default:
                return R.drawable.ic_volume_media;
        }
    }

    public int getMaxVolume(int streamType) {
        return audioManager.getStreamMaxVolume(streamType);
    }

    public int getCurrentVolume(int streamType) {
        return audioManager.getStreamVolume(streamType);
    }

    public void setVolume(int streamType, int volumeLevel) {
        audioManager.setStreamVolume(streamType, volumeLevel, 0);
    }
}
