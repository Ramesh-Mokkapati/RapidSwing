package com.rapidswing.chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

/**
 * A donut of shares of a whole, e.g. orders per channel: for part-to-whole at a glance, not for comparing close
 * values (use a {@link BarChart}). At most six segments are shown; smaller ones are folded into "Other". The
 * legend lists every segment with its value and share; hovering a segment highlights it.
 * <pre>
 * PieChart chart = new PieChart();
 * chart.categories(Arrays.asList("Web", "Shop", "Phone")).series("Orders", 540, 210, 80);
 * </pre>
 */
public class PieChart extends AbstractChart {

    static final int MAX_SEGMENTS = 6;

    private String otherLabel = "Other";
    private final List<double[]> arcs = new ArrayList<>();
    private int centerX;
    private int centerY;
    private int outer;
    private int inner;

    public PieChart() {
        setPreferredSize(new Dimension(420, 240));
    }

    /** The name of the folded segment, e.g. a translation of "Other". */
    public PieChart otherLabel(String label) {
        this.otherLabel = label;
        repaint();
        return this;
    }

    @Override
    protected int maxSeries() {
        return 1;
    }

    /** The shown segments: the largest five plus "Other" when there are more than six. Names and values. */
    public List<Object[]> segments() {
        List<Object[]> all = new ArrayList<>();
        if (series.isEmpty()) {
            return all;
        }
        for (int c = 0; c < categories.size(); c++) {
            all.add(new Object[]{categories.get(c), Math.max(0, series.get(0).values[c])});
        }
        if (all.size() <= MAX_SEGMENTS) {
            return all;
        }
        // Keep the largest five in their given order, so colors follow the categories rather than their rank.
        List<Object[]> bySize = new ArrayList<>(all);
        bySize.sort((a, b) -> Double.compare((Double) b[1], (Double) a[1]));
        List<Object[]> largest = bySize.subList(0, MAX_SEGMENTS - 1);
        List<Object[]> shown = new ArrayList<>();
        double rest = 0;
        for (Object[] segment : all) {
            if (largest.contains(segment)) {
                shown.add(segment);
            } else {
                rest += (Double) segment[1];
            }
        }
        shown.add(new Object[]{otherLabel, rest});
        return shown;
    }

    private Color colorOf(int index, int count) {
        boolean folded = categories.size() > MAX_SEGMENTS;
        return folded && index == count - 1 ? ChartTheme.other() : ChartTheme.series(index);
    }

    @Override
    protected void paintChart(Graphics2D g, int width, int height) {
        List<Object[]> segments = segments();
        double total = 0;
        for (Object[] segment : segments) {
            total += (Double) segment[1];
        }
        FontMetrics metrics = g.getFontMetrics();
        // The legend keeps its full width; the donut takes what is left.
        int legendWidth = 0;
        for (Object[] segment : segments) {
            double value = (Double) segment[1];
            legendWidth = Math.max(legendWidth, 16 + metrics.stringWidth(legendText(segment[0], value, total)));
        }
        outer = Math.max(20, Math.min(height - 24, width - legendWidth - 24 - 24 - 12) / 2);
        inner = (int) (outer * 0.6);
        centerX = 12 + outer;
        centerY = height / 2;
        arcs.clear();

        double start = 90;
        for (int i = 0; i < segments.size(); i++) {
            double share = total == 0 ? 0 : (Double) segments.get(i)[1] / total;
            double extent = -share * 360;
            arcs.add(new double[]{start, extent});
            Area slice = new Area(new Arc2D.Double(centerX - outer, centerY - outer, outer * 2, outer * 2, start, extent,
                    Arc2D.PIE));
            int grow = i == hovered ? 4 : 0;
            if (grow > 0) {
                slice = new Area(new Arc2D.Double(centerX - outer - grow, centerY - outer - grow, (outer + grow) * 2,
                        (outer + grow) * 2, start, extent, Arc2D.PIE));
            }
            slice.subtract(new Area(new Ellipse2D.Double(centerX - inner, centerY - inner, inner * 2, inner * 2)));
            g.setColor(colorOf(i, segments.size()));
            g.fill(slice);
            g.setColor(ChartTheme.surface());
            g.setStroke(new BasicStroke(2f));
            g.draw(slice);
            start += extent;
        }

        String totalText = valueFormat.apply(total);
        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, g.getFont().getSize2D() + 4));
        FontMetrics big = g.getFontMetrics();
        g.setColor(ChartTheme.primaryText());
        g.drawString(totalText, centerX - big.stringWidth(totalText) / 2, centerY + big.getAscent() / 2 - 2);
        g.setFont(g.getFont().deriveFont(java.awt.Font.PLAIN, g.getFont().getSize2D() - 4));

        int x = centerX + outer + 24;
        int y = centerY - segments.size() * (metrics.getHeight() + 4) / 2;
        for (int i = 0; i < segments.size(); i++) {
            Object[] segment = segments.get(i);
            double value = (Double) segment[1];
            g.setColor(colorOf(i, segments.size()));
            g.fillRoundRect(x, y + (metrics.getHeight() - 10) / 2, 10, 10, 3, 3);
            g.setColor(i == hovered ? ChartTheme.primaryText() : ChartTheme.secondaryText());
            g.drawString(legendText(segment[0], value, total), x + 16, y + metrics.getAscent());
            y += metrics.getHeight() + 4;
        }
    }

    /** "Europe  71.9K (58%)". */
    private String legendText(Object name, double value, double total) {
        String share = total == 0 ? "" : String.format(" (%d%%)", Math.round(value / total * 100));
        return name + "  " + valueFormat.apply(value) + share;
    }

    @Override
    protected int hitTest(int x, int y) {
        double dx = x - centerX;
        double dy = centerY - y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (arcs.isEmpty() || distance < inner - 2 || distance > outer + 6) {
            return -1;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        for (int i = 0; i < arcs.size(); i++) {
            double start = arcs.get(i)[0];
            double end = start + arcs.get(i)[1];
            for (double candidate = angle - 720; candidate <= angle + 720; candidate += 360) {
                if (candidate <= start && candidate >= end) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    protected String tooltip(int index) {
        List<Object[]> segments = segments();
        if (index >= segments.size()) {
            return null;
        }
        return "<html><b>" + escape(String.valueOf(segments.get(index)[0])) + "</b><br>"
                + escape(valueFormat.apply((Double) segments.get(index)[1])) + "</html>";
    }
}
