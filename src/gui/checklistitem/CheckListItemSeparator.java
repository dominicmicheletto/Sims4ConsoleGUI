package gui.checklistitem;

/**
 *
 * @author miche
 */
public class CheckListItemSeparator extends CheckListItem {

    public final String name;

    public CheckListItemSeparator(String name) {
        super(null);
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
