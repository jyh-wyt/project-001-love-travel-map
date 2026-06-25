"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Check, Copy, HeartHandshake, Map, Plus, RefreshCw, UsersRound } from "lucide-react";
import { BottomNav } from "@/shared/components/BottomNav";
import { toErrorMessage } from "@/shared/lib/api";
import {
  activateSpace,
  createCoupleSpace,
  createInviteCode,
  fetchSpaces,
  getSpaceStatusLabel,
  getSpaceTypeLabel,
  joinSpaceByInviteCode,
  type InviteCode,
  type TravelSpace
} from "@/features/space/lib/spaces";
import { useAuthGuard } from "@/features/auth/hooks/useAuthGuard";

export default function SpacesPage() {
  const { authLoading, authErrorMessage, currentUser } = useAuthGuard();
  const [spaces, setSpaces] = useState<TravelSpace[]>([]);
  const [inviteCode, setInviteCode] = useState<InviteCode | null>(null);
  const [inviteSpaceId, setInviteSpaceId] = useState<number | null>(null);
  const [joinCode, setJoinCode] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const [message, setMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  const hasCoupleSpace = spaces.some((space) => space.spaceType === "COUPLE");
  const inviteCountdown = inviteCode ? getInviteCountdown(inviteCode.expireAt, now) : null;
  const inviteExpired = inviteCountdown !== null && inviteCountdown.totalSeconds <= 0;

  useEffect(() => {
    let ignore = false;

    async function loadSpaces() {
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
        const nextSpaces = await fetchSpaces();
        if (!ignore) {
          setSpaces(nextSpaces);
        }
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

    loadSpaces();
    return () => {
      ignore = true;
    };
  }, [authErrorMessage, authLoading, currentUser]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  async function reloadSpaces() {
    setSpaces(await fetchSpaces());
  }

  async function createCouple() {
    if (saving) {
      return;
    }
    try {
      setSaving(true);
      setMessage("");
      setErrorMessage("");
      await createCoupleSpace();
      await reloadSpaces();
      setMessage("情侣空间已创建，未邀请你的伴侣不可使用。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  async function switchSpace(spaceId: number) {
    if (saving) {
      return;
    }
    try {
      setSaving(true);
      setMessage("");
      setErrorMessage("");
      await activateSpace(spaceId);
      await reloadSpaces();
      setMessage("已切换当前旅行空间。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  async function makeInvite(spaceId: number) {
    if (saving) {
      return;
    }
    try {
      setSaving(true);
      setMessage("");
      setErrorMessage("");
      const nextCode = await createInviteCode(spaceId);
      setInviteCode(nextCode);
      setInviteSpaceId(spaceId);
      setNow(Date.now());
      setMessage("邀请码已生成，1 分钟内有效。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  async function joinSpace() {
    if (saving || !joinCode.trim()) {
      return;
    }
    try {
      setSaving(true);
      setMessage("");
      setErrorMessage("");
      await joinSpaceByInviteCode(joinCode);
      setJoinCode("");
      setInviteCode(null);
      await reloadSpaces();
      setMessage("加入成功，当前空间已切换到情侣空间。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  async function copyInviteCode() {
    if (!inviteCode) {
      return;
    }
    await navigator.clipboard.writeText(inviteCode.code);
    setMessage("邀请码已复制。");
  }

  return (
    <main className="app-shell detail-shell">
      <Link className="back-link" href="/space">
        <ArrowLeft aria-hidden="true" size={18} />
        返回我的页面
      </Link>

      <section className="page-panel">
        <p className="section-label">旅行空间</p>
        <div className="spaces-heading">
          <div>
            <h1>切换旅行空间</h1>
            <p>当前空间会决定首页地图、城市记录、照片和旅行计划显示哪一份数据。</p>
          </div>
          {!hasCoupleSpace ? (
            <button className="primary-button" disabled={loading || saving} onClick={createCouple} type="button">
              <Plus aria-hidden="true" size={18} />
              创建情侣空间
            </button>
          ) : null}
        </div>

        {errorMessage ? <p className="plan-feedback error">{errorMessage}</p> : null}
        {message ? <p className="plan-feedback success">{message}</p> : null}

        <div className="space-switch-list">
          {loading ? (
            <div className="empty-state">
              <strong>正在同步空间</strong>
              <p>稍等一下，正在读取你拥有的旅行空间。</p>
            </div>
          ) : (
            spaces.map((space) => (
              <article className={space.current ? "space-switch-card active" : "space-switch-card"} key={space.spaceId}>
                <div className={space.spaceType === "COUPLE" ? "space-icon couple" : "space-icon personal"}>
                  {space.spaceType === "COUPLE" ? <UsersRound aria-hidden="true" size={22} /> : <Map aria-hidden="true" size={22} />}
                </div>
                <div className="space-switch-body">
                  <span>{getSpaceTypeLabel(space)} · {space.memberCount}/{space.memberLimit}</span>
                  <strong>{space.spaceName}</strong>
                  <p>{getSpaceStatusLabel(space)}</p>
                  {inviteCode && inviteSpaceId === space.spaceId ? (
                    <div className={inviteExpired ? "invite-countdown expired" : "invite-countdown"}>
                      <strong>{inviteCode.code}</strong>
                      <span>{inviteExpired ? "已过期，请重新获取" : `剩余 ${inviteCountdown?.label}`}</span>
                    </div>
                  ) : null}
                </div>
                <div className="space-switch-actions">
                  {space.current ? (
                    <span className="current-badge">
                      <Check aria-hidden="true" size={15} />
                      当前使用中
                    </span>
                  ) : (
                    <button className="secondary-button" disabled={saving} onClick={() => switchSpace(space.spaceId)} type="button">
                      切换到此空间
                    </button>
                  )}
                  {space.spaceType === "COUPLE" && space.memberCount < space.memberLimit ? (
                    <button className="secondary-button" disabled={saving} onClick={() => makeInvite(space.spaceId)} type="button">
                      {saving ? <RefreshCw aria-hidden="true" size={16} /> : <HeartHandshake aria-hidden="true" size={16} />}
                      获取邀请码
                    </button>
                  ) : null}
                  {inviteCode && inviteSpaceId === space.spaceId ? (
                    <button className="secondary-button" disabled={inviteExpired} onClick={copyInviteCode} type="button">
                      <Copy aria-hidden="true" size={16} />
                      复制
                    </button>
                  ) : null}
                </div>
              </article>
            ))
          )}
        </div>

        <section className="join-space-panel" aria-label="加入情侣空间">
          <div>
            <h2>加入对方的情侣空间</h2>
            <p>输入对方发来的 6 位邀请码，加入后会自动切换到情侣空间。</p>
          </div>
          <div className="invite-form compact">
            <input
              disabled={loading || saving}
              maxLength={6}
              onChange={(event) => setJoinCode(event.target.value)}
              placeholder="例如 A9Q2MA"
              value={joinCode}
            />
            <button className="primary-button" disabled={loading || saving || !joinCode.trim()} onClick={joinSpace} type="button">
              加入空间
            </button>
          </div>
        </section>
      </section>
      <BottomNav active="space" />
    </main>
  );
}

function getInviteCountdown(value: string, now: number) {
  const expireAt = new Date(value.includes("T") ? value : value.replace(" ", "T")).getTime();
  if (Number.isNaN(expireAt)) {
    return {
      totalSeconds: 0,
      label: "00:00"
    };
  }

  const totalSeconds = Math.max(0, Math.ceil((expireAt - now) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return {
    totalSeconds,
    label: `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`
  };
}