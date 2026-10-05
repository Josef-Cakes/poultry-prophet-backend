package com.poultryprophet.event;

/**
 * Keeps the existing event request contract compatible while allowing the optional
 * sale-purpose value to travel through the existing tags field.
 */
public final class SalePurposeParser {
    private static final String PREFIX = "SALE_PURPOSE:";

    private SalePurposeParser() {
    }

    public static SalePurpose parse(EventType eventType, String tags) {
        if (eventType != EventType.SALE || tags == null) return null;
        for (String tag : tags.split(",")) {
            String value = tag.trim();
            if (!value.startsWith(PREFIX)) continue;
            try {
                return SalePurpose.valueOf(value.substring(PREFIX.length()).trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
