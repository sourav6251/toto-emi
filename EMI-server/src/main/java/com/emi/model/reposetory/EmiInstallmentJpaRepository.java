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

    // Candidates for the collection worklist, each row as [EmiInstallment, Finance, Customer].
    // allCustomers=false limits to the staff member's own customers plus unassigned ones; pattern is a lowercase LIKE pattern.
    // Grace days are per finance, so the exact status is decided by the caller.
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
}
