"use client";

import { useMemo, useState } from "react";
import { RadioGroup } from "@baseline-ui/core";
import { CloudSun, MapPin, RefreshCcw, Sparkles, X } from "lucide-react";
import { API_BASE_URL, requestJson, toErrorMessage } from "@/shared/lib/api";

type PlanDay = {
  id: number;
  date: string;
  title: string;
  detail: string;
  sortOrder: number;
};

type PeriodMode = "PLAY" | "REST";
type Step = "places" | "time" | "preview";
type GenerateState = "idle" | "generating" | "done";
type RegenerateMode = "NEW" | "REVISE" | "REWRITE";

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

type AiApplyResponse = {
  success: boolean;
  day: PlanDay;
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

export function AiPlanDayDialog({ day, isOpen, onClose, onApply }: AiPlanDayDialogProps) {
  const [step, setStep] = useState<Step>("places");
  const [places, setPlaces] = useState<string[]>([]);
  const [placeInput, setPlaceInput] = useState("");
  const [mustVisitPlaces, setMustVisitPlaces] = useState<string[]>([]);
  const [morningMode, setMorningMode] = useState<PeriodMode>("PLAY");
  const [afternoonMode, setAfternoonMode] = useState<PeriodMode>("PLAY");
  const [eveningMode, setEveningMode] = useState<PeriodMode>("REST");
  const [notes, setNotes] = useState("");
  const [generateState, setGenerateState] = useState<GenerateState>("idle");
  const [progressMessages, setProgressMessages] = useState<string[]>([]);
  const [draft, setDraft] = useState<AiDraft | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [regenerateChoiceOpen, setRegenerateChoiceOpen] = useState(false);

  const isDateBeyondForecast = useMemo(() => isBeyondForecastRange(day.date), [day.date]);
  const canContinueFromPlaces = places.length > 0;

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
    try {
      setStep("preview");
      setGenerateState("generating");
      setProgressMessages([]);
      setErrorMessage("");
      setRegenerateChoiceOpen(false);

      const nextDraft = await streamGeneratePlan({
        dayId: day.id,
        destination: inferDestination(),
        places,
        mustVisitPlaces,
        morningMode,
        afternoonMode,
        eveningMode,
        notes,
        regenerateMode: mode,
        sourceDraftId: draft?.draftId ?? null,
        onProgress: (message) => setProgressMessages((current) => [...current, message])
      });
      setDraft(nextDraft);
      setGenerateState("done");
    } catch (error) {
      setGenerateState("idle");
      setErrorMessage(toErrorMessage(error));
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
    onClose();
  }

  function inferDestination() {
    const firstPlace = places[0]?.trim();
    return firstPlace || "当前目的地";
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

        {errorMessage ? <p className="plan-feedback error">{errorMessage}</p> : null}

        <div className="ai-dialog-body">
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
              {generateState === "generating" ? (
                <div className="ai-progress-list" aria-live="polite">
                  {progressMessages.map((message) => (
                    <span key={message}>{message}</span>
                  ))}
                </div>
              ) : null}

              {draft && generateState === "done" ? <AiDraftPreview draft={draft} /> : null}

              {regenerateChoiceOpen ? (
                <div className="ai-regenerate-box">
                  <strong>你想怎么重新生成？</strong>
                  <p>两种方式都会消耗 1 次 AI 规划次数。</p>
                  <div className="ai-regenerate-actions">
                    <button className="secondary-button" onClick={() => void generatePlan("REVISE")} type="button">
                      基于当前规划修改
                    </button>
                    <button className="secondary-button" onClick={() => void generatePlan("REWRITE")} type="button">
                      彻底重写
                    </button>
                  </div>
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
            <>
              <button className="secondary-button" disabled={generateState === "generating"} onClick={() => setRegenerateChoiceOpen(true)} type="button">
                <RefreshCcw aria-hidden="true" size={16} />
                重新生成
              </button>
              <button className="secondary-button" disabled={generateState === "generating"} onClick={() => void closeDialog()} type="button">
                取消
              </button>
              <button className="primary-button" disabled={!draft || generateState === "generating"} onClick={() => void applyDraft()} type="button">
                应用到这一天
              </button>
            </>
          ) : null}
        </div>
      </section>
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

function PreviewPeriod({ label, period }: { label: string; period: PeriodDraft }) {
  return (
    <section className="ai-preview-period">
      <strong>{label}</strong>
      <p>{period.content}</p>
    </section>
  );
}

async function streamGeneratePlan({
  dayId,
  destination,
  places,
  mustVisitPlaces,
  morningMode,
  afternoonMode,
  eveningMode,
  notes,
  regenerateMode,
  sourceDraftId,
  onProgress
}: {
  dayId: number;
  destination: string;
  places: string[];
  mustVisitPlaces: string[];
  morningMode: PeriodMode;
  afternoonMode: PeriodMode;
  eveningMode: PeriodMode;
  notes: string;
  regenerateMode: RegenerateMode;
  sourceDraftId: number | null;
  onProgress: (message: string) => void;
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
      morningMode,
      afternoonMode,
      eveningMode,
      notes,
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

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split("\n\n");
    buffer = parts.pop() ?? "";

    for (const part of parts) {
      const event = parseSseEvent(part);
      if (!event) {
        continue;
      }
      const data = JSON.parse(event.data);
      if (event.event === "progress") {
        onProgress(data.message ?? "正在生成");
      }
      if (event.event === "draft") {
        finalDraft = data as AiDraft;
      }
      if (event.event === "error") {
        throw new Error(data.message ?? "AI 生成失败");
      }
    }
  }

  if (!finalDraft) {
    throw new Error("AI 没有返回计划草稿");
  }
  return finalDraft;
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
