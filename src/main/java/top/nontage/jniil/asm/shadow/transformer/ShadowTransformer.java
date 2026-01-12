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
import java.util.HashMap;
import java.util.Map;

public class ShadowTransformer {
    private static final Instrumentation inst = JNIIL.getInstrumentation();

    public static void apply(ShadowBinding... bindings) throws Exception {
        ShadowContext context = ShadowContextHolder.INSTANCE;
        context.reset();

        Map<String, ClassNode> classNodeMap = new HashMap<>();

        for (ShadowBinding binding : bindings) {
            context.bindInstance(binding.shadowClass, binding.instance);
        }

        Map<String, Class<?>> classesToProcess = new HashMap<>();
        for (ShadowBinding binding : bindings) {
            classesToProcess.put(binding.shadowClass.getName(), binding.shadowClass);
            if (binding.instance != null) {
                classesToProcess.put(binding.instance.getClass().getName(), binding.instance.getClass());
            }
        }

        ShadowMetadataCollector collector = new ShadowMetadataCollector(context);
        for (Class<?> clazz : classesToProcess.values()) {
            byte[] bytes = InjectionUtil.getOriginalClassBytes(clazz);
            ClassReader cr = new ClassReader(bytes);
            ClassNode classNode = new ClassNode();
            cr.accept(classNode, 0);

            collector.collect(classNode);
            classNodeMap.put(classNode.name, classNode);
        }

        for (Map.Entry<String, ClassNode> entry : classNodeMap.entrySet()) {
            ClassNode classNode = entry.getValue();
            new ShadowFieldRewriter(context).rewrite(classNode);
            new ShadowMethodRewriter(context).rewrite(classNode);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(cw);
            byte[] newBytes = cw.toByteArray();

            Class<?> targetClass = Class.forName(entry.getKey().replace('/', '.'), true, Thread.currentThread().getContextClassLoader());
            transform(targetClass, newBytes);
            InjectionCacheProxy.put(targetClass, newBytes);
        }
    }


    private static void transform(Class<?> clazz, byte[] classBytes) throws UnmodifiableClassException {
        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (clazz == classBeingRedefined) {
                return classBytes;
            }
            return null;
        };
        inst.addTransformer(transformer, true);
        inst.retransformClasses(clazz);
        inst.removeTransformer(transformer);
    }
}