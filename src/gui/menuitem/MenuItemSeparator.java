package gui.menuitem;

/**
 *
 * @author miche
 */
public class MenuItemSeparator extends MenuItem {

    public MenuItemSeparator(String title) {
        super(title, null);
    }

    @Override
    public String toString() {
        return super.name;
    }

}
