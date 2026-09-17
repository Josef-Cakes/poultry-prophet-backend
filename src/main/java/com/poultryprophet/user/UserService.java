package com.poultryprophet.user;

import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.farm.FarmRepository;
import com.poultryprophet.user.dto.CreateHandlerRequest;
import com.poultryprophet.user.dto.HandlerResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FarmRepository farmRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       FarmRepository farmRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.farmRepository = farmRepository;
    }

    @Transactional
    public HandlerResponse createHandler(CreateHandlerRequest request, Long farmId) {
        requireValidFarm(farmId);
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(Role.HANDLER);
        user.setFarmId(farmId);
        userRepository.save(user);
        return HandlerResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<HandlerResponse> listHandlers(Long farmId) {
        requireValidFarm(farmId);
        return userRepository.findByRoleAndFarmId(Role.HANDLER, farmId).stream()
                .map(HandlerResponse::from)
                .toList();
    }
    private void requireValidFarm(Long farmId) {
        if (farmId == null || !farmRepository.existsById(farmId)) {
            throw new BadRequestException("A valid farm is required to manage handlers");
        }
    }

    /** Update the caller's own display name and login email. */
    @Transactional
    public User updateProfile(Long userId, String fullName, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
        // Email is the login identifier, so it must stay unique across users.
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        return userRepository.save(user);
    }

    /** Change the caller's own password after verifying the current one. */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
