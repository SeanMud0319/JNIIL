package top.nontage.jniil.injector.revert;

import top.nontage.jniil.JNIIL;
import top.nontage.jniil.injector.base.AbstractMethodInjector;

import java.util.List;

/**
 * Represents a historical snapshot of a class's bytecode captured during the injection process.
 *
 * <p>This record is used by the {@link InjectionReverter} to roll back changes made to a class
 * by restoring it to a previously captured state. Each record tracks the original bytecode
 * and the order in which the injection occurred.</p>
 */
public class InjectionRecord {
    /** The class type that was modified. */
    private final Class<?> type;

    /** The raw bytecode of the class captured before or after a specific injection. */
    private final byte[] bytecode;

    /** The sequence index of this injection record. */
    private final int count;

    /**
     * Constructs a new InjectionRecord.
     *
     * @param type     The class associated with this injection.
     * @param bytecode The bytecode state to be stored.
     * @param count    The chronological order or iteration count of this injection.
     */
    public InjectionRecord(Class<?> type, byte[] bytecode, int count) {
        this.type = type;
        this.bytecode = bytecode;
        this.count = count;
    }

    /** @return The class type associated with this record. */
    public Class<?> getType() {
        return type;
    }

    /** @return The stored bytecode array. */
    public byte[] getBytecode() {
        return bytecode;
    }

    /** @return The historical count/sequence index of this injection. */
    public int getCount() {
        return count;
    }

    /**
     * Retrieves the list of injection history records for a specific class.
     *
     * @param clazz The target class to look up.
     * @return A list of {@link InjectionRecord} for the given class.
     * @throws UnsupportedOperationException if {@code JNIIL.isStoreRevertByteCode()} is false.
     */
    public static List<InjectionRecord> getInjectionRecord(Class<?> clazz) {
        if (!JNIIL.isStoreRevertByteCode()) {
            throw new UnsupportedOperationException("Store Revert not enabled. Use JNIIL.setStoreRevertByteCode(true) to enable.");
        }
        return AbstractMethodInjector.getInjectionRecords().get(clazz);
    }
}