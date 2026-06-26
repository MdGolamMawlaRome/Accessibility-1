package com.gmr.smartaccessibility;

import android.content.pm.PackageInfo;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TypedArray typedArray = obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int textColor = typedArray.getColor(0, 0xFF000000); 
        typedArray.recycle();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(50, 50, 50, 50);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 80);

        ImageView backBtn = new ImageView(this);
        backBtn.setImageResource(R.drawable.ic_back_curved);
        backBtn.setPadding(10, 10, 30, 10);
        backBtn.setOnClickListener(v -> finish()); 

        TextView title = new TextView(this);
        title.setText("App Info");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTextColor(textColor);

        header.addView(backBtn);
        header.addView(title);
        root.addView(header);

        // Content
        TextView content = new TextView(this);
        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18); 
        content.setTextColor(textColor);
        
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            long versionCode = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) ? pInfo.getLongVersionCode() : pInfo.versionCode;
            String lastUpdateDate = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(new Date(pInfo.lastUpdateTime));

            content.setText("Version Name: " + versionName + "\nVersion Code: " + versionCode + "\nLast Updated: " + lastUpdateDate);
        } catch (Exception e) {
            content.setText("Failed to load app information.");
        }
        
        root.addView(content);
        setContentView(root);
    }
}
