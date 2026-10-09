package com.poultryprophet.batch;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchLifecycleAuditRepository extends JpaRepository<BatchLifecycleAudit, Long> {
}
