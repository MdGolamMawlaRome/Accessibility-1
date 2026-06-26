package com.gmr.smartaccessibility;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textView = new TextView(this);
        textView.setPadding(50, 50, 50, 50);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18); 

        TypedArray typedArray = obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int textColor = typedArray.getColor(0, 0xFF000000); 
        typedArray.recycle();
        
        textView.setTextColor(textColor);
        textView.setText("No feature/settings are available currently.");
        
        setContentView(textView);
    }
}
