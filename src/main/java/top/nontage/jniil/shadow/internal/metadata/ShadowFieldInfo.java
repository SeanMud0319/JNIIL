package top.nontage.jniil.shadow.internal.metadata;

public class ShadowFieldInfo {
    public final String targetOwner;
    public final String targetName;
    public final String desc;
    public final boolean isMutable;
    public final boolean isViewOnly;

    public ShadowFieldInfo(String targetOwner, String targetName, String desc, boolean isMutable, boolean isViewOnly) {
        this.targetOwner = targetOwner;
        this.targetName = targetName;
        this.desc = desc;
        this.isMutable = isMutable;
        this.isViewOnly = isViewOnly;
    }
}