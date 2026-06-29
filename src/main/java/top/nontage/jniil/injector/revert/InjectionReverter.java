package top.nontage.jniil.injector.revert;

import top.nontage.jniil.JNIIL;
import top.nontage.jniil.injector.cache.InjectionCacheProxy;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

/**
 * Utility for reverting class bytecode modifications.
 * * <p><b>WARNING: Use of this class is discouraged.</b></p>
 * * <p>While this reverter allows rolling back bytecode changes, it poses significant risks:
 * <ul>
 * <li><b>Instruction Offset Shifts:</b> Repeatedly re-injecting and reverting can lead to
 * inconsistent instruction offsets, making subsequent {@code @At} injections (based on
 * line numbers or ordinals) unreliable or impossible.</li>
 * <li><b>State Inconsistency:</b> It only restores the bytecode structure; it cannot
 * revert the actual runtime memory state or static variables modified by the injected code.</li>
 * <li><b>JVM Stability:</b> Frequent class retransformation increases the likelihood of
 * {@code VerifyError} and may trigger edge cases in the JVM's class-loading subsystem.</li>
 * </ul>
 * * <p><b>Recommendation:</b> It is highly recommended to design your application to use
 * permanent injections. If you must toggle logic, consider using a runtime flag or
 * conditional branches within your injected bytecode rather than modifying the class structure.</p>
 */
public class InjectionReverter {
    private static final Instrumentation inst = JNIIL.getInstrumentation();

    /**
     * Reverts a class to the state represented by the provided {@link InjectionRecord}.
     *
     * @param record The historical record of the class to be restored.
     * @throws UnmodifiableClassException If the JVM cannot retransform the class.
     * @throws UnsupportedOperationException If JNIIL revert storage is not enabled.
     */
    public static void revertInjection(InjectionRecord record) throws UnmodifiableClassException {
        if (record == null) {
            throw new IllegalArgumentException("InjectionRecord cannot be null");
        }

        if (!JNIIL.isStoreRevertByteCode()) {
            throw new UnsupportedOperationException("Store Revert not enabled. Use JNIIL.setStoreRevertByteCode(true) to enable.");
        }

        Class<?> clazz = record.getType();
        byte[] bytes = record.getBytecode();

        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytecode is null or empty");
        }

        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (classBeingRedefined == clazz) {
                // Restore cache to the reverted state
                InjectionCacheProxy.put(clazz, bytes);
                InjectionCacheProxy.removeNode(className == null ? clazz.getName() : className.replace('/', '.'));
                return bytes;
            }
            return null;
        };

        try {
            inst.addTransformer(transformer, true);
            inst.retransformClasses(clazz);
        } finally {
            inst.removeTransformer(transformer);
        }
    }
}