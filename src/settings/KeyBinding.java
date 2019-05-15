package settings;

import java.io.Serializable;

/**
 * A class which represents a key binding for a menu item. A key binding
 * consists of the key code for the virtual key, and flags for the control,
 * shift, and alt masks. This is also Serializable so that it can be saved on
 * disk.
 */
public class KeyBinding implements Serializable {

    public final int keyCode;
    public boolean ctrlMask;
    public boolean shiftMask;
    public boolean altMask;

    public KeyBinding() {
        this.keyCode = -1;
    }

    public KeyBinding(int keyCode, boolean ctrlMask, boolean shiftMask, boolean altMask) {
        this.keyCode = keyCode;
        this.ctrlMask = ctrlMask;
        this.shiftMask = shiftMask;
        this.altMask = altMask;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + this.keyCode;
        hash = 67 * hash + (this.ctrlMask ? 1 : 0);
        hash = 67 * hash + (this.shiftMask ? 1 : 0);
        hash = 67 * hash + (this.altMask ? 1 : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "KeyBinding{"
                + "keyCode=" + keyCode
                + ", ctrlMask=" + ctrlMask
                + ", shiftMask=" + shiftMask
                + ", altMask=" + altMask
                + '}';
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
        final KeyBinding other = (KeyBinding) obj;
        if (this.keyCode != other.keyCode) {
            return false;
        }
        if (this.ctrlMask != other.ctrlMask) {
            return false;
        }
        if (this.shiftMask != other.shiftMask) {
            return false;
        }
        return this.altMask == other.altMask;
    }

    /**
     * A convenience method which return the KeyStroke which this KeyBinding
     * represents. This is used when setting the accelerator for menu items.
     *
     * @return the KeyStroke this class represents
     */
    public javax.swing.KeyStroke getKeyStroke() {
        int modifiers = 0;

        if (this.altMask) {
            modifiers |= java.awt.event.KeyEvent.ALT_DOWN_MASK;
        }
        if (this.ctrlMask) {
            modifiers |= java.awt.event.KeyEvent.CTRL_DOWN_MASK;
        }
        if (this.shiftMask) {
            modifiers |= java.awt.event.KeyEvent.SHIFT_DOWN_MASK;
        }

        return javax.swing.KeyStroke.getKeyStroke(this.keyCode, modifiers);
    }

}
