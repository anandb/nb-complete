package github.anandb.netbeans.stereotype;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a type used to represent arguments accepted by an MCP tool. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface McpToolInput {
}
