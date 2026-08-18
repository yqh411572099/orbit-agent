package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.Task;
import com.butler.domain.repository.TaskRepository;
import com.butler.infrastructure.persistence.archive.ArchiveRecorder;
import com.butler.infrastructure.persistence.jpa.TaskJpaRepository;
import com.butler.infrastructure.persistence.po.TaskPO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRepositoryAdapter implements TaskRepository {
    private final TaskJpaRepository jpa;
    private final ArchiveRecorder archiveRecorder;

    public TaskRepositoryAdapter(TaskJpaRepository jpa, ArchiveRecorder archiveRecorder) {
        this.jpa = jpa;
        this.archiveRecorder = archiveRecorder;
    }

    @Override
    public Task save(Task t) {
        TaskPO po = new TaskPO();
        po.setId(t.getId());
        po.setSubSessionId(t.getSubSessionId());
        po.setContent(t.getContent());
        po.setFocusArea(t.getFocusArea());
        po.setDetail(t.getDetail());
        po.setModuleKey(t.getModuleKey());
        po.setMilestoneKey(t.getMilestoneKey());
        po.setRecurrence(t.getRecurrence());
        po.setNextHint(t.getNextHint());
        po.setCompleted(t.isCompleted());
        po.setRemindAt(t.getRemindAt());
        po.setDueDate(t.getDueDate());
        po.setReminded(t.isReminded());
        TaskPO saved = jpa.save(po);
        return toDomain(saved);
    }

    @Override
    public Optional<Task> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<Task> findBySubSessionId(Long subSessionId) {
        return jpa.findBySubSessionId(subSessionId).stream().map(this::toDomain).toList();
    }

    @Override
    public java.util.Optional<Instant> findLastUpdatedAt(Long subSessionId) {
        return java.util.Optional.ofNullable(jpa.findLastUpdatedAt(subSessionId));
    }

    @Override
    public List<Task> findDueReminders(Instant now) {
        return jpa.findByCompletedFalseAndRemindedFalseAndRemindAtBefore(now).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<Task> findOverdueRecurring(LocalDate today) {
        return jpa.findOverdueRecurring(today).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(Task t) {
        if (t.getId() != null) jpa.deleteById(t.getId());
    }

    @Override
    public void deleteBySubSessionId(Long subSessionId) {
        jpa.deleteBySubSessionId(subSessionId);
    }

    @Override
    public int deleteBySubSessionIdAndFocusArea(Long subSessionId, String focusArea) {
        return jpa.deleteBySubSessionIdAndFocusArea(subSessionId, focusArea);
    }

    @Override
    public void archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason) {
        List<TaskPO> rows = jpa.findBySubSessionId(subSessionId);
        archiveRecorder.archiveAll("task", rows, userId, subSessionId, reason);
        jpa.deleteBySubSessionId(subSessionId);
    }

    @Override
    public int archiveAndDeleteBySubSessionIdAndFocusArea(Long subSessionId, String focusArea, Long userId, String reason) {
        List<TaskPO> rows = jpa.findBySubSessionIdAndFocusArea(subSessionId, focusArea);
        archiveRecorder.archiveAll("task", rows, userId, subSessionId, reason);
        return jpa.deleteBySubSessionIdAndFocusArea(subSessionId, focusArea);
    }

    @Override
    public void archiveAndDelete(Task task, Long userId, String reason) {
        if (task.getId() == null) return;
        jpa.findById(task.getId()).ifPresent(po ->
                archiveRecorder.archive("task", po, userId, task.getSubSessionId(), reason));
        jpa.deleteById(task.getId());
    }

    private Task toDomain(TaskPO po) {
        return new Task(po.getId(), po.getSubSessionId(), po.getContent(), po.getDetail(), po.getNextHint(),
                po.getModuleKey(), po.getMilestoneKey(), po.getRecurrence(), po.getFocusArea(),
                po.isCompleted(), po.getRemindAt(), po.getDueDate(), po.isReminded());
    }
}
