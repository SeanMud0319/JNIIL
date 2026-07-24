package top.nontage.jniil.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Invoker {
    String value() default "";
    boolean isStatic() default false;
    // This will replace the Object parameters
    // use hint {"java.lang.String", "java.lang.String" } then it will from greet(String s, Object obj1, Object obj2) ->
    // greet(String s, String obj1, String obj2)
    String[] hints() default {};
}