package com.rapidswing.app;

import com.rapidswing.i18n.I18n;
import com.rapidswing.security.User;
import com.rapidswing.util.Edt;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What support needs to know: application and version, user and roles,
 * language, Java, operating system and memory. "Copy to Clipboard" puts it all into a bug report.
 * Set {@code app.version} in the configuration, or it is read from the jar manifest.
 */
public final class SystemInfo {

    private SystemInfo() {
    }

    /** Label to value, in display order. */
    public static Map<String, String> collect(AppContext ctx) {
        I18n i18n = I18n.global();
        Map<String, String> info = new LinkedHashMap<>();
        info.put(i18n.get("sysinfo.application"), ctx.config().get("app.title", ctx.mainFrame().getTitle()));
        String version = ctx.config().get("app.version", null);
        if (version == null) {
            Package appPackage = ctx.getClass().getPackage();
            version = appPackage != null ? appPackage.getImplementationVersion() : null;
        }
        info.put(i18n.get("sysinfo.version"), version != null ? version : "-");
        User user = ctx.session().getUser();
        if (user == null) {
            info.put(i18n.get("sysinfo.user"), i18n.get("sysinfo.notSignedIn"));
        } else {
            info.put(i18n.get("sysinfo.user"), user.getDisplayName() + " (" + user.getUsername() + ")");
            info.put(i18n.get("sysinfo.roles"), String.join(", ", user.getRoles()));
            if (ctx.session().getLoginTime() != null) {
                info.put(i18n.get("sysinfo.loginTime"), DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(i18n.getLocale()).withZone(ZoneId.systemDefault())
                        .format(ctx.session().getLoginTime()));
            }
        }
        info.put(i18n.get("sysinfo.locale"), i18n.getLocale().getDisplayName(i18n.getLocale()));
        info.put(i18n.get("sysinfo.java"), System.getProperty("java.version") + " ("
                + System.getProperty("java.vendor") + ")");
        info.put(i18n.get("sysinfo.javaHome"), System.getProperty("java.home"));
        info.put(i18n.get("sysinfo.os"), System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        info.put(i18n.get("sysinfo.memoryLabel"), i18n.get("sysinfo.memory", usedMb, maxMb));
        info.put(i18n.get("sysinfo.processors"), String.valueOf(runtime.availableProcessors()));
        return info;
    }

    /** The information as "Label: value" lines. */
    public static String asText(Map<String, String> info) {
        StringBuilder text = new StringBuilder();
        info.forEach((label, value) -> text.append(label).append(": ").append(value).append(System.lineSeparator()));
        return text.toString();
    }

    public static void show(AppContext ctx) {
        Edt.checkEdt();
        I18n i18n = I18n.global();
        Map<String, String> info = collect(ctx);
        DefaultTableModel model = new DefaultTableModel(0, 2) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        info.forEach((label, value) -> model.addRow(new Object[]{label, value}));
        JTable table = new JTable(model);
        table.setTableHeader(null);
        table.setShowGrid(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(380);

        JDialog dialog = new JDialog(ctx.mainFrame(), i18n.get("sysinfo.title"), JDialog.ModalityType.APPLICATION_MODAL);
        JButton copy = new JButton(i18n.get("sysinfo.copy"));
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(asText(info)), null));
        JButton close = new JButton(i18n.get("sysinfo.close"));
        close.addActionListener(e -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(copy);
        buttons.add(close);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(540, table.getRowHeight() * (info.size() + 1)));
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.pack();
        dialog.setLocationRelativeTo(ctx.mainFrame());
        dialog.setVisible(true);
    }
}
