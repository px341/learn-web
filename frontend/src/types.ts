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
};
export type User = { name: string; email: string; credits: number };
