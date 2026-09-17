package com.poultryprophet.sync;

import com.poultryprophet.record.DailyRecord;
import com.poultryprophet.sync.dto.ConflictType;
import com.poultryprophet.sync.dto.SyncItemRequest;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * SDD 1.2: detects duplicate (batch, date) submissions and classifies the conflict as
 * NEW, IDENTICAL, LOCAL_NEWER, or SERVER_NEWER.
 */
@Component
public class ConflictResolver {

    public ConflictType classify(DailyRecord existing, SyncItemRequest incoming) {
        if (existing == null) {
            return ConflictType.NEW;
        }
        if (isIdentical(existing, incoming)) {
            return ConflictType.IDENTICAL;
        }
        // The client edited the entry while offline more recently than the server copy.
        if (incoming.updatedAt().isAfter(existing.getUpdatedAt())) {
            return ConflictType.LOCAL_NEWER;
        }
        return ConflictType.SERVER_NEWER;
    }

    private boolean isIdentical(DailyRecord existing, SyncItemRequest incoming) {
        return Double.compare(existing.getTemperatureC(), incoming.temperatureC()) == 0
                && existing.getMortalityCount() == (incoming.mortalityCount() == null
                    ? 0 : incoming.mortalityCount())
                && Double.compare(existing.getFeedIntakeG(), incoming.feedIntakeG()) == 0
                && Double.compare(existing.getWaterIntakeMl(), incoming.waterIntakeMl()) == 0
                && Objects.equals(normalize(existing.getBehaviorNotes()), normalize(incoming.behaviorNotes()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
