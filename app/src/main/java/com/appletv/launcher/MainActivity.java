package com.appletv.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements PackageReceiver.PackageListener {

    private ImageView heroBanner;
    private View ambientGlow;
    private TextView heroTitle;
    private RecyclerView dockRecycler;
    private FrameLayout appDrawerOverlay;
    private RecyclerView drawerRecycler;
    private FrameLayout btnSettings;

    private AppAdapter dockAdapter;
    private DrawerAdapter drawerAdapter;

    private final List<AppItem> homeApps = new ArrayList<>();
    private final List<AppItem> allInstalledApps = new ArrayList<>();
    private SharedPreferences prefs;

    private int currentAmbientColor = 0xFF14101A;

    public static class AppItem {
        public String packageName;
        public String label;
        public Drawable icon;
        public int[] colors;
        public int glowColor;
        public int bannerRes;
        public boolean isActionAllApps = false;

        public AppItem(String packageName, String label, Drawable icon, int[] colors, int glowColor, int bannerRes) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.colors = colors;
            this.glowColor = glowColor;
            this.bannerRes = bannerRes;
        }

        public AppItem(String label, int[] colors, boolean isActionAllApps) {
            this.label = label;
            this.colors = colors;
            this.glowColor = 0xFF353545;
            this.bannerRes = R.drawable.bg_poster_studio;
            this.isActionAllApps = isActionAllApps;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("tvos_launcher_prefs", Context.MODE_PRIVATE);
        PackageReceiver.listener = this;

        heroBanner = findViewById(R.id.hero_banner);
        ambientGlow = findViewById(R.id.ambient_glow);
        heroTitle = findViewById(R.id.hero_title);
        dockRecycler = findViewById(R.id.dock_recycler);
        appDrawerOverlay = findViewById(R.id.app_drawer_overlay);
        drawerRecycler = findViewById(R.id.drawer_recycler);
        btnSettings = findViewById(R.id.btn_launcher_settings);

        dockRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dockAdapter = new AppAdapter();
        dockRecycler.setAdapter(dockAdapter);

        drawerRecycler.setLayoutManager(new GridLayoutManager(this, 5));
        drawerAdapter = new DrawerAdapter();
        drawerRecycler.setAdapter(drawerAdapter);

        btnSettings.setOnClickListener(v -> showLauncherSettingsDialog());
        btnSettings.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                animateScale(v, 1.15f, 1.15f, 12f);
                heroTitle.setText("Настройки лаунчера");
            } else {
                animateScale(v, 1.0f, 1.0f, 0f);
            }
        });

        loadApps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PackageReceiver.listener = null;
    }

    @Override
    public void onPackageChanged() {
        runOnUiThread(this::loadApps);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && appDrawerOverlay.getVisibility() == View.VISIBLE) {
            closeDrawer();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void openDrawer() {
        appDrawerOverlay.setVisibility(View.VISIBLE);
        appDrawerOverlay.setAlpha(0f);
        appDrawerOverlay.animate().alpha(1f).setDuration(220).start();
        drawerRecycler.requestFocus();
    }

    private void closeDrawer() {
        appDrawerOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            appDrawerOverlay.setVisibility(View.GONE);
            dockRecycler.requestFocus();
        }).start();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> hiddenApps = prefs.getStringSet("hidden_apps", new HashSet<>());
        List<String> pinnedOrder = loadPinnedOrder();

        Intent tvIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(tvIntent, 0);
        if (infos.isEmpty()) {
            Intent phoneIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            infos = pm.queryIntentActivities(phoneIntent, 0);
        }

        allInstalledApps.clear();
        List<AppItem> visibleApps = new ArrayList<>();

        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;

            String label = ri.loadLabel(pm).toString();
            Drawable icon = ri.loadIcon(pm);
            int[] colors = getThemeColorsForPackage(pkg);
            int glowColor = getGlowColorForPackage(pkg);
            int banner = getPosterForPackage(pkg);

            AppItem item = new AppItem(pkg, label, icon, colors, glowColor, banner);
            allInstalledApps.add(item);

            if (!hiddenApps.contains(pkg)) {
                visibleApps.add(item);
            }
        }

        boolean orderChanged = false;
        for (AppItem app : visibleApps) {
            if (!pinnedOrder.contains(app.packageName)) {
                pinnedOrder.add(app.packageName);
                orderChanged = true;
            }
        }
        if (orderChanged) savePinnedOrder(pinnedOrder);

        homeApps.clear();
        for (String pkg : pinnedOrder) {
            for (AppItem item : visibleApps) {
                if (item.packageName.equals(pkg)) {
                    homeApps.add(item);
                    break;
                }
            }
        }

        homeApps.add(new AppItem("Все приложения", new int[]{0xFF2A2A38, 0xFF14141E}, true));

        dockAdapter.notifyDataSetChanged();
        drawerAdapter.notifyDataSetChanged();
    }

    private void animateAmbientGlow(int targetColor) {
        ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), currentAmbientColor, targetColor);
        colorAnim.setDuration(300);
        colorAnim.addUpdateListener(animator -> {
            int c = (int) animator.getAnimatedValue();
            currentAmbientColor = c;
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{c, 0x00050508}
            );
            ambientGlow.setBackground(gd);
        });
        colorAnim.start();
    }

    private void showAppContextMenu(AppItem item, boolean fromDrawer) {
        if (item.isActionAllApps) return;

        List<String> options = new ArrayList<>();
        if (!fromDrawer) {
            options.add("Убрать с главного экрана");
        } else {
            options.add("Закрепить на главный экран");
        }
        options.add("Скрыть приложение");
        options.add("Удалить с ТВ");

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(item.label)
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
                    String choice = options.get(which);
                    if (choice.equals("Убрать с главного экрана")) {
                        List<String> order = loadPinnedOrder();
                        order.remove(item.packageName);
                        savePinnedOrder(order);
                        loadApps();
                        Toast.makeText(this, "Ярлык убран", Toast.LENGTH_SHORT).show();
                    } else if (choice.equals("Закрепить на главный экран")) {
                        List<String> order = loadPinnedOrder();
                        if (!order.contains(item.packageName)) {
                            order.add(item.packageName);
                            savePinnedOrder(order);
                            loadApps();
                            Toast.makeText(this, "Закреплено на главном", Toast.LENGTH_SHORT).show();
                        }
                    } else if (choice.equals("Скрыть приложение")) {
                        Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_apps", new HashSet<>()));
                        hidden.add(item.packageName);
                        prefs.edit().putStringSet("hidden_apps", hidden).apply();
                        loadApps();
                        Toast.makeText(this, "Приложение скрыто", Toast.LENGTH_SHORT).show();
                    } else if (choice.equals("Удалить с ТВ")) {
                        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                        intent.setData(Uri.parse("package:" + item.packageName));
                        startActivity(intent);
                    }
                })
                .show();
    }

    private void showLauncherSettingsDialog() {
        CharSequence[] items = new CharSequence[]{
            "Восстановить скрытые приложения",
            "Сбросить порядок ярлыков",
            "Параметры Android ТВ"
        };

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Настройки Apple TV Лаунчера")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        prefs.edit().remove("hidden_apps").apply();
                        loadApps();
                        Toast.makeText(this, "Скрытые приложения возвращены", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        prefs.edit().remove("pinned_order").apply();
                        loadApps();
                        Toast.makeText(this, "Порядок сброшен к начальному", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                        } catch (Exception ignored) {}
                    }
                })
                .show();
    }

    private List<String> loadPinnedOrder() {
        List<String> list = new ArrayList<>();
        try {
            String json = prefs.getString("pinned_order", null);
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void savePinnedOrder(List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        prefs.edit().putString("pinned_order", arr.toString()).apply();
    }

    private int[] getThemeColorsForPackage(String pkg) {
        if (pkg.contains("kinopoisk")) return new int[]{0xFFFF5500, 0xFF8A1E00};
        if (pkg.contains("youtube") || pkg.contains("ytplayer")) return new int[]{0xFFFF2A2A, 0xFF990000};
        if (pkg.contains("zona")) return new int[]{0xFF00C853, 0xFF00695C};
        if (pkg.contains("vk")) return new int[]{0xFF0077FF, 0xFF003C8F};
        return new int[]{0xFF2E2E3A, 0xFF14141E};
    }

    private int getGlowColorForPackage(String pkg) {
        if (pkg.contains("kinopoisk")) return 0x99FF5500;
        if (pkg.contains("youtube") || pkg.contains("ytplayer")) return 0x99FF2020;
        if (pkg.contains("zona")) return 0x9900C853;
        if (pkg.contains("vk")) return 0x990077FF;
        return 0x663A3A4C;
    }

    private int getPosterForPackage(String pkg) {
        if (pkg.contains("kinopoisk")) return R.drawable.bg_poster_kinopoisk;
        if (pkg.contains("youtube") || pkg.contains("ytplayer")) return R.drawable.bg_poster_youtube;
        if (pkg.contains("zona")) return R.drawable.bg_poster_zona;
        if (pkg.contains("vk")) return R.drawable.bg_poster_vk;
        return R.drawable.bg_poster_studio;
    }

    private class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(160, 100);
            lp.setMargins(14, 18, 14, 18);
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);
            card.setClipToOutline(true);

            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(iv, new FrameLayout.LayoutParams(56, 56, Gravity.CENTER));

            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(14);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            card.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            return new RecyclerView.ViewHolder(card) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AppItem item = homeApps.get(position);
            FrameLayout card = (FrameLayout) holder.itemView;
            ImageView iv = (ImageView) card.getChildAt(0);
            TextView tv = (TextView) card.getChildAt(1);

            if (item.isActionAllApps) {
                iv.setVisibility(View.GONE);
                tv.setVisibility(View.VISIBLE);
                tv.setText("⊞\nВсе");
            } else {
                tv.setVisibility(View.GONE);
                iv.setVisibility(View.VISIBLE);
                iv.setImageDrawable(item.icon);
            }

            GradientDrawable normalBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
            normalBg.setCornerRadius(20f);
            card.setBackground(normalBg);

            card.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    animateScale(v, 1.20f, 1.20f, 16f);
                    GradientDrawable focusBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
                    focusBg.setCornerRadius(20f);
                    focusBg.setStroke(4, Color.WHITE);
                    v.setBackground(focusBg);

                    heroTitle.setText(item.label);
                    heroBanner.setImageResource(item.bannerRes);
                    animateAmbientGlow(item.glowColor);
                } else {
                    animateScale(v, 1.0f, 1.0f, 0f);
                    v.setBackground(normalBg);
                }
            });

            card.setOnClickListener(v -> {
                if (item.isActionAllApps) {
                    openDrawer();
                } else {
                    PackageManager pm = getPackageManager();
                    Intent launch = pm.getLeanbackLaunchIntentForPackage(item.packageName);
                    if (launch == null) launch = pm.getLaunchIntentForPackage(item.packageName);
                    if (launch != null) startActivity(launch);
                }
            });

            card.setOnLongClickListener(v -> {
                showAppContextMenu(item, false);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return homeApps.size();
        }
    }

    private class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(180, 110);
            lp.setMargins(14, 14, 14, 14);
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);
            card.setClipToOutline(true);

            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(iv, new FrameLayout.LayoutParams(60, 60, Gravity.CENTER));

            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(12);
            tv.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            tv.setPadding(0, 0, 0, 8);
            card.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            return new RecyclerView.ViewHolder(card) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AppItem item = allInstalledApps.get(position);
            FrameLayout card = (FrameLayout) holder.itemView;
            ImageView iv = (ImageView) card.getChildAt(0);
            TextView tv = (TextView) card.getChildAt(1);

            iv.setImageDrawable(item.icon);
            tv.setText(item.label);

            GradientDrawable normalBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
            normalBg.setCornerRadius(20f);
            card.setBackground(normalBg);

            card.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    animateScale(v, 1.15f, 1.15f, 14f);
                    GradientDrawable focusBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
                    focusBg.setCornerRadius(20f);
                    focusBg.setStroke(3, Color.WHITE);
                    v.setBackground(focusBg);
                } else {
                    animateScale(v, 1.0f, 1.0f, 0f);
                    v.setBackground(normalBg);
                }
            });

            card.setOnClickListener(v -> {
                PackageManager pm = getPackageManager();
                Intent launch = pm.getLeanbackLaunchIntentForPackage(item.packageName);
                if (launch == null) launch = pm.getLaunchIntentForPackage(item.packageName);
                if (launch != null) startActivity(launch);
            });

            card.setOnLongClickListener(v -> {
                showAppContextMenu(item, true);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return allInstalledApps.size();
        }
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
