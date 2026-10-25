package com.poultryprophet.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OfflineSyncReceiptRepository extends JpaRepository<OfflineSyncReceipt, UUID> {
    Optional<OfflineSyncReceipt> findByOperationIdAndFarmId(UUID operationId, Long farmId);
}
