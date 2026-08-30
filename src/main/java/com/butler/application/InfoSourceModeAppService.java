package com.butler.application;

import com.butler.domain.model.InfoSourceMode;
import com.butler.domain.model.MainSession;
import com.butler.domain.model.SessionType;
import com.butler.domain.model.SubSession;
import com.butler.domain.repository.MainSessionRepository;
import com.butler.domain.repository.SubSessionRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 信息获取档位（三档）按会话配置与读取。
 * 档位只能由用户通过界面按钮设置；对话内容/模型无权修改，故这里不提供任何“按对话内容切换”的入口。
 */
@Service
public class InfoSourceModeAppService {

    private final MainSessionRepository mainSessionRepository;
    private final SubSessionRepository subSessionRepository;

    public InfoSourceModeAppService(MainSessionRepository mainSessionRepository,
                                    SubSessionRepository subSessionRepository) {
        this.mainSessionRepository = mainSessionRepository;
        this.subSessionRepository = subSessionRepository;
    }

    /** 读取当前会话档位（缺省 AUTO）。 */
    public InfoSourceMode get(Long userId, SessionType type, Long subSessionId) {
        if (type == SessionType.SUB && subSessionId != null) {
            SubSession sub = subSessionRepository.findById(subSessionId)
                    .orElseThrow(() -> new IllegalArgumentException("子对话不存在: " + subSessionId));
            ensureOwner(sub, userId);
            return sub.getInfoSourceMode();
        }
        return mainSessionRepository.findByUserId(userId)
                .map(MainSession::getInfoSourceMode).orElse(InfoSourceMode.ENABLED);
    }

    /** 设置当前会话档位（仅界面调用）。 */
    @Transactional
    public InfoSourceMode set(Long userId, SessionType type, Long subSessionId, InfoSourceMode mode) {
        InfoSourceMode target = mode == null ? InfoSourceMode.ENABLED : mode;
        if (type == SessionType.SUB && subSessionId != null) {
            SubSession sub = subSessionRepository.findById(subSessionId)
                    .orElseThrow(() -> new IllegalArgumentException("子对话不存在: " + subSessionId));
            ensureOwner(sub, userId);
            sub.setInfoSourceMode(target);
            subSessionRepository.save(sub);
            return target;
        }
        MainSession ms = mainSessionRepository.findByUserId(userId).orElse(null);
        if (ms == null) {
            ms = new MainSession(null, userId, Instant.now(), null, null, null, target);
        } else {
            ms = new MainSession(ms.getId(), ms.getUserId(), ms.getCreatedAt(),
                    ms.getCity(), ms.getLatitude(), ms.getLongitude(), target);
        }
        mainSessionRepository.save(ms);
        return target;
    }

    /** 供前端渲染三档选项：key=枚举名，value=中文名与说明。 */
    public Map<String, String> options() {
        Map<String, String> m = new LinkedHashMap<>();
        for (InfoSourceMode mode : InfoSourceMode.values()) {
            m.put(mode.name(), mode.getLabel() + "：" + mode.getDesc());
        }
        return m;
    }

    /** 越权校验：子会话档位只能由其所属用户读写。 */
    private void ensureOwner(SubSession sub, Long userId) {
        if (sub.getUserId() == null || !sub.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该子对话");
        }
    }
}
