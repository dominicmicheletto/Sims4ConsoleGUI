package gui.checklistitem;

import java.util.HashMap;

/**
 *
 * @author miche
 */
public class CheckListItemModel extends javax.swing.DefaultListModel<CheckListItem> {

    private final HashMap<String, Integer> nameToStringMap;

    public CheckListItemModel() {
        super();
        this.nameToStringMap = new HashMap<>();
    }

    @Override
    public void addElement(CheckListItem element) {
        String name;

        if (element instanceof CheckListItemSeparator || element == null) {
            name = "-";
        } else {
            name = element.item.getName();
        }

        this.nameToStringMap.put(name, this.size());
        super.addElement(element);
    }

    @Override
    public void addAll(java.util.Collection<? extends CheckListItem> elements) {
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
    
}
