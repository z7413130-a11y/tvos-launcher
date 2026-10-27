package com.appletv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import java.io.File;
import java.io.FileOutputStream;

public class SoundManager {

    private static SoundManager instance;
    private SoundPool soundPool;
    private int soundFocusId = -1;
    private int soundClickId = -1;
    private boolean isEnabled = true;

    private SoundManager(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("tvos_launcher_prefs", Context.MODE_PRIVATE);
        isEnabled = prefs.getBoolean("sfx_enabled", true);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build();

        try {
            File focusWav = new File(context.getCacheDir(), "tvos_focus.wav");
            generateTvosFocusWav(focusWav);
            soundFocusId = soundPool.load(focusWav.getAbsolutePath(), 1);

            File clickWav = new File(context.getCacheDir(), "tvos_click.wav");
            generateTvosClickWav(clickWav);
            soundClickId = soundPool.load(clickWav.getAbsolutePath(), 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context.getApplicationContext());
        }
        return instance;
    }

    public void playFocus() {
        if (isEnabled && soundPool != null && soundFocusId != -1) {
            soundPool.play(soundFocusId, 0.45f, 0.45f, 1, 0, 1.0f);
        }
    }

    public void playClick() {
        if (isEnabled && soundPool != null && soundClickId != -1) {
            soundPool.play(soundClickId, 0.65f, 0.65f, 1, 0, 1.0f);
        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled, Context context) {
        this.isEnabled = enabled;
        SharedPreferences prefs = context.getSharedPreferences("tvos_launcher_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("sfx_enabled", enabled).apply();
    }

    private void generateTvosFocusWav(File outputFile) throws Exception {
        int sampleRate = 44100;
        int numSamples = (int) (sampleRate * 0.028);
        short[] buffer = new short[numSamples];

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            double wave = Math.sin(2.0 * Math.PI * 1450.0 * t) * 0.75 
                        + Math.sin(2.0 * Math.PI * 720.0 * t) * 0.25;
            double envelope = Math.exp(-t * 220.0);
            buffer[i] = (short) (wave * envelope * Short.MAX_VALUE * 0.85);
        }
        writeWavFile(outputFile, buffer, sampleRate);
    }

    private void generateTvosClickWav(File outputFile) throws Exception {
        int sampleRate = 44100;
        int numSamples = (int) (sampleRate * 0.055);
        short[] buffer = new short[numSamples];

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            double freq = 980.0 - (t * 6000.0);
            if (freq < 300) freq = 300;
            double wave = Math.sin(2.0 * Math.PI * freq * t);
            double envelope = Math.exp(-t * 85.0);
            buffer[i] = (short) (wave * envelope * Short.MAX_VALUE * 0.85);
        }
        writeWavFile(outputFile, buffer, sampleRate);
    }

    private void writeWavFile(File file, short[] samples, int sampleRate) throws Exception {
        int byteRate = sampleRate * 2;
        int dataSize = samples.length * 2;
        int totalSize = 36 + dataSize;

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(new byte[]{'R', 'I', 'F', 'F'});
            fos.write(intToByteArray(totalSize));
            fos.write(new byte[]{'W', 'A', 'V', 'E'});

            fos.write(new byte[]{'f', 'm', 't', ' '});
            fos.write(intToByteArray(16));
            fos.write(shortToByteArray((short) 1));
            fos.write(shortToByteArray((short) 1));
            fos.write(intToByteArray(sampleRate));
            fos.write(intToByteArray(byteRate));
            fos.write(shortToByteArray((short) 2));
            fos.write(shortToByteArray((short) 16));

            fos.write(new byte[]{'d', 'a', 't', 'a'});
            fos.write(intToByteArray(dataSize));

            byte[] pcmData = new byte[dataSize];
            for (int i = 0; i < samples.length; i++) {
                pcmData[i * 2] = (byte) (samples[i] & 0xFF);
                pcmData[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
            }
            fos.write(pcmData);
        }
    }

    private byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    private byte[] shortToByteArray(short value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF)
        };
    }
}
