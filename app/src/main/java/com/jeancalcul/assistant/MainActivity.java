package com.jeancalcul.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(238, 244, 247), Color.rgb(198, 235, 229), Color.rgb(185, 216, 246)});
        root.setBackground(background);

        TextView title = new TextView(this);
        title.setText("Jean Calcul Assistant");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(20, 35, 45));
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("Client Android prêt à connecter Hermes via HTTP local.");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.rgb(55, 75, 85));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 24, 0, 0);

        root.addView(title);
        root.addView(subtitle);
        setContentView(root);
    }
}
