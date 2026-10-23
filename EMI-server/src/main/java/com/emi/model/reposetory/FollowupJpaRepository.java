package com.emi.model.reposetory;

import com.emi.model.entity.Followup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FollowupJpaRepository extends JpaRepository<Followup, String> {

    List<Followup> findAllByFinanceIdIn(Collection<String> financeIds);
}
