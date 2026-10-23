package com.emi.model.reposetory;

import com.emi.model.entity.Users;
import com.emi.utils.enums.Role;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<Users, String> {

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    Optional<Users> findByPhoneNumber(String phoneNumber);

    List<Users> findAllByRole(Role role, Sort sort);
}
