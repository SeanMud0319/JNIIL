package top.nontage.jniil.interfaces;

import javassist.CtMethod;
import org.objectweb.asm.tree.InsnList;
import top.nontage.jniil.injector.insn.InsnContext;

import java.util.Map;

public interface InsnInjectable extends Injectable {
    InsnList apply(InsnContext ctx, InsnList insns);

    @Override
    default String getInjectSourceCode(CtMethod ctMethod) {
        throw new UnsupportedOperationException("Insn injectable does not use source injection");
    }

    @Override
    default String targetTypeThreadName() {
        throw new UnsupportedOperationException("Insn injectable does not use source injection");
    }

    @Override
    default Class<?>[] appendClassLoader() {
        throw new UnsupportedOperationException("Insn injectable does not use source injection");
    }

    @Override
    default String[] appendFileLoader() {
        throw new UnsupportedOperationException("Insn injectable does not use source injection");
    }

    @Override
    default String[] appendJarLoader() {
        throw new UnsupportedOperationException("Insn injectable does not use source injection");
    }

    @Override
    default Map<String, byte[]> appendByteLoader() {
        throw new UnsupportedOperationException("Insn injectable does not use source injection");
    }

    @Override
    default boolean defaultLoader() {
        throw new UnsupportedOperationException("Insn injectable does not use source injection");
    }
}
