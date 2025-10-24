package top.nontage.jniil.annotations;

import top.nontage.auth.library.annotation.Protect;

import java.lang.annotation.*;
@Protect
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Experimental {
    String value() default "This feature is experimental and may change or be removed in future versions.";
}

