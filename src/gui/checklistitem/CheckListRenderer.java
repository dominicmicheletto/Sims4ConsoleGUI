package gui.checklistitem;

/**
 *
 * @author miche
 */
public class CheckListRenderer extends javax.swing.JCheckBox
        implements javax.swing.ListCellRenderer<CheckListItem> {

    public CheckListRenderer() {
        super.setOpaque(true);
    }

    @Override
    public java.awt.Component getListCellRendererComponent(
            javax.swing.JList<? extends CheckListItem> list,
            CheckListItem value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

        if (value == null) {
            return this;
        }

        if (value instanceof CheckListItemSeparator) {
            var label = new javax.swing.JLabel();
            var matteBorder
                    = javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.GRAY);
            var titledBorder
                    = javax.swing.BorderFactory.createTitledBorder(matteBorder,
                            String.valueOf(value), javax.swing.border.TitledBorder.CENTER,
                            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                            list.getFont().deriveFont(java.awt.Font.BOLD));
            label.setBorder(titledBorder);
            label.setEnabled(list.isEnabled());

            return label;
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
