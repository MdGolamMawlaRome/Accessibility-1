package com.gmr.smartaccessibility;

import android.content.Context;
import android.media.AudioManager;

public class AudioController {
    private final AudioManager audioManager;

    public AudioController(Context context) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean isNotificationMergedWithRing() {
        int ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
        int notifMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
        int ringCur = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        int notifCur = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        return (ringMax == notifMax && ringCur == notifCur);
    }

    public int getActiveStream() {
        int mode = audioManager.getMode();
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            return AudioManager.STREAM_VOICE_CALL;
        } else if (mode == AudioManager.MODE_RINGTONE) {
            return AudioManager.STREAM_RING;
        }
        return AudioManager.STREAM_MUSIC;
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
