package top.nontage.jniil.annotations;

import org.objectweb.asm.Opcodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InvokeRedirect {
    Opcode value();

    String target();

    int ordinal() default 1;

    enum Opcode {
        INVOKEVIRTUAL(Opcodes.INVOKEVIRTUAL),
        INVOKESTATIC(Opcodes.INVOKESTATIC),
        INVOKESPECIAL(Opcodes.INVOKESPECIAL),
        INVOKEINTERFACE(Opcodes.INVOKEINTERFACE);

        private final int value;

        Opcode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
