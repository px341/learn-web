export type AnalysisStatus = "queued" | "analyzing" | "completed" | "failed";

export type MistakeAnalysis = {
  summary: string;
  knowledge: string[];
  steps: string[];
  suggestion: string;
  answer: string;
  confidence: number;
};

export type MistakeSummary = {
  id: string;
  title: string;
  subject: string;
  chapter: string | null;
  type: string;
  status: AnalysisStatus;
  mastered: boolean;
  createdAt: string;
};

export type MistakeDetail = MistakeSummary & {
  questionText: string | null;
  userAnswer: string | null;
  image: { url: string; expiresAt: string } | null;
  analysis: MistakeAnalysis | null;
  failureMessage: string | null;
};

export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type DashboardStats = {
  total: number;
  weeklyNew: number;
  totalChangePercent: number;
  questionTypeCounts: { questionType: string; count: number }[];
};

export type PaymentPlan = {
  id: string;
  name: string;
  credits: number;
  priceFen: number;
  description: string;
  recommended: boolean;
};

export type PaymentOrder = {
  orderId: string;
  status: "pending" | "paid" | "failed";
  planId: string;
  planName: string;
  credits: number;
  amountFen: number;
  currency: string;
  createdAt: string;
};

export type User = {
  id: string;
  name: string;
  email: string;
  credits: number;
  /** Garage 私有对象生成的短期预签名地址；数据库不保存该 URL。 */
  avatarUrl?: string | null;
};
