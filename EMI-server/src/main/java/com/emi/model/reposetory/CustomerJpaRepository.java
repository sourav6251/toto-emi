package com.emi.model.reposetory;

import com.emi.model.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerJpaRepository extends JpaRepository<Customer, String> {

    Optional<Customer> findByUserId(String userId);

    boolean existsByPhoneNumberAndFullName(String phoneNumber, String fullName);

    long countByAssignedStaffId(String assignedStaffId);

    @Query("""
            select c from Customer c
            where (:allCustomers = true or c.assignedStaffId is null or c.assignedStaffId = :staffId)
              and (lower(c.fullName) like :pattern or c.phoneNumber like :pattern)
            """)
    Page<Customer> search(@Param("allCustomers") boolean allCustomers,
                          @Param("staffId") String staffId,
                          @Param("pattern") String pattern,
                          Pageable pageable);
}
