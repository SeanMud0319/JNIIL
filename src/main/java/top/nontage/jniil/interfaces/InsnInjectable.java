package top.nontage.jniil.interfaces;

import javassist.CtMethod;
import org.objectweb.asm.tree.InsnList;
import top.nontage.jniil.injector.insn.InsnContext;

import java.util.Map;

public interface InsnInjectable extends Injectable {

    InsnList apply(InsnContext ctx, InsnList insns);

    @Override
    default String getInjectSourceCode(CtMethod ctMethod) {
        throw new UnsupportedOperationException("InsnInjectable uses ASM Instruction API and does not support Javassist source code injection.");
    }

    @Override
    default String targetTypeThreadName() {
        throw new UnsupportedOperationException("InsnInjectable handles execution context via InsnContext; thread-name binding is not supported.");
    }

    @Override
    default Class<?>[] appendClassLoader() {
        throw new UnsupportedOperationException("ClassPath manipulation via appendClassLoader is only available for SourceInjectable.");
    }

    @Override
    default String[] appendFileLoader() {
        throw new UnsupportedOperationException("File-based ClassPath appending is not supported for bytecode-level InsnInjectable.");
    }

    @Override
    default String[] appendJarLoader() {
        throw new UnsupportedOperationException("Jar-based ClassPath appending is not supported for bytecode-level InsnInjectable.");
    }

    @Override
    default Map<String, byte[]> appendByteLoader() {
        throw new UnsupportedOperationException("Byte-array ClassPath appending is not supported for bytecode-level InsnInjectable.");
    }

    @Override
    default boolean defaultLoader() {
        throw new UnsupportedOperationException("DefaultLoader toggle is irrelevant for Instruction-level injection.");
    }
}