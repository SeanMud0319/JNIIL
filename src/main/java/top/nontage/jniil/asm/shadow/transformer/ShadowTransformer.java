package top.nontage.jniil.asm.shadow.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.asm.shadow.metadata.ShadowBinding;
import top.nontage.jniil.asm.shadow.metadata.ShadowContext;
import top.nontage.jniil.asm.shadow.metadata.ShadowContextHolder;
import top.nontage.jniil.asm.shadow.rewrite.ShadowFieldRewriter;
import top.nontage.jniil.asm.shadow.rewrite.ShadowMethodRewriter;
import top.nontage.jniil.asm.shadow.scan.ShadowMetadataCollector;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;
import top.nontage.jniil.utils.InjectionUtil;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ShadowTransformer {
    private static final Instrumentation inst = JNIIL.getInstrumentation();
    private static final ShadowContext context = ShadowContextHolder.INSTANCE;

    private static final Set<Class<?>> transformedClasses = ConcurrentHashMap.newKeySet();

    public static void apply(ShadowBinding... bindings) throws Exception {
        Set<Class<?>> classesToTransform = new HashSet<>();

        for (ShadowBinding binding : bindings) {
            context.bindInstance(binding.shadowClass, binding.instanceSupplier);

            if (!transformedClasses.contains(binding.shadowClass)) {
                classesToTransform.add(binding.shadowClass);
            }
        }

        if (classesToTransform.isEmpty()) {
            return;
        }

        ShadowMetadataCollector collector = new ShadowMetadataCollector(context);
        ShadowFieldRewriter fieldRewriter = new ShadowFieldRewriter(context);
        ShadowMethodRewriter methodRewriter = new ShadowMethodRewriter(context);

        for (Class<?> clazz : classesToTransform) {
            byte[] bytes = InjectionUtil.getOriginalClassBytes(clazz);
            ClassReader cr = new ClassReader(bytes);
            ClassNode classNode = new ClassNode();
            cr.accept(classNode, 0);

            collector.collect(classNode);

            fieldRewriter.rewrite(classNode);
            methodRewriter.rewrite(classNode);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(cw);
            byte[] newBytes = cw.toByteArray();

            transform(clazz, newBytes);
            InjectionCacheProxy.put(clazz, newBytes);

            transformedClasses.add(clazz);
        }
    }

    public static void unbind(Class<?>... shadowClasses) {
        for (Class<?> shadowClass : shadowClasses) {
            context.unbindInstance(shadowClass);
        }
    }

    public static void reset() {
        context.reset();
        transformedClasses.clear();
    }

    private static void transform(Class<?> clazz, byte[] classBytes) throws UnmodifiableClassException {
        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (clazz.getName().equals(className.replace('/', '.'))) {
                return classBytes;
            }
            return null;
        };
        inst.addTransformer(transformer, true);
        try {
            inst.retransformClasses(clazz);
        } finally {
            inst.removeTransformer(transformer);
        }
    }
}