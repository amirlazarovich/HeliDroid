package com.helidroid.commons;

/**
* @author Amir Lazarovich
*/
public enum EventType {
    UNKNOWN(""),
    LEFT_STICK("left_stick"),
    RIGHT_STICK("right_stick"),
    POWER("power"),
    TAKE_PICTURE("take_picture"),
    TOGGLE_MUSIC("toggle_music"),
    KEEP_ALIVE("keep_alive"),
    ACTION_STICKS("sticks"),
    ACTION_STANDBY("standby"),
    ACTION_TUNE("tune"),
    ACTION_TILT("tilt"),
    ACTION_CALIBRATE_TILT("calibrate_tilt"),
    ACTION_TILT_OFFSET("tilt_offset");


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
