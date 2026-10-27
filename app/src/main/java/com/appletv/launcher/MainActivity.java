package com.appletv.launcher;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements PackageReceiver.PackageListener {

    private ImageView heroBanner;
    private TextView heroTitle;
    private RecyclerView dockRecycler;
    private AppAdapter adapter;

    private final List<AppItem> currentApps = new ArrayList<>();
    private SharedPreferences prefs;

    public static class AppItem {
        public String packageName;
        public String label;
        public Drawable icon;
        public int[] colors;
        public boolean isActionAllApps = false;

        public AppItem(String packageName, String label, Drawable icon, int[] colors) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.colors = colors;
        }

        public AppItem(String label, int[] colors, boolean isActionAllApps) {
            this.label = label;
            this.colors = colors;
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
        heroTitle = findViewById(R.id.hero_title);

        dockRecycler = findViewById(R.id.dock_recycler);
        dockRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        adapter = new AppAdapter();
        dockRecycler.setAdapter(adapter);

        loadHomeApps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PackageReceiver.listener = null;
    }

    @Override
    public void onPackageChanged() {
        runOnUiThread(this::loadHomeApps);
    }

    /**
     * Загрузка приложений на главный экран с учётом закреплённых и скрытых
     */
    private void loadHomeApps() {
        PackageManager pm = getPackageManager();
        Set<String> hiddenApps = prefs.getStringSet("hidden_apps", new HashSet<>());
        List<String> pinnedOrder = loadPinnedOrder();

        // Ищем Leanback (ТВ) и стандартные приложения
        Intent tvIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(tvIntent, 0);
        if (infos.isEmpty()) {
            Intent phoneIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            infos = pm.queryIntentActivities(phoneIntent, 0);
        }

        List<AppItem> detected = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hiddenApps.contains(pkg)) continue;

            String label = ri.loadLabel(pm).toString();
            Drawable icon = ri.loadIcon(pm);
            int[] colors = generateAppColors(pkg);
            detected.add(new AppItem(pkg, label, icon, colors));
        }

        // Авто-добавление новых приложений на главный экран, если они ещё не в списке
        boolean changed = false;
        for (AppItem app : detected) {
            if (!pinnedOrder.contains(app.packageName)) {
                pinnedOrder.add(app.packageName);
                changed = true;
            }
        }
        if (changed) savePinnedOrder(pinnedOrder);

        // Формируем порядок сетки
        currentApps.clear();
        for (String pkg : pinnedOrder) {
            for (AppItem item : detected) {
                if (item.packageName.equals(pkg)) {
                    currentApps.add(item);
                    break;
                }
            }
        }

        // Добавляем плитку App Drawer ("Все приложения")
        currentApps.add(new AppItem("Все приложения", new int[]{0xFF2C2C35, 0xFF181820}, true));

        adapter.notifyDataSetChanged();
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

    /**
     * Контекстное меню кастомизации по долгому нажатию пультом
     */
    private void showAppContextMenu(AppItem item) {
        if (item.isActionAllApps) return;

        CharSequence[] options = new CharSequence[]{
            "Убрать с главного экрана",
            "Скрыть приложение",
            "Удалить из системы (Деинсталляция)"
        };

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(item.label)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Убрать ярлык с главного экрана (остается в Drawer)
                    List<String> order = loadPinnedOrder();
                    order.remove(item.packageName);
                    savePinnedOrder(order);
                    loadHomeApps();
                    Toast.makeText(this, "Ярлык удален с главного экрана", Toast.LENGTH_SHORT).show();
                } else if (which == 1) {
                    // Скрыть приложение
                    Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_apps", new HashSet<>()));
                    hidden.add(item.packageName);
                    prefs.edit().putStringSet("hidden_apps", hidden).apply();
                    loadHomeApps();
                    Toast.makeText(this, "Приложение скрыто", Toast.LENGTH_SHORT).show();
                } else if (which == 2) {
                    // Системная деинсталляция
                    Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                    intent.setData(Uri.parse("package:" + item.packageName));
                    startActivity(intent);
                }
            })
            .show();
    }

    /**
     * Каталог всех приложений (App Drawer)
     */
    private void showAllAppsDrawer() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        if (infos.isEmpty()) {
            intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            infos = pm.queryIntentActivities(intent, 0);
        }

        List<String> labels = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            labels.add(ri.loadLabel(pm).toString());
            pkgs.add(pkg);
        }

        CharSequence[] names = labels.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Каталог всех приложений (App Drawer)")
            .setItems(names, (dialog, which) -> {
                String selectedPkg = pkgs.get(which);
                Intent launch = pm.getLeanbackLaunchIntentForPackage(selectedPkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(selectedPkg);
                if (launch != null) startActivity(launch);
            })
            .setPositiveButton("Закрыть", null)
            .show();
    }

    private int[] generateAppColors(String pkg) {
        if (pkg.contains("kinopoisk")) return new int[]{0xFFFF5500, 0xFF8A1E00};
        if (pkg.contains("youtube") || pkg.contains("ytplayer")) return new int[]{0xFFFF2A2A, 0xFF990000};
        if (pkg.contains("zona")) return new int[]{0xFF00C853, 0xFF00695C};
        if (pkg.contains("vk")) return new int[]{0xFF0077FF, 0xFF003C8F};
        return new int[]{0xFF242430, 0xFF121218};
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
            card.setFocusableInTouchMode(true);
            card.setClickable(true);
            card.setClipToOutline(true);

            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(60, 60, Gravity.CENTER);
            card.addView(iv, ivLp);

            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(14);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            );
            card.addView(tv, tvLp);

            return new RecyclerView.ViewHolder(card) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AppItem item = currentApps.get(position);
            FrameLayout card = (FrameLayout) holder.itemView;
            ImageView iv = (ImageView) card.getChildAt(0);
            TextView tv = (TextView) card.getChildAt(1);

            if (item.isActionAllApps) {
                iv.setVisibility(View.GONE);
                tv.setVisibility(View.VISIBLE);
                tv.setText("⋮⋮\nВсе");
            } else {
                tv.setVisibility(View.GONE);
                iv.setVisibility(View.VISIBLE);
                if (item.icon != null) iv.setImageDrawable(item.icon);
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
                } else {
                    animateScale(v, 1.0f, 1.0f, 0f);
                    v.setBackground(normalBg);
                }
            });

            card.setOnClickListener(v -> {
                if (item.isActionAllApps) {
                    showAllAppsDrawer();
                } else {
                    PackageManager pm = getPackageManager();
                    Intent launch = pm.getLeanbackLaunchIntentForPackage(item.packageName);
                    if (launch == null) launch = pm.getLaunchIntentForPackage(item.packageName);
                    if (launch != null) startActivity(launch);
                }
            });

            // Долгое нажатие пульта (Long Click)
            card.setOnLongClickListener(v -> {
                showAppContextMenu(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return currentApps.size();
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
