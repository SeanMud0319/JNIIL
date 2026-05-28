package top.nontage.jniil.monitor.internal;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

public class InvocationMethodAdapter extends AdviceAdapter {
    private final String methodKey;
    private final Type returnType;
    private final boolean isConstructor;

    protected InvocationMethodAdapter(int api, MethodVisitor mv, int access, String name, String desc, String methodKey) {
        super(api, mv, access, name, desc);
        this.methodKey = methodKey;
        this.returnType = Type.getReturnType(desc);
        this.isConstructor = "<init>".equals(name);
    }

    @Override
    protected void onMethodEnter() {
        push(methodKey);

        if ((methodAccess & ACC_STATIC) != 0) {
            push((String) null);
        } else {
            loadThis();
        }

        loadArgArray();

        invokeStatic(Type.getType("Ltop/nontage/jniil/monitor/InvocationMonitor;"),
                new Method("dispatch",
                        "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)Ltop/nontage/jniil/monitor/InvocationControl;"));

        dup();
        invokeVirtual(Type.getType("Ltop/nontage/jniil/monitor/InvocationControl;"),
                new Method("isCancelled", "()Z"));

        Label notCancelled = new Label();
        ifZCmp(EQ, notCancelled);

        if (isConstructor) {
            pop();
            returnValue();
        } else {
            dup();
            invokeVirtual(Type.getType("Ltop/nontage/jniil/monitor/InvocationControl;"),
                    new Method("isReturnValueSet", "()Z"));

            Label noReturnValue = new Label();
            ifZCmp(EQ, noReturnValue);

            invokeVirtual(Type.getType("Ltop/nontage/jniil/monitor/InvocationControl;"),
                    new Method("getOverrideReturnValue", "()Ljava/lang/Object;"));

            unbox(returnType);
            returnValue();

            mark(noReturnValue);
            if (returnType.getSort() == Type.VOID) {
                pop();
                returnValue();
            } else {
                pop();
                pushDefaultValue(returnType);
                returnValue();
            }
        }

        mark(notCancelled);
        pop();
    }

    private void pushDefaultValue(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN: case Type.CHAR: case Type.BYTE: case Type.SHORT: case Type.INT:
                push(0); break;
            case Type.FLOAT: push(0f); break;
            case Type.LONG: push(0L); break;
            case Type.DOUBLE: push(0d); break;
            default: push((String)null);
        }
    }
}