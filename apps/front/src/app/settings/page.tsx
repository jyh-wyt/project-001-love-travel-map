"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Eye, EyeOff, KeyRound, LogOut, Moon, Pencil, Settings, Sun, Unlink, UserRound, X } from "lucide-react";
import { getCurrentUser, logout, requestJson, saveCurrentUser, toErrorMessage, type CurrentUser } from "@/shared/lib/api";
import { useAuthGuard } from "@/features/auth/hooks/useAuthGuard";

type CoupleSpace = {
  spaceId: number;
  spaceName: string;
  creatorUserId: number;
  memberCount: number;
  memberLimit: number;
};

type ThemeMode = "light" | "dark";
type SettingsDialog = "nickname" | "password" | null;

const THEME_KEY = "love-travel-theme";

export default function SettingsPage() {
  const { authLoading, authErrorMessage, currentUser: guardedUser } = useAuthGuard();
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [nickname, setNickname] = useState("");
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showOldPassword, setShowOldPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [activeDialog, setActiveDialog] = useState<SettingsDialog>(null);
  const [theme, setTheme] = useState<ThemeMode>("light");
  const [space, setSpace] = useState<CoupleSpace | null>(null);
  const [confirmUnbind, setConfirmUnbind] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    if (authLoading) {
      return;
    }
    if (!guardedUser) {
      setCurrentUser(null);
      setErrorMessage(authErrorMessage);
      return;
    }
    setCurrentUser(guardedUser);
    if (!activeDialog) {
      setNickname(guardedUser.nickname);
    }
    loadCurrentSpace();
  }, [activeDialog, authErrorMessage, authLoading, guardedUser]);

  useEffect(() => {
    const storedTheme = window.localStorage.getItem(THEME_KEY) === "dark" ? "dark" : "light";
    applyTheme(storedTheme);
    setTheme(storedTheme);
  }, []);

  const user = currentUser ?? getCurrentUser();
  const canUnbind = Boolean(space && space.memberCount >= 2);

  async function loadCurrentSpace() {
    try {
      const currentSpace = await requestJson<CoupleSpace | null>("/api/spaces/current");
      setSpace(currentSpace);
    } catch {
      setSpace(null);
    }
  }

  async function updateNickname() {
    if (!user || saving) {
      return;
    }
    try {
      setSaving(true);
      setMessage("");
      setErrorMessage("");
      const updated = await requestJson<CurrentUser>("/api/auth/me/nickname", {
        method: "PATCH",
        body: JSON.stringify({ nickname })
      });
      saveCurrentUser(updated);
      setCurrentUser(updated);
      setNickname(updated.nickname);
      setActiveDialog(null);
      setMessage("用户名已修改。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  async function updatePassword() {
    if (!user || saving) {
      return;
    }
    try {
      setSaving(true);
      setMessage("");
      setErrorMessage("");
      await requestJson<CurrentUser>("/api/auth/me/password", {
        method: "PATCH",
        body: JSON.stringify({ oldPassword, newPassword, confirmPassword })
      });
      closePasswordFields();
      setActiveDialog(null);
      setMessage("密码已修改，下次登录请使用新密码。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  async function unbindSpace() {
    if (!user || saving) {
      return;
    }
    try {
      setSaving(true);
      setMessage("");
      setErrorMessage("");
      const personalSpace = await requestJson<CoupleSpace>("/api/spaces/unbind", {
        method: "POST",
        body: JSON.stringify({})
      });
      setSpace(personalSpace);
      setConfirmUnbind(false);
      setMessage("已解绑，现在回到你的个人空间。");
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  function switchTheme(nextTheme: ThemeMode) {
    applyTheme(nextTheme);
    window.localStorage.setItem(THEME_KEY, nextTheme);
    setTheme(nextTheme);
  }

  function openNicknameDialog() {
    setNickname(user?.nickname ?? "");
    setErrorMessage("");
    setMessage("");
    setActiveDialog("nickname");
  }

  function openPasswordDialog() {
    closePasswordFields();
    setErrorMessage("");
    setMessage("");
    setActiveDialog("password");
  }

  function closeDialog() {
    if (saving) {
      return;
    }
    setActiveDialog(null);
    closePasswordFields();
  }

  function closePasswordFields() {
    setOldPassword("");
    setNewPassword("");
    setConfirmPassword("");
    setShowOldPassword(false);
    setShowNewPassword(false);
    setShowConfirmPassword(false);
  }

  return (
    <main className="app-shell detail-shell">
      <Link className="back-link" href="/space">
        <ArrowLeft aria-hidden="true" size={18} />
        返回我的页面
      </Link>

      <section className="page-panel settings-page-panel">
        <p className="section-label">设置</p>
        <div className="settings-heading">
          <span>
            <Settings aria-hidden="true" size={22} />
          </span>
          <div>
            <h1>账号设置</h1>
            <p>管理用户名、密码、情侣空间绑定和页面显示方式。</p>
          </div>
        </div>

        <div className="profile-card" aria-label="当前账号">
          <div className="profile-avatar">
            <UserRound aria-hidden="true" size={22} />
          </div>
          <div>
            <strong>{user?.nickname ?? "未登录"}</strong>
            <span>账号：{user?.account ?? "-"}</span>
          </div>
        </div>

        {errorMessage ? <p className="plan-feedback error">{errorMessage}</p> : null}
        {message ? <p className="plan-feedback success">{message}</p> : null}

        <div className="settings-section-list compact">
          <section className="settings-section compact-row">
            <div>
              <h2>修改用户名</h2>
              <p>用户名会显示在“我”的页面和共同编辑记录里。</p>
            </div>
            <button className="secondary-button settings-action-button" disabled={saving} onClick={openNicknameDialog} type="button">
              <Pencil aria-hidden="true" size={18} />
              修改用户名
            </button>
          </section>

          <section className="settings-section compact-row">
            <div>
              <h2>修改密码</h2>
              <p>新密码至少 8 位，确认密码需要和新密码一致。</p>
            </div>
            <button className="secondary-button settings-action-button" disabled={saving} onClick={openPasswordDialog} type="button">
              <KeyRound aria-hidden="true" size={18} />
              修改密码
            </button>
          </section>

          <section className="settings-section compact-row">
            <div>
              <h2>情侣空间解绑</h2>
              <p>{canUnbind ? "解绑后你会回到个人空间，不再看到对方共享的地图和计划。" : "当前是个人空间，暂时不需要解绑。"}</p>
            </div>
            <button className="secondary-button settings-action-button" disabled={!canUnbind || saving} onClick={() => setConfirmUnbind(true)} type="button">
              <Unlink aria-hidden="true" size={18} />
              解绑情侣空间
            </button>
          </section>

          <section className="settings-section">
            <div>
              <h2>显示模式</h2>
              <p>可以按你当前使用环境切换浅色或深色显示。</p>
            </div>
            <div className="theme-toggle" aria-label="显示模式">
              <button className={theme === "light" ? "active" : ""} onClick={() => switchTheme("light")} type="button">
                <Sun aria-hidden="true" size={18} />
                浅色
              </button>
              <button className={theme === "dark" ? "active" : ""} onClick={() => switchTheme("dark")} type="button">
                <Moon aria-hidden="true" size={18} />
                深色
              </button>
            </div>
          </section>
        </div>

        <button className="danger-button settings-logout" onClick={logout} type="button">
          <LogOut aria-hidden="true" size={18} />
          退出登录
        </button>
      </section>

      {confirmUnbind ? (
        <div className="confirm-backdrop" role="presentation">
          <section className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="unbind-title">
            <h2 id="unbind-title">确认解绑情侣空间？</h2>
            <p>解绑后你会回到个人空间，对方不会被退出。这个操作不会删除对方的数据。</p>
            <div className="compose-actions">
              <button className="secondary-button" disabled={saving} onClick={() => setConfirmUnbind(false)} type="button">
                取消
              </button>
              <button className="danger-button" disabled={saving} onClick={unbindSpace} type="button">
                确认解绑
              </button>
            </div>
          </section>
        </div>
      ) : null}

      {activeDialog ? (
        <div className="confirm-backdrop" role="presentation">
          <section className="settings-dialog" role="dialog" aria-modal="true" aria-labelledby="settings-dialog-title">
            <button className="dialog-close-button" disabled={saving} onClick={closeDialog} type="button" aria-label="关闭">
              <X aria-hidden="true" size={18} />
            </button>
            {activeDialog === "nickname" ? (
              <>
                <h2 id="settings-dialog-title">修改用户名</h2>
                <p>用户名会显示在“我”的页面和共同编辑记录里。</p>
                <label className="field-label">
                  用户名
                  <input
                    disabled={saving}
                    maxLength={20}
                    onChange={(event) => setNickname(event.target.value)}
                    placeholder="请输入 2 到 20 位用户名"
                    value={nickname}
                  />
                </label>
                <div className="compose-actions">
                  <button className="secondary-button" disabled={saving} onClick={closeDialog} type="button">
                    取消
                  </button>
                  <button className="primary-button" disabled={saving || !nickname.trim()} onClick={updateNickname} type="button">
                    保存
                  </button>
                </div>
              </>
            ) : (
              <>
                <h2 id="settings-dialog-title">修改密码</h2>
                <p>新密码至少 8 位，确认密码需要和新密码一致。</p>
                <PasswordInput label="原密码" saving={saving} show={showOldPassword} setShow={setShowOldPassword} value={oldPassword} setValue={setOldPassword} />
                <PasswordInput label="新密码" saving={saving} show={showNewPassword} setShow={setShowNewPassword} value={newPassword} setValue={setNewPassword} minLength={8} />
                <PasswordInput label="确认新密码" saving={saving} show={showConfirmPassword} setShow={setShowConfirmPassword} value={confirmPassword} setValue={setConfirmPassword} minLength={8} />
                <div className="compose-actions">
                  <button className="secondary-button" disabled={saving} onClick={closeDialog} type="button">
                    取消
                  </button>
                  <button className="primary-button" disabled={saving || !oldPassword || !newPassword || !confirmPassword} onClick={updatePassword} type="button">
                    保存
                  </button>
                </div>
              </>
            )}
          </section>
        </div>
      ) : null}
    </main>
  );
}

function PasswordInput({
  label,
  saving,
  show,
  setShow,
  value,
  setValue,
  minLength
}: {
  label: string;
  saving: boolean;
  show: boolean;
  setShow: (value: boolean | ((current: boolean) => boolean)) => void;
  value: string;
  setValue: (value: string) => void;
  minLength?: number;
}) {
  return (
    <label className="field-label">
      {label}
      <span className="password-field">
        <input disabled={saving} minLength={minLength} onChange={(event) => setValue(event.target.value)} type={show ? "text" : "password"} value={value} />
        <button onClick={() => setShow((current) => !current)} type="button">
          {show ? <EyeOff aria-hidden="true" size={18} /> : <Eye aria-hidden="true" size={18} />}
        </button>
      </span>
    </label>
  );
}

function applyTheme(theme: ThemeMode) {
  document.documentElement.dataset.theme = theme;
}