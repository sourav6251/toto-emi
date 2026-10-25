package com.emi.model.reposetory;

import com.emi.model.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerJpaRepository extends JpaRepository<Customer, String> {

    Optional<Customer> findByUserId(String userId);

    boolean existsByPhoneNumber(String phoneNumber);

    long countByAssignedStaffId(String assignedStaffId);

    Optional<Customer> findByPhoneNumber(String phoneNumber);
}
