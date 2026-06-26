package com.gmr.smartaccessibility;

public interface UpdateCallback {
    void onUpdateAvailable(String latestVersion, String downloadUrl);
    void onUpToDate();
    void onError(String message);
}
