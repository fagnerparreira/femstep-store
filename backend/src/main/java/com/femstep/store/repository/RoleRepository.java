package com.femstep.store.repository;

import com.femstep.store.domain.Role;
import com.femstep.store.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    
    Optional<Role> findByName(RoleName name);
    
    boolean existsByName(RoleName name);
}
