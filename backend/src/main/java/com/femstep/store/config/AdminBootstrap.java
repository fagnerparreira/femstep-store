package com.femstep.store.config;

import com.femstep.store.domain.Role;
import com.femstep.store.domain.RoleName;
import com.femstep.store.domain.User;
import com.femstep.store.repository.RoleRepository;
import com.femstep.store.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.bootstrap.email:}") String adminEmail,
            @Value("${admin.bootstrap.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() && adminPassword.isBlank()) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL e ADMIN_PASSWORD (mínimo 12 caracteres) devem ser definidos juntos"
            );
        }
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("A função ROLE_ADMIN não existe"));
        User admin = userRepository.findByEmail(adminEmail).orElseGet(() -> {
            User created = new User();
            created.setEmail(adminEmail);
            created.setFirstName("Admin");
            created.setLastName("FemStep");
            created.setActive(true);
            created.setPassword(passwordEncoder.encode(adminPassword));
            return created;
        });
        if (admin.getRoles().stream().noneMatch(role -> role.getName() == RoleName.ROLE_ADMIN)) {
            admin.addRole(adminRole);
        }
        userRepository.save(admin);
    }
}
