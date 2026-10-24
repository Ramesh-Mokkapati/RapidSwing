package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;
import com.rapidswing.util.Edt;

import javax.swing.JDialog;
import javax.swing.WindowConstants;
import java.awt.Window;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context help with F1: each view can have an HTML page, shown in a help window.
 * <pre>
 * HelpService help = new HelpService(views).register("customers", "/help/customers.html");
 * help.install(mainFrame);   // F1 shows help for the current view
 * </pre>
 * Pages are looked up per language first: for German, {@code /help/customers_de.html} is used if it exists.
 * Views without a page get the default page, if one is set, or a short "no help" note. Links of the form
 * {@code view:id} in help pages open views.
 */
public class HelpService {

    private final ViewManager views;
    private final Map<String, String> pages = new ConcurrentHashMap<>();
    private volatile String defaultPage;
    private JDialog window;
    private HtmlView content;

    public HelpService(ViewManager views) {
        this.views = views;
    }

    public HelpService register(String viewId, String htmlResource) {
        pages.put(Objects.requireNonNull(viewId, "viewId"), Objects.requireNonNull(htmlResource, "htmlResource"));
        return this;
    }

    /** The page for views without one of their own, e.g. a table of contents. */
    public HelpService setDefault(String htmlResource) {
        this.defaultPage = htmlResource;
        return this;
    }

    public boolean hasHelp(String viewId) {
        return viewId != null && pages.containsKey(viewId);
    }

    /** Binds F1 on the main window to help for the current view. */
    public HelpService install(MainFrame frame) {
        frame.bindKey("F1", "shortcuts.help", () -> show(frame, views == null ? null : views.currentId().get()));
        return this;
    }

    /** Shows help for the view, reusing the help window if it is open. */
    public void show(Window owner, String viewId) {
        Edt.checkEdt();
        String html = html(viewId);
        if (window == null || !window.isDisplayable()) {
            content = new HtmlView(I18n.global().get("help.title"), html, views);
            window = new JDialog(owner, I18n.global().get("help.title"), JDialog.ModalityType.MODELESS);
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.setContentPane(content);
            window.setSize(560, 480);
            if (owner != null) {
                window.setLocation(owner.getX() + owner.getWidth() - 600, owner.getY() + 80);
            }
        } else {
            content.setHtml(html);
        }
        window.setVisible(true);
        window.toFront();
    }

    /** The help text for the view: its page, the default page, or a "no help" note. */
    public String html(String viewId) {
        String page = viewId == null ? null : pages.get(viewId);
        if (page == null) {
            page = defaultPage;
        }
        if (page == null) {
            return "<html><body><p>" + I18n.global().get("help.none") + "</p></body></html>";
        }
        return HtmlView.load(localized(page, I18n.global().getLocale()));
    }

    /** {@code /help/page_de.html} for German if that resource exists, else the page itself. */
    static String localized(String page, Locale locale) {
        int dot = page.lastIndexOf('.');
        if (dot <= page.lastIndexOf('/') || locale.getLanguage().isEmpty()) {
            return page;
        }
        String resource = page.startsWith("/") ? page : "/" + page;
        int resourceDot = resource.lastIndexOf('.');
        String candidate = resource.substring(0, resourceDot) + "_" + locale.getLanguage() + resource.substring(resourceDot);
        return HelpService.class.getResource(candidate) != null ? candidate : page;
    }
}
