package com.rapidswing.chart;

import com.formdev.flatlaf.FlatLaf;

import java.awt.Color;

/**
 * Colors for charts, in light and dark variants. Series colors are assigned in a fixed order that was checked
 * for color-blind separation in both themes: series keep their color when others are filtered out, and there
 * is never a ninth generated color (fold extra series into "Other").
 * <p>
 * In light mode, slots 3 to 5 have less than 3:1 contrast with the surface, so charts always offer a legend,
 * tooltips and a table view ({@link AbstractChart#toTable()}), and never rely on color alone.
 */
public final class ChartTheme {

    private static final Color[] LIGHT = {
            new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a), new Color(0xeda100),
            new Color(0xe87ba4), new Color(0x008300), new Color(0x6250d6), new Color(0xe34948)};
    private static final Color[] DARK = {
            new Color(0x3987e5), new Color(0xd95926), new Color(0x199e70), new Color(0xc98500),
            new Color(0xd55181), new Color(0x008300), new Color(0x9085e9), new Color(0xe66767)};

    /** The number of distinct series colors. */
    public static final int SLOTS = LIGHT.length;

    private ChartTheme() {
    }

    /** The color of series slot {@code index} (from 0), for the current theme. */
    public static Color series(int index) {
        if (index < 0 || index >= SLOTS) {
            throw new IllegalArgumentException("Charts have " + SLOTS + " series colors; fold the rest into 'Other'");
        }
        return (dark() ? DARK : LIGHT)[index];
    }

    /** "Other" segments: a neutral that is not a series color. */
    public static Color other() {
        return dark() ? new Color(0x5f5e5a) : new Color(0xb5b3ac);
    }

    public static Color surface() {
        return dark() ? new Color(0x1a1a19) : new Color(0xfcfcfb);
    }

    public static Color primaryText() {
        return dark() ? new Color(0xf0efec) : new Color(0x0b0b0b);
    }

    public static Color secondaryText() {
        return dark() ? new Color(0xc3c2b7) : new Color(0x52514e);
    }

    public static Color mutedText() {
        return new Color(0x898781);
    }

    public static Color grid() {
        return dark() ? new Color(0x2c2c2a) : new Color(0xe1e0d9);
    }

    public static Color baseline() {
        return dark() ? new Color(0x383835) : new Color(0xc3c2b7);
    }

    public static boolean dark() {
        return FlatLaf.isLafDark();
    }
}
