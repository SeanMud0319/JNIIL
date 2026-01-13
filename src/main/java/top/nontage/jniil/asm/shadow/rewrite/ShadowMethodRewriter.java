package top.nontage.jniil.asm.shadow.rewrite;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import top.nontage.jniil.asm.shadow.metadata.MethodKey;
import top.nontage.jniil.asm.shadow.metadata.ShadowContext;
import top.nontage.jniil.asm.shadow.metadata.ShadowMethodInfo;

public class ShadowMethodRewriter {

    private final ShadowContext context;

    public ShadowMethodRewriter(ShadowContext context) {
        this.context = context;
    }

    public void rewrite(ClassNode node) {
        String shadowOwner = node.name;

        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;

            MethodKey key = new MethodKey(shadowOwner, method.name, method.desc);
            ShadowMethodInfo info = context.shadowMethods.get(key);

            if (info == null) continue;

            Handle bootstrap = new Handle(
                    Opcodes.H_INVOKESTATIC,
                    "top/nontage/jniil/asm/shadow/ShadowBootstrap",
                    "bootstrap",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;" +
                            "Ljava/lang/String;" +
                            "Ljava/lang/invoke/MethodType;" +
                            "Ljava/lang/String;" +
                            "Ljava/lang/String;" +
                            "Ljava/lang/String;" +
                            "Ljava/lang/String;" +
                            "I" +
                            "I)Ljava/lang/invoke/CallSite;",
                    false
            );

            boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
            int opcode = isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;

            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                    method.name,
                    method.desc,
                    bootstrap,
                    shadowOwner,
                    info.targetOwner,
                    info.targetName,
                    info.desc,
                    opcode,
                    0
            );

            InsnList newInsns = new InsnList();
            Type[] argTypes = Type.getArgumentTypes(method.desc);
            int varIndex = 0;
            if (!isStatic) {
                newInsns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                varIndex++;
            }
            for (Type argType : argTypes) {
                newInsns.add(new VarInsnNode(argType.getOpcode(Opcodes.ILOAD), varIndex));
                varIndex += argType.getSize();
            }

            newInsns.add(indy);
            newInsns.add(new InsnNode(getReturnOpcode(Type.getReturnType(method.desc))));

            method.instructions.clear();
            method.instructions.add(newInsns);
        }
    }

    private int getReturnOpcode(Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                return Opcodes.RETURN;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                return Opcodes.IRETURN;
            case Type.LONG:
                return Opcodes.LRETURN;
            case Type.FLOAT:
                return Opcodes.FRETURN;
            case Type.DOUBLE:
                return Opcodes.DRETURN;
            default:
                return Opcodes.ARETURN;
        }
    }
}