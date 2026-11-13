package com.dsh.voicecalc;

/** 中文发音：内置离线女声音频，按字/词拼接播放 */
public final class ZhSpeech {

    // 顺序必须和 res/raw/zh_*.wav 一致
    public static final String[] WORDS = {
            "零", "一", "二", "三", "四", "五", "六", "七", "八", "九",
            "十", "百", "千", "万", "亿", "点", "加", "减", "乘", "除",
            "等于", "百分之", "清空", "退格", "负"
    };

    // 长词优先匹配
    private static final int[] ORDER = {21, 20, 22, 23, 24, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
            10, 11, 12, 13, 14, 15, 16, 17, 18, 19};

    private static final int[] RAW = {
            R.raw.zh_0, R.raw.zh_1, R.raw.zh_2, R.raw.zh_3, R.raw.zh_4, R.raw.zh_5,
            R.raw.zh_6, R.raw.zh_7, R.raw.zh_8, R.raw.zh_9, R.raw.zh_10, R.raw.zh_11,
            R.raw.zh_12, R.raw.zh_13, R.raw.zh_14, R.raw.zh_15, R.raw.zh_16, R.raw.zh_17,
            R.raw.zh_18, R.raw.zh_19, R.raw.zh_20, R.raw.zh_21, R.raw.zh_22, R.raw.zh_23,
            R.raw.zh_24
    };

    private ZhSpeech() {
    }

    public static int rawOf(String word) {
        for (int i = 0; i < WORDS.length; i++) {
            if (WORDS[i].equals(word)) return RAW[i];
        }
        return 0;
    }

    /** 中文文本 -> 音频 id 列表 */
    public static java.util.List<Integer> ids(String text) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (text == null) return out;
        int i = 0;
        while (i < text.length()) {
            boolean hit = false;
            for (int k : ORDER) {
                String w = WORDS[k];
                if (text.startsWith(w, i)) {
                    out.add(RAW[k]);
                    i += w.length();
                    hit = true;
                    break;
                }
            }
            if (!hit) i++;
        }
        return out;
    }

    private static final String[] D = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] U = {"", "十", "百", "千"};

    /** 数字 -> 中文读法，例如 56 -> 五十六 */
    public static String words(double v) {
        StringBuilder b = new StringBuilder();
        if (v < 0) {
            b.append("负");
            v = -v;
        }
        long ip = (long) Math.floor(v);
        double frac = v - ip;
        if (ip == 0 && frac < 0.0000001) return "零";
        if (ip > 0) b.append(intZh(ip));
        if (frac > 0.0000001) {
            String s = String.valueOf(frac);
            s = s.substring(s.indexOf('.') + 1);
            b.append("点");
            for (int i = 0; i < s.length() && i < 6; i++) b.append(D[s.charAt(i) - '0']);
        }
        return b.toString();
    }

    private static String intZh(long n) {
        if (n == 0) return "零";
        StringBuilder b = new StringBuilder();
        long yi = n / 100000000L;
        long wan = (n / 10000) % 10000;
        long rest = n % 10000;
        if (yi > 0) b.append(intZh(yi)).append("亿");
        if (wan > 0) {
            if (b.length() > 0 && wan < 1000) b.append("零");
            b.append(section(wan)).append("万");
        }
        if (rest > 0) {
            if (b.length() > 0 && rest < 1000) b.append("零");
            b.append(section(rest));
        }
        return b.toString();
    }

    private static String section(long n) {
        StringBuilder sb = new StringBuilder();
        int[] d = new int[4];
        for (int i = 3; i >= 0; i--) {
            d[i] = (int) (n % 10);
            n /= 10;
        }
        boolean started = false;
        boolean zero = false;
        for (int i = 0; i < 4; i++) {
            if (d[i] == 0) {
                if (started) zero = true;
                continue;
            }
            if (zero) {
                sb.append("零");
                zero = false;
            }
            if (i == 2 && d[i] == 1 && !started) {
                sb.append("十");
            } else {
                sb.append(D[d[i]]).append(U[3 - i]);
            }
            started = true;
        }
        return sb.toString();
    }
}
