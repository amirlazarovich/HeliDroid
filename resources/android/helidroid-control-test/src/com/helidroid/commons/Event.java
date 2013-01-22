package com.helidroid.commons;

/**
* @author Amir Lazarovich
*/
public enum Event {
    MOTORS("motors"),
    ROTATION("rotation"),
    FUNCTION("function"),
    UNKNOWN("");

    private String mValue;

    Event(String value) {
        mValue = value;
    }

    public static Event getByValue(String value) {
        Event event = UNKNOWN;
        for (Event candidate : values()) {
            if (candidate.mValue.equalsIgnoreCase(value)) {
                event = candidate;
                break;
            }
        }

        return event;
    }


    public String getValue() {
        return mValue;
    }
}
