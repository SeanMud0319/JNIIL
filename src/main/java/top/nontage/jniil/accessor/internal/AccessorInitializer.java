package top.nontage.jniil.accessor.internal;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.injector.insn.InsnContext;
import top.nontage.jniil.injector.insn.InstructionInjector;
import top.nontage.jniil.interfaces.InsnInjectable;
import top.nontage.jniil.utils.UnsafeUtil;

/**
 * <h1>AccessorInitializer</h1>
 * * <p>A low-level JVM patch addressing <a href="https://bugs.openjdk.org/browse/JDK-8263089">JDK-8263089</a>.</p>
 * * <p>This class manages the lifecycle of generated Accessors by bypassing ClassLoader isolation.
 * When Java's reflection inflates (after ~15 uses), it creates classes inheriting from {@code MagicAccessorImpl}.
 * Due to the unidirectional nature of ClassLoaders and a specific bug in {@code DelegatingClassLoader},
 * these internal accessors often fail to resolve target interfaces.</p>
 * * <p><b>Mechanism:</b>
 * <ul>
 * <li>Injects a registry into the Bootstrap ClassPath.</li>
 * <li>Applies runtime bytecode patches to {@link ClassLoader} to intercept loading requests.</li>
 * <li>Redirects lookups containing "$$ImplByJNIIL$$" to our internal registry, bridging the isolation gap.</li>
 * </ul>
 * </p>
 */
public class AccessorInitializer {
    private static boolean initialize = false;
    private static Class<?> accessorRegistry;

    private static final boolean IS_JAVA_8;
    static {
        boolean is8 = false;
        try {
            Class.forName("jdk.internal.loader.BuiltinClassLoader");
        } catch (ClassNotFoundException e) {
            is8 = true;
        }
        IS_JAVA_8 = is8;
    }

    public static void init() {
        if (initialize) return;
        initialize = true;
        try {
            accessorRegistry = UnsafeUtil.defineClass("top.nontage.jniil.accessor.AccessorRegistry", null, generateRegistryBytecode());
            boolean verify = JNIIL.isJvmVerifyToggle();
            try {
                if (verify) JNIIL.setJvmVerifyToggle(false);

                if (IS_JAVA_8) {
                    new InstructionInjector().inject(new DelegatingClassLoaderPatch());
                } else {
                    new InstructionInjector().inject(new BuiltinClassLoaderPatch());
                }
            } finally {
                if (verify) JNIIL.setJvmVerifyToggle(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize AccessorRegistry", e);
        }
    }

    public static Class<?> getAccessorRegistry() {
        return accessorRegistry;
    }

    private static byte[] generateRegistryBytecode() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        String className = "top/nontage/jniil/accessor/AccessorRegistry";
        String classPath = className.replace('.', '/');

        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, classPath, null, "java/lang/Object", null);

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "REGISTRY", "Ljava/util/Map;",
                "Ljava/util/Map<Ljava/lang/String;Ljava/lang/Class<*>;>;", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "java/util/concurrent/ConcurrentHashMap");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/concurrent/ConcurrentHashMap",
                "<init>", "()V", false);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, classPath, "REGISTRY", "Ljava/util/Map;");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "register",
                "(Ljava/lang/String;Ljava/lang/Class;)V", null, null);
        mv.visitCode();

        mv.visitFieldInsn(Opcodes.GETSTATIC, classPath, "REGISTRY", "Ljava/util/Map;");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(4, 2);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "find",
                "(Ljava/lang/String;)Ljava/lang/Class;", null, null);
        mv.visitCode();

        mv.visitFieldInsn(Opcodes.GETSTATIC, classPath, "REGISTRY", "Ljava/util/Map;");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Class");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    // JDK-8263089 for Java8
    private static class DelegatingClassLoaderPatch implements InsnInjectable {
        @InjectMethodInfo(
                targetType = ClassLoader.class,
                targetMethodName = "loadClass",
                targetMethodParamTypes = {String.class, boolean.class}
        )
        @At(opcode = ALOAD, identifier = "0")
        @Override
        public InsnList apply(InsnContext ctx, InsnList insns) {
            InsnList inst = new InsnList();
            LabelNode notFound = new LabelNode();

            inst.add(new VarInsnNode(ALOAD, 1));
            inst.add(new InsnNode(DUP));
            inst.add(new LdcInsnNode("$$ImplByJNIIL$$"));
            inst.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "contains",
                    "(Ljava/lang/CharSequence;)Z", false));
            inst.add(new JumpInsnNode(IFEQ, notFound));
            inst.add(new MethodInsnNode(INVOKESTATIC,
                    "top/nontage/jniil/accessor/AccessorRegistry",
                    "find", "(Ljava/lang/String;)Ljava/lang/Class;", false));
            inst.add(new InsnNode(DUP));
            inst.add(new JumpInsnNode(IFNULL, notFound));
            inst.add(new InsnNode(ARETURN));
            inst.add(notFound);
            inst.add(new InsnNode(POP));
            return inst;
        }
    }

    // JDK-8263089 for Java11 and higher
    private static class BuiltinClassLoaderPatch implements InsnInjectable {
        @InjectMethodInfo(
                targetTypeInternalName = "jdk.internal.loader.BuiltinClassLoader",
                targetMethodName = "loadClassOrNull",
                targetMethodParamTypes = {String.class, boolean.class}
        )
        @At(opcode = ALOAD, identifier = "0")
        @Override
        public InsnList apply(InsnContext ctx, InsnList insns) {
            InsnList inst = new InsnList();
            LabelNode notFound = new LabelNode();

            inst.add(new VarInsnNode(ALOAD, 1));
            inst.add(new InsnNode(DUP));
            inst.add(new LdcInsnNode("$$ImplByJNIIL$$"));
            inst.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "contains",
                    "(Ljava/lang/CharSequence;)Z", false));
            inst.add(new JumpInsnNode(IFEQ, notFound));
            inst.add(new MethodInsnNode(INVOKESTATIC,
                    "top/nontage/jniil/accessor/AccessorRegistry",
                    "find", "(Ljava/lang/String;)Ljava/lang/Class;", false));
            inst.add(new InsnNode(DUP));
            inst.add(new JumpInsnNode(IFNULL, notFound));
            inst.add(new InsnNode(ARETURN));
            inst.add(notFound);
            inst.add(new InsnNode(POP));

            return inst;
        }
    }
}
