package com.example.dilun.polls.Repositories;

import com.example.dilun.polls.models.Role;
import com.example.dilun.polls.models.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName roleName);
}
