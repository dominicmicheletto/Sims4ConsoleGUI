package gui.initialsettings;

import java.util.HashMap;

/**
 *
 * @author miche
 */
public class InitialSettingsItemModel
        extends javax.swing.DefaultListModel<InitialSettingsItem> {
    
    private final HashMap<String, Integer> nameToStringMap;

    public InitialSettingsItemModel() {
        super();
        this.nameToStringMap = new HashMap<>();
    }

    @Override
    public void addElement(InitialSettingsItem element) {
        String name = element.item.getName();

        this.nameToStringMap.put(name, this.size());
        super.addElement(element);
    }

    @Override
    public void addAll(java.util.Collection<? extends InitialSettingsItem> elements) {
        elements.forEach((var element) -> this.addElement(element));
    }

    public void setState(String key, boolean value) {
        var index = this.nameToStringMap.get(key);
        if (index != null) {
            var item = this.get(index);
            if (item != null) {
                item.state(value);
            }
        }
    }
    
    public boolean getState(String key) {
        var index = this.nameToStringMap.get(key);
        if (index != null) {
            var item = this.get(index);
            if (item != null) {
                return item.state();
            }
        }
        
        return false;
    }
    
}
