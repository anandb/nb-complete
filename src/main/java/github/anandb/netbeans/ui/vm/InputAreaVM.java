package github.anandb.netbeans.ui.vm;

/**
 * Swing-free view-model for the bottom input area (draft text + send/stop
 * enablement + attachment indicator). Mirrors the mutable state on
 * {@code PlaceholderTextArea} / {@code StatusController} / {@code AttachmentUiHandler}.
 * <p>
 * <b>DSL-ready:</b> immutable record + withers, Swing-free.
 */
public record InputAreaVM(
        String draftText,
        boolean sendEnabled,
        boolean stopEnabled,
        int attachmentCount,
        boolean autocompletePopupVisible
) {
    public static InputAreaVM empty() {
        return new InputAreaVM("", false, false, 0, false);
    }

    public InputAreaVM withDraftText(String v) { return new InputAreaVM(v, sendEnabled, stopEnabled, attachmentCount, autocompletePopupVisible); }
    public InputAreaVM withSendEnabled(boolean v) { return new InputAreaVM(draftText, v, stopEnabled, attachmentCount, autocompletePopupVisible); }
    public InputAreaVM withStopEnabled(boolean v) { return new InputAreaVM(draftText, sendEnabled, v, attachmentCount, autocompletePopupVisible); }
    public InputAreaVM withAttachmentCount(int v) { return new InputAreaVM(draftText, sendEnabled, stopEnabled, v, autocompletePopupVisible); }
    public InputAreaVM withAutocompletePopupVisible(boolean v) { return new InputAreaVM(draftText, sendEnabled, stopEnabled, attachmentCount, v); }

    public boolean hasAttachments() { return attachmentCount > 0; }
}
