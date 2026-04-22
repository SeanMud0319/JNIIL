package top.nontage.jniil.injector.base;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.objectweb.asm.*;
import org.objectweb.asm.util.Printer;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.*;
import top.nontage.jniil.exception.BytecodeVerifyException;
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

/**
 * Abstract base class for method injection.
 * <p>
 * This class provides the full injection workflow using Javassist and NativeInstrumentation.
 * Subclasses can override specific steps to customize ClassPool, ClassLoader, CtClass processing,
 * or perform callbacks after injection.
 **/
public abstract class AbstractMethodInjector {

    protected static final Instrumentation inst = JNIIL.getInstrumentation();
    protected static final Set<String> injectedClasses = new HashSet<>();
    protected static final Map<Class<?>, byte[]> originalBytecodes = new HashMap<>();

    public abstract void inject(Injectable... injectable) throws Exception;

    /**
     * Encapsulates target class and method information to simplify passing data between methods.
     **/
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
     * Performs bytecode injection for the specified {@link Injectable} instance.
     * <p>
     * This is the core method of the JNIIL framework. It scans all methods within the given
     * {@code injectable} object for injection annotations (such as {@code @Before}, {@code @After},
     * {@code @At}, etc.), determines the target class and method, modifies their bytecode using
     * Javassist, and finally redefines the class through {@link java.lang.instrument.Instrumentation}.
     * </p>
     *
     * <h3>Workflow Overview</h3>
     * <ol>
     *   <li>Scan all declared methods of {@code injectable} for recognized injection annotations.</li>
     *   <li>Extract target metadata using {@link #extractTargetInfo(Injectable, Method)}.</li>
     *   <li>Prepare a {@link ClassPool} and insert all required class paths (including system, file, and JAR paths).</li>
     *   <li>Load the target {@link CtClass} and, if annotated with {@link FillLocalVariableTable},
     *       reconstruct its LocalVariableTable.</li>
     *   <li>Obtain the corresponding {@link CtMethod} and insert the injection source code
     *       {@link Injectable#getInjectSourceCode(CtMethod)}).</li>
     *   <li>If {@link JNIIL#isMethodOutputEnabled()} is enabled, dump the modified class file to
     *       the configured output directory.</li>
     *   <li>If {@link JNIIL#isBytecodeVerifying()} is enabled, verify the modified bytecode using
     *       {@link BytecodeVerifier}. Verification failures raise a {@link BytecodeVerifyException}.</li>
     *   <li>If verification succeeds, redefine the target class using the agent's API.</li>
     *   <li>Invoke {@link #onInjected(CtClass, Injectable)} to notify post-injection callbacks.</li>
     * </ol>
     *
     * <h3>Supported Injection Annotations</h3>
     * <ul>
     *   <li>{@link InjectMethodInfo}</li>
     *   <li>{@link Before}</li>
     *   <li>{@link After}</li>
     *   <li>{@link At}</li>
     *   <li>{@link ReplaceCall}</li>
     * </ul>
     *
     * <h3>Notes</h3>
     * <ul>
     *   <li>Frozen classes will be automatically defrosted before modification.</li>
     *   <li>If bytecode verification fails, a {@link BytecodeVerifyException} is thrown and
     *       the injection process is aborted.</li>
     *   <li>This method performs unsafe runtime redefinition and must be executed in an environment
     *       with instrumentation privileges.</li>
     * </ul>
     *
     * @param injectable the {@link Injectable} instance containing annotated injection methods and logic.
     * @throws Exception if any of the following occur:
     *                   <ul>
     *                     <li>The target class or method cannot be found.</li>
     *                     <li>Javassist fails to read, modify, or write the class bytecode.</li>
     *                     <li>The redefinition process via Instrumentation throws an exception.</li>
     *                     <li>{@link BytecodeVerifier} detects an invalid or corrupted class structure.</li>
     *                   </ul>
     * @see Injectable
     * @see BytecodeVerifier
     * @see BytecodeVerifyException
     * @see FillLocalVariableTable
     * @see ClassPool
     * @see CtMethod
     * @see JNIIL
     */
    public void inject(Injectable injectable) throws Exception {
        Class<?> clazz = injectable.getClass();
        Method method = getInjectionMethod(clazz);
        if (method == null) return;
        boolean hasInjectAnnotation =
                method.isAnnotationPresent(InjectMethodInfo.class) ||
                        method.isAnnotationPresent(After.class) ||
                        method.isAnnotationPresent(Before.class) ||
                        method.isAnnotationPresent(At.class) ||
                        method.isAnnotationPresent(Overwrite.class) ||
                        method.isAnnotationPresent(ReplaceCall.class);

        if (!hasInjectAnnotation) {
            throw new IllegalArgumentException("Method in " + clazz.getName() + " lacks injection annotations.");
        }

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
            info.appendByteLoader.forEach((className, bytes) -> {
                pool.insertClassPath(new ByteArrayClassPath(className, bytes));
            });
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
            byte[] modified = new LocalVariableTableFiller().fillLocalVariableNames(Class.forName(info.typeName), false);
            if (modified != null && modified.length > 0) {
                ctClass.defrost();
                ctClass = pool.makeClass(new ByteArrayInputStream(modified));
            }
        }

        ctClass = modifyCtClassBeforeInsertCode(ctClass, injectable);

        byte[] originalBytecode = InjectionUtil.getOriginalClassBytes(info.typeName);

        CtMethod ctMethod = getCtMethod(ctClass, info);

        String src = injectable.getInjectSourceCode(ctMethod);

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

    /**
     * Allows subclasses to provide a custom {@link ClassPool}.
     * <p>
     * Subclasses can append additional class paths or use a different ClassPool.
     *
     * @return a {@link ClassPool} to use for loading target classes
     **/
    protected ClassPool prepareClassPool() {
        return ClassPool.getDefault();
    }

    /**
     * Allows subclasses to provide a custom {@link ClassLoader} for the target class.
     *
     * @param info target information including class name and optional thread name
     * @return the {@link ClassLoader} used to load the target class
     * @throws ClassNotFoundException if the target class cannot be found
     **/
    protected ClassLoaderWrapper getTargetLoader(TargetInfo info) throws ClassNotFoundException {
        if (info.targetTypeThreadName == null || info.targetTypeThreadName.isEmpty()) {
            return ClassLoaderWrapper.of(InjectionUtil.findClassAcrossClassLoaders(info.typeName).getClassLoader());
        }
        return ClassLoaderWrapper.of(InjectionUtil.findClassLoaderByThread(info.targetTypeThreadName));
    }

    /**
     * Allows subclasses to modify the {@link CtClass} before it is redefined.
     * <p>
     * This can be used to apply additional transformations, instrumentation, or bytecode modifications.
     *
     * @param ctClass    the target {@link CtClass} to modify
     * @param injectable the original {@link Injectable} providing injection information
     * @return the modified {@link CtClass}, can be the same instance or a new one
     */
    protected CtClass modifyCtClassBeforeRedefinition(CtClass ctClass, Injectable injectable) {
        return ctClass;
    }

    /**
     * Allows subclasses to modify the {@link CtClass} before code insertion.
     * <p>
     * This can be used to prepare the class, add fields, or perform other setup before injecting code.
     *
     * @param ctClass    the target {@link CtClass} to modify
     * @param injectable the original {@link Injectable} providing injection information
     * @return the modified {@link CtClass}, can be the same instance or a new one
     */
    protected CtClass modifyCtClassBeforeInsertCode(CtClass ctClass, Injectable injectable) {
        return ctClass;
    }

    /**
     * Callback invoked after injection is complete.
     * <p>
     * Subclasses can override to perform additional actions, logging, or further processing.
     *
     * @param ctClass    the target {@link CtClass} after injection
     * @param injectable the original {@link Injectable} used for injection
     **/
    protected void onInjected(CtClass ctClass, Injectable injectable) {

    }

    /**
     * Retrieves the modified {@link CtClass} after injection.
     *
     * @param ctClass the target {@link CtClass}
     **/
    protected void getModifiedCtClass(CtClass ctClass) {

    }

    /**
     * Retrieves the target method from the {@link CtClass} based on {@link TargetInfo}.
     *
     * @param ctClass the target {@link CtClass}
     * @param info    the target method information
     * @return the {@link CtMethod} corresponding to the target
     * @throws Exception if the method cannot be found
     **/
    protected CtMethod getCtMethod(CtClass ctClass, TargetInfo info) throws Exception {
        if (info.methodParams.length == 0) return ctClass.getDeclaredMethod(info.methodName);

        CtClass[] paramTypes = Arrays.stream(info.methodParams)
                .map(type -> {
                    try {
                        return ctClass.getClassPool().get(type);
                    } catch (NotFoundException e) {
                        throw new RuntimeException("Parameter type not found: " + type, e);
                    }
                })
                .toArray(CtClass[]::new);
        return ctClass.getDeclaredMethod(info.methodName, paramTypes);
    }

    /**
     * Inserts code into the {@link CtMethod} based on annotations.
     * <p>
     * Supports @After, @Before, @At(line, opcode), and @ReplaceCall.
     *
     * @param ctMethod the target method
     * @param method   the method in the {@link Injectable} class
     * @param src      the source code to insert
     * @throws Exception if insertion fails
     **/
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
            if (parts.length != 2) throw new IllegalArgumentException("Invalid ReplaceCall format");
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
        throw new IllegalArgumentException("No valid injection point specified via @After, @Before, @At, @ReplaceAll or @ReplaceCall");
    }

    private void injectByGenericOpcode(CtMethod ctMethod, At at, String src) throws Exception {
        boolean debug = at.debug();
        int targetOpcode = at.opcode();
        if (targetOpcode == 114514 || targetOpcode <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Illegal @At configuration in method %s: Opcode %d is invalid!",
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
            throw new IllegalStateException(String.format(
                    "Cannot find anchor: @At(opcode=%s(%d), identifier='%s', ordinal=%d) in method %s.",
                    targetOpcodeName, targetOpcode, targetIdentifier, targetOrdinal, ctMethod.getName()
            ));
        }

        ctMethod.getDeclaringClass().defrost();
        ctMethod.insertAt(foundLineNumber[0], src);
    }

    /**
     * Redefines a class using {@link Instrumentation} with the new bytecode.
     * <p>
     * Stores the original bytecode if JNIIL.isStoreOriginalByteCode() is enabled.
     *
     * @param clazz       the class to redefine
     * @param newBytecode the new bytecode
     * @throws UnmodifiableClassException when can't modify
     **/
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

    /**
     * Extracts {@link TargetInfo} from a method annotated with @InjectMethodInfo or @Null.
     *
     * @param injectable the injectable instance
     * @param method     the method to read annotations from
     * @return the populated {@link TargetInfo}
     **/
    protected TargetInfo extractTargetInfo(Injectable injectable, Method method) {
        TargetInfo info = new TargetInfo();
        boolean hasMethodInfo = method.isAnnotationPresent(InjectMethodInfo.class);
        boolean isInsnInjectable = injectable instanceof InsnInjectable;
        if (hasMethodInfo) {
            InjectMethodInfo annotation = method.getAnnotation(InjectMethodInfo.class);
            info.typeName = annotation.targetType() != null ? annotation.targetType().getTypeName() : annotation.targetTypeInternalName();
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

    /**
     * Returns the original bytecodes stored for redefined classes.
     *
     * @return a map of class to its original bytecode
     **/
    public static Map<Class<?>, byte[]> getOriginalBytecodes() {
        return originalBytecodes;
    }
}
