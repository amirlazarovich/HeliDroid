package com.helidroid.commons;

/**
* @author Amir Lazarovich
*/
public enum EventType {
    POWER("power"),
    ORIENTATION("orientation"),
    TILT_UP_DOWN("tilt_up_down"),
    TILT_LEFT_RIGHT("tilt_left_right"),
    TAKE_PICTURE("take_picture"),
    TOGGLE_MUSIC("toggle_music"),
    STAND_BY("stand_by"),
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
