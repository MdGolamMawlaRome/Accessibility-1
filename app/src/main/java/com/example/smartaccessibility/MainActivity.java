package com.example.smartaccessibility;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Building a basic layout on-the-fly to guide the user cleanly
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 50, 50, 50);

        TextView titleText = new TextView(this);
        titleText.setText("Smart Accessibility Control Panel");
        titleText.setTextSize(22);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 0, 0, 40);
        layout.addView(titleText);

        Button actionButton = new Button(this);
        actionButton.setText("Open Settings & Configure");
        layout.addView(actionButton);

        setContentView(layout);

        actionButton.setOnClickListener(v -> {
            checkAndNavigate();
        });
    }

    private void checkAndNavigate() {
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
    }
}
