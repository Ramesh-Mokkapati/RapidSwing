package com.rapidswing.chart;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.ToolTipManager;
import javax.swing.table.DefaultTableModel;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.DoubleFunction;

/**
 * Shared parts of the charts: categories with one or more named series of values, a legend for two or more
 * series, hover tooltips, compact number labels, and a {@link #toTable() table view} of the same data.
 * Charts follow the light or dark theme when repainted.
 */
public abstract class AbstractChart extends JComponent {

    /** A named row of values, one per category. */
    public static final class Series {
        final String name;
        final double[] values;

        Series(String name, double[] values) {
            this.name = Objects.requireNonNull(name, "name");
            this.values = values.clone();
        }

        public String getName() {
            return name;
        }

        public double value(int category) {
            return values[category];
        }
    }

    protected final List<String> categories = new ArrayList<>();
    protected final List<Series> series = new ArrayList<>();
    protected DoubleFunction<String> valueFormat = AbstractChart::compact;
    /** The hovered category (or segment), or -1. */
    protected int hovered = -1;

    protected AbstractChart() {
        setOpaque(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHovered(hitTest(e.getX(), e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHovered(-1);
            }
        };
        addMouseListener(hover);
        addMouseMotionListener(hover);
    }

    /** The categories along the axis (or the segments of a pie), e.g. months. */
    public AbstractChart categories(List<String> names) {
        categories.clear();
        categories.addAll(names);
        repaint();
        return this;
    }

    /** Adds a series with one value per category. Series take the theme's colors in the order added. */
    public AbstractChart series(String name, double... values) {
        if (values.length != categories.size()) {
            throw new IllegalArgumentException("Series '" + name + "' has " + values.length + " values for "
                    + categories.size() + " categories");
        }
        if (series.size() >= maxSeries()) {
            throw new IllegalStateException("This chart shows at most " + maxSeries() + " series");
        }
        series.add(new Series(name, values));
        repaint();
        return this;
    }

    public AbstractChart clearSeries() {
        series.clear();
        repaint();
        return this;
    }

    /** How values are written in labels and tooltips; defaults to compact numbers (1.2K, 3.4M). */
    public AbstractChart valueFormat(DoubleFunction<String> format) {
        this.valueFormat = Objects.requireNonNull(format, "format");
        repaint();
        return this;
    }

    public List<String> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    public List<Series> getSeries() {
        return Collections.unmodifiableList(series);
    }

    /** The same data as a table, for screen readers, printing and anyone who prefers numbers. */
    public JTable toTable() {
        Object[] header = new Object[series.size() + 1];
        header[0] = "";
        for (int s = 0; s < series.size(); s++) {
            header[s + 1] = series.get(s).name;
        }
        DefaultTableModel model = new DefaultTableModel(header, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (int c = 0; c < categories.size(); c++) {
            Object[] row = new Object[series.size() + 1];
            row[0] = categories.get(c);
            for (int s = 0; s < series.size(); s++) {
                row[s + 1] = valueFormat.apply(series.get(s).values[c]);
            }
            model.addRow(row);
        }
        return new JTable(model);
    }

    /** Whether there is anything to draw. */
    public boolean isEmpty() {
        return categories.isEmpty() || series.isEmpty();
    }

    /** 0, 950, 1.2K, 3.4M: rounded, compact from a thousand up. */
    public static String compact(double value) {
        double magnitude = Math.abs(value);
        NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault());
        format.setMaximumFractionDigits(1);
        if (magnitude >= 1_000_000_000) {
            return format.format(value / 1_000_000_000) + "B";
        }
        if (magnitude >= 1_000_000) {
            return format.format(value / 1_000_000) + "M";
        }
        if (magnitude >= 1_000) {
            return format.format(value / 1_000) + "K";
        }
        format.setMaximumFractionDigits(magnitude < 10 ? 2 : 0);
        return format.format(value);
    }

    /** Rounded axis ticks from 0 to at least {@code max}: 0, 2K, 4K... */
    static double[] ticks(double max, int approximateCount) {
        if (max <= 0) {
            return new double[]{0, 1};
        }
        double rough = max / approximateCount;
        double power = Math.pow(10, Math.floor(Math.log10(rough)));
        double step = power;
        for (double factor : new double[]{1, 2, 2.5, 5, 10}) {
            step = factor * power;
            if (step >= rough) {
                break;
            }
        }
        int count = (int) Math.ceil(max / step);
        double[] ticks = new double[count + 1];
        for (int i = 0; i <= count; i++) {
            ticks[i] = i * step;
        }
        return ticks;
    }

    protected int maxSeries() {
        return ChartTheme.SLOTS;
    }

    /** The category or segment under the point, or -1. */
    protected abstract int hitTest(int x, int y);

    protected abstract void paintChart(Graphics2D g, int width, int height);

    @Override
    protected final void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(ChartTheme.surface());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setFont(getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            if (isEmpty()) {
                g2.setColor(ChartTheme.mutedText());
                String text = "-";
                FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2, getHeight() / 2);
            } else {
                paintChart(g2, getWidth(), getHeight());
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Draws the legend (swatch + name, in text color) in one row from the top left; returns its height. A single
     * series has no legend: the surrounding title names it.
     */
    protected int paintLegend(Graphics2D g, int x, int y, List<String> names, List<java.awt.Color> colors) {
        if (names.size() < 2) {
            return 0;
        }
        FontMetrics metrics = g.getFontMetrics();
        int cursor = x;
        for (int i = 0; i < names.size(); i++) {
            g.setColor(colors.get(i));
            g.fillRoundRect(cursor, y + (metrics.getHeight() - 10) / 2, 10, 10, 3, 3);
            g.setColor(ChartTheme.secondaryText());
            g.drawString(names.get(i), cursor + 15, y + metrics.getAscent());
            cursor += 15 + metrics.stringWidth(names.get(i)) + 16;
        }
        return metrics.getHeight() + 10;
    }

    protected List<java.awt.Color> seriesColors() {
        List<java.awt.Color> colors = new ArrayList<>();
        for (int s = 0; s < series.size(); s++) {
            colors.add(ChartTheme.series(s));
        }
        return colors;
    }

    protected List<String> seriesNames() {
        List<String> names = new ArrayList<>();
        for (Series one : series) {
            names.add(one.name);
        }
        return names;
    }

    /** "March — Revenue: 12K, Costs: 8K", for tooltips. */
    protected String describeCategory(int category) {
        StringBuilder text = new StringBuilder("<html><b>").append(escape(categories.get(category))).append("</b>");
        for (Series one : series) {
            text.append("<br>").append(escape(one.name)).append(": ").append(escape(valueFormat.apply(one.values[category])));
        }
        return text.append("</html>").toString();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = hitTest(event.getX(), event.getY());
        return index < 0 ? null : tooltip(index);
    }

    protected String tooltip(int index) {
        return describeCategory(index);
    }

    private void setHovered(int index) {
        if (index != hovered) {
            hovered = index;
            repaint();
        }
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
