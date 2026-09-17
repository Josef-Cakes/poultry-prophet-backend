package com.poultryprophet.invite;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HandlerInviteRepository extends JpaRepository<HandlerInvite, Long> {

    Optional<HandlerInvite> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from HandlerInvite i where i.token = :token")
    Optional<HandlerInvite> findByTokenForUpdate(@Param("token") String token);

    /** Pending invites for an email: not accepted, not declined and not expired. */
    List<HandlerInvite> findByEmailIgnoreCaseAndUsedAtIsNullAndDeclinedAtIsNullAndExpiresAtAfter(
            String email, Instant now);
}
