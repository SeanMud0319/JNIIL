package top.nontage.jniil.shadow.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.shadow.metadata.MultiBinding;
import top.nontage.jniil.shadow.metadata.ShadowContext;
import top.nontage.jniil.shadow.metadata.ShadowContextHolder;
import top.nontage.jniil.shadow.rewrite.ShadowFieldRewriter;
import top.nontage.jniil.shadow.rewrite.ShadowMethodRewriter;
import top.nontage.jniil.shadow.scan.ShadowMetadataCollector;
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

    public static void apply(MultiBinding... bindings) throws Exception {
        Set<Class<?>> classesToTransform = new HashSet<>();

        for (MultiBinding binding : bindings) {
            context.bindInstances(binding.getShadowClass().getName(), binding.getSuppliers());

            if (!transformedClasses.contains(binding.getShadowClass())) {
                classesToTransform.add(binding.getShadowClass());
            }
        }

        if (classesToTransform.isEmpty()) {
            return;
        }

        ShadowMetadataCollector collector = new ShadowMetadataCollector(context);
        ShadowFieldRewriter fieldRewriter = new ShadowFieldRewriter(context);
        ShadowMethodRewriter methodRewriter = new ShadowMethodRewriter(context);

        for (Class<?> clazz : classesToTransform) {
            byte[] bytes;
            if (InjectionCacheProxy.contains(clazz)) {
                bytes = InjectionCacheProxy.get(clazz);
            } else {
                bytes = InjectionUtil.getOriginalClassBytes(clazz);
            }
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
            context.unbindInstances(shadowClass.getName());
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
        inst.retransformClasses(clazz);
        inst.removeTransformer(transformer);

    }
}