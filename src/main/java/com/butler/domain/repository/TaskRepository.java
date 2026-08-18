package com.butler.domain.repository;

import com.butler.domain.model.Task;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    Task save(Task task);
    void delete(Task task);
    Optional<Task> findById(Long id);
    List<Task> findBySubSessionId(Long subSessionId);
    java.util.Optional<Instant> findLastUpdatedAt(Long subSessionId);
    /** 未完成、已过执行日的周期任务（需滚动到今天继续提醒）。 */
    List<Task> findOverdueRecurring(LocalDate today);
    List<Task> findDueReminders(Instant now);
    void deleteBySubSessionId(Long subSessionId);
    int deleteBySubSessionIdAndFocusArea(Long subSessionId, String focusArea);

    /** 删除前先归档真实用户数据；测试用户直接硬删。 */
    void archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason);
    int archiveAndDeleteBySubSessionIdAndFocusArea(Long subSessionId, String focusArea, Long userId, String reason);
    void archiveAndDelete(Task task, Long userId, String reason);
}
