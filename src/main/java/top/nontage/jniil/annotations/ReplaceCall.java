package top.nontage.jniil.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to replace specific method calls within the target instructions.
 * <p>
 * <b>Note:</b> This annotation is only available for {@link top.nontage.jniil.injector.StandardMethodInjector}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReplaceCall {
    String value();

    int limit() default -1;

    int[] counts() default {};
}
