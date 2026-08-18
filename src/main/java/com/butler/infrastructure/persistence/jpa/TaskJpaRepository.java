package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.TaskPO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TaskJpaRepository extends JpaRepository<TaskPO, Long> {
    List<TaskPO> findBySubSessionId(Long subSessionId);
    List<TaskPO> findBySubSessionIdAndFocusArea(Long subSessionId, String focusArea);
    List<TaskPO> findByCompletedFalseAndRemindedFalseAndRemindAtBefore(Instant now);
    @org.springframework.data.jpa.repository.Query("select t from TaskPO t where t.completed = false and t.recurrence is not null "
            + "and t.recurrence <> '' and t.dueDate < :today")
    List<TaskPO> findOverdueRecurring(@org.springframework.data.repository.query.Param("today") LocalDate today);
    @org.springframework.data.jpa.repository.Query("select max(t.updatedAt) from TaskPO t where t.subSessionId = :sid")
    Instant findLastUpdatedAt(@org.springframework.data.repository.query.Param("sid") Long sid);
    @Transactional
    void deleteBySubSessionId(Long subSessionId);
    @Transactional
    int deleteBySubSessionIdAndFocusArea(Long subSessionId, String focusArea);
}
