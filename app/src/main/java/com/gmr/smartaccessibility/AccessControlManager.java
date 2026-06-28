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

    public static boolean isDeviceAuthorized(Context context) {
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
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
                JSONArray allowed = json.optJSONArray("allowed");
                JSONArray denied = json.optJSONArray("denied");

                boolean isAuthorized = false;

                // চেক করা হচ্ছে লিস্টগুলোতে কী কী আছে
                boolean exactDeny = denied != null && containsId(denied, deviceId);
                boolean exactAllow = allowed != null && containsId(allowed, deviceId);
                boolean denyAll = denied != null && containsId(denied, "ALL");
                boolean allowAll = allowed != null && containsId(allowed, "ALL");

                // লজিক প্রায়োরিটি (আপনার চাহিদামতো)
                if (exactDeny) {
                    isAuthorized = false; // ১. নির্দিষ্ট আইডি ডিনাইড লিস্টে থাকলে ব্লক
                } else if (exactAllow) {
                    isAuthorized = true;  // ২. নির্দিষ্ট আইডি অ্যালাউড লিস্টে থাকলে অ্যালাউ
                } else if (denyAll) {
                    isAuthorized = false; // ৩. ডিনাইড লিস্টে "ALL" থাকলে, বাকি সবাই ব্লক
                } else if (allowAll) {
                    isAuthorized = true;  // ৪. অ্যালাউড লিস্টে "ALL" থাকলে, বাকি সবাই অ্যালাউ
                } else {
                    // ৫. যদি JSON এ ভুল থাকে বা ফাঁকা থাকে, তখন ডিফল্ট হিসেবে স্ট্যাটাস ফিল্ড চেক করবে
                    String status = json.optString("status", "ACTIVE");
                    isAuthorized = !"BLOCKED".equalsIgnoreCase(status);
                }

                // নতুন স্ট্যাটাসটি মেমরিতে সেভ করে রাখা হচ্ছে
                prefs.edit().putBoolean(KEY_IS_AUTHORIZED, isAuthorized).apply();
                return isAuthorized;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // ইন্টারনেটে সমস্যা থাকলে ক্যাশ ডাটা রিটার্ন করবে
        return lastKnownState;
    }

    public static boolean getCachedAuthState(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_IS_AUTHORIZED, true);
    }

    private static boolean containsId(JSONArray array, String id) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            // equalsIgnoreCase ব্যবহার করা হয়েছে যেন "ALL", "All", "all" যেকোনো ফরম্যাটে কাজ করে
            if (array.getString(i).equalsIgnoreCase(id)) return true;
        }
        return false;
    }
}
