package com.butler.perception.router;

import com.butler.domain.model.SessionType;
import org.springframework.stereotype.Component;

/**
 * 会话路由：根据入口决定走新建目标 / 子会话任务调整 / 记忆查询。
 * 实际分发逻辑由 ConversationAppService/GoalAppService 承载，这里仅做类型解析。
 */
@Component
public class SessionRouter {

    public SessionType resolve(String sessionType) {
        return SessionType.valueOf(sessionType.toUpperCase());
    }

    public boolean isMain(String sessionType) {
        return resolve(sessionType) == SessionType.MAIN;
    }
}
