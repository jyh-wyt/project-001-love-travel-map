"use client";

import { useEffect, useMemo, useState } from "react";
import { RadioGroup } from "@baseline-ui/core";
import { CloudSun, MapPin, RefreshCcw, Sparkles, X } from "lucide-react";
import { API_BASE_URL, requestJson, toErrorMessage } from "@/shared/lib/api";

type PlanDay = {
  id: number;
  date: string;
  title: string;
  detail: string;
  aiPlaces?: string[];
  aiMustVisitPlaces?: string[];
  aiHotelLocation?: string;
  sortOrder: number;
};

type PeriodMode = "PLAY" | "REST";
type Step = "places" | "time" | "preview";
type GenerateState = "idle" | "generating" | "done";
type RegenerateMode = "NEW" | "REVISE" | "REWRITE";
type AgentStepKey = "MEMORY_SYNC" | "MEMORY_RETRIEVAL" | "PLAN_GENERATION" | "DRAFT_SAVE";
type AgentStepStatus = "pending" | "running" | "done" | "skipped" | "failed";

type PeriodDraft = {
  mode: PeriodMode;
  content: string;
  places: string[];
};

type Recommendation = {
  title: string;
  items: string[];
};

type AiDraft = {
  runId: string;
  draftId: number;
  dayId: number;
  title: string;
  morning: PeriodDraft;
  afternoon: PeriodDraft;
  evening: PeriodDraft;
  recommendations: Recommendation[];
  reminders: string[];
};

type TravelMemory = {
  memoryId: string;
  sourceType: string;
  sourceId: number;
  cityName: string;
  content: string;
  score: number;
  reason: string;
};

type MemoryReferenceState = {
  success: boolean;
  items: TravelMemory[];
  errorMessage: string;
};

type AgentStep = {
  step: AgentStepKey;
  label: string;
  status: AgentStepStatus;
  message: string;
};

type ToolResult = {
  toolName: string;
  label: string;
  status: AgentStepStatus;
  summary: string;
};

type AiApplyResponse = {
  success: boolean;
  day: PlanDay;
};

type AiAgentEventItem = {
  id: number;
  eventType: string;
  eventMessage: string;
  eventJson: string;
  createdAt: string;
};

type AiAgentRunEvents = {
  runId: string;
  agentType: string;
  modelName: string;
  promptVersion: string;
  status: string;
  durationMs: number | null;
  createdAt: string;
  events: AiAgentEventItem[];
};

type AiGenerationError = {
  type: string;
  stage: string;
  message: string;
  detail: string;
};

type AiPlanDayDialogProps = {
  day: PlanDay;
  isOpen: boolean;
  onClose: () => void;
  onApply: (nextDay: PlanDay) => void;
};

const modeItems = [
  { id: "PLAY", label: "出去玩", description: "让 AI 安排地点和顺路推荐" },
  { id: "REST", label: "酒店休息", description: "保留轻松休息时间" }
];

const initialAgentSteps: AgentStep[] = [
  { step: "MEMORY_SYNC", label: "准备记忆", status: "pending", message: "等待同步日记和计划" },
  { step: "MEMORY_RETRIEVAL", label: "检索记忆", status: "pending", message: "等待向量库检索" },
  { step: "PLAN_GENERATION", label: "生成方案", status: "pending", message: "等待大模型生成" },
  { step: "DRAFT_SAVE", label: "保存草稿", status: "pending", message: "等待保存 AI 草稿" }
];
const visibleMemoryLimit = 3;

export function AiPlanDayDialog({ day, isOpen, onClose, onApply }: AiPlanDayDialogProps) {
  const [step, setStep] = useState<Step>("places");
  const [places, setPlaces] = useState<string[]>([]);
  const [placeInput, setPlaceInput] = useState("");
  const [hotelLocation, setHotelLocation] = useState("");
  const [mustVisitPlaces, setMustVisitPlaces] = useState<string[]>([]);
  const [morningMode, setMorningMode] = useState<PeriodMode>("PLAY");
  const [afternoonMode, setAfternoonMode] = useState<PeriodMode>("PLAY");
  const [eveningMode, setEveningMode] = useState<PeriodMode>("REST");
  const [notes, setNotes] = useState("");
  const [generateState, setGenerateState] = useState<GenerateState>("idle");
  const [agentSteps, setAgentSteps] = useState<AgentStep[]>(initialAgentSteps);
  const [toolResults, setToolResults] = useState<ToolResult[]>([]);
  const [streamingDraftText, setStreamingDraftText] = useState("");
  const [referencedMemories, setReferencedMemories] = useState<MemoryReferenceState | null>(null);
  const [draft, setDraft] = useState<AiDraft | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [generationError, setGenerationError] = useState<AiGenerationError | null>(null);
  const [regenerateChoiceOpen, setRegenerateChoiceOpen] = useState(false);
  const [revisionInstruction, setRevisionInstruction] = useState("");
  const [applyConfirmOpen, setApplyConfirmOpen] = useState(false);
  const [runEvents, setRunEvents] = useState<AiAgentRunEvents | null>(null);
  const [runEventsLoading, setRunEventsLoading] = useState(false);

  const isDateBeyondForecast = useMemo(() => isBeyondForecastRange(day.date), [day.date]);
  const canContinueFromPlaces = places.length > 0;

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    const initialPlaces = day.aiPlaces ?? [];
    setStep("places");
    setPlaces(initialPlaces);
    setPlaceInput("");
    setHotelLocation(day.aiHotelLocation ?? "");
    setMustVisitPlaces((day.aiMustVisitPlaces ?? []).filter((place) => initialPlaces.includes(place)));
    setMorningMode("PLAY");
    setAfternoonMode("PLAY");
    setEveningMode("REST");
    setNotes("");
    setGenerateState("idle");
    setAgentSteps(initialAgentSteps);
    setToolResults([]);
    setStreamingDraftText("");
    setReferencedMemories(null);
    setDraft(null);
    setErrorMessage("");
    setRegenerateChoiceOpen(false);
    setRevisionInstruction("");
    setApplyConfirmOpen(false);
    setRunEvents(null);
    setRunEventsLoading(false);
  }, [day.id, isOpen]);

  if (!isOpen) {
    return null;
  }

  function addPlace() {
    const nextPlace = placeInput.trim();
    if (!nextPlace) {
      setErrorMessage("请先输入一个想去的地方");
      return;
    }
    if (places.includes(nextPlace)) {
      setErrorMessage("这个地点已经添加过了");
      return;
    }

    setPlaces((current) => [...current, nextPlace]);
    setPlaceInput("");
    setErrorMessage("");
  }

  function removePlace(place: string) {
    setPlaces((current) => current.filter((item) => item !== place));
    setMustVisitPlaces((current) => current.filter((item) => item !== place));
  }

  function toggleMustVisit(place: string, selected: boolean) {
    setMustVisitPlaces((current) => {
      if (selected) {
        return current.includes(place) ? current : [...current, place];
      }
      return current.filter((item) => item !== place);
    });
  }

  function goNextFromPlaces() {
    if (!canContinueFromPlaces) {
      setErrorMessage("至少添加 1 个想去的地方");
      return;
    }
    setErrorMessage("");
    setStep("time");
  }

  async function generatePlan(mode: RegenerateMode) {
    const normalizedRevisionInstruction = revisionInstruction.trim();
    if (mode === "REVISE" && !normalizedRevisionInstruction) {
      setErrorMessage("请先写清楚希望 AI 怎么修改当前规划");
      return;
    }

    try {
      setStep("preview");
      setGenerateState("generating");
      setAgentSteps(initialAgentSteps);
      setToolResults([]);
      setStreamingDraftText("");
      setReferencedMemories(null);
      setRunEvents(null);
      setRunEventsLoading(false);
      setErrorMessage("");
      setGenerationError(null);
      setRegenerateChoiceOpen(false);
      setApplyConfirmOpen(false);

      const nextDraft = await streamGeneratePlan({
        dayId: day.id,
        destination: inferDestination(),
        places,
        mustVisitPlaces,
        hotelLocation: hotelLocation.trim(),
        morningMode,
        afternoonMode,
        eveningMode,
        notes,
        revisionInstruction: mode === "REVISE" ? normalizedRevisionInstruction : "",
        regenerateMode: mode,
        sourceDraftId: draft?.draftId ?? null,
        onProgress: (message) => updateAgentStep("PLAN_GENERATION", "running", message),
        onAgentStep: (agentStep) => updateAgentStep(agentStep.step, agentStep.status, agentStep.message),
        onToolResult: upsertToolResult,
        onDraftDelta: (text) => setStreamingDraftText((current) => `${current}${text}`),
        onMemories: setReferencedMemories
      });
      setDraft(nextDraft);
      setGenerateState("done");
    } catch (error) {
      setGenerateState("idle");
      if (error instanceof AiGenerationStreamError) {
        setGenerationError(error.info);
        setErrorMessage(error.info.message);
      } else {
        setGenerationError(null);
        setErrorMessage(toErrorMessage(error));
      }
    }
  }

  async function applyDraft() {
    if (!draft) {
      return;
    }

    try {
      const response = await requestJson<AiApplyResponse>(`/api/ai/plan-day-drafts/${draft.draftId}/apply`, {
        method: "POST"
      });
      setDraft(null);
      setApplyConfirmOpen(false);
      onApply(response.day);
      onClose();
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    }
  }

  async function closeDialog() {
    if (draft && generateState === "done") {
      try {
        await requestJson<{ success: boolean }>(`/api/ai/plan-day-drafts/${draft.draftId}/discard`, {
          method: "POST"
        });
      } catch {
        // Closing the dialog should not trap the user if discard fails.
      }
    }
    setDraft(null);
    setApplyConfirmOpen(false);
    onClose();
  }

  async function loadRunEvents() {
    if (!draft || runEventsLoading) {
      return;
    }

    try {
      setRunEventsLoading(true);
      setErrorMessage("");
      const response = await requestJson<AiAgentRunEvents>(`/api/ai/runs/${draft.runId}/events`);
      setRunEvents(response);
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setRunEventsLoading(false);
    }
  }

  function inferDestination() {
    const firstPlace = places[0]?.trim();
    return firstPlace || "当前目的地";
  }

  function updateAgentStep(step: AgentStepKey, status: AgentStepStatus, message: string) {
    setAgentSteps((current) =>
      current.map((item) =>
        item.step === step
          ? {
              ...item,
              status,
              message
            }
          : item
      )
    );
  }

  function upsertToolResult(result: ToolResult) {
    setToolResults((current) => {
      const existingIndex = current.findIndex((item) => item.toolName === result.toolName);
      if (existingIndex === -1) {
        return [...current, result];
      }
      return current.map((item, index) => (index === existingIndex ? result : item));
    });
  }

  return (
    <div className="ai-dialog-backdrop" role="presentation">
      <section className="ai-dialog" aria-label="AI 规划这一天" role="dialog" aria-modal="true">
        <div className="ai-dialog-header">
          <div>
            <span className="ai-kicker">
              <Sparkles aria-hidden="true" size={15} />
              AI 规划
            </span>
            <h2>AI 规划这一天</h2>
            <p>{formatDayLabel(day)}</p>
          </div>
          <button aria-label="关闭 AI 规划" className="dialog-close-button" onClick={() => void closeDialog()} type="button">
            <X aria-hidden="true" size={18} />
          </button>
        </div>

        <div className="ai-stepper" aria-label="AI 规划步骤">
          <StepPill active={step === "places"} done={step !== "places"} label="地点" />
          <StepPill active={step === "time"} done={step === "preview"} label="时间" />
          <StepPill active={step === "preview"} done={generateState === "done"} label="预览" />
        </div>

        <div className="ai-dialog-body">
          {errorMessage ? (
            <div className="ai-dialog-feedback">
              <p className="plan-feedback error">{errorMessage}</p>
              {generationError ? (
                <div className="ai-error-meta">
                  <span>阶段：{formatGenerationErrorStage(generationError.stage)}</span>
                  <span>类型：{generationError.type}</span>
                  {generationError.detail ? <small>{generationError.detail}</small> : null}
                </div>
              ) : null}
            </div>
          ) : null}

          {step === "places" ? (
            <div className="ai-form-section">
              <label className="field-label">
                <span className="ai-field-title">想去的地方</span>
                <span className="field-hint">每次添加一个地点，可以按 Enter 或点击“添加”。例如先添加“小麦岛”，再添加“五四广场”。</span>
                <span className="ai-place-input-row">
                  <input
                    onChange={(event) => setPlaceInput(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        addPlace();
                      }
                    }}
                    placeholder="例如：青岛小麦岛"
                    value={placeInput}
                  />
                  <button className="secondary-button" onClick={addPlace} type="button">
                    添加
                  </button>
                </span>
              </label>

              {places.length > 0 ? (
                <div className="ai-place-tags" aria-label="已添加地点">
                  {places.map((place) => (
                    <span className="ai-place-tag" key={place}>
                      <MapPin aria-hidden="true" size={14} />
                      {place}
                      <button aria-label={`删除${place}`} onClick={() => removePlace(place)} type="button">
                        <X aria-hidden="true" size={14} />
                      </button>
                    </span>
                  ))}
                </div>
              ) : (
                <div className="ai-inline-empty">
                  <strong>先把想去的地方写下来</strong>
                  <span>AI 会只围绕这些地点安排，不会凭空塞一堆不相关景点。</span>
                </div>
              )}

              <label className="field-label">
                <span className="ai-field-title">酒店地点</span>
                <span className="field-hint">选填。填写酒店、民宿或大概住宿区域后，AI 会把它作为出发和返回参考。</span>
                <input
                  onChange={(event) => {
                    setHotelLocation(event.target.value);
                    if (errorMessage) {
                      setErrorMessage("");
                    }
                  }}
                  placeholder="例如：五四广场附近酒店"
                  value={hotelLocation}
                />
              </label>

              {places.length > 0 ? (
                <div className="ai-must-visit-list">
                  <strong>必去地点</strong>
                  <span>从上面添加过的地点里选择，AI 会优先安排；不选也可以。</span>
                  <div className="ai-must-visit-options">
                    {places.map((place) => (
                      <button
                        aria-pressed={mustVisitPlaces.includes(place)}
                        className={mustVisitPlaces.includes(place) ? "ai-must-visit-option selected" : "ai-must-visit-option"}
                        key={place}
                        onClick={() => toggleMustVisit(place, !mustVisitPlaces.includes(place))}
                        type="button"
                      >
                        <span className="ai-must-visit-check" aria-hidden="true" />
                        <span>{place}</span>
                      </button>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}

          {step === "time" ? (
            <div className="ai-form-section">
              <PeriodModeGroup label="上午" value={morningMode} onChange={setMorningMode} />
              <PeriodModeGroup label="下午" value={afternoonMode} onChange={setAfternoonMode} />
              <PeriodModeGroup label="晚上" value={eveningMode} onChange={setEveningMode} />

              <label className="field-label">
                补充偏好
                <textarea
                  onChange={(event) => setNotes(event.target.value)}
                  placeholder="例如：不想太累，想拍照，晚上想看海"
                  value={notes}
                />
              </label>

              <div className="ai-weather-note">
                <CloudSun aria-hidden="true" size={18} />
                <span>
                  {isDateBeyondForecast
                    ? "当前日期超过 15 天，AI 会提示天气可能变化。"
                    : "15 天以内会参考真实天气预报。"}
                </span>
              </div>
            </div>
          ) : null}

          {step === "preview" ? (
            <div className="ai-preview-section">
              <AiAgentTrace steps={agentSteps} toolResults={toolResults} />

              <AiMemoryReferences memories={referencedMemories} isGenerating={generateState === "generating"} />

              {streamingDraftText && generateState === "generating" ? <AiStreamingDraft text={streamingDraftText} /> : null}

              {draft && generateState === "done" ? <AiDraftPreview draft={draft} /> : null}

              {draft && generateState === "done" ? (
                <AiRunEventsPanel events={runEvents} loading={runEventsLoading} onLoad={() => void loadRunEvents()} />
              ) : null}

              {regenerateChoiceOpen ? (
                <div className="ai-regenerate-box">
                  <label className="field-label">
                    <span className="ai-field-title">想让 AI 怎么修改？</span>
                    <span className="field-hint">例如：上午少走路，下午多安排海边拍照，晚上不要太晚回酒店。</span>
                    <textarea
                      onChange={(event) => {
                        setRevisionInstruction(event.target.value);
                        if (errorMessage) {
                          setErrorMessage("");
                        }
                      }}
                      placeholder="写下具体修改要求"
                      value={revisionInstruction}
                    />
                  </label>
                </div>
              ) : null}

            </div>
          ) : null}
        </div>

        <div className="ai-dialog-actions">
          {step === "places" ? (
            <>
              <button className="secondary-button" onClick={() => void closeDialog()} type="button">
                取消
              </button>
              <button className="primary-button" onClick={goNextFromPlaces} type="button">
                下一步
              </button>
            </>
          ) : null}

          {step === "time" ? (
            <>
              <button className="secondary-button" onClick={() => setStep("places")} type="button">
                上一步
              </button>
              <button className="primary-button" onClick={() => void generatePlan("NEW")} type="button">
                开始生成
              </button>
            </>
          ) : null}

          {step === "preview" ? (
            regenerateChoiceOpen ? (
              <div className="ai-regenerate-actions">
                <button className="secondary-button" disabled={generateState === "generating"} onClick={() => setRegenerateChoiceOpen(false)} type="button">
                  返回
                </button>
                <button className="secondary-button" disabled={generateState === "generating"} onClick={() => void generatePlan("REVISE")} type="button">
                  按要求修改
                </button>
                <button className="primary-button" disabled={generateState === "generating"} onClick={() => void generatePlan("REWRITE")} type="button">
                  彻底重写
                </button>
              </div>
            ) : (
              <>
                <button className="secondary-button" disabled={generateState === "generating"} onClick={() => setRegenerateChoiceOpen(true)} type="button">
                  <RefreshCcw aria-hidden="true" size={16} />
                  重新生成
                </button>
                <button className="secondary-button" disabled={generateState === "generating"} onClick={() => void closeDialog()} type="button">
                  取消
                </button>
                <button className="primary-button" disabled={!draft || generateState === "generating"} onClick={() => setApplyConfirmOpen(true)} type="button">
                  应用到这一天
                </button>
              </>
            )
          ) : null}
        </div>
      </section>
      {applyConfirmOpen && draft ? (
        <section className="ai-apply-confirm" aria-label="确认应用 AI 草稿" role="dialog" aria-modal="true">
          <h3>应用这版 AI 草稿？</h3>
          <p>确认后会覆盖当前这一天的标题和安排内容。</p>
          <div className="ai-apply-confirm-actions">
            <button className="secondary-button" disabled={generateState === "generating"} onClick={() => setApplyConfirmOpen(false)} type="button">
              再看看
            </button>
            <button className="primary-button" disabled={generateState === "generating"} onClick={() => void applyDraft()} type="button">
              确认应用
            </button>
          </div>
        </section>
      ) : null}
    </div>
  );
}

function StepPill({ active, done, label }: { active: boolean; done: boolean; label: string }) {
  return <span className={active ? "ai-step active" : done ? "ai-step done" : "ai-step"}>{label}</span>;
}

function PeriodModeGroup({
  label,
  value,
  onChange
}: {
  label: string;
  value: PeriodMode;
  onChange: (value: PeriodMode) => void;
}) {
  return (
    <div className="ai-period-group">
      <span>{label}</span>
      <RadioGroup
        aria-label={`${label}安排`}
        items={modeItems}
        onChange={(nextValue) => onChange(nextValue as PeriodMode)}
        optionsContainerClassName="ai-period-options"
        renderOption={(item, { isSelected }) => (
          <div className={isSelected ? "ai-period-option selected" : "ai-period-option"}>
            <strong>{item.label}</strong>
            {item.description ? <small>{item.description}</small> : null}
          </div>
        )}
        value={value}
      />
    </div>
  );
}

function AiAgentTrace({ steps, toolResults }: { steps: AgentStep[]; toolResults: ToolResult[] }) {
  return (
    <section className="ai-agent-trace" aria-label="AI 执行轨迹">
      <div className="ai-agent-trace-header">
        <strong>AI 执行轨迹</strong>
        <span>Agent 正在按步骤处理</span>
      </div>
      <div className="ai-agent-step-list">
        {steps.map((step) => (
          <article className={`ai-agent-step ${step.status}`} key={step.step}>
            <span className="ai-agent-step-dot" aria-hidden="true" />
            <div>
              <strong>{step.label}</strong>
              <p>{step.message}</p>
            </div>
          </article>
        ))}
      </div>
      {toolResults.length > 0 ? (
        <div className="ai-tool-result-list">
          {toolResults.map((result) => (
            <article className={`ai-tool-result ${result.status}`} key={result.toolName}>
              <strong>{result.label}</strong>
              <p>{result.summary}</p>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function AiDraftPreview({ draft }: { draft: AiDraft }) {
  return (
    <div className="ai-draft-preview">
      <h3>{draft.title}</h3>
      <PreviewPeriod label="上午" period={draft.morning} />
      <PreviewPeriod label="下午" period={draft.afternoon} />
      <PreviewPeriod label="晚上" period={draft.evening} />

      <section className="ai-recommendations">
        <strong>附近推荐</strong>
        {draft.recommendations.map((recommendation) => (
          <div key={recommendation.title}>
            <span>{recommendation.title}</span>
            <ul>
              {recommendation.items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        ))}
      </section>

      <section className="ai-reminders">
        <strong>提醒</strong>
        <ul>
          {draft.reminders.map((reminder) => (
            <li key={reminder}>{reminder}</li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function AiRunEventsPanel({
  events,
  loading,
  onLoad
}: {
  events: AiAgentRunEvents | null;
  loading: boolean;
  onLoad: () => void;
}) {
  return (
    <section className="ai-run-events">
      <div className="ai-run-events-header">
        <div>
          <strong>本次 AI 执行记录</strong>
          <p>{events ? `${events.modelName} · ${formatRunStatus(events.status)} · ${events.events.length} 条事件` : "查看工具调用、RAG、草稿和错误事件。"}</p>
        </div>
        <button className="secondary-button" disabled={loading} onClick={onLoad} type="button">
          {loading ? "读取中" : events ? "刷新记录" : "查看记录"}
        </button>
      </div>
      {events ? (
        <div className="ai-run-event-list">
          {events.events.map((event) => (
            <article className="ai-run-event-item" key={event.id}>
              <span>{formatEventType(event.eventType)}</span>
              <strong>{formatEventMessage(event)}</strong>
              <p>{formatEventDetail(event)}</p>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function AiMemoryReferences({ memories, isGenerating }: { memories: MemoryReferenceState | null; isGenerating: boolean }) {
  if (memories === null) {
    return (
      <section className="ai-memory-references muted">
        <strong>{isGenerating ? "正在查找你们的旅行记忆" : "旅行记忆"}</strong>
        <p>{isGenerating ? "AI 会先参考当前空间里的日记和计划，再生成这一天的安排。" : "还没有检索到可展示的记忆。"}</p>
      </section>
    );
  }

  if (!memories.success) {
    return (
      <section className="ai-memory-references warning">
        <strong>记忆检索暂时不可用</strong>
        <p>{memories.errorMessage || "本次将不参考历史记忆，但 AI 会继续根据你输入的地点和时间安排生成计划。"}</p>
      </section>
    );
  }

  if (memories.items.length === 0) {
    return (
      <section className="ai-memory-references">
        <strong>这次没有匹配到可参考的历史记忆</strong>
        <p>AI 会按你输入的地点、时间安排和天气信息来规划。</p>
      </section>
    );
  }

  const visibleMemories = getTopMemories(memories.items, visibleMemoryLimit);
  const hiddenCount = memories.items.length - visibleMemories.length;

  return (
    <section className="ai-memory-references">
      <strong>已参考 {memories.items.length} 条旅行记忆</strong>
      <details className="ai-memory-details">
        <summary>查看 AI 为什么这样规划</summary>
        {hiddenCount > 0 ? <p className="ai-memory-limit-note">仅展示 RAG 检索最相关的 Top {visibleMemoryLimit}，另外 {hiddenCount} 条已用于 AI 判断。</p> : null}
        <div className="ai-memory-list">
          {visibleMemories.map((memory) => (
            <article className="ai-memory-item" key={memory.memoryId}>
              <div className="ai-memory-meta">
                <span>{formatMemorySource(memory)}</span>
                <span className={`ai-memory-match ${formatMemoryMatchClass(memory.score)}`}>{formatMemoryMatchLevel(memory.score)}</span>
              </div>
              <p className="ai-memory-reason">{formatMemoryReason(memory)}</p>
              <p>{trimMemoryContent(memory.content)}</p>
            </article>
          ))}
        </div>
      </details>
    </section>
  );
}

function AiStreamingDraft({ text }: { text: string }) {
  return (
    <section className="ai-streaming-draft" aria-live="polite">
      <strong>实时生成内容</strong>
      <p>{formatStreamingDraftText(text)}</p>
    </section>
  );
}

function PreviewPeriod({ label, period }: { label: string; period: PeriodDraft }) {
  return (
    <section className="ai-preview-period">
      <strong>{label}</strong>
      <p>{period.content}</p>
    </section>
  );
}

class AiGenerationStreamError extends Error {
  info: AiGenerationError;

  constructor(info: AiGenerationError) {
    super(info.message);
    this.name = "AiGenerationStreamError";
    this.info = info;
  }
}

async function streamGeneratePlan({
  dayId,
  destination,
  places,
  mustVisitPlaces,
  hotelLocation,
  morningMode,
  afternoonMode,
  eveningMode,
  notes,
  revisionInstruction,
  regenerateMode,
  sourceDraftId,
  onProgress,
  onAgentStep,
  onToolResult,
  onDraftDelta,
  onMemories
}: {
  dayId: number;
  destination: string;
  places: string[];
  mustVisitPlaces: string[];
  hotelLocation: string;
  morningMode: PeriodMode;
  afternoonMode: PeriodMode;
  eveningMode: PeriodMode;
  notes: string;
  revisionInstruction: string;
  regenerateMode: RegenerateMode;
  sourceDraftId: number | null;
  onProgress: (message: string) => void;
  onAgentStep: (step: Pick<AgentStep, "step" | "status" | "message">) => void;
  onToolResult: (result: ToolResult) => void;
  onDraftDelta: (text: string) => void;
  onMemories: (memories: MemoryReferenceState) => void;
}) {
  const response = await fetch(`${API_BASE_URL}/api/ai/plan-days/${dayId}/generate-stream`, {
    method: "POST",
    credentials: "include",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      destination,
      places,
      mustVisitPlaces,
      hotelLocation,
      morningMode,
      afternoonMode,
      eveningMode,
      notes,
      revisionInstruction,
      regenerateMode,
      sourceDraftId
    })
  });

  if (!response.ok || !response.body) {
    if (response.status === 401) {
      window.location.href = "/login";
      throw new Error("请先登录");
    }
    throw new Error(await readStreamError(response));
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let finalDraft: AiDraft | null = null;
  let streamError: AiGenerationError | null = null;
  let shouldStopReading = false;

  function handleSsePart(part: string) {
    const event = parseSseEvent(part);
    if (!event) {
      return;
    }

    let data: Record<string, unknown>;
    try {
      data = JSON.parse(event.data) as Record<string, unknown>;
    } catch {
      streamError = {
        type: "SSE_PARSE_FAILED",
        stage: "STREAM",
        message: "AI 返回的数据格式不正确",
        detail: event.data
      };
      return;
    }

    if (event.event === "progress") {
      onProgress(typeof data.message === "string" ? data.message : "正在生成");
    }
    if (event.event === "agent-step") {
      const agentStep = parseAgentStep(data);
      if (agentStep) {
        onAgentStep(agentStep);
      }
    }
    if (event.event === "tool-result") {
      const toolResult = parseToolResult(data);
      if (toolResult) {
        onToolResult(toolResult);
      }
    }
    if (event.event === "draft-delta") {
      const text = typeof data.text === "string" ? data.text : "";
      if (text) {
        onDraftDelta(text);
      }
    }
    if (event.event === "memories") {
      onMemories(parseMemoryReferenceState(data));
    }
    if (event.event === "draft") {
      finalDraft = data as AiDraft;
      shouldStopReading = true;
    }
    if (event.event === "error") {
      streamError = parseAiGenerationError(data);
      shouldStopReading = true;
    }
  }

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split("\n\n");
    buffer = parts.pop() ?? "";

    for (const part of parts) {
      handleSsePart(part);
      if (shouldStopReading) {
        break;
      }
    }

    if (shouldStopReading) {
      await reader.cancel().catch(() => undefined);
      buffer = "";
      break;
    }
  }

  buffer += decoder.decode();
  if (buffer.trim()) {
    handleSsePart(buffer);
  }
  if (streamError) {
    throw new AiGenerationStreamError(streamError);
  }
  if (!finalDraft) {
    throw new Error("AI 没有返回计划草稿");
  }
  return finalDraft;
}

function parseAgentStep(value: Record<string, unknown>): Pick<AgentStep, "step" | "status" | "message"> | null {
  const step = typeof value.step === "string" ? value.step : "";
  const status = typeof value.status === "string" ? value.status : "";
  const message = typeof value.message === "string" ? value.message : "";
  if (!isAgentStepKey(step) || !isAgentStepStatus(status) || !message.trim()) {
    return null;
  }
  return { step, status, message };
}

function parseToolResult(value: Record<string, unknown>): ToolResult | null {
  const toolName = typeof value.toolName === "string" ? value.toolName : "";
  const label = typeof value.label === "string" ? value.label : "";
  const status = typeof value.status === "string" ? value.status : "";
  const summary = typeof value.summary === "string" ? value.summary : "";
  if (!toolName.trim() || !label.trim() || !isAgentStepStatus(status) || !summary.trim()) {
    return null;
  }
  return { toolName, label, status, summary };
}

function isAgentStepKey(value: string): value is AgentStepKey {
  return ["MEMORY_SYNC", "MEMORY_RETRIEVAL", "PLAN_GENERATION", "DRAFT_SAVE"].includes(value);
}

function isAgentStepStatus(value: string): value is AgentStepStatus {
  return ["pending", "running", "done", "skipped", "failed"].includes(value);
}

function parseMemoryReferenceState(value: Record<string, unknown>): MemoryReferenceState {
  const success = typeof value.success === "boolean" ? value.success : true;
  const errorMessage = typeof value.errorMessage === "string" ? value.errorMessage : "";
  return {
    success,
    items: parseTravelMemories(value.items),
    errorMessage
  };
}

function parseAiGenerationError(value: Record<string, unknown>): AiGenerationError {
  const message = typeof value.message === "string" && value.message.trim() ? value.message : "AI 生成失败";
  return {
    type: typeof value.type === "string" && value.type.trim() ? value.type : "UNKNOWN",
    stage: typeof value.stage === "string" && value.stage.trim() ? value.stage : "UNKNOWN",
    message,
    detail: typeof value.detail === "string" ? value.detail : ""
  };
}

function formatGenerationErrorStage(stage: string) {
  const labels: Record<string, string> = {
    CONFIG: "配置检查",
    MODEL: "模型调用",
    PARSE: "JSON 解析",
    VALIDATE: "字段校验",
    STREAM: "流式传输",
    UNKNOWN: "未知阶段"
  };
  return labels[stage] ?? stage;
}

function parseTravelMemories(value: unknown): TravelMemory[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => {
      if (!item || typeof item !== "object") {
        return null;
      }
      const record = item as Record<string, unknown>;
      return {
        memoryId: typeof record.memoryId === "string" ? record.memoryId : `${record.sourceType ?? "memory"}-${record.sourceId ?? ""}`,
        sourceType: typeof record.sourceType === "string" ? record.sourceType : "",
        sourceId: typeof record.sourceId === "number" ? record.sourceId : 0,
        cityName: typeof record.cityName === "string" ? record.cityName : "",
        content: typeof record.content === "string" ? record.content : "",
        score: typeof record.score === "number" ? record.score : 0,
        reason: typeof record.reason === "string" ? record.reason : ""
      };
    })
    .filter((item): item is TravelMemory => Boolean(item && item.content.trim()));
}

function formatMemorySource(memory: TravelMemory) {
  const source = memory.sourceType === "PLAN_DAY" ? "计划" : "日记";
  return memory.cityName ? `${memory.cityName} · ${source}` : source;
}

function formatRunStatus(status: string) {
  const normalized = status.toUpperCase();
  if (normalized === "SUCCESS") {
    return "生成成功";
  }
  if (normalized === "APPLIED") {
    return "已应用";
  }
  if (normalized === "FAILED") {
    return "生成失败";
  }
  if (normalized === "RUNNING") {
    return "生成中";
  }
  return status || "未知状态";
}

function formatEventType(eventType: string) {
  const labels: Record<string, string> = {
    AGENT_STEP: "步骤",
    TOOL_RESULT: "工具",
    MEMORIES: "记忆",
    DRAFT_DELTA: "流式",
    DRAFT: "草稿",
    ERROR: "错误",
    PROGRESS: "进度"
  };
  return labels[eventType] ?? eventType;
}

function formatEventMessage(event: AiAgentEventItem) {
  const parsed = parseEventJson(event.eventJson);
  if (event.eventType === "TOOL_RESULT" && typeof parsed?.label === "string") {
    return parsed.label;
  }
  return event.eventMessage || formatEventType(event.eventType);
}

function formatEventDetail(event: AiAgentEventItem) {
  const parsed = parseEventJson(event.eventJson);
  if (event.eventType === "TOOL_RESULT") {
    return formatToolEventDetail(parsed, event.eventMessage);
  }
  if (event.eventType === "AGENT_STEP" && typeof parsed?.message === "string") {
    return parsed.message;
  }
  return event.eventMessage || "暂无摘要";
}

function formatToolEventDetail(parsed: Record<string, unknown> | null, fallback: string) {
  if (!parsed) {
    return fallback || "工具调用完成";
  }
  const summary = typeof parsed.summary === "string" ? parsed.summary : fallback;
  const toolName = typeof parsed.toolName === "string" ? parsed.toolName : "";
  const data = parsed.data && typeof parsed.data === "object" ? (parsed.data as Record<string, unknown>) : null;
  if (!data) {
    return summary || "工具调用完成";
  }

  if (toolName === "place_constraint") {
    const places = toStringList(data.places);
    const mustVisitPlaces = toStringList(data.mustVisitPlaces);
    const hotelLocation = typeof data.hotelLocation === "string" ? data.hotelLocation : "";
    const parts = [
      places.length ? `地点：${places.join("、")}` : "",
      mustVisitPlaces.length ? `必去：${mustVisitPlaces.join("、")}` : "",
      hotelLocation ? `酒店：${hotelLocation}` : ""
    ].filter(Boolean);
    return parts.length ? `${summary}；${parts.join("；")}` : summary || "地点约束已记录";
  }

  if (toolName === "weather_context") {
    const available = data.available === true;
    if (!available) {
      const tips = toStringList(data.tips);
      return tips[0] || summary || "天气信息不可用";
    }
    const city = typeof data.city === "string" ? data.city : "";
    const date = typeof data.date === "string" ? data.date : "";
    const weather = typeof data.weather === "string" ? data.weather : "";
    const rainProbability = typeof data.rainProbability === "number" ? `降雨 ${data.rainProbability}%` : "";
    return [city, date, weather, rainProbability].filter(Boolean).join(" · ") || summary || "天气上下文已记录";
  }

  if (toolName === "memory_retrieval") {
    const memories = Array.isArray(data.topMemories) ? data.topMemories.slice(0, visibleMemoryLimit) : [];
    const previews = memories
      .map((item) => {
        if (!item || typeof item !== "object") {
          return "";
        }
        const memory = item as Record<string, unknown>;
        const source = memory.sourceType === "PLAN_DAY" ? "计划" : "日记";
        const city = typeof memory.cityName === "string" && memory.cityName ? `${memory.cityName} · ` : "";
        const content = typeof memory.content === "string" ? memory.content : "";
        return content ? `${city}${source}：${truncateText(content, 42)}` : "";
      })
      .filter(Boolean);
    return previews.length ? `${summary}；${previews.join("；")}` : summary || "没有可用历史记忆";
  }

  return summary || "工具调用完成";
}

function parseEventJson(value: string): Record<string, unknown> | null {
  if (!value) {
    return null;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === "object" ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function toStringList(value: unknown) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
}

function truncateText(value: string, maxLength: number) {
  return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value;
}

function getTopMemories(memories: TravelMemory[], limit: number) {
  return [...memories].sort((first, second) => first.score - second.score).slice(0, limit);
}

function formatMemoryMatchLevel(score: number) {
  if (score <= 0.25) {
    return "匹配度高";
  }
  if (score <= 0.55) {
    return "匹配度中";
  }
  return "匹配度低";
}

function formatMemoryMatchClass(score: number) {
  if (score <= 0.25) {
    return "high";
  }
  if (score <= 0.55) {
    return "medium";
  }
  return "low";
}

function formatMemoryReason(memory: TravelMemory) {
  if (memory.reason.trim()) {
    return `参考原因：${memory.reason.trim()}`;
  }
  if (memory.sourceType === "PLAN_DAY") {
    return "参考原因：来自你们之前保存过的旅行计划，可帮助 AI 延续已安排过的路线和偏好。";
  }
  return "参考原因：来自你们之前写过的旅行日记，可帮助 AI 记住真实去过的地点和体验。";
}

function trimMemoryContent(content: string) {
  const normalized = content.replace(/\s+/g, " ").trim();
  return normalized.length > 72 ? `${normalized.slice(0, 72)}...` : normalized;
}

function formatStreamingDraftText(content: string) {
  return content.replace(/[{}\[\]",]/g, " ").replace(/\s+/g, " ").trim();
}

function parseSseEvent(chunk: string) {
  const lines = chunk.split("\n");
  let event = "";
  let data = "";
  for (const line of lines) {
    if (line.startsWith("event:")) {
      event = line.slice("event:".length).trim();
    }
    if (line.startsWith("data:")) {
      data += line.slice("data:".length).trim();
    }
  }
  if (!event || !data) {
    return null;
  }
  return { event, data };
}

async function readStreamError(response: Response) {
  try {
    const data = (await response.json()) as { message?: string };
    return data.message || "AI 生成失败";
  } catch {
    return "AI 生成失败";
  }
}

function formatDayLabel(day: PlanDay) {
  return day.date ? `${formatPlanDate(day.date)} · Day ${day.sortOrder || day.id}` : `未设置日期 · Day ${day.sortOrder || day.id}`;
}

function formatPlanDate(date: string) {
  const parsed = new Date(`${date}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) {
    return date;
  }
  return `${parsed.getFullYear()}年${parsed.getMonth() + 1}.${parsed.getDate()}`;
}

function isBeyondForecastRange(date: string) {
  if (!date) {
    return false;
  }
  const target = new Date(`${date}T00:00:00`);
  if (Number.isNaN(target.getTime())) {
    return false;
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diffDays = Math.ceil((target.getTime() - today.getTime()) / 86_400_000);
  return diffDays > 15;
}
