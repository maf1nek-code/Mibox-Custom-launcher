package com.lumen.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class DemoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText(getString(R.string.demo_label));
        text.setTextSize(48);
        text.setTextColor(0xFFFFFFFF);
        text.setGravity(Gravity.CENTER);
        text.setBackgroundColor(getColor(R.color.demo_color));
        setContentView(text);
    }
}
