package main;

import java.util.Objects;

/**
 * A simple class which represents a Virtual Key. Used internally for the
 * jComboBoxSettingsShortcutKey.
 */
public class VirtualKey {

    public String name;
    public int keyCode;

    public VirtualKey() {
        this("", 0);
    }

    public VirtualKey(String name, int keyCode) {
        this.name = name;
        this.keyCode = keyCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final VirtualKey other = (VirtualKey) obj;
        if (this.keyCode != other.keyCode) {
            return false;
        }
        return Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 31 * hash + Objects.hashCode(this.name);
        hash = 31 * hash + this.keyCode;
        return hash;
    }

    @Override
    public String toString() {
        return this.name;
    }

}
