package gui;

/**
 *
 * @author miche
 */
public class BoundMenuItem extends javax.swing.JCheckBoxMenuItem {

    public final javax.swing.JCheckBoxMenuItem parent;

    public BoundMenuItem(java.awt.Component parent) {
        super();
        this.parent = (javax.swing.JCheckBoxMenuItem) parent;

        this.setUp();
    }

    private void setUp() {
        this.setText(this.parent.getText());
        for (var listener : this.parent.getActionListeners()) {
            this.addActionListener(listener);
        }
        this.parent.addPropertyChangeListener("enabled", (var listener)
                -> this.setEnabled((Boolean) listener.getNewValue()));
        this.parent.addChangeListener((var listener)
                -> this.setSelected(((javax.swing.JCheckBoxMenuItem) listener.getSource()).getState()));
        this.setAccelerator(this.parent.getAccelerator());
    }
    
}
