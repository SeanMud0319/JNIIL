package top.nontage.jniil.utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.*;

public class LocalVariableTableFiller {
    private final Instrumentation inst = JNIIL.getInstrumentation();

    public byte[] fillLocalVariableNames(final Class<?> targetClass, boolean debug) throws UnmodifiableClassException {
        final byte[][] transformedBytes = new byte[1][];

        ClassFileTransformer transformer = (loader, className, classBeingRedefined,
                                            protectionDomain, classfileBuffer) -> {
            if (!classBeingRedefined.equals(targetClass)) return null;

            try {
                byte[] bytes = InjectionCacheProxy.contains(className) ?
                        InjectionCacheProxy.get(className) :
                        InjectionUtil.getOriginalClassBytes(className);
                ClassReader cr = new ClassReader(bytes);
                ClassNode classNode = new ClassNode();
                cr.accept(classNode, ClassReader.EXPAND_FRAMES);

                Map<MethodNode, Type[]> methodParamTypes = new HashMap<>();
                for (MethodNode mn : classNode.methods) {
                    methodParamTypes.put(mn, Type.getArgumentTypes(mn.desc));
                }

                for (MethodNode mn : classNode.methods) {
                    if (mn.localVariables == null) mn.localVariables = new ArrayList<>();
                    Set<Integer> existingSlots = new HashSet<>();
                    for (LocalVariableNode lvn : mn.localVariables) {
                        existingSlots.add(lvn.index);
                    }

                    Type[] paramTypes = methodParamTypes.get(mn);
                    boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;
                    int paramSlotStart = isStatic ? 0 : 1;
                    Map<Integer, Type> paramSlotToType = new HashMap<>();

                    int currentSlot = paramSlotStart;
                    for (Type paramType : paramTypes) {
                        paramSlotToType.put(currentSlot, paramType);
                        currentSlot += paramType.getSize();
                    }

                    InsnList instructions = mn.instructions;
                    AbstractInsnNode[] insns = instructions.toArray();

                    for (int slot = 0; slot < mn.maxLocals; slot++) {
                        if (existingSlots.contains(slot)) continue;

                        if (!isStatic && slot == 0) continue;

                        AbstractInsnNode firstUse = null;
                        AbstractInsnNode lastUse = null;

                        for (AbstractInsnNode insn : insns) {
                            if (insn instanceof VarInsnNode) {
                                VarInsnNode vi = (VarInsnNode) insn;
                                if (vi.var == slot) {
                                    if (firstUse == null) firstUse = insn;
                                    lastUse = insn;
                                }
                            }
                        }

                        if (firstUse == null) continue;

                        LabelNode startLabel = new LabelNode(new Label());
                        LabelNode endLabel = new LabelNode(new Label());

                        instructions.insertBefore(firstUse, startLabel);
                        instructions.insert(lastUse, endLabel);

                        String typeDesc;
                        if (paramSlotToType.containsKey(slot)) {
                            typeDesc = paramSlotToType.get(slot).getDescriptor();
                        } else {
                            typeDesc = inferVarTypeDesc(mn, slot);
                        }

                        LocalVariableNode newVar = new LocalVariableNode(
                                "jniilVar" + slot,
                                typeDesc,
                                null,
                                startLabel,
                                endLabel,
                                slot
                        );
                        mn.localVariables.add(newVar);
                        existingSlots.add(slot);
                        if (debug) {
                            System.out.println("Added local variable: slot=" + slot + ", name=" + newVar.name + ", desc=" + newVar.desc + " in method " + mn.name);
                        }
                    }
                }

                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                classNode.accept(cw);
                transformedBytes[0] = cw.toByteArray();
                return transformedBytes[0];

            } catch (Exception e) {
                throw new RuntimeException("Failed to fill Local Variable Table: ", e);
            }
        };

        inst.addTransformer(transformer, true);
        inst.retransformClasses(targetClass);
        inst.removeTransformer(transformer);

        if (transformedBytes[0] == null)
            throw new RuntimeException("Failed to generate transformed bytecode for class: " + targetClass.getName());

        return transformedBytes[0];
    }

    private String inferVarTypeDesc(MethodNode mn, int slot) {
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (insn instanceof VarInsnNode) {
                VarInsnNode vi = (VarInsnNode) insn;
                if (vi.var == slot) {
                    switch (vi.getOpcode()) {
                        case Opcodes.ILOAD:
                        case Opcodes.ISTORE:
                            return "I";
                        case Opcodes.LLOAD:
                        case Opcodes.LSTORE:
                            return "J";
                        case Opcodes.FLOAD:
                        case Opcodes.FSTORE:
                            return "F";
                        case Opcodes.DLOAD:
                        case Opcodes.DSTORE:
                            return "D";
                        case Opcodes.ALOAD:
                        case Opcodes.ASTORE:
                            return "Ljava/lang/Object;";
                    }
                }
            }
        }
        return "Ljava/lang/Object;";
    }
}