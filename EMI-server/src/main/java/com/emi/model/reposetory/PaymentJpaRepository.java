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

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    // A rejected payment frees its reference for a corrected re-entry
    boolean existsByMethodAndTxnIdAndStatusNot(PaymentMethod method, String txnId, PaymentStatus status);

    List<Payment> findAllByFinanceIdOrderByPaymentDateDescCreatedAtDesc(String financeId);

    List<Payment> findAllByFinanceIdInAndStatus(Collection<String> financeIds, PaymentStatus status);

    // Verified money dated within [fromDate, toDate], for dashboards and reports.
    // allCustomers=false limits to the staff member's assigned customers.
    @Query("""
            select sum(p.amount) from Payment p, Customer c
            where p.customerId = c.customerId and p.status = com.emi.utils.enums.PaymentStatus.VERIFIED
              and p.paymentDate between :fromDate and :toDate
              and (:allCustomers = true or c.assignedStaffId = :staffId)
            """)
    BigDecimal sumVerifiedOrNull(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                 @Param("allCustomers") boolean allCustomers, @Param("staffId") String staffId);

    // SUM over no rows is null, so a period with no payments reads as zero
    default BigDecimal sumVerified(LocalDate fromDate, LocalDate toDate, boolean allCustomers, String staffId) {
        BigDecimal sum = sumVerifiedOrNull(fromDate, toDate, allCustomers, staffId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    // Verified payments dated within [fromDate, toDate] for the caller's scope, as a count
    @Query("""
            select count(p) from Payment p, Customer c
            where p.customerId = c.customerId and p.status = com.emi.utils.enums.PaymentStatus.VERIFIED
              and p.paymentDate between :fromDate and :toDate
              and (:allCustomers = true or c.assignedStaffId = :staffId)
            """)
    long countVerified(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                       @Param("allCustomers") boolean allCustomers, @Param("staffId") String staffId);

    // Backs the PAY-NNNNNN payment number and the RCT-YYYY-NNNNNN receipt number
    @Query(value = "SELECT nextval('payment_number_seq')", nativeQuery = true)
    long nextPaymentNumber();

    // Payment list for staff, each row as [Payment, Finance, Customer], newest first.
    // allCustomers=false limits to the staff member's own customers plus unassigned ones.
    // collectorId is only applied when mineOnly=true. Pass sentinel dates when a bound isn't wanted.
    @Query("""
            select p, f, c from Payment p, Finance f, Customer c
            where p.financeId = f.financeId and p.customerId = c.customerId
              and (:allCustomers = true or c.assignedStaffId is null or c.assignedStaffId = :staffId)
              and (:allStatuses = true or p.status = :status)
              and (:mineOnly = false or p.collectedBy = :staffId)
              and p.paymentDate between :fromDate and :toDate
            order by p.paymentDate desc, p.createdAt desc
            """)
    List<Object[]> search(@Param("allCustomers") boolean allCustomers,
                          @Param("staffId") String staffId,
                          @Param("allStatuses") boolean allStatuses,
                          @Param("status") PaymentStatus status,
                          @Param("mineOnly") boolean mineOnly,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate);

    // Pending payments in the caller's scope, ignoring every filter
    @Query("""
            select count(p) from Payment p, Customer c
            where p.customerId = c.customerId and p.status = com.emi.utils.enums.PaymentStatus.PENDING
              and (:allCustomers = true or c.assignedStaffId is null or c.assignedStaffId = :staffId)
            """)
    long countPending(@Param("allCustomers") boolean allCustomers, @Param("staffId") String staffId);
}
