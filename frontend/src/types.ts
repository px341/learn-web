export type AnalysisStatus = "queued" | "analyzing" | "completed" | "failed";
export type Mistake = {
  id: string;
  title: string;
  subject: string;
  chapter: string;
  type: string;
  status: AnalysisStatus;
  createdAt: string;
  accuracy: number;
  image?: string;
  analysis?: MistakeAnalysis;
};
export type MistakeAnalysis = {
  summary: string;
  knowledge: string[];
  steps: string[];
  suggestion: string;
  answer: string;
  confidence: number;
};
export type User = {
  id: string;
  name: string;
  email: string;
  credits: number;
  /** Garage 私有对象生成的短期预签名地址；数据库不保存该 URL。 */
  avatarUrl?: string | null;
};
