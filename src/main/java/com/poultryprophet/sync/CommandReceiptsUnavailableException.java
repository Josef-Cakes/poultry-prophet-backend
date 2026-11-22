package com.poultryprophet.sync;

/** Transient rollout/storage failure: never turn a queued command into a rejected write. */
public class CommandReceiptsUnavailableException extends RuntimeException {
    public CommandReceiptsUnavailableException() {
        super("Safe command syncing is not enabled on this server yet. Keep the queued record and contact the farm manager.");
    }
}
