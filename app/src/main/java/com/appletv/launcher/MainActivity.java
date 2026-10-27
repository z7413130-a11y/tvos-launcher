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
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements PackageReceiver.PackageListener {

    private ImageView heroBanner;
    private View ambientGlow;
    private TextView heroTitle;
    private RecyclerView dockRecycler;
    private FrameLayout btnSettings;
    private TextClock mainTextClock;
    private TextView tvDateDisplay;

    private FrameLayout appDrawerOverlay;
    private RecyclerView drawerRecycler;

    private FrameLayout settingsOverlay;
    private FrameLayout tilesManagerOverlay;
    private RecyclerView tilesManagerRecycler;

    private FrameLayout screensaverOverlay;
    private ImageView screensaverBg;
    private TextClock saverTextClock;
    private TextView saverDate;
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private Runnable idleRunnable;

    private TextView settingsPreviewIcon;
    private TextView settingsPreviewTitle;
    private TextView settingsPreviewDesc;

    private TextView tvWallpapersVal;
    private TextView tvAmbientVal;
    private TextView tvSizeVal;
    private TextView tvClockVal;
    private TextView tvSaverVal;
    private TextView tvAutostartVal;

    private AppAdapter dockAdapter;
    private DrawerAdapter drawerAdapter;
    private TilesManagerAdapter tilesAdapter;

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
        btnSettings = findViewById(R.id.btn_launcher_settings);
        mainTextClock = findViewById(R.id.main_text_clock);
        tvDateDisplay = findViewById(R.id.tv_date_display);

        appDrawerOverlay = findViewById(R.id.app_drawer_overlay);
        drawerRecycler = findViewById(R.id.drawer_recycler);

        settingsOverlay = findViewById(R.id.settings_overlay);
        tilesManagerOverlay = findViewById(R.id.tiles_manager_overlay);
        tilesManagerRecycler = findViewById(R.id.tiles_manager_recycler);

        screensaverOverlay = findViewById(R.id.screensaver_overlay);
        screensaverBg = findViewById(R.id.screensaver_bg);
        saverTextClock = findViewById(R.id.saver_text_clock);
        saverDate = findViewById(R.id.saver_date);

        settingsPreviewIcon = findViewById(R.id.settings_preview_icon);
        settingsPreviewTitle = findViewById(R.id.settings_preview_title);
        settingsPreviewDesc = findViewById(R.id.settings_preview_desc);

        tvWallpapersVal = findViewById(R.id.tv_setting_wallpapers_val);
        tvAmbientVal = findViewById(R.id.tv_setting_ambient_val);
        tvSizeVal = findViewById(R.id.tv_setting_size_val);
        tvClockVal = findViewById(R.id.tv_setting_clock_val);
        tvSaverVal = findViewById(R.id.tv_setting_saver_val);
        tvAutostartVal = findViewById(R.id.tv_setting_autostart_val);

        dockRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dockAdapter = new AppAdapter();
        dockRecycler.setAdapter(dockAdapter);

        drawerRecycler.setLayoutManager(new GridLayoutManager(this, 5));
        drawerAdapter = new DrawerAdapter();
        drawerRecycler.setAdapter(drawerAdapter);

        tilesManagerRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        tilesAdapter = new TilesManagerAdapter();
        tilesManagerRecycler.setAdapter(tilesAdapter);

        initSettingsListeners();
        updateDateAndClockUi();
        applyPreferencesUi();
        setupScreensaverTimer();

        loadApps();
        checkAutostart();
    }

    private void updateDateAndClockUi() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM", new Locale("ru"));
        String dateStr = sdf.format(new Date());
        if (!dateStr.isEmpty()) {
            dateStr = Character.toUpperCase(dateStr.charAt(0)) + dateStr.substring(1);
        }
        tvDateDisplay.setText(dateStr);
        saverDate.setText(dateStr);

        boolean is24 = prefs.getBoolean("clock_24h", true);
        if (is24) {
            mainTextClock.setFormat24Hour("HH:mm");
            mainTextClock.setFormat12Hour("HH:mm");
            saverTextClock.setFormat24Hour("HH:mm");
            saverTextClock.setFormat12Hour("HH:mm");
        } else {
            mainTextClock.setFormat24Hour("h:mm a");
            mainTextClock.setFormat12Hour("h:mm a");
            saverTextClock.setFormat24Hour("h:mm a");
            saverTextClock.setFormat12Hour("h:mm a");
        }
    }

    private void applyPreferencesUi() {
        String wallpaper = prefs.getString("wallpaper_theme", "dynamic");
        switch (wallpaper) {
            case "space":
                tvWallpapersVal.setText("Космос (Aerial)");
                heroBanner.setImageResource(R.drawable.bg_poster_space);
                break;
            case "city":
                tvWallpapersVal.setText("Ночной город");
                heroBanner.setImageResource(R.drawable.bg_poster_city);
                break;
            case "ocean":
                tvWallpapersVal.setText("Океан (Aerial)");
                heroBanner.setImageResource(R.drawable.bg_poster_ocean);
                break;
            case "minimal":
                tvWallpapersVal.setText("Тёмный графит");
                heroBanner.setImageResource(R.drawable.bg_poster_minimal);
                break;
            default:
                tvWallpapersVal.setText("Динамические");
                break;
        }

        int ambientLevel = prefs.getInt("ambient_level", 45);
        if (ambientLevel == 0) {
            tvAmbientVal.setText("Выключена");
            ambientGlow.setVisibility(View.GONE);
        } else {
            ambientGlow.setVisibility(View.VISIBLE);
            ambientGlow.setAlpha(ambientLevel / 100f);
            tvAmbientVal.setText(ambientLevel == 25 ? "Мягкая (25%)" : (ambientLevel == 45 ? "Стандарт (45%)" : "Яркая (75%)"));
        }

        String sizeMode = prefs.getString("tiles_size", "normal");
        tvSizeVal.setText(sizeMode.equals("compact") ? "Компактный" : (sizeMode.equals("large") ? "Крупный" : "Стандарт tvOS"));

        tvClockVal.setText(prefs.getBoolean("clock_24h", true) ? "24-часовой" : "12-часовой (AM/PM)");

        int saverMinutes = prefs.getInt("screensaver_timeout", 5);
        tvSaverVal.setText(saverMinutes == 0 ? "Отключено" : "Через " + saverMinutes + " мин");

        String autostartPkg = prefs.getString("autostart_package", "none");
        if (autostartPkg.equals("none")) {
            tvAutostartVal.setText("Главный экран");
        } else {
            tvAutostartVal.setText(prefs.getString("autostart_label", "Приложение"));
        }
    }

    private void checkAutostart() {
        boolean alreadyStarted = prefs.getBoolean("session_started", false);
        String autostartPkg = prefs.getString("autostart_package", "none");
        if (!alreadyStarted && !autostartPkg.equals("none")) {
            prefs.edit().putBoolean("session_started", true).apply();
            try {
                PackageManager pm = getPackageManager();
                Intent launch = pm.getLeanbackLaunchIntentForPackage(autostartPkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(autostartPkg);
                if (launch != null) startActivity(launch);
            } catch (Exception ignored) {}
        }
    }

    private void setupScreensaverTimer() {
        int minutes = prefs.getInt("screensaver_timeout", 5);
        if (idleRunnable != null) idleHandler.removeCallbacks(idleRunnable);
        if (minutes <= 0) return;

        idleRunnable = () -> {
            if (screensaverOverlay.getVisibility() != View.VISIBLE) {
                openScreensaver();
            }
        };
        resetScreensaverTimer();
    }

    private void resetScreensaverTimer() {
        int minutes = prefs.getInt("screensaver_timeout", 5);
        if (minutes > 0 && idleRunnable != null) {
            idleHandler.removeCallbacks(idleRunnable);
            idleHandler.postDelayed(idleRunnable, minutes * 60 * 1000L);
        }
    }

    private void openScreensaver() {
        String wallpaper = prefs.getString("wallpaper_theme", "dynamic");
        if (wallpaper.equals("city")) screensaverBg.setImageResource(R.drawable.bg_poster_city);
        else if (wallpaper.equals("ocean")) screensaverBg.setImageResource(R.drawable.bg_poster_ocean);
        else screensaverBg.setImageResource(R.drawable.bg_poster_space);

        screensaverOverlay.setVisibility(View.VISIBLE);
        screensaverOverlay.setAlpha(0f);
        screensaverOverlay.animate().alpha(1f).setDuration(500).start();
    }

    private void closeScreensaver() {
        screensaverOverlay.animate().alpha(0f).setDuration(350).withEndAction(() -> {
            screensaverOverlay.setVisibility(View.GONE);
            dockRecycler.requestFocus();
        }).start();
        resetScreensaverTimer();
    }

    private void initSettingsListeners() {
        btnSettings.setOnClickListener(v -> openSettings());
        btnSettings.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                animateScale(v, 1.15f, 1.15f, 12f);
                heroTitle.setText("Настройки лаунчера");
            } else {
                animateScale(v, 1.0f, 1.0f, 0f);
            }
        });

        FrameLayout itemTiles = findViewById(R.id.setting_item_tiles);
        TextView tvTiles = findViewById(R.id.tv_setting_tiles_title);
        TextView arrTiles = findViewById(R.id.tv_setting_tiles_arrow);
        itemTiles.setOnClickListener(v -> openTilesManager());
        setupSettingFocus(itemTiles, tvTiles, arrTiles, "⊞", "Плитки экрана", "Выберите, какие приложения отображаются в нижней полке Dock, а какие остаются только в каталоге.");

        FrameLayout itemWallpapers = findViewById(R.id.setting_item_wallpapers);
        TextView tvWallpapers = findViewById(R.id.tv_setting_wallpapers_title);
        itemWallpapers.setOnClickListener(v -> showWallpaperPicker());
        setupSettingFocus(itemWallpapers, tvWallpapers, tvWallpapersVal, "🖼", "Обои и фон", "Выбор кинематографичных заставок Apple Aerial, динамического режима под приложения или тёмного графита.");

        FrameLayout itemAmbient = findViewById(R.id.setting_item_ambient);
        TextView tvAmbient = findViewById(R.id.tv_setting_ambient_title);
        itemAmbient.setOnClickListener(v -> showAmbientPicker());
        setupSettingFocus(itemAmbient, tvAmbient, tvAmbientVal, "💡", "Эмбиент-подсветка", "Регулировка насыщенности и яркости динамического свечения фона под цвет выбранного приложения.");

        FrameLayout itemSize = findViewById(R.id.setting_item_tiles_size);
        TextView tvSize = findViewById(R.id.tv_setting_size_title);
        itemSize.setOnClickListener(v -> showTileSizePicker());
        setupSettingFocus(itemSize, tvSize, tvSizeVal, "🔲", "Размер плиток", "Настройка масштаба карточек в полке Dock (Компактный, Стандартный tvOS, Крупный).");

        FrameLayout itemClock = findViewById(R.id.setting_item_clock);
        TextView tvClock = findViewById(R.id.tv_setting_clock_title);
        itemClock.setOnClickListener(v -> showClockPicker());
        setupSettingFocus(itemClock, tvClock, tvClockVal, "⏱", "Часы и верхний бар", "Переключение между 24-часовым форматом и 12-часовым режимом с отображением даты и дня недели.");

        FrameLayout itemSaver = findViewById(R.id.setting_item_screensaver);
        TextView tvSaver = findViewById(R.id.tv_setting_saver_title);
        itemSaver.setOnClickListener(v -> showScreensaverPicker());
        setupSettingFocus(itemSaver, tvSaver, tvSaverVal, "🕹", "Заставка Aerial", "Таймер автоперехода в режим заставки: скрывает док и оставляет чистый кинематографичный вид с часами.");

        FrameLayout itemAutostart = findViewById(R.id.setting_item_autostart);
        TextView tvAutostart = findViewById(R.id.tv_setting_autostart_title);
        itemAutostart.setOnClickListener(v -> showAutostartPicker());
        setupSettingFocus(itemAutostart, tvAutostart, tvAutostartVal, "🚀", "Быстрый запуск", "Приложение по умолчанию, которое открывается автоматически при включении телевизора.");

        FrameLayout itemReset = findViewById(R.id.setting_item_reset_order);
        TextView tvReset = findViewById(R.id.tv_setting_reset_title);
        itemReset.setOnClickListener(v -> {
            prefs.edit().remove("pinned_order").apply();
            loadApps();
            Toast.makeText(this, "Порядок ярлыков сброшен", Toast.LENGTH_SHORT).show();
        });
        setupSettingFocus(itemReset, tvReset, null, "↺", "Сбросить порядок", "Возвращает исходный порядок плиток по умолчанию на домашнем экране.");

        FrameLayout itemRestore = findViewById(R.id.setting_item_restore_hidden);
        TextView tvRestore = findViewById(R.id.tv_setting_restore_title);
        itemRestore.setOnClickListener(v -> {
            prefs.edit().remove("hidden_apps").apply();
            loadApps();
            Toast.makeText(this, "Все скрытые приложения восстановлены", Toast.LENGTH_SHORT).show();
        });
        setupSettingFocus(itemRestore, tvRestore, null, "👁", "Скрытые программы", "Восстанавливает видимость всех ранее скрытых программ в каталоге и главном меню.");

        FrameLayout itemSys = findViewById(R.id.setting_item_system);
        TextView tvSys = findViewById(R.id.tv_setting_sys_title);
        TextView arrSys = findViewById(R.id.tv_setting_sys_arrow);
        itemSys.setOnClickListener(v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {}
        });
        setupSettingFocus(itemSys, tvSys, arrSys, "⚙", "Android TV", "Переход в стандартные системные параметры телевизора.");
    }

    private void showWallpaperPicker() {
        CharSequence[] options = {"Динамические (под приложение)", "Космос (Apple Aerial)", "Ночной город", "Океан (Aerial)", "Тёмный графит (Minimal)"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Внешний вид и Обои")
                .setItems(options, (dialog, which) -> {
                    String[] keys = {"dynamic", "space", "city", "ocean", "minimal"};
                    prefs.edit().putString("wallpaper_theme", keys[which]).apply();
                    applyPreferencesUi();
                    loadApps();
                }).show();
    }

    private void showAmbientPicker() {
        CharSequence[] options = {"Выключить подсветку", "Мягкая (25%)", "Стандартная (45%)", "Яркая неоновая (75%)"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Эмбиент-подсветка (Ambient Glow)")
                .setItems(options, (dialog, which) -> {
                    int[] levels = {0, 25, 45, 75};
                    prefs.edit().putInt("ambient_level", levels[which]).apply();
                    applyPreferencesUi();
                }).show();
    }

    private void showTileSizePicker() {
        CharSequence[] options = {"Компактный (7-8 в ряд)", "Стандарт tvOS (160dp)", "Крупный Focus (190dp)"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Размер плиток в Dock")
                .setItems(options, (dialog, which) -> {
                    String[] keys = {"compact", "normal", "large"};
                    prefs.edit().putString("tiles_size", keys[which]).apply();
                    applyPreferencesUi();
                    dockAdapter.notifyDataSetChanged();
                }).show();
    }

    private void showClockPicker() {
        CharSequence[] options = {"24-часовой формат (21:45)", "12-часовой формат (9:45 PM)"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Формат времени")
                .setItems(options, (dialog, which) -> {
                    prefs.edit().putBoolean("clock_24h", which == 0).apply();
                    updateDateAndClockUi();
                    applyPreferencesUi();
                }).show();
    }

    private void showScreensaverPicker() {
        CharSequence[] options = {"Отключить заставку", "Через 2 минуты", "Через 5 минут", "Через 10 минут"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Заставка и режим сна")
                .setItems(options, (dialog, which) -> {
                    int[] timeouts = {0, 2, 5, 10};
                    prefs.edit().putInt("screensaver_timeout", timeouts[which]).apply();
                    applyPreferencesUi();
                    setupScreensaverTimer();
                }).show();
    }

    private void showAutostartPicker() {
        List<String> labels = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        labels.add("Оставаться на Главном экране");
        pkgs.add("none");

        for (AppItem item : allInstalledApps) {
            labels.add(item.label);
            pkgs.add(item.packageName);
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Быстрый запуск при включении")
                .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                    prefs.edit()
                            .putString("autostart_package", pkgs.get(which))
                            .putString("autostart_label", labels.get(which))
                            .apply();
                    applyPreferencesUi();
                    Toast.makeText(this, "Автозапуск: " + labels.get(which), Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void setupSettingFocus(View item, TextView title, TextView extra, String icon, String header, String desc) {
        item.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                animateScale(v, 1.04f, 1.04f, 10f);
                if (title != null) title.setTextColor(Color.BLACK);
                if (extra != null) extra.setTextColor(0xFF222222);
                settingsPreviewIcon.setText(icon);
                settingsPreviewTitle.setText(header);
                settingsPreviewDesc.setText(desc);
            } else {
                animateScale(v, 1.0f, 1.0f, 0f);
                if (title != null) title.setTextColor(Color.WHITE);
                if (extra != null) extra.setTextColor(0x88FFFFFF);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PackageReceiver.listener = null;
        if (idleRunnable != null) idleHandler.removeCallbacks(idleRunnable);
    }

    @Override
    public void onPackageChanged() {
        runOnUiThread(this::loadApps);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        resetScreensaverTimer();

        if (screensaverOverlay.getVisibility() == View.VISIBLE) {
            closeScreensaver();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (tilesManagerOverlay.getVisibility() == View.VISIBLE) {
                closeTilesManager();
                return true;
            }
            if (settingsOverlay.getVisibility() == View.VISIBLE) {
                closeSettings();
                return true;
            }
            if (appDrawerOverlay.getVisibility() == View.VISIBLE) {
                closeDrawer();
                return true;
            }
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

    private void openSettings() {
        applyPreferencesUi();
        settingsOverlay.setVisibility(View.VISIBLE);
        settingsOverlay.setAlpha(0f);
        settingsOverlay.animate().alpha(1f).setDuration(220).start();
        findViewById(R.id.setting_item_tiles).requestFocus();
    }

    private void closeSettings() {
        settingsOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            settingsOverlay.setVisibility(View.GONE);
            dockRecycler.requestFocus();
        }).start();
    }

    private void openTilesManager() {
        tilesAdapter.notifyDataSetChanged();
        tilesManagerOverlay.setVisibility(View.VISIBLE);
        tilesManagerOverlay.setAlpha(0f);
        tilesManagerOverlay.animate().alpha(1f).setDuration(220).start();
        tilesManagerRecycler.requestFocus();
    }

    private void closeTilesManager() {
        tilesManagerOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            tilesManagerOverlay.setVisibility(View.GONE);
            findViewById(R.id.setting_item_tiles).requestFocus();
            loadApps();
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

        if (pinnedOrder.isEmpty()) {
            for (AppItem app : visibleApps) {
                pinnedOrder.add(app.packageName);
            }
            savePinnedOrder(pinnedOrder);
        }

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
        tilesAdapter.notifyDataSetChanged();
    }

    private void animateAmbientGlow(int targetColor) {
        if (prefs.getInt("ambient_level", 45) == 0) return;

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
        String wallpaper = prefs.getString("wallpaper_theme", "dynamic");
        if (wallpaper.equals("space")) return R.drawable.bg_poster_space;
        if (wallpaper.equals("city")) return R.drawable.bg_poster_city;
        if (wallpaper.equals("ocean")) return R.drawable.bg_poster_ocean;
        if (wallpaper.equals("minimal")) return R.drawable.bg_poster_minimal;

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
            String sizeMode = prefs.getString("tiles_size", "normal");
            int cardW = 160;
            int cardH = 100;
            int iconSize = 56;
            if (sizeMode.equals("compact")) {
                cardW = 135;
                cardH = 84;
                iconSize = 46;
            } else if (sizeMode.equals("large")) {
                cardW = 190;
                cardH = 118;
                iconSize = 66;
            }

            FrameLayout card = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(cardW, cardH);
            lp.setMargins(14, 18, 14, 18);
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);
            card.setClipToOutline(true);

            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(iv, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));

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
                resetScreensaverTimer();
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
                resetScreensaverTimer();
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
                resetScreensaverTimer();
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
                resetScreensaverTimer();
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
                resetScreensaverTimer();
                PackageManager pm = getPackageManager();
                Intent launch = pm.getLeanbackLaunchIntentForPackage(item.packageName);
                if (launch == null) launch = pm.getLaunchIntentForPackage(item.packageName);
                if (launch != null) startActivity(launch);
            });

            card.setOnLongClickListener(v -> {
                resetScreensaverTimer();
                showAppContextMenu(item, true);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return allInstalledApps.size();
        }
    }

    private class TilesManagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout row = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 68
            );
            lp.setMargins(10, 8, 10, 8);
            row.setLayoutParams(lp);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackgroundResource(R.drawable.settings_item_selector);
            row.setPadding(20, 0, 20, 0);

            LinearLayout left = new LinearLayout(parent.getContext());
            left.setOrientation(LinearLayout.HORIZONTAL);
            left.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START
            );
            row.addView(left, leftLp);

            ImageView iv = new ImageView(parent.getContext());
            left.addView(iv, new LinearLayout.LayoutParams(42, 42));

            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(16);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            tvLp.setMarginStart(18);
            left.addView(tv, tvLp);

            TextView statusBadge = new TextView(parent.getContext());
            statusBadge.setTextSize(13);
            statusBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            statusBadge.setPadding(16, 6, 16, 6);
            FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL
            );
            row.addView(statusBadge, badgeLp);

            return new RecyclerView.ViewHolder(row) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AppItem item = allInstalledApps.get(position);
            FrameLayout row = (FrameLayout) holder.itemView;
            LinearLayout left = (LinearLayout) row.getChildAt(0);
            ImageView iv = (ImageView) left.getChildAt(0);
            TextView tv = (TextView) left.getChildAt(1);
            TextView badge = (TextView) row.getChildAt(1);

            iv.setImageDrawable(item.icon);
            tv.setText(item.label);

            List<String> pinned = loadPinnedOrder();
            boolean isPinned = pinned.contains(item.packageName);

            updateBadgeUi(badge, isPinned, false);

            row.setOnFocusChangeListener((v, hasFocus) -> {
                resetScreensaverTimer();
                if (hasFocus) {
                    animateScale(v, 1.03f, 1.03f, 8f);
                    tv.setTextColor(Color.BLACK);
                    updateBadgeUi(badge, loadPinnedOrder().contains(item.packageName), true);
                } else {
                    animateScale(v, 1.0f, 1.0f, 0f);
                    tv.setTextColor(Color.WHITE);
                    updateBadgeUi(badge, loadPinnedOrder().contains(item.packageName), false);
                }
            });

            row.setOnClickListener(v -> {
                resetScreensaverTimer();
                List<String> currentPinned = loadPinnedOrder();
                if (currentPinned.contains(item.packageName)) {
                    currentPinned.remove(item.packageName);
                    savePinnedOrder(currentPinned);
                    updateBadgeUi(badge, false, row.hasFocus());
                    Toast.makeText(MainActivity.this, item.label + " убран с главного", Toast.LENGTH_SHORT).show();
                } else {
                    currentPinned.add(item.packageName);
                    savePinnedOrder(currentPinned);
                    updateBadgeUi(badge, true, row.hasFocus());
                    Toast.makeText(MainActivity.this, item.label + " добавлен на главный", Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void updateBadgeUi(TextView badge, boolean isPinned, boolean isFocused) {
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(12f);
            if (isPinned) {
                badge.setText("НА ЭКРАНЕ");
                if (isFocused) {
                    badge.setTextColor(Color.WHITE);
                    gd.setColor(0xFF248A3D);
                } else {
                    badge.setTextColor(0xFF34C759);
                    gd.setColor(0x3334C759);
                }
            } else {
                badge.setText("СКРЫТО");
                if (isFocused) {
                    badge.setTextColor(Color.WHITE);
                    gd.setColor(0xFF555566);
                } else {
                    badge.setTextColor(0x88FFFFFF);
                    gd.setColor(0x22FFFFFF);
                }
            }
            badge.setBackground(gd);
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
