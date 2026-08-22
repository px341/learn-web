import type { User } from "./types";

type AuthSession = {
  token: string;
  user: User;
};

type ApiResponse<T> = { data: T };
type ApiError = {
  message?: string;
  fieldErrors?: Record<string, string>;
};

const storageKey = "mistake-lab-auth";
const apiUrl = (import.meta.env.VITE_API_URL || "/api").replace(/\/$/, "");

const loadSession = (): AuthSession | null => {
  try {
    return JSON.parse(localStorage.getItem(storageKey) || "null");
  } catch {
    localStorage.removeItem(storageKey);
    return null;
  }
};

let session = loadSession();

const clearSession = () => {
  session = null;
  localStorage.removeItem(storageKey);
};

const request = async (path: string, body: unknown): Promise<AuthSession> => {
  const response = await fetch(`${apiUrl}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  const payload = (await response.json().catch(() => null)) as
    | ApiResponse<AuthSession>
    | ApiError
    | null;

  if (!response.ok) {
    const error = payload as ApiError | null;
    const fieldMessage = error?.fieldErrors
      ? Object.values(error.fieldErrors)[0]
      : undefined;
    throw new Error(fieldMessage || error?.message || "请求失败，请稍后重试");
  }

  const auth = (payload as ApiResponse<AuthSession> | null)?.data;
  if (!auth?.token || !auth.user) {
    throw new Error("服务器返回的登录数据不完整");
  }

  session = auth;
  localStorage.setItem(storageKey, JSON.stringify(session));
  return auth;
};

export const auth = {
  getUser: () => session?.user ?? null,
  getToken: () => session?.token ?? null,
  login: (email: string, password: string) =>
    request("/auth/login", { email, password }),
  register: (
    name: string,
    email: string,
    password: string,
    passwordConfirmation: string,
  ) => request("/auth/register", { name, email, password, passwordConfirmation }),
  refreshUser: async (): Promise<User> => {
    if (!session?.token) throw new Error("当前未登录");

    const response = await fetch(`${apiUrl}/auth/me`, {
      headers: { Authorization: `Bearer ${session.token}` },
    });
    const payload = (await response.json().catch(() => null)) as
      | ApiResponse<User>
      | ApiError
      | null;

    if (!response.ok) {
      if (response.status === 401) clearSession();
      throw new Error((payload as ApiError | null)?.message || "获取用户信息失败");
    }

    const user = (payload as ApiResponse<User> | null)?.data;
    if (!user?.id) throw new Error("服务器返回的用户数据不完整");

    session = { ...session, user };
    localStorage.setItem(storageKey, JSON.stringify(session));
    return user;
  },
  logout: clearSession,
  updateCredits: (credits: number) => {
    if (!session) return;
    session = { ...session, user: { ...session.user, credits } };
    localStorage.setItem(storageKey, JSON.stringify(session));
  },
};
