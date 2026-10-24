package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Shows an HTML page: landing pages for navigation folders, help or "about" pages.
 * Links of the form {@code view:customers} open views; {@code http(s)} links open in the system browser.
 * <p>
 * The page is a classpath resource (e.g. {@code /pages/sales.html}) or, if the text starts with {@code <},
 * the HTML itself. Swing renders HTML 3.2 with simple CSS: keep pages plain.
 */
public class HtmlView extends AbstractView {

    private final JEditorPane pane = new JEditorPane();
    private final ViewManager views;

    public HtmlView(String title, String htmlResourceOrText, ViewManager views) {
        super(title);
        this.views = views;
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        pane.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                follow(e.getDescription());
            }
        });
        setHtml(load(Objects.requireNonNull(htmlResourceOrText, "htmlResourceOrText")));
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    public void setHtml(String html) {
        pane.setText(html);
        pane.setCaretPosition(0);
    }

    public String getHtml() {
        return pane.getText();
    }

    /** Opens a {@code view:id} link in the application, or a web link in the browser. */
    void follow(String link) {
        if (link == null) {
            return;
        }
        if (link.startsWith("view:") && views != null) {
            views.show(link.substring(5));
        } else if ((link.startsWith("http://") || link.startsWith("https://"))
                && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(link));
            } catch (IOException | IllegalArgumentException ignored) {
                // Nothing sensible to do: the link stays where it is.
            }
        }
    }

    static String load(String resourceOrText) {
        if (resourceOrText.trim().startsWith("<")) {
            return resourceOrText;
        }
        String resource = resourceOrText.startsWith("/") ? resourceOrText : "/" + resourceOrText;
        try (InputStream in = HtmlView.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "<html><body><p>" + I18n.global().get("html.missing", resourceOrText) + "</p></body></html>";
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<html><body><p>" + I18n.global().get("html.missing", resourceOrText) + "</p></body></html>";
        }
    }
}
