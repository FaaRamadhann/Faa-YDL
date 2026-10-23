package com.faa.faadl;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Tema tampilan yang bisa dipilih user: Miuix (ala HyperOS) atau Material.
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

    private Theme(boolean miuix) {
        this.miuix = miuix;
        this.name = miuix ? "Miuix" : "Material";
        if (miuix) {
            // HyperOS: latar abu terang, kartu putih, sudut besar, tombol pil.
            bg = Color.parseColor("#F2F4F7");
            card = Color.WHITE;
            stroke = Color.parseColor("#E3E8EF");
            primary = Color.parseColor("#0284C7");
            primaryDeep = Color.parseColor("#0369A1");
            textStrong = Color.parseColor("#0C4A6E");
            subtext = Color.parseColor("#475569");
            muted = Color.parseColor("#64748B");
            cardRadius = 18;
            btnRadius = 26;
        } else {
            // Material 3 (light): permukaan lebih terang, sudut lebih kecil.
            bg = Color.parseColor("#F8FAFD");
            card = Color.WHITE;
            stroke = Color.parseColor("#DDE5EE");
            primary = Color.parseColor("#0B6BCB");
            primaryDeep = Color.parseColor("#084E94");
            textStrong = Color.parseColor("#1A1C1E");
            subtext = Color.parseColor("#44474E");
            muted = Color.parseColor("#73777F");
            cardRadius = 14;
            btnRadius = 12;
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
