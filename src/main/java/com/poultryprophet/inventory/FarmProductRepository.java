package com.poultryprophet.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FarmProductRepository extends JpaRepository<FarmProduct, Long> {
    List<FarmProduct> findByFarmIdAndActiveOrderByBrandNameAsc(Long farmId, boolean active);
    List<FarmProduct> findByFarmIdOrderByActiveDescBrandNameAsc(Long farmId);
    Optional<FarmProduct> findByIdAndFarmId(Long id, Long farmId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FarmProduct p where p.id = :id and p.farmId = :farmId")
    Optional<FarmProduct> findByIdAndFarmIdForUpdate(@Param("id") Long id, @Param("farmId") Long farmId);
}
