package com.butler.perception.api;

import com.butler.application.ChatAppService;
import com.butler.infrastructure.auth.CurrentUser;
import com.butler.application.SubSessionAppService;
import com.butler.domain.model.RawChatLog;
import com.butler.domain.model.SessionType;
import com.butler.domain.model.SubSession;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import com.butler.perception.api.dto.ChatRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatAppService chatAppService;
    private final SubSessionRepository subSessionRepository;
    private final com.butler.domain.repository.MissionRepository missionRepository;
    private final ScenarioRegistry scenarioRegistry;
    private final SubSessionAppService subSessionAppService;
    private final com.butler.application.FocusAreaAppService focusAreaAppService;
    private final com.butler.domain.service.GeocodePort geocodePort;
    private final com.butler.application.ConversationAppService conversationAppService;

    public ChatController(ChatAppService chatAppService,
                          SubSessionRepository subSessionRepository,
                          com.butler.domain.repository.MissionRepository missionRepository,
                          ScenarioRegistry scenarioRegistry,
                          SubSessionAppService subSessionAppService,
                          com.butler.application.FocusAreaAppService focusAreaAppService,
                          com.butler.domain.service.GeocodePort geocodePort,
                          com.butler.application.ConversationAppService conversationAppService) {
        this.chatAppService = chatAppService;
        this.subSessionRepository = subSessionRepository;
        this.missionRepository = missionRepository;
        this.scenarioRegistry = scenarioRegistry;
        this.subSessionAppService = subSessionAppService;
        this.focusAreaAppService = focusAreaAppService;
        this.geocodePort = geocodePort;
        this.conversationAppService = conversationAppService;
    }

    /** 已注册的场景类型（前端创建目标弹窗据此动态生成表单）。 */
    @GetMapping("/scenarios")
    public List<ScenarioView> scenarios() {
        return scenarioRegistry.all().stream()
                .map(d -> new ScenarioView(d.type(), d.displayName(), d.description(),
                        d.collectFields().stream()
                                .map(f -> new FieldView(f.key(), f.label(), f.placeholder(), f.required(),
                                        f.type().name(), f.semanticType().name(), f.options(), f.unit()))
                                .toList(),
                        d.focusAreas().stream()
                                .map(f -> new FocusAreaView(f.key(), f.label(), f.description(),
                                        f.audience().name(), f.defaultSelected(), f.mandatory(), f.dependsOn()))
                                .toList()))
                .toList();
    }

    @GetMapping("/sub-sessions")
    public List<SubSessionView> listSubSessions() {
        Long userId = CurrentUser.userId();
        return subSessionRepository.findByUserId(userId).stream()
                .map(s -> {
                    String title = missionRepository.findById(s.getMissionId())
                            .map(com.butler.domain.model.Mission::getTitle).orElse("");
                    return new SubSessionView(s.getId(), s.getMissionId(), s.getScenarioType(),
                            s.getSessionDesc(), s.getStatus().name(), title);
                })
                .toList();
    }

    /** 归档子对话：从列表移除但保留对话/任务/记忆，可历史复盘。 */
    @PostMapping("/sub-sessions/{id}/archive")
    public String archive(@PathVariable Long id) {
        subSessionAppService.archive(id);
        return "ok";
    }

    /** 物理删除子对话：删除子会话、任务、记忆绑定（不删记忆本身）。 */
    @DeleteMapping("/sub-sessions/{id}")
    public String deleteSubSession(@PathVariable Long id) {
        subSessionAppService.purge(id);
        return "ok";
    }

    /** 创建目标后更新关注项：后端按强制/依赖解析生效集合，并增量生成/归档对应模块任务。 */
    @PostMapping("/sub-sessions/{id}/focus")
    public String updateFocus(@PathVariable Long id, @RequestBody java.util.Map<String, java.util.List<String>> body) {
        focusAreaAppService.updateFocusAreas(id, body.get("focusAreas"));
        return "ok";
    }

    /** 浏览器定位得到经纬度后，由后端逆地理解析为城市/区县，避免前端跨域/密钥问题。 */
    @GetMapping("/geo/reverse")
    public GeoView reverseGeo(@RequestParam double lat, @RequestParam double lon) {
        com.butler.domain.service.GeocodePort.GeoPlace place = geocodePort.reverse(lat, lon);
        if (place == null) return new GeoView(false, null, null, null, null);
        return new GeoView(true, place.province(), place.city(), place.district(), place.label());
    }

    /** 保存用户级定位（浏览器授权后调用），供所有子对话/工具回退使用。 */
    @PostMapping("/users/location")
    public java.util.Map<String, Object> saveUserLocation(@RequestBody java.util.Map<String, String> body) {
        Long userId = CurrentUser.userId();
        chatAppService.saveUserLocation(userId, body.get("city"), body.get("latitude"), body.get("longitude"));
        return java.util.Map.of("ok", true);
    }

    /** 读取子对话已收集字段（前端用于判断是否需要授权定位等）。 */
    @GetMapping("/sub-sessions/{id}/collected")
    public java.util.Map<String, String> getCollected(@PathVariable Long id) {
        return conversationAppService.getCollected(id);
    }

    @GetMapping("/sub-sessions/{id}/materials")
    public java.util.List<java.util.Map<String, String>> getStudyMaterials(@PathVariable Long id) {
        return subSessionAppService.getStudyMaterials(id);
    }

    @GetMapping("/sub-sessions/{id}/state")
    public com.butler.application.ScenarioStateSupport.ScenarioState getState(@PathVariable Long id) {
        return conversationAppService.getState(id);
    }

    @GetMapping("/sub-sessions/{id}/custom-focus")
    public java.util.Map<String, String> getCustomFocusLabels(@PathVariable Long id) {
        return conversationAppService.getCustomFocusLabels(id);
    }

    /** 更新子对话已收集字段（如浏览器定位得到的城市/区县），并重排时间轴。 */
    @PostMapping("/sub-sessions/{id}/collected")
    public java.util.Map<String, String> updateCollected(@PathVariable Long id,
                                                          @RequestBody java.util.Map<String, String> body) {
        return conversationAppService.updateCollected(id, body);
    }

    @GetMapping("/chat/history")
    public List<MessageView> history(@RequestParam String sessionType,
                                     @RequestParam(required = false) Long subSessionId) {
        Long userId = CurrentUser.userId();
        SessionType type = SessionType.valueOf(sessionType.toUpperCase());
        return chatAppService.history(userId, type, subSessionId).stream()
                .map(l -> new MessageView(l.getRole(), l.getContent(), l.getReasoning())).toList();
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest req) {
        Long userId = CurrentUser.userId();
        SessionType type = SessionType.valueOf(req.sessionType().toUpperCase());

        SseEmitter emitter = new SseEmitter(0L);
        Thread.startVirtualThread(() -> {
            try {
                chatAppService.chat(userId, type, req.subSessionId(), req.message(), new ChatAppService.ChatListener() {
                    @Override
                    public void onChunk(String text) {
                        try {
                            emitter.send(SseEmitter.event().name("chunk").data(text));
                        } catch (Exception e) {
                            // 客户端已断开，结束本次推送即可，不能影响业务/持久化
                        }
                    }
                    @Override
                    public void onReasoning(String text) {
                        try {
                            emitter.send(SseEmitter.event().name("reasoning").data(text));
                        } catch (Exception ignored) {
                        }
                    }
                    @Override
                    public void onGoalCreated(Long subSessionId, String scenarioType, String title) {
                        try {
                            emitter.send(SseEmitter.event().name("goal_created")
                                    .data(new GoalCreatedData(subSessionId, scenarioType, title)));
                        } catch (Exception ignored) {
                        }
                    }
                    @Override
                    public void onProposal(com.butler.application.ChangePreview preview) {
                        try {
                            emitter.send(SseEmitter.event().name("change_proposal").data(preview));
                        } catch (Exception ignored) {
                        }
                    }
                    @Override
                    public void onGoalProposal(String proposalId,
                                               com.butler.application.PendingGoalProposalStore.GoalProposal proposal) {
                        try {
                            emitter.send(SseEmitter.event().name("goal_proposal")
                                    .data(new GoalProposalData(proposalId, proposal)));
                        } catch (Exception ignored) {
                        }
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage() == null ? "error" : e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PostMapping("/sub-sessions/{subSessionId}/proposals/{proposalId}/apply")
    public java.util.Map<String, Object> applyProposal(@PathVariable Long subSessionId,
                                                        @PathVariable String proposalId) {
        conversationAppService.applyProposal(proposalId);
        return java.util.Map.of("applied", true);
    }

    @PostMapping("/sub-sessions/{subSessionId}/proposals/{proposalId}/discard")
    public java.util.Map<String, Object> discardProposal(@PathVariable Long subSessionId,
                                                          @PathVariable String proposalId) {
        conversationAppService.discardProposal(proposalId);
        return java.util.Map.of("discarded", true);
    }

    /** 确认主对话中“待创建目标”的调研方案，真正创建子对话。 */
    @PostMapping("/goal-proposals/{proposalId}/confirm")
    public GoalCreatedData confirmGoalProposal(@PathVariable String proposalId) {
        Long userId = CurrentUser.userId();
        if (userId == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录");
        com.butler.application.PendingGoalProposalStore.StoredProposal sp =
                chatAppService.getPendingProposal(proposalId, userId);
        String title = sp == null ? "" : sp.proposal().title();
        SubSession sub = chatAppService.confirmGoalProposal(userId, proposalId);
        return new GoalCreatedData(sub.getId(), sub.getScenarioType(), title);
    }

    /** 取当前用户主对话中最近一份待确认的建目标方案，供页面刷新/轮询后恢复确认卡。 */
    @GetMapping("/goal-proposals/latest")
    public GoalProposalData latestGoalProposal() {
        Long userId = CurrentUser.userId();
        if (userId == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录");
        com.butler.application.PendingGoalProposalStore.StoredProposal sp =
                chatAppService.getLatestPendingProposal(userId);
        if (sp == null) return null;
        return new GoalProposalData(sp.id(), sp.proposal());
    }

    public record ScenarioView(String type, String displayName, String description, List<FieldView> fields,
                               List<FocusAreaView> focusAreas) {}
    public record FieldView(String key, String label, String placeholder, boolean required,
                            String type, String semanticType, List<String> options, String unit) {}
    public record FocusAreaView(String key, String label, String description, String audience,
                               boolean defaultSelected, boolean mandatory, List<String> dependsOn) {}
    public record SubSessionView(Long id, Long missionId, String scenarioType, String sessionDesc, String status, String title) {}
    public record MessageView(String role, String content, String reasoning) {}
    public record GoalCreatedData(Long subSessionId, String scenarioType, String title) {}

    public record GoalProposalData(String proposalId, String scenarioType, String title, String goalText,
                                   java.util.List<Section> sections) {
        public GoalProposalData(String proposalId,
                                com.butler.application.PendingGoalProposalStore.GoalProposal p) {
            this(proposalId, p.scenarioType(), p.title(), p.goalText(),
                    p.sections().stream().map(s -> new Section(s.icon(), s.title(),
                            s.rows().stream().map(r -> new Row(r.label(), r.value(), r.uncertain())).toList()))
                            .toList());
        }
        public record Section(String icon, String title, java.util.List<Row> rows) {}
        public record Row(String label, String value, boolean uncertain) {}
    }
    public record GeoView(boolean ok, String province, String city, String district, String label) {}
}
