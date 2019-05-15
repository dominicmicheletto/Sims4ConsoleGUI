package settings;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

/**
 * A class which represents the settings held by this application. It is
 * Serializable so that they can be saved to a file, and Cloneable so that they
 * can be copied.
 */
public class Settings implements Serializable, Cloneable {

    /**
     * The minimum dimension this application's main window is allowed to take.
     */
    public static final java.awt.Dimension MINIMUM_SIZE = new java.awt.Dimension(550, 630);

    public String fontName;
    public float fontSize;

    public boolean openAtReady;

    public String lookAndFeel;
    public HashMap<String, java.awt.Color> colours;

    public java.awt.Dimension defaultDimension;
    public boolean savePreviousDimension;

    public HashMap<String, KeyBinding> keyBindings;
    public HashSet<String> quickCheats;
    public HashMap<String, Boolean> initialSettings;

    public transient boolean isInValidState;
    public transient HashMap<String, Boolean> mcccSettings;
    public transient HashMap<String, Boolean> tmexSettings;

    private static final long VERSION_OFFSET = 1;
    private static final long serialVersionUID = -8308417833864101145L + VERSION_OFFSET;

    public static HashMap<String, KeyBinding> get_default_keybindings() {
        var bindings = new HashMap<String, KeyBinding>();

        bindings.put("jMenuItemOptions", new KeyBinding(
                java.awt.event.KeyEvent.VK_F1, false, false, false));
        bindings.put("jMenuItemExit", new KeyBinding(
                java.awt.event.KeyEvent.VK_F4, false, false, true));
        bindings.put("jCheckBoxMenuItemCheatsConsole", new KeyBinding(
                java.awt.event.KeyEvent.VK_C, true, true, false));
        bindings.put("jCheckBoxMenuItemCheatsExplorer", new KeyBinding(
                java.awt.event.KeyEvent.VK_E, true, true, false));
        bindings.put("jMenuItemAbout", new KeyBinding());
        bindings.put("jMenuItemCascade", new KeyBinding());
        bindings.put("jMenuItemAbout", new KeyBinding());
        bindings.put("jMenuItemMaximise", new KeyBinding());
        bindings.put("jMenuItemMinimise", new KeyBinding());
        bindings.put("jMenuItemClose", new KeyBinding());
        bindings.put("jMenuItemCloseAll", new KeyBinding());
        bindings.put("jCheckBoxMenuItemTestingCheats", new KeyBinding(
                java.awt.event.KeyEvent.VK_T, true, true, false));
        bindings.put("jCheckBoxMenuItemHoverEffects", new KeyBinding());
        bindings.put("jCheckBoxMenuItemHeadlineEffects", new KeyBinding(
                java.awt.event.KeyEvent.VK_H, true, true, false));
        bindings.put("jCheckBoxMenuItemMoveObjects", new KeyBinding(
                java.awt.event.KeyEvent.VK_M, true, false, false));
        bindings.put("jCheckBoxMenuItemHiddenObjects", new KeyBinding(
                java.awt.event.KeyEvent.VK_H, true, false, false));
        bindings.put("jCheckBoxMenuItemGamePlayUnlocks", new KeyBinding(
                java.awt.event.KeyEvent.VK_U, true, false, false));
        bindings.put("jCheckBoxMenuItemFreeRealEstate", new KeyBinding(
                java.awt.event.KeyEvent.VK_R, true, false, false));
        bindings.put("jCheckBoxMenuItemFullEditMode", new KeyBinding(
                java.awt.event.KeyEvent.VK_F, true, false, false));
        bindings.put("jMenuItemMotherlode", new KeyBinding(
                java.awt.event.KeyEvent.VK_M, true, true, false));
        bindings.put("jMenuItemRosebud", new KeyBinding(
                java.awt.event.KeyEvent.VK_R, true, true, false));
        bindings.put("jMenuItemClearMoney", new KeyBinding(
                java.awt.event.KeyEvent.VK_0, true, false, false));
        bindings.put("jMenuItemSetMoney", new KeyBinding(
                java.awt.event.KeyEvent.VK_4, true, true, false));
        bindings.put("jMenuItemDepositMoney", new KeyBinding(
                java.awt.event.KeyEvent.VK_EQUALS, true, true, false));
        bindings.put("jMenuItemWithdrawMoney", new KeyBinding(
                java.awt.event.KeyEvent.VK_MINUS, true, true, false));
        bindings.put("jMenuItemSetSkill", new KeyBinding(
                java.awt.event.KeyEvent.VK_S, true, false, true));
        bindings.put("jMenuItemClearAllSkills", new KeyBinding(
                java.awt.event.KeyEvent.VK_C, true, false, true));
        bindings.put("jMenuItemPromote", new KeyBinding());
        bindings.put("jMenuItemDemote", new KeyBinding());
        bindings.put("jMenuItemEnableFreeBuild", new KeyBinding());
        bindings.put("jMenuItemEditRelationship", new KeyBinding());
        bindings.put("jMenuItemClearAllRelationships", new KeyBinding());
        bindings.put("jMenuItemIntroduceSelfToEveryone", new KeyBinding());

        return bindings;
    }

    public static HashSet<String> get_default_quickcheats() {
        var set = new HashSet<String>();

        set.add("jCheckBoxMenuItemTestingCheats");
        set.add("jCheckBoxMenuItemMoveObjects");
        set.add("jCheckBoxMenuItemHeadlineEffects");
        set.add("jMenuItemMotherlode");
        set.add("jMenuItemSetSkill");

        return set;
    }
    
    public static HashMap<String, Boolean> get_default_initial_settings() {
        var map = new HashMap<String, Boolean>();
        
        map.put("jCheckBoxMenuItemTestingCheats", false);
        map.put("jCheckBoxMenuItemHeadlineEffects", true);
        map.put("jCheckBoxMenuItemHoverEffects", true);
        map.put("jCheckBoxMenuItemMoveObjects", false);
        map.put("jCheckBoxMenuItemHiddenObjects", false);
        map.put("jCheckBoxMenuItemGamePlayUnlocks", false);
        map.put("jCheckBoxMenuItemFreeRealEstate", false);
        map.put("jCheckBoxMenuItemFullEditMode", false);
        
        return map;
    }

    public Settings() {
        this.isInValidState = true;
        this.mcccSettings = null;
        this.tmexSettings = null;

        this.fontName = "DejaVu Sans Mono";
        this.fontSize = 10f;
        this.openAtReady = false;
        this.lookAndFeel = "[System]";
        this.colours = new HashMap<>();

        this.colours.put("Prompt", new java.awt.Color(0, 0, 0));
        this.colours.put("Output", new java.awt.Color(0, 153, 0));
        this.colours.put("Error", new java.awt.Color(255, 0, 0));

        this.savePreviousDimension = false;
        this.defaultDimension = MINIMUM_SIZE;

        this.keyBindings = Settings.get_default_keybindings();
        this.quickCheats = Settings.get_default_quickcheats();
        this.initialSettings = Settings.get_default_initial_settings();
    }

    public Settings(String fontName, float fontSize, boolean openAtReady,
            String lookAndFeel, HashMap<String, java.awt.Color> colours,
            boolean savePreviousDimension, java.awt.Dimension defaultDimension,
            HashMap<String, KeyBinding> keyBindings,
            HashSet<String> quickCheats, HashMap<String, Boolean> initialSettings) {
        this.isInValidState = true;
        this.mcccSettings = null;
        this.tmexSettings = null;

        this.fontName = fontName;
        this.fontSize = fontSize;
        this.openAtReady = openAtReady;
        this.lookAndFeel = lookAndFeel;
        this.colours = colours;
        this.savePreviousDimension = savePreviousDimension;
        this.defaultDimension = defaultDimension;
        this.keyBindings = keyBindings;
        this.quickCheats = quickCheats;
        this.initialSettings = initialSettings;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        var clone = (Settings) super.clone();

        clone.colours = (HashMap<String, java.awt.Color>) this.colours.clone();
        clone.keyBindings = (HashMap<String, KeyBinding>) this.keyBindings.clone();
        clone.quickCheats = (HashSet<String>) this.quickCheats.clone();
        clone.initialSettings = (HashMap<String, Boolean>) this.initialSettings.clone();
        clone.defaultDimension = (java.awt.Dimension) this.defaultDimension.clone();
        
        if (this.mcccSettings != null)
            clone.mcccSettings = (HashMap<String, Boolean>) this.mcccSettings.clone();
        if (this.tmexSettings != null)
            clone.tmexSettings = (HashMap<String, Boolean>) this.tmexSettings.clone();

        return clone;
    }

    public java.awt.Font getFont() {
        return java.awt.Font.decode(this.fontName).deriveFont(this.fontSize);
    }

    public void setFont(String fontName, float fontSize) {
        this.fontName = fontName;
        this.fontSize = fontSize;
    }

    public boolean openAtReady() {
        return this.openAtReady;
    }

    public void openAtReady(boolean openAtReady) {
        this.openAtReady = openAtReady;
    }

    public String lookAndFeel() {
        return this.lookAndFeel;
    }

    public void lookAndFeel(String lookAndFeel) {
        this.lookAndFeel = lookAndFeel;
    }

    public boolean savePreviousDimension() {
        return this.savePreviousDimension;
    }

    public void savePreviousDimension(boolean savePreviousDimension) {
        this.savePreviousDimension = savePreviousDimension;
    }

    public java.awt.Dimension defaultDimension() {
        return defaultDimension;
    }

    public void defaultDimension(java.awt.Dimension defaultDimension) {
        this.defaultDimension = defaultDimension;
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeUTF(this.fontName);
        out.writeFloat(this.fontSize);
        out.writeBoolean(this.openAtReady);
        out.writeUTF(this.lookAndFeel);
        out.writeObject(this.colours);
        out.writeBoolean(this.savePreviousDimension);
        out.writeObject(this.defaultDimension);
        out.writeObject(this.keyBindings);
        out.writeObject(this.quickCheats);
        out.writeObject(this.initialSettings);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.fontName = in.readUTF();
        this.fontSize = in.readFloat();
        this.openAtReady = in.readBoolean();
        this.lookAndFeel = in.readUTF();
        this.colours = (HashMap<String, java.awt.Color>) in.readObject();
        this.savePreviousDimension = in.readBoolean();
        this.defaultDimension = (java.awt.Dimension) in.readObject();
        this.keyBindings = (HashMap<String, KeyBinding>) in.readObject();
        this.quickCheats = (HashSet<String>) in.readObject();
        this.initialSettings = (HashMap<String, Boolean>) in.readObject();
    }

    private void readObjectNoData() throws ObjectStreamException {
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(this.fontName);
        hash = 31 * hash + Float.floatToIntBits(this.fontSize);
        hash = 31 * hash + (this.openAtReady ? 1 : 0);
        hash = 31 * hash + Objects.hashCode(this.lookAndFeel);
        hash = 31 * hash + Objects.hashCode(this.colours);
        hash = 31 * hash + (this.savePreviousDimension ? 1 : 0);
        hash = 31 * hash + Objects.hashCode(this.defaultDimension);
        hash = 31 * hash + Objects.hashCode(this.keyBindings);
        hash = 31 * hash + Objects.hashCode(this.quickCheats);
        hash = 31 * hash + Objects.hashCode(this.initialSettings);
        return hash;
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
        final Settings other = (Settings) obj;
        if (Float.floatToIntBits(this.fontSize) != Float.floatToIntBits(other.fontSize)) {
            return false;
        }
        if (this.openAtReady != other.openAtReady) {
            return false;
        }
        if (!Objects.equals(this.fontName, other.fontName)) {
            return false;
        }
        if (!Objects.equals(this.lookAndFeel, other.lookAndFeel)) {
            return false;
        }
        if (!Objects.equals(this.colours, other.colours)) {
            return false;
        }
        if (this.savePreviousDimension != other.savePreviousDimension) {
            return false;
        }
        if (!Objects.equals(this.defaultDimension, other.defaultDimension)) {
            return false;
        }
        if (!Objects.equals(this.keyBindings, other.keyBindings)) {
            return false;
        }
        if (!Objects.equals(this.quickCheats, other.quickCheats)) {
            return false;
        }
        return Objects.equals(this.initialSettings, other.initialSettings);
    }

    @Override
    public String toString() {
        return "Settings{"
                + "fontName=" + fontName
                + ", fontSize=" + fontSize
                + ", openAtReady=" + openAtReady
                + ", lookAndFeel=" + lookAndFeel
                + ", colours=" + colours
                + ", savePreviousDimension=" + savePreviousDimension
                + ", defaultDimension=" + defaultDimension
                + ", keyBindings=" + keyBindings
                + ", quickCheats=" + quickCheats
                + ", initialSettings=" + initialSettings
                + '}';
    }

}
