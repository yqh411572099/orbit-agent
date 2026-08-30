package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.RawChatLogPO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RawChatLogJpaRepository extends JpaRepository<RawChatLogPO, Long> {
    List<RawChatLogPO> findByUserIdAndCreatedAtBetween(Long userId, java.time.Instant from, java.time.Instant to);
    List<RawChatLogPO> findBySubSessionId(Long subSessionId);
    List<RawChatLogPO> findByUserId(Long userId);

    @Query("select r from RawChatLogPO r where r.userId = :uid and r.sessionType = :type "
            + "and (:subId is null and r.subSessionId is null or r.subSessionId = :subId) order by r.id desc")
    List<RawChatLogPO> findRecent(@Param("uid") Long uid, @Param("type") String type,
                                  @Param("subId") Long subId, org.springframework.data.domain.Pageable pageable);

    @Query("select r from RawChatLogPO r where r.userId = :uid and r.sessionType = :type "
            + "and (:subId is null and r.subSessionId is null or r.subSessionId = :subId) "
            + "and r.id > :afterId order by r.id asc")
    List<RawChatLogPO> findNewer(@Param("uid") Long uid, @Param("type") String type,
                                 @Param("subId") Long subId, @Param("afterId") Long afterId);

    @Query("select r from RawChatLogPO r where r.userId = :uid and r.sessionType = :type "
            + "and (:subId is null and r.subSessionId is null or r.subSessionId = :subId) "
            + "and r.id < :beforeId order by r.id desc")
    List<RawChatLogPO> findOlder(@Param("uid") Long uid, @Param("type") String type,
                                 @Param("subId") Long subId, @Param("beforeId") Long beforeId,
                                 org.springframework.data.domain.Pageable pageable);

    @Transactional
    int deleteBySubSessionId(Long subSessionId);
    @Transactional
    int deleteByUserId(Long userId);

    @Query("select r from RawChatLogPO r where r.subSessionId = :subId and r.id >= :fromId order by r.id asc")
    List<RawChatLogPO> findBySubSessionIdAndIdGreaterThanEqual(@Param("subId") Long subSessionId,
                                                               @Param("fromId") Long fromId);

    @Transactional
    void deleteBySubSessionIdAndIdGreaterThanEqual(@Param("subId") Long subSessionId,
                                                   @Param("fromId") Long fromId);
}
