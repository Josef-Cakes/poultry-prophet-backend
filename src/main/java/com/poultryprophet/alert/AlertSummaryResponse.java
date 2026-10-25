package com.poultryprophet.alert;

public record AlertSummaryResponse(long activeTotal, long critical, long warning, long info) {}
