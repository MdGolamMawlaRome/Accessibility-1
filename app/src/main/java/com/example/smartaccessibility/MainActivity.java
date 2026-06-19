package com.gmr.smartaccessibility;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // অ্যাপ চালু হওয়ার সাথে সাথে পারমিশন চেক করা হচ্ছে
        checkRequiredPermissions();
    }

    private void checkRequiredPermissions() {
        // ১. Write Settings Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }

        // ২. Accessibility Service (এটি সরাসরি ওপেন করা যায়)
        // ইউজারকে মেনু থেকে অন করতে হয়, তাই এটি সরাসরি সেটিংস পেজে নিয়ে যাচ্ছে
        if (!isAccessibilityServiceEnabled()) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        // এখানে সার্ভিস অন আছে কি না তার লজিক চেক করা যেতে পারে
        return false; 
    }
}
