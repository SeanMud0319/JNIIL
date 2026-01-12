package top.nontage.jniil.asm.shadow.rewrite;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import top.nontage.jniil.asm.shadow.metadata.*;

public class ShadowMethodRewriter {

    private final ShadowContext context;

    public ShadowMethodRewriter(ShadowContext context) {
        this.context = context;
    }

    public void rewrite(ClassNode node) {
        String shadowOwner = node.name;

        for (MethodNode method : node.methods) {
            MethodKey key = new MethodKey(node.name, method.name, method.desc);
            ShadowMethodInfo info = context.shadowMethods.get(key);
            if (info == null) continue;

            boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;

            method.instructions.clear();

            int index = 0;
            if (!isStatic) {
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                index = 1;
            }

            Type[] args = Type.getArgumentTypes(method.desc);
            for (Type t : args) {
                method.instructions.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), index));
                index += t.getSize();
            }

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
                            "I)Ljava/lang/invoke/CallSite;",
                    false
            );

            String indyDesc = method.desc;
            if (!isStatic) {
                indyDesc = "(" + Type.getObjectType(shadowOwner) + method.desc.substring(1);
            }

            int opcode = isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;

            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                    method.name,
                    indyDesc,
                    bootstrap,
                    shadowOwner,
                    info.targetOwner,
                    info.targetName,
                    info.desc,
                    opcode
            );

            method.instructions.add(indy);
            method.instructions.add(new InsnNode(getReturnOpcode(Type.getReturnType(info.desc))));
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