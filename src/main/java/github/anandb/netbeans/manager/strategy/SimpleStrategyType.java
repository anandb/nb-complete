package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Enum-dispatched strategies for simple session update types.
 * Replaces 6 individual strategy classes with a single enum.
 */
public enum SimpleStrategyType implements DataExtractionStrategy {

    CONFIG_OPTIONS_UPDATE(MessageType.config_options_update) {
        @Override
        public void extract(SessionUpdate update, UIHandler handler) {
            handler.updateConfig(update.update().configOptions());
        }
    },
    USAGE_UPDATE(MessageType.usage_update) {
        @Override
        public void extract(SessionUpdate update, UIHandler handler) {
            if (update.update() != null) {
                handler.updateUsage(update.update().used(), update.update().size());
            }
        }
    },
    SESSION_INFO_UPDATE(MessageType.session_info_update) {
        @Override
        public void extract(SessionUpdate update, UIHandler handler) {
            handler.refreshSessions();
        }
    },
    AGENT_THOUGHT_CHUNK(MessageType.agent_thought_chunk) {
        @Override
        public void extract(SessionUpdate update, UIHandler handler) {
            String text = AgentMessageChunkStrategy.extractText(update.content());
            ProcessedMessage target = new ProcessedMessage.Builder()
                    .messageType(MessageType.valueOf(update.type()))
                    .text(text)
                    .messageId(update.messageId())
                    .kind(update.kind())
                    .rawText(text)
                    .streaming(true)
                    .build();
            handler.displayMessage(target);
        }
    },
    USER_MESSAGE_CHUNK(MessageType.user_message_chunk) {
        @Override
        public void extract(SessionUpdate update, UIHandler handler) {
            String text = AgentMessageChunkStrategy.extractText(update.content());
            if (isBlank(text)) return;
            ProcessedMessage target = new ProcessedMessage.Builder()
                    .messageType(MessageType.valueOf(update.type()))
                    .text(text)
                    .messageId(update.messageId())
                    .kind(update.kind())
                    .rawText(text)
                    .streaming(true)
                    .build();
            handler.displayMessage(target);
        }
    },
    /** Catch-all fallback — logs unknown types. */
    DEFAULT(null) {
        @Override
        public void extract(SessionUpdate update, UIHandler handler) {
            LOG.warn("Received unknown ACP session update type: {0}", update.type());
            LOG.fine("Unknown update payload: {0}", update);
        }
    };

    private static final Logger LOG = Logger.from(SimpleStrategyType.class);
    private final MessageType messageType;

    SimpleStrategyType(MessageType messageType) {
        this.messageType = messageType;
    }

    @Override
    public boolean canHandle(SessionUpdate update, String reclassifiedType) {
        return messageType == null || messageType.name().equals(reclassifiedType);
    }

    public MessageType messageType() {
        return messageType;
    }
}
