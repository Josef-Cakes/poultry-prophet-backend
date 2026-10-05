package com.poultryprophet.config;

import com.poultryprophet.farm.Farm;
import com.poultryprophet.farm.FarmRepository;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Provisions local validation accounts after a snapshot restore. It is never active outside the
 * validation Spring profile and never creates accounts in a production deployment.
 */
@Component
@Profile("validation")
public class ValidationAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ValidationAccountSeeder.class);

    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final PasswordEncoder passwordEncoder;
    private final String managerEmail;
    private final String managerPassword;
    private final String handlerEmail;
    private final String handlerPassword;
    private final Long configuredFarmId;

    public ValidationAccountSeeder(UserRepository userRepository,
                                   FarmRepository farmRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${validation.manager.email:}") String managerEmail,
                                   @Value("${validation.manager.password:}") String managerPassword,
                                   @Value("${validation.handler.email:}") String handlerEmail,
                                   @Value("${validation.handler.password:}") String handlerPassword,
                                   @Value("${validation.farm-id:}") String configuredFarmId) {
        this.userRepository = userRepository;
        this.farmRepository = farmRepository;
        this.passwordEncoder = passwordEncoder;
        this.managerEmail = managerEmail;
        this.managerPassword = managerPassword;
        this.handlerEmail = handlerEmail;
        this.handlerPassword = handlerPassword;
        this.configuredFarmId = configuredFarmId == null || configuredFarmId.isBlank()
                ? null : Long.valueOf(configuredFarmId);
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (managerEmail.isBlank() || managerPassword.isBlank()
                || handlerEmail.isBlank() || handlerPassword.isBlank()) {
            throw new IllegalStateException(
                    "Validation accounts are required; set VALIDATION_MANAGER_EMAIL/PASSWORD and "
                            + "VALIDATION_HANDLER_EMAIL/PASSWORD");
        }

        Farm farm = resolveFarm();
        ensureUser(managerEmail, managerPassword, "Validation Manager", Role.MANAGER, farm.getId());
        ensureUser(handlerEmail, handlerPassword, "Validation Handler", Role.HANDLER, farm.getId());
        log.info("Validation accounts are ready for local farm {}", farm.getId());
    }

    private Farm resolveFarm() {
        if (configuredFarmId != null) {
            return farmRepository.findById(configuredFarmId)
                    .orElseThrow(() -> new IllegalStateException(
                            "VALIDATION_FARM_ID does not exist: " + configuredFarmId));
        }
        List<Farm> farms = farmRepository.findAll();
        if (!farms.isEmpty()) return farms.get(0);
        Farm farm = new Farm();
        farm.setName("Local validation farm");
        farm.setDescription("Disposable local validation data; not a production farm.");
        return farmRepository.save(farm);
    }

    private void ensureUser(String email, String password, String fullName, Role role, Long farmId) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setEmail(email.trim().toLowerCase());
        user.setFullName(fullName);
        user.setRole(role);
        user.setFarmId(farmId);
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
    }
}
