package github.anandb.netbeans.model;

import github.anandb.netbeans.support.Logger;

public class MessageTransformer {
    private static final Logger LOG = Logger.from(MessageTransformer.class);

    public ProcessedMessage convert(Message message) {
        String type = message.type();
        StringBuilder sb = new StringBuilder();
        LOG.fine("addMessage(Message) called. role={0}", type);

        if ("user".equals(type)) {
            if (message.prompt() != null) {
                if (message.prompt().text() != null) {
                    sb.append(message.prompt().text());
                }
                if (message.prompt().parts() != null) {
                    for (Message.ContentPart part : message.prompt().parts()) {
                        String pt = part.getDisplayText();
                        if (pt != null && !pt.isEmpty()) {
                            if (sb.length() > 0) {
                                sb.append("\n");
                            }
                            sb.append(pt);
                        }
                    }
                }
            }
        } else {
            if (message.completion() != null) {
                if (message.completion().text() != null) {
                    sb.append(message.completion().text());
                }
                if (message.completion().parts() != null) {
                    for (Message.ContentPart part : message.completion().parts()) {
                        String pt = part.getDisplayText();
                        if (pt != null && !pt.isEmpty()) {
                            if (sb.length() > 0) {
                                sb.append("\n\n");
                            }
                            sb.append(pt);
                        }
                    }
                }
            }
        }

        MessageType msgType = "user".equals(type) ? MessageType.user_message_chunk : MessageType.agent_message_chunk;
        String text = sb.toString();
        return new ProcessedMessage.Builder()
                .messageType(msgType)
                .text(text)
                .messageId(message.id())
                .rawText(text)
                .build();
    }
}
