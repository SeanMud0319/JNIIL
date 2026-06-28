package top.nontage.jniil.injector.base;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.objectweb.asm.*;
import org.objectweb.asm.util.Printer;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.*;
import top.nontage.jniil.exception.InjectionException;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.interfaces.InsnInjectable;
import top.nontage.jniil.javassist.FileClassPath;
import top.nontage.jniil.javassist.JarFileClassPath;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.utils.LocalVariableTableFiller;
import top.nontage.jniil.verify.BytecodeVerifier;
import top.nontage.jniil.wrapper.ClassLoaderWrapper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.util.*;

public abstract class AbstractMethodInjector {

    protected static final Instrumentation inst = JNIIL.getInstrumentation();
    protected static final Set<String> injectedClasses = new HashSet<>();
    protected static final Map<Class<?>, byte[]> originalBytecodes = new HashMap<>();

    public abstract void inject(Object... injectable) throws Exception;

    public static class TargetInfo {
        public String typeName;
        public String methodName;
        public String[] methodParams;
        public Class<?>[] appendClasses;
        public String targetTypeThreadName;
        public String[] appendFileLoader;
        public String[] appendJarLoader;
        public Map<String, byte[]> appendByteLoader;
        public boolean defaultLoader;
    }

    /**
     * Why Object instead of Injectable?
     * <p>
     * If the parameter was Injectable, the JVM would try to load the Injectable
     * interface when resolving this method signature. In a Bootstrap ClassLoader
     * environment (when JNIIL is installed with useBootLoader=true), the Bootstrap
     * ClassLoader doesn't have Injectable in its search path. This would cause
     * ClassNotFoundException or LinkageError.
     * <p>
     * By using Object, method resolution doesn't depend on any JNIIL-specific type.
     * The actual type check only happens inside the method body, after the JNIIL
     * core classes are already loaded in the correct classloader.
     * <p>
     * tl;dr: Object avoids ClassLoader conflicts in multi-loader environments.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void inject(Object injectableInstance) throws Exception {
        if (injectableInstance == null) {
            throw new InjectionException("Injectable cannot be null");
        }

        if (!(injectableInstance instanceof Injectable)) {
            throw new InjectionException("Class: " + injectableInstance.getClass().getName() + " needs to implement Injectable");
        }

        Injectable injectable = (Injectable) injectableInstance;
        Class<?> clazz = injectableInstance.getClass();
        Method method = getInjectionMethod(clazz);
        if (method == null) return;

        if (method.getReturnType() != String.class) {
            throw new InjectionException(String.format(
                    "Injection method '%s' must return String, but returns %s",
                    method.getName(), method.getReturnType().getName()
            ));
        }

        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 1) {
            throw new InjectionException(String.format(
                    "Injection method '%s' must have at most 1 parameter, but has %d",
                    method.getName(), paramTypes.length
            ));
        }

        boolean hasInjectAnnotation =
                method.isAnnotationPresent(InjectMethodInfo.class) ||
                        method.isAnnotationPresent(After.class) ||
                        method.isAnnotationPresent(Before.class) ||
                        method.isAnnotationPresent(At.class) ||
                        method.isAnnotationPresent(Overwrite.class) ||
                        method.isAnnotationPresent(ReplaceCall.class);

        if (!hasInjectAnnotation) {
            throw new InjectionException("Method " + clazz.getName() + "." + method.getName() + " lacks injection annotations");
        }

        TargetInfo info = extractTargetInfo(injectable, method);

        if (info.typeName == null || info.typeName.isEmpty()) {
            throw new InjectionException("Target class name cannot be null or empty");
        }
        if (info.methodName == null || info.methodName.isEmpty()) {
            throw new InjectionException("Target method name cannot be null or empty");
        }

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
            info.appendByteLoader.forEach((className, bytes) -> {
                pool.insertClassPath(new ByteArrayClassPath(className, bytes));
            });
        }

        Class<?> targetClass;
        try {
            targetClass = Class.forName(info.typeName, true, loader);
        } catch (ClassNotFoundException e) {
            throw new InjectionException("Target class not found: " + info.typeName, e);
        }

        CtClass ctClass;
        try {
            if (InjectionCacheProxy.contains(info.typeName)) {
                ctClass = pool.makeClass(new ByteArrayInputStream(InjectionCacheProxy.get(targetClass)));
            } else {
                ctClass = pool.get(info.typeName);
            }
        } catch (NotFoundException e) {
            throw new InjectionException("Cannot find class " + info.typeName + " in ClassPool", e);
        } catch (Exception e) {
            throw new InjectionException("Failed to load CtClass for " + info.typeName, e);
        }

        if (ctClass.isFrozen()) ctClass.defrost();

        if (method.isAnnotationPresent(FillLocalVariableTable.class)) {
            byte[] modified = new LocalVariableTableFiller().fillLocalVariableNames(Class.forName(info.typeName), false);
            if (modified != null && modified.length > 0) {
                ctClass.defrost();
                ctClass = pool.makeClass(new ByteArrayInputStream(modified));
            }
        }

        ctClass = modifyCtClassBeforeInsertCode(ctClass, injectable);

        byte[] originalBytecode = InjectionUtil.getOriginalClassBytes(info.typeName);

        CtMethod ctMethod;
        try {
            ctMethod = getCtMethod(ctClass, info);
        } catch (NotFoundException e) {
            throw new InjectionException("Method not found: " + info.methodName + " in class " + info.typeName, e);
        }

        String src = injectable.getInjectSourceCode(ctMethod);

        if (ctClass.isFrozen()) ctClass.defrost();

        try {
            insertCode(ctMethod, method, src);
        } catch (CannotCompileException e) {
            throw new InjectionException("Failed to compile injection code for " + info.methodName + ": " + e.getMessage(), e);
        }

        ctClass = modifyCtClassBeforeRedefinition(ctClass, injectable);

        if (JNIIL.isMethodOutputEnabled()) {
            File outputDir = JNIIL.getMethodOutputDir();
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            ctClass.writeFile(outputDir.getAbsolutePath());
            System.out.println("Dumped injected method to: " + outputDir.getAbsolutePath());
        }

        byte[] bytecode = ctClass.toBytecode();

        if (JNIIL.isBytecodeVerifying()) {
            BytecodeVerifier.verify(info.typeName, originalBytecode, bytecode);
        }

        try {
            apply(targetClass, bytecode);
        } catch (UnmodifiableClassException e) {
            throw new InjectionException("Cannot retransform class " + info.typeName + " (may be locked by JVM)", e);
        }

        injectedClasses.add(info.typeName);

        onInjected(ctClass, injectable);
        getModifiedCtClass(ctClass);
        InjectionCacheProxy.put(targetClass, bytecode);
    }

    protected ClassPool prepareClassPool() {
        return ClassPool.getDefault();
    }

    protected ClassLoaderWrapper getTargetLoader(TargetInfo info) throws ClassNotFoundException {
        if (info.targetTypeThreadName == null || info.targetTypeThreadName.isEmpty()) {
            return ClassLoaderWrapper.of(InjectionUtil.findClassAcrossClassLoaders(info.typeName).getClassLoader());
        }
        return ClassLoaderWrapper.of(InjectionUtil.findClassLoaderByThread(info.targetTypeThreadName));
    }

    protected CtClass modifyCtClassBeforeRedefinition(CtClass ctClass, Injectable injectable) {
        return ctClass;
    }

    protected CtClass modifyCtClassBeforeInsertCode(CtClass ctClass, Injectable injectable) {
        return ctClass;
    }

    protected void onInjected(CtClass ctClass, Injectable injectable) {
    }

    protected void getModifiedCtClass(CtClass ctClass) {
    }

    protected CtMethod getCtMethod(CtClass ctClass, TargetInfo info) throws NotFoundException {
        if (info.methodParams.length == 0) {
            CtMethod method = ctClass.getDeclaredMethod(info.methodName);
            if (method == null) {
                throw new NotFoundException("Method not found: " + info.methodName);
            }
            return method;
        }

        CtClass[] paramTypes = Arrays.stream(info.methodParams)
                .map(type -> {
                    try {
                        return ctClass.getClassPool().get(type);
                    } catch (NotFoundException e) {
                        throw new InjectionException("Parameter type not found: " + type + " for method " + info.methodName, e);
                    }
                })
                .toArray(CtClass[]::new);

        CtMethod method = ctClass.getDeclaredMethod(info.methodName, paramTypes);
        if (method == null) {
            throw new NotFoundException("Method not found: " + info.methodName + " with given parameter types");
        }
        return method;
    }

    protected void insertCode(CtMethod ctMethod, Method method, String src) throws Exception {
        After afterAnn = method.getAnnotation(After.class);
        Before beforeAnn = method.getAnnotation(Before.class);
        At atAnn = method.getAnnotation(At.class);
        Overwrite overwriteAnn = method.getAnnotation(Overwrite.class);
        ReplaceCall replaceCallAnn = method.getAnnotation(ReplaceCall.class);

        if (afterAnn != null) {
            ctMethod.insertAfter(src);
            return;
        }

        if (beforeAnn != null) {
            ctMethod.insertBefore(src);
            return;
        }

        if (atAnn != null) {
            if (atAnn.line() >= 0) {
                ctMethod.insertAt(atAnn.line(), src);
                return;
            }
            if (atAnn.opcode() != 114514) {
                injectByGenericOpcode(ctMethod, atAnn, src);
                return;
            }
        }

        if (overwriteAnn != null) {
            if (!src.startsWith("{") && !src.endsWith("}")) {
                src = "{" + src + "}";
            }
            ctMethod.setBody(src);
            return;
        }

        if (replaceCallAnn != null && !replaceCallAnn.value().isEmpty()) {
            String[] parts = replaceCallAnn.value().split("#");
            if (parts.length != 2) {
                throw new InjectionException("Invalid ReplaceCall format, expected 'ClassName#methodName', got: " + replaceCallAnn.value());
            }
            String replaceCallClass = parts[0];
            String replaceCallMethod = parts[1];
            int limit = replaceCallAnn.limit();
            int[] counts = replaceCallAnn.counts();
            String finalSrc = src;
            ctMethod.instrument(new ExprEditor() {
                int current = 1;

                @Override
                public void edit(MethodCall m) {
                    if (m.getClassName().equals(replaceCallClass) && m.getMethodName().equals(replaceCallMethod)) {
                        boolean shouldReplace = limit >= 0 ? current <= limit
                                : counts.length == 0 || Arrays.stream(counts).anyMatch(c -> c == current);
                        if (shouldReplace) {
                            try {
                                m.replace(finalSrc);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        current++;
                    }
                }
            });
            return;
        }
        throw new InjectionException("No valid injection point specified");
    }

    private void injectByGenericOpcode(CtMethod ctMethod, At at, String src) throws Exception {
        boolean debug = at.debug();
        int targetOpcode = at.opcode();
        if (targetOpcode == 114514 || targetOpcode <= 0) {
            throw new InjectionException(String.format(
                    "Illegal @At configuration in method %s: Opcode %d is invalid",
                    ctMethod.getName(), targetOpcode
            ));
        }

        byte[] classBytes = ctMethod.getDeclaringClass().toBytecode();
        ClassReader classReader = new ClassReader(classBytes);

        String targetMethodName = ctMethod.getName();
        String targetMethodDesc = ctMethod.getSignature();
        String targetIdentifier = at.identifier() != null ? at.identifier().replace('/', '.') : "";
        int targetOrdinal = at.ordinal();
        boolean shiftAfter = at.shiftAfter();

        String targetOpcodeName = targetOpcode < Printer.OPCODES.length
                ? Printer.OPCODES[targetOpcode] : "UNKNOWN_" + targetOpcode;

        if (debug) {
            System.out.println("[JNIIL-Debug] Starting opcode injection for: " + ctMethod.getName());
            System.out.println("[JNIIL-Debug] Target: " + targetOpcodeName + "(" + targetOpcode + "), ID: " + targetIdentifier + ", Ordinal: " + targetOrdinal);
        }

        final int[] foundLineNumber = {-1};

        classReader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals(targetMethodName) || !descriptor.equals(targetMethodDesc)) {
                    return null;
                }

                return new MethodVisitor(Opcodes.ASM9) {
                    private int currentLine = -1;
                    private int currentOrdinal = 0;
                    private int lastOpcodeLine = -1;

                    @Override
                    public void visitLineNumber(int line, Label start) {
                        if (lastOpcodeLine != -1) {
                            foundLineNumber[0] = line;
                            lastOpcodeLine = -1;
                            return;
                        }
                        this.currentLine = line;
                    }

                    private void processOpcode(int opcode, String... identifiers) {
                        if (foundLineNumber[0] != -1) return;

                        boolean opcodeMatch = (opcode == targetOpcode);
                        if (!opcodeMatch && targetOpcode == Opcodes.LDC) {
                            opcodeMatch = (opcode == 19 || opcode == 20);
                        }

                        if (opcodeMatch) {
                            boolean identifierMatch = targetIdentifier.isEmpty();
                            if (!identifierMatch) {
                                for (String id : identifiers) {
                                    if (id == null) continue;
                                    String normalizedId = id.replace('/', '.');
                                    if (normalizedId.equals(targetIdentifier) || normalizedId.endsWith("." + targetIdentifier)) {
                                        identifierMatch = true;
                                        break;
                                    }
                                }
                            }

                            if (identifierMatch) {
                                checkAndSetLineNumber();
                            }
                        }
                    }

                    private void checkAndSetLineNumber() {
                        if (currentLine == -1) return;

                        currentOrdinal++;
                        if (currentOrdinal == targetOrdinal) {
                            if (shiftAfter) {
                                lastOpcodeLine = currentLine;
                            } else {
                                foundLineNumber[0] = currentLine;
                            }
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        processOpcode(opcode);
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        processOpcode(opcode, String.valueOf(operand));
                    }

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        processOpcode(opcode, String.valueOf(var));
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        processOpcode(opcode, type, type.replace('/', '.'));
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        String ownerDotted = owner.replace('/', '.');
                        processOpcode(opcode, name, ownerDotted + "." + name, ownerDotted);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        String ownerDotted = owner.replace('/', '.');
                        processOpcode(opcode, name, ownerDotted + "." + name, ownerDotted);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                        processOpcode(Opcodes.INVOKEDYNAMIC, name);
                    }

                    @Override
                    public void visitJumpInsn(int opcode, Label label) {
                        processOpcode(opcode);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        processOpcode(Opcodes.LDC, value.toString());
                    }

                    @Override
                    public void visitIincInsn(int var, int increment) {
                        processOpcode(Opcodes.IINC, String.valueOf(var));
                    }

                    @Override
                    public void visitEnd() {
                        if (lastOpcodeLine != -1 && foundLineNumber[0] == -1) {
                            foundLineNumber[0] = lastOpcodeLine;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);

        if (foundLineNumber[0] < 0) {
            throw new InjectionException(String.format(
                    "Cannot find anchor: @At(opcode=%s(%d), identifier='%s', ordinal=%d) in method %s",
                    targetOpcodeName, targetOpcode, targetIdentifier, targetOrdinal, ctMethod.getName()
            ));
        }

        ctMethod.getDeclaringClass().defrost();
        ctMethod.insertAt(foundLineNumber[0], src);
    }

    protected void apply(Class<?> clazz, byte[] newBytecode) throws UnmodifiableClassException {
        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (classBeingRedefined == clazz) {
                if (JNIIL.isStoreOriginalByteCode() && !originalBytecodes.containsKey(clazz)) {
                    originalBytecodes.put(clazz, Arrays.copyOf(classfileBuffer, classfileBuffer.length));
                }
                return newBytecode;
            }
            return null;
        };
        inst.addTransformer(transformer, true);
        inst.retransformClasses(clazz);
        inst.removeTransformer(transformer);
    }

    protected TargetInfo extractTargetInfo(Injectable injectable, Method method) {
        TargetInfo info = new TargetInfo();
        boolean hasMethodInfo = method.isAnnotationPresent(InjectMethodInfo.class);
        boolean isInsnInjectable = injectable instanceof InsnInjectable;
        if (hasMethodInfo) {
            InjectMethodInfo annotation = method.getAnnotation(InjectMethodInfo.class);
            info.typeName = annotation.targetType() != Object.class ? annotation.targetType().getTypeName() : annotation.targetTypeInternalName();
            info.methodName = annotation.targetMethodName();
            info.methodParams = classArrayToName(annotation.targetMethodParamTypes(), annotation.targetMethodParams());
            info.appendClasses = annotation.appendClassLoader();
            info.targetTypeThreadName = annotation.targetTypeThreadName();
            info.appendFileLoader = annotation.appendFileLoader();
            info.appendJarLoader = annotation.appendJarLoader();
            info.defaultLoader = annotation.defaultLoader();
            return info;
        }
        info.typeName = injectable.targetType() != null && injectable.targetType() != Object.class ? injectable.targetType().getName() : injectable.targetTypeInternalName();
        info.methodName = injectable.targetMethodName();
        info.methodParams = classArrayToName(injectable.targetMethodParamTypes(), injectable.targetMethodParams());
        if (!isInsnInjectable) {
            info.appendClasses = injectable.appendClassLoader();
            info.targetTypeThreadName = injectable.targetTypeThreadName();
            info.appendFileLoader = injectable.appendFileLoader();
            info.appendJarLoader = injectable.appendJarLoader();
            info.appendByteLoader = injectable.appendByteLoader();
            info.defaultLoader = injectable.defaultLoader();
        }
        return info;
    }

    protected String[] classArrayToName(Class<?>[] classes, String[] fallback) {
        if (classes != null && classes.length != 0) {
            return Arrays.stream(classes)
                    .map(Class::getName)
                    .toArray(String[]::new);
        }
        return fallback != null ? fallback : new String[0];
    }

    private Method getInjectionMethod(Class<?> clazz) {
        try {
            return clazz.getDeclaredMethod("getInjectSourceCode", CtMethod.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    public static Map<Class<?>, byte[]> getOriginalBytecodes() {
        return originalBytecodes;
    }
}