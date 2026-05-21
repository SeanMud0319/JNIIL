package top.nontage.jniil.injector.functional;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class TypeConverter {

    public static void loadLocalVar(InsnList list, MethodNode method, int slot) {
        Type varType = getLocalVarType(method, slot);
        loadLocalVar(list, slot, varType);
    }

    public static void loadLocalVar(InsnList list, int slot, Type varType) {
        if (varType == null) {
            list.add(new VarInsnNode(Opcodes.ALOAD, slot));
            return;
        }

        switch (varType.getSort()) {
            case Type.BOOLEAN:
                list.add(new VarInsnNode(Opcodes.ILOAD, slot));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
                break;
            case Type.BYTE:
                list.add(new VarInsnNode(Opcodes.ILOAD, slot));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                break;
            case Type.CHAR:
                list.add(new VarInsnNode(Opcodes.ILOAD, slot));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                break;
            case Type.SHORT:
                list.add(new VarInsnNode(Opcodes.ILOAD, slot));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                break;
            case Type.INT:
                list.add(new VarInsnNode(Opcodes.ILOAD, slot));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                break;
            case Type.LONG:
                list.add(new VarInsnNode(Opcodes.LLOAD, slot));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
                break;
            case Type.FLOAT:
                list.add(new VarInsnNode(Opcodes.FLOAD, slot));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                break;
            case Type.DOUBLE:
                list.add(new VarInsnNode(Opcodes.DLOAD, slot));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                break;
            default:
                list.add(new VarInsnNode(Opcodes.ALOAD, slot));
                break;
        }
    }

    public static Type getLocalVarType(MethodNode method, int slot) {
        if (method.localVariables != null) {
            for (LocalVariableNode lv : method.localVariables) {
                if (lv.index == slot) {
                    return Type.getType(lv.desc);
                }
            }
        }

        Type[] argTypes = Type.getArgumentTypes(method.desc);
        int paramIndex = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;

        for (Type argType : argTypes) {
            if (paramIndex == slot) return argType;
            paramIndex += argType.getSize();
        }

        return null;
    }

    public static void castAndReturn(InsnList list, String methodDesc) {
        Type returnType = Type.getReturnType(methodDesc);
        if (returnType.getSort() == Type.VOID) {
            list.add(new InsnNode(Opcodes.RETURN));
            return;
        }
        LabelNode notNullLabel = new LabelNode();
        if (returnType.getSort() != Type.OBJECT && returnType.getSort() != Type.ARRAY) {
            list.add(new InsnNode(Opcodes.DUP));
            list.add(new JumpInsnNode(Opcodes.IFNONNULL, notNullLabel));

            String exceptionClass = "java/lang/IllegalStateException";
            list.add(new TypeInsnNode(Opcodes.NEW, exceptionClass));
            list.add(new InsnNode(Opcodes.DUP));

            String friendlyTypeName = returnType.getClassName();

            String errorMsg = "[JNIIL] Execution cancelled on method '" + methodDesc
                    + "', but no return value was provided. Since this method returns a primitive type, "
                    + "you MUST explicitly provide a value of type '" + friendlyTypeName
                    + "' using MethodInfo.setReturnValue() in your hook method.";

            list.add(new LdcInsnNode(errorMsg));

            list.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    exceptionClass,
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false
            ));
            list.add(new InsnNode(Opcodes.ATHROW));
        }
        list.add(notNullLabel);
        switch (returnType.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                list.add(new InsnNode(Opcodes.IRETURN));
                break;
            case Type.LONG:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false));
                list.add(new InsnNode(Opcodes.LRETURN));
                break;
            case Type.FLOAT:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "floatValue", "()F", false));
                list.add(new InsnNode(Opcodes.FRETURN));
                break;
            case Type.DOUBLE:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false));
                list.add(new InsnNode(Opcodes.DRETURN));
                break;
            default:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
                list.add(new InsnNode(Opcodes.ARETURN));
                break;
        }
    }

    public static InsnList loadArgsArray(MethodNode method) {
        InsnList list = new InsnList();
        Type[] argTypes = Type.getArgumentTypes(method.desc);
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        int localIndex = isStatic ? 0 : 1;

        list.add(new IntInsnNode(Opcodes.BIPUSH, argTypes.length));
        list.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));

        for (int i = 0; i < argTypes.length; i++) {
            list.add(new InsnNode(Opcodes.DUP));
            list.add(new IntInsnNode(Opcodes.BIPUSH, i));
            loadLocalVar(list, method, localIndex);
            list.add(new InsnNode(Opcodes.AASTORE));
            localIndex += argTypes[i].getSize();
        }

        return list;
    }

    public static int getReturnOpcode(String methodDesc) {
        Type returnType = Type.getReturnType(methodDesc);
        switch (returnType.getSort()) {
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