package top.nontage.jniil.asm.shadow.metadata;

public class ShadowFieldInfo {
    public final String targetOwner;
    public final String targetName;
    public final String desc;
    public final boolean isMutable;

    public ShadowFieldInfo(String targetOwner, String targetName, String desc, boolean isMutable) {
        this.targetOwner = targetOwner;
        this.targetName = targetName;
        this.desc = desc;
        this.isMutable = isMutable;
    }
}