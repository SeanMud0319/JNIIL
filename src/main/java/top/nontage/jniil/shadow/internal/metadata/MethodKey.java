package top.nontage.jniil.shadow.internal.metadata;

import java.util.Objects;

public class MethodKey {
    public final String owner;
    public final String name;
    public final String desc;

    public MethodKey(String owner, String name, String desc) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodKey)) return false;
        MethodKey that = (MethodKey) o;
        return owner.equals(that.owner) && name.equals(that.name) && desc.equals(that.desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, desc);
    }
}

