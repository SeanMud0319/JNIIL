package top.nontage.jniil.shadow.internal.rewrite;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import top.nontage.jniil.shadow.internal.metadata.MethodKey;
import top.nontage.jniil.shadow.internal.metadata.ShadowContext;
import top.nontage.jniil.shadow.internal.metadata.ShadowMethodInfo;

public class ShadowMethodRewriter {

    private final ShadowContext context;

    public ShadowMethodRewriter(ShadowContext context) {
        this.context = context;
    }

    public void rewrite(ClassNode node) {
        String shadowOwner = node.name;

        for (MethodNode method : node.methods) {
            MethodKey key = new MethodKey(shadowOwner, method.name, method.desc);
            ShadowMethodInfo info = context.shadowMethods.get(key);

            if (info == null) continue;

            boolean isNative = (method.access & Opcodes.ACC_NATIVE) != 0;
            boolean isAbstract = (method.access & Opcodes.ACC_ABSTRACT) != 0;

            if (method.instructions == null) {
                method.instructions = new InsnList();
            } else {
                if (!isNative && !isAbstract && method.instructions.size() > 0) {
                    System.err.println("WARN: Method '" + method.name + "' in shadow class '" + shadowOwner.replace('/', '.') +
                            "' has both a @Shadow annotation and a method body. The existing body will be discarded.");
                }
                method.instructions.clear();
            }

            method.access &= ~Opcodes.ACC_NATIVE;
            method.access &= ~Opcodes.ACC_ABSTRACT;


            Handle bootstrap = new Handle(
                    Opcodes.H_INVOKESTATIC,
                    "top/nontage/jniil/shadow/ShadowBootstrap",
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

            String indyDesc;
            if (isStatic) {
                indyDesc = method.desc;
            } else {
                Type[] argTypes = Type.getArgumentTypes(method.desc);
                Type returnType = Type.getReturnType(method.desc);
                Type[] newArgTypes = new Type[argTypes.length + 1];
                newArgTypes[0] = Type.getObjectType(shadowOwner);
                System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);
                indyDesc = Type.getMethodDescriptor(returnType, newArgTypes);
            }

            InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                    method.name,
                    indyDesc,
                    bootstrap,
                    shadowOwner,
                    info.targetOwner,
                    info.targetName,
                    info.desc,
                    opcode,
                    0
            );

            InsnList newInsns = method.instructions;
            Type[] argTypes = Type.getArgumentTypes(method.desc);
            int varIndex = 0;

            if (!isStatic) {
                newInsns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                varIndex++;
            }

            for (Type argType : argTypes) {
                newInsns.add(new VarInsnNode(argType.getOpcode(Opcodes.ILOAD), varIndex));
                varIndex += argType.getSize();
            }

            newInsns.add(indy);
            newInsns.add(new InsnNode(getReturnOpcode(Type.getReturnType(method.desc))));
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