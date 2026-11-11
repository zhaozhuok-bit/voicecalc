package com.dsh.voicecalc;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 计算记录：只保留最近 30 天 */
public class HistoryDb extends SQLiteOpenHelper {

    private static final long KEEP_MS = 30L * 24 * 60 * 60 * 1000;

    public HistoryDb(Context c) {
        super(c, "hist.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE hist(id INTEGER PRIMARY KEY AUTOINCREMENT, expr TEXT, res TEXT, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
    }

    public void add(String expr, String res) {
        try {
            prune();
            ContentValues v = new ContentValues();
            v.put("expr", expr);
            v.put("res", res);
            v.put("ts", System.currentTimeMillis());
            getWritableDatabase().insert("hist", null, v);
        } catch (Exception ignored) {
        }
    }

    public void prune() {
        try {
            getWritableDatabase().delete("hist", "ts < ?",
                    new String[]{String.valueOf(System.currentTimeMillis() - KEEP_MS)});
        } catch (Exception ignored) {
        }
    }

    public List<String[]> recent(int n) {
        List<String[]> out = new ArrayList<>();
        try {
            Cursor c = getReadableDatabase().query("hist", null, null, null, null, null,
                    "ts DESC", String.valueOf(n));
            while (c.moveToNext()) {
                String e = c.getString(c.getColumnIndexOrThrow("expr"));
                String r = c.getString(c.getColumnIndexOrThrow("res"));
                long ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                out.add(new String[]{e, r, String.valueOf(ts)});
            }
            c.close();
        } catch (Exception ignored) {
        }
        return out;
    }

    public int count() {
        try {
            Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM hist", null);
            int n = c.moveToFirst() ? c.getInt(0) : 0;
            c.close();
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    public void clear() {
        try {
            getWritableDatabase().delete("hist", null, null);
        } catch (Exception ignored) {
        }
    }
}
