package top.nontage.jniil.utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

public class DebugUtil {

    public static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        }
        int dotIndex = version.indexOf('.');
        if (dotIndex != -1) {
            return Integer.parseInt(version.substring(0, dotIndex));
        }
        return Integer.parseInt(version);
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
