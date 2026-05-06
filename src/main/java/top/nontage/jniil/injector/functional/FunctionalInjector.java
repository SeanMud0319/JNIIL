package top.nontage.jniil.injector.functional;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.*;
import top.nontage.jniil.exception.BytecodeVerifyException;
import top.nontage.jniil.injector.base.AbstractMethodInjector;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;
import top.nontage.jniil.interfaces.FunctionalInjectable;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.utils.LocalVariableTableFiller;
import top.nontage.jniil.verify.BytecodeVerifier;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class FunctionalInjector extends AbstractMethodInjector {

    private static class ParsedClass {
        final ClassNode node;
        final ClassReader reader;

        ParsedClass(ClassNode node, ClassReader reader) {
            this.node = node;
            this.reader = reader;
        }
    }

    @Override
    public void inject(Injectable... injectable) throws Exception {
        for (Injectable i : injectable) inject(i);
    }

    @Override
    public void inject(Injectable injectable) throws Exception {
        if (!(injectable instanceof FunctionalInjectable)) {
            throw new UnsupportedOperationException("FunctionalInjector only supports FunctionalInjectable");
        }

        FunctionalInjectable funInjectable = (FunctionalInjectable) injectable;

        for (Method method : funInjectable.getClass().getMethods()) {
            if (!hasInjectAnnotation(method)) continue;
            checkMethod(method);

            TargetInfo info = extractTargetInfo(injectable, method);
            ClassLoader loader = getTargetLoader(info).unwarp();
            Class<?> targetClass = Class.forName(info.typeName, true, loader);

            if (method.isAnnotationPresent(FillLocalVariableTable.class)) {
                byte[] modified = new LocalVariableTableFiller().fillLocalVariableNames(Class.forName(info.typeName), false);
                if (modified != null && modified.length > 0) {
                    InjectionCacheProxy.put(targetClass.getName(), modified);
                }
            }

            byte[] currentBytecode = getCurrentBytecode(info.typeName, targetClass);
            ParsedClass parsed = parseClass(currentBytecode);
            ClassNode cn = parsed.node;
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

            byte[] finalBytecode = generateBytecode(cn, parsed.reader, info.typeName, currentBytecode);
            apply(targetClass, finalBytecode);
            injectedClasses.add(info.typeName);
            InjectionCacheProxy.put(targetClass, finalBytecode);

            if (JNIIL.isMethodOutputEnabled()) {
                String relativePath = info.typeName.replace('.', File.separatorChar) + ".class";
                InjectionUtil.dumpClass(finalBytecode, new File(JNIIL.getMethodOutputDir(), relativePath).getAbsolutePath());
            }
        }
    }

    private byte[] getCurrentBytecode(String typeName, Class<?> targetClass) throws Exception {
        return InjectionCacheProxy.contains(typeName)
                ? InjectionCacheProxy.get(targetClass)
                : InjectionUtil.getOriginalClassBytes(typeName);
    }

    private ParsedClass parseClass(byte[] bytecode) {
        ClassReader cr = new ClassReader(bytecode);
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);
        return new ParsedClass(cn, cr);
    }

    private byte[] generateBytecode(ClassNode cn, ClassReader cr, String typeName, byte[] originalBytecode) throws BytecodeVerifyException {
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
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