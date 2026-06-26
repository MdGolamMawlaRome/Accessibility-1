package com.gmr.smartaccessibility;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {

    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/MdGolamMawlaRome/Smart-Accessibility/releases/latest";
    private static final String PREFS_NAME = "UpdatePrefs";
    private static final String KEY_LAST_CHECK = "last_check_time";
    private static final String KEY_PENDING_URL = "pending_update_url";
    
    private final Context context;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final SharedPreferences prefs;

    public UpdateManager(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Background auto-check
    public void checkForUpdates(boolean isManualCheck) {
        long lastCheckTime = prefs.getLong(KEY_LAST_CHECK, 0);
        long currentTime = System.currentTimeMillis();
        long updateCheckingInterval = 6 * 60 * 60 * 1000;

        if (!isManualCheck && (currentTime - lastCheckTime < updateCheckingInterval)) return;

        executorService.execute(() -> {
            try {
                fetchUpdateData(new UpdateCallback() {
                    @Override
                    public void onUpdateAvailable(String version, String downloadUrl) {
                        showUpdateNotification(downloadUrl);
                    }
                    @Override public void onUpToDate() {}
                    @Override public void onError(String msg) {}
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    // New method for CheckUpdateActivity
    public void checkForUpdatesWithCallback(UpdateCallback callback) {
        executorService.execute(() -> fetchUpdateData(callback));
    }

    private void fetchUpdateData(UpdateCallback callback) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(LATEST_RELEASE_API);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Smart-Accessibility-App");

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);

                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

                JSONObject jsonObject = new JSONObject(response.toString());
                String latestVersion = jsonObject.getString("tag_name").replace("v", "").trim();
                
                PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                String currentVersion = pInfo.versionName.trim();

                if (isNewerVersion(currentVersion, latestVersion)) {
                    JSONArray assets = jsonObject.getJSONArray("assets");
                    if (assets.length() > 0) {
                        String downloadUrl = assets.getJSONObject(0).getString("browser_download_url");
                        mainHandler.post(() -> callback.onUpdateAvailable(latestVersion, downloadUrl));
                    }
                } else {
                    mainHandler.post(callback::onUpToDate);
                }
            } else {
                mainHandler.post(() -> callback.onError("Server Error"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            mainHandler.post(() -> callback.onError("Network Error"));
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isNewerVersion(String current, String latest) {
        try {
            String[] currParts = current.split("\\.");
            String[] lateParts = latest.split("\\.");
            int length = Math.max(currParts.length, lateParts.length);
            for (int i = 0; i < length; i++) {
                int currPart = i < currParts.length ? Integer.parseInt(currParts[i]) : 0;
                int latePart = i < lateParts.length ? Integer.parseInt(lateParts[i]) : 0;
                if (latePart > currPart) return true;
                if (currPart > latePart) return false;
            }
        } catch (Exception e) { return !current.equals(latest); }
        return false;
    }

    private void showUpdateNotification(String downloadUrl) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "update_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("UPDATE_URL", downloadUrl);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Update Available")
                .setContentText("A new update is available. Tap to install.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(1001, builder.build());
    }

    public void processUpdate(String downloadUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.getPackageManager().canRequestPackageInstalls()) {
            prefs.edit().putString(KEY_PENDING_URL, downloadUrl).apply();
            new AlertDialog.Builder(context)
                    .setTitle("Permission Required")
                    .setMessage("To install the update, please allow 'Install Unknown Apps' permission in settings.")
                    .setPositiveButton("Go to Settings", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        intent.setData(Uri.parse("package:" + context.getPackageName()));
                        context.startActivity(intent);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> prefs.edit().remove(KEY_PENDING_URL).apply())
                    .show();
            return;
        }
        startDownload(downloadUrl);
    }

    public void resumeUpdateFlow() {
        String pendingUrl = prefs.getString(KEY_PENDING_URL, null);
        if (pendingUrl != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(context)
                        .setTitle("Ready to Update")
                        .setMessage("Permission granted! Would you like to start the update now?")
                        .setPositiveButton("Update", (dialog, which) -> startDownload(pendingUrl))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            prefs.edit().remove(KEY_PENDING_URL).apply();
        }
    }

    private void startDownload(String downloadUrl) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            try {
                URL url = new URL(downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                File cacheDir = new File(context.getExternalCacheDir(), "updates");
                if (!cacheDir.exists()) cacheDir.mkdirs();

                File apkFile = new File(cacheDir, "update.apk");
                if (apkFile.exists()) apkFile.delete();

                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(apkFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, bytesRead);

                outputStream.flush();
                mainHandler.post(() -> launchProcessInstall(apkFile));
            } catch (Exception e) { e.printStackTrace(); } 
            finally {
                try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) {}
                try { if (inputStream != null) inputStream.close(); } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void launchProcessInstall(File apkFile) {
        new AlertDialog.Builder(context)
                .setTitle("Update Guidance")
                .setMessage("Dear user, if you are seeing this message, so that means my app is still in verification process under Google. So , for currently you have to click a few buttons for update, firstly, when play store comes, you have to click on \"more\" and than \"install anyway\"")
                .setCancelable(false)
                .setPositiveButton("Proceed to Install", (dialog, which) -> {
                    try {
                        Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e) { e.printStackTrace(); }
                })
                .show();
    }
}
