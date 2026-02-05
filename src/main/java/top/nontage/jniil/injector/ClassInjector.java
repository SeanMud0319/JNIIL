package top.nontage.jniil.injector;

import top.nontage.auth.library.annotation.Protect;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.InjectClassInfo;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.File;
import java.io.FileOutputStream;

@Protect
public class ClassInjector {

    public static void injectClasses(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            injectClass(clazz);
        }
    }

    public static void injectClass(Class<?> clazz) {
        try {
            InjectClassInfo info = clazz.getAnnotation(InjectClassInfo.class);
            if (info == null) {
                return;
            }

            String anchorClassName = info.anchorClass().isEmpty() ? info.anchorClassType().getName() : info.anchorClass();
            String injectClassName = clazz.getName();
            String anchorThreadName = info.anchorThread();

            ClassLoader targetLoader;

            if (anchorThreadName == null || anchorThreadName.isEmpty()) {
                targetLoader = InjectionUtil
                        .findClassAcrossClassLoaders(anchorClassName)
                        .getClassLoader();
            } else {
                targetLoader = InjectionUtil.findClassLoaderByThread(anchorThreadName);
            }

            byte[] originalBytecode = InjectionUtil.getOriginalClassBytes(clazz);

            InjectionUtil.unsafeInjectClass(
                    targetLoader,
                    injectClassName,
                    originalBytecode
            );

            System.out.println(
                    "Injected " + injectClassName +
                            " into " + anchorClassName +
                            (anchorThreadName == null ? "" : ("@" + anchorThreadName))
            );

            if (JNIIL.isClassOutputEnabled()) {
                dumpClass(injectClassName, originalBytecode);
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void dumpClass(String className, byte[] bytecode) throws Exception {
        File outputDir = JNIIL.getClassOutputDir();
        if (!outputDir.exists()) outputDir.mkdirs();

        String filePath = className.replace('.', '/') + ".class";
        File outputFile = new File(outputDir, filePath);
        outputFile.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(bytecode);
            fos.flush();
        }

        System.out.println("Dumped injected class to: " + outputFile.getAbsolutePath());
    }
}
