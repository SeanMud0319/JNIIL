package top.nontage.jniil.monitor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;

public class InvocationClassVisitor extends ClassVisitor {
    private final String targetMethodName;
    private final String targetMethodDesc;
    private final String methodKey;

    public InvocationClassVisitor(ClassVisitor cv, Method method, String methodKey) {
        super(Opcodes.ASM9, cv);
        this.targetMethodName = method.getName();
        this.targetMethodDesc = Type.getMethodDescriptor(method);
        this.methodKey = methodKey;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals(targetMethodName) && desc.equals(targetMethodDesc)) {
            return new InvocationMethodAdapter(api, mv, access, name, desc, methodKey);
        }
        return mv;
    }
}
