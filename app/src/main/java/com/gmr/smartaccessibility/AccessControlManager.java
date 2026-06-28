package com.gmr.smartaccessibility;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AccessControlManager {

    private static final String RULES_URL = "https://raw.githubusercontent.com/MdGolamMawlaRome/Smart-Accessibility/main/access_rules.json";
    private static final String PREF_NAME = "AccessPrefs";
    private static final String KEY_IS_AUTHORIZED = "is_authorized";

    // সার্ভার থেকে লাইভ চেক করবে এবং ডাটা সেভ করবে
    public static boolean isDeviceAuthorized(Context context) {
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // ডিফল্ট স্ট্যাটাস (আগে কখনো চেক না হয়ে থাকলে true ধরবে, তবে একবার ব্লক হলে সেটাই সেভ থাকবে)
        boolean lastKnownState = prefs.getBoolean(KEY_IS_AUTHORIZED, true);

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

                boolean isAuthorized = true;

                if ("BLOCKED".equals(status)) {
                    isAuthorized = (allowed != null && containsId(allowed, deviceId));
                } else {
                    isAuthorized = !(denied != null && containsId(denied, deviceId));
                }

                // নতুন স্ট্যাটাসটি মেমরিতে সেভ করে রাখা হচ্ছে
                prefs.edit().putBoolean(KEY_IS_AUTHORIZED, isAuthorized).apply();
                return isAuthorized;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // যদি ইন্টারনেটে সমস্যা হয়, তাহলে সর্বশেষ সেভ করা স্ট্যাটাস রিটার্ন করবে (অফলাইন বাইপাস প্রটেকশন)
        return lastKnownState;
    }

    // অ্যাক্সেসিবিলিটি বাটন ক্লিক করার সাথে সাথে ইনস্ট্যান্ট চেক করার জন্য মেমরি থেকে ডাটা নেবে
    public static boolean getCachedAuthState(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_IS_AUTHORIZED, true);
    }

    private static boolean containsId(JSONArray array, String id) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            if (array.getString(i).equals(id)) return true;
        }
        return false;
    }
}
