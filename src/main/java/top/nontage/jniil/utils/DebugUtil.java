package top.nontage.jniil.utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

public class DebugUtil {

    public static int getJavaVersion() {
        String spec = System.getProperty("java.specification.version");
        if (spec == null || spec.isEmpty()) {
            spec = System.getProperty("java.version");
        }
        if (spec.startsWith("1.")) {
            spec = spec.substring(2);
        }
        int dot = spec.indexOf('.');
        if (dot != -1) {
            spec = spec.substring(0, dot);
        }
        int dash = spec.indexOf('-');
        if (dash != -1) {
            spec = spec.substring(0, dash);
        }
        return Integer.parseInt(spec);
    }

    public static void printASMInfo(byte[] bytecode, String label) {
        ClassReader cr = new ClassReader(bytecode);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        System.out.println("=== ASM DUMP: " + label + " ===");
        for (MethodNode mn : cn.methods) {
            System.out.println("Method: " + mn.name + mn.desc);
            if (mn.localVariables != null) {
                System.out.println(" Locals:");
                for (LocalVariableNode lvn : mn.localVariables) {
                    System.out.printf("  name=%s, desc=%s, index=%d%n", lvn.name, lvn.desc, lvn.index);
                }
            }

            System.out.println(" Instructions:");
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                System.out.println("  " + insn.getClass().getSimpleName() + " opcode=" + insn.getOpcode());
            }
        }
        System.out.println("===========================\n");
    }
}
