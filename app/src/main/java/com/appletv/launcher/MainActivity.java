package com.appletv.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
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

    private View blurTargetContainer;
    private ImageView heroBanner;
    private View ambientGlow;
    private TextView heroTitle;
    private RelativeLayout homeContentContainer;
    private FrameLayout glassDockContainer;
    private RecyclerView dockRecycler;
    private FrameLayout btnSettings;
    private TextClock mainTextClock;
    private TextView tvDateDisplay;

    // Стеклянные оверлеи
    private FrameLayout appDrawerOverlay;
    private RecyclerView drawerRecycler;

    private FrameLayout settingsOverlay;
    private FrameLayout tilesManagerOverlay;
    private RecyclerView tilesManagerRecycler;

    // Заставка Screensaver
    private FrameLayout screensaverOverlay;
    private ImageView screensaverBg;
    private TextClock saverTextClock;
    private TextView saverDate;
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private Runnable idleRunnable;

    // Превью настроек
    private TextView settingsPreviewIcon;
    private TextView settingsPreviewTitle;
    private TextView settingsPreviewDesc;

    // Значения настроек
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
    private ValueAnimator blurAnimator;

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

    public static class PickerOption {
        public String title;
        public String key;
        public boolean isSelected;

        public PickerOption(String title, String key, boolean isSelected) {
            this.title = title;
            this.key = key;
            this.isSelected = isSelected;
        }
    }

    public interface OnOptionSelectedListener {
        void onSelected(PickerOption option);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("tvos_launcher_prefs", Context.MODE_PRIVATE);
        PackageReceiver.listener = this;

        blurTargetContainer = findViewById(R.id.blur_target_container);
        heroBanner = findViewById(R.id.hero_banner);
        ambientGlow = findViewById(R.id.ambient_glow);
        heroTitle = findViewById(R.id.hero_title);
        homeContentContainer = findViewById(R.id.home_content_container);
        glassDockContainer = findViewById(R.id.glass_dock_container);
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

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void applyPreferencesUi() {
        // 1. Применение обоев
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

        // 2. Эмбиент подсветка
        int ambientLevel = prefs.getInt("ambient_level", 45);
        if (ambientLevel == 0) {
            tvAmbientVal.setText("Выключена");
            ambientGlow.setVisibility(View.GONE);
        } else {
            ambientGlow.setVisibility(View.VISIBLE);
            ambientGlow.setAlpha(ambientLevel / 100f);
            tvAmbientVal.setText(ambientLevel == 25 ? "Мягкая (25%)" : (ambientLevel == 45 ? "Стандарт (45%)" : "Яркая (75%)"));
        }

        // 3. Размер плиток и адаптация высоты стеклянного дока
        String sizeMode = prefs.getString("tiles_size", "normal");
        tvSizeVal.setText(sizeMode.equals("compact") ? "Компактный" : (sizeMode.equals("large") ? "Крупный" : "Стандарт tvOS"));

        // Динамический пересчет высоты дока: базовый размер карточки + запас под 1.20x зум при фокусе
        int cardHeightDp = sizeMode.equals("compact") ? 84 : (sizeMode.equals("large") ? 118 : 100);
        int marginVerticalDp = sizeMode.equals("compact") ? 12 : (sizeMode.equals("large") ? 20 : 16);
        int dockHeightPx = dpToPx((int)(cardHeightDp * 1.25f) + (marginVerticalDp * 2) + 24);

        if (glassDockContainer != null) {
            ViewGroup.LayoutParams dLp = glassDockContainer.getLayoutParams();
            if (dLp != null) {
                dLp.height = dockHeightPx;
                glassDockContainer.setLayoutParams(dLp);
            }
        }

        // 4. Часы
        tvClockVal.setText(prefs.getBoolean("clock_24h", true) ? "24-часовой" : "12-часовой (AM/PM)");

        // 5. Заставка
        int saverMinutes = prefs.getInt("screensaver_timeout", 5);
        tvSaverVal.setText(saverMinutes == 0 ? "Отключено" : "Через " + saverMinutes + " мин");

        // 6. Автозапуск
        String autostartPkg = prefs.getString("autostart_package", "none");
        if (autostartPkg.equals("none")) {
            tvAutostartVal.setText("Главный экран");
        } else {
            tvAutostartVal.setText(prefs.getString("autostart_label", "Приложение"));
        }
    }

    /**
     * Аппаратный мягкий эффект размытия заднего плана (iOS / tvOS Backdrop Filter)
     */
    private void applyBlurEffect(boolean enable) {
        if (blurTargetContainer == null) return;

        if (blurAnimator != null) {
            blurAnimator.cancel();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float fromRadius = enable ? 0.1f : 40.0f;
            float toRadius = enable ? 40.0f : 0.1f;

            blurAnimator = ValueAnimator.ofFloat(fromRadius, toRadius);
            blurAnimator.setDuration(240);
            blurAnimator.setInterpolator(new DecelerateInterpolator());
            blurAnimator.addUpdateListener(anim -> {
                float radius = (float) anim.getAnimatedValue();
                if (radius > 0.5f) {
                    blurTargetContainer.setRenderEffect(
                            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                    );
                } else {
                    blurTargetContainer.setRenderEffect(null);
                }
            });
            blurAnimator.start();
        } else {
            // Мягкий cinematic scale fallback для более старых систем
            float targetScale = enable ? 0.96f : 1.0f;
            float targetAlpha = enable ? 0.55f : 1.0f;

            blurTargetContainer.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .alpha(targetAlpha)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
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
        screensaverOverlay.requestFocus();
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

        // 1. Плитки главного экрана
        FrameLayout itemTiles = findViewById(R.id.setting_item_tiles);
        TextView tvTiles = findViewById(R.id.tv_setting_tiles_title);
        TextView arrTiles = findViewById(R.id.tv_setting_tiles_arrow);
        itemTiles.setOnClickListener(v -> openTilesManager());
        setupSettingFocus(itemTiles, tvTiles, arrTiles, "⊞", "Плитки экрана", "Выберите, какие приложения отображаются в нижней полке Dock, а какие остаются только в каталоге.");

        // 2. Внешний вид и обои
        FrameLayout itemWallpapers = findViewById(R.id.setting_item_wallpapers);
        TextView tvWallpapers = findViewById(R.id.tv_setting_wallpapers_title);
        itemWallpapers.setOnClickListener(v -> showWallpaperPickerTvos());
        setupSettingFocus(itemWallpapers, tvWallpapers, tvWallpapersVal, "🖼", "Обои и фон", "Выбор кинематографичных заставок Apple Aerial, динамического режима под приложения или тёмного графита.");

        // 3. Эмбиент-подсветка
        FrameLayout itemAmbient = findViewById(R.id.setting_item_ambient);
        TextView tvAmbient = findViewById(R.id.tv_setting_ambient_title);
        itemAmbient.setOnClickListener(v -> showAmbientPickerTvos());
        setupSettingFocus(itemAmbient, tvAmbient, tvAmbientVal, "💡", "Эмбиент-подсветка", "Регулировка насыщенности и яркости динамического свечения фона под цвет выбранного приложения.");

        // 4. Размер плиток
        FrameLayout itemSize = findViewById(R.id.setting_item_tiles_size);
        TextView tvSize = findViewById(R.id.tv_setting_size_title);
        itemSize.setOnClickListener(v -> showTileSizePickerTvos());
        setupSettingFocus(itemSize, tvSize, tvSizeVal, "🔲", "Размер плиток", "Настройка масштаба всех карточек в полке Dock (Компактный, Стандартный tvOS, Крупный).");

        // 5. Часы и верхний бар
        FrameLayout itemClock = findViewById(R.id.setting_item_clock);
        TextView tvClock = findViewById(R.id.tv_setting_clock_title);
        itemClock.setOnClickListener(v -> showClockPickerTvos());
        setupSettingFocus(itemClock, tvClock, tvClockVal, "⏱", "Часы и верхний бар", "Переключение между 24-часовым форматом и 12-часовым режимом с отображением даты и дня недели.");

        // 6. Заставка Aerial
        FrameLayout itemSaver = findViewById(R.id.setting_item_screensaver);
        TextView tvSaver = findViewById(R.id.tv_setting_saver_title);
        itemSaver.setOnClickListener(v -> showScreensaverPickerTvos());
        setupSettingFocus(itemSaver, tvSaver, tvSaverVal, "🕹", "Заставка Aerial", "Таймер автоперехода в режим заставки: скрывает док и оставляет чистый кинематографичный вид с часами.");

        // 7. Автозапуск
        FrameLayout itemAutostart = findViewById(R.id.setting_item_autostart);
        TextView tvAutostart = findViewById(R.id.tv_setting_autostart_title);
        itemAutostart.setOnClickListener(v -> showAutostartPickerTvos());
        setupSettingFocus(itemAutostart, tvAutostart, tvAutostartVal, "🚀", "Быстрый запуск", "Приложение по умолчанию, которое открывается автоматически при включении телевизора.");

        // 8. Сбросить порядок
        FrameLayout itemReset = findViewById(R.id.setting_item_reset_order);
        TextView tvReset = findViewById(R.id.tv_setting_reset_title);
        itemReset.setOnClickListener(v -> {
            prefs.edit().remove("pinned_order").apply();
            loadApps();
            Toast.makeText(this, "Порядок ярлыков сброшен", Toast.LENGTH_SHORT).show();
        });
        setupSettingFocus(itemReset, tvReset, null, "↺", "Сбросить порядок", "Возвращает исходный порядок плиток по умолчанию на домашнем экране.");

        // 9. Восстановить скрытые
        FrameLayout itemRestore = findViewById(R.id.setting_item_restore_hidden);
        TextView tvRestore = findViewById(R.id.tv_setting_restore_title);
        itemRestore.setOnClickListener(v -> {
            prefs.edit().remove("hidden_apps").apply();
            loadApps();
            Toast.makeText(this, "Все скрытые приложения восстановлены", Toast.LENGTH_SHORT).show();
        });
        setupSettingFocus(itemRestore, tvRestore, null, "👁", "Скрытые программы", "Восстанавливает видимость всех ранее скрытых программ в каталоге и главном меню.");

        // 10. Параметры Android TV
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

    // --- МОДАЛЬНЫЙ ДИАЛОГ ВЫБОРА tvOS ---

    private void openTvosPickerDialog(String title, List<PickerOption> options, OnOptionSelectedListener listener) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_tvos_picker);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.dialog_picker_title);
        tvTitle.setText(title);

        RecyclerView recycler = dialog.findViewById(R.id.dialog_picker_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        PickerAdapter adapter = new PickerAdapter(options, option -> {
            listener.onSelected(option);
            dialog.dismiss();
        });
        recycler.setAdapter(adapter);

        dialog.show();
        recycler.post(() -> {
            recycler.requestFocus();
            if (adapter.getItemCount() > 0) {
                View first = recycler.getLayoutManager().findViewByPosition(0);
                if (first != null) first.requestFocus();
            }
        });
    }

    private void showWallpaperPickerTvos() {
        String current = prefs.getString("wallpaper_theme", "dynamic");
        List<PickerOption> list = new ArrayList<>();
        list.add(new PickerOption("Динамические (под приложение)", "dynamic", current.equals("dynamic")));
        list.add(new PickerOption("Космос (Apple Aerial)", "space", current.equals("space")));
        list.add(new PickerOption("Ночной город", "city", current.equals("city")));
        list.add(new PickerOption("Океан (Aerial)", "ocean", current.equals("ocean")));
        list.add(new PickerOption("Тёмный графит (Minimal)", "minimal", current.equals("minimal")));

        openTvosPickerDialog("Внешний вид и Обои", list, option -> {
            prefs.edit().putString("wallpaper_theme", option.key).apply();
            applyPreferencesUi();
            loadApps();
        });
    }

    private void showAmbientPickerTvos() {
        int current = prefs.getInt("ambient_level", 45);
        List<PickerOption> list = new ArrayList<>();
        list.add(new PickerOption("Выключить подсветку", "0", current == 0));
        list.add(new PickerOption("Мягкая (25%)", "25", current == 25));
        list.add(new PickerOption("Стандартная (45%)", "45", current == 45));
        list.add(new PickerOption("Яркая неоновая (75%)", "75", current == 75));

        openTvosPickerDialog("Эмбиент-подсветка", list, option -> {
            prefs.edit().putInt("ambient_level", Integer.parseInt(option.key)).apply();
            applyPreferencesUi();
        });
    }

    private void showTileSizePickerTvos() {
        String current = prefs.getString("tiles_size", "normal");
        List<PickerOption> list = new ArrayList<>();
        list.add(new PickerOption("Компактный (7-8 в ряд)", "compact", current.equals("compact")));
        list.add(new PickerOption("Стандарт tvOS (160dp)", "normal", current.equals("normal")));
        list.add(new PickerOption("Крупный Focus (190dp)", "large", current.equals("large")));

        openTvosPickerDialog("Размер плиток в Dock", list, option -> {
            prefs.edit().putString("tiles_size", option.key).apply();
            applyPreferencesUi();

            dockAdapter = new AppAdapter();
            dockRecycler.setAdapter(dockAdapter);
        });
    }

    private void showClockPickerTvos() {
        boolean is24 = prefs.getBoolean("clock_24h", true);
        List<PickerOption> list = new ArrayList<>();
        list.add(new PickerOption("24-часовой формат (21:45)", "true", is24));
        list.add(new PickerOption("12-часовой формат (9:45 PM)", "false", !is24));

        openTvosPickerDialog("Формат времени", list, option -> {
            prefs.edit().putBoolean("clock_24h", Boolean.parseBoolean(option.key)).apply();
            updateDateAndClockUi();
            applyPreferencesUi();
        });
    }

    private void showScreensaverPickerTvos() {
        int current = prefs.getInt("screensaver_timeout", 5);
        List<PickerOption> list = new ArrayList<>();
        list.add(new PickerOption("Отключить заставку", "0", current == 0));
        list.add(new PickerOption("Через 2 минуты", "2", current == 2));
        list.add(new PickerOption("Через 5 минут", "5", current == 5));
        list.add(new PickerOption("Через 10 минут", "10", current == 10));

        openTvosPickerDialog("Заставка и режим сна", list, option -> {
            prefs.edit().putInt("screensaver_timeout", Integer.parseInt(option.key)).apply();
            applyPreferencesUi();
            setupScreensaverTimer();
        });
    }

    private void showAutostartPickerTvos() {
        String current = prefs.getString("autostart_package", "none");
        List<PickerOption> list = new ArrayList<>();
        list.add(new PickerOption("Оставаться на Главном экране", "none", current.equals("none")));

        for (AppItem app : allInstalledApps) {
            list.add(new PickerOption(app.label, app.packageName, current.equals(app.packageName)));
        }

        openTvosPickerDialog("Быстрый запуск при включении", list, option -> {
            prefs.edit()
                    .putString("autostart_package", option.key)
                    .putString("autostart_label", option.title)
                    .apply();
            applyPreferencesUi();
            Toast.makeText(this, "Автозапуск: " + option.title, Toast.LENGTH_SHORT).show();
        });
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
        if (homeContentContainer != null) {
            homeContentContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        applyBlurEffect(true);
        appDrawerOverlay.setVisibility(View.VISIBLE);
        appDrawerOverlay.setAlpha(0f);
        appDrawerOverlay.animate().alpha(1f).setDuration(220).start();
        drawerRecycler.requestFocus();
    }

    private void closeDrawer() {
        applyBlurEffect(false);
        appDrawerOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            appDrawerOverlay.setVisibility(View.GONE);
            if (homeContentContainer != null) {
                homeContentContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            dockRecycler.requestFocus();
        }).start();
    }

    private void openSettings() {
        if (homeContentContainer != null) {
            homeContentContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        applyPreferencesUi();
        applyBlurEffect(true);
        settingsOverlay.setVisibility(View.VISIBLE);
        settingsOverlay.setAlpha(0f);
        settingsOverlay.animate().alpha(1f).setDuration(220).start();
        findViewById(R.id.setting_item_tiles).requestFocus();
    }

    private void closeSettings() {
        applyBlurEffect(false);
        settingsOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            settingsOverlay.setVisibility(View.GONE);
            if (homeContentContainer != null) {
                homeContentContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            dockRecycler.requestFocus();
        }).start();
    }

    private void openTilesManager() {
        if (settingsOverlay != null) {
            settingsOverlay.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        tilesAdapter.notifyDataSetChanged();
        tilesManagerOverlay.setVisibility(View.VISIBLE);
        tilesManagerOverlay.setAlpha(0f);
        tilesManagerOverlay.animate().alpha(1f).setDuration(220).start();
        tilesManagerRecycler.requestFocus();
    }

    private void closeTilesManager() {
        tilesManagerOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            tilesManagerOverlay.setVisibility(View.GONE);
            if (settingsOverlay != null) {
                settingsOverlay.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
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

    // --- 1. Адаптер главного Dock (Идеальное сохранение скругления и защита от обрезки) ---
    private class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private int getCardWidth() {
            String sizeMode = prefs.getString("tiles_size", "normal");
            if (sizeMode.equals("compact")) return dpToPx(135);
            if (sizeMode.equals("large")) return dpToPx(190);
            return dpToPx(160);
        }

        private int getCardHeight() {
            String sizeMode = prefs.getString("tiles_size", "normal");
            if (sizeMode.equals("compact")) return dpToPx(84);
            if (sizeMode.equals("large")) return dpToPx(118);
            return dpToPx(100);
        }

        private int getIconSize() {
            String sizeMode = prefs.getString("tiles_size", "normal");
            if (sizeMode.equals("compact")) return dpToPx(46);
            if (sizeMode.equals("large")) return dpToPx(66);
            return dpToPx(56);
        }

        private int getMarginHorizontal() {
            String sizeMode = prefs.getString("tiles_size", "normal");
            if (sizeMode.equals("compact")) return dpToPx(10);
            if (sizeMode.equals("large")) return dpToPx(16);
            return dpToPx(14);
        }

        private int getMarginVertical() {
            String sizeMode = prefs.getString("tiles_size", "normal");
            if (sizeMode.equals("compact")) return dpToPx(12);
            if (sizeMode.equals("large")) return dpToPx(20);
            return dpToPx(16);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(getCardWidth(), getCardHeight());
            lp.setMargins(getMarginHorizontal(), getMarginVertical(), getMarginHorizontal(), getMarginVertical());
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);

            // Аппаратное скругление углов tvOS, защищающее от срезания в квадрат
            final float radiusPx = dpToPx(20);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                }
            });
            card.setClipToOutline(true);

            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(iv, new FrameLayout.LayoutParams(getIconSize(), getIconSize(), Gravity.CENTER));

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

            // Синхронизация габаритов при смене масштаба в SharedPreferences
            int targetW = getCardWidth();
            int targetH = getCardHeight();
            int marginH = getMarginHorizontal();
            int marginV = getMarginVertical();

            ViewGroup.MarginLayoutParams clp = (ViewGroup.MarginLayoutParams) card.getLayoutParams();
            if (clp.width != targetW || clp.height != targetH || clp.topMargin != marginV) {
                clp.width = targetW;
                clp.height = targetH;
                clp.setMargins(marginH, marginV, marginH, marginV);
                card.setLayoutParams(clp);
            }

            ImageView iv = (ImageView) card.getChildAt(0);
            TextView tv = (TextView) card.getChildAt(1);

            int isize = getIconSize();
            FrameLayout.LayoutParams ivLp = (FrameLayout.LayoutParams) iv.getLayoutParams();
            if (ivLp.width != isize || ivLp.height != isize) {
                ivLp.width = isize;
                ivLp.height = isize;
                iv.setLayoutParams(ivLp);
            }

            if (item.isActionAllApps) {
                iv.setVisibility(View.GONE);
                tv.setVisibility(View.VISIBLE);
                tv.setText("⊞\nВсе");
            } else {
                tv.setVisibility(View.GONE);
                iv.setVisibility(View.VISIBLE);
                iv.setImageDrawable(item.icon);
            }

            float cornerRadius = dpToPx(20);
            GradientDrawable normalBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
            normalBg.setCornerRadius(cornerRadius);
            card.setBackground(normalBg);

            card.setOnFocusChangeListener((v, hasFocus) -> {
                resetScreensaverTimer();
                if (hasFocus) {
                    animateScale(v, 1.20f, 1.20f, dpToPx(16));
                    GradientDrawable focusBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
                    focusBg.setCornerRadius(cornerRadius);
                    focusBg.setStroke(dpToPx(4), Color.WHITE);
                    v.setBackground(focusBg);

                    heroTitle.setText(item.label);

                    String theme = prefs.getString("wallpaper_theme", "dynamic");
                    if (theme.equals("dynamic")) {
                        heroBanner.setImageResource(item.bannerRes);
                    }
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

    // --- 2. Адаптер каталога Drawer ---
    private class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(dpToPx(180), dpToPx(110));
            int m = dpToPx(14);
            lp.setMargins(m, m, m, m);
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);

            final float radiusPx = dpToPx(20);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                }
            });
            card.setClipToOutline(true);

            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(iv, new FrameLayout.LayoutParams(dpToPx(60), dpToPx(60), Gravity.CENTER));

            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(12);
            tv.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            tv.setPadding(0, 0, 0, dpToPx(8));
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

            float cornerRadius = dpToPx(20);
            GradientDrawable normalBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
            normalBg.setCornerRadius(cornerRadius);
            card.setBackground(normalBg);

            card.setOnFocusChangeListener((v, hasFocus) -> {
                resetScreensaverTimer();
                if (hasFocus) {
                    animateScale(v, 1.15f, 1.15f, dpToPx(14));
                    GradientDrawable focusBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
                    focusBg.setCornerRadius(cornerRadius);
                    focusBg.setStroke(dpToPx(3), Color.WHITE);
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

    // --- 3. Адаптер «Управление плитками» ---
    private class TilesManagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout row = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(68)
            );
            lp.setMargins(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
            row.setLayoutParams(lp);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackgroundResource(R.drawable.settings_item_selector);
            row.setPadding(dpToPx(20), 0, dpToPx(20), 0);

            LinearLayout left = new LinearLayout(parent.getContext());
            left.setOrientation(LinearLayout.HORIZONTAL);
            left.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START
            );
            row.addView(left, leftLp);

            ImageView iv = new ImageView(parent.getContext());
            left.addView(iv, new LinearLayout.LayoutParams(dpToPx(42), dpToPx(42)));

            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(16);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            tvLp.setMarginStart(dpToPx(18));
            left.addView(tv, tvLp);

            TextView statusBadge = new TextView(parent.getContext());
            statusBadge.setTextSize(13);
            statusBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            statusBadge.setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6));
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
                    animateScale(v, 1.03f, 1.03f, dpToPx(8));
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
            gd.setCornerRadius(dpToPx(12));
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

    // --- 4. Адаптер нативного окна выбора tvOS Picker ---
    private class PickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<PickerOption> options;
        private final OnOptionSelectedListener listener;

        public PickerAdapter(List<PickerOption> options, OnOptionSelectedListener listener) {
            this.options = options;
            this.listener = listener;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout item = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(56)
            );
            lp.setMargins(0, dpToPx(5), 0, dpToPx(5));
            item.setLayoutParams(lp);
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackgroundResource(R.drawable.settings_item_selector);
            item.setPadding(dpToPx(20), 0, dpToPx(20), 0);

            item.setNextFocusLeftId(item.getId());
            item.setNextFocusRightId(item.getId());

            TextView title = new TextView(parent.getContext());
            title.setTextSize(16);
            title.setTextColor(Color.WHITE);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL
            );
            item.addView(title, tLp);

            TextView check = new TextView(parent.getContext());
            check.setTextSize(18);
            check.setText("✓");
            check.setTextColor(0xFF34C759);
            check.setTypeface(null, android.graphics.Typeface.BOLD);
            FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL
            );
            item.addView(check, cLp);

            return new RecyclerView.ViewHolder(item) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            PickerOption opt = options.get(position);
            FrameLayout item = (FrameLayout) holder.itemView;
            TextView title = (TextView) item.getChildAt(0);
            TextView check = (TextView) item.getChildAt(1);

            title.setText(opt.title);
            check.setVisibility(opt.isSelected ? View.VISIBLE : View.GONE);

            item.setOnFocusChangeListener((v, hasFocus) -> {
                resetScreensaverTimer();
                if (hasFocus) {
                    animateScale(v, 1.03f, 1.03f, dpToPx(8));
                    title.setTextColor(Color.BLACK);
                    check.setTextColor(0xFF1B7030);
                } else {
                    animateScale(v, 1.0f, 1.0f, 0f);
                    title.setTextColor(Color.WHITE);
                    check.setTextColor(0xFF34C759);
                }
            });

            item.setOnClickListener(v -> {
                resetScreensaverTimer();
                if (listener != null) {
                    listener.onSelected(opt);
                }
            });
        }

        @Override
        public int getItemCount() {
            return options.size();
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
