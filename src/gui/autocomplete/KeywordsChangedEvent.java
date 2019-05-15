package gui.autocomplete;

import java.util.ArrayList;

/**
 *
 * @author miche
 */
public class KeywordsChangedEvent {

    public static enum EventType {
        INSERTION,
        REMOVAL,
        MODIFICATION
    }

    private final ArrayList<String> list;
    private final javax.swing.text.JTextComponent source;
    private final String value;
    private final EventType eventType;

    public KeywordsChangedEvent(ArrayList<String> list,
            javax.swing.text.JTextComponent source,
            String value,
            EventType eventType) {
        this.list = list;
        this.source = source;
        this.value = value;
        this.eventType = eventType;
    }

    public ArrayList<String> getList() {
        return this.list;
    }

    public javax.swing.text.JTextComponent getSource() {
        return this.source;
    }

    public String getValue() {
        return this.value;
    }

    public EventType getEventType() {
        return this.eventType;
    }
    
}
