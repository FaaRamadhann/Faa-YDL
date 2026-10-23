package com.faa.faadlmod;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Tema tampilan yang bisa dipilih user: Miuix (ala HyperOS) atau Material.
 * Dua-duanya tetap pakai identitas biru Faa DL; bedanya bentuk & elevasi.
 * Disimpan di prefs, dipakai saat buildUI() dijalankan ulang.
 */
public final class Theme {
    public static final int MIUIX = 0;
    public static final int MATERIAL = 1;
    private static final String PREF = "fydl_theme";
    private static final String KEY = "mode";

    public final boolean miuix;
    public final String name;
    public final int bg, card, stroke, primary, primaryDeep, textStrong, subtext, muted;
    public final int cardRadius, btnRadius;
    /** null = tombol solid th.primary; selain itu gradien kiri->kanan. */
    public final int[] btnGradient;

    private Theme(boolean miuix) {
        this.miuix = miuix;
        this.name = miuix ? "Miuix" : "Material";
        if (miuix) {
            // HyperOS: latar biru muda khas Faa DL, sudut besar, tombol pil gradien.
            bg = Color.parseColor("#E0F2FE");
            card = Color.WHITE;
            stroke = Color.parseColor("#BAE6FD");
            primary = Color.parseColor("#0284C7");
            primaryDeep = Color.parseColor("#0369A1");
            textStrong = Color.parseColor("#0C4A6E");
            subtext = Color.parseColor("#475569");
            muted = Color.parseColor("#64748B");
            cardRadius = 18;
            btnRadius = 26;
            btnGradient = new int[]{Color.parseColor("#0284C7"), Color.parseColor("#0D9488")};
        } else {
            // Material 3 (light): permukaan netral, tombol solid, sudut lebih kecil.
            bg = Color.parseColor("#F1F5F9");
            card = Color.WHITE;
            stroke = Color.parseColor("#E2E8F0");
            primary = Color.parseColor("#0284C7");
            primaryDeep = Color.parseColor("#0369A1");
            textStrong = Color.parseColor("#0F172A");
            subtext = Color.parseColor("#475569");
            muted = Color.parseColor("#64748B");
            cardRadius = 14;
            btnRadius = 12;
            btnGradient = null;
        }
    }

    public static Theme load(Context c) {
        return new Theme(prefs(c).getInt(KEY, MIUIX) == MATERIAL);
    }

    public static void toggle(Context c) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(KEY, p.getInt(KEY, MIUIX) == MIUIX ? MATERIAL : MIUIX).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
