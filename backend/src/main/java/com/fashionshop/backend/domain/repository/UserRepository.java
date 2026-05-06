package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(com.fashionshop.backend.common.enums.Role role);

    long countByStatus(com.fashionshop.backend.common.enums.UserStatus status);
}
