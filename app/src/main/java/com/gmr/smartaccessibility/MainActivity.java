package com.gmr.smartaccessibility;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private TextView dynamicHomepageText;
    private UpdateManager updateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dynamicHomepageText = findViewById(R.id.dynamicHomepageText);
        loadTextFromAssets();

        updateManager = new UpdateManager(this);
        updateManager.checkForUpdates();

        // Hamburger Menu Initialization
        View btnHomeMenu = findViewById(R.id.btnHomeMenu);
        HomeMenuController homeMenuController = new HomeMenuController(this);

        if (btnHomeMenu != null) {
            btnHomeMenu.setOnClickListener(v -> {
                homeMenuController.showMenu(btnHomeMenu);
            });
        }

        handleUpdateIntent(getIntent());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkRequiredPermissions();
        
        if (updateManager != null) {
            updateManager.resumeUpdateFlow();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleUpdateIntent(intent);
    }

    private void handleUpdateIntent(Intent intent) {
        if (intent != null && intent.hasExtra("UPDATE_URL")) {
            String url = intent.getStringExtra("UPDATE_URL");
            if (updateManager != null) {
                updateManager.processUpdate(url);
            }
            intent.removeExtra("UPDATE_URL");
        }
    }

    private void loadTextFromAssets() {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream is = getAssets().open("homepage_info.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
             
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            dynamicHomepageText.setText(stringBuilder.toString());
            
        } catch (IOException e) {
            dynamicHomepageText.setText("Welcome to Smart Accessibility.\n\n(Note: homepage_info.txt file not found in assets folder. Please create it to edit this text.)");
        }
    }

    private void checkRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                showWriteSettingsDialog();
                return; 
            }
        }

        if (!isAccessibilityServiceEnabled(SmartAccessibilityService.class)) {
            showAccessibilityDialog();
        }
    }

    private void showWriteSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Smart Accessibility needs permission to modify system settings to control your brightness. Please allow this in the next screen.")
                .setCancelable(false)
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .show();
    }

    private void showAccessibilityDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Accessibility Required")
                .setMessage("To control volume, take screenshots, and show the overlay, please enable 'Smart Accessibility' in the Accessibility settings.")
                .setCancelable(false)
                .setPositiveButton("Open Accessibility", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .show();
    }

    private boolean isAccessibilityServiceEnabled(Class<?> serviceClass) {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + serviceClass.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
        }

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                colonSplitter.setString(settingValue);
                while (colonSplitter.hasNext()) {
                    String accessibilityService = colonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
