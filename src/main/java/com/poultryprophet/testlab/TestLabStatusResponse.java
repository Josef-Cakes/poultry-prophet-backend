package com.poultryprophet.testlab;

public record TestLabStatusResponse(
        boolean enabled,
        String environment,
        String message
) {
}
