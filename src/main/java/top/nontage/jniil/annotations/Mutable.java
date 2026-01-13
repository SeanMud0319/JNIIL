package top.nontage.jniil.annotations;

import top.nontage.auth.library.annotation.Protect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Shadow} field indicating that the target field should be modified
 * even if it is declared as {@code final}.
 * <p>
 * <b>Warning:</b> This operation bypasses the JVM's safety checks by directly modifying
 * {@code final} fields via {@code sun.misc.Unsafe}.
 * This is a dangerous operation and may lead to undefined behavior, memory visibility
 * issues, or JVM crashes.
 * This functionality is not guaranteed to work across all JVM implementations or versions.
 * Use with extreme caution.
 */
@Protect
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Mutable {
}