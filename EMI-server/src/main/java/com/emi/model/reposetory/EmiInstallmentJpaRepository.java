package com.emi.model.reposetory;

import com.emi.model.entity.EmiInstallment;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface EmiInstallmentJpaRepository extends JpaRepository<EmiInstallment, String> {

    List<EmiInstallment> findAllByFinanceIdOrderByInstallmentNo(String financeId);

    List<EmiInstallment> findAllByFinanceIdIn(Collection<String> financeIds);

    @Query("""
            select i, f, c from EmiInstallment i, Finance f, Customer c
            where i.financeId = f.financeId and f.customerId = c.customerId
              and f.status not in :excludedFinanceStatuses
              and i.status in :statuses
              and i.dueDate between :fromDate and :toDate
              and (:allCustomers = true or c.assignedStaffId is null or c.assignedStaffId = :staffId)
              and (lower(c.fullName) like :pattern or c.phoneNumber like :pattern or lower(f.financeNumber) like :pattern)
            """)
    List<Object[]> findWorklist(@Param("excludedFinanceStatuses") Collection<FinanceStatus> excludedFinanceStatuses,
                                @Param("statuses") Collection<InstallmentStatus> statuses,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("allCustomers") boolean allCustomers,
                                @Param("staffId") String staffId,
                                @Param("pattern") String pattern);

    @Query("""
            select i, f from EmiInstallment i, Finance f
            where i.financeId = f.financeId
              and f.status not in (com.emi.utils.enums.FinanceStatus.CLOSED, com.emi.utils.enums.FinanceStatus.CANCELLED, com.emi.utils.enums.FinanceStatus.FORECLOSED)
              and f.subscriptionId is not null
              and i.status not in (com.emi.utils.enums.InstallmentStatus.PAID, com.emi.utils.enums.InstallmentStatus.WAIVED, com.emi.utils.enums.InstallmentStatus.CANCELLED)
              and i.dueDate < :asOfDate
            order by f.financeId, i.installmentNo
            """)
    List<Object[]> findOverdueInstallmentsWithSubscription(@Param("asOfDate") LocalDate asOfDate);
}
