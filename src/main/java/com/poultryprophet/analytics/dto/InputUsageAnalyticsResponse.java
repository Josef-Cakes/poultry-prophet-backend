package com.poultryprophet.analytics.dto;

import java.util.Map;

public record InputUsageAnalyticsResponse(long totalLogs, Map<String, Long> byProductType,
                                          Map<String, Long> byBrand) {}
