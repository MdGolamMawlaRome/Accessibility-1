package com.example.smartaccessibility;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

public class ScreenRecordPermissionActivity extends Activity {
    private static final int REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        } else {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Intent serviceIntent = new Intent(this, SmartAccessibilityService.class);
            serviceIntent.setAction("ACTION_START_RECORDING");
            serviceIntent.putExtra("RESULT_CODE", resultCode);
            serviceIntent.putExtra("DATA", data);
            startService(serviceIntent);
        }
        finish();
    }
}
