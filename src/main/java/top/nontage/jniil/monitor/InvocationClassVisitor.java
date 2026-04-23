package top.nontage.jniil.monitor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;

public class InvocationClassVisitor extends ClassVisitor {
    private final String targetName;
    private final String targetDesc;
    private final String methodKey;

    public InvocationClassVisitor(ClassVisitor cv, Executable executable, String methodKey) {
        super(Opcodes.ASM9, cv);
        this.targetName = (executable instanceof Constructor) ? "<init>" : executable.getName();
        this.targetDesc = (executable instanceof Constructor)
                ? Type.getConstructorDescriptor((Constructor<?>) executable)
                : Type.getMethodDescriptor((java.lang.reflect.Method) executable);
        this.methodKey = methodKey;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals(targetName) && desc.equals(targetDesc)) {
            return new InvocationMethodAdapter(api, mv, access, name, desc, methodKey);
        }
        return mv;
    }
}