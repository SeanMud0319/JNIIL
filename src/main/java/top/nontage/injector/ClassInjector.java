package top.nontage.injector;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import top.nontage.JNIIL;
import top.nontage.annotations.InjectClassInfo;
import top.nontage.interfaces.Injectable;
import top.nontage.utils.InjectionUtil;

public class ClassInjector {
    public static void injectAllClass() {
        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .acceptPackages(JNIIL.class.getPackageName())
                .scan()) {
            scanResult.getClassesWithAllAnnotations(InjectClassInfo.class.getName())
                    .forEach(classInfo -> {
                        try {
                            Class<?> clazz = classInfo.loadClass();
                            if (Injectable.class.isAssignableFrom(clazz)) {
                                InjectClassInfo info = clazz.getAnnotation(InjectClassInfo.class);
                                if (info == null) return;
                                String anchorClassName = info.anchorClass();
                                String injectClassName = info.inject().getName();
                                ClassLoader targetLoader = InjectionUtil.findClassAcrossClassLoaders(anchorClassName).getClassLoader();
                                byte[] bytecode = InjectionUtil.getClassBytes(info.inject());
                                InjectionUtil.unsafeInjectClass(targetLoader, injectClassName, bytecode);
                                System.out.println("Injected " + injectClassName + " into " + anchorClassName);
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    });
        }
    }
}
