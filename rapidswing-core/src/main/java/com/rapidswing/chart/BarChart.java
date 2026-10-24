package com.rapidswing.chart;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Columns per category, side by side for several series: for comparing amounts, e.g. revenue per month.
 * Columns are at most 24px wide with a 2px gap and rounded tops; hovering a category highlights it and shows
 * its values.
 * <pre>
 * BarChart chart = new BarChart();
 * chart.categories(Arrays.asList("Jan", "Feb", "Mar")).series("Revenue", 12_000, 15_500, 14_200);
 * </pre>
 * For a single number, show the number instead of a one-bar chart.
 */
public class BarChart extends CartesianChart {

    static final int MAX_BAR = 24;
    static final int GAP = 2;

    public BarChart() {
        setPreferredSize(new Dimension(480, 260));
    }

    @Override
    protected void paintData(Graphics2D g) {
        int count = series.size();
        int band = bandWidth();
        int bar = Math.max(2, Math.min(MAX_BAR, (int) ((band * 0.7 - GAP * (count - 1)) / count)));
        int group = bar * count + GAP * (count - 1);
        int baseline = yOf(0);
        for (int c = 0; c < categories.size(); c++) {
            if (c == hovered) {
                g.setColor(ChartTheme.grid());
                g.fillRect(plot.x + band * c + 2, plot.y, band - 4, plot.height);
            }
            int left = bandCenter(c) - group / 2;
            for (int s = 0; s < count; s++) {
                int top = yOf(series.get(s).values[c]);
                int height = baseline - top;
                if (height <= 0) {
                    continue;
                }
                g.setColor(ChartTheme.series(s));
                g.fill(column(left + s * (bar + GAP), top, bar, height));
            }
        }
    }

    /** A column with a 4px rounded top and a square foot on the baseline. */
    static Area column(int x, int top, int width, int height) {
        int radius = Math.min(4, Math.min(width / 2, height));
        Area shape = new Area(new RoundRectangle2D.Double(x, top, width, height, radius * 2, radius * 2));
        shape.add(new Area(new Rectangle2D.Double(x, top + radius, width, height - radius)));
        return shape;
    }
}
