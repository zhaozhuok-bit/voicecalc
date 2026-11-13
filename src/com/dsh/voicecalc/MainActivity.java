package com.dsh.voicecalc;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity implements View.OnClickListener, RecognitionListener {

    private TextView tvExpr, tvResult, tvStatus, btnMic, tvPrev;
    private SpeechRecognizer recognizer;
    private Intent recIntent;
    private TextToSpeech tts;
    private boolean listening;
    private String recService = "";

    private org.vosk.Model vModel;
    private org.vosk.Recognizer vRec;
    private AudioRecord vAudio;
    private volatile boolean vListening;
    private volatile String vPartial = "";
    private static final String GRAMMAR_EN = "[\"zero\",\"one\",\"two\",\"three\",\"four\",\"five\","
            + "\"six\",\"seven\",\"eight\",\"nine\",\"ten\",\"eleven\",\"twelve\",\"thirteen\","
            + "\"fourteen\",\"fifteen\",\"sixteen\",\"seventeen\",\"eighteen\",\"nineteen\","
            + "\"twenty\",\"thirty\",\"forty\",\"fifty\",\"sixty\",\"seventy\",\"eighty\",\"ninety\","
            + "\"hundred\",\"thousand\",\"million\",\"point\",\"plus\",\"minus\",\"times\","
            + "\"divided\",\"by\",\"over\",\"equals\",\"percent\",\"and\",\"a\"]";

    private static final String GRAMMAR = "[\"零\",\"一\",\"二\",\"三\",\"四\",\"五\",\"六\",\"七\",\"八\",\"九\",\"十\",\"百\",\"千\",\"万\",\"亿\",\"点\",\"加\",\"减\",\"乘\",\"除\",\"以\",\"等\",\"于\",\"多\",\"少\",\"负\",\"分\",\"之\",\"的\"]";
    private final StringBuilder input = new StringBuilder();
    private boolean justEvaluated = false;
    private final java.util.ArrayList<String> recentCalcs = new java.util.ArrayList<>();
    private float density;
    private ToneGenerator toneGen;
    private HistoryDb hist;
    private TextView btnHist;
    private TextView btnAbout;
    private TextView btnLang;
    private boolean langEn = false;
    private boolean vModelLang = false;
    private boolean warnedNoEnTts = false;

    private String S(String zh, String en) {
        return langEn ? en : zh;
    }

    private void saveLang() {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("lang_en", langEn).apply();
    }
    private final HashMap<String, TextView> keyViews = new HashMap<>();
    private LinearLayout keypad;
    private TextView btnSound;
    private boolean soundOn = true;

    private static final String PREF = "voicecalc";
    private static final int REQ_AUDIO = 7;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        soundOn = getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("sound", true);
        density = getResources().getDisplayMetrics().density;
        setContentView(buildUi());
        updateSoundUi();
        langEn = getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("lang_en", false);
        hist = new HistoryDb(this);
        hist.prune();
        initTone();
    }

    private void initTone() {
        try {
            toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 75);
        } catch (Throwable ignored) {
            toneGen = null;
        }
    }

    /** 按键人声：数字读成中文数字，符号读名称；关掉开关就完全安静 */
    private void speakKey(String label) {
        if (!soundOn || label == null || label.isEmpty()) return;
        char c = label.charAt(0);
        if (langEn) {
            String w = null;
            if (label.length() == 1 && c >= '0' && c <= '9') {
                w = new String[]{"zero", "one", "two", "three", "four",
                        "five", "six", "seven", "eight", "nine"}[c - '0'];
            } else {
                switch (label) {
                    case ".": w = "point"; break;
                    case "+": w = "plus"; break;
                    case "−": w = "minus"; break;
                    case "×": w = "times"; break;
                    case "÷": w = "divided by"; break;
                    case "%": w = "percent"; break;
                    case "C": w = "clear"; break;
                    case "⌫": w = "delete"; break;
                    default: break;
                }
            }
            if (w != null) speakEn(w);
            return;
        }
        String say = null;
        if (label.length() == 1 && c >= '0' && c <= '9') {
            say = String.valueOf("零一二三四五六七八九".charAt(c - '0'));
        } else {
            switch (label) {
                case ".": say = "点"; break;
                case "+": say = "加"; break;
                case "−": say = "减"; break;
                case "×": say = "乘"; break;
                case "÷": say = "除"; break;
                case "%": say = "百分之"; break;
                case "C": say = "清空"; break;
                case "⌫": say = "退格"; break;
                default: return;
            }
        }
        playEn(ZhSpeech.ids(say));
    }

    private void updateSoundUi() {
        if (btnSound == null) return;
        boolean land = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        btnSound.setText(land
                ? S(soundOn ? "人声" : "静音", soundOn ? "Snd" : "Mute")
                : S(soundOn ? "🔊 人声：开" : "🔇 人声：关",
                    soundOn ? "🔊 Sound: on" : "🔇 Sound: off"));
        btnSound.setTextColor(soundOn ? 0xFF3BE0C8 : 0xFF8A8AA3);
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    private View buildUi() {
        boolean land = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF05080D);
        root.setPadding(dp(16), dp(16), dp(16), dp(12));

        // 顶栏（仅竖屏；横屏不要标题）
        LinearLayout topBar = null;
        if (!land) {
            topBar = new LinearLayout(this);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);

            View spacer = new View(this);
            topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        }

        btnLang = new TextView(this);
        btnLang.setText(langEn ? "中" : "EN");
        btnLang.setTag("LANG");
        btnLang.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnLang.setPadding(dp(12), dp(7), dp(12), dp(7));
        btnLang.setBackgroundResource(R.drawable.bg_key);
        btnLang.setTextColor(0xFF37F5D8);
        btnLang.setClickable(true);
        btnLang.setOnClickListener(this);

        btnAbout = new TextView(this);
        btnAbout.setText(S("关于", langEn && getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ? "Info" : "About"));
        btnAbout.setTag("ABOUT");
        btnAbout.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnAbout.setPadding(dp(12), dp(7), dp(12), dp(7));
        btnAbout.setBackgroundResource(R.drawable.bg_key);
        btnAbout.setTextColor(0xFF37F5D8);
        btnAbout.setClickable(true);
        btnAbout.setOnClickListener(this);

        btnHist = new TextView(this);
        btnHist.setText(S("记录", getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ? "Log" : "History"));
        btnHist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnHist.setPadding(dp(12), dp(7), dp(12), dp(7));
        btnHist.setBackgroundResource(R.drawable.bg_key);
        btnHist.setTextColor(0xFF37F5D8);
        btnHist.setClickable(true);
        btnHist.setTag("HIST");
        btnHist.setOnClickListener(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.rightMargin = dp(8);
        if (topBar != null) {
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.rightMargin = dp(8);
            topBar.addView(btnLang, llp);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.rightMargin = dp(8);
            topBar.addView(btnAbout, alp);
            topBar.addView(btnHist, hlp);
        }

        btnSound = new TextView(this);
        btnSound.setTag("SOUND");
        btnSound.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnSound.setPadding(dp(12), dp(7), dp(12), dp(7));
        btnSound.setBackgroundResource(R.drawable.bg_key);
        btnSound.setClickable(true);
        btnSound.setOnClickListener(this);
        if (topBar != null) {
            topBar.addView(btnSound, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // 显示屏
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_display);
        card.setPadding(dp(20), dp(land ? 10 : 16), dp(20), dp(land ? 10 : 16));

        tvPrev = new TextView(this);
        tvPrev.setTextColor(0xFF3E6570);
        tvPrev.setTextSize(TypedValue.COMPLEX_UNIT_SP, land ? 10 : 12);
        tvPrev.setGravity(Gravity.END);
        tvPrev.setSingleLine(true);
        tvPrev.setEllipsize(android.text.TextUtils.TruncateAt.START);
        tvPrev.setPadding(0, 0, 0, dp(2));
        card.addView(tvPrev, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvExpr = new TextView(this);
        tvExpr.setTextColor(0xFFBFEFE7);
        tvExpr.setTextSize(TypedValue.COMPLEX_UNIT_SP, land ? 20 : 28);
        tvExpr.setGravity(Gravity.END);
        tvExpr.setSingleLine(true);
        tvExpr.setEllipsize(android.text.TextUtils.TruncateAt.START);
        card.addView(tvExpr, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvResult = new TextView(this);
        tvResult.setText("0");
        tvResult.setTextColor(0xFFFFFFFF);
        tvResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, land ? 34 : 50);
        tvResult.setTypeface(Typeface.DEFAULT_BOLD);
        tvResult.setGravity(Gravity.END);
        tvResult.setSingleLine(true);
        tvResult.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvResult.setPadding(0, dp(6), 0, 0);
        card.addView(tvResult, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 麦克风
        btnMic = new TextView(this);
        btnMic.setText(S("🎤  说话算一算", "🎤  Tap and speak"));
        btnMic.setTextColor(0xFFFFFFFF);
        btnMic.setTextSize(TypedValue.COMPLEX_UNIT_SP, land ? 15 : 19);
        btnMic.setTypeface(Typeface.DEFAULT_BOLD);
        btnMic.setGravity(Gravity.CENTER);
        btnMic.setBackgroundResource(R.drawable.bg_mic);
        btnMic.setClickable(true);
        btnMic.setOnClickListener(this);
        btnMic.setTag("MIC");
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(land ? 54 : 62));
        micLp.topMargin = dp(10);

        // 状态
        tvStatus = new TextView(this);
        tvStatus.setText(S("例：三十二加五十八 · 十五乘以四 · 百分之二十", "e.g. 三十二加五十八 (Chinese numerals)"));
        tvStatus.setTextColor(0xFF3E6570);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setSingleLine(true);
        tvStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = dp(8);

        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.bottomMargin = dp(10);

        if (!land) {
            // 竖屏：一个完整键盘
            LinearLayout pad = new LinearLayout(this);
            pad.setOrientation(LinearLayout.VERTICAL);
            String[][] rows = {
                    {"C", "⌫", "%", "÷"},
                    {"7", "8", "9", "×"},
                    {"4", "5", "6", "−"},
                    {"1", "2", "3", "+"}
            };
            for (String[] r : rows) {
                LinearLayout row = newRow();
                for (String k : r) addKey(row, k, 1f);
                pad.addView(row, rowLp());
            }
            LinearLayout lastRow = newRow();
            addKey(lastRow, "0", 1f);
            addKey(lastRow, ".", 1f);
            addKey(lastRow, "=", 2f);
            pad.addView(lastRow, rowLp());

            root.addView(topBar, topLp);
            root.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams kpLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            kpLp.topMargin = dp(10);
            root.addView(pad, kpLp);
            root.addView(btnMic, micLp);
            root.addView(tvStatus, stLp);
        } else {
            // 横屏：左＝数字框 + 运算键；右＝数字 + 底行(退格/百分号/语音)
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout opsGrid = new LinearLayout(this);
            opsGrid.setOrientation(LinearLayout.HORIZONTAL);

            // 左列：清空 在上，大 + 在下
            LinearLayout col1 = new LinearLayout(this);
            col1.setOrientation(LinearLayout.VERTICAL);
            LinearLayout rowC = newRow();
            addKey(rowC, "C", 1f);
            col1.addView(rowC, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            LinearLayout rowPlus = newRow();
            addKey(rowPlus, "+", 1f);
            col1.addView(rowPlus, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));

            // 右列：减 乘 除
            LinearLayout col2 = new LinearLayout(this);
            col2.setOrientation(LinearLayout.VERTICAL);
            String[] ops = {"−", "×", "÷"};
            for (String op : ops) {
                LinearLayout r = newRow();
                addKey(r, op, 1f);
                col2.addView(r, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            }

            opsGrid.addView(col1, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            opsGrid.addView(col2, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            gridLp.topMargin = dp(10);
            left.addView(opsGrid, gridLp);

            // 右列：数字 + 底部一行
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);

            LinearLayout numPad = new LinearLayout(this);
            numPad.setOrientation(LinearLayout.VERTICAL);
            String[][] nums = {{"7", "8", "9"}, {"4", "5", "6"}, {"1", "2", "3"}, {"0", ".", "="}};
            for (String[] rowKeys : nums) {
                LinearLayout row = newRow();
                for (String k : rowKeys) addKey(row, k, 1f);
                numPad.addView(row, rowLp());
            }
            right.addView(numPad, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout bottomRow = newRow();
            LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 0.55f);
            keyLp.leftMargin = dp(5);
            keyLp.rightMargin = dp(5);
            attrKeyStyle(btnHist);
            attrKeyStyle(btnSound);
            attrKeyStyle(btnLang);
            LinearLayout.LayoutParams lgLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 0.5f);
            lgLp.leftMargin = dp(5);
            lgLp.rightMargin = dp(5);
            bottomRow.addView(btnLang, lgLp);
            attrKeyStyle(btnAbout);
            LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 0.55f);
            abLp.leftMargin = dp(5);
            abLp.rightMargin = dp(5);
            bottomRow.addView(btnAbout, abLp);
            bottomRow.addView(btnHist, keyLp);
            LinearLayout.LayoutParams keyLp2 = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 0.55f);
            keyLp2.leftMargin = dp(5);
            keyLp2.rightMargin = dp(5);
            bottomRow.addView(btnSound, keyLp2);
            addKey(bottomRow, "⌫", 0.95f);
            addKey(bottomRow, "%", 0.9f);

            btnMic.setText(S("🎤 说话", "🎤 Speak"));
            LinearLayout.LayoutParams micLp2 = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1.7f);
            micLp2.leftMargin = dp(5);
            micLp2.rightMargin = dp(5);
            bottomRow.addView(btnMic, micLp2);

            LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            brLp.topMargin = dp(8);
            right.addView(bottomRow, brLp);

            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            leftLp.rightMargin = dp(12);
            root.addView(left, leftLp);
            root.addView(right, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1.15f));
        }
        return root;
    }

    private LinearLayout newRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(8);
        return lp;
    }

    private void addKey(LinearLayout row, String label, float weight) {
        TextView key = new TextView(this);
        key.setText(label);
        key.setGravity(Gravity.CENTER);
        key.setTag(label);
        key.setOnClickListener(this);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        boolean isEq = "=".equals(label);
        boolean isOp = "+-×÷%".contains(label) || "C".equals(label);
        key.setTextColor(isEq ? 0xFF04121A : (isOp ? 0xFF37F5D8 : 0xFFDFF7F2));
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, isEq ? 26 : 22);
        key.setBackgroundResource(isEq ? R.drawable.bg_key_eq
                : (isOp ? R.drawable.bg_key_op : R.drawable.bg_key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.leftMargin = dp(5);
        lp.rightMargin = dp(5);
        row.addView(key, lp);
        keyViews.put(label, key);
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        String expr = input.toString();
        String res = tvResult != null ? tvResult.getText().toString() : "0";
        String st = tvStatus != null ? tvStatus.getText().toString() : "";
        keyViews.clear();
        setContentView(buildUi());
        updateSoundUi();
        input.setLength(0);
        input.append(expr);
        tvExpr.setText(pretty(expr));
        tvResult.setText(res);
        tvStatus.setText(st);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String label = mapKey(keyCode, event);
        if (label != null) {
            speakKey(label);
            flash(label);
            performKey(label);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** USB / 蓝牙键盘按键映射（区分 Shift，避免 = 与 + 冲突） */
    private String mapKey(int code, KeyEvent e) {
        boolean shift = e.isShiftPressed();

        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            if (shift) {
                if (code == KeyEvent.KEYCODE_5) return "%";
                if (code == KeyEvent.KEYCODE_8) return "×";
            }
            return String.valueOf((char) ('0' + (code - KeyEvent.KEYCODE_0)));
        }
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
            return String.valueOf((char) ('0' + (code - KeyEvent.KEYCODE_NUMPAD_0)));
        }
        switch (code) {
            case KeyEvent.KEYCODE_NUMPAD_ADD:
            case KeyEvent.KEYCODE_PLUS:
                return "+";
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
            case KeyEvent.KEYCODE_MINUS:
                return "−";
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
            case KeyEvent.KEYCODE_STAR:
                return "×";
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE:
            case KeyEvent.KEYCODE_SLASH:
                return "÷";
            case KeyEvent.KEYCODE_NUMPAD_DOT:
            case KeyEvent.KEYCODE_PERIOD:
                return ".";
            case KeyEvent.KEYCODE_EQUALS:
                return shift ? "+" : "=";
            case KeyEvent.KEYCODE_NUMPAD_EQUALS:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return "=";
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return "⌫";
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_CLEAR:
                return "C";
            default:
                return null;
        }
    }

    private void attrKeyStyle(TextView v) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        v.setSingleLine(true);
        v.setPadding(0, 0, 0, 0);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundResource(R.drawable.bg_key);
    }

    private void flash(final String label) {
        final TextView v = keyViews.get(label);
        if (v == null) return;
        v.setBackgroundResource(R.drawable.bg_key_active);
        v.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean isEq = "=".equals(label);
                boolean isOp = "+-×÷%".contains(label) || "C".equals(label);
                v.setBackgroundResource(isEq ? R.drawable.bg_key_eq
                        : (isOp ? R.drawable.bg_key_op : R.drawable.bg_key));
            }
        }, 130);
    }

    @Override
    public void onClick(View v) {
        String k = (String) v.getTag();
        if (k == null) return;
        if ("LANG".equals(k)) {
            langEn = !langEn;
            saveLang();
            stopEn();
            try {
                if (tts != null) tts.stop();
            } catch (Throwable ignored) {
            }
            if (vListening) stopVosk();
            if (vModel != null) {
                try {
                    vModel.close();
                } catch (Throwable ignored) {
                }
                vModel = null;
                vRec = null;
            }
            String keep = input.toString();
            String res = tvResult != null ? tvResult.getText().toString() : "0";
            keyViews.clear();
            setContentView(buildUi());
            updateSoundUi();
            input.setLength(0);
            input.append(keep);
            tvExpr.setText(pretty(keep));
            setResultText(res);
            return;
        }
        if ("ABOUT".equals(k)) {
            showAbout();
            return;
        }
        if ("HIST".equals(k)) {
            showHistory();
            return;
        }
        if ("SOUND".equals(k)) {
            soundOn = !soundOn;
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("sound", soundOn).apply();
            if (!soundOn && tts != null) {
                try {
                    tts.stop();
                } catch (Throwable ignored) {
                }
            }
            updateSoundUi();
            if (soundOn) {
                playEn(langEn ? EnSpeech.ids("one") : ZhSpeech.ids("一"));
            }
            return;
        }
        if ("MIC".equals(k)) {
            if (toneGen != null && soundOn) {
                try {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 90);
                } catch (Throwable ignored) {
                }
            }
            if (vListening) stopVosk();
            else startListen();
            return;
        }
        speakKey(k);
        flash(k);
        performKey(k);
    }

    private void performKey(String k) {
        switch (k) {
            case "C":
                input.setLength(0);
                justEvaluated = false;
                tvExpr.setText("");
                setResultText("0");
                break;
            case "⌫":
                if (justEvaluated) {
                    input.setLength(0);
                    justEvaluated = false;
                } else if (input.length() > 0) {
                    input.deleteCharAt(input.length() - 1);
                }
                refreshDisplay();
                break;
            case "=":
                evaluate();
                break;
            case "÷":
                chainOp("/");
                break;
            case "×":
                chainOp("*");
                break;
            case "−":
                chainOp("-");
                break;
            case "+":
                chainOp("+");
                break;
            case "%":
                chainOp("/100");
                break;
            default:
                if (justEvaluated) {
                    if (tvPrev != null) {
                        String prev = tvExpr.getText().toString();
                        String pr = tvResult.getText().toString();
                        if (!prev.isEmpty()) {
                            recentCalcs.add(prev + " = " + pr);
                            while (recentCalcs.size() > 4) recentCalcs.remove(0);
                            StringBuilder b = new StringBuilder();
                            for (int i = 0; i < recentCalcs.size(); i++) {
                                if (i > 0) b.append("   ");
                                b.append(recentCalcs.get(i));
                            }
                            tvPrev.setText(b.toString());
                        }
                    }
                    input.setLength(0);
                    justEvaluated = false;
                }
                input.append(k);
                refreshDisplay();
                break;
        }
    }

    /** 出结果后按运算符：从结果继续算 */
    private void chainOp(String op) {
        if (justEvaluated) {
            String r = tvResult.getText().toString();
            input.setLength(0);
            try {
                Double.parseDouble(r);
                input.append(r);
            } catch (Exception ignored) {
            }
            justEvaluated = false;
        }
        input.append(op);
        refreshDisplay();
    }

    private String pretty(String s) {
        return s.replace("*", "×").replace("/", "÷").replace("-", "−");
    }

    /** 大号数字：按长度自动缩放，避免出格 */
    private void setResultText(String text) {
        boolean land = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        float base = land ? 34f : 50f;
        float sp = text.length() <= 8 ? base : Math.max(14f, base * 8f / text.length());
        tvResult.setText(text);
        tvResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    /** 大号数字显示当前正在输入的运算数 */
    private void refreshDisplay() {
        String s = input.toString();
        if (s.isEmpty()) {
            setResultText("0");
            tvExpr.setText("");
            return;
        }
        int cut = -1;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '(' || c == ')') {
                cut = i;
                break;
            }
        }
        String operand = s.substring(cut + 1);
        if (operand.isEmpty()) {
            setResultText("0");
        } else {
            setResultText(operand);
        }
        tvExpr.setText(pretty(s));
    }

    private static final String ZH_ABOUT =
            "语音计算器  v" + "$V" + "\n\n"
            + "一个完全离线的中文语音计算器。说「三十二加五十八」就能算出结果，"
            + "识别全部在本机完成，不上传任何语音，也不需要联网。\n\n"
            + "主要功能\n"
            + "· 离线语音识别：说完停顿即自动计算并朗读结果\n"
            + "· 连续计算：出结果后按数字＝新算，按运算符＝接着算\n"
            + "· 计算记录：自动保存最近 30 天，可点击回填\n"
            + "· 横竖屏自适应：横屏左右手分工，单手也好按\n"
            + "· 外接键盘：USB / 蓝牙键盘按键映射，屏幕同步高亮\n"
            + "· 按键人声可开关，设置自动记忆\n\n"
            + "技术说明\n"
            + "界面为原生 Android 实现；语音识别使用开源的 Vosk 引擎与中文小模型，"
            + "全部离线运行。\n\n"
            + "免责声明\n"
            + "1. 本软件完全免费，不以任何形式收费，也不收集、上传或出售用户数据。\n"
            + "2. 软件按「现状」提供，不对计算或识别结果的准确性作任何担保。"
            + "因使用本软件造成的任何直接或间接损失，由使用者自行承担。\n"
            + "3. 软件中涉及的第三方组件（包括但不限于 Vosk 语音识别引擎及其声学模型），"
            + "版权归各自作者所有，遵循其原有的开源许可协议。\n"
            + "4. 若本软件的任何内容侵犯了您的版权或其他协议权益，请通过下方邮箱联系我们，"
            + "我们会在核实后立即删除或修正。\n\n"
            + "联系方式\n"
            + "邮箱：Zhaozhuok@vip.qq.com\n\n"
            + "开源地址\n"
            + "github.com/zhaozhuok-bit/voicecalc";

    private static final String EN_ABOUT =
            "Voice Calculator  v" + "$V" + "\n\n"
            + "A fully offline Chinese speech calculator. Say \"san shi er jia wu shi ba\" "
            + "and it gives you the answer. Recognition runs entirely on the device - "
            + "no audio is uploaded, no network needed.\n\n"
            + "Features\n"
            + "- Offline speech: pause after speaking, it stops, computes and reads the result\n"
            + "- Chained calculation: after a result, digits start a new one, an operator continues\n"
            + "- History: the last 30 days are kept, tap an entry to reuse it\n"
            + "- Portrait and landscape: in landscape the left hand gets the display and operators\n"
            + "- External keyboards: USB / Bluetooth keys map to the on-screen keys\n"
            + "- Spoken key feedback can be turned off and is remembered\n\n"
            + "Notes\n"
            + "Native Android UI, with the open-source Vosk engine and a small Chinese model, "
            + "all running offline. Speech input understands Chinese numerals.\n\n"
            + "Disclaimer\n"
            + "1. This software is completely free. It does not charge any fee and does not "
            + "collect, upload or sell user data.\n"
            + "2. It is provided \"as is\", without any warranty on the accuracy of results. "
            + "Any loss caused by using it is borne by the user.\n"
            + "3. Third-party components (including but not limited to the Vosk speech engine and "
            + "its acoustic model) remain the property of their authors and follow their licenses.\n"
            + "4. If any content infringes your copyright or license terms, please contact us "
            + "and we will remove or correct it promptly.\n\n"
            + "Contact\n"
            + "Email: Zhaozhuok@vip.qq.com\n\n"
            + "Source\n"
            + "github.com/zhaozhuok-bit/voicecalc";

    private void showAbout() {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setPadding(dp(18), dp(10), dp(18), dp(10));
        tv.setTextColor(0xFFCFE9E4);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setLineSpacing(dp(3), 1f);
        tv.setText((langEn ? EN_ABOUT : ZH_ABOUT).replace("$V", versionName()));
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle(S("关于 / 免责声明", "About / Disclaimer"))
                .setView(sv)
                .setPositiveButton(S("知道了", "OK"), null)
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "3.8";
        }
    }

    private void showHistory() {
        java.util.List<String[]> rows = hist.recent(200);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), dp(4));

        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(S("还没有记录\n算完会自动存这里，保留 30 天", "No history yet\nResults are kept here for 30 days"));
            empty.setTextColor(0xFF4E7F8C);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            box.addView(empty);
        } else {
            ScrollView sv = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            final java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm",
                    java.util.Locale.CHINA);
            for (final String[] r : rows) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setBackgroundResource(R.drawable.bg_hist_row);
                row.setPadding(dp(12), dp(9), dp(12), dp(9));
                row.setClickable(true);

                TextView main = new TextView(this);
                main.setText(r[0] + " = " + r[1]);
                main.setTextColor(0xFFDFF7F2);
                main.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                main.setSingleLine(true);
                main.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(main);

                TextView time = new TextView(this);
                time.setText(fmt.format(new java.util.Date(Long.parseLong(r[2]))));
                time.setTextColor(0xFF3E6570);
                time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                row.addView(time);

                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        input.setLength(0);
                        input.append(r[0].replace("×", "*").replace("÷", "/").replace("−", "-"));
                        tvExpr.setText(r[0]);
                        tvResult.setText(r[1]);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(6);
                list.addView(row, lp);
            }
            sv.addView(list);
            box.addView(sv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
        }

        new AlertDialog.Builder(this)
                .setTitle(S("计算记录（30 天）", "History (30 days)"))
                .setView(box)
                .setPositiveButton(S("关闭", "Close"), null)
                .setNeutralButton(S("清空", "Clear"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dlg, int w) {
                        hist.clear();
                    }
                })
                .show();
    }

    private void evaluate() {
        String e = input.toString().trim();
        if (e.isEmpty()) return;
        try {
            double v = Calc.eval(e);
            showResult(v);
            speak(langEn ? ("equals " + Calc.fmt(v)) : ("等于" + ZhSpeech.words(v)));
            hist.add(pretty(e), Calc.fmt(v));
            justEvaluated = true;
        } catch (ArithmeticException ex) {
            justEvaluated = false;
            tvResult.setText(S("不能除以零", "Divide by zero"));
            tvResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        } catch (Exception ex) {
            justEvaluated = false;
            tvResult.setText(S("算式有误", "Invalid expression"));
            tvResult.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        }
    }

    private void showResult(double v) {
        String s = Calc.fmt(v);
        setResultText(s);
    }

    // ---------------- 语音 ----------------


    /** 主动在系统里找可用的 RecognitionService */
    private ComponentName pickRecognizer() {
        try {
            java.util.List<android.content.pm.ResolveInfo> list =
                    getPackageManager().queryIntentServices(
                            new Intent("android.speech.RecognitionService"), 0);
            if (list != null && !list.isEmpty()) {
                android.content.pm.ResolveInfo ri = list.get(0);
                if (ri.serviceInfo != null) {
                    return new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String describeRecognizers() {
        StringBuilder b = new StringBuilder();
        try {
            java.util.List<android.content.pm.ResolveInfo> list =
                    getPackageManager().queryIntentServices(
                            new Intent("android.speech.RecognitionService"), 0);
            if (list == null || list.isEmpty()) return "系统里没有任何语音识别服务";
            for (android.content.pm.ResolveInfo ri : list) {
                if (ri.serviceInfo != null) b.append(ri.serviceInfo.packageName).append("\n");
            }
        } catch (Throwable t) {
            return "查询失败";
        }
        return b.toString().trim();
    }

    private void startListen() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startVosk();
    }

    // ---------------- 离线语音识别（Vosk + 中文小模型） ----------------

    private void startVosk() {
        if (vListening) {
            stopVosk();
            return;
        }
        if (vModel != null && vRec != null) {
            startMic();
            return;
        }
        tvStatus.setText(S("正在准备语音模型（首次约需几秒）…", "Preparing speech model (a few seconds)..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String assetName = langEn ? "model_en" : "model";
                    File dir = new File(getFilesDir(), assetName);
                    if (!new File(dir, "am/final.mdl").exists()) {
                        copyAssets(assetName, dir);
                    }
                    vModel = new org.vosk.Model(dir.getAbsolutePath());
                    vRec = new org.vosk.Recognizer(vModel, 16000f, langEn ? GRAMMAR_EN : GRAMMAR);
                    vModelLang = langEn;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startMic();
                        }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText(S("语音模型加载失败：", "Speech model failed: ") + t);
                        }
                    });
                }
            }
        }).start();
    }

    private void copyAssets(String from, File to) throws Exception {
        String[] list = getAssets().list(from);
        if (list == null) return;
        if (!to.exists()) to.mkdirs();
        for (String name : list) {
            String path = from.isEmpty() ? name : from + "/" + name;
            String[] sub = getAssets().list(path);
            File target = new File(to, name);
            if (sub != null && sub.length > 0) {
                copyAssets(path, target);
            } else {
                InputStream in = getAssets().open(path);
                OutputStream out = new java.io.FileOutputStream(target);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
            }
        }
    }

    private void startMic() {
        try {
            int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            vAudio = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min * 2, 8192));
            vAudio.startRecording();
            vListening = true;
            vPartial = "";
            btnMic.setText(S("🎤  正在听…（说完自动结束）", "🎤  Listening... (auto-stop)"));
            btnMic.setBackgroundResource(R.drawable.bg_mic_active);
            tvStatus.setText(S("请说，例如：三十二加五十八，说完会自动计算", "Say a calculation, e.g. 三十二加五十八"));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buf = new byte[3200];
                    boolean heardAny = false;
                    long started = System.currentTimeMillis();
                    while (vListening) {
                        int n;
                        try {
                            n = vAudio.read(buf, 0, buf.length);
                        } catch (Throwable t) {
                            break;
                        }
                        if (n <= 0) continue;
                        try {
                            boolean end = vRec.acceptWaveForm(buf, n);
                            if (end) {
                                String done = extractText(vRec.getResult());
                                vRec.reset();
                                // 还没听到内容时（比如刚开麦的静音），继续听，不提前结束
                                if ((done == null || done.trim().isEmpty()) && !heardAny
                                        && System.currentTimeMillis() - started < 12000) {
                                    continue;
                                }
                                vListening = false;
                                releaseAudio();
                                final String finalText = done;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        resetMic();
                                        if (finalText == null || finalText.trim().isEmpty()) {
                                            tvStatus.setText(S("没听清，再说一次", "Not caught, please repeat"));
                                        } else {
                                            handleVoice(finalText.replace(" ", ""));
                                        }
                                    }
                                });
                                return;
                            }
                            final String t = extractText(vRec.getPartialResult());
                            if (t != null && !t.isEmpty()) heardAny = true;
                            if (t != null && !t.isEmpty() && !t.equals(vPartial)) {
                                vPartial = t;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        tvStatus.setText(S("听到：", "Heard: ") + t);
                                    }
                                });
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }).start();
        } catch (Throwable t) {
            tvStatus.setText(S("麦克风打不开：", "Mic unavailable: ") + t);
            resetMic();
        }
    }

    private void releaseAudio() {
        try {
            if (vAudio != null) {
                vAudio.stop();
                vAudio.release();
                vAudio = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private void stopVosk() {
        vListening = false;
        releaseAudio();
        resetMic();
        try {
            String text = extractText(vRec.getFinalResult());
            vRec.reset();
            if (text == null || text.trim().isEmpty()) {
                tvStatus.setText(S("没听清，再说一次", "Not caught, please repeat"));
                return;
            }
            handleVoice(text.replace(" ", ""));
        } catch (Throwable t) {
            tvStatus.setText(S("识别出错：", "Recognition error: ") + t);
        }
    }

    private static String extractText(String json) {
        if (json == null) return null;
        int i = json.indexOf("\"text\"");
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        int a = json.indexOf('"', c + 1);
        int b = json.indexOf('"', a + 1);
        if (a < 0 || b < 0) return null;
        return json.substring(a + 1, b);
    }

    private void stopListen() {
        stopVosk();
    }

    private void resetMic() {
        listening = false;
        if (btnMic != null) {
            btnMic.setText(S("🎤  说话算一算", "🎤  Tap and speak"));
            btnMic.setBackgroundResource(R.drawable.bg_mic);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_AUDIO) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startListen();
            else tvStatus.setText(S("没有麦克风权限，只能手动按键了", "No mic permission - use the keypad"));
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        tvStatus.setText(S("请说…", "Speak now..."));
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rms) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        tvStatus.setText(S("识别中…", "Recognizing..."));
    }

    @Override
    public void onError(int error) {
        resetMic();
        String msg;
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                msg = "没听清，再说一次";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                msg = "没听到声音";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                msg = "网络不好，识别失败";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                msg = "缺少麦克风权限";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                msg = "识别器忙，稍后再试";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                msg = "识别器异常（" + recService + "）";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                msg = "识别服务出错（" + recService + "）";
                break;
            default:
                msg = "没听清，再说一次";
                break;
        }
        tvStatus.setText(msg);
    }

    @Override
    public void onResults(Bundle results) {
        resetMic();
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String text = (list != null && !list.isEmpty()) ? list.get(0) : null;
        if (text == null || text.trim().isEmpty()) {
            tvStatus.setText(S("没听清，再说一次", "Not caught, please repeat"));
            return;
        }
        handleVoice(text);
    }

    @Override
    public void onPartialResults(Bundle partial) {
        ArrayList<String> list = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) tvStatus.setText(S("听到：", "Heard: ") + list.get(0));
    }

    @Override
    public void onEvent(int type, Bundle params) {
    }

    private void handleVoice(String text) {
        try {
            String e = Parser.toExpression(text);
            double v = Calc.eval(e);
            input.setLength(0);
            input.append(e);
            tvExpr.setText(pretty(e));
            showResult(v);
            tvStatus.setText("听到：" + text);
            speak(langEn ? ("equals " + Calc.fmt(v)) : ("等于" + ZhSpeech.words(v)));
            justEvaluated = true;
            hist.add(pretty(e), Calc.fmt(v));
        } catch (ArithmeticException ex) {
            tvStatus.setText(S("听到：“", "Heard: ") + text + S("”，不能除以零", " - divide by zero"));
        } catch (Exception ex) {
            tvStatus.setText(S("没听懂：“", "Not understood: ") + text + S("”，再说一次", " - try again"));
        }
    }

    // ---------------- TTS ----------------

    private void applyTtsLang() {
        if (tts == null) return;
        java.util.Locale loc = langEn ? java.util.Locale.US : java.util.Locale.CHINA;
        boolean ok = false;
        // 先按音色挑：系统里装了英文音色就一定能念英文
        try {
            java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
            if (voices != null) {
                for (android.speech.tts.Voice v : voices) {
                    java.util.Locale vl = v.getLocale();
                    if (vl == null) continue;
                    String lang = vl.getLanguage();
                    boolean match = langEn ? "en".equals(lang) : "zh".equals(lang);
                    if (match && !v.isNetworkConnectionRequired()) {
                        if (tts.setVoice(v) == TextToSpeech.SUCCESS) {
                            ok = true;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        int r;
        try {
            r = ok ? TextToSpeech.SUCCESS : tts.setLanguage(loc);
        } catch (Throwable t) {
            r = -3;
        }
        if (r >= TextToSpeech.LANG_AVAILABLE) ok = true;
        if (langEn && !ok) {
            if (!warnedNoEnTts) {
                warnedNoEnTts = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(S("缺少英文语音包", "English voice missing"))
                                    .setMessage(S("系统里没有安装英文语音，英文界面下的朗读仍会带中文口音。\n\n"
                                                    + "去「设置 → 语音合成」安装一个英文语音包（如 Google 语音服务）后即可正常朗读。",
                                            "No English TTS voice is installed, so spoken output will still sound Chinese.\n\n"
                                                    + "Open Settings -> Text-to-speech and install an English voice."))
                                    .setPositiveButton(S("打开语音设置", "Open TTS settings"),
                                            new android.content.DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(android.content.DialogInterface d, int w) {
                                                    try {
                                                        startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                                                    } catch (Throwable ignored) {
                                                        try {
                                                            startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                                                        } catch (Throwable ignored2) {
                                                        }
                                                    }
                                                }
                                            })
                                    .setNegativeButton(S("知道了", "OK"), null)
                                    .show();
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }
    }

    private void initTts() {
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    try {
                        tts.setLanguage(Locale.CHINA);
                    } catch (Throwable ignored) {
                    }
                    tts.setSpeechRate(1.05f);
                }
            }
        });
    }

    private void speak(String s) {
        if (!soundOn || s == null || s.isEmpty()) return;
        if (langEn) speakEn(s);
        else playEn(ZhSpeech.ids(s));
    }

    /** 英文用内置音频按词播放，不依赖系统语音包 */
    private void speakEn(String text) {
        playEn(EnSpeech.ids(text));
    }

    private final java.util.HashMap<Integer, byte[]> enPcm = new java.util.HashMap<>();
    private android.media.MediaPlayer enMp;

    private byte[] pcmOf(int resId) {
        byte[] cached = enPcm.get(resId);
        if (cached != null) return cached;
        try {
            InputStream in = getResources().openRawResource(resId);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            byte[] all = bos.toByteArray();
            int off = -1;
            for (int i = 0; i + 4 <= all.length; i++) {
                if (all[i] == 'd' && all[i + 1] == 'a' && all[i + 2] == 't' && all[i + 3] == 'a') {
                    off = i + 8;
                    break;
                }
            }
            byte[] pcm = (off > 0 && off < all.length)
                    ? java.util.Arrays.copyOfRange(all, off, all.length) : new byte[0];
            enPcm.put(resId, pcm);
            return pcm;
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static void le32(java.io.OutputStream o, int v) throws java.io.IOException {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }

    private static void le16(java.io.OutputStream o, int v) throws java.io.IOException {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    private void playEn(java.util.List<Integer> ids) {
        stopEn();
        if (ids.isEmpty()) return;
        try {
            java.io.ByteArrayOutputStream pcm = new java.io.ByteArrayOutputStream();
            byte[] gap = new byte[22050 / 50];   // 20ms 静音
            for (int id : ids) {
                byte[] p = pcmOf(id);
                pcm.write(p, 0, p.length);
                pcm.write(gap, 0, gap.length);
            }
            byte[] data = pcm.toByteArray();
            final File f = new File(getCacheDir(), "say_en.wav");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write("RIFF".getBytes("US-ASCII"));
            le32(fos, 36 + data.length);
            fos.write("WAVEfmt ".getBytes("US-ASCII"));
            le32(fos, 16);
            le16(fos, 1);
            le16(fos, 1);
            le32(fos, 22050);
            le32(fos, 44100);
            le16(fos, 2);
            le16(fos, 16);
            fos.write("data".getBytes("US-ASCII"));
            le32(fos, data.length);
            fos.write(data);
            fos.close();

            enMp = new android.media.MediaPlayer();
            enMp.setDataSource(f.getAbsolutePath());
            enMp.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(android.media.MediaPlayer mp) {
                    try {
                        mp.start();
                    } catch (Throwable ignored) {
                    }
                }
            });
            enMp.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(android.media.MediaPlayer mp) {
                    try {
                        mp.release();
                    } catch (Throwable ignored) {
                    }
                    if (enMp == mp) enMp = null;
                    f.delete();
                }
            });
            enMp.prepareAsync();
        } catch (Throwable t) {
        }
    }

    private void stopEn() {
        try {
            if (enMp != null) {
                enMp.stop();
                enMp.release();
            }
        } catch (Throwable ignored) {
        }
        enMp = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (listening) stopListen();
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }
        if (toneGen != null) {
            try {
                toneGen.release();
            } catch (Throwable ignored) {
            }
            toneGen = null;
        }
        super.onDestroy();
    }
}
