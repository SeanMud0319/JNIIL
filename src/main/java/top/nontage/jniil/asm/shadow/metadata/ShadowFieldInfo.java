package top.nontage.jniil.asm.shadow.metadata;

public class ShadowFieldInfo {
    public final String targetOwner;
    public final String targetName;
    public final String desc;

    public ShadowFieldInfo(String targetOwner, String targetName, String desc) {
        this.targetOwner = targetOwner;
        this.targetName = targetName;
        this.desc = desc;
    }
}
