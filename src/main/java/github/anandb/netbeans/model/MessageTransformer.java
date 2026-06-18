package github.anandb.netbeans.model;

import github.anandb.netbeans.support.Logger;

public class MessageTransformer {
    private static final Logger LOG = Logger.from(MessageTransformer.class);

    public ProcessedMessage convert(Message message) {
        String type = message.type();
        StringBuilder sb = new StringBuilder();
        LOG.fine("addMessage(Message) called. role={0}, state={1}", new Object[]{type, message.state()});

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

        // Map the stored message type to the runtime MessageType. A non-user
        // message whose state is "thinking" carries the model's reasoning text
        // and must be routed as a thought (not a regular assistant response),
        // otherwise it would render as an assistant bubble when loading a
        // session. Tools are also stored under type="assistant"; they are
        // distinguished by the presence of a toolCalls field.
        MessageType msgType;
        if ("user".equals(type)) {
            msgType = MessageType.user_message_chunk;
        } else if ("thinking".equals(message.state())) {
            msgType = MessageType.agent_thought_chunk;
        } else {
            msgType = MessageType.agent_message_chunk;
        }
        String text = sb.toString();
        return new ProcessedMessage.Builder()
                .messageType(msgType)
                .text(text)
                .messageId(message.id())
                .rawText(text)
                .build();
    }
}
