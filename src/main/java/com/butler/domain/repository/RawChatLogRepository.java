package com.butler.domain.repository;

import com.butler.domain.model.RawChatLog;
import java.time.Instant;
import java.util.List;

public interface RawChatLogRepository {
    RawChatLog save(RawChatLog log);
    List<RawChatLog> findByUserIdAndCreatedAtBetween(Long userId, Instant from, Instant to);
    /** 最近若干条消息（按 id 倒序查，调用方按需再正序）。subSessionId 为 null 表示主对话。 */
    List<RawChatLog> findRecent(Long userId, com.butler.domain.model.SessionType type, Long subSessionId, int limit);
    /** id 大于 afterId 的新增消息（正序）。 */
    List<RawChatLog> findNewer(Long userId, com.butler.domain.model.SessionType type, Long subSessionId, Long afterId);
    /** id 小于 beforeId 的更早消息（倒序，最多 limit）。 */
    List<RawChatLog> findOlder(Long userId, com.butler.domain.model.SessionType type, Long subSessionId, Long beforeId, int limit);
    int deleteBySubSessionId(Long subSessionId);
    int deleteByUserId(Long userId);

    int archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason);
    int archiveAndDeleteByUserId(Long userId, String reason);
    /** 归档并删除某子会话中 id >= fromId 的对话（含 fromId），返回删除条数。 */
    int archiveAndDeleteSubSessionFromId(Long subSessionId, Long userId, Long fromId, String reason);
}
