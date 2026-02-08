package top.nontage.jniil.injector.insn;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class InsnContext {

    private final MethodNode method;

    private final AbstractInsnNode anchor;

    public InsnContext(MethodNode method, AbstractInsnNode anchor) {
        this.method = method;
        this.anchor = anchor;
    }

    public AbstractInsnNode anchor() {
        return anchor;
    }

    public AbstractInsnNode previous() {
        return anchor.getPrevious();
    }

    public AbstractInsnNode next() {
        return anchor.getNext();
    }

    public int opcode() {
        return anchor.getOpcode();
    }

    public MethodNode method() {
        return method;
    }

    public boolean isStatic() {
        return (method.access & Opcodes.ACC_STATIC) != 0;
    }

    public boolean isConstructor() {
        return "<init>".equals(method.name);
    }
}
