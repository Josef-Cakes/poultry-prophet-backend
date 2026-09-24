package com.poultryprophet.invite;

import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.farm.FarmRepository;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class InviteService {

    private final HandlerInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final FarmRepository farmRepository;

    public InviteService(HandlerInviteRepository inviteRepository, UserRepository userRepository,
                         FarmRepository farmRepository) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.farmRepository = farmRepository;
    }

    @Transactional
    public InviteResponse createHandlerInvite(String email, Long farmId, int expiresInDays) {
        requireValidFarm(farmId);
        // The invitee must already be a registered handler: acceptance is gated to
        // the HANDLER role and matches on email, so an invite to a non-existent or
        // non-handler address could never be accepted.
        User invitee = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No account exists for " + email));
        if (invitee.getRole() != Role.HANDLER) {
            throw new BadRequestException("Only handler accounts can be invited to a farm");
        }
        if (invitee.getFarmId() != null) {
            throw new BadRequestException("This handler is already assigned to a farm");
        }

        HandlerInvite invite = new HandlerInvite();
        invite.setToken(UUID.randomUUID().toString());
        invite.setEmail(email);
        invite.setFarmId(farmId);
        invite.setExpiresAt(Instant.now().plus(expiresInDays, ChronoUnit.DAYS));
        inviteRepository.save(invite);

        return new InviteResponse(invite.getToken(), invite.getEmail(), invite.getFarmId(), invite.getExpiresAt());
    }

    /**
     * Invites awaiting a decision for the given handler. Invites to a farm the
     * handler already belongs to are omitted — there's nothing to accept there.
     */
    @Transactional(readOnly = true)
    public List<InviteResponse> pendingInvitesFor(String email, Long currentFarmId) {
        return inviteRepository
                .findByEmailIgnoreCaseAndUsedAtIsNullAndDeclinedAtIsNullAndExpiresAtAfter(email, Instant.now())
                .stream()
                .filter(i -> !i.getFarmId().equals(currentFarmId))
                .map(i -> new InviteResponse(i.getToken(), i.getEmail(), i.getFarmId(), i.getExpiresAt()))
                .toList();
    }

    /** Decline a pending invite so it stops appearing for the handler. */
    @Transactional
    public void declineInvite(String token, String email) {
        HandlerInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid invite token"));

        if (!invite.getEmail().equalsIgnoreCase(email)) {
            throw new BadRequestException("Invite token does not match the email address");
        }

        if (invite.getUsedAt() != null) {
            throw new BadRequestException("Invite token has already been used");
        }

        if (invite.getDeclinedAt() == null) {
            invite.setDeclinedAt(Instant.now());
            inviteRepository.save(invite);
        }
    }

    @Transactional
    public User acceptInvite(String token, String email) {
        HandlerInvite invite = inviteRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new BadRequestException("Invalid invite token"));

        if (!invite.getEmail().equalsIgnoreCase(email)) {
            throw new BadRequestException("Invite token does not match the email address");
        }

        if (invite.getUsedAt() != null) {
            throw new BadRequestException("Invite token has already been used");
        }

        if (invite.getDeclinedAt() != null) {
            throw new BadRequestException("Invite has been declined");
        }

        if (!invite.getExpiresAt().isAfter(Instant.now())) {
            throw new BadRequestException("Invite token has expired");
        }

        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (user.getRole() != Role.HANDLER) {
            throw new BadRequestException("Only handler accounts can accept farm invitations");
        }
        if (user.getFarmId() != null) {
            throw new BadRequestException("This handler is already assigned to a farm");
        }
        requireValidFarm(invite.getFarmId());

        invite.setUsedAt(Instant.now());
        inviteRepository.save(invite);
        user.setFarmId(invite.getFarmId());
        return userRepository.save(user);
    }

    private void requireValidFarm(Long farmId) {
        if (farmId == null || !farmRepository.existsById(farmId)) {
            throw new BadRequestException("Invitation target farm does not exist");
        }
    }
}
