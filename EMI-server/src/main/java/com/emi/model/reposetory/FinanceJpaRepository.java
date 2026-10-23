package com.emi.model.reposetory;

import com.emi.model.entity.Finance;
import com.emi.utils.enums.FinanceStatus;
import com.emi.utils.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FinanceJpaRepository extends JpaRepository<Finance, String> {

    boolean existsByDoNumber(String doNumber);

    // key is the cleaned DO number with spaces removed; compares against every finance, closed and cancelled included
    @Query("select count(f) > 0 from Finance f where upper(replace(f.doNumber, ' ', '')) = :key")
    boolean existsByDoNumberKey(@Param("key") String key);

    Optional<Finance> findByFinanceNumber(String financeNumber);

    List<Finance> findAllByCustomerId(String customerId);

    List<Finance> findAllByCustomerIdIn(Collection<String> customerIds);

    // Finance list for staff, each row as [Finance, Customer, Asset], newest first.
    // allCustomers=false limits to the staff member's own customers plus unassigned ones;
    // pattern is a lowercase LIKE pattern; productType is ignored when allTypes=true.
    @Query("""
            select f, c, a from Finance f, Customer c, Asset a
            where f.customerId = c.customerId and f.assetId = a.assetId
              and (:allCustomers = true or c.assignedStaffId is null or c.assignedStaffId = :staffId)
              and (:allTypes = true or a.productType = :productType)
              and (lower(f.financeNumber) like :pattern or lower(c.fullName) like :pattern
                   or c.phoneNumber like :pattern or lower(f.doNumber) like :pattern
                   or lower(a.vehicleNumber) like :pattern)
            order by f.createdAt desc
            """)
    List<Object[]> search(@Param("allCustomers") boolean allCustomers,
                          @Param("staffId") String staffId,
                          @Param("allTypes") boolean allTypes,
                          @Param("productType") ProductType productType,
                          @Param("pattern") String pattern);

    // Finances with their customers, each row as [Finance, Customer], excluding the given statuses.
    // allCustomers=true ignores ownership; otherwise only the staff member's assigned customers, plus unassigned
    // ones when includeUnassigned=true.
    @Query("""
            select f, c from Finance f, Customer c
            where f.customerId = c.customerId
              and f.status not in :excludedStatuses
              and (:allCustomers = true or c.assignedStaffId = :staffId
                   or (:includeUnassigned = true and c.assignedStaffId is null))
            """)
    List<Object[]> findInScope(@Param("excludedStatuses") Collection<FinanceStatus> excludedStatuses,
                               @Param("allCustomers") boolean allCustomers,
                               @Param("staffId") String staffId,
                               @Param("includeUnassigned") boolean includeUnassigned);

    // Backs the FIN-YYYY-NNNNNN finance number
    @Query(value = "SELECT nextval('finance_number_seq')", nativeQuery = true)
    long nextFinanceNumber();
}
