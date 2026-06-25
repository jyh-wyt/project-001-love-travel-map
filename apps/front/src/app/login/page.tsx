"use client";

import { useEffect, useState } from "react";
import { Eye, EyeOff, Heart, LogIn, UserPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { fetchCurrentUser, login, register, toErrorMessage } from "@/shared/lib/api";

type AuthMode = "login" | "register";

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<AuthMode>("login");
  const [account, setAccount] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [nickname, setNickname] = useState("");
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const isRegister = mode === "register";

  useEffect(() => {
    let ignore = false;

    async function syncLoginState() {
      try {
        await fetchCurrentUser();
        if (!ignore) {
          router.replace(resolveLoginRedirectPath());
        }
      } catch {
        // Staying on the login page is correct when the session is missing or expired.
      }
    }

    syncLoginState();
    return () => {
      ignore = true;
    };
  }, [router]);

  async function submit() {
    if (saving) {
      return;
    }

    if (isRegister) {
      if (!/^[A-Za-z0-9_]{5,20}$/.test(account.trim())) {
        setErrorMessage("账号只能使用英文字母、数字和下划线，长度 5 到 20 位。");
        return;
      }
      if (nickname.trim().length < 2 || nickname.trim().length > 20) {
        setErrorMessage("用户名长度需要在 2 到 20 个字符之间。");
        return;
      }
      if (password.length < 8) {
        setErrorMessage("密码至少需要 8 位。");
        return;
      }
      if (password !== confirmPassword) {
        setErrorMessage("两次输入的密码不一致。");
        return;
      }
    }

    try {
      setSaving(true);
      setErrorMessage("");
      if (isRegister) {
        await register(account, password, confirmPassword, nickname);
      } else {
        await login(account, password);
      }
      router.replace(resolveLoginRedirectPath());
    } catch (error) {
      setErrorMessage(toErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="login-shell">
      <section className="login-panel" aria-label="登录情侣旅行地图">
        <div className="login-brand">
          <span>
            <Heart aria-hidden="true" size={20} />
          </span>
          <div>
            <p className="section-label">情侣旅行地图</p>
            <h1>{isRegister ? "创建你的旅行账号" : "欢迎回来"}</h1>
          </div>
        </div>

        <p className="page-summary">登录后，你的地图、城市日记和旅行计划会保存到自己的账号里。绑定对方后，两个人共享同一个空间。</p>

        <div className="auth-tabs" role="tablist" aria-label="登录方式">
          <button className={!isRegister ? "active" : ""} onClick={() => setMode("login")} type="button">
            登录
          </button>
          <button className={isRegister ? "active" : ""} onClick={() => setMode("register")} type="button">
            注册
          </button>
        </div>

        {errorMessage ? <p className="plan-feedback error">{errorMessage}</p> : null}

        <form className="login-form" onSubmit={(event) => event.preventDefault()}>
          <label className="field-label">
            账号
            <input
              autoComplete="username"
              disabled={saving}
              maxLength={20}
              onChange={(event) => setAccount(event.target.value)}
              placeholder="例如：qingdao2026"
              value={account}
            />
            {isRegister ? <span className="field-hint">账号不能重复，只能使用英文字母、数字和下划线，5 到 20 位。</span> : null}
          </label>
          {isRegister ? (
            <label className="field-label">
              用户名
              <input
                autoComplete="nickname"
                disabled={saving}
                maxLength={20}
                onChange={(event) => setNickname(event.target.value)}
                placeholder="例如：焦焦"
                value={nickname}
              />
              <span className="field-hint">用户名不能重复，2 到 20 个字符。</span>
            </label>
          ) : null}
          <label className="field-label">
            密码
            <span className="password-field">
              <input
                autoComplete={isRegister ? "new-password" : "current-password"}
                disabled={saving}
                onChange={(event) => setPassword(event.target.value)}
                placeholder={isRegister ? "至少 8 位" : "请输入密码"}
                type={showPassword ? "text" : "password"}
                value={password}
              />
              <button
                aria-label={showPassword ? "隐藏密码" : "显示密码"}
                disabled={saving}
                onClick={() => setShowPassword((current) => !current)}
                type="button"
              >
                {showPassword ? <EyeOff aria-hidden="true" size={18} /> : <Eye aria-hidden="true" size={18} />}
              </button>
            </span>
            {isRegister ? <span className="field-hint">密码至少 8 位。请不要使用和其他网站相同的密码。</span> : null}
          </label>
          {isRegister ? (
            <label className="field-label">
              确认密码
              <span className="password-field">
                <input
                  autoComplete="new-password"
                  disabled={saving}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  placeholder="再次输入密码"
                  type={showConfirmPassword ? "text" : "password"}
                  value={confirmPassword}
                />
                <button
                  aria-label={showConfirmPassword ? "隐藏确认密码" : "显示确认密码"}
                  disabled={saving}
                  onClick={() => setShowConfirmPassword((current) => !current)}
                  type="button"
                >
                  {showConfirmPassword ? <EyeOff aria-hidden="true" size={18} /> : <Eye aria-hidden="true" size={18} />}
                </button>
              </span>
              <span className="field-hint">两次密码一致后才能注册成功。</span>
            </label>
          ) : null}

          <button className="primary-button login-submit" disabled={saving} onClick={submit} type="button">
            {isRegister ? <UserPlus aria-hidden="true" size={18} /> : <LogIn aria-hidden="true" size={18} />}
            {saving ? "处理中" : isRegister ? "注册并进入" : "登录"}
          </button>
        </form>
      </section>
    </main>
  );
}

function resolveLoginRedirectPath() {
  if (typeof window === "undefined") {
    return "/";
  }

  const next = new URLSearchParams(window.location.search).get("next");
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/login")) {
    return "/";
  }

  return next;
}
