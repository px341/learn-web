import { auth } from "./auth";
import type { Mistake } from "./types";

const seed: Mistake[] = [
  {
    id: "m-1",
    title: "二次函数图像与最值",
    subject: "数学",
    chapter: "函数",
    type: "概念不清",
    status: "completed",
    createdAt: "今天 09:42",
    accuracy: 62,
    analysis: {
      summary: "你混淆了开口方向与顶点坐标的关系，导致最值判断相反。",
      knowledge: ["二次函数顶点式", "开口方向与最值"],
      steps: [
        "将函数化为顶点式 y=a(x-h)²+k",
        "根据 a 的正负判断开口方向",
        "结合顶点坐标得出最值",
      ],
      suggestion: "建议重新练习 3 道顶点式变形题，重点关注 a 的符号。",
      answer: "当 a > 0 时有最小值 k；当 a < 0 时有最大值 k。",
    },
  },
  {
    id: "m-2",
    title: "牛顿第二定律综合题",
    subject: "物理",
    chapter: "力与运动",
    type: "审题错误",
    status: "completed",
    createdAt: "昨天 16:18",
    accuracy: 74,
    analysis: {
      summary: "受力分析中遗漏了摩擦力，造成合外力计算偏差。",
      knowledge: ["受力分析", "牛顿第二定律"],
      steps: [
        "隔离研究对象并画出受力图",
        "规定正方向并分解各力",
        "使用 F合=ma 列式计算",
      ],
      suggestion: "画受力图时逐项检查接触面和约束条件。",
      answer: "合外力应包含沿运动方向的摩擦力分量。",
    },
  },
  {
    id: "m-3",
    title: "英语长难句翻译",
    subject: "英语",
    chapter: "阅读理解",
    type: "方法不熟",
    status: "analyzing",
    createdAt: "昨天 11:05",
    accuracy: 81,
  },
  {
    id: "m-4",
    title: "有机物结构推断",
    subject: "化学",
    chapter: "有机化学",
    type: "粗心失误",
    status: "completed",
    createdAt: "周一 20:36",
    accuracy: 68,
    analysis: {
      summary: "官能团的连接顺序判断正确，但漏写了一个氢原子。",
      knowledge: ["官能团", "有机物结构式"],
      steps: [
        "数出每个碳原子的成键数",
        "补足氢原子至四价",
        "检查分子式与题目条件",
      ],
      suggestion: "完成结构式后做一次碳四价检查。",
      answer: "结构式中每个碳原子均需满足四条价键。",
    },
  },
];

const key = "mistake-lab-state";
type State = { mistakes: Mistake[] };
const load = (): State => {
  try {
    const stored = JSON.parse(localStorage.getItem(key) || "");
    return { mistakes: stored.mistakes || seed };
  } catch {
    return { mistakes: seed };
  }
};
let state = load();
const persist = () => localStorage.setItem(key, JSON.stringify(state));
export const api = {
  addCredits: (amount: number) => {
    const user = auth.getUser();
    if (user) auth.updateCredits(user.credits + amount);
  },
  listMistakes: () => state.mistakes,
  submit: (payload: {
    title: string;
    subject: string;
    chapter: string;
    type: string;
    image?: string;
  }) => {
    const item: Mistake = {
      id: `m-${Date.now()}`,
      ...payload,
      status: "queued",
      createdAt: "刚刚",
      accuracy: 0,
    };
    state.mistakes = [item, ...state.mistakes];
    const user = auth.getUser();
    if (user) auth.updateCredits(user.credits - 1);
    persist();
    window.setTimeout(() => {
      item.status = "analyzing";
      persist();
      window.dispatchEvent(new Event("mock-update"));
    }, 900);
    window.setTimeout(() => {
      item.status = "completed";
      item.accuracy = 70;
      item.analysis = {
        summary:
          "这道题的主要问题在于知识点应用不够熟练，建议结合步骤拆解思路。",
        knowledge: [payload.chapter, "基础概念应用"],
        steps: [
          "提取题目中的已知条件",
          "匹配对应知识点和公式",
          "代入计算并检查结果",
        ],
        suggestion: "把这类题加入本周复习计划，完成两道同类型练习。",
        answer: "先明确题目条件，再选择对应方法逐步推导。",
      };
      persist();
      window.dispatchEvent(new Event("mock-update"));
    }, 2600);
    return item;
  },
};
