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

            if (JNIIL.isFunctionalVerifyToggle()) {
                isClassLoaderCompatible(injectableInstance.getClass(), targetClass);
            }

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
                if (resolver.getType() == InjectionPointResolver.InjectionType.OVERWRITE) {
                    throw new IllegalStateException(String.format(
                            "Injection error in method %s: Cannot capture local variable names using @Capture when using @Overwrite, " +
                                    "because the entire method body is being cleared and local variable tables will be invalidated.",
                            targetMethod.name
                    ));
                }

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
                    method, targetMethod, Modifier.isStatic(targetMethod.access), localsToCapture, resolver.getType() == InjectionPointResolver.InjectionType.OVERWRITE
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

    // The target class will be injected with code that calls injectable class members
    private void isClassLoaderCompatible(Class<?> injectableClass, Class<?> targetClass) {
        ClassLoader targetLoader = targetClass.getClassLoader();
        if (injectableClass.getClassLoader() == targetLoader) return;

        try {
            Class.forName(injectableClass.getName(), false, targetLoader);
        } catch (ClassNotFoundException e) {
            throw new InjectionException(
                    "\n[JNIIL-CLASSLOADER-ISOLATION] ClassLoader isolation conflict detected!\n" +
                            "═══════════════════════════════════════════════════════════════════════\n" +
                            "  Injectable class: " + injectableClass.getName() + "\n" +
                            "  NOT visible to target ClassLoader - will cause NoClassDefFoundError on execution\n" +
                            "═══════════════════════════════════════════════════════════════════════\n" +
                            "\n" +
                            "  【ROOT CAUSE EXPLANATION】\n" +
                            "  ──────────────────────────\n" +
                            "  Your injected code effectively opens a new execution path INSIDE the target.\n" +
                            "  The injected bytecode becomes part of the target class and executes within the\n" +
                            "  target ClassLoader context.\n" +
                            "\n" +
                            "  However, your FunctionalInjectable implementation class exists ONLY in your own\n" +
                            "  ClassLoader. When the injected code tries to reference it from within\n" +
                            "  the target ClassLoader, the target ClassLoader cannot find it → NoClassDefFoundError.\n" +
                            "\n" +
                            "  This is the OPPOSITE direction: it's NOT that your loader can't find the target,\n" +
                            "  it's that THE TARGET LOADER CAN'T FIND YOUR CLASSES.\n" +
                            "\n" +
                            "  This error indicates you are in a HIGHLY COMPLEX ClassLoader environment. \n" +
                            "  Please try to avoid referencing external classes in your injectable class as much as possible\n" +
                            "  If you MUST reference external classes, you will need to inject them into the target ClassLoader as\n" +
                            "  well -\n" +
                            "  but be aware that this can cause SEVERE CIRCULAR DEPENDENCY issues. \n" +
                            "  You should MINIMIZE external class references whenever possible.\n" +
                            "═══════════════════════════════════════════════════════════════════════\n" +
                            "  【CLASSLOADER HIERARCHY】\n" +
                            "  ────────────────────────\n" +
                            "  " + formatClassLoaderTree(targetLoader) + "\n" +
                            "  " + formatClassLoaderTree(injectableClass.getClassLoader()) + "\n" +
                            "  (▶ indicates the ClassLoader that failed to locate the class)\n" +
                            "\n" +
                            "  【FAILURE DETAILS】\n" +
                            "  ──────────────────\n" +
                            "  " + (targetLoader != null ? targetLoader.getClass().getSimpleName() : "BootstrapClassLoader (null)") + ".loadClass(\"" + injectableClass.getName() + "\") → ClassNotFoundException\n" +
                            "\n" +
                            "═══════════════════════════════════════════════════════════════════════\n" +
                            "  【SOLUTIONS】（Must be performed BEFORE calling inject()）\n" +
                            "  ─────────────────────────────────────────────────────────────────────\n" +
                            "\n" +
                            "  Solution 1: 【RECOMMENDED】Use ClassInjector.injectClass()\n" +
                            "  ────────────────────────────────────────────────────────────\n" +
                            "  Inject your injectable implementation class into the target ClassLoader\n" +
                            "  BEFORE calling inject():\n" +
                            "\n" +
                            "      // Inject your injectable class into target ClassLoader\n" +
                            "      ClassInjector.injectClass(YourInjectable.class, TargetClass.class);\n" +
                            "\n" +
                            "      // Then perform the injection\n" +
                            "      injector.inject(new YourInjectable());\n" +
                            "\n" +
                            "  Under the hood, this uses UnsafeUtil.defineClass() to define the class\n" +
                            "  directly into the target ClassLoader. Available utilities:\n" +
                            "\n" +
                            "      - ClassInjector.injectClass(clazz, target)       // Most convenient\n" +
                            "      - UnsafeUtil.defineClass(name, loader, bytes)   // Low-level control\n" +
                            "      - InjectionUtil.unsafeInjectClass(loader, name, bytecode)  // Alternative\n" +
                            "\n" +
                            "  ⚠ IMPORTANT: Once you inject your class into the target ClassLoader,\n" +
                            "     the target now has its own COPY of your class. This means any references\n" +
                            "     from that copy to YOUR original ClassLoader's classes will still fail\n" +
                            "     (see Solution 2).\n" +
                            "\n" +
                            "\n" +
                            "  Solution 2: 【ADVANCED】Avoid external dependencies or use reflection\n" +
                            "  ──────────────────────────────────────────────────────────────────────\n" +
                            "  If your injectable class references other external classes, you have two options:\n" +
                            "\n" +
                            "  ① Inject ALL referenced classes into the target ClassLoader as well:\n" +
                            "\n" +
                            "      ClassInjector.injectClass(YourInjectable.class, TargetClass.class);\n" +
                            "      ClassInjector.injectClass(YourHelper.class, TargetClass.class);\n" +
                            "      ClassInjector.injectClass(YourData.class, TargetClass.class);\n" +
                            "\n" +
                            "  ② Use reflection inside the injectable method to avoid hard references:\n" +
                            "\n" +
                            "      @InjectMethodInfo(...)\n" +
                            "      public static void injectedMethod() {\n" +
                            "          try {\n" +
                            "              // No hard-coded reference - uses reflection\n" +
                            "              Class<?> clazz = Class.forName(\"your.ExternalClass\");\n" +
                            "              Method method = clazz.getMethod(\"doSomething\");\n" +
                            "              method.invoke(null);\n" +
                            "          } catch (Exception e) {\n" +
                            "              // Fallback handling\n" +
                            "          }\n" +
                            "      }\n" +
                            "\n" +
                            "  ③ Use InjectionUtil with reflection utilities:\n" +
                            "      InjectionUtil.findClassAcrossClassLoaders(className);\n" +
                            "\n" +
                            "\n" +
                            "  Solution 3: 【FALLBACK】Self-contained injectable methods\n" +
                            "  ──────────────────────────────────────────────────────────\n" +
                            "  Use @Capture to get data from the target method and inline all logic:\n" +
                            "\n" +
                            "      @InjectMethodInfo(...)\n" +
                            "      public static void injectedMethod() {\n" +
                            "          // All logic here - no external class dependencies\n" +
                            "          // Use only JDK classes or primitives or target loader classes\n" +
                            "      }\n" +
                            "\n" +
                            "═══════════════════════════════════════════════════════════════════════\n" +
                            "  【CRITICAL WARNINGS】\n" +
                            "  ─────────────────────\n" +
                            "\n" +
                            "  ⚠ After injecting your class into the target ClassLoader, the target now\n" +
                            "     has a COPY of your class. This copy executes in the target's context.\n" +
                            "     Your ORIGINAL class (in your ClassLoader) and the COPY (in target\n" +
                            "     ClassLoader) are TWO SEPARATE CLASSES as far as the JVM is concerned.\n" +
                            "\n" +
                            "  ⚠ If your injectable class references ANY other class from your class,\n" +
                            "     you MUST inject those classes too. The copied class cannot see your\n" +
                            "     original ClassLoader's classes - it can only see classes that are\n" +
                            "     also defined in the target ClassLoader.\n" +
                            "\n" +
                            "  ⚠ Be careful about circular dependencies when injecting multiple\n" +
                            "     interdependent classes into the target ClassLoader - define them\n" +
                            "     in the correct order to avoid LinkageError.\n" +
                            "\n" +
                            "  ⚠ If you've injected all referenced classes and STILL see NoClassDefFoundError,\n" +
                            "     it means your injected code is trying to call classes that belong to YOUR\n" +
                            "     original ClassLoader from WITHIN the target ClassLoader context.\n" +
                            "     Double-check your injectable method body for any external references.\n" +
                            "\n" +
                            "═══════════════════════════════════════════════════════════════════════\n" +
                            "  【DIAGNOSTICS】\n" +
                            "  ───────────────\n" +
                            "  Injectable class:        " + injectableClass.getName() + "\n" +
                            "  Injectable loader:       " + getLoaderInfo(injectableClass.getClassLoader()) + "\n" +
                            "  Target class:            " + targetClass.getName() + "\n" +
                            "  Target loader:           " + getLoaderInfo(targetLoader) + "\n" +
                            "\n" +
                            "  【QUICK START EXAMPLE】\n" +
                            "  ──────────────────────\n" +
                            "  // Step 1: Inject your class(es) into target ClassLoader BEFORE inject()\n" +
                            "  ClassInjector.injectClass(MyInjectable.class, TargetClass.class);\n" +
                            "  ClassInjector.injectClass(MyHelper.class, TargetClass.class);   // if referenced\n" +
                            "\n" +
                            "  // Step 2: Now perform the injection\n" +
                            "  injector.inject(new MyInjectable());\n" +
                            "\n" +
                            "  // If MyInjectable.method references MyHelper, both are now visible\n" +
                            "  // to the target ClassLoader because both were injected.\n" +
                            "\n" +
                            "═══════════════════════════════════════════════════════════════════════\n" +
                            "  Caused by: " + e.getClass().getName() + ": " + e.getMessage() + "\n" +
                            "═══════════════════════════════════════════════════════════════════════"
            );
        }
    }

    private String formatClassLoaderTree(ClassLoader loader) {
        StringBuilder sb = new StringBuilder();
        ClassLoader current = loader;
        int level = 0;
        while (current != null) {
            for (int i = 0; i < level; i++) {
                sb.append("  ");
            }
            if (level == 0) {
                sb.append("▶ ");
            } else {
                sb.append("└─ ");
            }
            sb.append(current.getClass().getName())
                    .append(" @")
                    .append(Integer.toHexString(System.identityHashCode(current)));

            if (current.getParent() != null) {
                sb.append(" → parent: ");
            }
            sb.append("\n");
            current = current.getParent();
            level++;
        }
        if (level > 0) {
            for (int i = 0; i < level; i++) {
                sb.append("  ");
            }
            sb.append("└─ Bootstrap ClassLoader (null)\n");
        }
        if (level == 0) {
            sb.append("▶ Bootstrap ClassLoader (null)\n");
        }
        return sb.toString();
    }

    private String getLoaderInfo(ClassLoader loader) {
        if (loader == null) {
            return "Bootstrap ClassLoader (null)";
        }
        return loader.getClass().getName() + " @" +
                Integer.toHexString(System.identityHashCode(loader)) +
                (loader.getParent() != null ? " (parent: " + loader.getParent().getClass().getSimpleName() + ")" : " (no parent)");
    }
}