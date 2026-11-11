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

    public static String toExpression(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty");
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
