package com.gmr.smartaccessibility;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
    private final Context context;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public UpdateManager(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void checkForUpdates() {
        executorService.execute(() -> {
            try {
                URL url = new URL(LATEST_RELEASE_API);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonObject = new JSONObject(response.toString());
                    String latestVersion = jsonObject.getString("tag_name").replace("v", "").trim();
                    
                    PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    String currentVersion = pInfo.versionName.trim();

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        JSONArray assets = jsonObject.getJSONArray("assets");
                        if (assets.length() > 0) {
                            String downloadUrl = assets.getJSONObject(0).getString("browser_download_url");
                            mainHandler.post(() -> showUpdateDialog(downloadUrl, latestVersion));
                        }
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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
        } catch (Exception e) {
            return !current.equals(latest);
        }
        return false;
    }

    private void showUpdateDialog(String downloadUrl, String newVersion) {
        new AlertDialog.Builder(context)
                .setTitle("Update Available")
                .setMessage("A new version (v" + newVersion + ") is available. Would you like to update now?")
                .setPositiveButton("Update", (dialog, which) -> checkInstallPermissionAndDownload(downloadUrl))
                .setNegativeButton("Later", null)
                .show();
    }

    private void checkInstallPermissionAndDownload(String downloadUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(context)
                        .setTitle("Permission Required")
                        .setMessage("To install updates, you must allow this app to install unknown apps.")
                        .setPositiveButton("Settings", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            intent.setData(Uri.parse("package:" + context.getPackageName()));
                            context.startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }
        downloadAndInstallApk(downloadUrl);
    }

    private void downloadAndInstallApk(String downloadUrl) {
        executorService.execute(() -> {
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                File cacheDir = new File(context.getCacheDir(), "updates");
                if (!cacheDir.exists()) cacheDir.mkdirs();

                File apkFile = new File(cacheDir, "update.apk");
                if (apkFile.exists()) apkFile.delete();

                InputStream inputStream = connection.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(apkFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();
                connection.disconnect();

                mainHandler.post(() -> launchProcessInstall(apkFile));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void launchProcessInstall(File apkFile) {
        Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
