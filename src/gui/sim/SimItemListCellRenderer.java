package gui.sim;

import java.awt.Component;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.TitledBorder;

/**
 *
 * @author miche
 */
public class SimItemListCellRenderer extends JLabel implements ListCellRenderer<SimItem> {
    
    private final ResourceBundle bundle;
    
    public SimItemListCellRenderer() {
        super();
        super.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 5, 0, 0));
        
        this.bundle = ResourceBundle.getBundle("ConsoleGUI");
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends SimItem> list,
            SimItem value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
        
        if (value instanceof SimItemSeparator) {
            String title = this.bundle.getString(String.format("SimCategory%s",
                    value.getName()));
            
            var label = new JLabel();
                var matteBorder
                        = BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.GRAY);
                var titledBorder
                        = BorderFactory.createTitledBorder(matteBorder,
                                title, TitledBorder.CENTER,
                                TitledBorder.DEFAULT_JUSTIFICATION,
                                list.getFont().deriveFont(java.awt.Font.BOLD));
                label.setBorder(titledBorder);
                
                return label;
        }
        
        if (isSelected) {
            this.setBackground(list.getSelectionBackground());
            this.setForeground(list.getSelectionForeground());
        } else {
            this.setBackground(list.getBackground());
            this.setForeground(list.getForeground());
        }

        this.setOpaque(isSelected || cellHasFocus);
        this.setFont(list.getFont());
        this.setText(String.valueOf(value));

        return this;
        
    }
    
    
    
}
