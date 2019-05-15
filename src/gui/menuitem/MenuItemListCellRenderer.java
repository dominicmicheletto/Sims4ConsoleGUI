package gui.menuitem;

/**
 *
 * @author miche
 */
public class MenuItemListCellRenderer
            extends javax.swing.JLabel
            implements javax.swing.ListCellRenderer<MenuItem> {

        public MenuItemListCellRenderer() {
            super();
            super.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 5, 0, 0));
        }

        @Override
        public java.awt.Component getListCellRendererComponent(
                javax.swing.JList<? extends MenuItem> list,
                MenuItem value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {

            if (value instanceof MenuItemSeparator) {
                var label = new javax.swing.JLabel();
                var matteBorder
                        = javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.GRAY);
                var titledBorder
                        = javax.swing.BorderFactory.createTitledBorder(matteBorder,
                                String.valueOf(value), javax.swing.border.TitledBorder.CENTER,
                                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
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
