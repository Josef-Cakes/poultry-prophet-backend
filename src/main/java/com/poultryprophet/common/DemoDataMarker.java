package com.poultryprophet.common;

/** Marker used on the existing, explicitly documented demonstration purchases. */
public final class DemoDataMarker {
    public static final String PREFIX = "DEMO / TEST DATA;";
    private DemoDataMarker() {}

    public static boolean isMarked(String description) {
        return description != null && description.stripLeading().regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }
}
