package top.nontage.jniil.annotations;

import top.nontage.auth.library.annotation.Protect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a shadow field as view-only.
 * Any attempt to write to this field will result in an error during transformation.
 */
@Protect
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ViewOnly {
}