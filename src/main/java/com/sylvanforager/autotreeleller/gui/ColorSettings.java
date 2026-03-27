package com.sylvanforager.autotreeleller.gui;

/** Holds all customisable GUI colors. Read at render-time so changes apply live. */
public class ColorSettings {

    // ── Primary customisable fields ────────────────────────────────────────
    public static int accent    = 0xFF7855FF;
    public static int bgMain    = 0xFF161728;
    public static int bgSidebar = 0xFF0F1020;
    public static int bgCard    = 0xFF1F2140;

    // ── Derived (computed from primaries) ─────────────────────────────────
    public static int accentDim()   { return blend(accent,  0xFF000000, 0.42f); }
    public static int bgHeader()    { return darken(bgMain,    6); }
    public static int bgLogo()      { return darken(bgMain,   14); }
    public static int cardHover()   { return lighten(bgCard,   8); }
    public static int chipSel()     { return accent; }
    public static int toggleOn()    { return accent; }
    public static int kbListen()    { return accent; }
    public static int navAccent()   { return accent; }

    // ── Preset themes ──────────────────────────────────────────────────────
    public enum Theme {
        NEBULA  ("Nebula",   0xFF7855FF, 0xFF161728, 0xFF0F1020, 0xFF1F2140),
        OCEAN   ("Ocean",    0xFF1E88E5, 0xFF0A1528, 0xFF071020, 0xFF0D1C35),
        CRIMSON ("Crimson",  0xFFE53935, 0xFF1A0C0C, 0xFF120808, 0xFF1E1010),
        EMERALD ("Emerald",  0xFF43A047, 0xFF0C1A0D, 0xFF081209, 0xFF101E12),
        GOLD    ("Gold",     0xFFFFC107, 0xFF1A1500, 0xFF120F00, 0xFF1E1A00),
        ROSE    ("Rose",     0xFFE91E63, 0xFF1A0D14, 0xFF12080E, 0xFF1E1018);

        public final String name;
        public final int accent, bgMain, bgSidebar, bgCard;
        Theme(String n, int a, int m, int s, int c) {
            name=n; accent=a; bgMain=m; bgSidebar=s; bgCard=c;
        }
    }

    // Background-only darkness presets (keeps current accent)
    public static final int[][] BG_PRESETS = {
        {0xFF161728, 0xFF0F1020, 0xFF1F2140},  // Standard
        {0xFF0F1020, 0xFF080910, 0xFF161728},  // Darker
        {0xFF08091A, 0xFF05060F, 0xFF0D0E1E},  // Darkest
    };
    public static final String[] BG_PRESET_NAMES = {"Standard", "Darker", "Darkest"};

    public static void applyTheme(Theme t) {
        accent=t.accent; bgMain=t.bgMain; bgSidebar=t.bgSidebar; bgCard=t.bgCard;
    }

    public static void applyBgPreset(int idx) {
        bgMain    = BG_PRESETS[idx][0];
        bgSidebar = BG_PRESETS[idx][1];
        bgCard    = BG_PRESETS[idx][2];
    }

    /** Returns the matching preset Theme if current settings equal it, else null. */
    public static Theme activeTheme() {
        for (Theme t : Theme.values()) {
            if (t.accent==accent && t.bgMain==bgMain
                    && t.bgSidebar==bgSidebar && t.bgCard==bgCard) return t;
        }
        return null;
    }

    /** Active background preset index (0-2), or -1 if custom. */
    public static int activeBgPreset() {
        for (int i = 0; i < BG_PRESETS.length; i++) {
            if (BG_PRESETS[i][0]==bgMain && BG_PRESETS[i][1]==bgSidebar
                    && BG_PRESETS[i][2]==bgCard) return i;
        }
        return -1;
    }

    public static void resetToDefault() {
        applyTheme(Theme.NEBULA);
    }

    public static String accentHex() {
        return String.format("#%06X", accent & 0xFFFFFF);
    }

    // ── Color math helpers ────────────────────────────────────────────────
    public static int darken(int c, int amt) {
        int r = Math.max(0, ((c>>16)&0xFF)-amt);
        int g = Math.max(0, ((c>> 8)&0xFF)-amt);
        int b = Math.max(0, ( c     &0xFF)-amt);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
    public static int lighten(int c, int amt) {
        int r = Math.min(255, ((c>>16)&0xFF)+amt);
        int g = Math.min(255, ((c>> 8)&0xFF)+amt);
        int b = Math.min(255, ( c     &0xFF)+amt);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
    private static int blend(int a, int b, float t) {
        int ar=(a>>16)&0xFF, ag=(a>>8)&0xFF, ab=a&0xFF;
        int br=(b>>16)&0xFF, bg=(b>>8)&0xFF, bb=b&0xFF;
        return 0xFF000000|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);
    }
}
