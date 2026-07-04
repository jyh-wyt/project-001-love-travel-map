"use client";

import { useEffect, useMemo, useState } from "react";
import { Bot, CalendarDays, ListChecks, Pencil, Plus, Trash2, X } from "lucide-react";
import { AiPlanDayDialog } from "@/features/ai/components/AiPlanDayDialog";
import { BottomNav } from "@/shared/components/BottomNav";
import { requestJson, toErrorMessage } from "@/shared/lib/api";
import { fetchCurrentSpace, getSpaceStatusLabel, type TravelSpace } from "@/features/space/lib/spaces";
import { useAuthGuard } from "@/features/auth/hooks/useAuthGuard";

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

type PendingDelete = {
  id: number;
  title: string;
};

type PendingSave = {
  id: number;
  title: string;
};

export default function PlansPage() {
  const { authLoading, authErrorMessage, currentUser } = useAuthGuard();
  const [space, setSpace] = useState<TravelSpace | null>(null);
  const [days, setDays] = useState<PlanDay[]>([]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [draft, setDraft] = useState<PlanDay | null>(null);
  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(null);
  const [pendingSave, setPendingSave] = useState<PendingSave | null>(null);
  const [previewDay, setPreviewDay] = useState<PlanDay | null>(null);
  const [aiDialogOpen, setAiDialogOpen] = useState(false);
  const [modifyChoiceOpen, setModifyChoiceOpen] = useState(false);
  const [manualEditing, setManualEditing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const activeDay = useMemo(() => days.find((day) => day.id === editingId) ?? null, [days, editingId]);
  const sortedDays = useMemo(() => [...days].sort(comparePlanDays), [days]);
  const filledDaysCount = days.filter((day) => day.title.trim() || day.detail.trim()).length;
  const canEdit = Boolean(space?.editable);
  const activeDayHasContent = Boolean(activeDay && hasPlanContent(activeDay));
  const isEditingPlan = manualEditing || !activeDayHasContent;

  useEffect(() => {
    let ignore = false;

    async function loadPlans() {
      if (authLoading) {
        return;
      }
      if (!currentUser) {
        setErrorMessage(authErrorMessage);
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setErrorMessage("");
        const [currentSpace, planDays] = await Promise.all([
          fetchCurrentSpace(),
          requestJson<PlanDay[]>("/api/plans/days")
        ]);
        if (ignore) {
          return;
        }
        setSpace(currentSpace);
        setDays(planDays);
        const firstDay = planDays[0] ?? null;
        setEditingId(firstDay?.id ?? null);
        setDraft(firstDay);
        setManualEditing(firstDay ? !hasPlanContent(firstDay) : false);
      } catch (error) {
        if (!ignore) {
          setErrorMessage(toErrorMessage(error));
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    loadPlans();
    return () => {
      ignore = true;
    };
  }, [authErrorMessage, authLoading, currentUser]);

  function openDay(day: PlanDay) {
    setEditingId(day.id);
    setDraft(day);
    setPreviewDay(day);
    setManualEditing(!hasPlanContent(day));
    setModifyChoiceOpen(false);
    setPendingSave(null);
    setErrorMessage("");
  }

  async function addDay() {
    if (saving || !canEdit) {
      return;
    }

    try {
      setSaving(true);
      setErrorMessage("");
      const nextDay = await requestJson<PlanDay>("/api/plans/days", {
        method: "POST",
        body: JSON.stringify({
          date: "",
          title: "",
          detail: ""
        })
      });
      setDays((current) => [...current, nextDay]);
      setEditingId(nextDay.id);
      setDraft(nextDay);
      setManualEditing(true);
      setModifyChoiceOpen(false);
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  function requestSaveDay() {
    if (!draft || saving || !canEdit) {
      return;
    }
    if (activeDayHasContent) {
      setPendingSave({ id: draft.id, title: draft.title });
      return;
    }
    void saveDay();
  }

  async function saveDay() {
    if (!draft || saving || !canEdit) {
      return;
    }

    try {
      setSaving(true);
      setErrorMessage("");
      const savedDay = await requestJson<PlanDay>(`/api/plans/days/${draft.id}`, {
        method: "PUT",
        body: JSON.stringify({
          date: draft.date,
          title: draft.title,
          detail: draft.detail
        })
      });
      setDays((current) => current.map((day) => (day.id === savedDay.id ? savedDay : day)));
      setEditingId(savedDay.id);
      setDraft(savedDay);
      setManualEditing(false);
      setPendingSave(null);
      setPreviewDay((current) => (current?.id === savedDay.id ? savedDay : current));
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  function cancelEdit() {
    if (activeDay) {
      setDraft(activeDay);
    }
    setManualEditing(!activeDay || !hasPlanContent(activeDay));
    setPendingSave(null);
    setErrorMessage("");
  }

  function openModifyChoice() {
    if (!canEdit) {
      return;
    }
    setModifyChoiceOpen(true);
    setErrorMessage("");
  }

  function startManualEdit() {
    setManualEditing(true);
    setModifyChoiceOpen(false);
    setErrorMessage("");
  }

  function startAiRegenerate() {
    setModifyChoiceOpen(false);
    setAiDialogOpen(true);
    setErrorMessage("");
  }

  async function confirmDelete() {
    if (!pendingDelete || saving || !canEdit) {
      return;
    }

    try {
      setSaving(true);
      setErrorMessage("");
      await requestJson<{ success: boolean }>(`/api/plans/days/${pendingDelete.id}`, {
        method: "DELETE"
      });

      const nextDays = days.filter((day) => day.id !== pendingDelete.id);
      setDays(nextDays);
      setPreviewDay((current) => (current?.id === pendingDelete.id ? null : current));
      if (editingId === pendingDelete.id) {
        const nextActive = [...nextDays].sort(comparePlanDays)[0] ?? null;
        setEditingId(nextActive?.id ?? null);
        setDraft(nextActive);
        setManualEditing(nextActive ? !hasPlanContent(nextActive) : false);
      }
      setPendingDelete(null);
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="page-panel plan-workspace">
        <div className="plan-header-row">
          <div>
            <p className="section-label">旅行计划</p>
            <h1>{space?.spaceName ?? "当前空间旅行计划"}</h1>
            <p className="page-summary">{space ? getSpaceStatusLabel(space) : "按 Day 记录每一天的安排。"}</p>
          </div>
          <button className="primary-button plan-add-button" disabled={loading || saving || !canEdit} onClick={addDay} type="button">
            <Plus aria-hidden="true" size={18} />
            新增一天
          </button>
        </div>

        {space && !space.editable ? <p className="plan-feedback warning">未邀请你的伴侣不可使用。</p> : null}
        {errorMessage ? <p className="plan-feedback error">{errorMessage}</p> : null}

        <div className="plan-stats" aria-label="计划概览">
          <span>
            <CalendarDays aria-hidden="true" size={16} />
            共 {days.length} 天
          </span>
          <span>
            <ListChecks aria-hidden="true" size={16} />
            已填写 {filledDaysCount} 天
          </span>
        </div>

        {loading ? (
          <div className="empty-state">
            <strong>正在加载计划</strong>
            <p>稍等一下，正在同步当前空间的旅行安排。</p>
          </div>
        ) : (
          <div className="plan-layout day-plan-layout">
            <div className="plan-day-list" aria-label="行程日期">
              {sortedDays.length > 0 ? (
                sortedDays.map((day, index) => (
                  <button
                    className={day.id === editingId ? "plan-day active" : "plan-day"}
                    key={day.id}
                    onClick={() => openDay(day)}
                    type="button"
                  >
                    <span className="plan-day-index">Day {index + 1}</span>
                    <div>
                      <span className="plan-day-date">{formatPlanDate(day.date)}</span>
                      <strong>{day.title || "未命名行程"}</strong>
                      <p className="plan-day-status">{day.detail.trim() ? "已写规划" : "待填写"}</p>
                      <span className="plan-day-view">点开查看完整规划</span>
                    </div>
                  </button>
                ))
              ) : (
                <div className="empty-state">
                  <strong>还没有计划</strong>
                  <p>点击新增一天，先把第一天要去哪里写下来。</p>
                </div>
              )}
            </div>

            {draft ? (
              <form className="plan-editor" onSubmit={(event) => event.preventDefault()}>
                <div className="ai-plan-entry">
                  <div>
                    <strong>AI 规划这一天</strong>
                    <span>{activeDayHasContent ? "已有规划后，请从修改入口选择自己修改或 AI 重新生成。" : "根据地点、天气和上午/下午/晚上安排生成草稿。"}</span>
                  </div>
                  {activeDayHasContent ? (
                    <button className="secondary-button" disabled={saving || !canEdit} onClick={openModifyChoice} type="button">
                      <Pencil aria-hidden="true" size={16} />
                      修改
                    </button>
                  ) : (
                    <button
                      className="secondary-button"
                      disabled={saving || !canEdit}
                      onClick={() => setAiDialogOpen(true)}
                      type="button"
                    >
                      AI 规划
                    </button>
                  )}
                </div>
                <label className="field-label">
                  日期
                  <input
                    disabled={saving || !canEdit || !isEditingPlan}
                    onChange={(event) => setDraft({ ...draft, date: event.target.value })}
                    type="date"
                    value={draft.date}
                  />
                </label>
                <label className="field-label">
                  当天标题
                  <input
                    disabled={saving || !canEdit || !isEditingPlan}
                    onChange={(event) => setDraft({ ...draft, title: event.target.value })}
                    placeholder="例如：到达青岛"
                    value={draft.title}
                  />
                </label>
                <label className="field-label">
                  当天安排
                  <textarea
                    disabled={saving || !canEdit || !isEditingPlan}
                    onChange={(event) => setDraft({ ...draft, detail: event.target.value })}
                    placeholder="写下这一天要去哪里、几点出发、有什么备注"
                    value={draft.detail}
                  />
                </label>
                <div className="compose-actions">
                  {isEditingPlan ? (
                    <>
                      <button className="secondary-button" disabled={saving || !canEdit} onClick={cancelEdit} type="button">
                        取消
                      </button>
                      <button
                        className="danger-text-button"
                        disabled={saving || !canEdit}
                        onClick={() => setPendingDelete({ id: draft.id, title: draft.title })}
                        type="button"
                      >
                        <Trash2 aria-hidden="true" size={16} />
                        删除这一天
                      </button>
                      <button className="primary-button" disabled={saving || !canEdit} onClick={requestSaveDay} type="button">
                        {saving ? "保存中" : "确定保存"}
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        className="danger-text-button"
                        disabled={saving || !canEdit}
                        onClick={() => setPendingDelete({ id: draft.id, title: draft.title })}
                        type="button"
                      >
                        <Trash2 aria-hidden="true" size={16} />
                        删除这一天
                      </button>
                      <button className="primary-button" disabled={saving || !canEdit} onClick={openModifyChoice} type="button">
                        <Pencil aria-hidden="true" size={16} />
                        修改
                      </button>
                    </>
                  )}
                </div>
              </form>
            ) : (
              <div className="empty-state plan-editor-empty">
                <strong>选择一天开始规划</strong>
                <p>新增或选择左侧 Day 后，可以编辑当天安排。</p>
              </div>
            )}
          </div>
        )}
      </section>

      {pendingDelete ? (
        <div className="confirm-backdrop" role="presentation">
          <section className="confirm-dialog" aria-label="删除行程确认" role="dialog" aria-modal="true">
            <h2>删除这一天的计划？</h2>
            <p>
              {pendingDelete.title?.trim()
                ? `删除后，这一天的内容会从计划里移除：${pendingDelete.title}`
                : "删除后，这一天的内容会从计划里移除。"}
            </p>
            <div className="compose-actions">
              <button className="secondary-button" disabled={saving} onClick={() => setPendingDelete(null)} type="button">
                取消
              </button>
              <button className="danger-button" disabled={saving} onClick={confirmDelete} type="button">
                确认删除
              </button>
            </div>
          </section>
        </div>
      ) : null}

      {pendingSave ? (
        <div className="confirm-backdrop" role="presentation">
          <section className="confirm-dialog" aria-label="保存修改确认" role="dialog" aria-modal="true">
            <h2>保存这次修改？</h2>
            <p>
              {pendingSave.title?.trim()
                ? `确认后会覆盖当前这一天的规划内容：${pendingSave.title}`
                : "确认后会覆盖当前这一天的规划内容。"}
            </p>
            <div className="compose-actions">
              <button className="secondary-button" disabled={saving} onClick={() => setPendingSave(null)} type="button">
                再看看
              </button>
              <button className="primary-button" disabled={saving} onClick={() => void saveDay()} type="button">
                {saving ? "保存中" : "确认保存"}
              </button>
            </div>
          </section>
        </div>
      ) : null}

      {modifyChoiceOpen && draft ? (
        <div className="confirm-backdrop" role="presentation">
          <section className="confirm-dialog plan-modify-dialog" aria-label="选择修改方式" role="dialog" aria-modal="true">
            <button aria-label="关闭修改方式选择" className="dialog-close-button" onClick={() => setModifyChoiceOpen(false)} type="button">
              <X aria-hidden="true" size={18} />
            </button>
            <h2>修改这一天的规划</h2>
            <p>可以自己编辑内容，也可以让 AI 基于当前规划重新生成。</p>
            <div className="plan-modify-options">
              <button className="plan-modify-option" disabled={saving || !canEdit} onClick={startManualEdit} type="button">
                <Pencil aria-hidden="true" size={20} />
                <span>
                  <strong>自己修改</strong>
                  <small>直接编辑日期、标题和当天安排，保存前会再次确认。</small>
                </span>
              </button>
              <button className="plan-modify-option" disabled={saving || !canEdit} onClick={startAiRegenerate} type="button">
                <Bot aria-hidden="true" size={20} />
                <span>
                  <strong>AI 重新生成</strong>
                  <small>进入 AI 弹窗，可以彻底重写，也可以按你的要求修改。</small>
                </span>
              </button>
            </div>
          </section>
        </div>
      ) : null}

      {previewDay ? (
        <div className="confirm-backdrop" role="presentation">
          <section className="plan-preview-dialog" aria-label="当天规划详情" role="dialog" aria-modal="true">
            <button aria-label="关闭当天规划详情" className="dialog-close-button" onClick={() => setPreviewDay(null)} type="button">
              <X aria-hidden="true" size={18} />
            </button>
            <span className="plan-preview-date">{formatPlanDate(previewDay.date)}</span>
            <h2>{previewDay.title || "未命名行程"}</h2>
            <PlanPreviewContent detail={previewDay.detail} />
            <div className="compose-actions">
              <button className="primary-button" onClick={() => setPreviewDay(null)} type="button">
                我知道了
              </button>
            </div>
          </section>
        </div>
      ) : null}

      {draft ? (
        <AiPlanDayDialog
          day={draft}
          isOpen={aiDialogOpen}
          onApply={(nextDay) => {
            setDays((current) => current.map((day) => (day.id === nextDay.id ? nextDay : day)));
            setDraft(nextDay);
            setEditingId(nextDay.id);
            setManualEditing(false);
            setModifyChoiceOpen(false);
            setPreviewDay((current) => (current?.id === nextDay.id ? nextDay : current));
          }}
          onClose={() => setAiDialogOpen(false)}
        />
      ) : null}

      <BottomNav active="plans" />
    </main>
  );
}

function PlanPreviewContent({ detail }: { detail: string }) {
  const trimmed = detail.trim();
  const sections = parsePlanDetail(trimmed);

  if (!trimmed) {
    return <div className="plan-preview-content empty">这一天还没有写详细规划，可以在右侧编辑后保存。</div>;
  }

  if (sections.length === 0) {
    return <div className="plan-preview-content">{trimmed}</div>;
  }

  return (
    <div className="plan-preview-content structured">
      {sections.map((section) => (
        <section className="plan-preview-section" key={section.label}>
          <strong>{section.label}</strong>
          <p>{section.content}</p>
        </section>
      ))}
    </div>
  );
}

function hasPlanContent(day: PlanDay) {
  return Boolean(day.title.trim() || day.detail.trim());
}

function parsePlanDetail(detail: string) {
  if (!detail) {
    return [];
  }

  const labelPattern = /(上午|下午|晚上|附近推荐|提醒)：/g;
  const matches = [...detail.matchAll(labelPattern)];
  if (matches.length < 2) {
    return [];
  }

  return matches
    .map((match, index) => {
      const label = match[1];
      const contentStart = (match.index ?? 0) + match[0].length;
      const contentEnd = matches[index + 1]?.index ?? detail.length;
      return {
        label,
        content: detail.slice(contentStart, contentEnd).trim()
      };
    })
    .filter((section) => section.content);
}

function formatPlanDate(date: string) {
  if (!date) {
    return "未设置日期";
  }

  const parsed = new Date(`${date}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) {
    return date;
  }

  return `${parsed.getFullYear()}年${parsed.getMonth() + 1}.${parsed.getDate()}`;
}

function comparePlanDays(left: PlanDay, right: PlanDay) {
  if (left.date && right.date && left.date !== right.date) {
    return left.date.localeCompare(right.date);
  }
  if (left.date && !right.date) {
    return -1;
  }
  if (!left.date && right.date) {
    return 1;
  }
  return left.sortOrder - right.sortOrder || left.id - right.id;
}
