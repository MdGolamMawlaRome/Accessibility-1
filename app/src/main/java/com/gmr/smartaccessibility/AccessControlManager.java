package com.gmr.smartaccessibility;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AccessControlManager {

    private static final String RULES_URL = "https://raw.githubusercontent.com/MdGolamMawlaRome/Smart-Accessibility/main/access_rules.json";
    
    // এটি এখন যেকোনো Context থেকে কাজ করবে (Service বা Activity)
    public static boolean isDeviceAuthorized(Context context) {
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
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

                if ("BLOCKED".equals(status)) {
                    if (allowed != null && containsId(allowed, deviceId)) return true;
                    return false;
                } else {
                    if (denied != null && containsId(denied, deviceId)) return false;
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true; // যদি ইন্টারনেটে সমস্যা হয়, অ্যাপ চালু রাখবে
    }

    private static boolean containsId(JSONArray array, String id) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            if (array.getString(i).equals(id)) return true;
        }
        return false;
    }
}
