package com.gmr.smartaccessibility;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

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
        title.setText("Settings");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTextColor(textColor);

        header.addView(backBtn);
        header.addView(title);
        root.addView(header);

        // Content
        TextView content = new TextView(this);
        content.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18); 
        content.setTextColor(textColor);
        content.setText("No feature/settings are available currently.");
        root.addView(content);
        
        setContentView(root);
    }
}
