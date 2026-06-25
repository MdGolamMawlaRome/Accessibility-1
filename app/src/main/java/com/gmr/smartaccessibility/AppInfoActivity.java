package com.gmr.smartaccessibility;

import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.util.TypedValue; // এই লাইনটি যোগ করা হয়েছে
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView textView = new TextView(this);
        textView.setPadding(50, 50, 50, 50);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18); 

        // ফিক্সড কালারের বদলে সিস্টেমের থিম কালার ব্যবহার করছি
        TypedArray typedArray = obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int textColor = typedArray.getColor(0, 0xFF000000); // ডিফল্ট কালো ধরে নিচ্ছি যদি থিম না পাওয়া যায়
        typedArray.recycle();
        
        textView.setTextColor(textColor);
        setContentView(textView);

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            long versionCode;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                versionCode = pInfo.getLongVersionCode();
            } else {
                versionCode = pInfo.versionCode;
            }

            long lastUpdateTimeMillis = pInfo.lastUpdateTime;
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault());
            String lastUpdateDate = dateFormat.format(new Date(lastUpdateTimeMillis));

            String infoText = "App Information\n\n" +
                    "Version Name: " + versionName + "\n" +
                    "Version Code: " + versionCode + "\n" +
                    "Last Updated: " + lastUpdateDate;

            textView.setText(infoText);

        } catch (Exception e) {
            e.printStackTrace();
            textView.setText("Failed to load app information.");
        }
    }
}
