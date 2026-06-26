package com.gmr.smartaccessibility;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class CheckUpdateActivity extends AppCompatActivity implements UpdateCallback {

    private TextView statusTextView;
    private Button updateButton;
    private UpdateManager updateManager;
    private Handler handler;
    private Runnable dotRunnable;
    private int dotCount = 0;
    private String pendingDownloadUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updateManager = new UpdateManager(this);
        handler = new Handler(Looper.getMainLooper());

        TypedArray typedArray = obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int textColor = typedArray.getColor(0, 0xFF000000);
        typedArray.recycle();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(50, 50, 50, 50);

        // Header with Back Arrow
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 80);

        ImageView backBtn = new ImageView(this);
        backBtn.setImageResource(R.drawable.ic_back_curved);
        backBtn.setPadding(10, 10, 30, 10);
        backBtn.setOnClickListener(v -> finish()); // Go back to Main Screen

        TextView title = new TextView(this);
        title.setText("Updates");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTextColor(textColor);

        header.addView(backBtn);
        header.addView(title);
        root.addView(header);

        // Status Text (Animation goes here)
        statusTextView = new TextView(this);
        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        statusTextView.setTextColor(textColor);
        statusTextView.setPadding(0, 0, 0, 50);
        root.addView(statusTextView);

        // Update Button (Hidden by default)
        updateButton = new Button(this);
        updateButton.setText("Update Now");
        updateButton.setVisibility(View.GONE);
        updateButton.setOnClickListener(v -> {
            if (!pendingDownloadUrl.isEmpty()) {
                updateManager.processUpdate(pendingDownloadUrl);
            }
        });
        root.addView(updateButton);

        setContentView(root);

        // Start checking and animation
        startDotAnimation();
        updateManager.checkForUpdatesWithCallback(this);
    }

    private void startDotAnimation() {
        dotRunnable = new Runnable() {
            @Override
            public void run() {
                dotCount = (dotCount + 1) % 4;
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) dots.append(".");
                statusTextView.setText("Checking for updates" + dots.toString());
                handler.postDelayed(this, 500);
            }
        };
        handler.post(dotRunnable);
    }

    private void stopDotAnimation() {
        if (dotRunnable != null) {
            handler.removeCallbacks(dotRunnable);
        }
    }

    @Override
    public void onUpdateAvailable(String latestVersion, String downloadUrl) {
        stopDotAnimation();
        this.pendingDownloadUrl = downloadUrl;
        statusTextView.setText("Update is available, do you want to update?\n\nThe new version is: " + latestVersion);
        updateButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onUpToDate() {
        stopDotAnimation();
        statusTextView.setText("Your app is already up to date.");
    }

    @Override
    public void onError(String message) {
        stopDotAnimation();
        statusTextView.setText("Failed to check for updates. " + message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopDotAnimation();
    }
}
