package com.dsh.voicecalc;

import java.util.Locale;

/** 四则运算求值 */
public final class Calc {

    private final String s;
    private int i;

    private Calc(String s) {
        this.s = s;
    }

    public static double eval(String expr) {
        String e = expr.replace(" ", "").replace("（", "(").replace("）", ")");
        if (e.isEmpty()) throw new IllegalArgumentException("empty");
        Calc c = new Calc(e);
        double v = c.parseExpr();
        if (c.i < c.s.length()) throw new IllegalArgumentException("trailing");
        return v;
    }

    private double parseExpr() {
        double v = parseTerm();
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '+') { i++; v += parseTerm(); }
            else if (ch == '-') { i++; v -= parseTerm(); }
            else break;
        }
        return v;
    }

    private double parseTerm() {
        double v = parseFactor();
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '*') { i++; v *= parseFactor(); }
            else if (ch == '/') {
                i++;
                double d = parseFactor();
                if (d == 0) throw new ArithmeticException("div0");
                v /= d;
            } else break;
        }
        return v;
    }

    private double parseFactor() {
        if (i < s.length() && s.charAt(i) == '-') { i++; return -parseFactor(); }
        if (i < s.length() && s.charAt(i) == '+') { i++; return parseFactor(); }
        if (i < s.length() && s.charAt(i) == '(') {
            i++;
            double v = parseExpr();
            if (i < s.length() && s.charAt(i) == ')') i++;
            return v;
        }
        int start = i;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
        if (start == i) throw new IllegalArgumentException("number expected");
        return Double.parseDouble(s.substring(start, i));
    }

    public static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "算不出来";
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e15) {
            return String.valueOf((long) Math.rint(v));
        }
        String r = String.format(Locale.US, "%.6f", v);
        while (r.endsWith("0")) r = r.substring(0, r.length() - 1);
        if (r.endsWith(".")) r = r.substring(0, r.length() - 1);
        return r;
    }
}
