package gui.checklistitem;

/**
 *
 * @author miche
 */
public class CheckListItem {

    public final javax.swing.JMenuItem item;
    public boolean state;

    public CheckListItem(javax.swing.JMenuItem item) {
        this.item = item;
        this.state = false;
    }

    public boolean state() {
        return this.state;
    }

    public void state(boolean state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return this.item.getText();
    }
    
}
