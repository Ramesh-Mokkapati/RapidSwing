package com.rapidswing.chart;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Charts with categories along the bottom and one value axis starting at zero: hairline gridlines, compact tick
 * labels, category labels that thin out when crowded. One axis only; show measures of different scale in
 * separate charts.
 */
public abstract class CartesianChart extends AbstractChart {

    /** Where the data is drawn, computed while painting. */
    protected final Rectangle plot = new Rectangle();
    protected double axisMax = 1;
    /** Extra room right of the plot, e.g. for line-end labels. */
    protected int rightPadding = 12;

    /** The largest value, for the axis; values below zero are drawn at the baseline. */
    protected double maxValue() {
        double max = 0;
        for (Series one : series) {
            for (double value : one.values) {
                max = Math.max(max, value);
            }
        }
        return max;
    }

    protected int bandWidth() {
        return categories.isEmpty() ? plot.width : plot.width / categories.size();
    }

    protected int bandCenter(int category) {
        return plot.x + bandWidth() * category + bandWidth() / 2;
    }

    protected int yOf(double value) {
        double clamped = Math.max(0, value);
        return plot.y + plot.height - (int) Math.round(clamped / axisMax * plot.height);
    }

    @Override
    protected void paintChart(Graphics2D g, int width, int height) {
        FontMetrics metrics = g.getFontMetrics();
        int top = 8 + paintLegend(g, 8, 8, seriesNames(), seriesColors());
        double[] ticks = ticks(maxValue(), Math.max(2, Math.min(5, (height - top) / 50)));
        axisMax = ticks[ticks.length - 1];
        int labelWidth = 0;
        for (double tick : ticks) {
            labelWidth = Math.max(labelWidth, metrics.stringWidth(valueFormat.apply(tick)));
        }
        plot.setBounds(8 + labelWidth + 8, top + metrics.getAscent() / 2,
                Math.max(10, width - (8 + labelWidth + 8) - rightPadding),
                Math.max(10, height - top - metrics.getAscent() / 2 - metrics.getHeight() - 10));

        g.setStroke(new BasicStroke(1f));
        for (double tick : ticks) {
            int y = yOf(tick);
            g.setColor(tick == 0 ? ChartTheme.baseline() : ChartTheme.grid());
            g.drawLine(plot.x, y, plot.x + plot.width, y);
            String label = valueFormat.apply(tick);
            g.setColor(ChartTheme.mutedText());
            g.drawString(label, plot.x - 8 - metrics.stringWidth(label), y + metrics.getAscent() / 2 - 1);
        }
        paintCategoryLabels(g, metrics);
        paintData(g);
    }

    private void paintCategoryLabels(Graphics2D g, FontMetrics metrics) {
        int widest = 0;
        for (String category : categories) {
            widest = Math.max(widest, metrics.stringWidth(category));
        }
        int every = Math.max(1, (int) Math.ceil((widest + 12.0) / Math.max(1, bandWidth())));
        int y = plot.y + plot.height + 6 + metrics.getAscent();
        for (int c = 0; c < categories.size(); c += every) {
            String label = categories.get(c);
            g.setColor(c == hovered ? ChartTheme.primaryText() : ChartTheme.mutedText());
            g.drawString(label, bandCenter(c) - metrics.stringWidth(label) / 2, y);
        }
    }

    /** Draws the bars or lines into {@link #plot}. */
    protected abstract void paintData(Graphics2D g);

    /** The category band under the point, anywhere in the plot's height. */
    @Override
    protected int hitTest(int x, int y) {
        if (categories.isEmpty() || x < plot.x || x >= plot.x + plot.width || y < plot.y - 4
                || y > plot.y + plot.height + 4) {
            return -1;
        }
        return Math.min(categories.size() - 1, (x - plot.x) / Math.max(1, bandWidth()));
    }
}
