package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.support.AgentUtils;

final class ChatLayoutBuilder {

    private final AssistantTopComponent topComponent;
    private final ChatThreadPanel chatPanel;
    private final ConfigPanelController configPanelController;

    private JComboBox<?> sessionDropdown;
    private JButton newSessionBtn;
    private JButton renameSessionBtn;
    private JButton toggleBlocksBtn;
    private JButton keepBtn;
    private JButton filterBtn;
    private JButton helpBtn;
    private JButton toggleOptionsBtn;
    private JLabel statusLabel;
    private JLabel versionLabel;
    private JLabel cwdLabel;
    private PlaceholderTextArea inputArea;
    private JScrollPane inputScrollPane;
    private JPanel header;
    private JButton sendBtn;
    private JButton stopBtn;

    ChatLayoutBuilder(AssistantTopComponent topComponent, ChatThreadPanel chatPanel,
            ConfigPanelController configPanelController) {
        this.topComponent = topComponent;
        this.chatPanel = chatPanel;
        this.configPanelController = configPanelController;
    }

    JPanel buildHeader() {
        header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);

        sessionDropdown = new UIUtils.WrappingComboBox<>();

        JPanel sessionControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sessionControls.setOpaque(false);

        newSessionBtn = UIUtils.createToolbarButton("new.svg", NbBundle.getMessage(AssistantTopComponent.class, "HINT_NewSession"), e -> {
            org.netbeans.api.project.Project[] projects = github.anandb.netbeans.project.ACPProjectManager.getInstance().getAllOpenProjects();
            if (projects == null || projects.length == 0) {
                return;
            }
            if (projects.length == 1) {
                Lookup.getDefault()
                    .lookup(github.anandb.netbeans.contract.SessionControl.class)
                    .createNewSession(projects[0].getProjectDirectory().getPath());
            } else {
                topComponent.componentLifecycleHandler.showProjectPickerPopup((javax.swing.JComponent) e.getSource());
            }
        });
        String renameHint = NbBundle.getMessage(AssistantTopComponent.class, "HINT_RenameSession");
        renameSessionBtn = UIUtils.createToolbarButton("rename.svg", renameHint, e -> topComponent.renameCurrentSession());
        JButton refreshBtn = UIUtils.createToolbarButton("reload.svg",
                NbBundle.getMessage(AssistantTopComponent.class, "HINT_ReloadConversation"), e -> {
            topComponent.reloadCurrentSession();
        });

        String exportHint = NbBundle.getMessage(AssistantTopComponent.class, "HINT_ExportConversation");
        JButton exportBtn = UIUtils.createToolbarButton("export.svg", exportHint, e -> {
            topComponent.exportConversation();
        });
        String restartHint = NbBundle.getMessage(AssistantTopComponent.class, "HINT_RestartServer");
        JButton restartServerBtn = UIUtils.createToolbarButton("restart.svg", restartHint, e -> {
            topComponent.componentLifecycleHandler.promptRestartServer();
        });

        JButton tb = UIUtils.createToolbarButton("expand.svg", NbBundle.getMessage(AssistantTopComponent.class, "HINT_ExpandAll"), null);
        tb.addActionListener(e -> {
            boolean expanded = !chatPanel.isAllBlocksExpanded();
            chatPanel.toggleAllBlocks(expanded);
            String newState = expanded ? "collapse" : "expand";
            tb.putClientProperty("state", newState);
            tb.setToolTipText(expanded
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_CollapseAll")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ExpandAll"));
            tb.setIcon(ThemeManager.getIcon(expanded ? "collapse.svg" : "expand.svg", 28));
        });
        toggleBlocksBtn = tb;
        toggleBlocksBtn.putClientProperty("state", "expand");

        final boolean savedKeepState = NbPreferences.forModule(AssistantTopComponent.class).getBoolean("keepOlderMessages", false);
        chatPanel.setKeepOlderMessages(savedKeepState);
        JButton pinBtn = UIUtils.createToolbarButton(savedKeepState ? "pin.svg" : "pin_off.svg",
                NbBundle.getMessage(AssistantTopComponent.class, savedKeepState ? "HINT_TruncateMessages" : "HINT_KeepMessages"), null);
        pinBtn.addActionListener(e -> {
            boolean keep = !chatPanel.isKeepOlderMessages();
            chatPanel.setKeepOlderMessages(keep);
            NbPreferences.forModule(AssistantTopComponent.class).putBoolean("keepOlderMessages", keep);
            pinBtn.setIcon(ThemeManager.getIcon(keep ? "pin.svg" : "pin_off.svg", 28));
            pinBtn.setToolTipText(keep
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_TruncateMessages")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_KeepMessages"));
        });
        keepBtn = pinBtn;
        keepBtn.putClientProperty("state", savedKeepState ? "pinned" : "unpinned");

        filterBtn = createFilterButton();

        sessionControls.add(newSessionBtn);
        sessionControls.add(renameSessionBtn);
        sessionControls.add(refreshBtn);
        sessionControls.add(keepBtn);
        sessionControls.add(toggleBlocksBtn);
        sessionControls.add(filterBtn);
        sessionControls.add(exportBtn);
        sessionControls.add(restartServerBtn);

        topBar.add(sessionDropdown, BorderLayout.CENTER);
        topBar.add(sessionControls, BorderLayout.EAST);

        cwdLabel = new JLabel("");
        cwdLabel.setFont(cwdLabel.getFont().deriveFont(Font.BOLD));

        cwdLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                topComponent.showCwdContextMenu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                topComponent.showCwdContextMenu(e);
            }
        });

        JPanel headerContent = new JPanel(new BorderLayout(0, 4));
        headerContent.setOpaque(false);

        JPanel cwdRow = new JPanel(new BorderLayout(4, 0));
        cwdRow.setOpaque(false);
        cwdRow.add(cwdLabel, BorderLayout.CENTER);

        String quickstartUrl = "https://github.com/anandb/nb-complete/blob/main/QUICKSTART.md";
        String feedbackUrl = "https://forms.gle/ZQn5Wy2aDSSpkzkaA";

        helpBtn = UIUtils.createToolbarButton("help.svg",
            NbBundle.getMessage(AssistantTopComponent.class, "HINT_QuickstartGuide"), null);
        helpBtn.setContentAreaFilled(false);
        helpBtn.setBorderPainted(false);
        helpBtn.addActionListener(e -> github.anandb.netbeans.support.BrowserUtils.openOrCopyUrl(quickstartUrl, "STATUS_QuickstartCopied",
            (url, key) -> topComponent.statusController.setStatus(key, url)));

        JButton feedbackBtn = UIUtils.createToolbarButton("feedback.svg",
            NbBundle.getMessage(AssistantTopComponent.class, "HINT_Feedback"), null);
        feedbackBtn.setContentAreaFilled(false);
        feedbackBtn.setBorderPainted(false);
        feedbackBtn.addActionListener(e -> github.anandb.netbeans.support.BrowserUtils.openOrCopyUrl(feedbackUrl, "STATUS_FeedbackCopied",
            (url, key) -> topComponent.statusController.setStatus(key, url)));

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightButtons.setOpaque(false);
        rightButtons.add(feedbackBtn);
        rightButtons.add(helpBtn);
        cwdRow.add(rightButtons, BorderLayout.EAST);

        HelpButtonFlash.flash(helpBtn);

        headerContent.add(cwdRow, BorderLayout.NORTH);
        headerContent.add(topBar, BorderLayout.SOUTH);

        header.add(headerContent, BorderLayout.CENTER);

        return header;
    }

    JPanel buildBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(new EmptyBorder(4, 12, 4, 12));
        statusPanel.setOpaque(false);

        statusLabel = new JLabel(NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Ready"));
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        toggleOptionsBtn = UIUtils.createToolbarButton("settings.svg", 25, NbBundle.getMessage(AssistantTopComponent.class, "HINT_Options"), null);

        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rightStatusPanel.setOpaque(false);

        rightStatusPanel.add(toggleOptionsBtn);

        statusPanel.add(rightStatusPanel, BorderLayout.EAST);

        bottomPanel.add(statusPanel, BorderLayout.NORTH);

        inputArea = new PlaceholderTextArea(NbBundle.getMessage(AssistantTopComponent.class, "LBL_TypeMessage"));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        int editorFontSize = ThemeManager.getMonospaceFont().getSize();
        inputArea.setFont(ThemeManager.getFont().deriveFont(editorFontSize));

        inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setPreferredSize(new Dimension(100, 100));

        JPanel inputMainPanel = new JPanel(new BorderLayout(0, 4));
        inputMainPanel.setOpaque(false);
        inputMainPanel.add(configPanelController.getComponent(), BorderLayout.NORTH);
        inputMainPanel.add(inputScrollPane, BorderLayout.CENTER);

        JPanel btnCard = UIUtils.createTransparentPanel(new CardLayout());
        sendBtn = UIUtils.createTextButton(NbBundle.getMessage(AssistantTopComponent.class, "BTN_Go"), null);
        stopBtn = UIUtils.createTextButton(NbBundle.getMessage(AssistantTopComponent.class, "BTN_Stop"), null);

        btnCard.add(sendBtn, "SEND");
        btnCard.add(stopBtn, "STOP");

        versionLabel = new JLabel("v" + AgentUtils.getVersion());
        Font labelFont = UIManager.getFont("Label.font");
        versionLabel.setFont(versionLabel.getFont().deriveFont(labelFont != null ? labelFont.getSize() - 1f : 9f));
        versionLabel.setForeground(Color.GRAY);
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightPanel.setOpaque(false);
        rightPanel.add(btnCard, BorderLayout.CENTER);
        rightPanel.add(versionLabel, BorderLayout.SOUTH);

        inputMainPanel.add(rightPanel, BorderLayout.EAST);

        bottomPanel.add(inputMainPanel, BorderLayout.CENTER);

        return bottomPanel;
    }

    private JButton createFilterButton() {
        final JButton[] btnRef = new JButton[1];
        JButton btn = UIUtils.createToolbarButton("filter.svg", 25, NbBundle.getMessage(AssistantTopComponent.class, "HINT_FilterMessages"), e -> {
            javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
            for (String type : MessageFilterManager.getEffectiveMessageTypes()) {
                javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(type, !MessageFilterManager.isTypeHidden(type));
                item.addActionListener(ev -> {
                    MessageFilterManager.setTypeHidden(type, !item.isSelected());
                    chatPanel.applyTypeFilters();
                });
                popup.add(item);
            }
            popup.show(btnRef[0], 0, btnRef[0].getHeight());
        });
        btnRef[0] = btn;
        return btn;
    }

    @SuppressWarnings("unchecked")
    JComboBox<github.anandb.netbeans.model.SessionItem> getSessionDropdown() {
        return (JComboBox<github.anandb.netbeans.model.SessionItem>) sessionDropdown;
    }

    JButton getNewSessionBtn() { return newSessionBtn; }

    JButton getRenameSessionBtn() { return renameSessionBtn; }

    JButton getToggleBlocksBtn() { return toggleBlocksBtn; }

    JButton getKeepBtn() { return keepBtn; }

    JButton getFilterBtn() { return filterBtn; }

    JButton getHelpBtn() { return helpBtn; }

    JButton getToggleOptionsBtn() { return toggleOptionsBtn; }

    JLabel getStatusLabel() { return statusLabel; }

    JLabel getVersionLabel() { return versionLabel; }

    JLabel getCwdLabel() { return cwdLabel; }

    PlaceholderTextArea getInputArea() { return inputArea; }

    JScrollPane getInputScrollPane() { return inputScrollPane; }

    JPanel getHeader() { return header; }

    JButton getSendBtn() { return sendBtn; }

    JButton getStopBtn() { return stopBtn; }
}
