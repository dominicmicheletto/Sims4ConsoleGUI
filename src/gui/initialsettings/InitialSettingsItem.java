package gui.initialsettings;

/**
 *
 * @author miche
 */
public class InitialSettingsItem {
    
    public final javax.swing.JMenuItem item;
    public boolean state;

    public InitialSettingsItem(javax.swing.JMenuItem item) {
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
