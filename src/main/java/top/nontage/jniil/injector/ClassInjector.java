package top.nontage.jniil.injector;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.InjectClassInfo;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.File;
import java.io.FileOutputStream;

public class ClassInjector {
    public static void injectAllClass(String packageName) {
        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .acceptPackages(packageName)
                .scan()) {
            scanResult.getClassesWithAllAnnotations(InjectClassInfo.class.getName())
                    .forEach(classInfo -> {
                        System.out.println("Found injectable class: " + classInfo.getName());
                        try {
                            Class<?> clazz = classInfo.loadClass();
                            if (Injectable.class.isAssignableFrom(clazz)) {
                                InjectClassInfo info = clazz.getAnnotation(InjectClassInfo.class);
                                if (info == null) return;
                                String anchorClassName = info.anchorClass();
                                String injectClassName = clazz.getName();
                                String anchorThreadName = info.anchorThread();
                                ClassLoader targetLoader;
                                if (anchorThreadName == null || anchorThreadName.isEmpty()) {
                                    targetLoader = InjectionUtil.findClassAcrossClassLoaders(anchorClassName).getClassLoader();
                                } else {
                                    targetLoader = InjectionUtil.findClassLoaderByThread(anchorThreadName);
                                }
                                byte[] originalBytecode = InjectionUtil.getClassBytes(clazz);
                                InjectionUtil.unsafeInjectClass(targetLoader, injectClassName, originalBytecode);
                                System.out.println("Injected " + injectClassName + " into " + anchorClassName + anchorThreadName);
                                if (JNIIL.isClassOutputEnabled()) {
                                    File outputDir = JNIIL.getClassOutputDir();
                                    if (!outputDir.exists()) outputDir.mkdirs();
                                    String filePath = injectClassName.replace('.', '/') + ".class";
                                    File outputFile = new File(outputDir, filePath);
                                    outputFile.getParentFile().mkdirs();
                                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                        fos.write(originalBytecode);
                                        fos.flush();
                                        System.out.println("Dumped injected class to: " + outputFile.getAbsolutePath());
                                    }
                                }
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();

                        }
                    });
        }
    }
}
