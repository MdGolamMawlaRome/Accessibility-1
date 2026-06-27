package com.gmr.smartaccessibility;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccessControlManager {

    // আপনার গিটহাবের রুলস ফাইলের ডিরেক্ট লিঙ্ক
    private static final String RULES_URL = "https://raw.githubusercontent.com/MdGolamMawlaRome/Smart-Accessibility/main/access_rules.json";
    
    // গুগল ফর্মের লিঙ্ক এবং এন্ট্রি আইডি (আপনার লিঙ্ক থেকে নেয়া)
    private static final String FORM_URL = "https://docs.google.com/forms/d/e/1FAIpQLSfiLxhYKcEa19eVPuL9HGGKNRR7uHDDNjeDVfnMsSZIxd7VqQ/formResponse";
    private static final String ENTRY_DEVICE_MODEL = "entry.1395778414";
    private static final String ENTRY_DEVICE_ID = "entry.1072977240";

    private final Activity activity;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final String deviceId;
    private final String deviceModel;

    public AccessControlManager(Activity activity) {
        this.activity = activity;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // ডিভাইসের ইউনিক আইডি এবং মডেল বের করা
        this.deviceId = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);
        this.deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
    }

    public void checkAccessAndReport() {
        executor.execute(() -> {
            reportDeviceToGoogleSheet(); // ডিভাইসের তথ্য শিটে পাঠানো
            verifyAccessRules();         // গিটহাব থেকে রুলস চেক করা
        });
    }

    private void reportDeviceToGoogleSheet() {
        SharedPreferences prefs = activity.getSharedPreferences("DeviceTracker", Activity.MODE_PRIVATE);
        boolean isReported = prefs.getBoolean("is_reported", false);
        
        // একবার রিপোর্ট হয়ে গেলে আর পাঠাবে না, ডেটা বাঁচানোর জন্য
        if (isReported) return; 

        try {
            URL url = new URL(FORM_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postData = ENTRY_DEVICE_MODEL + "=" + URLEncoder.encode(deviceModel, "UTF-8") +
                    "&" + ENTRY_DEVICE_ID + "=" + URLEncoder.encode(deviceId, "UTF-8");

            OutputStream os = conn.getOutputStream();
            os.write(postData.getBytes());
            os.flush();
            os.close();

            if (conn.getResponseCode() == 200) {
                prefs.edit().putBoolean("is_reported", true).apply();
            }
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void verifyAccessRules() {
        try {
            URL url = new URL(RULES_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                
                JSONObject json = new JSONObject(sb.toString());
                String status = json.getString("status"); 
                JSONArray allowed = json.optJSONArray("allowed");
                JSONArray denied = json.optJSONArray("denied");

                boolean hasAccess = true;

                // চেকিং লজিক
                if ("BLOCKED".equals(status)) {
                    hasAccess = false; // সব বন্ধ
                    if (allowed != null && containsId(allowed, deviceId)) hasAccess = true; // শুধু অ্যালাউড লিস্টের ছাড়া
                } else {
                    // "ALL" (সবার জন্য খোলা)
                    if (denied != null && containsId(denied, deviceId)) hasAccess = false; // শুধু ডিনাইড লিস্টের ছাড়া
                }

                if (!hasAccess) {
                    mainHandler.post(this::showBlockedDialogAndExit);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean containsId(JSONArray array, String id) throws Exception {
        if (array == null) return false;
        for (int i = 0; i < array.length(); i++) {
            if (array.getString(i).equals(id)) return true;
        }
        return false;
    }

    private void showBlockedDialogAndExit() {
        new AlertDialog.Builder(activity)
                .setTitle("Access Denied")
                .setMessage("Your device ID (" + deviceId + ") is not authorized to use this application.\nIf you think this is a mistake, please contact the developer.")
                .setCancelable(false)
                .setPositiveButton("Exit", (dialog, which) -> activity.finishAffinity())
                .show();
    }
}
