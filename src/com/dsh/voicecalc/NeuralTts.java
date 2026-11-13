package com.dsh.voicecalc;

import android.content.Context;
import android.media.MediaPlayer;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 内置神经语音（sherpa-onnx + VITS）：中英文各一套，完全不依赖系统 TTS。
 * 模型放在 assets/tts-en、assets/tts-zh，首次使用时解压到应用目录。
 */
public final class NeuralTts {

    private final Context ctx;
    private OfflineTts en;
    private OfflineTts zh;
    private MediaPlayer mp;

    public NeuralTts(Context c) {
        this.ctx = c.getApplicationContext();
    }

    private static boolean prepared(File dir) {
        return new File(dir, "tokens.txt").exists();
    }

    private File ensure(String assetDir, File dir) {
        if (prepared(dir)) return dir;
        if (!dir.exists()) dir.mkdirs();
        copyAssets(assetDir, dir);
        return dir;
    }

    private void copyAssets(String assetDir, File outDir) {
        try {
            String[] names = ctx.getAssets().list(assetDir);
            if (names == null) return;
            for (String name : names) {
                String childAsset = assetDir + "/" + name;
                File child = new File(outDir, name);
                String[] sub = ctx.getAssets().list(childAsset);
                if (sub != null && sub.length > 0) {
                    if (!child.exists()) child.mkdirs();
                    copyAssets(childAsset, child);
                } else {
                    InputStream in = ctx.getAssets().open(childAsset);
                    OutputStream out = new java.io.FileOutputStream(child);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.close();
                    in.close();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private synchronized OfflineTts get(boolean english) {
        try {
            if (english && en != null) return en;
            if (!english && zh != null) return zh;
            File base = english ? new File(ctx.getFilesDir(), "tts-en")
                    : new File(ctx.getFilesDir(), "tts-zh");
            ensure(english ? "tts-en" : "tts-zh", base);
            File model = null;
            File[] files = base.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith(".onnx")) {
                        model = f;
                        break;
                    }
                }
            }
            if (model == null) return null;
            File tokens = new File(base, "tokens.txt");
            // espeak-ng-data 两个模型共用一份
            File enDir = ensure("tts-en", new File(ctx.getFilesDir(), "tts-en"));
            File dataDir = new File(enDir, "espeak-ng-data");
            OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig(
                    model.getAbsolutePath(), "",
                    tokens.getAbsolutePath(), dataDir.getAbsolutePath(), "",
                    0.667f, 0.8f, 1.0f);
            OfflineTtsModelConfig mc = new OfflineTtsModelConfig(vits, null, null, 2, false, "cpu");
            OfflineTtsConfig cfg = new OfflineTtsConfig(mc, "", "", 1, 0.2f);
            OfflineTts t = new OfflineTts(ctx.getAssets(), cfg);
            if (english) en = t;
            else zh = t;
            return t;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 后台合成并播放；返回 false 表示不可用（调用方可回退） */
    public boolean speak(final String text, final boolean english) {
        if (text == null || text.trim().isEmpty()) return false;
        final String say = text.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OfflineTts t = get(english);
                    if (t == null) return;
                    GeneratedAudio audio = t.generate(say, 0, 1.0f);
                    play(audio.getSamples(), audio.getSampleRate());
                } catch (Throwable ignored) {
                }
            }
        }, "tts").start();
        return true;
    }

    private void play(float[] samples, int rate) {
        if (samples == null || samples.length == 0) return;
        try {
            short[] pcm = new short[samples.length];
            for (int i = 0; i < samples.length; i++) {
                float v = Math.max(-1f, Math.min(1f, samples[i]));
                pcm[i] = (short) (v * 32767);
            }
            // 写成 WAV 交给 MediaPlayer（兼容性最好）
            File f = new File(ctx.getCacheDir(), "say_tts.wav");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            int dataLen = pcm.length * 2;
            fos.write("RIFF".getBytes("US-ASCII"));
            le32(fos, 36 + dataLen);
            fos.write("WAVEfmt ".getBytes("US-ASCII"));
            le32(fos, 16);
            le16(fos, 1);
            le16(fos, 1);
            le32(fos, rate);
            le32(fos, rate * 2);
            le16(fos, 2);
            le16(fos, 16);
            fos.write("data".getBytes("US-ASCII"));
            le32(fos, dataLen);
            for (short v : pcm) {
                fos.write(v & 0xFF);
                fos.write((v >> 8) & 0xFF);
            }
            fos.close();
            stop();
            mp = new MediaPlayer();
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    try {
                        m.release();
                    } catch (Throwable ignored) {
                    }
                    if (mp == m) mp = null;
                }
            });
            mp.prepare();
            mp.start();
        } catch (Throwable ignored) {
        }
    }

    private static void le32(OutputStream o, int v) throws java.io.IOException {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }

    private static void le16(OutputStream o, int v) throws java.io.IOException {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    public void stop() {
        try {
            if (mp != null) {
                mp.stop();
                mp.release();
            }
        } catch (Throwable ignored) {
        }
        mp = null;
    }

    public boolean ready(boolean english) {
        return get(english) != null;
    }
}
