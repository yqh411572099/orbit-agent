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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 子对话可视化指标卡：维护指标定义（JSON 存于子对话）与数据点（独立表，构成时间序列）。
 * 指标定义由模型在建目标/对话中声明；数据点在用户汇报数值时写入，同日同指标覆盖更新。
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

    public record Def(String key, String label, String unit, String chartType) {}
    public record Point(String key, Object value, String unit, String date, String label) {}

    /** 读取指标定义；无则空列表。 */
    @Transactional(readOnly = true)
    public List<Def> getDefs(Long subSessionId) {
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null || sub.getMetricDefs() == null || sub.getMetricDefs().isBlank()) return List.of();
        try {
            return objectMapper.readValue(sub.getMetricDefs(), new TypeReference<List<Def>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 合并写入新的指标定义（按 key 去重，保留已存在的 label/unit/chartType）。 */
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
            Def incoming = new Def(key,
                    d.label() == null || d.label().isBlank() ? key : d.label().trim(),
                    d.unit() == null ? "" : d.unit().trim(),
                    d.chartType() == null || d.chartType().isBlank() ? "line" : d.chartType().trim());
            // 同 key 已存在时，以用户最新口径更新 label/单位/图表类型（例如单位从 kg 切到“斤”），
            // 但保留已有定义；不做任何单位换算，数据点始终按用户报数原值存储。
            Def existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, incoming);
            } else {
                byKey.put(key, new Def(key,
                        existing.label() == null || existing.label().isBlank() ? incoming.label() : existing.label(),
                        incoming.unit().isBlank() ? existing.unit() : incoming.unit(),
                        incoming.chartType().isBlank() ? existing.chartType() : incoming.chartType()));
            }
        }
        try {
            sub.setMetricDefs(objectMapper.writeValueAsString(new ArrayList<>(byKey.values())));
            subSessionRepository.save(sub);
        } catch (Exception e) {
            log.warn("保存指标定义失败 sub={} err={}", subSessionId, e.getMessage());
        }
    }

    /**
     * 写入数据点：自动补齐缺失的指标定义；同日同指标覆盖（更新），否则新增。
     * 返回实际写入的点数量。
     */
    @Transactional
    public int addPoints(Long subSessionId, Long userId, List<MetricPointIn> points) {
        if (points == null || points.isEmpty()) return 0;
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null) return 0;
        Map<String, Def> defs = new LinkedHashMap<>();
        for (Def d : getDefs(subSessionId)) defs.put(d.key(), d);
        LocalDate today = LocalDate.now(ZONE);
        List<MetricPoint> existing = metricPointRepository.findBySubSessionId(subSessionId);
        int written = 0;
        for (MetricPointIn p : points) {
            if (p == null || p.key() == null || p.key().isBlank() || p.value() == null) continue;
            String key = p.key().trim();
            LocalDate date = parseDate(p.date(), today);
            Def def = defs.get(key);
            String label = def == null ? key : def.label();
            String unit = def == null ? "" : def.unit();
            // 同日同指标覆盖
            MetricPoint same = existing.stream()
                    .filter(x -> key.equals(x.getMetricKey()) && date.equals(x.getValueDate()))
                    .findFirst().orElse(null);
            metricPointRepository.save(new MetricPoint(same == null ? null : same.getId(),
                    subSessionId, userId, key, label, p.value(), unit, date, Instant.now()));
            written++;
        }
        return written;
    }

    /** 前端头部：每个指标的最新值。 */
    @Transactional(readOnly = true)
    public List<Point> latest(Long subSessionId) {
        List<Def> defs = getDefs(subSessionId);
        Map<String, List<MetricPoint>> byKey = groupByKey(metricPointRepository.findBySubSessionId(subSessionId));
        List<Point> out = new ArrayList<>();
        for (Def d : defs) {
            List<MetricPoint> series = byKey.get(d.key());
            // 已声明但还没数据点：仍返回卡片（值为空，前端显示 “—”），
            // 让用户在第一次汇报数值前就能看到该指标卡已建立。
            if (series == null || series.isEmpty()) {
                out.add(new Point(d.key(), null, d.unit(), null, d.label()));
                continue;
            }
            MetricPoint last = series.get(series.size() - 1);
            out.add(new Point(d.key(), last.getValue(), d.unit(),
                    last.getValueDate() == null ? null : last.getValueDate().toString(), d.label()));
        }
        return out;
    }

    /** 点击卡片：某个指标的完整序列（按日期升序）+ 图表类型。 */
    @Transactional(readOnly = true)
    public Map<String, Object> series(Long subSessionId, String key) {
        Map<String, Object> out = new LinkedHashMap<>();
        Def def = getDefs(subSessionId).stream().filter(d -> d.key().equals(key)).findFirst().orElse(null);
        List<MetricPoint> points = metricPointRepository.findBySubSessionId(subSessionId).stream()
                .filter(p -> p.getMetricKey().equals(key)).toList();
        out.put("key", key);
        out.put("label", def == null ? key : def.label());
        out.put("unit", def == null ? "" : def.unit());
        out.put("chartType", def == null ? "line" : def.chartType());
        List<Map<String, Object>> pts = new ArrayList<>();
        for (MetricPoint p : points) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", p.getValueDate() == null ? null : p.getValueDate().toString());
            m.put("value", p.getValue());
            pts.add(m);
        }
        out.put("points", pts);
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
