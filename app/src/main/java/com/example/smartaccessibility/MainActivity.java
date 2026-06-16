package com.example.smartaccessibility;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnWriteSettings = findViewById(R.id.btnWriteSettings);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);

        btnWriteSettings.setOnClickListener(v -> {
            if (!Settings.System.canWrite(MainActivity.this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Write Settings Permission already granted, Bhai!", Toast.LENGTH_SHORT).show();
            }
        });

        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(MainActivity.this, "Find 'Smart Accessibility' and turn it ON", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Friendly dynamic status update check on window focus return
        if (Settings.System.canWrite(this)) {
            Button btnWriteSettings = findViewById(R.id.btnWriteSettings);
            if (btnWriteSettings != null) {
                btnWriteSettings.setText("Brightness Permission: ACTIVE");
                btnWriteSettings.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2E7D32")));
            }
        }
    }
}
