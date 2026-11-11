package com.dsh.voicecalc;

/** 中文数字（口语）转数值 */
public final class ChineseNum {

    private static final String DIGITS = "零〇一二两三四五六七八九";
    private static final String UNITS = "十百千万亿";

    public static boolean isNumChar(char c) {
        return DIGITS.indexOf(c) >= 0 || UNITS.indexOf(c) >= 0 || c == '点';
    }

    static int digitOf(char c) {
        switch (c) {
            case '零': case '〇': return 0;
            case '一': return 1;
            case '二': case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    static long unitOf(char c) {
        switch (c) {
            case '十': return 10;
            case '百': return 100;
            case '千': return 1000;
            case '万': return 10000;
            case '亿': return 100000000L;
            default: return 0;
        }
    }

    public static double parse(String s) {
        s = s.replace('〇', '零');
        int dot = s.indexOf('点');
        if (dot >= 0) {
            double ip = parseInt(s.substring(0, dot));
            String dec = s.substring(dot + 1);
            double v = 0, base = 0.1;
            for (int i = 0; i < dec.length(); i++) {
                int d = digitOf(dec.charAt(i));
                if (d < 0) continue;
                v += d * base;
                base /= 10;
            }
            return ip + v;
        }
        return parseInt(s);
    }

    private static double parseInt(String s) {
        if (s.isEmpty()) return 0;
        double total = 0;      // 万/亿 已结算部分
        double section = 0;    // 万以下
        double num = 0;
        double lastUnit = 0;
        boolean sawZero = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = digitOf(c);
            if (d >= 0) {
                num = d;
                if (d == 0) sawZero = true;
                continue;
            }
            double u = unitOf(c);
            if (u == 0) continue;
            if (u >= 10000) {
                section = (section + num) * u;
                total += section;
                section = 0;
                num = 0;
            } else {
                if (num == 0) num = 1;
                section += num * u;
                num = 0;
            }
            lastUnit = u;
            sawZero = false;
        }
        // 口语省略：一千二 = 1200，两百五 = 250
        if (num > 0 && lastUnit >= 100 && !sawZero) {
            num = num * (lastUnit / 10);
        }
        return total + section + num;
    }
}
