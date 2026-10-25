package com.emi.model.reposetory;

import com.emi.model.entity.Finance;
import com.emi.utils.enums.FinanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FinanceJpaRepository extends JpaRepository<Finance, String> {

    boolean existsByDoNumber(String doNumber);

    @Query("select count(f) > 0 from Finance f where upper(replace(f.doNumber, ' ', '')) = :key")
    boolean existsByDoNumberKey(@Param("key") String key);

    Optional<Finance> findByFinanceNumber(String financeNumber);

    List<Finance> findAllByCustomerId(String customerId);

    List<Finance> findAllByCustomerIdIn(Collection<String> customerIds);

    @Query("""
            select f, c, a from Finance f, Customer c, Asset a
            where f.customerId = c.customerId and f.assetId = a.assetId
            order by f.createdAt desc
            """)
    List<Object[]> searchAll();

    @Query("""
            select f, c, a from Finance f, Customer c, Asset a
            where f.customerId = c.customerId and f.assetId = a.assetId
              and f.createdAt >= :start
            order by f.createdAt desc
            """)
    List<Object[]> searchFrom(@Param("start") OffsetDateTime start);

    @Query("""
            select f, c, a from Finance f, Customer c, Asset a
            where f.customerId = c.customerId and f.assetId = a.assetId
              and f.createdAt < :end
            order by f.createdAt desc
            """)
    List<Object[]> searchTo(@Param("end") OffsetDateTime end);

    @Query("""
            select f, c, a from Finance f, Customer c, Asset a
            where f.customerId = c.customerId and f.assetId = a.assetId
              and f.createdAt >= :start
              and f.createdAt < :end
            order by f.createdAt desc
            """)
    List<Object[]> searchBetween(@Param("start") OffsetDateTime start,
                                 @Param("end") OffsetDateTime end);

    // Finance list, every customer's loans, each row as [Finance, Customer, Asset], newest first.
    // start/end bound f.createdAt and are inclusive/exclusive respectively; either may be null.
    default List<Object[]> search(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null) {
            return searchBetween(start, end);
        } else if (start != null) {
            return searchFrom(start);
        } else if (end != null) {
            return searchTo(end);
        } else {
            return searchAll();
        }
    }

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

    @Query(value = "SELECT nextval('finance_number_seq')", nativeQuery = true)
    long nextFinanceNumber();

    @Query("""
            select count(f) > 0 from Finance f, Customer c
            where f.customerId = c.customerId
              and f.financeId = :financeId
              and c.userId = :userId
            """)
    boolean isUsersFinance(@Param("userId") String userId, @Param("financeId") String financeId);
}
