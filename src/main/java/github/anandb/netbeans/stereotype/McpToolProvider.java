package github.anandb.netbeans.stereotype;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a class that registers one or more MCP tools. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface McpToolProvider {
}
