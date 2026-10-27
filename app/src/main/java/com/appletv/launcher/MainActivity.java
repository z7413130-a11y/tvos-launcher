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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements PackageReceiver.PackageListener {

    private ImageView heroBanner;
    private View ambientGlow;
    private TextView heroTitle;
    private RelativeLayout homeContentContainer;
    private RecyclerView dockRecycler;
    private FrameLayout btnSettings;
    private FrameLayout btnOpenControlCenter;
    private TextClock mainTextClock;
    private TextView tvDateDisplay;

    // Стеклянные оверлеи
    private FrameLayout appDrawerOverlay;
    private RecyclerView drawerRecycler;

    private FrameLayout settingsOverlay;
    private FrameLayout tilesManagerOverlay;
    private RecyclerView tilesManagerRecycler;

    // Центр управления tvOS (по центру экрана, как на фото)
    private FrameLayout controlCenterOverlay;
    private LinearLayout controlCenterPanel;
    private TextView ccWifiName;
    private TextView ccWifiStatus;
    private TextView ccVolumeLevel;
    private TextView ccVolIcon;
    private TextView ccSleepTimerVal;
    private AudioManager audioManager;
    private int sleepTimerMinutes = 0;
    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTimerRunnable;

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
    private TextView tvSoundVal;
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

    // Звуковой движок tvOS
    private SoundManager soundManager;

    // Режим сортировки в стиле tvOS
    private boolean isJiggleMode = false;
    private boolean isDraggingItem = false;
    private int currentGrabbedPosition = -1;
    private long jiggleModeStartTime = 0L;

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
        soundManager = SoundManager.getInstance(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        heroBanner = findViewById(R.id.hero_banner);
        ambientGlow = findViewById(R.id.ambient_glow);
        heroTitle = findViewById(R.id.hero_title);
        homeContentContainer = findViewById(R.id.home_content_container);
        dockRecycler = findViewById(R.id.dock_recycler);
        btnSettings = findViewById(R.id.btn_launcher_settings);
        btnOpenControlCenter = findViewById(R.id.btn_open_control_center);
        mainTextClock = findViewById(R.id.main_text_clock);
        tvDateDisplay = findViewById(R.id.tv_date_display);

        appDrawerOverlay = findViewById(R.id.app_drawer_overlay);
        drawerRecycler = findViewById(R.id.drawer_recycler);

        settingsOverlay = findViewById(R.id.settings_overlay);
        tilesManagerOverlay = findViewById(R.id.tiles_manager_overlay);
        tilesManagerRecycler = findViewById(R.id.tiles_manager_recycler);

        // Центр управления
        controlCenterOverlay = findViewById(R.id.control_center_overlay);
        controlCenterPanel = findViewById(R.id.control_center_panel);
        ccWifiName = findViewById(R.id.cc_wifi_name);
        ccWifiStatus = findViewById(R.id.cc_wifi_status);
        ccVolumeLevel = findViewById(R.id.cc_volume_level);
        ccVolIcon = findViewById(R.id.cc_vol_icon);
        ccSleepTimerVal = findViewById(R.id.cc_sleeptimer_val);

        screensaverOverlay = findViewById(R.id.screensaver_overlay);
        screensaverBg = findViewById(R.id.screensaver_bg);
        saverTextClock = findViewById(R.id.saver_text_clock);
        saverDate = findViewById(R.id.saver_date);

        settingsPreviewIcon = findViewById(R.id.settings_preview_icon);
        settingsPreviewTitle = findViewById(R.id.settings_preview_title);
        settingsPreviewDesc = findViewById(R.id.settings_preview_desc);

        tvSoundVal = findViewById(R.id.tv_setting_sound_val);
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
        initControlCenterListeners();
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
        boolean soundOn = prefs.getBoolean("sfx_enabled", true);
        tvSoundVal.setText(soundOn ? "Включены" : "Выключены");

        int ambientLevel = prefs.getInt("ambient_level", 55);
        if (ambientLevel == 0) {
            tvAmbientVal.setText("Выключена");
            ambientGlow.setVisibility(View.GONE);
        } else {
            ambientGlow.setVisibility(View.VISIBLE);
            ambientGlow.setAlpha(ambientLevel / 100f);
            tvAmbientVal.setText(ambientLevel == 30 ? "Мягкая (30%)" : (ambientLevel == 55 ? "Стандарт (55%)" : "Яркая (80%)"));
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

    private void initControlCenterListeners() {
        controlCenterOverlay.setOnClickListener(v -> closeControlCenter());

        btnOpenControlCenter.setOnClickListener(v -> {
            soundManager.playClick();
            openControlCenter();
        });
        btnOpenControlCenter.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                soundManager.playFocus();
                animateScale(v, 1.12f, 1.12f, dpToPx(8));
                heroTitle.setText("Пункт управления");
            } else {
                animateScale(v, 1.0f, 1.0f, 0f);
            }
        });

        FrameLayout cardPower = findViewById(R.id.cc_card_power);
        cardPower.setOnClickListener(v -> {
            soundManager.playClick();
            openScreensaver();
            closeControlCenter();
        });
        setupCcCardFocus(cardPower);

        FrameLayout cardWifi = findViewById(R.id.cc_card_wifi);
        cardWifi.setOnClickListener(v -> {
            soundManager.playClick();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            }
        });
        setupCcCardFocus(cardWifi);

        FrameLayout cardVol = findViewById(R.id.cc_card_volume);
        cardVol.setOnClickListener(v -> {
            soundManager.playClick();
            if (audioManager != null) {
                int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (cur > 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                } else {
                    int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max / 2, 0);
                }
                updateControlCenterUi();
            }
        });
        setupCcCardFocus(cardVol);

        FrameLayout cardSaver = findViewById(R.id.cc_card_screensaver);
        cardSaver.setOnClickListener(v -> {
            soundManager.playClick();
            closeControlCenter();
            openScreensaver();
        });
        setupCcCardFocus(cardSaver);

        FrameLayout cardTimer = findViewById(R.id.cc_card_sleeptimer);
        cardTimer.setOnClickListener(v -> {
            soundManager.playClick();
            cycleSleepTimer();
        });
        setupCcCardFocus(cardTimer);

        FrameLayout cardSleepMode = findViewById(R.id.cc_card_sleepmode);
        cardSleepMode.setOnClickListener(v -> {
            soundManager.playClick();
            openScreensaver();
            closeControlCenter();
        });
        setupCcCardFocus(cardSleepMode);
    }

    private void setupCcCardFocus(View card) {
        card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                soundManager.playFocus();
                animateScale(v, 1.03f, 1.03f, dpToPx(6));
            } else {
                animateScale(v, 1.0f, 1.0f, 0f);
            }
        });
    }

    private void updateControlCenterUi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm != null ? cm.getActiveNetworkInfo() : null;
            if (netInfo != null && netInfo.isConnected()) {
                if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo winfo = wm != null ? wm.getConnectionInfo() : null;
                    String ssid = (winfo != null && winfo.getSSID() != null) ? winfo.getSSID().replace("\"", "") : "Wi-Fi";
                    if ("<unknown ssid>".equals(ssid)) ssid = "Wi-Fi Подключено";
                    ccWifiName.setText(ssid);
                    ccWifiStatus.setText("Подключено");
                } else if (netInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    ccWifiName.setText("Ethernet");
                    ccWifiStatus.setText("Проводное подключение");
                }
            } else {
                ccWifiName.setText("Нет сети");
                ccWifiStatus.setText("Отключено");
            }
        } catch (Exception ignored) {
            ccWifiName.setText("Wi-Fi сеть");
            ccWifiStatus.setText("Подключено");
        }

        if (audioManager != null) {
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int percent = (int) (((float) current / (float) Math.max(1, max)) * 100);
            ccVolumeLevel.setText(percent + "%");
            if (percent == 0) {
                ccVolIcon.setText("🔇");
                ccVolumeLevel.setTextColor(0xFFFF3B30);
            } else {
                ccVolIcon.setText("🔊");
                ccVolumeLevel.setTextColor(0xFF34C759);
            }
        }

        if (sleepTimerMinutes == 0) {
            ccSleepTimerVal.setText("Выкл");
        } else {
            ccSleepTimerVal.setText(sleepTimerMinutes + " мин");
        }
    }

    private void cycleSleepTimer() {
        if (sleepTimerRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        }

        if (sleepTimerMinutes == 0) sleepTimerMinutes = 30;
        else if (sleepTimerMinutes == 30) sleepTimerMinutes = 60;
        else if (sleepTimerMinutes == 60) sleepTimerMinutes = 90;
        else sleepTimerMinutes = 0;

        updateControlCenterUi();

        if (sleepTimerMinutes > 0) {
            Toast.makeText(this, "Таймер сна: заставка через " + sleepTimerMinutes + " мин", Toast.LENGTH_SHORT).show();
            sleepTimerRunnable = () -> {
                openScreensaver();
                sleepTimerMinutes = 0;
                updateControlCenterUi();
            };
            sleepTimerHandler.postDelayed(sleepTimerRunnable, sleepTimerMinutes * 60 * 1000L);
        } else {
            Toast.makeText(this, "Таймер сна отключён", Toast.LENGTH_SHORT).show();
        }
    }

    private void openControlCenter() {
        if (isJiggleMode) stopJiggleMode();
        updateControlCenterUi();

        controlCenterOverlay.setVisibility(View.VISIBLE);
        controlCenterOverlay.setAlpha(0f);
        controlCenterOverlay.animate().alpha(1f).setDuration(220).start();

        controlCenterPanel.setScaleX(0.85f);
        controlCenterPanel.setScaleY(0.85f);
        controlCenterPanel.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();

        findViewById(R.id.cc_card_power).requestFocus();
    }

    private void closeControlCenter() {
        controlCenterPanel.animate().scaleX(0.85f).scaleY(0.85f).setDuration(200).start();
        controlCenterOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            controlCenterOverlay.setVisibility(View.GONE);
            dockRecycler.requestFocus();
        }).start();
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
        screensaverOverlay.setVisibility(View.VISIBLE);
        screensaverOverlay.setAlpha(0f);
        screensaverOverlay.animate().alpha(1f).setDuration(400).start();
        screensaverOverlay.requestFocus();
    }

    private void closeScreensaver() {
        screensaverOverlay.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            screensaverOverlay.setVisibility(View.GONE);
            dockRecycler.requestFocus();
        }).start();
        resetScreensaverTimer();
    }

    private void initSettingsListeners() {
        btnSettings.setOnClickListener(v -> {
            soundManager.playClick();
            openSettings();
        });
        btnSettings.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                soundManager.playFocus();
                animateScale(v, 1.12f, 1.12f, dpToPx(8));
                heroTitle.setText("Настройки");
            } else {
                animateScale(v, 1.0f, 1.0f, 0f);
            }
        });

        FrameLayout itemTiles = findViewById(R.id.setting_item_tiles);
        TextView tvTiles = findViewById(R.id.tv_setting_tiles_title);
        TextView arrTiles = findViewById(R.id.tv_setting_tiles_arrow);
        itemTiles.setOnClickListener(v -> {
            soundManager.playClick();
            openTilesManager();
        });
        setupSettingFocus(itemTiles, tvTiles, arrTiles, "⊞", "Плитки экрана", "Выберите, какие приложения отображаются в нижней полке Dock, а какие остаются только в каталоге.");

        FrameLayout itemSound = findViewById(R.id.setting_item_sound);
        TextView tvSound = findViewById(R.id.tv_setting_sound_title);
        itemSound.setOnClickListener(v -> {
            soundManager.playClick();
            showSoundPickerTvos();
        });
        setupSettingFocus(itemSound, tvSound, tvSoundVal, "🔊", "Звуки tvOS", "Приятные стеклянные щелчки при листании пультом и мягкий звук открытия программ в стиле Apple TV.");

        FrameLayout itemAmbient = findViewById(R.id.setting_item_ambient);
        TextView tvAmbient = findViewById(R.id.tv_setting_ambient_title);
        itemAmbient.setOnClickListener(v -> {
            soundManager.playClick();
            showAmbientPickerTvos();
        });
        setupSettingFocus(itemAmbient, tvAmbient, tvAmbientVal, "💡", "Эмбиент-подсветка", "Регулировка насыщенности и яркости динамического свечения фона под цвет выбранного приложения.");

        FrameLayout itemSize = findViewById(R.id.setting_item_tiles_size);
        TextView tvSize = findViewById(R.id.tv_setting_size_title);
        itemSize.setOnClickListener(v -> {
            soundManager.playClick();
            showTileSizePickerTvos();
        });
        setupSettingFocus(itemSize, tvSize, tvSizeVal, "🔲", "Размер плиток", "Настройка масштаба всех карточек в полке Dock (Компактный, Стандартный tvOS, Крупный).");

        FrameLayout itemClock = findViewById(R.id.setting_item_clock);
        TextView tvClock = findViewById(R.id.tv_setting_clock_title);
        itemClock.setOnClickListener(v -> {
            soundManager.playClick();
            showClockPickerTvos();
        });
        setupSettingFocus(itemClock, tvClock, tvClockVal, "⏱", "Часы и дата", "Переключение между 24-часовым форматом и 12-часовым режимом с отображением даты и дня недели.");

        FrameLayout itemSaver = findViewById(R.id.setting_item_screensaver);
        TextView tvSaver = findViewById(R.id.tv_setting_saver_title);
        itemSaver.setOnClickListener(v -> {
            soundManager.playClick();
            showScreensaverPickerTvos();
        });
        setupSettingFocus(itemSaver, tvSaver, tvSaverVal, "🕹", "Заставка Aerial", "Таймер автоперехода в режим заставки: скрывает док и оставляет чистый кинематографичный вид с часами.");

        FrameLayout itemAutostart = findViewById(R.id.setting_item_autostart);
        TextView tvAutostart = findViewById(R.id.tv_setting_autostart_title);
        itemAutostart.setOnClickListener(v -> {
            soundManager.playClick();
            showAutostartPickerTvos();
        });
        setupSettingFocus(itemAutostart, tvAutostart, tvAutostartVal, "🚀", "Быстрый запуск", "Приложение по умолчанию, которое открывается автоматически при включении телевизора.");

        FrameLayout itemReset = findViewById(R.id.setting_item_reset_order);
        TextView tvReset = findViewById(R.id.tv_setting_reset_title);
        itemReset.setOnClickListener(v -> {
            soundManager.playClick();
            prefs.edit().remove("pinned_order").apply();
            loadApps();
            Toast.makeText(this, "Порядок ярлыков сброшен", Toast.LENGTH_SHORT).show();
        });
        setupSettingFocus(itemReset, tvReset, null, "↺", "Сбросить порядок", "Возвращает исходный порядок плиток по умолчанию на домашнем экране.");

        FrameLayout itemRestore = findViewById(R.id.setting_item_restore_hidden);
        TextView tvRestore = findViewById(R.id.tv_setting_restore_title);
        itemRestore.setOnClickListener(v -> {
            soundManager.playClick();
            prefs.edit().remove("hidden_apps").apply();
            loadApps();
            Toast.makeText(this, "Все скрытые приложения восстановлены", Toast.LENGTH_SHORT).show();
        });
        setupSettingFocus(itemRestore, tvRestore, null, "👁", "Скрытые программы", "Восстанавливает видимость всех ранее скрытых программ в каталоге и главном меню.");

        FrameLayout itemSys = findViewById(R.id.setting_item_system);
        TextView tvSys = findViewById(R.id.tv_setting_sys_title);
        TextView arrSys = findViewById(R.id.tv_setting_sys_arrow);
        itemSys.setOnClickListener(v -> {
            soundManager.playClick();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {}
        });
        setupSettingFocus(itemSys, tvSys, arrSys, "⚙", "Android TV", "Переход в стандартные системные параметры телевизора.");
    }

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
            soundManager.playClick();
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

    private void showSoundPickerTvos() {
        boolean current = prefs.getBoolean("sfx_enabled", true);
        List<PickerOption> list = new ArrayList<>();
        list.add(new PickerOption("Включить звуковые эффекты", "true", current));
        list.add(new PickerOption("Выключить (беззвучный режим)", "false", !current));

        openTvosPickerDialog("Звуки интерфейса tvOS", list, option -> {
            boolean enabled = Boolean.parseBoolean(option.key);
            soundManager.setEnabled(enabled, this);
            applyPreferencesUi();
        });
    }

    private void showAmbientPickerTvos() {
        int current = prefs.getInt("ambient_level", 55);
        List<PickerOption> list = new ArrayList<>();
        list.add(new PickerOption("Выключить подсветку", "0", current == 0));
        list.add(new PickerOption("Мягкая (30%)", "30", current == 30));
        list.add(new PickerOption("Стандартная (55%)", "55", current == 55));
        list.add(new PickerOption("Яркая неоновая (80%)", "80", current == 80));

        openTvosPickerDialog("Эмбиент-подсветка", list, option -> {
            prefs.edit().putInt("ambient_level", Integer.parseInt(option.key)).apply();
            applyPreferencesUi();
        });
    }

    private void showTileSizePickerTvos() {
        String current = prefs.getString("tiles_size", "normal");
        List<PickerOption> list = new ArrayList<>();
        list.add(new PickerOption("Компактный (7-8 в ряд)", "compact", current.equals("compact")));
        list.add(new PickerOption("Стандарт tvOS (140x82dp)", "normal", current.equals("normal")));
        list.add(new PickerOption("Крупный Focus (165x96dp)", "large", current.equals("large")));

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
                soundManager.playFocus();
                animateScale(v, 1.03f, 1.03f, dpToPx(6));
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
        if (sleepTimerRunnable != null) sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
    }

    @Override
    public void onPackageChanged() {
        runOnUiThread(this::loadApps);
    }

    // --- УПРАВЛЕНИЕ РЕЖИМОМ СОРТИРОВКИ (TVOS JIGGLE MODE) ---

    private void startJiggleMode() {
        if (isJiggleMode) return;
        isJiggleMode = true;
        isDraggingItem = false;
        currentGrabbedPosition = -1;
        jiggleModeStartTime = SystemClock.uptimeMillis();

        soundManager.playClick();
        heroTitle.setText("Редактирование: выберите иконку и нажмите [ОК]");
        dockAdapter.notifyDataSetChanged();
    }

    private void grabItem(int position) {
        if (position < 0 || position >= homeApps.size() - 1) return;
        isDraggingItem = true;
        currentGrabbedPosition = position;
        soundManager.playClick();
        heroTitle.setText("Перемещение: [◀ ▶] двигать, [ОК] закрепить");
        dockAdapter.notifyDataSetChanged();
    }

    private void releaseGrabbedItem() {
        isDraggingItem = false;
        currentGrabbedPosition = -1;
        soundManager.playClick();
        heroTitle.setText("Редактирование: выберите иконку и нажмите [ОК]");
        dockAdapter.notifyDataSetChanged();
    }

    private void stopJiggleMode() {
        if (!isJiggleMode) return;
        isJiggleMode = false;
        isDraggingItem = false;
        currentGrabbedPosition = -1;

        soundManager.playClick();
        List<String> order = new ArrayList<>();
        for (AppItem item : homeApps) {
            if (!item.isActionAllApps) {
                order.add(item.packageName);
            }
        }
        savePinnedOrder(order);

        heroTitle.setText("Apple TV");
        dockAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Порядок ярлыков сохранён", Toast.LENGTH_SHORT).show();
    }

    private void moveItem(int fromPosition, int toPosition) {
        int maxIndex = homeApps.size() - 2;
        if (toPosition < 0 || toPosition > maxIndex || fromPosition == toPosition) return;

        soundManager.playFocus();
        Collections.swap(homeApps, fromPosition, toPosition);
        dockAdapter.notifyItemMoved(fromPosition, toPosition);
        currentGrabbedPosition = toPosition;

        dockRecycler.post(() -> {
            View view = dockRecycler.getLayoutManager().findViewByPosition(toPosition);
            if (view != null) {
                view.requestFocus();
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        resetScreensaverTimer();

        if (screensaverOverlay.getVisibility() == View.VISIBLE) {
            closeScreensaver();
            return true;
        }

        // Закрытие Центра управления по кнопке Назад
        if (controlCenterOverlay.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                soundManager.playClick();
                closeControlCenter();
                return true;
            }
        }

        if (isJiggleMode) {
            if (isDraggingItem) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    moveItem(currentGrabbedPosition, currentGrabbedPosition - 1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    moveItem(currentGrabbedPosition, currentGrabbedPosition + 1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (SystemClock.uptimeMillis() - jiggleModeStartTime > 300L) {
                        releaseGrabbedItem();
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                    releaseGrabbedItem();
                    return true;
                }
            } else {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    stopJiggleMode();
                    return true;
                }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View focused = getCurrentFocus();
            if (focused != null && focused.getParent() == dockRecycler) {
                int pos = dockRecycler.getChildAdapterPosition(focused);
                if (pos >= 0 && pos < homeApps.size()) {
                    showAppContextMenu(homeApps.get(pos), false);
                    return true;
                }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            soundManager.playFocus();
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

    private void setHomeForegroundVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        findViewById(R.id.top_status_pill).setVisibility(visibility);
        findViewById(R.id.hero_title).setVisibility(visibility);
        findViewById(R.id.dock_layout_wrapper).setVisibility(visibility);
    }

    private void openDrawer() {
        if (isJiggleMode) stopJiggleMode();
        setHomeForegroundVisible(false);

        appDrawerOverlay.setVisibility(View.VISIBLE);
        appDrawerOverlay.setAlpha(0f);
        appDrawerOverlay.animate().alpha(1f).setDuration(220).start();
        drawerRecycler.requestFocus();
    }

    private void closeDrawer() {
        appDrawerOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            appDrawerOverlay.setVisibility(View.GONE);
            setHomeForegroundVisible(true);
            dockRecycler.requestFocus();
        }).start();
    }

    private void openSettings() {
        if (isJiggleMode) stopJiggleMode();
        setHomeForegroundVisible(false);

        applyPreferencesUi();
        settingsOverlay.setVisibility(View.VISIBLE);
        settingsOverlay.setAlpha(0f);
        settingsOverlay.animate().alpha(1f).setDuration(220).start();
        findViewById(R.id.setting_item_tiles).requestFocus();
    }

    private void closeSettings() {
        settingsOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            settingsOverlay.setVisibility(View.GONE);
            setHomeForegroundVisible(true);
            dockRecycler.requestFocus();
        }).start();
    }

    private void openTilesManager() {
        tilesAdapter.notifyDataSetChanged();
        tilesManagerOverlay.setVisibility(View.VISIBLE);
        tilesManagerOverlay.setAlpha(0f);
        tilesManagerOverlay.animate().alpha(1f).setDuration(200).start();
        tilesManagerRecycler.requestFocus();
    }

    private void closeTilesManager() {
        tilesManagerOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
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
            int[] colors = getThemeColorsForPackage(pkg, icon);
            int glowColor = getGlowColorForPackage(pkg, icon);
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

        homeApps.add(new AppItem("Все приложения", new int[]{0xFF242430, 0xFF121218}, true));

        dockAdapter.notifyDataSetChanged();
        drawerAdapter.notifyDataSetChanged();
        tilesAdapter.notifyDataSetChanged();
    }

    private void animateAmbientGlow(int targetColor) {
        if (prefs.getInt("ambient_level", 55) == 0) return;

        ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), currentAmbientColor, targetColor);
        colorAnim.setDuration(260);
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

        soundManager.playClick();
        List<String> options = new ArrayList<>();
        if (!fromDrawer) {
            options.add("Переместить на экране (Jiggle Mode)");
            options.add("Убрать с главного экрана");
        } else {
            options.add("Закрепить на главный экран");
        }
        options.add("Скрыть приложение");
        options.add("Удалить с ТВ");

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(item.label)
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
                    soundManager.playClick();
                    String choice = options.get(which);
                    if (choice.equals("Переместить на экране (Jiggle Mode)")) {
                        int index = homeApps.indexOf(item);
                        startJiggleMode();
                        if (index != -1) {
                            grabItem(index);
                        }
                    } else if (choice.equals("Убрать с главного экрана")) {
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

    private int extractDominantColor(Drawable drawable) {
        if (drawable == null) return 0xFF353545;
        try {
            Bitmap bitmap;
            if (drawable instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) drawable).getBitmap();
            } else {
                bitmap = Bitmap.createBitmap(
                        Math.max(1, drawable.getIntrinsicWidth()),
                        Math.max(1, drawable.getIntrinsicHeight()),
                        Bitmap.Config.ARGB_8888
                );
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            if (bitmap != null) {
                Bitmap small = Bitmap.createScaledBitmap(bitmap, 16, 16, false);
                long r = 0, g = 0, b = 0;
                int count = 0;
                for (int x = 2; x < 14; x++) {
                    for (int y = 2; y < 14; y++) {
                        int pixel = small.getPixel(x, y);
                        if (Color.alpha(pixel) > 120) {
                            r += Color.red(pixel);
                            g += Color.green(pixel);
                            b += Color.blue(pixel);
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    return Color.rgb((int)(r / count), (int)(g / count), (int)(b / count));
                }
            }
        } catch (Exception ignored) {}
        return 0xFF2A2E3D;
    }

    private int[] getThemeColorsForPackage(String pkg, Drawable icon) {
        String p = pkg.toLowerCase();
        if (p.contains("youtube") || p.contains("ytplayer")) {
            return new int[]{0xFFFF1438, 0xFF7A0012};
        }
        if (p.contains("kinopoisk")) {
            return new int[]{0xFFFF6600, 0xFF8A1E00};
        }
        if (p.contains("vk") || p.contains("vigo")) {
            return new int[]{0xFF0077FF, 0xFF003882};
        }
        if (p.contains("zona")) {
            return new int[]{0xFF00E676, 0xFF005E28};
        }
        if (p.contains("settings")) {
            return new int[]{0xFF64748B, 0xFF1E293B};
        }
        if (p.contains("file") || p.contains("manager")) {
            return new int[]{0xFF38BDF8, 0xFF0369A1};
        }

        int dom = extractDominantColor(icon);
        int darker = Color.rgb(
                (int)(Color.red(dom) * 0.45f),
                (int)(Color.green(dom) * 0.45f),
                (int)(Color.blue(dom) * 0.45f)
        );
        return new int[]{dom, darker};
    }

    private int getGlowColorForPackage(String pkg, Drawable icon) {
        String p = pkg.toLowerCase();
        if (p.contains("youtube") || p.contains("ytplayer")) return 0xB3FF1438;
        if (p.contains("kinopoisk")) return 0xB3FF6600;
        if (p.contains("vk") || p.contains("vigo")) return 0xB30077FF;
        if (p.contains("zona")) return 0xB300E676;
        if (p.contains("settings")) return 0x9964748B;
        if (p.contains("file") || p.contains("manager")) return 0xB338BDF8;

        int dom = extractDominantColor(icon);
        return Color.argb(160, Color.red(dom), Color.green(dom), Color.blue(dom));
    }

    private int getPosterForPackage(String pkg) {
        String p = pkg.toLowerCase();
        if (p.contains("kinopoisk")) return R.drawable.bg_poster_kinopoisk;
        if (p.contains("youtube") || p.contains("ytplayer")) return R.drawable.bg_poster_youtube;
        if (p.contains("zona")) return R.drawable.bg_poster_zona;
        if (p.contains("vk") || p.contains("vigo")) return R.drawable.bg_poster_vk;
        return R.drawable.bg_poster_studio;
    }

    // --- 1. АДАПТЕР ГЛАВНОГО DOCK ---
    private class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private int getCardWidth() {
            String size = prefs.getString("tiles_size", "normal");
            if ("compact".equals(size)) return dpToPx(120);
            if ("large".equals(size)) return dpToPx(160);
            return dpToPx(140);
        }

        private int getCardHeight() {
            String size = prefs.getString("tiles_size", "normal");
            if ("compact".equals(size)) return dpToPx(72);
            if ("large".equals(size)) return dpToPx(94);
            return dpToPx(82);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(getCardWidth(), getCardHeight());
            lp.setMargins(dpToPx(10), dpToPx(12), dpToPx(10), dpToPx(12));
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);

            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(18));
                }
            });
            card.setClipToOutline(true);

            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(iv, new FrameLayout.LayoutParams(dpToPx(48), dpToPx(48), Gravity.CENTER));

            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(14);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            card.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            View shimmer = new View(parent.getContext());
            GradientDrawable shimmerGrad = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{0x4DFFFFFF, 0x1AFFFFFF, 0x00FFFFFF}
            );
            shimmerGrad.setCornerRadius(dpToPx(18));
            shimmer.setBackground(shimmerGrad);
            shimmer.setAlpha(0f);
            card.addView(shimmer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            return new RecyclerView.ViewHolder(card) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AppItem item = homeApps.get(position);
            FrameLayout card = (FrameLayout) holder.itemView;
            int pos = holder.getBindingAdapterPosition();

            int targetW = getCardWidth();
            int targetH = getCardHeight();
            ViewGroup.LayoutParams clp = card.getLayoutParams();
            if (clp.width != targetW || clp.height != targetH) {
                clp.width = targetW;
                clp.height = targetH;
                card.setLayoutParams(clp);
            }

            ImageView iv = (ImageView) card.getChildAt(0);
            TextView tv = (TextView) card.getChildAt(1);
            View shimmer = card.getChildAt(2);

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
            normalBg.setCornerRadius(dpToPx(18));

            boolean isThisItemGrabbed = (isDraggingItem && pos == currentGrabbedPosition);
            if (isThisItemGrabbed) {
                normalBg.setStroke(dpToPx(4), Color.parseColor("#FFCC00"));
            }
            card.setBackground(normalBg);

            ObjectAnimator jiggleAnim = (ObjectAnimator) card.getTag(R.id.dock_recycler);
            if (isJiggleMode && !item.isActionAllApps) {
                if (jiggleAnim == null) {
                    jiggleAnim = ObjectAnimator.ofFloat(card, "rotation", -1.8f, 1.8f);
                    jiggleAnim.setDuration(120);
                    jiggleAnim.setRepeatMode(ValueAnimator.REVERSE);
                    jiggleAnim.setRepeatCount(ValueAnimator.INFINITE);
                    jiggleAnim.setInterpolator(new LinearInterpolator());
                    card.setTag(R.id.dock_recycler, jiggleAnim);
                    jiggleAnim.start();
                }
            } else {
                if (jiggleAnim != null) {
                    jiggleAnim.cancel();
                    card.setTag(R.id.dock_recycler, null);
                }
                card.setRotation(0f);
            }

            card.setOnFocusChangeListener((v, hasFocus) -> {
                resetScreensaverTimer();
                if (hasFocus) {
                    soundManager.playFocus();
                    currentGrabbedPosition = holder.getBindingAdapterPosition();
                    animateScale(v, isThisItemGrabbed ? 1.22f : 1.15f, isThisItemGrabbed ? 1.22f : 1.15f, dpToPx(14));
                    GradientDrawable focusBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
                    focusBg.setCornerRadius(dpToPx(18));

                    int strokeColor = isThisItemGrabbed ? Color.parseColor("#FFCC00") : (isJiggleMode ? Color.parseColor("#34C759") : Color.WHITE);
                    focusBg.setStroke(dpToPx(isThisItemGrabbed ? 4 : 3), strokeColor);
                    v.setBackground(focusBg);

                    if (!isJiggleMode) {
                        heroTitle.setText(item.label);
                    }
                    heroBanner.setImageResource(item.bannerRes);
                    animateAmbientGlow(item.glowColor);

                    shimmer.setAlpha(0f);
                    shimmer.animate().alpha(1f).setDuration(240).withEndAction(() -> {
                        shimmer.animate().alpha(0.35f).setDuration(400).start();
                    }).start();

                    startParallaxTilt(v, iv);

                } else {
                    stopParallaxTilt(v, iv);
                    shimmer.animate().alpha(0f).setDuration(200).start();
                    animateScale(v, 1.0f, 1.0f, 0f);
                    v.setBackground(normalBg);
                }
            });

            card.setOnClickListener(v -> {
                resetScreensaverTimer();
                if (isJiggleMode) {
                    if (SystemClock.uptimeMillis() - jiggleModeStartTime < 300L) {
                        return;
                    }
                    if (item.isActionAllApps) return;

                    if (isDraggingItem) {
                        releaseGrabbedItem();
                    } else {
                        grabItem(pos);
                    }
                    return;
                }

                soundManager.playClick();
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
                if (item.isActionAllApps) return true;

                startJiggleMode();
                grabItem(pos);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return homeApps.size();
        }
    }

    private void startParallaxTilt(View card, View innerIcon) {
        ObjectAnimator tiltX = ObjectAnimator.ofFloat(card, "rotationX", -1.8f, 1.8f);
        tiltX.setDuration(1600);
        tiltX.setRepeatMode(ValueAnimator.REVERSE);
        tiltX.setRepeatCount(ValueAnimator.INFINITE);
        tiltX.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator tiltY = ObjectAnimator.ofFloat(card, "rotationY", 2.2f, -2.2f);
        tiltY.setDuration(2200);
        tiltY.setRepeatMode(ValueAnimator.REVERSE);
        tiltY.setRepeatCount(ValueAnimator.INFINITE);
        tiltY.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator iconShiftX = ObjectAnimator.ofFloat(innerIcon, "translationX", -2.5f, 2.5f);
        iconShiftX.setDuration(1600);
        iconShiftX.setRepeatMode(ValueAnimator.REVERSE);
        iconShiftX.setRepeatCount(ValueAnimator.INFINITE);
        iconShiftX.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet set = new AnimatorSet();
        set.playTogether(tiltX, tiltY, iconShiftX);
        card.setTag(R.id.ambient_glow, set);
        set.start();
    }

    private void stopParallaxTilt(View card, View innerIcon) {
        AnimatorSet set = (AnimatorSet) card.getTag(R.id.ambient_glow);
        if (set != null) {
            set.cancel();
            card.setTag(R.id.ambient_glow, null);
        }
        card.animate().rotationX(0f).rotationY(0f).setDuration(200).start();
        innerIcon.animate().translationX(0f).translationY(0f).setDuration(200).start();
    }

    // --- 2. АДАПТЕР КАТАЛОГА ВСЕХ ПРИЛОЖЕНИЙ ---
    private class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(dpToPx(156), dpToPx(92));
            lp.setMargins(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);

            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(18));
                }
            });
            card.setClipToOutline(true);

            ImageView iv = new ImageView(parent.getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(iv, new FrameLayout.LayoutParams(dpToPx(44), dpToPx(44), Gravity.CENTER));

            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(12);
            tv.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            tv.setPadding(dpToPx(8), 0, dpToPx(8), dpToPx(8));
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
            normalBg.setCornerRadius(dpToPx(18));
            card.setBackground(normalBg);

            card.setOnFocusChangeListener((v, hasFocus) -> {
                resetScreensaverTimer();
                if (hasFocus) {
                    soundManager.playFocus();
                    animateScale(v, 1.12f, 1.12f, dpToPx(10));
                    GradientDrawable focusBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, item.colors);
                    focusBg.setCornerRadius(dpToPx(18));
                    focusBg.setStroke(dpToPx(3), Color.WHITE);
                    v.setBackground(focusBg);

                    heroBanner.setImageResource(item.bannerRes);
                    animateAmbientGlow(item.glowColor);
                } else {
                    animateScale(v, 1.0f, 1.0f, 0f);
                    v.setBackground(normalBg);
                }
            });

            card.setOnClickListener(v -> {
                resetScreensaverTimer();
                soundManager.playClick();
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

    // --- 3. АДАПТЕР УПРАВЛЕНИЯ ПЛИТКАМИ ---
    private class TilesManagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout row = new FrameLayout(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(56)
            );
            lp.setMargins(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
            row.setLayoutParams(lp);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackgroundResource(R.drawable.settings_item_selector);
            row.setPadding(dpToPx(16), 0, dpToPx(16), 0);

            LinearLayout left = new LinearLayout(parent.getContext());
            left.setOrientation(LinearLayout.HORIZONTAL);
            left.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START
            );
            row.addView(left, leftLp);

            ImageView iv = new ImageView(parent.getContext());
            left.addView(iv, new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)));

            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(14);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            tvLp.setMarginStart(dpToPx(14));
            left.addView(tv, tvLp);

            TextView statusBadge = new TextView(parent.getContext());
            statusBadge.setTextSize(12);
            statusBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            statusBadge.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
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
                    soundManager.playFocus();
                    animateScale(v, 1.02f, 1.02f, dpToPx(6));
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
                soundManager.playClick();
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
            gd.setCornerRadius(dpToPx(10));
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

    // --- 4. АДАПТЕР ОКНА ВЫБОРА TVOS PICKER ---
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
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(52)
            );
            lp.setMargins(0, dpToPx(4), 0, dpToPx(4));
            item.setLayoutParams(lp);
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackgroundResource(R.drawable.settings_item_selector);
            item.setPadding(dpToPx(18), 0, dpToPx(18), 0);

            item.setNextFocusLeftId(item.getId());
            item.setNextFocusRightId(item.getId());

            TextView title = new TextView(parent.getContext());
            title.setTextSize(15);
            title.setTextColor(Color.WHITE);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL
            );
            item.addView(title, tLp);

            TextView check = new TextView(parent.getContext());
            check.setTextSize(17);
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
                    soundManager.playFocus();
                    animateScale(v, 1.02f, 1.02f, dpToPx(6));
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
        set.setDuration(200);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }
}
