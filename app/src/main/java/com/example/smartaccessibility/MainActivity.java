package com.example.smartaccessibility;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Settings.System.canWrite(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Brightness কন্ট্রোল করার জন্য পারমিশন দিন", Toast.LENGTH_LONG).show();
        } else {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Smart Accessibility সার্ভিসটি চালু করুন", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
