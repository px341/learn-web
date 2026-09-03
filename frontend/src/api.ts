import { auth } from "./auth";
import type {
  AnalysisStatus,
  DashboardStats,
  MistakeDetail,
  MistakeSummary,
  Page,
  PaymentOrder,
  PaymentPlan,
} from "./types";

type ApiResponse<T> = { data: T };
type ApiError = {
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
};

const apiUrl = (import.meta.env.VITE_API_URL || "/api").replace(/\/$/, "");

export class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = auth.getToken();
  if (!token) throw new ApiRequestError("当前未登录", 401);

  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${apiUrl}${path}`, { ...init, headers });
  const payload = (await response.json().catch(() => null)) as
    | ApiResponse<T>
    | ApiError
    | null;
  if (!response.ok) {
    if (response.status === 401) auth.logout();
    const error = payload as ApiError | null;
    const fieldMessage = error?.fieldErrors
      ? Object.values(error.fieldErrors)[0]
      : undefined;
    throw new ApiRequestError(
      fieldMessage || error?.message || "请求失败，请稍后重试",
      response.status,
      error?.code,
    );
  }
  if (!payload || !("data" in payload)) {
    throw new ApiRequestError("服务器返回的数据不完整", response.status);
  }
  return payload.data;
}

export type MistakeListQuery = {
  keyword?: string;
  subject?: string;
  status?: AnalysisStatus;
  mastered?: boolean;
  page?: number;
  size?: number;
  sort?: "createdAt,asc" | "createdAt,desc";
};

export const api = {
  dashboard: () => request<DashboardStats>("/dashboard/stats"),

  listMistakes: (query: MistakeListQuery = {}) => {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== "") params.set(key, String(value));
    });
    const suffix = params.size ? `?${params}` : "";
    return request<Page<MistakeSummary>>(`/mistakes${suffix}`);
  },

  getMistake: (id: string) =>
    request<MistakeDetail>(`/mistakes/${encodeURIComponent(id)}`),

  submitMistake: async (payload: {
    title?: string;
    subject: string;
    chapter?: string;
    type: string;
    text?: string;
    userAnswer?: string;
    image?: File;
  }) => {
    const body = new FormData();
    Object.entries(payload).forEach(([key, value]) => {
      if (value !== undefined && value !== "") body.append(key, value);
    });
    return request<{ mistake: MistakeSummary; creditsRemaining: number }>(
      "/mistakes",
      { method: "POST", body },
    );
  },

  updateMastery: (id: string, mastered: boolean) =>
    request<MistakeSummary>(`/mistakes/${encodeURIComponent(id)}/mastery`, {
      method: "PATCH",
      body: JSON.stringify({ mastered }),
    }),

  paymentPlans: () => request<PaymentPlan[]>("/payments/plans"),

  createMockPayment: (planId: string, idempotencyKey: string) =>
    request<PaymentOrder>("/payments/mock", {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ planId }),
    }),
};
