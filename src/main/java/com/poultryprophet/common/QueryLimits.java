package com.poultryprophet.common;

/** Keeps client-controlled list sizes valid and bounded before creating a Pageable. */
public final class QueryLimits {

    public static final int MAX_PAGE_SIZE = 100;

    private QueryLimits() {
    }

    public static int clamp(int requested) {
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
