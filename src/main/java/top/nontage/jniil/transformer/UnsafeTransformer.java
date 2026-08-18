package top.nontage.jniil.transformer;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * Patches {@code sun.misc.Unsafe#beforeMemoryAccessSlow()} to bypass JDK 23+
 * memory-access restrictions.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>forceBypass = true</b>: inserts {@code RETURN} at method entry,
 *       unconditionally bypassing ALL checks (ALLOW/WARN/DEBUG/DENY).</li>
 *   <li><b>forceBypass = false</b>: inserts
 *       {@code if (MEMORY_ACCESS_OPTION != DENY) return;} at method entry,
 *       only bypassing WARN/DEBUG modes. DENY still proceeds to original logic.</li>
 * </ul>
 *
 * @since 2026/08/18
 */
public class UnsafeTransformer implements ClassFileTransformer {

    private final boolean forceBypass;

    public UnsafeTransformer(boolean forceBypass) {
        this.forceBypass = forceBypass;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!"sun/misc/Unsafe".equals(className)) return null;

        ClassNode cn = new ClassNode();
        new ClassReader(classfileBuffer).accept(cn, ClassReader.SKIP_FRAMES);

        for (MethodNode mn : cn.methods) {
            if ("beforeMemoryAccessSlow".equals(mn.name) && "()V".equals(mn.desc)) {
                if (forceBypass) {
                    mn.instructions.clear();
                    mn.instructions.add(new InsnNode(Opcodes.RETURN));
                } else {
                    LabelNode end = new LabelNode();
                    mn.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            "sun/misc/Unsafe", "MEMORY_ACCESS_OPTION",
                            "Lsun/misc/Unsafe$MemoryAccessOption;"));
                    mn.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            "sun/misc/Unsafe$MemoryAccessOption", "DENY",
                            "Lsun/misc/Unsafe$MemoryAccessOption;"));
                    mn.instructions.add(new JumpInsnNode(Opcodes.IF_ACMPNE, end));
                    mn.instructions.add(new InsnNode(Opcodes.RETURN));
                    mn.instructions.add(end);
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }
}