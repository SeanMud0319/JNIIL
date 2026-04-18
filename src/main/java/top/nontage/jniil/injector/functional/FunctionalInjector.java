package top.nontage.jniil.injector.functional;

import javassist.*;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.*;
import top.nontage.jniil.exception.BytecodeVerifyException;
import top.nontage.jniil.injector.base.AbstractMethodInjector;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;
import top.nontage.jniil.interfaces.FunctionalInjectable;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.javassist.FileClassPath;
import top.nontage.jniil.javassist.JarFileClassPath;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.verify.BytecodeVerifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Method;

// Test Feature depend Javassist injection
// Functional Injector 只支援用 @InjectMethodInfo 注入，功能具體為委派轉發，不修改內部邏輯，最多只能 cancel 原邏輯執行，並依照回傳值回傳資料，
// 並且 MethodInfo 只回傳被注入方法的實例以及方法參數，不支援局部變數存取，若為 static 方法則回傳 null，如果要修改內部邏輯應該使用一般注入或是 insn 注入。
// Functional Injector 如果要注入到不同 loader 的 class 需要自己另外做處理
public class FunctionalInjector extends AbstractMethodInjector {
    @Override
    protected ClassPool prepareClassPool() {
        return new ClassPool(null);
    }

    @Override
    public void inject(Injectable... injectable) throws Exception {
        for (Injectable IInjectable : injectable) {
            this.inject(IInjectable);
        }
    }

    @Override
    public void inject(Injectable injectable) throws Exception {
        if (!(injectable instanceof FunctionalInjectable)) {
            throw new UnsupportedOperationException("Functional Injector only supported Functional Injectable");
        }
        FunctionalInjectable funInjectable = (FunctionalInjectable) injectable;
        Class<?> clazz = funInjectable.getClass();
        for (Method method : clazz.getMethods()) {
            boolean hasInjectAnnotation =
                    method.isAnnotationPresent(InjectMethodInfo.class) ||
                            method.isAnnotationPresent(After.class) ||
                            method.isAnnotationPresent(Before.class) ||
                            method.isAnnotationPresent(At.class) ||
                            method.isAnnotationPresent(Overwrite.class) ||
                            method.isAnnotationPresent(ReplaceCall.class);

            if (!hasInjectAnnotation) {
                continue;
            }

            checkMethod(method);

            TargetInfo info = extractTargetInfo(injectable, method);
            ClassPool pool = prepareClassPool();
            ClassLoader loader = getTargetLoader(info).unwarp();

            if (info.defaultLoader) {
                pool.insertClassPath(new LoaderClassPath(loader));
                pool.appendSystemPath();
                pool.appendClassPath(new LoaderClassPath(injectable.getClass().getClassLoader()));
            }

            if (info.appendClasses != null) {
                for (Class<?> append : info.appendClasses) {
                    pool.appendClassPath(new LoaderClassPath(append.getClassLoader()));
                }
            }

            if (info.appendFileLoader != null) {
                for (String f : info.appendFileLoader) {
                    if (!f.isEmpty()) pool.appendClassPath(new FileClassPath(new File(f)));
                }
            }

            if (info.appendJarLoader != null) {
                for (String f : info.appendJarLoader) {
                    if (!f.isEmpty()) pool.insertClassPath(new JarFileClassPath(new File(f)));
                }
            }

            if (info.appendByteLoader != null) {
                info.appendByteLoader.forEach((className, bytes) -> pool.insertClassPath(new ByteArrayClassPath(className, bytes)));
            }

            Class<?> targetClass = Class.forName(info.typeName, true, loader);
            // Load CtClass from cache if available, because you can't get redefined class bytecode just from ClassPool or ClassLoader, even from retransformed class.
            CtClass ctClass;
            if (InjectionCacheProxy.contains(info.typeName)) {
                ctClass = pool.makeClass(new ByteArrayInputStream(InjectionCacheProxy.get(targetClass)));
            } else {
                ctClass = pool.get(info.typeName);
            }

            if (ctClass.isFrozen()) ctClass.defrost();

            if (method.isAnnotationPresent(FillLocalVariableTable.class)) {
                throw new UnsupportedOperationException(String.format(
                        "Annotation @FillLocalVariableTable is not supported in Functional Injection (Method: %s). " +
                                "Functional injectors use static proxying and do not require local variable name reconstruction.",
                        method.getName()
                ));
            }

            ctClass = modifyCtClassBeforeInsertCode(ctClass, injectable);

            byte[] originalBytecode = InjectionUtil.getOriginalClassBytes(info.typeName);

            CtMethod ctMethod = getCtMethod(ctClass, info);

            String src = buildSrc(method, ctMethod);

            if (ctClass.isFrozen()) ctClass.defrost();

            insertCode(ctMethod, method, src);

            ctClass = modifyCtClassBeforeRedefinition(ctClass, injectable);

            if (JNIIL.isMethodOutputEnabled()) {
                File outputDir = JNIIL.getMethodOutputDir();
                if (!outputDir.exists()) {
                    // noinspection ResultOfMethodCallIgnored
                    outputDir.mkdirs();
                }
                ctClass.writeFile(outputDir.getAbsolutePath());
                System.out.println("Dumped injected method to: " + outputDir.getAbsolutePath());
            }

            byte[] bytecode = ctClass.toBytecode();

            if (JNIIL.isBytecodeVerifying()) {
                BytecodeVerifier.Result result = BytecodeVerifier.verifyAll(info.typeName, originalBytecode, bytecode);
                if (!result.isAsmValid() || !result.isJvmValid()) {
                    String msg = "[BytecodeVerifier] Class " + ctClass.getName() +
                            " failed verification:\n" + result.getDetails();
                    throw new BytecodeVerifyException(msg);
                }
            }

            apply(targetClass, bytecode);
            injectedClasses.add(info.typeName);

            onInjected(ctClass, injectable);

            getModifiedCtClass(ctClass);

            InjectionCacheProxy.put(targetClass, bytecode);
        }
    }

    private static String buildSrc(Method method, CtMethod ctMethod) throws NotFoundException {
        boolean isTargetStatic = Modifier.isStatic(ctMethod.getModifiers());
        String targetParam = isTargetStatic ? "null" : "$0";

        String methodInfoClass = MethodInfo.class.getName();
        String injectClass = method.getDeclaringClass().getName();
        String injectMethod = method.getName();

        CtClass returnType = ctMethod.getReturnType();
        boolean isVoid = returnType.equals(CtClass.voidType);
        String invokeArgs = (method.getParameterCount() == 1) ? "info" : "";
        StringBuilder src = new StringBuilder("{ ");
        src.append(methodInfoClass).append(" info = new ").append(methodInfoClass).append("(").append(targetParam).append(", $args); ");
        src.append(injectClass).append(".").append(injectMethod).append("(").append(invokeArgs).append("); ");
        src.append("if (info.isCancelled()) { ");
        if (isVoid) {
            src.append("return; ");
        } else {
            src.append("Object r = info.getReturnValue(); ");
            src.append("if (r == null) { ");
            if (returnType.isPrimitive()) {
                src.append("return ($r)($w)0; ");
            } else {
                src.append("return null; ");
            }
            src.append("} else { ");
            src.append("return ($r) r; ");
            src.append("} ");
        }
        src.append("} ");
        src.append("}");
        return src.toString();
    }

    private void checkMethod(Method method) throws IllegalStateException {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)) {
            throw new IllegalStateException(String.format("Injection method '%s' must be public.", method.getName()));
        }
        if (!Modifier.isStatic(modifiers)) {
            throw new IllegalStateException(String.format("Injection method '%s' must be static to be used in functional injection.", method.getName()));
        }
        if (method.getReturnType() != void.class) {
            System.out.println(method.getReturnType());
            throw new IllegalStateException(String.format("Injection method '%s' return type must be void.", method.getName()));
        }
    }

    @Override
    protected TargetInfo extractTargetInfo(Injectable injectable, Method method) {
        TargetInfo info = new TargetInfo();
        boolean hasMethodInfo = method.isAnnotationPresent(InjectMethodInfo.class);
        if (!hasMethodInfo) {
            throw new IllegalArgumentException(String.format(
                    "Missing @InjectMethodInfo annotation on injection method '%s' in class '%s'. " +
                            "Every injection method must be annotated with target metadata.",
                    method.getName(), injectable.getClass().getName()
            ));
        }
        InjectMethodInfo annotation = method.getAnnotation(InjectMethodInfo.class);
        info.typeName = (annotation.targetType() != null && annotation.targetType() != Object.class)
                ? annotation.targetType().getTypeName()
                : annotation.targetTypeInternalName();
        if (info.typeName == null || info.typeName.isEmpty()) {
            throw new IllegalArgumentException("Target type name cannot be empty in @InjectMethodInfo");
        }
        info.methodName = annotation.targetMethodName();
        info.methodParams = classArrayToName(annotation.targetMethodParamTypes(), annotation.targetMethodParams());
        info.appendClasses = annotation.appendClassLoader();
        info.targetTypeThreadName = annotation.targetTypeThreadName();
        info.appendFileLoader = annotation.appendFileLoader();
        info.appendJarLoader = annotation.appendJarLoader();
        info.defaultLoader = annotation.defaultLoader();

        return info;
    }
}
