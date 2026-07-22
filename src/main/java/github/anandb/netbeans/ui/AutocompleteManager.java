package github.anandb.netbeans.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.model.ModelRecords.CommandInfo;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProcessService;

// DSL-CONTROLLER: not a view — autocomplete popup state (filter, navigate,
// enter-to-send). The popup JPopupMenu is a leaf the DSL wraps; the keyboard
// dispatcher + filter timer stays imperative.
public class AutocompleteManager {

    // --- inner class: merged from AutocompleteRenderer -----------------------
    private static final class AutocompleteRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SessionUpdate.AvailableCommand cmd) {
                setText(" /" + cmd.name() + (cmd.description() != null ? "  - " + cmd.description() : ""));
                setFont(ThemeManager.getFont().deriveFont(13f));
                setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            }
            return this;
        }
    }
    // -------------------------------------------------------------------------

    private static final Logger LOG = Logger.from(AutocompleteManager.class);
    private String lastPrefix;

    private final ProcessService processService = PlatformBridge.processServiceSafe();

    private final PlaceholderTextArea inputArea;
    private final Runnable sendMessageAction;
    private final JPopupMenu autocompletePopup;
    private final JList<SessionUpdate.AvailableCommand> commandList;
    private final JViewport viewport;
    private final Set<String> excludedCommands;

    public AutocompleteManager(PlaceholderTextArea inputArea, Runnable sendMessageAction) {
        this(inputArea, sendMessageAction, Set.of());
    }

    public AutocompleteManager(PlaceholderTextArea inputArea, Runnable sendMessageAction, Set<String> excludedCommands) {
        this.inputArea = inputArea;
        this.sendMessageAction = sendMessageAction;
        this.excludedCommands = excludedCommands != null ? excludedCommands : Set.of();

        autocompletePopup = new JPopupMenu();
        autocompletePopup.setBorder(BorderFactory.createLineBorder(UIManager.getColor("controlShadow")));

        commandList = new JList<>();
        commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commandList.setFocusable(false);
        commandList.setCellRenderer(new AutocompleteRenderer());

        JScrollPane scrollPane = new JScrollPane(commandList);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(750, 200));
        viewport = scrollPane.getViewport();
        autocompletePopup.add(scrollPane);

        commandList.setFixedCellHeight(22);

        commandList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectCommand();
                }
            }
        });
    }

    public boolean isPopupVisible() {
        return autocompletePopup.isVisible();
    }

    public boolean handleKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && autocompletePopup.isVisible()) {
            handleEnterWithAutocomplete(e);
            return true;
        }
        return false;
    }

    public void handleKeyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_ESCAPE) {
            autocompletePopup.setVisible(false);
            lastPrefix = null;
            return;
        }
        if (keyCode == KeyEvent.VK_ENTER) {
            if (autocompletePopup.isVisible()) {
                selectCommand();
            }
            autocompletePopup.setVisible(false);
            lastPrefix = null;
            return;
        }
        if (keyCode == KeyEvent.VK_SPACE) {
            autocompletePopup.setVisible(false);
            lastPrefix = null;
            return;
        }
        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN) {
            if (autocompletePopup.isVisible()) {
                e.consume();
                int size = commandList.getModel().getSize();
                if (size > 0) {
                    int index = commandList.getSelectedIndex();
                    switch (keyCode) {
                        case KeyEvent.VK_UP -> index = (index - 1 + size) % size;
                        case KeyEvent.VK_DOWN -> index = (index + 1) % size;
                    }
                    commandList.setSelectedIndex(index);
                    commandList.ensureIndexIsVisible(index);
                }
            }
            return;
        }
        showAutocomplete();
    }

    private void handleEnterWithAutocomplete(KeyEvent e) {
        String curText = inputArea.getText();
        int cr = inputArea.getCaretPosition();
        int s = cr - 1;
        while (s >= 0 && !Character.isWhitespace(curText.charAt(s))) {
            if (curText.charAt(s) == '/' || curText.charAt(s) == '@') {
                break;
            }
            s--;
        }
        boolean exactMatch = false;
        if (s >= 0 && (curText.charAt(s) == '/' || curText.charAt(s) == '@')) {
            String typedPrefix = curText.substring(s + 1, cr);
            SessionUpdate.AvailableCommand sel = commandList.getSelectedValue();
            if (sel != null && sel.name().equals(typedPrefix)) {
                exactMatch = true;
            }
        }
        if (exactMatch) {
            autocompletePopup.setVisible(false);
            sendMessageAction.run();
        } else {
            e.consume();
            selectCommand();
            sendMessageAction.run();
        }
    }

    private void showAutocomplete() {
        String text = inputArea.getText();
        int caret = inputArea.getCaretPosition();
        if (caret <= 0) {
            autocompletePopup.setVisible(false);
            lastPrefix = null;
            return;
        }

        int start = caret - 1;
        while (start >= 0 && !Character.isWhitespace(text.charAt(start))) {
            if (text.charAt(start) == '/' || text.charAt(start) == '@') {
                break;
            }
            start--;
        }

        if (start < 0 || (text.charAt(start) != '/' && text.charAt(start) != '@')) {
            autocompletePopup.setVisible(false);
            lastPrefix = null;
            return;
        }

        char trigger = text.charAt(start);
        String prefix = text.substring(start + 1, caret).toLowerCase();

        if (prefix.equals(lastPrefix) && autocompletePopup.isVisible()) {
            return;
        }
        lastPrefix = prefix;

        List<SessionUpdate.AvailableCommand> allCommands = new ArrayList<>();

        if (trigger == '/') {
            SlashCommandInterceptor interceptor = processService.get().getSlashCommandInterceptor();
            if (interceptor != null) {
                for (Map.Entry<String, CommandInfo> entry : interceptor.getCommands().entrySet()) {
                    String cmdName = entry.getKey();
                    CommandInfo info = entry.getValue();
                    String name = cmdName.startsWith("/") ? cmdName.substring(1) : cmdName;
                    allCommands.add(new SessionUpdate.AvailableCommand(name, info.description(), null));
                }
            }

            allCommands.addAll(processService.get().getAvailableCommands());
        }

        List<SessionUpdate.AvailableCommand> filtered = allCommands.stream()
                .filter(c -> !excludedCommands.contains(c.name().toLowerCase()))
                .filter(c -> c.name().toLowerCase().startsWith(prefix))
                .toList();

        if (filtered.isEmpty()) {
            autocompletePopup.setVisible(false);
            lastPrefix = null;
            return;
        }

        DefaultListModel<SessionUpdate.AvailableCommand> model = new DefaultListModel<>();
        for (SessionUpdate.AvailableCommand cmd : filtered) {
            model.addElement(cmd);
        }
        commandList.setModel(model);
        commandList.setSelectedIndex(0);

        try {
            Rectangle2D rect2d = inputArea.modelToView2D(start);
            Rectangle rect = rect2d.getBounds();
            int height = autocompletePopup.getPreferredSize().height;
            autocompletePopup.show(inputArea, rect.x, rect.y - height - 2);
        } catch (Exception ex) {
            autocompletePopup.show(inputArea, 0, 0);
        }

        inputArea.requestFocusInWindow();
    }

    void selectCommand() {
        SessionUpdate.AvailableCommand selected = commandList.getSelectedValue();
        if (selected != null) {
            String text = inputArea.getText();
            int caret = inputArea.getCaretPosition();
            int start = caret - 1;
            while (start >= 0 && !Character.isWhitespace(text.charAt(start))) {
                if (text.charAt(start) == '/' || text.charAt(start) == '@') {
                    break;
                }
                start--;
            }

            if (start >= 0) {
                String before = text.substring(0, start);
                String after = text.substring(caret);
                inputArea.setText(before + "/" + selected.name() + " " + after);
                inputArea.setCaretPosition(before.length() + selected.name().length() + 2);
            }

            autocompletePopup.setVisible(false);
            lastPrefix = null;
            inputArea.requestFocusInWindow();
        }
    }
}
