package com.butler.application;

import java.util.List;

/**
 * 一次用户输入即将引发的结构化变更预览（修改前弹窗确认用）。
 * 覆盖四类影响：对话记忆、关注项、目标定制信息（collected 字段/头部）、待办任务。
 */
public record ChangePreview(
        String proposalId,
        List<FieldChange> fields,
        List<String> focusAdded,
        List<String> focusRemoved,
        List<TaskChange> tasksAdded,
        List<TaskChange> tasksUpdated,
        List<String> tasksRemoved,
        List<String> tasksCompleted,
        List<TaskChange> tasksPlanned,
        List<String> memories,
        String note
) {
    public boolean isEmpty() {
        return fields.isEmpty() && focusAdded.isEmpty() && focusRemoved.isEmpty()
                && tasksAdded.isEmpty() && tasksUpdated.isEmpty() && tasksRemoved.isEmpty()
                && tasksCompleted.isEmpty() && (tasksPlanned == null || tasksPlanned.isEmpty())
                && memories.isEmpty();
    }

    /** collected 字段变更：label 旧值 -> 新值。 */
    public record FieldChange(String key, String label, String oldValue, String newValue) {}

    /** 待办新增/改期/拟新建预览。oldDueDate/oldRemindTime 仅更新时有值。 */
    public record TaskChange(String title, String focusArea, String remindDate,
                             String dueDate, String oldDueDate, String recurrence,
                             String remindTime, String detail, String oldRemindTime) {
        public TaskChange(String title, String focusArea, String remindDate, String dueDate, String oldDueDate) {
            this(title, focusArea, remindDate, dueDate, oldDueDate, null, null, null, null);
        }
        public TaskChange(String title, String focusArea, String remindDate, String dueDate, String oldDueDate,
                          String recurrence, String remindTime, String detail) {
            this(title, focusArea, remindDate, dueDate, oldDueDate, recurrence, remindTime, detail, null);
        }
    }
}
