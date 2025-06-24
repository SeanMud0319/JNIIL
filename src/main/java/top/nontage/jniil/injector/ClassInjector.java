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
                                ClassLoader targetLoader = InjectionUtil.findClassAcrossClassLoaders(anchorClassName).getClassLoader();
                                byte[] originalBytecode = InjectionUtil.getClassBytes(clazz);
                                byte[] cleanBytecode = InjectionUtil.removeInjectMethodInfo(originalBytecode);
                                InjectionUtil.unsafeInjectClass(targetLoader, injectClassName, cleanBytecode);
                                System.out.println("Injected " + injectClassName + " into " + anchorClassName);
                                if (JNIIL.isClassOutputEnabled()) {
                                    File outputDir = JNIIL.getClassOutputDir();
                                    if (!outputDir.exists()) outputDir.mkdirs();
                                    String filePath = injectClassName.replace('.', '/') + ".class";
                                    File outputFile = new File(outputDir, filePath);
                                    outputFile.getParentFile().mkdirs();
                                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                        fos.write(cleanBytecode);
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
