package com.rapidswing.chart;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/**
 * Lines across categories: for change over time, e.g. orders per week. Lines are 2px; up to four series are
 * also named at their line ends; hovering shows a crosshair with every series' value at that point.
 * <pre>
 * LineChart chart = new LineChart();
 * chart.categories(weeks).series("Orders", orders).series("Returns", returns);
 * </pre>
 */
public class LineChart extends CartesianChart {

    static final int DIRECT_LABEL_LIMIT = 4;

    public LineChart() {
        setPreferredSize(new Dimension(480, 260));
    }

    @Override
    protected void paintChart(Graphics2D g, int width, int height) {
        int widest = 0;
        if (series.size() <= DIRECT_LABEL_LIMIT) {
            FontMetrics metrics = g.getFontMetrics();
            for (Series one : series) {
                widest = Math.max(widest, metrics.stringWidth(one.name));
            }
        }
        rightPadding = widest > 0 && series.size() > 1 ? widest + 20 : 12;
        super.paintChart(g, width, height);
    }

    @Override
    protected void paintData(Graphics2D g) {
        if (hovered >= 0) {
            g.setColor(ChartTheme.baseline());
            g.setStroke(new BasicStroke(1f));
            g.drawLine(bandCenter(hovered), plot.y, bandCenter(hovered), plot.y + plot.height);
        }
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int s = 0; s < series.size(); s++) {
            Path2D line = new Path2D.Double();
            double[] values = series.get(s).values;
            for (int c = 0; c < values.length; c++) {
                if (c == 0) {
                    line.moveTo(bandCenter(c), yOf(values[c]));
                } else {
                    line.lineTo(bandCenter(c), yOf(values[c]));
                }
            }
            g.setColor(ChartTheme.series(s));
            g.draw(line);
        }
        if (hovered >= 0) {
            for (int s = 0; s < series.size(); s++) {
                int x = bandCenter(hovered);
                int y = yOf(series.get(s).values[hovered]);
                g.setColor(ChartTheme.surface());
                g.fillOval(x - 6, y - 6, 12, 12);
                g.setColor(ChartTheme.series(s));
                g.fillOval(x - 4, y - 4, 8, 8);
            }
        }
        paintEndLabels(g);
    }

    /** Names at the line ends, nudged apart so they do not overlap. */
    private void paintEndLabels(Graphics2D g) {
        if (series.size() < 2 || series.size() > DIRECT_LABEL_LIMIT || categories.isEmpty()) {
            return;
        }
        FontMetrics metrics = g.getFontMetrics();
        int last = categories.size() - 1;
        Integer[] order = new Integer[series.size()];
        int[] ys = new int[series.size()];
        for (int s = 0; s < series.size(); s++) {
            order[s] = s;
            ys[s] = yOf(series.get(s).values[last]);
        }
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(ys[a], ys[b]));
        int previous = Integer.MIN_VALUE;
        for (int index : order) {
            int y = Math.max(ys[index], previous + metrics.getHeight());
            previous = y;
            int x = bandCenter(last) + 10;
            g.setColor(ChartTheme.series(index));
            g.fillOval(x, y - 3, 6, 6);
            g.setColor(ChartTheme.secondaryText());
            g.drawString(series.get(index).name, x + 10, y + metrics.getAscent() / 2 - 1);
        }
    }
}
