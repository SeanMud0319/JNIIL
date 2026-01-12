package top.nontage.jniil.asm.shadow.scan;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import top.nontage.jniil.annotations.Shadow;
import top.nontage.jniil.annotations.ShadowOf;
import top.nontage.jniil.asm.shadow.metadata.*;

import java.util.List;

public class ShadowMetadataCollector {

    private final ShadowContext context;

    public ShadowMetadataCollector(ShadowContext context) {
        this.context = context;
    }

    public void collect(ClassNode node) {
        String owner = node.name;

        String classTargetOwner = resolveShadowOf(node);

        for (FieldNode field : node.fields) {
            AnnotationNode shadow = findAnnotation(field.visibleAnnotations, Shadow.class);
            if (shadow != null) {
                String targetOwner = resolveShadowTarget(
                        shadow,
                        classTargetOwner,
                        "field",
                        field.name,
                        node.name
                );

                context.shadowFields.put(
                        new FieldKey(owner, field.name, field.desc),
                        new ShadowFieldInfo(targetOwner, field.name, field.desc)
                );
            }
        }

        for (MethodNode method : node.methods) {
            AnnotationNode shadow = findAnnotation(method.visibleAnnotations, Shadow.class);
            if (shadow != null) {
                String targetOwner = resolveShadowTarget(
                        shadow,
                        classTargetOwner,
                        "method",
                        method.name + method.desc,
                        node.name
                );

                context.shadowMethods.put(
                        new MethodKey(owner, method.name, method.desc),
                        new ShadowMethodInfo(targetOwner, method.name, method.desc)
                );
            }
        }
    }


    private String resolveShadowOf(ClassNode node) {
        AnnotationNode an = findAnnotation(node.visibleAnnotations, ShadowOf.class);
        if (an == null) return null;

        String owner = extractTargetOwner(an);
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
        String target = extractTargetOwner(shadow);

        if (target != null) return target;
        if (classTarget != null) return classTarget;

        throw new IllegalStateException(
                "@" + Shadow.class.getSimpleName() + " on " + kind + " " + member +
                        " in class " + owner +
                        " has no target (no @Shadow value/className and no @ShadowOf on class)"
        );
    }


    private AnnotationNode findAnnotation(List<AnnotationNode> anns, Class<?> type) {
        if (anns == null) return null;
        String desc = Type.getDescriptor(type);
        for (AnnotationNode an : anns) {
            if (an.desc.equals(desc)) return an;
        }
        return null;
    }

    private String extractTargetOwner(AnnotationNode an) {
        if (an.values == null) return null;

        for (int i = 0; i < an.values.size(); i += 2) {
            String key = (String) an.values.get(i);
            Object value = an.values.get(i + 1);

            if (key.equals("value") && value instanceof Type) {
                Type t = (Type) value;
                if (!t.getClassName().equals(Object.class.getName())) {
                    return t.getInternalName();
                }
            }

            if (key.equals("className") && value instanceof String) {
                String s = (String) value;
                if (!s.isEmpty()) {
                    return s.replace('.', '/');
                }
            }
        }
        return null;
    }
}
