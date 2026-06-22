package top.nontage.jniil.injector.cache;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.utils.UnsafeUtil;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InjectionCacheProxy
 *
 * <p>This class provides a global bytecode cache shared across all ClassLoaders
 * inside the same JVM instance. Normally, each plugin (or module) runs under a
 * different ClassLoader, which means static fields are <b>not shared</b>, causing
 * duplicated or overwritten bytecode injections.
 *
 * <p>To solve this, we dynamically define a hidden class named
 * {@code top.nontage.jniil.injector.cache.InjectionCache} using a special
 * ClassLoader whose parent is {@code null}. This effectively places the class
 * at the highest possible loader level (near bootstrap), allowing:
 *
 * <ul>
 *     <li>Every plugin's ClassLoader to discover the same InjectionCache class</li>
 *     <li>A single static {@code Map<String, byte[]>} to be shared JVM-wide</li>
 *     <li>Bytecode injection from different plugins to reference the same cache</li>
 * </ul>
 *
 * <p>The logic is:
 * <ol>
 *     <li>Try to find InjectionCache across all loaders</li>
 *     <li>If it exists → reuse its static CACHE map</li>
 *     <li>If it does not exist → generate the class bytecode and inject it</li>
 * </ol>
 *
 * <p>This technique is known as:
 * <b>"Global static storage via a bootstrap-level bridge class"</b>
 * or
 * <b>"Cross-ClassLoader shared state via artificially lifted ClassLoader scope"</b>.
 *
 * <p>We expose simple wrapper methods (put/get/contains/clear) so other code
 * does not need to directly reflect into the hidden InjectionCache class.
 *
 * <p>Why this approach is necessary:
 * <ul>
 *     <li>Plugin ClassLoaders cannot share static fields by default</li>
 *     <li>Returning the same class name in different loaders produces isolated classes</li>
 *     <li>Java 8–21 does not allow easily adding classes to bootstrap without Unsafe</li>
 *     <li>A dedicated parent=null loader allows deterministic class placement</li>
 * </ul>
 *
 * <p>Outcome:
 * <br>All plugins using different ClassLoaders share a single global cache,
 * avoiding duplicated injections and preventing second injections from overriding
 * the first one.
 */
public class InjectionCacheProxy implements Opcodes {
    private static Map<String, byte[]> CACHE;
    private static Map<String, ClassNode> NODE_CACHE;

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
                hiddenCacheClass = UnsafeUtil.defineClass("top.nontage.jniil.injector.cache.InjectionCache", injectionCacheLoader, classBytes);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } finally {
            if (hiddenCacheClass != null) {
                try {
                    Field byteField = hiddenCacheClass.getDeclaredField("CACHE");
                    byteField.setAccessible(true);
                    CACHE = (Map<String, byte[]>) byteField.get(null);

                    Field nodeField = hiddenCacheClass.getDeclaredField("NODE_CACHE");
                    nodeField.setAccessible(true);
                    NODE_CACHE = (Map<String, ClassNode>) nodeField.get(null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                CACHE = new ConcurrentHashMap<>();
                NODE_CACHE = new ConcurrentHashMap<>();
            }
        }
    }

    public static void put(Class<?> clazz, byte[] bytecode) { CACHE.put(clazz.getName(), bytecode); }
    public static void put(String className, byte[] bytecode) { CACHE.put(className, bytecode); }
    public static void put(String className, ClassNode node) { NODE_CACHE.put(className, node); }

    public static byte[] get(Class<?> clazz) { return CACHE.get(clazz.getName()); }
    public static byte[] get(String className) { return CACHE.get(className); }
    public static ClassNode getNode(String className) { return NODE_CACHE.get(className); }

    public static boolean contains(Class<?> clazz) { return CACHE.containsKey(clazz.getName()); }
    public static boolean contains(String className) { return CACHE.containsKey(className); }
    public static boolean containsNode(String className) { return NODE_CACHE.containsKey(className); }

    public static void clear() {
        CACHE.clear();
        NODE_CACHE.clear();
    }

    private static byte[] generateInjectionCacheBytes() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String className = "top/nontage/jniil/injector/cache/InjectionCache";
        String mapDesc = "Ljava/util/Map;";
        String concurrentMap = "java/util/concurrent/ConcurrentHashMap";

        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, className, null, "java/lang/Object", null);

        cw.visitField(ACC_PUBLIC | ACC_STATIC, "CACHE", mapDesc, "Ljava/util/Map<Ljava/lang/String;[B>;", null).visitEnd();
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "NODE_CACHE", mapDesc, "Ljava/util/Map<Ljava/lang/String;Lorg/objectweb/asm/tree/ClassNode;>;", null).visitEnd();

        {
            MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();

            mv.visitTypeInsn(NEW, concurrentMap);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, concurrentMap, "<init>", "()V", false);
            mv.visitFieldInsn(PUTSTATIC, className, "CACHE", mapDesc);

            mv.visitTypeInsn(NEW, concurrentMap);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, concurrentMap, "<init>", "()V", false);
            mv.visitFieldInsn(PUTSTATIC, className, "NODE_CACHE", mapDesc);

            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 0);
            mv.visitEnd();
        }

        {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }
}
