package top.nontage.jniil.injector.insn;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.exception.InjectionException;
import top.nontage.jniil.injector.base.AbstractMethodInjector;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;
import top.nontage.jniil.interfaces.InsnInjectable;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.verify.BytecodeVerifier;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class InstructionInjector extends AbstractMethodInjector {
    @Override
    public void inject(Object... injectable) throws Exception {
        for (Object i : injectable) this.inject(i);
    }

    @Override
    public void inject(Object injectableInstance) throws Exception {
        if (!(injectableInstance instanceof InsnInjectable)) {
            throw new InjectionException("Class: " + injectableInstance.getClass().getName() + " needs to implement InsnInjectable");
        }

        InsnInjectable insnInjectable = (InsnInjectable) injectableInstance;
        Method applyMethod = insnInjectable.getClass().getDeclaredMethod("apply", InsnContext.class, InsnList.class);
        At at = applyMethod.getAnnotation(At.class);
        if (at == null) {
            throw new IllegalStateException("InsnInjectable implementation must have @At annotation on apply method: "
                    + insnInjectable.getClass().getName());
        }

        TargetInfo info = extractTargetInfo(insnInjectable, applyMethod);
        ClassLoader loader = getTargetLoader(info).unwarp();
        Class<?> targetClass = Class.forName(info.typeName, true, loader);

        byte[] currentBytecode = InjectionCacheProxy.contains(info.typeName)
                ? InjectionCacheProxy.get(targetClass)
                : InjectionUtil.getOriginalClassBytes(info.typeName);

        ClassReader cr = new ClassReader(currentBytecode);
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);

        MethodNode targetMethod = null;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(info.methodName)) {
                String[] targetParams = info.methodParams;
                if (targetParams.length > 0) {
                    String currentParamsDesc = mn.desc.substring(0, mn.desc.indexOf(')') + 1);
                    String expectedParamsDesc = InjectionUtil.getMethodDescriptor(targetParams, "V");
                    expectedParamsDesc = expectedParamsDesc.substring(0, expectedParamsDesc.indexOf(')') + 1);
                    if (currentParamsDesc.equals(expectedParamsDesc)) {
                        targetMethod = mn;
                        break;
                    }
                } else {
                    targetMethod = mn;
                    break;
                }
            }
        }

        if (targetMethod == null) throw new NoSuchMethodException(info.methodName + " in " + info.typeName);

        AbstractInsnNode anchor = findAnchorByAt(targetMethod, at);

        InsnContext ctx = new InsnContext(targetMethod, anchor);
        InsnList toPush = new InsnList();
        InsnList resultInsnList = insnInjectable.apply(ctx, toPush);

        InsnList finalToInject = (resultInsnList != null) ? resultInsnList : toPush;
        if (finalToInject.size() > 0) {
            if (at.debug())
                System.out.println("[JNIIL-DEBUG] Injecting " + finalToInject.size() + " instructions " + (at.shiftAfter() ? "AFTER" : "BEFORE") + " anchor.");
            if (at.shiftAfter()) {
                targetMethod.instructions.insert(anchor, finalToInject);
            } else {
                targetMethod.instructions.insertBefore(anchor, finalToInject);
            }
        }

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        byte[] finalBytecode = cw.toByteArray();

        if (JNIIL.isBytecodeVerifying()) {
            BytecodeVerifier.verify(info.typeName, currentBytecode, finalBytecode);
        }

        apply(targetClass, finalBytecode);
        InjectionCacheProxy.put(targetClass, finalBytecode);
        injectedClasses.add(info.typeName);

        if (JNIIL.isMethodOutputEnabled()) {
            String relativePath = info.typeName.replace('.', File.separatorChar) + ".class";
            File outputFile = new File(JNIIL.getMethodOutputDir(), relativePath);
            InjectionUtil.dumpClass(finalBytecode, outputFile.getAbsolutePath());
            System.out.println("[JNIIL-DEBUG] Class dumped to hierarchy: " + outputFile.getAbsolutePath());
        }
    }

    public static AbstractInsnNode findAnchorByAt(MethodNode mn, At at) {
        int targetLine = at.line();
        if (targetLine >= 0) {
            if (at.debug()) {
                System.out.println("[JNIIL-DEBUG] Looking for line number: " + targetLine);
            }
            return getAbstractInsnNode(mn, targetLine);
        }

        int targetOpcode = at.opcode();
        String targetId = at.identifier();
        int targetOrdinal = at.ordinal();
        boolean debug = at.debug();

        if (targetOpcode == 114514 || targetOpcode <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Illegal @At configuration in method %s: Opcode %d is invalid! " +
                            "You must specify a valid opcode to locate an anchor.",
                    mn.name, targetOpcode
            ));
        }

        String targetOpcodeName = targetOpcode < Printer.OPCODES.length
                ? Printer.OPCODES[targetOpcode]
                : "UNKNOWN_OP_" + targetOpcode;

        if (debug) {
            System.out.println("[JNIIL-DEBUG] Scanning method: " + mn.name + mn.desc);
            System.out.println("[JNIIL-DEBUG] Target: Opcode=" + targetOpcodeName + "(" + targetOpcode + "), ID=" + targetId + ", Ordinal=" + targetOrdinal);
        }

        List<AbstractInsnNode> candidates = new ArrayList<>();
        AbstractInsnNode[] allInsns = mn.instructions.toArray();

        for (int i = 0; i < allInsns.length; i++) {
            AbstractInsnNode insn = allInsns[i];

            if (insn.getOpcode() == targetOpcode) {
                boolean idMatch = (targetId == null || targetId.isEmpty() || checkIdentifierSafe(insn, targetId));

                if (debug)
                    System.out.println("[JNIIL-DEBUG] Found potential match at index " + i + " (ID Match: " + idMatch + ")");

                if (idMatch) {
                    candidates.add(insn);
                }
            }
        }

        if (candidates.isEmpty()) {
            throw new RuntimeException(String.format(
                    "Injection error: No occurrences of opcode %s(%d) found in method %s.",
                    targetOpcodeName, targetOpcode, mn.name
            ));
        }

        try {
            return candidates.get(targetOrdinal - 1);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexOutOfBoundsException(String.format(
                    "Injection error: @At(opcode=%s, ordinal=%d) failed in method %s. Only %d occurrence(s) found.",
                    targetOpcodeName, targetOrdinal, mn.name, candidates.size()
            ));
        }
    }

    private static AbstractInsnNode getAbstractInsnNode(MethodNode mn, int targetLine) {
        AbstractInsnNode target = null;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LineNumberNode) {
                LineNumberNode ln = (LineNumberNode) insn;
                if (ln.line == targetLine) {
                    target = insn;
                    break;
                }
            }
        }

        if (target == null) {
            throw new RuntimeException(String.format(
                    "Injection error: Line %d not found in method %s.",
                    targetLine, mn.name
            ));
        }
        return target;
    }

    private static boolean checkIdentifierSafe(AbstractInsnNode insn, String id) {
        if (id == null || id.isEmpty()) return true;

        String normalizedId = id.replace('/', '.');

        if (insn instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode) insn;
            String ownerDotted = f.owner.replace('/', '.');
            String fullName = ownerDotted + "." + f.name;
            return normalizedId.equals(f.name) || normalizedId.equals(fullName) || normalizedId.equals(ownerDotted);
        }

        if (insn instanceof MethodInsnNode) {
            MethodInsnNode m = (MethodInsnNode) insn;
            String ownerDotted = m.owner.replace('/', '.');
            String fullName = ownerDotted + "." + m.name;
            return normalizedId.equals(m.name) || normalizedId.equals(fullName) || normalizedId.equals(ownerDotted);
        }

        if (insn instanceof TypeInsnNode) {
            String typeDotted = ((TypeInsnNode) insn).desc.replace('/', '.');
            return typeDotted.equals(normalizedId) || typeDotted.endsWith("." + normalizedId);
        }

        if (insn instanceof LdcInsnNode) {
            Object cst = ((LdcInsnNode) insn).cst;
            if (cst instanceof String) {
                return ((String) cst).replace('/', '.').contains(normalizedId);
            }
            return cst != null && cst.toString().equals(id);
        }

        if (insn instanceof VarInsnNode) {
            return id.equals(String.valueOf(((VarInsnNode) insn).var));
        }

        return false;
    }
}
