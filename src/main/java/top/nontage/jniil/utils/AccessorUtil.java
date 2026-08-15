package top.nontage.jniil.utils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ASM9;

// Invoke from AccessorInitializer.DefineClassPatch = Invoke from ClassLoader.defineClass
public class AccessorUtil {
    public static boolean isExcludedPrefix(String className) {
        for (String prefix : EXCLUDED_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    private static final Set<String> EXCLUDED_PREFIXES = new HashSet<>(Arrays.asList(
            "java.", "javax.", "sun.", "jdk.", "com.sun.",
            "com.oracle.", "org.w3c.", "org.xml.", "org.ietf.", "org.omg."
    ));

    public static boolean hasInterfaceAndAnnotation(byte[] classBytes) {
        try {
            ClassReader cr = new ClassReader(classBytes);
            if ((cr.getAccess() & ACC_INTERFACE) == 0) return false;

            final boolean[] found = {false};
            cr.accept(new ClassVisitor(ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (desc.equals("Ltop/nontage/jniil/annotations/BootAccessor;")) {
                        found[0] = true;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            return found[0];
        } catch (Exception e) {
            return false;
        }
    }
}
