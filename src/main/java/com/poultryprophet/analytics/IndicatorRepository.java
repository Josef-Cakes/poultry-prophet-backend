package com.poultryprophet.analytics;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IndicatorRepository extends JpaRepository<Indicator, Long> {

    Optional<Indicator> findByRecordId(Long recordId);

    Optional<Indicator> findFirstByBatchIdOrderByComputedAtDesc(Long batchId);

    @Query("select i from Indicator i join fetch i.record r where i.batch.id = :batchId "
            + "order by r.recordDate desc, i.id desc")
    List<Indicator> findByBatchIdOrderByObservationDateDesc(@Param("batchId") Long batchId, Pageable pageable);

    List<Indicator> findByBatchIdOrderByComputedAtDesc(Long batchId, Pageable pageable);

    List<Indicator> findByBatchIdAndComputedAtBetweenOrderByComputedAtAsc(Long batchId,
                                                                          Instant start,
                                                                          Instant end);

    @Query("select avg(i.bhi) from Indicator i where i.batch.id = :batchId "
            + "and i.computedAt between :start and :end")
    Double avgBhiBetween(@Param("batchId") Long batchId,
                         @Param("start") Instant start,
                         @Param("end") Instant end);

    @Query("select avg(i.wfr) from Indicator i where i.batch.id = :batchId "
            + "and i.computedAt between :start and :end")
    Double avgWfrBetween(@Param("batchId") Long batchId,
                         @Param("start") Instant start,
                         @Param("end") Instant end);
}
