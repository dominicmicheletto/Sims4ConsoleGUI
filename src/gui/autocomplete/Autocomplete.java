package gui.autocomplete;

import java.util.ArrayList;

/**
 *
 * @author miche
 */
public class Autocomplete extends java.util.AbstractList<String>
        implements javax.swing.event.DocumentListener, java.util.List<String>,
        KeywordsChangedListener {

    private static enum Mode {
        INSERTION,
        COMPLETION
    }

    private final javax.swing.text.JTextComponent parent;
    private final ArrayList<String> keywords;
    private final ArrayList<KeywordsChangedListener> listeners;
    private Mode mode;

    private static final String COMMIT_ACTION = "commit";

    public Autocomplete(javax.swing.text.JTextComponent parent) {
        this(parent, new ArrayList<>());
    }

    public Autocomplete(javax.swing.text.JTextComponent parent,
            java.util.Collection<? extends String> keywords) {
        this.parent = parent;
        this.keywords = new ArrayList<>();
        this.mode = Mode.INSERTION;
        this.listeners = new ArrayList<>();

        this.keywords.addAll(keywords);
        java.util.Collections.sort(this.keywords);

        this.parent.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("TAB"), COMMIT_ACTION);
        this.parent.getActionMap().put(COMMIT_ACTION, new CommitAction());

        this.registerSelf();
    }

    private void registerSelf() {
        this.addKeywordsChangedListener(this);
    }

    private void notifyListeners(KeywordsChangedEvent evt) {
        this.listeners.forEach((listener) -> listener.keywordsChanged(evt));
    }

    public void addKeywordsChangedListener(KeywordsChangedListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void keywordsChanged(KeywordsChangedEvent evt) {
        java.util.Collections.sort(evt.getList());
    }

    @Override
    public String get(int index) {
        return this.keywords.get(index);
    }

    @Override
    public int size() {
        return this.keywords.size();
    }

    @Override
    public void add(int index, String keyword) {
        this.keywords.add(index, keyword);
        this.notifyListeners(new KeywordsChangedEvent(
                this.keywords, this.parent, keyword,
                KeywordsChangedEvent.EventType.INSERTION));
    }

    @Override
    public String remove(int index) {
        var item = this.keywords.remove(index);

        if (item != null) {
            this.notifyListeners(new KeywordsChangedEvent(
                    this.keywords, this.parent, item,
                    KeywordsChangedEvent.EventType.REMOVAL));
        }

        return item;
    }

    @Override
    public boolean remove(Object obj) {
        var removed = this.keywords.remove(obj);

        if (removed && obj instanceof String) {
            this.notifyListeners(new KeywordsChangedEvent(
                    this.keywords, this.parent, String.valueOf(obj),
                    KeywordsChangedEvent.EventType.REMOVAL));
        }

        return removed;
    }

    @Override
    public String set(int index, String value) {
        var item = this.keywords.set(index, value);

        this.notifyListeners(new KeywordsChangedEvent(
                this.keywords, this.parent, item,
                KeywordsChangedEvent.EventType.REMOVAL));

        return item;
    }

    @Override
    public void insertUpdate(javax.swing.event.DocumentEvent event) {
        
    }

    @Override
    public void removeUpdate(javax.swing.event.DocumentEvent event) {
        
    }

    @Override
    public void changedUpdate(javax.swing.event.DocumentEvent event) {
        
    }

    private class CompletionTask implements Runnable {

        private final String completion;
        private final int position;

        CompletionTask(String completion, int position) {
            this.completion = completion;
            this.position = position;
        }

        @Override
        public void run() {
            
        }
    }

    public class CommitAction extends javax.swing.AbstractAction {

        private static final long serialVersionUID = 5794543109646743416L;

        @Override
        public void actionPerformed(java.awt.event.ActionEvent ev) {
            
        }
    }
    
}
