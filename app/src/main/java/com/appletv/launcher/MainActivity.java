package com.appletv.launcher;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
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

    private ImageView heroBanner;
    private TextView heroTitle;

    static class TvApp {
        String title;
        String heroName;
        String packageName;
        int bannerRes;
        int[] gradientColors;
        String iconText;

        TvApp(String title, String heroName, String packageName, int bannerRes, int[] gradientColors, String iconText) {
            this.title = title;
            this.heroName = heroName;
            this.packageName = packageName;
            this.bannerRes = bannerRes;
            this.gradientColors = gradientColors;
            this.iconText = iconText;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        heroBanner = findViewById(R.id.hero_banner);
        heroTitle = findViewById(R.id.hero_title);

        RecyclerView dock = findViewById(R.id.dock_recycler);
        dock.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        PackageManager pm = getPackageManager();

        List<TvApp> apps = new ArrayList<>();
        apps.add(new TvApp("Apple TV", "The Studio", "ru.kinopoisk.yandex.tv", R.drawable.bg_poster_studio, new int[]{0xFF1C1C22, 0xFF0D0D11}, " tv"));
        apps.add(new TvApp("Кинопоиск", "Мажор в Сочи", "ru.kinopoisk.yandex.tv", R.drawable.bg_poster_kinopoisk, new int[]{0xFFFF5500, 0xFF8A1E00}, "КП"));
        apps.add(new TvApp("YouTube", "Trending Videos", "com.yandex.tv.ytplayer", R.drawable.bg_poster_youtube, new int[]{0xFFFF2A2A, 0xFF990000}, "▶"));
        apps.add(new TvApp("Zona", "Премьеры фильмов", "mobi.zona", R.drawable.bg_poster_zona, new int[]{0xFF00C853, 0xFF00695C}, "ZONA"));
        apps.add(new TvApp("VK Видео", "Шоу и блоги", "com.vk.tv", R.drawable.bg_poster_vk, new int[]{0xFF0077FF, 0xFF003C8F}, "VK"));
        apps.add(new TvApp("Настройки", "Параметры ТВ", "com.android.tv.settings", R.drawable.bg_poster_studio, new int[]{0xFF607D8B, 0xFF263238}, "⚙"));

        dock.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                FrameLayout card = new FrameLayout(parent.getContext());
                ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(160, 100);
                lp.setMargins(14, 18, 14, 18);
                card.setLayoutParams(lp);
                card.setFocusable(true);
                card.setFocusableInTouchMode(true);
                card.setClickable(true);
                card.setClipToOutline(true);

                TextView label = new TextView(parent.getContext());
                label.setTextColor(Color.WHITE);
                label.setTextSize(18);
                label.setTypeface(null, android.graphics.Typeface.BOLD);
                label.setGravity(Gravity.CENTER);

                FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                card.addView(label, textLp);

                return new RecyclerView.ViewHolder(card) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                TvApp item = apps.get(position);
                FrameLayout card = (FrameLayout) holder.itemView;
                TextView label = (TextView) card.getChildAt(0);

                label.setText(item.iconText);

                GradientDrawable normalBg = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM, item.gradientColors
                );
                normalBg.setCornerRadius(20f);
                card.setBackground(normalBg);

                card.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        animateScale(v, 1.20f, 1.20f, 16f);

                        GradientDrawable focusedBg = new GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM, item.gradientColors
                        );
                        focusedBg.setCornerRadius(20f);
                        focusedBg.setStroke(4, Color.WHITE);
                        v.setBackground(focusedBg);

                        heroTitle.setText(item.heroName);
                        heroBanner.setImageResource(item.bannerRes);
                        heroBanner.animate().alpha(1.0f).setDuration(250).start();
                    } else {
                        animateScale(v, 1.0f, 1.0f, 0f);
                        v.setBackground(normalBg);
                    }
                });

                card.setOnClickListener(v -> {
                    Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(item.packageName);
                    if (launchIntent == null) launchIntent = pm.getLaunchIntentForPackage(item.packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(MainActivity.this, item.title, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public int getItemCount() {
                return apps.size();
            }
        });
    }

    private void animateScale(View view, float sx, float sy, float elev) {
        ObjectAnimator ax = ObjectAnimator.ofFloat(view, "scaleX", sx);
        ObjectAnimator ay = ObjectAnimator.ofFloat(view, "scaleY", sy);
        ObjectAnimator az = ObjectAnimator.ofFloat(view, "translationZ", elev);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(ax, ay, az);
        set.setDuration(220);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }
}
