package top.nontage.jniil.utils;


import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.lang.instrument.UnmodifiableClassException;

public class LocalVariableChecker {
    public static void printLocalVariableSlot(Class<?> clazz, String methodName, boolean shouldFillLocals) {
        try {
            byte[] bytecode;
            if (shouldFillLocals) {
                bytecode = new LocalVariableTableFiller().fillLocalVariableNames(clazz, false);
            } else {
                bytecode = InjectionUtil.getClassBytes(clazz);
            }
            ClassReader cr = new ClassReader(bytecode);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            System.out.println("===== LocalVariable Slot For Method: " + methodName + " =====");
            for (MethodNode mn : cn.methods) {
                if (!mn.name.equals(methodName)) continue;
                System.out.println("Method: " + mn.name + mn.desc);
                if (mn.localVariables != null) {
                    System.out.println(" Locals:");
                    for (LocalVariableNode lvn : mn.localVariables) {
                        System.out.printf("  name=%s, desc=%s, index=%d%n", lvn.name, lvn.desc, lvn.index);
                    }
                }
            }
            System.out.println("===========================\n");
        } catch (IOException | UnmodifiableClassException e) {
            throw new RuntimeException(e);
        }
    }
}
