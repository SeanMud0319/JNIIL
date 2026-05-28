package top.nontage.jniil.shadow.internal.scan;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import top.nontage.jniil.annotations.Mutable;
import top.nontage.jniil.annotations.Shadow;
import top.nontage.jniil.annotations.ShadowOf;
import top.nontage.jniil.annotations.ViewOnly;
import top.nontage.jniil.shadow.internal.metadata.FieldKey;
import top.nontage.jniil.shadow.internal.metadata.MethodKey;
import top.nontage.jniil.shadow.internal.metadata.ShadowContext;
import top.nontage.jniil.shadow.internal.metadata.ShadowFieldInfo;
import top.nontage.jniil.shadow.internal.metadata.ShadowMethodInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ShadowMetadataCollector {

    private final ShadowContext context;

    public ShadowMetadataCollector(ShadowContext context) {
        this.context = context;
    }

    public void collect(ClassNode node) {
        String owner = node.name;
        String classTargetOwner = resolveShadowOf(node);
        ClassLoader classLoader = this.getClass().getClassLoader();

        for (FieldNode field : node.fields) {
            AnnotationNode shadow = findAnnotation(field.visibleAnnotations, Shadow.class);
            if (shadow != null) {
                String targetOwner = resolveShadowTarget(shadow, classTargetOwner, "field", field.name, node.name);
                String targetName = resolveTargetName(shadow, field.name);
                boolean isMutable = findAnnotation(field.visibleAnnotations, Mutable.class) != null;
                boolean isViewOnly = findAnnotation(field.visibleAnnotations, ViewOnly.class) != null;

                if (isMutable && isViewOnly) {
                    throw new IllegalStateException("Field '" + field.name + "' in shadow class '" + owner.replace('/', '.') +
                            "' cannot be marked with both @Mutable and @ViewOnly.");
                }

                validateFieldExists(targetOwner, targetName, field.desc, classLoader, owner);

                context.shadowFields.put(
                        new FieldKey(owner, field.name, field.desc),
                        new ShadowFieldInfo(targetOwner, targetName, field.desc, isMutable, isViewOnly)
                );
            }
        }

        for (MethodNode method : node.methods) {
            AnnotationNode shadow = findAnnotation(method.visibleAnnotations, Shadow.class);
            if (shadow != null) {
                String targetOwner = resolveShadowTarget(shadow, classTargetOwner, "method", method.name + method.desc, node.name);
                String targetName = resolveTargetName(shadow, method.name);

                validateMethodExists(targetOwner, targetName, method.desc, classLoader, owner);

                context.shadowMethods.put(
                        new MethodKey(owner, method.name, method.desc),
                        new ShadowMethodInfo(targetOwner, targetName, method.desc)
                );
            }
        }
    }

    private String resolveShadowOf(ClassNode node) {
        AnnotationNode an = findAnnotation(node.visibleAnnotations, ShadowOf.class);
        if (an == null) return null;

        String owner = extractAnnotationString(an, "value", "className");
        if (owner == null) {
            throw new IllegalStateException(
                    "@ShadowOf on class " + node.name + " must specify value or className"
            );
        }
        return owner;
    }

    private String resolveShadowTarget(
            AnnotationNode shadow,
            String classTarget,
            String kind,
            String member,
            String owner
    ) {
        String target = extractAnnotationString(shadow, "value", "className");

        if (target != null) return target;
        if (classTarget != null) return classTarget;

        throw new IllegalStateException(
                "@" + Shadow.class.getSimpleName() + " on " + kind + " " + member +
                        " in class " + owner +
                        " has no target (no @Shadow value/className and no @ShadowOf on class)"
        );
    }

    private String resolveTargetName(AnnotationNode shadow, String defaultName) {
        String targetName = extractAnnotationString(shadow, "targetName");
        return (targetName != null && !targetName.isEmpty()) ? targetName : defaultName;
    }

    private AnnotationNode findAnnotation(List<AnnotationNode> anns, Class<?> type) {
        if (anns == null) return null;
        String desc = Type.getDescriptor(type);
        for (AnnotationNode an : anns) {
            if (an.desc.equals(desc)) return an;
        }
        return null;
    }

    private String extractAnnotationString(AnnotationNode an, String... keys) {
        if (an.values == null) return null;

        for (int i = 0; i < an.values.size(); i += 2) {
            String key = (String) an.values.get(i);
            Object value = an.values.get(i + 1);

            for (String targetKey : keys) {
                if (key.equals(targetKey)) {
                    if (value instanceof Type) {
                        Type t = (Type) value;
                        if (!t.getClassName().equals(Object.class.getName())) {
                            return t.getInternalName();
                        }
                    } else if (value instanceof String) {
                        String s = (String) value;
                        if (!s.isEmpty()) {
                            return s.replace('.', '/');
                        }
                    }
                }
            }
        }
        return null;
    }

    private void validateFieldExists(String targetOwner, String fieldName, String fieldDesc, ClassLoader loader, String shadowOwner) {
        try {
            Class<?> targetClass = Class.forName(targetOwner.replace('/', '.'), false, loader);
            Type expectedType = Type.getType(fieldDesc);

            Field field = targetClass.getDeclaredField(fieldName);
            Type actualType = Type.getType(field.getType());

            if (!actualType.equals(expectedType)) {
                throw new NoSuchFieldError("Field type mismatch for '" + fieldName + "'. Expected " + expectedType.getClassName() + " but found " + actualType.getClassName());
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Shadow target class not found: " + targetOwner.replace('/', '.') + " for shadow field " + fieldName + " in " + shadowOwner, e);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Shadow target field not found: " + fieldName + " in class " + targetOwner.replace('/', '.') + " for shadow in " + shadowOwner, e);
        }
    }

    private void validateMethodExists(String targetOwner, String methodName, String methodDesc, ClassLoader loader, String shadowOwner) {
        try {
            Class<?> targetClass = Class.forName(targetOwner.replace('/', '.'), false, loader);
            Type[] expectedArgTypes = Type.getArgumentTypes(methodDesc);

            Class<?>[] argClasses = Arrays.stream(expectedArgTypes)
                    .map(t -> {
                        try {
                            return classFromType(t, loader);
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toArray(Class<?>[]::new);

            Method method = targetClass.getDeclaredMethod(methodName, argClasses);
            Type expectedReturnType = Type.getReturnType(methodDesc);
            Type actualReturnType = Type.getType(method.getReturnType());

            if (!actualReturnType.equals(expectedReturnType)) {
                throw new NoSuchMethodError("Method return type mismatch for '" + methodName + "'. Expected " + expectedReturnType.getClassName() + " but found " + actualReturnType.getClassName());
            }

        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Shadow target class not found: " + targetOwner.replace('/', '.') + " for shadow method " + methodName + " in " + shadowOwner, e);
        } catch (NoSuchMethodException e) {
            String args = Arrays.stream(Type.getArgumentTypes(methodDesc)).map(Type::getClassName).collect(Collectors.joining(", "));
            String message = "Shadow target method not found: " + methodName + "(" + args + ") in class " + targetOwner.replace('/', '.') + " for shadow in " + shadowOwner;

            try {
                Class<?> targetClass = Class.forName(targetOwner.replace('/', '.'), false, loader);
                String similarMethods = Arrays.stream(targetClass.getDeclaredMethods())
                        .filter(m -> m.getName().equals(methodName))
                        .map(m -> "    " + m.getName() + "(" + Arrays.stream(m.getParameterTypes()).map(Class::getName).collect(Collectors.joining(", ")) + ")")
                        .collect(Collectors.joining("\n"));
                if (!similarMethods.isEmpty()) {
                    message += ".\nDid you mean one of these?\n" + similarMethods;
                }
            } catch (Exception ignored) {
            }
            throw new IllegalStateException(message, e);
        }
    }

    private Class<?> classFromType(Type type, ClassLoader classLoader) throws ClassNotFoundException {
        switch (type.getSort()) {
            case Type.BOOLEAN: return boolean.class;
            case Type.CHAR:    return char.class;
            case Type.BYTE:    return byte.class;
            case Type.SHORT:   return short.class;
            case Type.INT:     return int.class;
            case Type.FLOAT:   return float.class;
            case Type.LONG:    return long.class;
            case Type.DOUBLE:  return double.class;
            case Type.VOID:    return void.class;
            default:
                return Class.forName(type.getClassName(), false, classLoader);
        }
    }
}