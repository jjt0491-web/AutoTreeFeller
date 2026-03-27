package com.sylvanforager.autotreeleller.gui;

import com.sylvanforager.autotreeleller.module.ForagingModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * SylvanHub — main GUI for the AutoTreeFeller mod.
 * Open with INSERT key.  Sidebar Settings item → live color customization.
 */
public class SylvanHubScreen extends Screen {

    // ── Fixed UI colors (not theme-able) ──────────────────────────────────
    private static final int TEXT        = 0xFFE0E0F0;
    private static final int TEXT_MU     = 0xFF8888A8;
    private static final int SECTION_LBL = 0xFF7070A0;
    private static final int DIVIDER     = 0xFF272845;
    private static final int BORDER      = 0xFF2E2F55;
    private static final int TOGGLE_OFF  = 0xFF3A3B5C;
    private static final int GEAR_N      = 0xFF6666A0;
    private static final int GEAR_HOV    = 0xFFAAAACC;
    private static final int CHIP        = 0xFF252548;
    private static final int CHIP_HOV    = 0xFF30315A;
    private static final int CHIP_DIS    = 0xFF1E1F3A;
    private static final int WARN        = 0xFFFFAA44;
    private static final int CLOSE_HOV   = 0xFFDD6666;
    private static final int KB_BG       = 0xFF252548;

    // ── Layout ────────────────────────────────────────────────────────────
    private static final int SIDEBAR_W  = 180;
    private static final int HEADER_H   = 48;
    private static final int CARD_W     = 250;
    private static final int CARD_H     = 85;
    private static final int PANEL_W    = 350;
    private static final int PANEL_H    = 256;
    private static final int GEAR_SZ    = 10;
    private static final int KB_W       = 90;
    private static final int KB_H       = 20;
    private static final int SL_W       = 160;  // slider bar width
    private static final int SL_H       = 8;    // slider bar height

    // ── View / module-settings state ─────────────────────────────────────
    private String  currentView     = "Foraging"; // "Foraging" | "Settings"
    private boolean showSettings    = false;       // module settings overlay
    private boolean listeningForKey = false;

    // ── Stored hit-boxes (updated during render) ──────────────────────────
    private int gearX, gearY;
    private int panelX, panelY;
    private final int[] chipHitX = new int[ForagingModule.TreeType.values().length];
    private final int[] chipHitW = new int[ForagingModule.TreeType.values().length];
    private int chipHitY, kbBtnX, kbBtnY;

    // Color-settings hit-boxes
    private final int[] themeSwX = new int[ColorSettings.Theme.values().length];
    private static final int SWATCH_W = 46, SWATCH_H = 26;
    private int themeSwY;
    private final int[] slBx = new int[3];  // slider bar start X
    private final int[] slBy = new int[3];  // slider bar start Y
    private final int[] bgBtnX = new int[ColorSettings.BG_PRESETS.length];
    private int bgBtnY;
    private static final int BG_BTN_W = 64, BG_BTN_H = 20;
    private int resetBtnX, resetBtnY;
    private static final int RESET_W = 100, RESET_H = 20;

    // ── Slider drag state ────────────────────────────────────────────────
    private int draggingSlider = -1; // -1=none, 0=R, 1=G, 2=B

    // ── Sidebar Y positions (computed once, used in click detection) ───────
    // Matching renderSidebar layout exactly:
    //   y = HEADER_H+14 = 62  → "MAIN" label
    //   +18 = 80  → Modules item starts
    //   +25 = 105 → General sub
    //   +20 = 125 → Skills sub
    //   +20 = 145 → Mining sub
    //   +20 = 165 → Foraging sub
    //   +20 = 185 → Misc sub
    //   +20 = 205 → Dev sub
    //   +20+10 = 235 → Routes item
    //   +25 = 260 → Command Binds
    //   +25 = 285 → Documentation
    //   +25+14 = 324 → "CONFIGURATION" label
    //   +18 = 342 → Settings item
    private static final int NAV_MODULES_Y  = 80;
    private static final int NAV_FORAGING_Y = 165;
    private static final int NAV_SETTINGS_Y = 342;

    public SylvanHubScreen() { super(Text.literal("SylvanHub")); }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return !showSettings && !listeningForKey; }

    // ═════════════════════════════════════════════════════════════════════
    //  RENDER ROOT
    // ═════════════════════════════════════════════════════════════════════

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, ColorSettings.bgMain);
        renderSidebar(ctx, mouseX, mouseY);
        renderContent(ctx, mouseX, mouseY);
        if (showSettings) renderModuleSettingsPanel(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SIDEBAR
    // ═════════════════════════════════════════════════════════════════════

    private void renderSidebar(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, SIDEBAR_W, height, ColorSettings.bgSidebar);
        ctx.fill(SIDEBAR_W - 1, 0, SIDEBAR_W, height, DIVIDER);

        // Logo row
        ctx.fill(0, 0, SIDEBAR_W - 1, HEADER_H, ColorSettings.bgLogo());
        ctx.fill(0, HEADER_H - 1, SIDEBAR_W - 1, HEADER_H, DIVIDER);
        drawDot(ctx, 24, 24, 7, ColorSettings.accent);
        ctx.drawText(textRenderer, "SylvanHub", 38, 20, TEXT, false);

        boolean inModules  = !"Settings".equals(currentView);
        boolean inSettings = "Settings".equals(currentView);

        int y = HEADER_H + 14;
        ctx.drawText(textRenderer, "MAIN", 16, y, SECTION_LBL, false);
        y += 18;

        y = navItem(ctx, "Modules", y, inModules,  mx, my);
        y = navSub (ctx, "General",  y, false, false, mx, my);
        y = navSub (ctx, "Skills",   y, false, false, mx, my);
        y = navSub (ctx, "Mining",   y, false, false, mx, my);
        y = navSub (ctx, "Foraging", y, inModules && "Foraging".equals(currentView), true, mx, my);
        y = navSub (ctx, "Misc",     y, false, false, mx, my);
        y = navSub (ctx, "Dev",      y, false, false, mx, my);
        y += 10;

        y = navItem(ctx, "Routes",        y, false, mx, my);
        y = navItem(ctx, "Command Binds", y, false, mx, my);
        y = navItem(ctx, "Documentation", y, false, mx, my);
        y += 14;

        ctx.drawText(textRenderer, "CONFIGURATION", 16, y, SECTION_LBL, false);
        y += 18;
        navItem(ctx, "Settings", y, inSettings, mx, my);
    }

    private int navItem(DrawContext ctx, String label, int y, boolean active, int mx, int my) {
        boolean hover = !active && mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + 22;
        if (active || hover) ctx.fill(0, y, SIDEBAR_W - 1, y + 22, active ? 0x18FFFFFF : 0x0CFFFFFF);
        if (active) ctx.fill(0, y, 3, y + 22, ColorSettings.accent);
        ctx.fill(14, y + 7, 22, y + 15, active ? ColorSettings.accent : (hover ? TEXT_MU : SECTION_LBL));
        ctx.drawText(textRenderer, label, 26, y + 7, active ? TEXT : (hover ? TEXT : TEXT_MU), false);
        return y + 25;
    }

    private int navSub(DrawContext ctx, String label, int y, boolean active, boolean tinted, int mx, int my) {
        boolean hover = !active && mx >= 0 && mx < SIDEBAR_W && my >= y && my < y + 20;
        if (active) ctx.fill(0, y, SIDEBAR_W - 1, y + 20, 0x12FFFFFF);
        if (active) ctx.fill(0, y, 3, y + 20, ColorSettings.accent);
        ctx.fill(28, y + 8, 32, y + 12, active ? ColorSettings.accent : (hover ? TEXT_MU : 0xFF383860));
        int col = active ? TEXT : (tinted ? TEXT_MU : 0xFF55557A);
        ctx.drawText(textRenderer, label, 36, y + 6, hover ? TEXT_MU : col, false);
        return y + 20;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CONTENT DISPATCH
    // ═════════════════════════════════════════════════════════════════════

    private void renderContent(DrawContext ctx, int mx, int my) {
        int cx = SIDEBAR_W + 24;

        // Shared header bar
        ctx.fill(SIDEBAR_W, 0, width, HEADER_H, ColorSettings.bgHeader());
        ctx.fill(SIDEBAR_W, HEADER_H - 1, width, HEADER_H, DIVIDER);

        if ("Settings".equals(currentView)) {
            ctx.drawText(textRenderer, "Settings", cx, 18, TEXT, false);
            int pw = cx + textRenderer.getWidth("Settings") + 6;
            ctx.drawText(textRenderer, "| Customize appearance", pw, 18, TEXT_MU, false);
            renderColorSettings(ctx, mx, my, cx);
        } else {
            ctx.drawText(textRenderer, "Modules", cx, 18, TEXT, false);
            int pw = cx + textRenderer.getWidth("Modules") + 6;
            ctx.drawText(textRenderer, "| Manage modules", pw, 18, TEXT_MU, false);
            int sbX = width - 214, sbY = 14;
            ctx.fill(sbX, sbY, sbX + 200, sbY + 20, ColorSettings.bgCard);
            drawBorder(ctx, sbX, sbY, 200, 20, BORDER);
            ctx.drawText(textRenderer, "Search modules...", sbX + 8, sbY + 6, 0xFF444465, false);
            renderForagingContent(ctx, mx, my, cx);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  FORAGING MODULE CARDS VIEW
    // ═════════════════════════════════════════════════════════════════════

    private void renderForagingContent(DrawContext ctx, int mx, int my, int cx) {
        int cy = HEADER_H + 20;

        ctx.drawText(textRenderer, "Foraging", cx, cy + 4, TEXT_MU, false);
        ctx.fill(cx + textRenderer.getWidth("Foraging") + 10, cy + 9, width - 24, cy + 10, DIVIDER);
        cy += 22;

        boolean cardHov = mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H;
        drawCard(ctx, cx, cy, CARD_W, CARD_H, cardHov);

        gearX = cx + CARD_W - 20;
        gearY = cy + 9;
        boolean gearHov = mx >= gearX && mx < gearX + GEAR_SZ + 4 && my >= gearY && my < gearY + GEAR_SZ + 4;
        ctx.drawText(textRenderer, "\u2699", gearX, gearY, gearHov ? GEAR_HOV : GEAR_N, false);

        ctx.drawText(textRenderer, "Auto Tree Feller",            cx + 12, cy + 11, TEXT,    false);
        ctx.drawText(textRenderer, "Automatically fells fig trees and", cx + 12, cy + 25, TEXT_MU, false);
        ctx.drawText(textRenderer, "navigates between them.",     cx + 12, cy + 35, TEXT_MU, false);

        ForagingModule.TreeType t = ForagingModule.getSelectedTree();
        ctx.drawText(textRenderer, "\u25CF " + t.display, cx + 12, cy + 58, ColorSettings.accentDim(), false);

        String kLabel = keyName(ForagingModule.getActivationKey());
        int kbW = textRenderer.getWidth(kLabel) + 12;
        int kbX = cx + CARD_W - kbW - 12, kbY = cy + CARD_H - 22;
        ctx.fill(kbX, kbY, kbX + kbW, kbY + 14, CHIP);
        drawBorder(ctx, kbX, kbY, kbW, 14, BORDER);
        ctx.drawText(textRenderer, kLabel, kbX + 6, kbY + 3, TEXT_MU, false);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  COLOR SETTINGS VIEW
    // ═════════════════════════════════════════════════════════════════════

    private void renderColorSettings(DrawContext ctx, int mx, int my, int cx) {
        int cy = HEADER_H + 18;
        int contentW = width - cx - 20;

        // ── Preset Themes ─────────────────────────────────────────────────
        sectionLabel(ctx, "Preset Themes", cx, cy, contentW);
        cy += 20;

        ColorSettings.Theme active = ColorSettings.activeTheme();
        themeSwY = cy;
        int swX = cx;
        for (int i = 0; i < ColorSettings.Theme.values().length; i++) {
            ColorSettings.Theme th = ColorSettings.Theme.values()[i];
            boolean sel = th == active;
            boolean hover = mx >= swX && mx < swX + SWATCH_W && my >= cy && my < cy + SWATCH_H;

            themeSwX[i] = swX;

            // swatch background = that theme's bg color
            ctx.fill(swX, cy, swX + SWATCH_W, cy + SWATCH_H, th.bgCard);
            // accent strip across top of swatch
            ctx.fill(swX, cy, swX + SWATCH_W, cy + 4, th.accent);
            // border
            drawBorder(ctx, swX, cy, SWATCH_W, SWATCH_H, sel ? th.accent : (hover ? 0xFF888888 : BORDER));
            // name label
            int nameW = textRenderer.getWidth(th.name);
            ctx.drawText(textRenderer, th.name, swX + (SWATCH_W - nameW) / 2, cy + 9, sel ? TEXT : TEXT_MU, false);
            // tick on active
            if (sel) ctx.drawText(textRenderer, "\u2713", swX + SWATCH_W - 10, cy + 1, th.accent, false);

            swX += SWATCH_W + 4;
        }
        cy += SWATCH_H + 14;

        // ── Custom Accent Color ───────────────────────────────────────────
        sectionLabel(ctx, "Custom Accent Color", cx, cy, contentW);
        cy += 20;

        int ar = (ColorSettings.accent >> 16) & 0xFF;
        int ag = (ColorSettings.accent >>  8) & 0xFF;
        int ab =  ColorSettings.accent        & 0xFF;

        cy = drawChannelSlider(ctx, mx, my, cx, cy, 0, "R", ar, ag, ab);
        cy = drawChannelSlider(ctx, mx, my, cx, cy, 1, "G", ar, ag, ab);
        cy = drawChannelSlider(ctx, mx, my, cx, cy, 2, "B", ar, ag, ab);

        // Hex swatch + label
        ctx.fill(cx, cy, cx + 14, cy + 10, ColorSettings.accent);
        drawBorder(ctx, cx, cy, 14, 10, BORDER);
        ctx.drawText(textRenderer, ColorSettings.accentHex(), cx + 18, cy + 1, TEXT_MU, false);
        cy += 18;

        // Divider
        ctx.fill(cx, cy, cx + contentW, cy + 1, DIVIDER);
        cy += 14;

        // ── Background Shade ──────────────────────────────────────────────
        sectionLabel(ctx, "Background Shade", cx, cy, contentW);
        cy += 20;

        bgBtnY = cy;
        int activeBg = ColorSettings.activeBgPreset();
        int bx = cx;
        for (int i = 0; i < ColorSettings.BG_PRESET_NAMES.length; i++) {
            boolean sel = activeBg == i;
            boolean hover = mx >= bx && mx < bx + BG_BTN_W && my >= cy && my < cy + BG_BTN_H;
            bgBtnX[i] = bx;
            ctx.fill(bx, cy, bx + BG_BTN_W, cy + BG_BTN_H, sel ? ColorSettings.accent : (hover ? CHIP_HOV : CHIP));
            drawBorder(ctx, bx, cy, BG_BTN_W, BG_BTN_H, sel ? ColorSettings.accent : BORDER);
            int lblW = textRenderer.getWidth(ColorSettings.BG_PRESET_NAMES[i]);
            ctx.drawText(textRenderer, ColorSettings.BG_PRESET_NAMES[i],
                    bx + (BG_BTN_W - lblW) / 2, cy + (BG_BTN_H - 8) / 2,
                    sel ? TEXT : TEXT_MU, false);
            bx += BG_BTN_W + 6;
        }
        cy += BG_BTN_H + 14;

        // Divider
        ctx.fill(cx, cy, cx + contentW, cy + 1, DIVIDER);
        cy += 12;

        // ── Reset button ──────────────────────────────────────────────────
        resetBtnX = cx;
        resetBtnY = cy;
        boolean resetHov = mx >= resetBtnX && mx < resetBtnX + RESET_W && my >= resetBtnY && my < resetBtnY + RESET_H;
        ctx.fill(resetBtnX, resetBtnY, resetBtnX + RESET_W, resetBtnY + RESET_H,
                resetHov ? 0xFF3A1A1A : 0xFF2A1212);
        drawBorder(ctx, resetBtnX, resetBtnY, RESET_W, RESET_H, resetHov ? 0xFF995555 : 0xFF5A3333);
        int rlW = textRenderer.getWidth("Reset to Default");
        ctx.drawText(textRenderer, "Reset to Default",
                resetBtnX + (RESET_W - rlW) / 2, resetBtnY + (RESET_H - 8) / 2, 0xFFCC7777, false);
    }

    /** Draws a single R/G/B gradient slider row, stores bounds in slBx/slBy[ch], returns next cy. */
    private int drawChannelSlider(DrawContext ctx, int mx, int my, int cx, int cy,
                                  int ch, String label, int r, int g, int b) {
        // Channel label
        ctx.drawText(textRenderer, label, cx, cy + 1, TEXT_MU, false);

        int barX = cx + 12;
        int barY = cy;
        slBx[ch] = barX;
        slBy[ch] = barY;

        // Gradient bar
        for (int i = 0; i < SL_W; i++) {
            int v = (i * 255) / SL_W;
            int col = switch (ch) {
                case 0 -> 0xFF000000 | (v << 16) | (g << 8) | b;
                case 1 -> 0xFF000000 | (r << 16) | (v << 8) | b;
                default -> 0xFF000000 | (r << 16) | (g << 8) | v;
            };
            ctx.fill(barX + i, barY, barX + i + 1, barY + SL_H, col);
        }
        drawBorder(ctx, barX, barY, SL_W, SL_H, BORDER);

        // Knob
        int curV = ch == 0 ? r : (ch == 1 ? g : b);
        int kx = barX + (curV * SL_W) / 255;
        boolean dragging = draggingSlider == ch;
        boolean hover = !dragging && mx >= barX && mx < barX + SL_W && my >= barY && my < barY + SL_H;
        ctx.fill(kx - 1, barY - 2, kx + 2, barY + SL_H + 2, 0xFFFFFFFF);
        ctx.fill(kx - 1, barY - 2, kx + 2, barY - 1, 0xFF888888);

        // Value label
        ctx.drawText(textRenderer, String.valueOf(curV), barX + SL_W + 6, cy + 1, TEXT, false);

        return cy + SL_H + 10;
    }

    private void sectionLabel(DrawContext ctx, String label, int cx, int cy, int contentW) {
        ctx.drawText(textRenderer, label, cx, cy + 4, TEXT_MU, false);
        int lw = textRenderer.getWidth(label);
        ctx.fill(cx + lw + 8, cy + 9, cx + contentW, cy + 10, DIVIDER);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  MODULE SETTINGS OVERLAY (gear icon → panel)
    // ═════════════════════════════════════════════════════════════════════

    private void renderModuleSettingsPanel(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, height, 0x88000000);

        panelX = (width  - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        ctx.fill(panelX + 4, panelY + 4, panelX + PANEL_W + 4, panelY + PANEL_H + 4, 0x44000000);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF1A1B30);
        drawBorder(ctx, panelX, panelY, PANEL_W, PANEL_H, BORDER);

        // Title bar
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 38, 0xFF13142A);
        ctx.fill(panelX, panelY + 38, panelX + PANEL_W, panelY + 39, DIVIDER);
        ctx.drawText(textRenderer, "Auto Tree Feller", panelX + 14, panelY + 14, TEXT, false);
        ctx.drawText(textRenderer, "Jump to:", panelX + PANEL_W - 132, panelY + 14, TEXT_MU, false);
        ctx.drawText(textRenderer, "Foraging Macro", panelX + PANEL_W - 90, panelY + 14, ColorSettings.accent, false);

        // Close X
        int closeX = panelX + PANEL_W - 18, closeY = panelY + 12;
        boolean closeHov = mx >= closeX - 4 && mx < closeX + 14 && my >= closeY - 4 && my < closeY + 14;
        ctx.drawText(textRenderer, "\u2715", closeX, closeY, closeHov ? CLOSE_HOV : 0xFF6666A0, false);

        int ry = panelY + 52;

        // Tree type chips
        ctx.drawText(textRenderer, "Tree Type", panelX + 14, ry, TEXT_MU, false);
        ry += 18;

        ForagingModule.TreeType selected = ForagingModule.getSelectedTree();
        int chipXCur = panelX + 14;
        chipHitY = ry;
        ForagingModule.TreeType[] types = ForagingModule.TreeType.values();
        for (int i = 0; i < types.length; i++) {
            ForagingModule.TreeType type = types[i];
            boolean supported = ForagingModule.isSupported(type);
            boolean sel = type == selected;
            int cw = textRenderer.getWidth(type.display) + 18;
            boolean chHov = supported && mx >= chipXCur && mx < chipXCur + cw && my >= ry && my < ry + 20;

            chipHitX[i] = chipXCur;
            chipHitW[i] = cw;

            int cbg = sel ? ColorSettings.accent : (!supported ? CHIP_DIS : (chHov ? CHIP_HOV : CHIP));
            ctx.fill(chipXCur, ry, chipXCur + cw, ry + 20, cbg);
            drawBorder(ctx, chipXCur, ry, cw, 20, sel ? ColorSettings.accent : BORDER);
            int col = sel ? TEXT : (!supported ? 0xFF404060 : (chHov ? TEXT_MU : 0xFF6666A0));
            ctx.drawText(textRenderer, type.display, chipXCur + 9, ry + 6, col, false);
            if (!supported)
                ctx.fill(chipXCur + 9, ry + 11,
                        chipXCur + 9 + textRenderer.getWidth(type.display), ry + 12, 0xFF383858);
            chipXCur += cw + 6;
        }
        ry += 26;

        if (!ForagingModule.isSupported(selected)) {
            ctx.fill(panelX + 14, ry, panelX + PANEL_W - 14, ry + 22, 0xFF2A1A00);
            drawBorder(ctx, panelX + 14, ry, PANEL_W - 28, 22, 0xFF5A3A00);
            ctx.drawText(textRenderer, "\u26A0 This tree type is not yet supported.", panelX + 20, ry + 7, WARN, false);
            ry += 28;
        } else {
            ry += 6;
        }

        ctx.fill(panelX + 14, ry, panelX + PANEL_W - 14, ry + 1, DIVIDER);
        ry += 14;

        // Keybind row
        ctx.drawText(textRenderer, "Activation Key",  panelX + 14, ry + 5, TEXT, false);
        ctx.drawText(textRenderer, "Toggle the macro", panelX + 14, ry + 16, TEXT_MU, false);

        kbBtnX = panelX + PANEL_W - 14 - KB_W;
        kbBtnY = ry;
        boolean kbHov = mx >= kbBtnX && mx < kbBtnX + KB_W && my >= kbBtnY && my < kbBtnY + KB_H;
        int kbBg = listeningForKey ? ColorSettings.kbListen() : (kbHov ? CHIP_HOV : KB_BG);
        ctx.fill(kbBtnX, kbBtnY, kbBtnX + KB_W, kbBtnY + KB_H, kbBg);
        drawBorder(ctx, kbBtnX, kbBtnY, KB_W, KB_H, listeningForKey ? ColorSettings.accent : BORDER);
        String kbText = listeningForKey ? "Press a key..." : keyName(ForagingModule.getActivationKey());
        ctx.drawText(textRenderer, kbText,
                kbBtnX + (KB_W - textRenderer.getWidth(kbText)) / 2, kbBtnY + (KB_H - 8) / 2,
                listeningForKey ? TEXT : TEXT_MU, false);
        ry += 34;

        ctx.fill(panelX + 14, ry, panelX + PANEL_W - 14, ry + 1, DIVIDER);
        ry += 14;

        ctx.drawText(textRenderer, "Show Stats HUD", panelX + 14, ry + 4, TEXT, false);
        drawToggle(ctx, panelX + PANEL_W - 44, ry, false);
        ry += 28;

        ctx.drawText(textRenderer, "Click the key box above to rebind.", panelX + 14, ry + 2, TEXT_MU, false);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  INPUT
    // ═════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int imx = (int) mx, imy = (int) my;

        // ── Module settings overlay ────────────────────────────────────────
        if (showSettings) {
            int px = panelX, py = panelY;
            int closeX = px + PANEL_W - 18;
            if (imx >= closeX - 4 && imx < closeX + 14 && imy >= py + 8 && imy < py + 28) {
                showSettings = false; listeningForKey = false; return true;
            }
            if (imx < px || imx >= px + PANEL_W || imy < py || imy >= py + PANEL_H) {
                showSettings = false; listeningForKey = false; return true;
            }
            ForagingModule.TreeType[] types = ForagingModule.TreeType.values();
            for (int i = 0; i < types.length; i++) {
                if (ForagingModule.isSupported(types[i])
                        && imx >= chipHitX[i] && imx < chipHitX[i] + chipHitW[i]
                        && imy >= chipHitY && imy < chipHitY + 20) {
                    ForagingModule.setSelectedTree(types[i]); return true;
                }
            }
            if (imx >= kbBtnX && imx < kbBtnX + KB_W && imy >= kbBtnY && imy < kbBtnY + KB_H) {
                listeningForKey = !listeningForKey; return true;
            }
            return true;
        }

        // ── Sidebar navigation ─────────────────────────────────────────────
        if (imx >= 0 && imx < SIDEBAR_W) {
            if (imy >= NAV_MODULES_Y  && imy < NAV_MODULES_Y  + 25) { currentView = "Foraging"; return true; }
            if (imy >= NAV_FORAGING_Y && imy < NAV_FORAGING_Y + 20) { currentView = "Foraging"; return true; }
            if (imy >= NAV_SETTINGS_Y && imy < NAV_SETTINGS_Y + 25) { currentView = "Settings"; return true; }
            return true;
        }

        // ── Gear icon (Foraging view only) ────────────────────────────────
        if (!"Settings".equals(currentView)) {
            if (imx >= gearX && imx < gearX + GEAR_SZ + 4 && imy >= gearY && imy < gearY + GEAR_SZ + 4) {
                showSettings = true; listeningForKey = false; return true;
            }
        }

        // ── Color settings interactions ────────────────────────────────────
        if ("Settings".equals(currentView)) {

            // Theme swatches
            for (int i = 0; i < ColorSettings.Theme.values().length; i++) {
                if (imx >= themeSwX[i] && imx < themeSwX[i] + SWATCH_W
                        && imy >= themeSwY && imy < themeSwY + SWATCH_H) {
                    ColorSettings.applyTheme(ColorSettings.Theme.values()[i]); return true;
                }
            }

            // Slider bars — start drag
            for (int ch = 0; ch < 3; ch++) {
                if (slBx[ch] == 0) continue; // not yet rendered
                if (imx >= slBx[ch] && imx < slBx[ch] + SL_W
                        && imy >= slBy[ch] - 3 && imy < slBy[ch] + SL_H + 3) {
                    draggingSlider = ch;
                    applySliderValue(ch, imx);
                    return true;
                }
            }

            // Background presets
            for (int i = 0; i < ColorSettings.BG_PRESET_NAMES.length; i++) {
                if (bgBtnX[i] == 0) continue;
                if (imx >= bgBtnX[i] && imx < bgBtnX[i] + BG_BTN_W
                        && imy >= bgBtnY && imy < bgBtnY + BG_BTN_H) {
                    ColorSettings.applyBgPreset(i); return true;
                }
            }

            // Reset button
            if (resetBtnX != 0 && imx >= resetBtnX && imx < resetBtnX + RESET_W
                    && imy >= resetBtnY && imy < resetBtnY + RESET_H) {
                ColorSettings.resetToDefault(); return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (draggingSlider >= 0) {
            applySliderValue(draggingSlider, (int) click.x());
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSlider = -1;
        return super.mouseReleased(click);
    }

    private void applySliderValue(int ch, int mouseX) {
        if (slBx[ch] == 0) return;
        int v = Math.max(0, Math.min(255, ((mouseX - slBx[ch]) * 255) / SL_W));
        int r = (ColorSettings.accent >> 16) & 0xFF;
        int g = (ColorSettings.accent >>  8) & 0xFF;
        int b =  ColorSettings.accent        & 0xFF;
        if (ch == 0) r = v;
        else if (ch == 1) g = v;
        else b = v;
        ColorSettings.accent = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (listeningForKey) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) ForagingModule.setActivationKey(keyCode);
            listeningForKey = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (showSettings) { showSettings = false; return true; }
            close(); return true;
        }
        return super.keyPressed(input);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DRAWING HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private void drawCard(DrawContext ctx, int x, int y, int w, int h, boolean hover) {
        ctx.fill(x, y, x + w, y + h, hover ? ColorSettings.cardHover() : ColorSettings.bgCard);
        drawBorder(ctx, x, y, w, h, BORDER);
        if (hover) ctx.fill(x, y + 1, x + 2, y + h - 1, ColorSettings.accentDim());
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x,         y,         x + w,     y + 1,     color);
        ctx.fill(x,         y + h - 1, x + w,     y + h,     color);
        ctx.fill(x,         y,         x + 1,     y + h,     color);
        ctx.fill(x + w - 1, y,         x + w,     y + h,     color);
    }

    private void drawToggle(DrawContext ctx, int x, int y, boolean on) {
        int bg = on ? ColorSettings.toggleOn() : TOGGLE_OFF;
        ctx.fill(x, y, x + 30, y + 14, bg);
        ctx.fill(x, y, x + 30, y + 1, on ? ColorSettings.lighten(ColorSettings.accent, 30) : 0xFF4A4B70);
        ctx.fill(x, y + 13, x + 30, y + 14, on ? ColorSettings.darken(ColorSettings.accent, 20) : 0xFF2A2B4A);
        ctx.fill(x, y, x + 1, y + 14, on ? ColorSettings.lighten(ColorSettings.accent, 30) : 0xFF4A4B70);
        ctx.fill(x + 29, y, x + 30, y + 14, on ? ColorSettings.darken(ColorSettings.accent, 20) : 0xFF2A2B4A);
        int kx = on ? x + 17 : x + 3;
        ctx.fill(kx, y + 2, kx + 10, y + 12, on ? 0xFFEEEEFF : 0xFF8888A8);
    }

    private void drawDot(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  KEY NAME
    // ═════════════════════════════════════════════════════════════════════

    public static String keyName(int key) {
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE        -> "SPACE";
            case GLFW.GLFW_KEY_ESCAPE       -> "ESC";
            case GLFW.GLFW_KEY_ENTER        -> "ENTER";
            case GLFW.GLFW_KEY_TAB          -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE    -> "BKSP";
            case GLFW.GLFW_KEY_INSERT       -> "INSERT";
            case GLFW.GLFW_KEY_DELETE       -> "DELETE";
            case GLFW.GLFW_KEY_HOME         -> "HOME";
            case GLFW.GLFW_KEY_END          -> "END";
            case GLFW.GLFW_KEY_PAGE_UP      -> "PG UP";
            case GLFW.GLFW_KEY_PAGE_DOWN    -> "PG DN";
            case GLFW.GLFW_KEY_UP           -> "UP";
            case GLFW.GLFW_KEY_DOWN         -> "DOWN";
            case GLFW.GLFW_KEY_LEFT         -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT        -> "RIGHT";
            case GLFW.GLFW_KEY_LEFT_SHIFT,
                 GLFW.GLFW_KEY_RIGHT_SHIFT  -> "SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL,
                 GLFW.GLFW_KEY_RIGHT_CONTROL -> "CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT,
                 GLFW.GLFW_KEY_RIGHT_ALT    -> "ALT";
            case GLFW.GLFW_KEY_F1  -> "F1";  case GLFW.GLFW_KEY_F2  -> "F2";
            case GLFW.GLFW_KEY_F3  -> "F3";  case GLFW.GLFW_KEY_F4  -> "F4";
            case GLFW.GLFW_KEY_F5  -> "F5";  case GLFW.GLFW_KEY_F6  -> "F6";
            case GLFW.GLFW_KEY_F7  -> "F7";  case GLFW.GLFW_KEY_F8  -> "F8";
            case GLFW.GLFW_KEY_F9  -> "F9";  case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11"; case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "KEY " + key;
        };
    }
}
