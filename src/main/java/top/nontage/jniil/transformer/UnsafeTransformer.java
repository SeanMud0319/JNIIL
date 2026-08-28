package top.nontage.jniil.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * Patches {@code sun.misc.Unsafe#beforeMemoryAccessSlow()} to bypass JDK 23+
 * memory-access restrictions.
 *
 * <p>Three modes:
 * <ul>
 *   <li><b>hideWarning = true, forceBypass = false</b>: if {@code MEMORY_ACCESS_OPTION == WARN},
 *       return early to suppress the warning message. Other modes (ALLOW/DEBUG/DENY) proceed normally.</li>
 *   <li><b>hideWarning = true, forceBypass = true</b>: unconditional {@code RETURN},
 *       bypassing ALL checks (ALLOW/WARN/DEBUG/DENY).</li>
 *   <li><b>hideWarning = false, forceBypass = true</b>: unconditional {@code RETURN},
 *       bypassing ALL checks.</li>
 *   <li><b>hideWarning = false, forceBypass = false</b>: no modification.</li>
 * </ul>
 *
 * @since 2026/08/18
 */
public class UnsafeTransformer implements ClassFileTransformer {

    private final boolean hideWarning;
    private final boolean forceBypass;

    public UnsafeTransformer(boolean hideWarning, boolean forceBypass) {
        this.hideWarning = hideWarning;
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
                } else if (hideWarning) {
                    LabelNode end = new LabelNode();
                    InsnList insertList = new InsnList();
                    insertList.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            "sun/misc/Unsafe", "MEMORY_ACCESS_OPTION",
                            "Lsun/misc/Unsafe$MemoryAccessOption;"));
                    insertList.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            "sun/misc/Unsafe$MemoryAccessOption", "WARN",
                            "Lsun/misc/Unsafe$MemoryAccessOption;"));
                    insertList.add(new JumpInsnNode(Opcodes.IF_ACMPNE, end));
                    insertList.add(new InsnNode(Opcodes.RETURN));
                    insertList.add(end);

                    mn.instructions.insert(insertList);
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }
}