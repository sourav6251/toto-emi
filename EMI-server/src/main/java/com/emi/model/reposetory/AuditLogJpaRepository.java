package com.emi.model.reposetory;

import com.emi.model.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, String> {

    // Newest first. action is ignored when allActions=true; userId when allUsers=true.
    @Query("""
            select a from AuditLog a
            where (:allActions = true or a.action = :action)
              and (:allUsers = true or a.userId = :userId)
            order by a.createdAt desc
            """)
    List<AuditLog> search(@Param("allActions") boolean allActions, @Param("action") String action,
                          @Param("allUsers") boolean allUsers, @Param("userId") String userId,
                          Pageable pageable);
}
