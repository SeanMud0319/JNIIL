package top.nontage.jniil.annotations;

import top.nontage.auth.library.annotation.Protect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Protect
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InjectMethodInfo {
    String targetTypeInternalName();
    String targetMethodName();
    String[] targetMethodParams() default {};
    Class<?>[] appendClassLoader() default {};
    String targetTypeThreadName() default "";
    String[] appendFileLoader() default "";
    String[] appendJarLoader() default "";
    boolean defaultLoader() default true;
}

