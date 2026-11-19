package github.anandb.netbeans.stereotype;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a class that exposes or mediates a protocol boundary with an external process or client. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Boundary {
}
