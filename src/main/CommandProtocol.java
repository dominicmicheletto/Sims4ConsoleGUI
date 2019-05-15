package main;

/**
 *
 * @author miche
 */
public class CommandProtocol {

    public enum CommandState {
        /**
         * Disconnected from the Game (an error occurred, the game crashed,
         * etc).
         */
        DISCONNECTED,
        /**
         * Waiting for a connection from the Game to be established.
         */
        WAITING,
        /**
         * Connected to the Game but not ready to execute commands yet.
         */
        CONNECTED_NOT_READY,
        /**
         * Connected to the Game and ready to execute commands.
         */
        CONNECTED_READY,
        /**
         * Connected to the Game and travelling.
         */
        CONNECTED_TRAVELLING,
        /**
         * Connected and currently executing a command.
         */
        CONNECTED_COMMAND_EXECUTING,
        /**
         * Connected and done executing a command.
         */
        CONNECTED_COMMAND_DONE,
        /**
         * Connected and command failed during execution.
         */
        CONNECTED_COMMAND_FAILED,
        /**
         * Connected and querying the Game for information.
         */
        CONNECTED_QUERY_EXECUTING,
        /**
         * Connected and done querying the Game for information.
         */
        CONNECTED_QUERY_DONE,
        /**
         * Connected and querying the Game for information failed.
         */
        CONNECTED_QUERY_FAILED;
    }

    public CommandState currentState;

    public CommandProtocol() {
        this.currentState = CommandState.WAITING;
    }
}
