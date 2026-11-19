package github.anandb.netbeans.stereotype;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a contract type that defines an interaction across architectural layers. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface HexagonalPort {
}
