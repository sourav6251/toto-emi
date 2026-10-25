package com.emi.model.reposetory;

import com.emi.model.entity.Users;
import com.emi.utils.enums.Role;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<Users, String> {

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    Optional<Users> findByPhoneNumber(String phoneNumber);

    List<Users> findAllByRole(Role role, Sort sort);

    @Query("""
            SELECT u FROM Users u
            WHERE u.phoneNumber LIKE CONCAT('%', :pattern, '%')
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :pattern, '%'))
            """)
    List<Users> searchByPhoneOrName(@Param("pattern") String pattern);
}
