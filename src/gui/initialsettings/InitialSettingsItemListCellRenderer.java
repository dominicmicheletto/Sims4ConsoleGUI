package gui.initialsettings;

import java.awt.Component;
import javax.swing.JList;

/**
 *
 * @author miche
 */
public class InitialSettingsItemListCellRenderer extends javax.swing.JCheckBox
        implements javax.swing.ListCellRenderer<InitialSettingsItem> {

    @Override
    public Component getListCellRendererComponent(
            JList<? extends InitialSettingsItem> list,
            InitialSettingsItem value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
        
        if (value == null) {
            return this;
        }
        
        if (isSelected) {
            this.setBackground(list.getSelectionBackground());
            this.setForeground(list.getSelectionForeground());
        } else {
            this.setBackground(list.getBackground());
            this.setForeground(list.getForeground());
        }

        this.setEnabled(list.isEnabled());
        this.setSelected(value.state());
        this.setFont(list.getFont());
        this.setText(String.valueOf(value));
        return this;
        
    }
    
    
    
}
