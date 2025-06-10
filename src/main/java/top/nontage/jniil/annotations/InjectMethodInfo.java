package top.nontage.jniil.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InjectMethodInfo {
    String targetTypeInternalName();
    String targetMethodName();
    Class<?>[] appendClassLoader() default {};
}

