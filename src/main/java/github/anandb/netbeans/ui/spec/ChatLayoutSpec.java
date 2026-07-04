package github.anandb.netbeans.ui.spec;

import github.anandb.netbeans.ui.vm.ChatToolbarVM;

/**
 * Pure builder entry point for the chat toolbar + bottom input panel.
 * <p>
 * <b>Current state (Phase 2 seam):</b> the imperative construction still lives
 * in {@code ChatLayoutBuilder} (package-private in {@code github.anandb.netbeans.ui}).
 * The spec method signature ({@code build(vm, actions)} returning
 * {@link ChatLayoutRefs}) is the DSL-ready contract — a future swingtree
 * migration replaces only the body of this method with a declarative
 * {@code UI.of(this).add(...)} tree; the signature, the refs record, and the
 * actions interface stay unchanged.
 * <p>
 * <b>Deferred to a follow-up PR</b>: collapsing {@code ChatLayoutBuilder}'s
 * duplicate mutable {@code JButton} fields into this builder (the field
 * collapse is a large mechanical change with behavioral risk and is tracked
 * separately). For now this class establishes the seam without moving
 * construction.
 */
public final class ChatLayoutSpec {

    private ChatLayoutSpec() {}

    /**
     * Build the chat toolbar + bottom input panel and return its component refs.
     *
     * @param vm     the toolbar view-model (Swing-free state seed)
     * @param actions the callback contract wired to Swing listeners
     * @return immutable refs to every component the caller needs to attach
     *         controllers / listeners to
     */
    public static ChatLayoutRefs build(ChatToolbarVM vm, ChatToolbarActions actions) {
        // Phase 2 seam: the imperative construction stays in ChatLayoutBuilder
        // for now. A follow-up PR moves the body here (declarative) and makes
        // ChatLayoutBuilder stateless.
        throw new UnsupportedOperationException(
                "ChatLayoutSpec.build — seam only; delegate wiring is added in the follow-up PR. "
                + "Use ChatLayoutBuilder directly until the field collapse lands.");
    }
}
