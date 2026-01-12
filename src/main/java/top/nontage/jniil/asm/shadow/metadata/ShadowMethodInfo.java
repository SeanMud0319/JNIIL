package top.nontage.jniil.asm.shadow.metadata;

public class ShadowMethodInfo {
    public final String targetOwner;
    public final String targetName;
    public final String desc;

    public ShadowMethodInfo(String targetOwner, String targetName, String desc) {
        this.targetOwner = targetOwner;
        this.targetName = targetName;
        this.desc = desc;
    }
}
