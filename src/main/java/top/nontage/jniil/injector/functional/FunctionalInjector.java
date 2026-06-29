package top.nontage.jniil.injector.functional;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.*;
import top.nontage.jniil.exception.BytecodeVerifyException;
import top.nontage.jniil.exception.InjectionException;
import top.nontage.jniil.injector.base.AbstractMethodInjector;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;
import top.nontage.jniil.injector.functional.internal.InjectionPointResolver;
import top.nontage.jniil.injector.functional.internal.LocalVariableValidator;
import top.nontage.jniil.injector.functional.internal.MethodInfoCodeGenerator;
import top.nontage.jniil.interfaces.FunctionalInjectable;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.utils.LocalVariableTableFiller;
import top.nontage.jniil.verify.BytecodeVerifier;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class FunctionalInjector extends AbstractMethodInjector {

    private static class CachedClass {
        final ClassNode node;
        final byte[] bytecode;
        final ClassReader reader;

        CachedClass(ClassNode node, byte[] bytecode, ClassReader reader) {
            this.node = node;
            this.bytecode = bytecode;
            this.reader = reader;
        }
    }

    @Override
    public void inject(Object... injectable) throws Exception {
        for (Object i : injectable) this.inject(i);
    }

    @Override
    public void inject(Object injectableInstance) throws Exception {
        if (!(injectableInstance instanceof FunctionalInjectable)) {
            throw new InjectionException("Class: " + injectableInstance.getClass().getName() + " needs to implement FunctionalInjectable");
        }

        FunctionalInjectable funInjectable = (FunctionalInjectable) injectableInstance;

        for (Method method : funInjectable.getClass().getMethods()) {
            if (!hasInjectAnnotation(method)) continue;
            checkMethod(method);

            TargetInfo info = extractTargetInfo(funInjectable, method);
            ClassLoader loader = getTargetLoader(info).unwarp();
            Class<?> targetClass = Class.forName(info.typeName, true, loader);

            if (method.isAnnotationPresent(FillLocalVariableTable.class)) {
                byte[] modified = new LocalVariableTableFiller().fillLocalVariableNames(Class.forName(info.typeName), false);
                if (modified != null && modified.length > 0) {
                    InjectionCacheProxy.put(targetClass.getName(), modified);
                }
            }

            CachedClass cached = getCachedClass(info.typeName, targetClass);
            ClassNode cn = cached.node;
            ClassReader cr = cached.reader;
            byte[] baseBytecode = cached.bytecode;

            MethodNode targetMethod = findTargetMethod(cn, info);

            if (targetMethod == null) {
                throw new NoSuchMethodException(info.methodName + " in " + info.typeName);
            }

            InjectionPointResolver resolver = new InjectionPointResolver(targetMethod, method);
            Capture capture = method.getAnnotation(Capture.class);
            String[] localsToCapture = capture != null ? capture.value() : new String[0];

            if (localsToCapture.length > 0 && resolver.getType() != InjectionPointResolver.InjectionType.AT_OPCODE) {
                List<String> nameCaptures = new ArrayList<>();
                for (String s : localsToCapture) {
                    if (!s.startsWith("=")) {
                        nameCaptures.add(s);
                    }
                }
                if (!nameCaptures.isEmpty()) {
                    new LocalVariableValidator(targetMethod).validate(
                            nameCaptures.toArray(new String[0]),
                            resolver.getInjectionLine(),
                            resolver.isShiftAfter()
                    );
                }
            }

            InsnList injectedCode = new MethodInfoCodeGenerator(
                    method, targetMethod, Modifier.isStatic(targetMethod.access), localsToCapture
            ).generate();

            resolver.inject(injectedCode);

            byte[] finalBytecode = generateBytecode(cn, cr, info.typeName, baseBytecode, targetClass.getClassLoader());
            apply(targetClass, finalBytecode, baseBytecode);
            injectedClasses.add(info.typeName);
            InjectionCacheProxy.put(info.typeName, finalBytecode);
            InjectionCacheProxy.put(info.typeName, cn);

            if (JNIIL.isMethodOutputEnabled()) {
                String relativePath = info.typeName.replace('.', File.separatorChar) + ".class";
                InjectionUtil.dumpClass(finalBytecode, new File(JNIIL.getMethodOutputDir(), relativePath).getAbsolutePath());
            }
        }
    }

    private CachedClass getCachedClass(String typeName, Class<?> targetClass) throws Exception {
        ClassNode cachedNode = InjectionCacheProxy.getNode(typeName);
        byte[] cachedBytecode = InjectionCacheProxy.contains(typeName) ? InjectionCacheProxy.get(targetClass) : null;

        if (cachedNode == null && cachedBytecode != null) {
            ClassReader cr = new ClassReader(cachedBytecode);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.EXPAND_FRAMES);
            InjectionCacheProxy.put(typeName, cn);
            return new CachedClass(cn, cachedBytecode, cr);
        }

        if (cachedNode != null && cachedBytecode != null) {
            ClassReader reader = new ClassReader(cachedBytecode);
            return new CachedClass(cachedNode, cachedBytecode, reader);
        }

        byte[] originalBytecode = InjectionUtil.getOriginalClassBytes(typeName);
        ClassReader cr = new ClassReader(originalBytecode);
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);

        return new CachedClass(cn, originalBytecode, cr);
    }

    private byte[] generateBytecode(ClassNode cn, ClassReader cr, String typeName, byte[] originalBytecode, ClassLoader targetLoader) throws BytecodeVerifyException {
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                Class<?> c, d;
                try {
                    c = Class.forName(type1.replace('/', '.'), false, targetLoader);
                    d = Class.forName(type2.replace('/', '.'), false, targetLoader);
                } catch (Exception e) {
                    return "java/lang/Object";
                }

                if (c.isAssignableFrom(d)) return type1;
                if (d.isAssignableFrom(c)) return type2;
                if (c.isInterface() || d.isInterface()) return "java/lang/Object";

                do {
                    c = c.getSuperclass();
                } while (!c.isAssignableFrom(d));
                return c.getName().replace('.', '/');
            }
        };

        cn.accept(cw);
        byte[] finalBytecode = cw.toByteArray();

        if (JNIIL.isBytecodeVerifying()) {
            BytecodeVerifier.verify(typeName, originalBytecode, finalBytecode);
        }

        return finalBytecode;
    }

    private MethodNode findTargetMethod(ClassNode cn, TargetInfo info) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals(info.methodName)) continue;

            if (info.methodParams.length > 0) {
                String currentParams = mn.desc.substring(0, mn.desc.indexOf(')') + 1);
                String expectedParams = InjectionUtil.getMethodDescriptor(info.methodParams, "V");
                expectedParams = expectedParams.substring(0, expectedParams.indexOf(')') + 1);
                if (currentParams.equals(expectedParams)) return mn;
            } else {
                return mn;
            }
        }
        return null;
    }

    private boolean hasInjectAnnotation(Method method) {
        return method.isAnnotationPresent(InjectMethodInfo.class) ||
                method.isAnnotationPresent(After.class) ||
                method.isAnnotationPresent(Before.class) ||
                method.isAnnotationPresent(At.class) ||
                method.isAnnotationPresent(Overwrite.class) ||
                method.isAnnotationPresent(ReplaceCall.class);
    }

    private void checkMethod(Method method) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalStateException("Injection method must be public: " + method.getName());
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException("Injection method must be static: " + method.getName());
        }
        if (method.getReturnType() != void.class) {
            throw new IllegalStateException("Injection method return type must be void: " + method.getName());
        }
    }
}