package com.butler.application;

import com.butler.domain.model.MetricPoint;
import com.butler.domain.model.SubSession;
import com.butler.domain.repository.MetricPointRepository;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.infrastructure.llm.LlmPort.MetricDef;
import com.butler.infrastructure.llm.LlmPort.MetricPointIn;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 子对话可视化指标卡：维护卡片定义（JSON 存于子对话）与数据点（独立表，构成时间序列）。
 * 一张卡片可包含多条序列（series），如“热量消耗构成”含静息/运动/总消耗三条线。
 * 卡片由模型在建目标/对话中声明、合并、删除；数据点在用户汇报数值时按序列 key 写入，同日同序列覆盖。
 */
@Service
public class MetricAppService {

    private static final Logger log = LoggerFactory.getLogger(MetricAppService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SubSessionRepository subSessionRepository;
    private final MetricPointRepository metricPointRepository;
    private final ObjectMapper objectMapper;

    public MetricAppService(SubSessionRepository subSessionRepository,
                            MetricPointRepository metricPointRepository,
                            ObjectMapper objectMapper) {
        this.subSessionRepository = subSessionRepository;
        this.metricPointRepository = metricPointRepository;
        this.objectMapper = objectMapper;
    }

    /** 一张卡片/图。series 为图内多条序列；单指标卡 series 为一条（key 与卡片 key 同名）。 */
    public record Series(String key, String label) {}
    public record Def(String key, String label, String unit, String chartType, List<Series> series) {
        public Def {
            if (series == null || series.isEmpty()) {
                series = List.of(new Series(key, label));
            }
        }
    }
    /** 头部卡片最新值：value 为该卡主序列最新值（多序列卡 value 为 null，点击看图）。 */
    public record Point(String key, Object value, String unit, String date, String label) {}

    /** 读取卡片定义；无则空列表。 */
    @Transactional(readOnly = true)
    public List<Def> getDefs(Long subSessionId) {
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null || sub.getMetricDefs() == null || sub.getMetricDefs().isBlank()) return List.of();
        try {
            List<Def> defs = objectMapper.readValue(sub.getMetricDefs(), new TypeReference<List<Def>>() {});
            return defs == null ? List.of() : defs;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void saveDefs(Long subSessionId, SubSession sub, List<Def> defs) {
        try {
            sub.setMetricDefs(objectMapper.writeValueAsString(defs));
            subSessionRepository.save(sub);
        } catch (Exception e) {
            log.warn("保存指标定义失败 sub={} err={}", subSessionId, e.getMessage());
        }
    }

    /** 合并写入新卡片（按 key 去重，已存在则更新展示名/单位/图类型/序列）。 */
    @Transactional
    public void mergeDefs(Long subSessionId, List<MetricDef> defs) {
        if (defs == null || defs.isEmpty()) return;
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null) return;
        Map<String, Def> byKey = new LinkedHashMap<>();
        for (Def d : getDefs(subSessionId)) byKey.put(d.key(), d);
        for (MetricDef d : defs) {
            if (d == null || d.key() == null || d.key().isBlank()) continue;
            String key = d.key().trim();
            List<Series> series = new ArrayList<>();
            if (d.series() != null) {
                for (var s : d.series()) {
                    if (s == null || s.key() == null || s.key().isBlank()) continue;
                    String sk = s.key().trim();
                    series.add(new Series(sk, s.label() == null || s.label().isBlank() ? sk : s.label().trim()));
                }
            }
            if (series.isEmpty()) series.add(new Series(key, d.label() == null ? key : d.label().trim()));
            byKey.put(key, new Def(key,
                    d.label() == null || d.label().isBlank() ? key : d.label().trim(),
                    d.unit() == null ? "" : d.unit().trim(),
                    d.chartType() == null || d.chartType().isBlank() ? "line" : d.chartType().trim(),
                    series));
        }
        saveDefs(subSessionId, sub, new ArrayList<>(byKey.values()));
    }

    /** 删除卡片：移除定义，并删除该卡所有序列的数据点。返回被删除的卡片 key。 */
    @Transactional
    public List<String> removeDefs(Long subSessionId, List<String> keys) {
        if (keys == null || keys.isEmpty()) return List.of();
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null) return List.of();
        List<Def> defs = getDefs(subSessionId);
        List<String> removeKeys = keys.stream().filter(k -> k != null && !k.isBlank()).map(String::trim).toList();
        List<MetricPoint> points = metricPointRepository.findBySubSessionId(subSessionId);
        List<String> removed = new ArrayList<>();
        List<Def> kept = new ArrayList<>();
        for (Def d : defs) {
            if (removeKeys.contains(d.key())) {
                removed.add(d.key());
                List<String> seriesKeys = d.series().stream().map(Series::key).toList();
                for (MetricPoint p : points) {
                    if (seriesKeys.contains(p.getMetricKey())) metricPointRepository.delete(p);
                }
            } else {
                kept.add(d);
            }
        }
        if (!removed.isEmpty()) saveDefs(subSessionId, sub, kept);
        return removed;
    }

    /**
     * 写入数据点：自动补齐缺失的卡片定义；同日同序列覆盖，否则新增。
     * 数据点 key 对应某张卡的序列 key；未归属任何卡的点按“同名单序列卡”兜底，避免丢失。
     */
    /**
     * 查询某序列在某日已落库的值（无则 null）。供变更预览判断“值是否真的变化”。
     * rawDate 为空回退当天。
     */
    @Transactional(readOnly = true)
    public Double existingValue(Long subSessionId, String seriesKey, String rawDate, LocalDate today) {
        if (seriesKey == null) return null;
        LocalDate date = parseDate(rawDate, today);
        return metricPointRepository.findBySubSessionId(subSessionId).stream()
                .filter(p -> seriesKey.equals(p.getMetricKey()) && date.equals(p.getValueDate()))
                .map(MetricPoint::getValue)
                .filter(java.util.Objects::nonNull)
                .reduce((a, b) -> b)  // 取最新一条
                .orElse(null);
    }

    @Transactional
    public int addPoints(Long subSessionId, Long userId, List<MetricPointIn> points) {
        if (points == null || points.isEmpty()) return 0;
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null) return 0;
        List<Def> defs = getDefs(subSessionId);
        Map<String, Def> cardBySeries = new LinkedHashMap<>();
        for (Def d : defs) for (Series s : d.series()) cardBySeries.put(s.key(), d);
        LocalDate today = LocalDate.now(ZONE);
        List<MetricPoint> existing = metricPointRepository.findBySubSessionId(subSessionId);
        int written = 0;
        for (MetricPointIn p : points) {
            if (p == null || p.key() == null || p.key().isBlank() || p.value() == null) continue;
            String rawKey = p.key().trim();
            // 数据点只能写入“已确认存在”的卡片序列：精确匹配，失败再做通用 token 归一兜底。
            // 匹配不上任何已确认卡片的点直接丢弃（不隐式建卡、不存孤儿）；新图表须经 metricDefs 走用户确认。
            String key = cardBySeries.containsKey(rawKey) ? rawKey : resolveSeriesKey(rawKey, cardBySeries.keySet());
            Def card = cardBySeries.get(key);
            if (card == null) {
                log.info("丢弃未归属已确认卡片的指标点 sub={} key={}（新图表需先经用户确认创建）", subSessionId, rawKey);
                continue;
            }
            LocalDate date = parseDate(p.date(), today);
            String unit = card.unit();
            Series s = card.series().stream().filter(x -> x.key().equals(key)).findFirst().orElse(null);
            String label = s != null ? s.label() : key;
            MetricPoint same = existing.stream()
                    .filter(x -> key.equals(x.getMetricKey()) && date.equals(x.getValueDate()))
                    .findFirst().orElse(null);
            metricPointRepository.save(new MetricPoint(same == null ? null : same.getId(),
                    subSessionId, userId, key, label, p.value(), unit, date, Instant.now()));
            written++;
        }
        return written;
    }

    /** 前端头部：每张卡的最新值（单序列卡显示数值，多序列卡显示 “N 项” 占位，点击看图）。 */
    @Transactional(readOnly = true)
    public List<Point> latest(Long subSessionId) {
        List<Def> defs = getDefs(subSessionId);
        Map<String, List<MetricPoint>> byKey = groupByKey(metricPointRepository.findBySubSessionId(subSessionId));
        List<Point> out = new ArrayList<>();
        for (Def d : defs) {
            Series primary = d.series().get(0);
            List<MetricPoint> series = byKey.get(primary.key());
            if (d.series().size() > 1) {
                out.add(new Point(d.key(), null, d.unit(), null, d.label()));
            } else if (series != null && !series.isEmpty()) {
                MetricPoint last = series.get(series.size() - 1);
                out.add(new Point(d.key(), last.getValue(), d.unit(),
                        last.getValueDate() == null ? null : last.getValueDate().toString(), d.label()));
            } else {
                out.add(new Point(d.key(), null, d.unit(), null, d.label()));
            }
        }
        return out;
    }

    /** 点击卡片：该卡所有序列的完整数据（按日期升序）+ 图表类型。 */
    @Transactional(readOnly = true)
    public Map<String, Object> series(Long subSessionId, String key) {
        Map<String, Object> out = new LinkedHashMap<>();
        Def def = getDefs(subSessionId).stream().filter(d -> d.key().equals(key)).findFirst().orElse(null);
        out.put("key", key);
        out.put("label", def == null ? key : def.label());
        out.put("unit", def == null ? "" : def.unit());
        out.put("chartType", def == null ? "line" : def.chartType());
        List<MetricPoint> all = metricPointRepository.findBySubSessionId(subSessionId);
        List<Map<String, Object>> seriesOut = new ArrayList<>();
        List<String> seriesKeys = def == null ? List.of(key) : def.series().stream().map(Series::key).toList();
        List<String> seriesLabels = def == null ? List.of(key) : def.series().stream().map(Series::label).toList();
        for (int i = 0; i < seriesKeys.size(); i++) {
            String sk = seriesKeys.get(i);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("key", sk);
            one.put("label", seriesLabels.get(i));
            List<Map<String, Object>> pts = new ArrayList<>();
            for (MetricPoint p : all) {
                if (!p.getMetricKey().equals(sk)) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", p.getValueDate() == null ? null : p.getValueDate().toString());
                m.put("value", p.getValue());
                pts.add(m);
            }
            one.put("points", pts);
            seriesOut.add(one);
        }
        out.put("series", seriesOut);
        return out;
    }

    /**
     * 数据点 key 与卡片序列 key 近似匹配（通用，不针对具体场景）：
     * 按下划线拆 token，若点的 token 集合是某序列 token 集合的真子集（或反向），取 token 差最小且唯一的候选；
     * 存在歧义（多个候选同分）或无包含关系时返回原 key（落为孤儿点，不强行归并）。
     */
    private String resolveSeriesKey(String pointKey, java.util.Set<String> seriesKeys) {
        java.util.List<String> pTokens = tokens(pointKey);
        String best = null;
        int bestDiff = Integer.MAX_VALUE;
        boolean unique = false;
        for (String sk : seriesKeys) {
            java.util.List<String> sTokens = tokens(sk);
            boolean pSub = sTokens.containsAll(pTokens) && pTokens.size() < sTokens.size();
            boolean sSub = pTokens.containsAll(sTokens) && sTokens.size() < pTokens.size();
            if (!pSub && !sSub) continue;
            int diff = Math.abs(sTokens.size() - pTokens.size());
            if (diff < bestDiff) {
                bestDiff = diff;
                best = sk;
                unique = true;
            } else if (diff == bestDiff) {
                unique = false;
            }
        }
        return unique ? best : pointKey;
    }

    private java.util.List<String> tokens(String key) {
        java.util.List<String> out = new ArrayList<>();
        for (String t : key.toLowerCase().split("[_\\-\\s]+")) {
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }

    private Map<String, List<MetricPoint>> groupByKey(List<MetricPoint> all) {
        Map<String, List<MetricPoint>> map = new LinkedHashMap<>();
        for (MetricPoint p : all) map.computeIfAbsent(p.getMetricKey(), k -> new ArrayList<>()).add(p);
        return map;
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw.trim().substring(0, Math.min(10, raw.trim().length())));
        } catch (Exception e) {
            return fallback;
        }
    }
}
