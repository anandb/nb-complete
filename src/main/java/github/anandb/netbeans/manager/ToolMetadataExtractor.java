package github.anandb.netbeans.manager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.abbreviateMiddle;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;

public final class ToolMetadataExtractor {
    private static final Logger LOG = new Logger(ToolMetadataExtractor.class);
    private static final Pattern[] TOOL_CONTENT_PATTERNS = {
        Pattern.compile("<skill_content name=\"([^\"]{2,})\""),
        Pattern.compile("<path>([^<]{10,})</path>")
    };

    private ToolMetadataExtractor() {
    }


    public static String extractToolTitle(String messageId, String rawText, String kind) {
        String identifier = "";
        String tag = defaultString(messageId);

        int pos = tag.indexOf(':');
        if (pos > 0 && pos < tag.length()) {
            tag = tag.substring(0, pos);
        } else {
            tag = firstNonBlank(kind, "Tool");
        }

        for (var pattern : TOOL_CONTENT_PATTERNS) {
            Matcher m = pattern.matcher(rawText);
            if (m.find()) {
                identifier = m.group(1);
                break;
            }
        }

        LOG.fine("Identifier {0}/{1}", identifier, rawText);
        tag = tag.length() < 60 ? tag : "Tool";
        return tag + " " + abbreviateMiddle(identifier, "...", 60);
    }
}
