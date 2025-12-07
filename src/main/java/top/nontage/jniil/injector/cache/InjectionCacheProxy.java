package top.nontage.jniil.injector.cache;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import top.nontage.jniil.utils.InjectionUtil;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InjectionCacheProxy implements Opcodes {
    private static Map<String, byte[]> CACHE;

    static {
        Class<?> hiddenCacheClass = null;
        try {
            hiddenCacheClass = InjectionUtil.findClassAcrossClassLoaders("top.nontage.jniil.injector.cache.InjectionCache");
        } catch (Throwable ignored) {
            try {
                ClassLoader injectionCacheLoader = new ClassLoader(null) {
                    @Override
                    public Class<?> loadClass(String name) throws ClassNotFoundException {
                        if (name.equals("top.nontage.jniil.injector.cache.InjectionCache"))
                            throw new ClassNotFoundException();
                        return super.loadClass(name);
                    }
                };
                byte[] classBytes = generateInjectionCacheBytes();
                hiddenCacheClass = InjectionUtil.unsafeInjectClass(injectionCacheLoader, "top.nontage.jniil.injector.cache.InjectionCache", classBytes);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } finally {
            if (hiddenCacheClass != null) {
                try {
                    Field cacheMapField = hiddenCacheClass.getDeclaredField("CACHE");
                    cacheMapField.setAccessible(true);
                    CACHE = (Map<String, byte[]>) cacheMapField.get(null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                CACHE = new ConcurrentHashMap<>();
            }
        }
    }

    public static void put(Class<?> clazz, byte[] bytecode) {
        CACHE.put(clazz.getName(), bytecode);
    }

    public static byte[] get(Class<?> clazz) {
        return CACHE.get(clazz.getName());
    }

    public static boolean contains(Class<?> clazz) {
        return CACHE.containsKey(clazz.getName());
    }

    public static void clear() {
        CACHE.clear();
    }

    public static byte[] generateInjectionCacheBytes() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        String className = "top/nontage/jniil/injector/cache/InjectionCache";
        String mapDesc = "Ljava/util/Map;";
        String concurrentMap = "java/util/concurrent/ConcurrentHashMap";

        // class header
        cw.visit(V17, ACC_PUBLIC | ACC_SUPER,
                className, null, "java/lang/Object", null);

        // static field: private static final Map<String, byte[]> CACHE;
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                "CACHE",
                "Ljava/util/Map;",
                "Ljava/util/Map<Ljava/lang/String;[B>;",
                null).visitEnd();

        /* <clinit> static block */
        {
            MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();

            // new ConcurrentHashMap()
            mv.visitTypeInsn(NEW, concurrentMap);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, concurrentMap, "<init>", "()V", false);

            // put into CACHE
            mv.visitFieldInsn(PUTSTATIC, className, "CACHE", mapDesc);

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        /* constructor */
        {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }
}
