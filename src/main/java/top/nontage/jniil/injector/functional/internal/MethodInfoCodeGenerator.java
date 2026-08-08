package top.nontage.jniil.injector.functional.internal;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import top.nontage.jniil.injector.functional.MethodInfo;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MethodInfoCodeGenerator {

    private final Method injectionMethod;
    private final MethodNode targetMethod;
    private final boolean isTargetStatic;
    private final String[] localsToCapture;
    private final boolean isOverwrite;

    public MethodInfoCodeGenerator(Method injectionMethod, MethodNode targetMethod,
                                   boolean isTargetStatic, String[] localsToCapture, boolean isOverwrite) {
        this.injectionMethod = injectionMethod;
        this.targetMethod = targetMethod;
        this.isTargetStatic = isTargetStatic;
        this.localsToCapture = localsToCapture;
        this.isOverwrite = isOverwrite;
    }

    public InsnList generate() {
        InsnList list = new InsnList();
        createMethodInfo(list);
        int infoVar = storeMethodInfo(list);
        captureLocals(list, infoVar);
        callInjectionMethod(list, infoVar);
        writeBackArguments(list, infoVar);
        writeBackLocals(list, infoVar);
        handleCancellation(list, infoVar);
        if (isOverwrite) {
            appendDefaultReturn(list);
        }
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
        for (String capture : localsToCapture) {
            ParsedCapture parsed = parseCapture(capture);
            if (parsed.slot >= 0) {
                int slot = parsed.slot;
                Type varType = parsed.explicitType;
                if (varType == null) {
                    varType = inferTypeFromSlot(slot);
                }
                list.add(new VarInsnNode(Opcodes.ALOAD, infoVar));
                list.add(new LdcInsnNode(parsed.original));

                if (varType == null) {
                    list.add(new VarInsnNode(Opcodes.ALOAD, slot));
                } else {
                    switch (varType.getSort()) {
                        case Type.BOOLEAN:
                        case Type.BYTE:
                        case Type.CHAR:
                        case Type.SHORT:
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

                list.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        MethodInfo.class.getName().replace('.', '/'),
                        "captureLocal",
                        "(Ljava/lang/String;Ljava/lang/Object;)V",
                        false
                ));
            } else {
                Map<String, Integer> localVarSlots = getLocalVarSlots();
                Integer slot = localVarSlots.get(parsed.original);
                if (slot != null) {
                    list.add(new VarInsnNode(Opcodes.ALOAD, infoVar));
                    list.add(new LdcInsnNode(parsed.original));
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
    }

    private Type inferTypeFromSlot(int slot) {
        if (targetMethod.localVariables != null) {
            for (LocalVariableNode lv : targetMethod.localVariables) {
                if (lv.index == slot && !lv.name.equals("this")) {
                    return Type.getType(lv.desc);
                }
            }
        }
        Type[] argTypes = Type.getArgumentTypes(targetMethod.desc);
        int paramIndex = isTargetStatic ? 0 : 1;
        for (Type argType : argTypes) {
            if (paramIndex == slot) {
                return argType;
            }
            paramIndex += argType.getSize();
        }

        for (AbstractInsnNode insn : targetMethod.instructions.toArray()) {
            if (insn instanceof VarInsnNode) {
                VarInsnNode vi = (VarInsnNode) insn;
                if (vi.var == slot) {
                    int opcode = vi.getOpcode();
                    switch (opcode) {
                        case Opcodes.ILOAD:
                        case Opcodes.ISTORE:
                            return Type.INT_TYPE;
                        case Opcodes.LLOAD:
                        case Opcodes.LSTORE:
                            return Type.LONG_TYPE;
                        case Opcodes.FLOAD:
                        case Opcodes.FSTORE:
                            return Type.FLOAT_TYPE;
                        case Opcodes.DLOAD:
                        case Opcodes.DSTORE:
                            return Type.DOUBLE_TYPE;
                        default:
                            return Type.getType(Object.class);
                    }
                }
            }
        }

        return null;
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

    private void writeBackArguments(InsnList list, int infoVar) {
        Type[] argTypes = Type.getArgumentTypes(targetMethod.desc);
        boolean[] isFinalFlags = getParameterFinalFlags();
        int localIndex = isTargetStatic ? 0 : 1;

        for (int i = 0; i < argTypes.length; i++) {
            if (isFinalFlags[i]) {
                localIndex += argTypes[i].getSize();
                continue;
            }
            list.add(new VarInsnNode(Opcodes.ALOAD, infoVar));
            list.add(new IntInsnNode(Opcodes.BIPUSH, i));
            list.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    MethodInfo.class.getName().replace('.', '/'),
                    "getArgument",
                    "(I)Ljava/lang/Object;",
                    false
            ));
            unboxAndStoreToLocal(list, argTypes[i], localIndex);
            localIndex += argTypes[i].getSize();
        }
    }

    private void writeBackLocals(InsnList list, int infoVar) {
        for (String capture : localsToCapture) {
            ParsedCapture parsed = parseCapture(capture);

            if (parsed.slot >= 0) {
                int slot = parsed.slot;
                Type varType = parsed.explicitType;
                if (varType == null) {
                    varType = inferTypeFromSlot(slot);
                }

                list.add(new VarInsnNode(Opcodes.ALOAD, infoVar));
                list.add(new LdcInsnNode(parsed.original));
                list.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        MethodInfo.class.getName().replace('.', '/'),
                        "getLocal",
                        "(Ljava/lang/String;)Ljava/lang/Object;",
                        false
                ));

                if (varType == null) {
                    throw new IllegalStateException(
                            "Failed to infer type for local variable capture: " + capture + " (slot " + slot + ")\n" +
                                    "Method: " + targetMethod.name + targetMethod.desc + "\n" +
                                    "Solution: Use explicit type in @Capture (e.g., @Capture({\"=4:F\"}) to specify float)"
                    );
                }

                unboxAndStoreToLocal(list, varType, slot);
            } else {
                Map<String, Integer> localVarSlots = getLocalVarSlots();
                Map<String, Type> localVarTypes = getLocalVarTypes();
                Integer slot = localVarSlots.get(parsed.original);
                Type varType = localVarTypes.get(parsed.original);
                if (slot == null || varType == null) continue;

                list.add(new VarInsnNode(Opcodes.ALOAD, infoVar));
                list.add(new LdcInsnNode(parsed.original));
                list.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        MethodInfo.class.getName().replace('.', '/'),
                        "getLocal",
                        "(Ljava/lang/String;)Ljava/lang/Object;",
                        false
                ));
                unboxAndStoreToLocal(list, varType, slot);
            }
        }
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

    private void unboxAndStoreToLocal(InsnList list, Type type, int slot) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
                list.add(new VarInsnNode(Opcodes.ISTORE, slot));
                break;
            case Type.BYTE:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Byte"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false));
                list.add(new VarInsnNode(Opcodes.ISTORE, slot));
                break;
            case Type.CHAR:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Character"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false));
                list.add(new VarInsnNode(Opcodes.ISTORE, slot));
                break;
            case Type.SHORT:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Short"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false));
                list.add(new VarInsnNode(Opcodes.ISTORE, slot));
                break;
            case Type.INT:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false));
                list.add(new VarInsnNode(Opcodes.ISTORE, slot));
                break;
            case Type.LONG:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false));
                list.add(new VarInsnNode(Opcodes.LSTORE, slot));
                break;
            case Type.FLOAT:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "floatValue", "()F", false));
                list.add(new VarInsnNode(Opcodes.FSTORE, slot));
                break;
            case Type.DOUBLE:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false));
                list.add(new VarInsnNode(Opcodes.DSTORE, slot));
                break;
            case Type.ARRAY:
            case Type.OBJECT:
            default:
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
                list.add(new VarInsnNode(Opcodes.ASTORE, slot));
                break;
        }
    }

    private void appendDefaultReturn(InsnList list) {
        int returnOpcode = TypeConverter.getReturnOpcode(targetMethod.desc);
        if (returnOpcode == Opcodes.RETURN) {
            list.add(new InsnNode(Opcodes.RETURN));
        } else {
            Type returnType = Type.getReturnType(targetMethod.desc);
            switch (returnType.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.CHAR:
                case Type.SHORT:
                case Type.INT:
                    list.add(new InsnNode(Opcodes.ICONST_0));
                    break;
                case Type.LONG:
                    list.add(new InsnNode(Opcodes.LCONST_0));
                    break;
                case Type.FLOAT:
                    list.add(new InsnNode(Opcodes.FCONST_0));
                    break;
                case Type.DOUBLE:
                    list.add(new InsnNode(Opcodes.DCONST_0));
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                default:
                    list.add(new InsnNode(Opcodes.ACONST_NULL));
                    break;
            }
            list.add(new InsnNode(returnOpcode));
        }
    }

    private Map<String, Type> getLocalVarTypes() {
        Map<String, Type> types = new HashMap<>();
        if (targetMethod.localVariables != null) {
            for (LocalVariableNode lv : targetMethod.localVariables) {
                if (!lv.name.equals("this")) {
                    types.put(lv.name, Type.getType(lv.desc));
                }
            }
        }
        return types;
    }

    private boolean[] getParameterFinalFlags() {
        Type[] argTypes = Type.getArgumentTypes(targetMethod.desc);
        boolean[] isFinal = new boolean[argTypes.length];

        if (targetMethod.parameters != null) {
            for (int i = 0; i < targetMethod.parameters.size() && i < argTypes.length; i++) {
                ParameterNode param = targetMethod.parameters.get(i);
                isFinal[i] = (param.access & Opcodes.ACC_FINAL) != 0;
            }
        }

        return isFinal;
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

    private ParsedCapture parseCapture(String capture) {
        if (!capture.startsWith("=")) {
            return new ParsedCapture(capture, null, -1);
        }

        String rest = capture.substring(1);
        int colonIdx = rest.indexOf(':');
        if (colonIdx > 0) {
            String slotStr = rest.substring(0, colonIdx);
            String typeCode = rest.substring(colonIdx + 1);
            try {
                int slot = Integer.parseInt(slotStr);
                Type type = parseTypeCode(typeCode);
                return new ParsedCapture(capture, type, slot);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid slot number in @Capture: " + capture);
            }
        } else {
            try {
                int slot = Integer.parseInt(rest);
                return new ParsedCapture(capture, null, slot);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid slot index in @Capture: " + capture);
            }
        }
    }

    private Type parseTypeCode(String typeCode) {
        typeCode = typeCode.toUpperCase();
        switch (typeCode) {
            case "Z":
                return Type.BOOLEAN_TYPE;
            case "B":
                return Type.BYTE_TYPE;
            case "C":
                return Type.CHAR_TYPE;
            case "S":
                return Type.SHORT_TYPE;
            case "I":
                return Type.INT_TYPE;
            case "J":
                return Type.LONG_TYPE;
            case "F":
                return Type.FLOAT_TYPE;
            case "D":
                return Type.DOUBLE_TYPE;
            default:
                if (typeCode.startsWith("L") && typeCode.endsWith(";")) {
                    return Type.getType(typeCode);
                }
                if (typeCode.contains(".")) {
                    return Type.getType("L" + typeCode.replace('.', '/') + ";");
                }
                throw new IllegalArgumentException("Unknown type code: " + typeCode);
        }
    }

    private static class ParsedCapture {
        final String original;
        final Type explicitType;
        final int slot;

        ParsedCapture(String original, Type explicitType, int slot) {
            this.original = original;
            this.explicitType = explicitType;
            this.slot = slot;
        }
    }
}