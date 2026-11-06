package github.anandb.netbeans.ui.assistant;

import java.util.function.BiConsumer;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;

import github.anandb.netbeans.support.AppLinks;
import github.anandb.netbeans.support.BrowserUtils;
import github.anandb.netbeans.ui.UIBundle;

/**
 * The project's funding link. Every entry point asks before handing the user to an external page.
 */
final class SupportLink {

    private SupportLink() {
    }

    /**
     * Confirms, then opens the support page; a declined dialog does nothing.
     *
     * @param onCopied receives {@code (url, statusKey)} when the browser could not be opened and
     *                 the URL was copied to the clipboard instead
     */
    static void confirmAndOpen(BiConsumer<String, String> onCopied) {
        String url = AppLinks.supportUrl();
        NotifyDescriptor confirm = new NotifyDescriptor.Confirmation(
                NbBundle.getMessage(UIBundle.class, "MSG_ConfirmOpenSupportPage"),
                NbBundle.getMessage(UIBundle.class, "TITLE_SupportProject"),
                NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.INFORMATION_MESSAGE);
        if (DialogDisplayer.getDefault().notify(confirm) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        BrowserUtils.openOrCopyUrl(url, "STATUS_SupportUrlCopied", onCopied);
    }
}
