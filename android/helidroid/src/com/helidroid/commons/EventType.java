package com.helidroid.commons;

/**
* @author Amir Lazarovich
*/
public enum EventType {
    LEFT_STICK("left_stick"),
    RIGHT_STICK("right_stick"),
    POWER("power"),
    TAKE_PICTURE("take_picture"),
    TOGGLE_MUSIC("toggle_music"),
    KEEP_ALIVE("keep_alive"),
    UNKNOWN("");


    private String mValue;

    EventType(String value) {
        mValue = value;
    }

    public static EventType getByValue(String value) {
        EventType event = UNKNOWN;
        for (EventType candidate : values()) {
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
