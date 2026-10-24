package com.rapidswing.table;

import com.formdev.flatlaf.FlatLaf;

import java.awt.Color;
import java.awt.Font;
import java.util.Objects;

/**
 * How a cell stands out: text color, background and weight. Use the semantic styles
 * ({@link #ERROR}, {@link #WARNING}, {@link #SUCCESS}, {@link #MUTED}); their colors suit both light and dark
 * themes. Selected rows keep the selection colors, so styles never hide the selection.
 * <pre>
 * .column("Balance", BigDecimal.class, Account::getBalance).style(b -&gt; b.signum() &lt; 0 ? CellStyle.ERROR : null)
 * grid.rowStyle(order -&gt; order.isOverdue() ? CellStyle.WARNING : null);
 * </pre>
 */
public final class CellStyle {

    public static final CellStyle ERROR = semantic(new Color(0xB3261E), new Color(0xFF8A80), new Color(0xFDECEA), new Color(0x4A2423));
    public static final CellStyle WARNING = semantic(new Color(0x8A5300), new Color(0xFFCC80), new Color(0xFFF4E0), new Color(0x46371C));
    public static final CellStyle SUCCESS = semantic(new Color(0x1E6B34), new Color(0x8BD8A0), new Color(0xE7F5EC), new Color(0x1F3B28));
    public static final CellStyle MUTED = new CellStyle(new Color(0x6E6E73), new Color(0x9A9AA0), null, null, Font.PLAIN);
    public static final CellStyle BOLD = new CellStyle(null, null, null, null, Font.BOLD);

    private final Color lightForeground;
    private final Color darkForeground;
    private final Color lightBackground;
    private final Color darkBackground;
    private final int fontStyle;

    private CellStyle(Color lightForeground, Color darkForeground, Color lightBackground, Color darkBackground,
                      int fontStyle) {
        this.lightForeground = lightForeground;
        this.darkForeground = darkForeground;
        this.lightBackground = lightBackground;
        this.darkBackground = darkBackground;
        this.fontStyle = fontStyle;
    }

    private static CellStyle semantic(Color lightText, Color darkText, Color lightFill, Color darkFill) {
        return new CellStyle(lightText, darkText, lightFill, darkFill, Font.PLAIN);
    }

    /** Text color only, the same in all themes. */
    public static CellStyle foreground(Color color) {
        Objects.requireNonNull(color, "color");
        return new CellStyle(color, color, null, null, Font.PLAIN);
    }

    /** Background only, the same in all themes; pick a color that keeps the text readable. */
    public static CellStyle background(Color color) {
        Objects.requireNonNull(color, "color");
        return new CellStyle(null, null, color, color, Font.PLAIN);
    }

    /** This style with bold text. */
    public CellStyle bold() {
        return new CellStyle(lightForeground, darkForeground, lightBackground, darkBackground, Font.BOLD);
    }

    /** This style without a background, e.g. to color only the text of an error. */
    public CellStyle textOnly() {
        return new CellStyle(lightForeground, darkForeground, null, null, fontStyle);
    }

    /** The text color for the current theme, or {@code null} to keep the default. */
    public Color getForeground() {
        return dark() ? darkForeground : lightForeground;
    }

    /** The background for the current theme, or {@code null} to keep the default. */
    public Color getBackground() {
        return dark() ? darkBackground : lightBackground;
    }

    public boolean isBold() {
        return (fontStyle & Font.BOLD) != 0;
    }

    /** This style with the other's settings on top, e.g. a column style over a row style. */
    CellStyle overlay(CellStyle other) {
        if (other == null) {
            return this;
        }
        return new CellStyle(
                other.lightForeground != null ? other.lightForeground : lightForeground,
                other.darkForeground != null ? other.darkForeground : darkForeground,
                other.lightBackground != null ? other.lightBackground : lightBackground,
                other.darkBackground != null ? other.darkBackground : darkBackground,
                fontStyle | other.fontStyle);
    }

    private static boolean dark() {
        return FlatLaf.isLafDark();
    }
}
