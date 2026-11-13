package com.dsh.voicecalc;

/** 把语音识别出来的中文句子，转成可计算的算式 */
public final class Parser {

    private static final String[] FILLERS = {
            "等于多少", "等于几", "等于", "结果是多少", "结果是", "结果",
            "是多少钱", "是多少", "多少钱", "多少", "请问", "麻烦",
            "帮我算一下", "帮我算算", "帮我算", "帮我", "算一下", "算算",
            "一共", "得多少", "答案", "是几", "嗯", "那个"
    };

    private Parser() {
    }

    private static final String[] EN_ONES = {
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"
    };
    private static final String[] EN_TENS = {
            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    /** 英文口算：thirty two plus fifty eight */
    private static String englishExpression(String raw) {
        String t = raw.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9 ]", " ").trim();
        String[] words = t.isEmpty() ? new String[0] : t.split("\\s+");
        StringBuilder out = new StringBuilder();
        java.util.List<String> group = new java.util.ArrayList<>();
        for (String tok : words) {
            switch (tok) {
                case "plus":
                case "add":
                    flush(out, group);
                    out.append('+');
                    break;
                case "minus":
                case "subtract":
                    flush(out, group);
                    out.append('-');
                    break;
                case "times":
                case "multiplied":
                case "multiply":
                case "x":
                    flush(out, group);
                    out.append('*');
                    break;
                case "divided":
                case "divide":
                case "over":
                    flush(out, group);
                    out.append('/');
                    break;
                case "percent":
                    flush(out, group);
                    out.append("/100");
                    break;
                case "by":
                case "and":
                case "equals":
                case "equal":
                case "is":
                case "what":
                case "whats":
                case "the":
                case "calculate":
                case "please":
                case "answer":
                case "how":
                case "much":
                case "result":
                case "of":
                case "a":
                    break;
                default:
                    group.add(tok);
                    break;
            }
        }
        flush(out, group);
        String s = out.toString();
        if (s.isEmpty()) throw new IllegalArgumentException("empty");
        if (!s.matches("[0-9.+\\-*/()]+")) throw new IllegalArgumentException("chars");
        return s;
    }

    private static void flush(StringBuilder out, java.util.List<String> group) {
        if (group.isEmpty()) return;
        out.append(parseEnglishNumber(group));
        group.clear();
    }

    private static String parseEnglishNumber(java.util.List<String> g) {
        long total = 0, cur = 0;
        StringBuilder dec = null;
        for (String s : g) {
            if ("point".equals(s)) {
                dec = new StringBuilder();
                continue;
            }
            if (dec != null) {
                int d = oneVal(s);
                if (d >= 0) dec.append(d);
                continue;
            }
            if ("hundred".equals(s)) {
                if (cur == 0) cur = 1;
                cur *= 100;
                continue;
            }
            if ("thousand".equals(s)) {
                total += Math.max(cur, 1) * 1000;
                cur = 0;
                continue;
            }
            if ("million".equals(s)) {
                total += Math.max(cur, 1) * 1000000;
                cur = 0;
                continue;
            }
            int v = wordVal(s);
            if (v >= 0) cur += v;
        }
        long val = total + cur;
        if (dec != null && dec.length() > 0) return val + "." + dec;
        return String.valueOf(val);
    }

    private static int oneVal(String s) {
        for (int i = 0; i < EN_ONES.length; i++) if (EN_ONES[i].equals(s)) return i;
        return -1;
    }

    private static int wordVal(String s) {
        int v = oneVal(s);
        if (v >= 0) return v;
        for (int i = 2; i < EN_TENS.length; i++) if (EN_TENS[i].equals(s)) return i * 10;
        return -1;
    }


    public static String toExpression(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty");
        if (raw.matches(".*[A-Za-z].*")) return englishExpression(raw);
        String t = widen(raw);
        t = t.replaceAll("[\\s,，。、！？!?；;：:'\"“”‘’]", "");
        for (String f : FILLERS) t = t.replace(f, "");

        // 语音识别常见谐音纠正
        t = t.replace("热", "十").replace("室", "十").replace("时", "十");
        t = t.replace("家", "加").replace("佳", "加").replace("夹", "加").replace("嘉", "加");
        t = t.replace("见", "减").replace("建", "减").replace("箭", "减");
        t = t.replace("成", "乘").replace("程", "乘").replace("城", "乘");
        t = t.replace("出", "除").replace("初", "除").replace("楚", "除");
        t = t.replace("等以", "除以").replace("初以", "除以");
        t = t.replace("乘以", "*").replace("乘上", "*").replace("乘", "*").replace("×", "*");
        t = t.replace("除以", "/").replace("除", "/").replace("÷", "/");
        t = t.replace("加上", "+").replace("加", "+").replace("＋", "+");
        t = t.replace("减去", "-").replace("减掉", "-").replace("减", "-").replace("－", "-");
        t = t.replace("负", "-").replace("正", "");

        t = convertPercent(t);
        t = convertNumbers(t);

        if (t.isEmpty()) throw new IllegalArgumentException("empty");
        if (!t.matches("[0-9.+\\-*/()]+")) throw new IllegalArgumentException("chars");
        return t;
    }

    private static String widen(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') b.append((char) ('0' + (c - '０')));
            else if (c == '．') b.append('.');
            else b.append(c);
        }
        return b.toString();
    }

    private static String convertPercent(String t) {
        int guard = 0;
        while (guard++ < 20) {
            int idx = t.indexOf("百分之");
            if (idx < 0) break;
            int p = idx + 3;
            int j = p;
            while (j < t.length() && (Character.isDigit(t.charAt(j)) || t.charAt(j) == '.')) j++;
            if (j == p) {
                while (j < t.length() && ChineseNum.isNumChar(t.charAt(j))) j++;
            }
            if (j == p) {
                t = t.substring(0, idx) + "*" + t.substring(p);
                continue;
            }
            String numStr = t.substring(p, j);
            double v;
            try {
                v = Character.isDigit(numStr.charAt(0))
                        ? Double.parseDouble(numStr) : ChineseNum.parse(numStr);
            } catch (Exception e) {
                v = 0;
            }
            t = t.substring(0, idx) + "(" + Calc.fmt(v / 100.0) + ")" + t.substring(j);
        }
        return t;
    }

    private static String convertNumbers(String t) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (ChineseNum.isNumChar(c) && c != '点') {
                int j = i;
                while (j < t.length() && ChineseNum.isNumChar(t.charAt(j))) j++;
                out.append(Calc.fmt(ChineseNum.parse(t.substring(i, j))));
                i = j;
                continue;
            }
            if (c == '点') {
                out.append('.');
                i++;
                continue;
            }
            if (Character.isDigit(c) || c == '.' || "+-*/()".indexOf(c) >= 0) out.append(c);
            i++;
        }
        return out.toString();
    }
}
