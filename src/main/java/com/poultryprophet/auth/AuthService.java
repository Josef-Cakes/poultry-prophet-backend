    package com.poultryprophet.auth;

    import com.poultryprophet.auth.dto.AuthResponse;
    import com.poultryprophet.auth.dto.LoginRequest;
    import com.poultryprophet.auth.dto.RegisterRequest;
    import com.poultryprophet.common.BadRequestException;
    import com.poultryprophet.farm.Farm;
    import com.poultryprophet.farm.FarmRepository;
    import com.poultryprophet.security.JwtService;
    import com.poultryprophet.user.User;
    import com.poultryprophet.user.UserRepository;
    import com.poultryprophet.user.Role;
    import org.springframework.security.authentication.AuthenticationManager;
    import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
    import org.springframework.security.core.AuthenticationException;
    import org.springframework.security.crypto.password.PasswordEncoder;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    @Service
    public class AuthService {

        private final UserRepository userRepository;
        private final FarmRepository farmRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;

        public AuthService(UserRepository userRepository,
                        FarmRepository farmRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuthenticationManager authenticationManager) {
            this.userRepository = userRepository;
            this.farmRepository = farmRepository;
            this.passwordEncoder = passwordEncoder;
            this.jwtService = jwtService;
            this.authenticationManager = authenticationManager;
        }

        @Transactional
        public AuthResponse register(RegisterRequest request) {
            if (userRepository.existsByEmail(request.email())) {
                throw new BadRequestException("Email already registered");
            }
            User user = new User();
            user.setEmail(request.email());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setFullName(request.fullName());
            // Public registration is intentionally manager-only. Handler accounts are
            // provisioned by a manager or accepted through the invitation flow.
            user.setRole(Role.MANAGER);
            Farm farm = farmRepository.save(new Farm());
            user.setFarmId(farm.getId());

            userRepository.save(user);
            return toResponse(user);
        }

        public AuthResponse login(LoginRequest request) {
            try {
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            } catch (AuthenticationException ex) {
                throw new BadRequestException("Invalid email or password");
            }
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new BadRequestException("Invalid email or password"));
            return toResponse(user);
        }

        public AuthResponse toResponse(User user) {
            return new AuthResponse(
                    jwtService.generateToken(user),
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole(),
                    user.getFarmId());
        }
    }
