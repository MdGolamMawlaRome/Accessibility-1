package com.gmr.smartaccessibility;

import android.content.Context;
import android.media.AudioManager;

public class AudioController {
    private final AudioManager audioManager;

    public AudioController(Context context) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    // ডায়নামিক ডিটেকশন লজিক: বর্তমান সিস্টেমের রানিং অবস্থা অনুযায়ী সঠিক অডিও স্ট্রিম বের করা
    public int getActiveStream() {
        int mode = audioManager.getMode();
        
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            return AudioManager.STREAM_VOICE_CALL;
        } else if (mode == AudioManager.MODE_RINGTONE) {
            return AudioManager.STREAM_RING;
        } else {
            // ডিফল্ট হিসেবে মিডিয়া ভলিউম থাকবে
            return AudioManager.STREAM_MUSIC;
        }
    }

    // বর্তমান অ্যাক্টিভ স্ট্রিমের ওপর ভিত্তি করে সঠিক ভেক্টর আইকন রিটার্ন করা
    public int getIconForStream(int streamType) {
        switch (streamType) {
            case AudioManager.STREAM_VOICE_CALL:
                return R.drawable.ic_volume_call;
            case AudioManager.STREAM_RING:
                return R.drawable.ic_volume_ring;
            case AudioManager.STREAM_ALARM:
                return R.drawable.ic_volume_alarm;
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
