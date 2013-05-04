package com.labs.commons;

/**
 * @author Amir Lazarovich
 */
public class ADK {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////

    // adk-commands
    public static final byte COMMAND_CONTROL = 1;

    // adk-actions
    public static final byte ACTION_LEFT_STICK = 1;
    public static final byte ACTION_RIGHT_STICK = 2;


    ///////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////
    public static String parseCommand(byte command) {
        switch (command) {
            case COMMAND_CONTROL:
                return "Control";

            default:
                return "Unknown";
        }
    }

    public static String parseAction(byte action) {
        switch (action) {
            case ACTION_LEFT_STICK:
                return "Left Stick";

            case ACTION_RIGHT_STICK:
                return "Right Stick";

            default:
                return "Unknown";
        }
    }
}
