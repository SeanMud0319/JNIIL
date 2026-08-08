package top.nontage.jniil.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents a capture instruction annotation.
 * <p>
 * <b>Note:</b> This annotation is only available for {@link top.nontage.jniil.injector.functional.FunctionalInjector}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Capture {
    String[] value();
}