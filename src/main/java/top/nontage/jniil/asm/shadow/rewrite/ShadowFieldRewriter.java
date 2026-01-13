package top.nontage.jniil.asm.shadow.rewrite;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import top.nontage.jniil.asm.shadow.metadata.FieldKey;
import top.nontage.jniil.asm.shadow.metadata.ShadowContext;
import top.nontage.jniil.asm.shadow.metadata.ShadowFieldInfo;

public class ShadowFieldRewriter {

    private final ShadowContext context;

    public ShadowFieldRewriter(ShadowContext context) {
        this.context = context;
    }

    public void rewrite(ClassNode node) {
        String shadowOwner = node.name;

        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;

            InsnList insns = method.instructions;
            for (AbstractInsnNode insn : insns.toArray()) {
                if (!(insn instanceof FieldInsnNode)) continue;

                FieldInsnNode fi = (FieldInsnNode) insn;

                if (!fi.owner.equals(shadowOwner)) continue;

                FieldKey key = new FieldKey(fi.owner, fi.name, fi.desc);
                ShadowFieldInfo info = context.shadowFields.get(key);
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

                boolean isStatic = fi.getOpcode() == Opcodes.GETSTATIC || fi.getOpcode() == Opcodes.PUTSTATIC;
                boolean isGet = fi.getOpcode() == Opcodes.GETFIELD || fi.getOpcode() == Opcodes.GETSTATIC;

                String indyName = (isGet ? "get$" : "set$") + fi.name;
                String indyDesc;
                String shadowOwnerDesc = Type.getObjectType(shadowOwner).getDescriptor();

                if (isStatic) {
                    indyDesc = isGet ? "()" + info.desc : "(" + info.desc + ")V";
                } else {
                    indyDesc = isGet ? "(" + shadowOwnerDesc + ")" + info.desc : "(" + shadowOwnerDesc + info.desc + ")V";
                }

                InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                        indyName,
                        indyDesc,
                        bootstrap,
                        shadowOwner,
                        info.targetOwner,
                        info.targetName,
                        info.desc,
                        fi.getOpcode(),
                        info.isMutable ? 1 : 0
                );

                insns.set(fi, indy);
            }
        }
    }
}