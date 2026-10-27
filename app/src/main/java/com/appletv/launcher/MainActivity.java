package com.appletv.launcher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView focusedTitle;

    static class AppItem {
        String name;
        String packageName;
        Drawable icon;

        AppItem(String name, String packageName, Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        focusedTitle = findViewById(R.id.focused_title);
        RecyclerView dock = findViewById(R.id.dock_recycler);
        dock.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        PackageManager pm = getPackageManager();
        List<AppItem> apps = new ArrayList<>();

        String[][] targets = {
            {"Кинопоиск", "ru.kinopoisk.yandex.tv"},
            {"YouTube", "com.yandex.tv.ytplayer"},
            {"Zona", "mobi.zona"},
            {"VK Видео", "com.vk.tv"}
        };

        for (String[] t : targets) {
            Drawable icon = null;
            try {
                icon = pm.getApplicationIcon(t[1]);
            } catch (Exception ignored) {}
            apps.add(new AppItem(t[0], t[1], icon));
        }

        dock.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                FrameLayout card = new FrameLayout(parent.getContext());
                card.setLayoutParams(new ViewGroup.MarginLayoutParams(130, 76));
                ((ViewGroup.MarginLayoutParams) card.getLayoutParams()).setMargins(8, 0, 8, 0);
                card.setFocusable(true);
                card.setClickable(true);
                card.setBackgroundResource(R.drawable.card_selector);

                ImageView iv = new ImageView(parent.getContext());
                iv.setId(View.generateViewId());
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(48, 48, Gravity.CENTER);
                card.addView(iv, lp);

                return new RecyclerView.ViewHolder(card) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                AppItem item = apps.get(position);
                FrameLayout card = (FrameLayout) holder.itemView;
                ImageView iv = (ImageView) card.getChildAt(0);

                if (item.icon != null) {
                    iv.setImageDrawable(item.icon);
                }

                card.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.setScaleX(1.15f);
                        v.setScaleY(1.15f);
                        v.setElevation(12f);
                        focusedTitle.setText(item.name);
                    } else {
                        v.setScaleX(1.0f);
                        v.setScaleY(1.0f);
                        v.setElevation(0f);
                    }
                });

                card.setOnClickListener(v -> {
                    Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(item.packageName);
                    if (launchIntent == null) launchIntent = pm.getLaunchIntentForPackage(item.packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(MainActivity.this, item.name + " не установлено", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public int getItemCount() {
                return apps.size();
            }
        });
    }
}
