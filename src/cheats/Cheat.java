package cheats;

import java.util.ArrayList;

/**
 *
 * @author miche
 */
public class Cheat {

    public enum CheatType {
        TOGGLE,
        SKILL,
        OTHER
    }

    public enum Category {
        BUILD_AND_BUY,
        MONEY,
        MISCELLANEOUS
    }

    public static class Parameter {

        public enum ParameterType {
            BOOLEAN,
            INTEGER,
            STRING
        }

        private final String name;
        private final ParameterType type;
        private final boolean optional;
        private final String description;

        public Parameter(String name, ParameterType type, boolean optional, String description) {
            this.name = name;
            this.type = type;
            this.optional = optional;
            this.description = description;
        }

        public String getName() {
            return this.name;
        }

        public ParameterType getType() {
            return this.type;
        }

        public boolean isOptional() {
            return this.optional;
        }

        public String getDescription() {
            return this.description;
        }

        @Override
        public String toString() {
            return this.name;
        }

    }

    private final String name;
    private final Category category;
    private final CheatType type;
    private final ArrayList<Parameter> parameters;
    private final String notes;

    public Cheat(String name, Category category, CheatType type, ArrayList<Parameter> parameters, String notes) {
        this.name = name;
        this.category = category;
        this.type = type;
        this.parameters = parameters;
        this.notes = notes;
    }

    public String getName() {
        return this.name;
    }

    public Category getCategory() {
        return this.category;
    }

    public CheatType getType() {
        return this.type;
    }

    public ArrayList<Parameter> getParameters() {
        return this.parameters;
    }

    public String getNotes() {
        return this.notes;
    }

    public ArrayList getTableModel() {
        var list = new ArrayList();

        list.add(this.name);
        list.add(this.type.toString());
        list.add(new javax.swing.JButton("Hello"));
        list.add(this.notes);

        return list;
    }

    @Override
    public String toString() {
        return this.name;
    }

}
