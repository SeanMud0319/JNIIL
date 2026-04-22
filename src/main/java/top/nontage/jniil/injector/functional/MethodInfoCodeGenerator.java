package top.nontage.jniil.injector.functional;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.Map;

public class MethodInfoCodeGenerator {

    private final Method injectionMethod;
    private final MethodNode targetMethod;
    private final boolean isTargetStatic;
    private final String[] localsToCapture;

    public MethodInfoCodeGenerator(Method injectionMethod, MethodNode targetMethod,
                                   boolean isTargetStatic, String[] localsToCapture) {
        this.injectionMethod = injectionMethod;
        this.targetMethod = targetMethod;
        this.isTargetStatic = isTargetStatic;
        this.localsToCapture = localsToCapture;
    }

    public InsnList generate() {
        InsnList list = new InsnList();
        createMethodInfo(list);
        int infoVar = storeMethodInfo(list);
        captureLocals(list, infoVar);
        callInjectionMethod(list, infoVar);
        handleCancellation(list, infoVar);
        return list;
    }

    private void createMethodInfo(InsnList list) {
        String methodInfoClass = MethodInfo.class.getName().replace('.', '/');

        list.add(new TypeInsnNode(Opcodes.NEW, methodInfoClass));
        list.add(new InsnNode(Opcodes.DUP));

        if (isTargetStatic) {
            list.add(new InsnNode(Opcodes.ACONST_NULL));
        } else {
            list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }

        list.add(TypeConverter.loadArgsArray(targetMethod));

        list.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                methodInfoClass,
                "<init>",
                "(Ljava/lang/Object;[Ljava/lang/Object;)V",
                false
        ));
    }

    private int storeMethodInfo(InsnList list) {
        int infoVar = targetMethod.maxLocals;
        targetMethod.maxLocals += 1;
        list.add(new VarInsnNode(Opcodes.ASTORE, infoVar));
        return infoVar;
    }

    private void captureLocals(InsnList list, int infoVar) {
        if (localsToCapture.length == 0) return;

        Map<String, Integer> localVarSlots = getLocalVarSlots();
        for (String localName : localsToCapture) {
            Integer slot = localVarSlots.get(localName);
            if (slot != null) {
                list.add(new VarInsnNode(Opcodes.ALOAD, infoVar));
                list.add(new LdcInsnNode(localName));
                TypeConverter.loadLocalVar(list, targetMethod, slot);
                list.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        MethodInfo.class.getName().replace('.', '/'),
                        "captureLocal",
                        "(Ljava/lang/String;Ljava/lang/Object;)V",
                        false
                ));
            }
        }
    }

    private void callInjectionMethod(InsnList list, int infoVar) {
        String injectClassDesc = injectionMethod.getDeclaringClass().getName().replace('.', '/');
        String injectMethodName = injectionMethod.getName();
        String injectMethodDesc = getMethodDescriptor(injectionMethod);

        list.add(new VarInsnNode(Opcodes.ALOAD, infoVar));
        list.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                injectClassDesc,
                injectMethodName,
                injectMethodDesc,
                false
        ));
    }

    private void handleCancellation(InsnList list, int infoVar) {
        list.add(new VarInsnNode(Opcodes.ALOAD, infoVar));
        list.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                MethodInfo.class.getName().replace('.', '/'),
                "isCancelled",
                "()Z",
                false
        ));

        LabelNode notCancelled = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.IFEQ, notCancelled));

        int returnType = TypeConverter.getReturnOpcode(targetMethod.desc);
        if (returnType == Opcodes.RETURN) {
            list.add(new InsnNode(Opcodes.RETURN));
        } else {
            list.add(new VarInsnNode(Opcodes.ALOAD, infoVar));
            list.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    MethodInfo.class.getName().replace('.', '/'),
                    "getReturnValue",
                    "()Ljava/lang/Object;",
                    false
            ));
            TypeConverter.castAndReturn(list, targetMethod.desc);
        }

        list.add(notCancelled);
    }

    private Map<String, Integer> getLocalVarSlots() {
        Map<String, Integer> slots = new java.util.HashMap<>();
        if (targetMethod.localVariables != null) {
            for (LocalVariableNode lv : targetMethod.localVariables) {
                if (!lv.name.equals("this")) {
                    slots.put(lv.name, lv.index);
                }
            }
        }
        return slots;
    }

    private String getMethodDescriptor(Method method) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> param : method.getParameterTypes()) {
            sb.append(Type.getDescriptor(param));
        }
        sb.append(")V");
        return sb.toString();
    }
}