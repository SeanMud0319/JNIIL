package top.nontage.jniil.shadow.metadata;

import java.util.Objects;

public class FieldKey {
    public final String owner;
    public final String name;
    public final String desc;

    public FieldKey(String owner, String name, String desc) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldKey)) return false;
        FieldKey that = (FieldKey) o;
        return owner.equals(that.owner) && name.equals(that.name) && desc.equals(that.desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, desc);
    }
}

