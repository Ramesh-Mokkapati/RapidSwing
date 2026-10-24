package com.rapidswing.generator;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A small schematic picture of an application type's window, for the wizard. Follows the theme's colors. */
final class TypePreview extends JComponent {

    private final AppType type;

    TypePreview(AppType type) {
        this.type = type;
        setPreferredSize(new Dimension(150, 96));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color background = color("Panel.background", Color.LIGHT_GRAY);
            Color window = color("TextField.background", Color.WHITE);
            Color line = color("Component.borderColor", Color.GRAY);
            Color accent = color("Component.accentColor", new Color(0x2675BF));
            Color text = color("Label.disabledForeground", Color.GRAY);

            int width = type == AppType.DIALOG ? 100 : getWidth() - 2;
            int height = type == AppType.DIALOG ? 70 : getHeight() - 2;
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            g.setColor(background);
            g.fillRoundRect(x, y, width, height, 6, 6);
            g.setColor(line);
            g.drawRoundRect(x, y, width, height, 6, 6);
            g.setColor(accent);
            g.fillRect(x + 1, y + 1, width - 1, 8);
            int top = y + 10;
            if (type != AppType.DIALOG) {
                bars(g, x + 4, top + 1, 4, 14, text);
                top += 7;
            }
            switch (type) {
                case SDI:
                    g.setColor(window);
                    g.fillRect(x + 4, top + 2, width - 8, y + height - top - 6);
                    lines(g, x + 8, top + 7, width - 24, 6, text);
                    break;
                case MDI:
                    g.setColor(line.darker());
                    g.fillRect(x + 4, top + 2, width - 8, y + height - top - 6);
                    childWindow(g, x + 10, top + 6, 70, 44, window, line, accent, text);
                    childWindow(g, x + 50, top + 22, 80, 44, window, line, accent, text);
                    break;
                case DIALOG:
                    for (int row = 0; row < 3; row++) {
                        int rowY = top + 6 + row * 12;
                        g.setColor(text);
                        g.fillRect(x + 8, rowY + 2, 22, 3);
                        g.setColor(window);
                        g.fillRect(x + 36, rowY, width - 46, 8);
                        g.setColor(line);
                        g.drawRect(x + 36, rowY, width - 46, 8);
                    }
                    g.setColor(accent);
                    g.fillRoundRect(x + width - 34, y + height - 14, 26, 9, 4, 4);
                    break;
                default:
                    int navigation = 34;
                    g.setColor(window);
                    g.fillRect(x + 4, top + 2, navigation, y + height - top - 6);
                    lines(g, x + 8, top + 6, navigation - 10, 3, text);
                    for (int button = 0; button < 3; button++) {
                        g.setColor(button == 0 ? accent : line);
                        g.fillRect(x + 6, y + height - 26 + button * 7, navigation - 4, 5);
                    }
                    int listX = x + navigation + 7;
                    int listWidth = (width - navigation - 15) / 2;
                    g.setColor(window);
                    g.fillRect(listX, top + 2, listWidth, y + height - top - 6);
                    for (int row = 0; row < 5; row++) {
                        g.setColor(row == 1 ? accent : text);
                        g.fillRect(listX + 3, top + 6 + row * 9, listWidth - 6, 4);
                    }
                    g.setColor(window);
                    g.fillRect(listX + listWidth + 4, top + 2, listWidth, y + height - top - 6);
                    lines(g, listX + listWidth + 8, top + 6, listWidth - 10, 5, text);
            }
        } finally {
            g.dispose();
        }
    }

    private static void childWindow(Graphics2D g, int x, int y, int width, int height, Color window, Color line,
                                    Color accent, Color text) {
        g.setColor(window);
        g.fillRect(x, y, width, height);
        g.setColor(line);
        g.drawRect(x, y, width, height);
        g.setColor(accent);
        g.fillRect(x + 1, y + 1, width - 1, 6);
        lines(g, x + 4, y + 11, width - 12, 3, text);
    }

    private static void bars(Graphics2D g, int x, int y, int count, int width, Color color) {
        g.setColor(color);
        for (int i = 0; i < count; i++) {
            g.fillRect(x + i * (width + 4), y, width, 3);
        }
    }

    private static void lines(Graphics2D g, int x, int y, int width, int count, Color color) {
        g.setColor(color);
        for (int i = 0; i < count; i++) {
            g.fillRect(x, y + i * 7, i % 3 == 2 ? width * 2 / 3 : width, 3);
        }
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
