package gui.menuitem;

/**
 *
 * @author miche
 */
public class MenuItem {

    public final javax.swing.JMenuItem item;
    public final String name;

    public MenuItem(String name, javax.swing.JMenuItem item) {
        this.name = name;
        this.item = item;
    }

    @Override
    public String toString() {
        return this.item.getText();
    }

}
