package com.dsh.voicecalc;

/** 英文发音：内置离线单词音频，按词拼接播放 */
public final class EnSpeech {

    public static final String[] WORDS = {
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen",
            "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
            "hundred", "thousand", "million", "and", "point", "plus", "minus", "times",
            "divided", "by", "equals", "percent", "clear", "delete"
    };

    private static final int[] RAW = {
            R.raw.en_0, R.raw.en_1, R.raw.en_2, R.raw.en_3, R.raw.en_4, R.raw.en_5,
            R.raw.en_6, R.raw.en_7, R.raw.en_8, R.raw.en_9, R.raw.en_10, R.raw.en_11,
            R.raw.en_12, R.raw.en_13, R.raw.en_14, R.raw.en_15, R.raw.en_16, R.raw.en_17,
            R.raw.en_18, R.raw.en_19, R.raw.en_20, R.raw.en_21, R.raw.en_22, R.raw.en_23,
            R.raw.en_24, R.raw.en_25, R.raw.en_26, R.raw.en_27, R.raw.en_28, R.raw.en_29,
            R.raw.en_30, R.raw.en_31, R.raw.en_32, R.raw.en_33, R.raw.en_34, R.raw.en_35,
            R.raw.en_36, R.raw.en_37, R.raw.en_38, R.raw.en_39, R.raw.en_40, R.raw.en_41
    };

    private EnSpeech() {
    }

    /** 单词 -> 音频资源，找不到返回 0 */
    public static int rawOf(String word) {
        for (int i = 0; i < WORDS.length; i++) {
            if (WORDS[i].equals(word)) return RAW[i];
        }
        return 0;
    }

    /** 英文文本 -> 音频 id 列表 */
    public static java.util.List<Integer> ids(String text) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (text == null) return out;
        for (String tok : text.toLowerCase(java.util.Locale.US).split("\\s+")) {
            if (tok.isEmpty()) continue;
            if (tok.matches("-?[0-9]+(\\.[0-9]+)?")) {
                for (String w : words(Double.parseDouble(tok)).split(" ")) {
                    int id = rawOf(w);
                    if (id != 0) out.add(id);
                }
            } else {
                int id = rawOf(tok.replaceAll("[^a-z]", ""));
                if (id != 0) out.add(id);
            }
        }
        return out;
    }

    /** 数字转英文单词序列，例如 56 -> "fifty six" */
    public static String words(double v) {
        StringBuilder b = new StringBuilder();
        if (v < 0) {
            b.append("minus ");
            v = -v;
        }
        long ip = (long) Math.floor(v);
        double frac = v - ip;
        if (ip == 0 && frac == 0) return b.append("zero").toString();
        if (ip > 0) b.append(intWords(ip));
        if (frac > 0.0000001) {
            String s = String.valueOf(frac);
            s = s.substring(s.indexOf('.') + 1);
            b.append(b.length() > 0 ? " " : "").append("point");
            for (int i = 0; i < s.length() && i < 6; i++) b.append(' ').append(ONES[s.charAt(i) - '0']);
        }
        return b.toString();
    }

    private static final String[] ONES = {
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"
    };
    private static final String[] TENS = {
            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    private static String intWords(long n) {
        if (n < 20) return ONES[(int) n];
        if (n < 100) {
            return TENS[(int) (n / 10)] + (n % 10 > 0 ? " " + ONES[(int) (n % 10)] : "");
        }
        if (n < 1000) {
            String s = ONES[(int) (n / 100)] + " hundred";
            long r = n % 100;
            return r > 0 ? s + " and " + intWords(r) : s;
        }
        if (n < 1000000L) {
            String s = intWords(n / 1000) + " thousand";
            long r = n % 1000;
            return r > 0 ? s + " " + intWords(r) : s;
        }
        if (n < 1000000000L) {
            String s = intWords(n / 1000000L) + " million";
            long r = n % 1000000L;
            return r > 0 ? s + " " + intWords(r) : s;
        }
        return ONES[(int) (n % 10)];
    }
}
