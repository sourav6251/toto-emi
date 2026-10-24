package com.emi.model.reposetory;

import com.emi.model.entity.Payment;
import com.emi.utils.enums.PaymentMethod;
import com.emi.utils.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByPaymentNumber(String paymentNumber);

    Optional<Payment> findFirstByTxnId(String txnId);

    Optional<Payment> findFirstByReference(String reference);

    Optional<Payment> findFirstByFinanceIdAndPaymentDateAndAmountAndStatusNot(
            String financeId, LocalDate paymentDate, BigDecimal amount, PaymentStatus status);

    boolean existsByMethodAndTxnIdAndStatusNot(PaymentMethod method, String txnId, PaymentStatus status);

    List<Payment> findAllByFinanceIdOrderByPaymentDateDescCreatedAtDesc(String financeId);

    List<Payment> findAllByFinanceIdInAndStatus(Collection<String> financeIds, PaymentStatus status);

    @Query("""
            select sum(p.amount) from Payment p, Customer c
            where p.customerId = c.customerId and p.status = com.emi.utils.enums.PaymentStatus.VERIFIED
              and p.paymentDate between :fromDate and :toDate
              and (:allCustomers = true or c.assignedStaffId = :staffId)
            """)
    BigDecimal sumVerifiedOrNull(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                 @Param("allCustomers") boolean allCustomers, @Param("staffId") String staffId);

    default BigDecimal sumVerified(LocalDate fromDate, LocalDate toDate, boolean allCustomers, String staffId) {
        BigDecimal sum = sumVerifiedOrNull(fromDate, toDate, allCustomers, staffId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Query("""
            select count(p) from Payment p, Customer c
            where p.customerId = c.customerId and p.status = com.emi.utils.enums.PaymentStatus.VERIFIED
              and p.paymentDate between :fromDate and :toDate
              and (:allCustomers = true or c.assignedStaffId = :staffId)
            """)
    long countVerified(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                       @Param("allCustomers") boolean allCustomers, @Param("staffId") String staffId);

    @Query(value = "SELECT nextval('payment_number_seq')", nativeQuery = true)
    long nextPaymentNumber();

    @Query("""
            select p, f, c from Payment p, Finance f, Customer c
            where p.financeId = f.financeId and p.customerId = c.customerId
              and (:allStatuses = true or p.status = :status)
              and p.paymentDate between :fromDate and :toDate
            order by p.paymentDate desc, p.createdAt desc
            """)
    List<Object[]> search(@Param("allStatuses") boolean allStatuses,
                          @Param("status") PaymentStatus status,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate);

    long countByStatus(PaymentStatus status);

    @Query("""
            select count(p) from Payment p, Customer c
            where p.customerId = c.customerId and p.status = com.emi.utils.enums.PaymentStatus.PENDING
              and (:allCustomers = true or c.assignedStaffId is null or c.assignedStaffId = :staffId)
            """)
    long countPending(@Param("allCustomers") boolean allCustomers, @Param("staffId") String staffId);
}
