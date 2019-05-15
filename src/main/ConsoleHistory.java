package main;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Optional;

/**
 *
 * @author miche
 */
public class ConsoleHistory {

    public ArrayList<String> history;
    public ListIterator<String> position;
    public ArrayList<String> output;

    public ConsoleHistory() {
        this.history = new ArrayList<>();
        this.output = new ArrayList<>();
    }

    public Optional<String> decrementPosition() {
        if (this.position != null && this.position.hasPrevious()) {
            return Optional.of(this.position.previous());
        } else {
            this.resetPosition(false);
        }
        return Optional.empty();
    }

    public Optional<String> incrementPosition() {
        if (this.position != null && this.position.hasNext()) {
            return Optional.of(this.position.next());
        } else {
            this.resetPosition(true);
        }
        return Optional.empty();
    }

    public void resetPosition(boolean forward) {
        if (forward) {
            this.position = this.history.listIterator(this.history.size());
        } else {
            this.position = this.history.listIterator();
        }
    }

    public void addValue(String value, String output) {
        this.history.add(value);
        this.output.add(output);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<html><body>");

        String promptHeader = "&gt;&gt;&gt";
        String formatString = "<div><p class='prompt'>%s <span class='command'>%s</span></p>"
                + "%s</div>";
        String outputString = "<blockquote><p><span class='%s'>%s</span></p></blockquote>";
        String commandOutput = String.format(formatString, promptHeader, "%s", outputString);

        for (int i = 0; i < this.history.size(); i++) {
            var command = this.history.get(i);
            var result = this.output.get(i);

            if (result.equals("OUTPUT: NO-OP")) {
                builder.append(String.format(formatString, promptHeader, command, ""));
            } else {
                var state = result.contains("SUCCESS") ? "success" : "error";
                result = result.replace("OUTPUT: ", "")
                        .replace("SUCCESS: ", "").replace("FAILURE: ", "");
                builder.append(String.format(commandOutput, command, state, result));
            }
        }

        builder.append("</body></html>");

        return builder.toString();
    }
}
