package com.rapidswing.chart;

import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartTest {

    private static BufferedImage paint(AbstractChart chart) {
        chart.setSize(480, 260);
        BufferedImage image = new BufferedImage(480, 260, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        chart.paint(g);
        g.dispose();
        return image;
    }

    private static Set<Integer> colors(BufferedImage image) {
        Set<Integer> found = new HashSet<>();
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                found.add(image.getRGB(x, y) & 0xffffff);
            }
        }
        return found;
    }

    @Test
    void ticksAreRoundAndLabelsCompact() {
        assertArrayEquals(new double[]{0, 5000, 10000, 15000}, AbstractChart.ticks(14_200, 4), 0.001);
        assertEquals("12K", AbstractChart.compact(12_000));
        assertEquals("1.5M", AbstractChart.compact(1_500_000));
        assertEquals("950", AbstractChart.compact(950));
    }

    @Test
    void barsAndLinesPaintEachSeriesInItsSlotColor() {
        Edt.runAndWait(() -> {
            BarChart bars = new BarChart();
            bars.categories(Arrays.asList("Jan", "Feb", "Mar")).series("Revenue", 12_000, 15_500, 14_200)
                    .series("Costs", 8_000, 9_000, 7_500);
            Set<Integer> barColors = colors(paint(bars));
            assertTrue(barColors.contains(ChartTheme.series(0).getRGB() & 0xffffff));
            assertTrue(barColors.contains(ChartTheme.series(1).getRGB() & 0xffffff));

            LineChart lines = new LineChart();
            lines.categories(Arrays.asList("W1", "W2", "W3", "W4")).series("Orders", 40, 55, 52, 70);
            assertTrue(colors(paint(lines)).contains(ChartTheme.series(0).getRGB() & 0xffffff));

            JTable table = bars.toTable();
            assertEquals(3, table.getRowCount());
            assertEquals("15.5K", table.getValueAt(1, 1));
        });
    }

    @Test
    void piesFoldSmallSegmentsIntoOtherAndKeepTheirOrder() {
        PieChart pie = new PieChart();
        pie.categories(Arrays.asList("A", "B", "C", "D", "E", "F", "G"))
                .series("Orders", 50, 1, 40, 30, 20, 10, 2);
        List<Object[]> segments = pie.segments();

        assertEquals(6, segments.size());
        assertEquals(Arrays.asList("A", "C", "D", "E", "F", "Other"),
                Arrays.asList(segments.stream().map(s -> s[0]).toArray()));
        assertEquals(3.0, (Double) segments.get(5)[1], 0.001);
        assertThrows(IllegalStateException.class, () -> pie.series("Second", 1, 2, 3, 4, 5, 6, 7));
    }

    @Test
    void thereIsNoNinthColor() {
        assertThrows(IllegalArgumentException.class, () -> ChartTheme.series(8));
        assertTrue(!ChartTheme.other().equals(new Color(0)));
    }
}
