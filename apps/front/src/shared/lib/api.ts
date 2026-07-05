"use client";

export type CurrentUser = {
  userId: number;
  account: string;
  nickname: string;
};

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? resolveApiBaseUrl();

const CURRENT_USER_KEY = "love-travel-current-user";
const LOGIN_PATH = "/login";

export async function login(account: string, password: string) {
  const user = await requestJson<CurrentUser>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ account, password })
  });
  saveCurrentUser(user);
  return user;
}

export async function register(account: string, password: string, confirmPassword: string, nickname: string) {
  const user = await requestJson<CurrentUser>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ account, password, confirmPassword, nickname })
  });
  saveCurrentUser(user);
  return user;
}

export async function fetchCurrentUser() {
  const user = await requestJson<CurrentUser>("/api/auth/me");
  saveCurrentUser(user);
  return user;
}

export function getCurrentUser() {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(CURRENT_USER_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as CurrentUser;
  } catch {
    clearCurrentUser();
    return null;
  }
}

export function requireCurrentUser() {
  const user = getCurrentUser();
  if (!user) {
    redirectToLogin();
    throw new Error("请先登录");
  }
  return user;
}

export async function logout() {
  try {
    await requestJson<{ success: boolean }>("/api/auth/logout", {
      method: "POST"
    });
  } catch {
    // Local cleanup still happens if the server is offline.
  }
  clearCurrentUser();
  redirectToLogin();
}

export function saveCurrentUser(user: CurrentUser) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(user));
}

export function clearCurrentUser() {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem(CURRENT_USER_KEY);
}

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...init?.headers
    }
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    if (response.status === 401) {
      handleUnauthorized();
    }
    throw new Error(message);
  }

  const text = await response.text();
  if (!text) {
    return null as T;
  }

  return JSON.parse(text) as T;
}

export function toErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : "操作失败，请稍后再试";
}
async function readErrorMessage(response: Response) {
  let message = "请求失败，请稍后再试";
  try {
    const data = (await response.json()) as { message?: string };
    message = data.message || message;
  } catch {
    // Keep the default message when the server returns an empty error body.
  }
  return message;
}

function handleUnauthorized() {
  clearCurrentUser();
  redirectToLogin();
}

function redirectToLogin() {
  if (typeof window === "undefined" || window.location.pathname === LOGIN_PATH) {
    return;
  }

  window.location.href = LOGIN_PATH;
}

function resolveApiBaseUrl() {
  if (typeof window === "undefined") {
    return "http://127.0.0.1:8080";
  }

  return `${window.location.protocol}//${window.location.hostname}:8080`;
}
