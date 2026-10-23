package com.emi.model.reposetory;

import com.emi.model.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PaymentAllocationJpaRepository extends JpaRepository<PaymentAllocation, String> {

    List<PaymentAllocation> findAllByPaymentIdIn(Collection<String> paymentIds);
}
