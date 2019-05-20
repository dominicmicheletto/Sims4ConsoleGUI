package main;

import main.CommandProtocol.CommandState;

/**
 *
 * @author miche
 */
public class CommandStateChangedEvent {
    
    private final CommandState prevState;
    private final CommandState newState;
    
    public CommandStateChangedEvent(CommandState prevState, CommandState newState) {
        this.prevState = prevState;
        this.newState = newState;
    }

    public CommandState getPrevState() {
        return prevState;
    }

    public CommandState getNewState() {
        return newState;
    }
    
}
