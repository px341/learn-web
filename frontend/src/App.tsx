import { useEffect, useState } from "react";
import {
  Link,
  Navigate,
  NavLink,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import {
  BookOpen,
  BrainCircuit,
  Camera,
  CheckCircle2,
  ChevronRight,
  CircleHelp,
  FileImage,
  Home,
  LogOut,
  Menu,
  Plus,
  Search,
  Sparkles,
  Upload,
  UserRound,
  WalletCards,
  X,
} from "lucide-react";
import { auth } from "./auth";
import { api, ApiRequestError } from "./api";
import type {
  DashboardStats,
  MistakeDetail as MistakeDetailData,
  MistakeSummary,
  Page,
  PaymentPlan,
  User,
} from "./types";

function errorMessage(cause: unknown) {
  return cause instanceof Error ? cause.message : "请求失败，请稍后重试";
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function greeting() {
  const hour = new Date().getHours();
  if (hour < 6) return "夜深了";
  if (hour < 12) return "早上好";
  if (hour < 18) return "下午好";
  return "晚上好";
}

function Avatar({ user, className }: { user: User | null; className: string }) {
  return (
    <span className={className}>
      {user?.avatarUrl ? (
        <img src={user.avatarUrl} alt={`${user.name}的头像`} />
      ) : (
        user?.name?.[0]?.toUpperCase() || "D"
      )}
    </span>
  );
}

function Logo() {
  return (
    <Link to="/dashboard" className="logo">
      <span className="logo-mark">
        <BrainCircuit size={20} />
      </span>
      <span>
        错题<span className="accent">实验室</span>
      </span>
    </Link>
  );
}
function Auth({ register = false }: { register?: boolean }) {
  const nav = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  return (
    <main className="auth-shell">
      <div className="auth-art">
        <div className="art-orb orb-one" />
        <div className="art-orb orb-two" />
        <Logo />
        <div className="art-copy">
          <p className="eyebrow">AI-powered learning workspace</p>
          <h1>
            让每一道错题，
            <br />
            <em>都变成进步。</em>
          </h1>
          <p>
            拍下错题，得到清晰的思路拆解，
            <br />
            把“会做”变成真正的掌握。
          </p>
        </div>
        <div className="art-bottom">
          <span>✦ 你的智能错题本</span>
          <span>持续记录每一次进步</span>
        </div>
      </div>
      <div className="auth-card">
        <div className="auth-mobile-logo">
          <Logo />
        </div>
        <div className="auth-heading">
          <span className="icon-chip">
            <Sparkles size={17} />
          </span>
          <p className="eyebrow">{register ? "JOIN MISTAKE LAB" : "WELCOME BACK"}</p>
          <h2>{register ? "创建你的学习空间" : "欢迎回来，同学"}</h2>
          <p>
            {register
              ? "从今天开始，让错题不再重来。"
              : "继续记录今天的学习进步。"}
          </p>
        </div>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setError("");
            setSubmitting(true);
            try {
              if (register) {
                await auth.register(name, email, password, passwordConfirmation);
              } else {
                await auth.login(email, password);
              }
              nav("/dashboard");
            } catch (cause) {
              setError(cause instanceof Error ? cause.message : "请求失败，请稍后重试");
            } finally {
              setSubmitting(false);
            }
          }}
        >
          {register && (
            <label>
              你的称呼
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="例如：小林"
                autoComplete="name"
                required
              />
            </label>
          )}
          <label>
            邮箱
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@example.com"
              autoComplete="email"
              required
            />
          </label>
          <label>
            密码
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="请输入密码"
              autoComplete={register ? "new-password" : "current-password"}
              minLength={6}
              maxLength={64}
              required
            />
          </label>
          {register && (
            <label>
              确认密码
              <input
                type="password"
                value={passwordConfirmation}
                onChange={(e) => setPasswordConfirmation(e.target.value)}
                autoComplete="new-password"
                minLength={6}
                maxLength={64}
                required
              />
            </label>
          )}
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary wide" type="submit" disabled={submitting}>
            {submitting ? "提交中…" : register ? "创建账户" : "登录"}{" "}
            {!submitting && <ChevronRight size={17} />}
          </button>
        </form>
        {register && <p className="terms-note">注册即表示你同意错题实验室的服务条款与隐私政策。</p>}
        <p className="auth-switch">
          {register ? "已有账户？" : "还没有账户？"}{" "}
          <Link to={register ? "/login" : "/register"}>
            {register ? "立即登录" : "免费注册"}
          </Link>
        </p>
      </div>
    </main>
  );
}

function Layout({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const [user, setUser] = useState(auth.getUser());
  const loc = useLocation();
  useEffect(() => {
    auth.refreshUser()
      .then(setUser)
      .catch(() => {
        if (!auth.getUser()) {
          window.location.href = "/login";
        }
      });

    const syncUser = () => {
      const currentUser = auth.getUser();
      if (!currentUser) {
        window.location.href = "/login";
        return;
      }
      setUser(currentUser);
    };
    window.addEventListener("auth-session-change", syncUser);
    return () => window.removeEventListener("auth-session-change", syncUser);
  }, []);
  const items = [
    ["/dashboard", "总览", Home],
    ["/mistakes", "我的错题", BookOpen],
    ["/mistakes/upload", "上传错题", Upload],
  ] as const;
  return (
    <div className="app-shell">
      <aside className={open ? "sidebar open" : "sidebar"}>
        <div className="side-top">
          <Logo />
          <button
            className="icon-button mobile-only"
            onClick={() => setOpen(false)}
          >
            <X size={20} />
          </button>
        </div>
        <nav>
          {items.map(([to, label, Icon]) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                `nav-item ${isActive || (to === "/mistakes" && loc.pathname.startsWith("/mistakes/") && loc.pathname !== "/mistakes/upload") ? "active" : ""}`
              }
            >
              <Icon size={18} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="side-help">
          <CircleHelp size={18} />
          <div>
            <strong>需要帮助？</strong>
            <small>查看使用指南</small>
          </div>
          <ChevronRight size={15} />
        </div>
        <div className="side-user">
          <Avatar className="avatar" user={user} />
          <div className="user-copy">
            <strong>{user?.name || "同学"}</strong>
            <small>{user?.email || ""}</small>
          </div>
          <button
            className="ghost icon-button"
            onClick={() => {
              auth.logout();
              window.location.href = "/login";
            }}
          >
            <LogOut size={16} />
          </button>
        </div>
      </aside>
      {open && <div className="backdrop" onClick={() => setOpen(false)} />}
      <section className="main-area">
        <header className="topbar">
          <button
            className="icon-button mobile-only"
            onClick={() => setOpen(true)}
          >
            <Menu size={21} />
          </button>
          <div className="breadcrumb">
            学习空间 <ChevronRight size={14} />{" "}
            <span>
              {loc.pathname.includes("upload")
                ? "上传错题"
                : loc.pathname.includes("mistakes")
                  ? "我的错题"
                  : "总览"}
            </span>
          </div>
          <div className="top-actions">
            <div className="credit-pill">
              <WalletCards size={16} />
              <span>剩余分析次数</span>
              <b>{user?.credits ?? 0}</b>
            </div>
            <Link to="/profile" aria-label="打开个人设置">
              <Avatar className="avatar" user={user} />
            </Link>
          </div>
        </header>
        <div className="content">{children}</div>
      </section>
    </div>
  );
}
function Guard({ children }: { children: React.ReactNode }) {
  return auth.getUser() ? (
    <Layout>{children}</Layout>
  ) : (
    <Navigate to="/login" replace />
  );
}
function PageTitle({
  eyebrow,
  title,
  action,
}: {
  eyebrow?: string;
  title: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="page-title">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
      </div>
      {action}
    </div>
  );
}
function Dashboard() {
  const [stats, setStats] = useState<DashboardStats>();
  const [recent, setRecent] = useState<MistakeSummary[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([api.dashboard(), api.listMistakes({ page: 0, size: 4 })])
      .then(([dashboard, page]) => {
        if (!active) return;
        setStats(dashboard);
        setRecent(page.items);
      })
      .catch((cause) => {
        if (active) setError(errorMessage(cause));
      });
    return () => {
      active = false;
    };
  }, []);
  const types = stats?.questionTypeCounts ?? [];
  const chartColors = ["#7658e8", "#f19b78", "#83c5ad", "#f4cd68", "#6fa8dc"];
  const typeTotal = types.reduce((sum, item) => sum + item.count, 0);
  let accumulated = 0;
  const donutGradient = typeTotal
    ? `conic-gradient(${types.map((item, index) => {
        const start = (accumulated / typeTotal) * 100;
        accumulated += item.count;
        const end = (accumulated / typeTotal) * 100;
        return `${chartColors[index % chartColors.length]} ${start}% ${end}%`;
      }).join(", ")})`
    : "#eeedf3";
  const today = new Intl.DateTimeFormat("zh-CN", { dateStyle: "full" })
    .format(new Date());
  return (
    <>
      <PageTitle
        eyebrow={today}
        title={`${greeting()}，${auth.getUser()?.name || "同学"} 👋`}
        action={
          <Link to="/mistakes/upload" className="primary">
            <Plus size={17} /> 上传新错题
          </Link>
        }
      />
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="welcome-card">
        <div>
          <span className="tag tag-purple">
            <Sparkles size={13} /> 学习小贴士
          </span>
          <h2>今天也要保持好奇心。</h2>
          <p>及时回顾最近的错题，把分析建议变成真正掌握。</p>
          <Link to="/mistakes" className="text-link">
            开始复习 <ChevronRight size={15} />
          </Link>
        </div>
        <div className="welcome-illustration">
          <div className="float-card card-a">x² + 2x + 1</div>
          <div className="float-card card-b">✓ 已掌握</div>
          <div className="book-shape">
            <BookOpen size={47} />
          </div>
        </div>
      </div>
      <div className="stats-grid">
        <Stat
          label="累计错题"
          value={stats?.total ?? "—"}
          unit="道"
          change={`近 7 天变化 ${stats?.totalChangePercent ?? 0}%`}
          positive={(stats?.totalChangePercent ?? 0) >= 0}
        />
        <Stat
          label="本周新增"
          value={stats?.weeklyNew ?? "—"}
          unit="道"
          change="保持得很好"
          positive
        />
        <Stat
          label="错题类型"
          value={types.length}
          unit="类"
          change="按题型自动归类"
        />
        <Stat
          label="近 7 天占比"
          value={stats?.total ? Math.round((stats.weeklyNew / stats.total) * 100) : 0}
          unit="%"
          change="新增错题占累计比例"
        />{" "}
      </div>
      <div className="dashboard-grid">
        <section className="panel chart-panel">
          <div className="panel-head">
            <div>
              <h3>本周记录</h3>
              <p>真实错题数据概览</p>
            </div>
            <span className="tag tag-purple">最近 7 天</span>
          </div>
          <div className="week-summary">
            <strong>{stats?.weeklyNew ?? "—"}</strong>
            <span>道新错题</span>
            <p>累计 {stats?.total ?? 0} 道，较此前累计变化 {stats?.totalChangePercent ?? 0}%</p>
          </div>
        </section>
        <section className="panel">
          <div className="panel-head">
            <div>
              <h3>错误类型分布</h3>
              <p>找到问题，才能解决问题</p>
            </div>
            <Link to="/mistakes" className="more-link">
              详情 <ChevronRight size={14} />
            </Link>
          </div>
          <div className="donut-wrap">
            <div className="donut" style={{ background: donutGradient }}>
              <div>
                <strong>{types.length}</strong>
                <small>类型</small>
              </div>
            </div>
            <div className="legend">
              {types.map(({ questionType, count }, i) => (
                <div key={questionType}>
                  <i
                    className="dot"
                    style={{ background: chartColors[i % chartColors.length] }}
                  />
                  <span>{questionType}</span>
                  <b>{count}</b>
                </div>
              ))}
            </div>
          </div>
        </section>
      </div>
      <section className="panel recent">
        <div className="panel-head">
          <div>
            <h3>最近错题</h3>
            <p>你的学习记录</p>
          </div>
          <Link to="/mistakes" className="more-link">
            查看全部 <ChevronRight size={14} />
          </Link>
        </div>
        <div className="mistake-table">
          {recent.map((m) => (
            <MistakeRow key={m.id} m={m} />
          ))}
          {!error && stats && recent.length === 0 && (
            <p className="empty-inline">还没有错题，上传第一道开始分析吧。</p>
          )}
        </div>
      </section>
    </>
  );
}
function Stat({
  label,
  value,
  unit,
  change,
  positive,
}: {
  label: string;
  value: string | number;
  unit: string;
  change: string;
  positive?: boolean;
}) {
  return (
    <div className="stat-card">
      <span>{label}</span>
      <div>
        <strong>{value}</strong>
        <small>{unit}</small>
      </div>
      <p className={positive ? "positive" : ""}>
        {positive ? "↑" : "•"} {change}
      </p>
    </div>
  );
}
function MistakeRow({ m }: { m: MistakeSummary }) {
  return (
    <Link to={`/mistakes/${m.id}`} className="mistake-row">
      <div className="subject-icon">
        <FileImage size={17} />
      </div>
      <div className="row-title">
        <strong>{m.title}</strong>
        <span>
          {m.subject} · {m.chapter}
        </span>
      </div>
      <span className="type-pill">{m.type}</span>
      <Status status={m.status} />
      <span className="row-date">{formatDate(m.createdAt)}</span>
      <ChevronRight size={16} className="row-arrow" />
    </Link>
  );
}
function Status({ status }: { status: MistakeSummary["status"] }) {
  return (
    <span className={`status ${status}`}>
      {status === "completed"
        ? "已完成"
        : status === "analyzing"
          ? "分析中"
          : status === "failed"
            ? "分析失败"
            : "排队中"}
    </span>
  );
}
function Mistakes() {
  const [search, setSearch] = useState("");
  const [subject, setSubject] = useState("");
  const [status, setStatus] = useState<"" | MistakeSummary["status"]>("");
  const [mastered, setMastered] = useState("");
  const [sort, setSort] = useState<"createdAt,asc" | "createdAt,desc">(
    "createdAt,desc",
  );
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<MistakeSummary>>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    const timer = window.setTimeout(() => {
      api.listMistakes({
        keyword: search.trim() || undefined,
        subject: subject || undefined,
        status: status || undefined,
        mastered: mastered === "" ? undefined : mastered === "true",
        page,
        size: 20,
        sort,
      })
        .then((data) => {
          if (active) setResult(data);
        })
        .catch((cause) => {
          if (active) setError(errorMessage(cause));
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [search, subject, status, mastered, page, sort]);

  const updateFilter = (setter: (value: string) => void, value: string) => {
    setter(value);
    setPage(0);
  };
  return (
    <>
      <PageTitle
        eyebrow="MISTAKE LIBRARY"
        title="我的错题"
        action={
          <Link to="/mistakes/upload" className="primary">
            <Plus size={17} /> 上传错题
          </Link>
        }
      />
      <div className="toolbar">
        <div className="search">
          <Search size={17} />
          <input
            placeholder="搜索题目、学科或知识点"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <select
          className="filter-button"
          value={subject}
          onChange={(event) => updateFilter(setSubject, event.target.value)}
          aria-label="按学科筛选"
        >
          <option value="">全部学科</option>
          <option value="数学">数学</option>
          <option value="物理">物理</option>
          <option value="英语">英语</option>
          <option value="化学">化学</option>
        </select>
        <select
          className="filter-button"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as "" | MistakeSummary["status"]);
            setPage(0);
          }}
          aria-label="按分析状态筛选"
        >
          <option value="">全部状态</option>
          <option value="queued">排队中</option>
          <option value="analyzing">分析中</option>
          <option value="completed">已完成</option>
          <option value="failed">分析失败</option>
        </select>
        <select
          className="filter-button"
          value={mastered}
          onChange={(event) => updateFilter(setMastered, event.target.value)}
          aria-label="按掌握状态筛选"
        >
          <option value="">全部掌握状态</option>
          <option value="true">已掌握</option>
          <option value="false">未掌握</option>
        </select>
      </div>
      <section className="panel list-panel">
        <div className="list-summary">
          <span>共 {result?.totalElements ?? 0} 道错题</span>
          <select
            className="sort-select"
            value={sort}
            onChange={(event) => {
              setSort(event.target.value as typeof sort);
              setPage(0);
            }}
          >
            <option value="createdAt,desc">按最近上传排序</option>
            <option value="createdAt,asc">按最早上传排序</option>
          </select>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        {loading && <p className="empty-inline">正在加载错题…</p>}
        {!loading && !error && result?.items.length === 0 && (
          <p className="empty-inline">没有符合条件的错题。</p>
        )}
        {!error && result?.items.map((m) => (
          <MistakeRow key={m.id} m={m} />
        ))}
        {(result?.totalPages ?? 0) > 1 && (
          <div className="pagination">
            <button
              className="secondary"
              disabled={page === 0}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              上一页
            </button>
            <span>第 {page + 1} / {result?.totalPages} 页</span>
            <button
              className="secondary"
              disabled={page + 1 >= (result?.totalPages ?? 0)}
              onClick={() => setPage((value) => value + 1)}
            >
              下一页
            </button>
          </div>
        )}
      </section>
    </>
  );
}
function UploadPage() {
  const nav = useNavigate();
  const [title, setTitle] = useState("");
  const [subject, setSubject] = useState("数学");
  const [chapter, setChapter] = useState("");
  const [type, setType] = useState("概念不清");
  const [text, setText] = useState("");
  const [userAnswer, setUserAnswer] = useState("");
  const [image, setImage] = useState<File>();
  const [imagePreview, setImagePreview] = useState<string>();
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const user = auth.getUser();
  useEffect(() => {
    return () => {
      if (imagePreview) URL.revokeObjectURL(imagePreview);
    };
  }, [imagePreview]);
  const onFile = (file?: File) => {
    if (!file) return;
    setError("");
    if (file.size > 10 * 1024 * 1024) {
      setError("图片不能超过 10MB");
      return;
    }
    if (!file.type.match(/^image\/(png|jpeg|webp)$/)) {
      setError("仅支持 PNG、JPG、WEBP 图片");
      return;
    }
    setImage(file);
    setImagePreview(URL.createObjectURL(file));
  };
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!text.trim() && !image) {
      setError("请至少上传图片或填写题目文字");
      return;
    }
    if (!user || user.credits < 1) {
      nav("/payment");
      return;
    }
    setSubmitting(true);
    try {
      const result = await api.submitMistake({
        title: title.trim() || undefined,
        subject,
        chapter: chapter.trim() || undefined,
        type,
        text: text.trim() || undefined,
        userAnswer: userAnswer.trim() || undefined,
        image,
      });
      auth.updateCredits(result.creditsRemaining);
      nav(`/mistakes/${result.mistake.id}`);
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 402) {
        nav("/payment");
        return;
      }
      setError(errorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <>
      <PageTitle
        eyebrow="NEW SUBMISSION"
        title="上传一道错题"
        action={
          <div className="credit-pill large">
            <WalletCards size={16} /> 剩余 <b>{user?.credits ?? 0}</b> 次分析
          </div>
        }
      />
      <form className="upload-layout" onSubmit={submit}>
        <div className="upload-main">
          <section className="panel">
            <div className="panel-heading">
              <span className="number">01</span>
              <div>
                <h3>上传题目图片</h3>
                <p>拍照或选择清晰的题目截图，AI 会自动识别内容</p>
              </div>
            </div>
            <label className={imagePreview ? "dropzone has-image" : "dropzone"}>
              {imagePreview ? (
                <>
                  <img src={imagePreview} alt="待上传的错题预览" />
                  <button
                    type="button"
                    className="remove-image"
                    onClick={(e) => {
                      e.preventDefault();
                      setImage(undefined);
                      setImagePreview(undefined);
                    }}
                  >
                    <X size={16} />
                  </button>
                </>
              ) : (
                <>
                  <span className="upload-icon">
                    <Upload size={22} />
                  </span>
                  <strong>
                    拖拽图片到这里，或 <u>点击上传</u>
                  </strong>
                  <small>支持 PNG、JPG、WEBP，最大 10MB</small>
                </>
              )}
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(e) => onFile(e.target.files?.[0])}
              />
            </label>
          </section>
          <section className="panel">
            <div className="panel-heading">
              <span className="number">02</span>
              <div>
                <h3>
                  补充题目文字 <span className="optional">选填</span>
                </h3>
                <p>如果图片不清晰，可以手动补充题目和你的答案</p>
              </div>
            </div>
            <textarea
              className="question-input"
              placeholder="粘贴或输入题目内容……"
              value={text}
              onChange={(e) => setText(e.target.value)}
              maxLength={10000}
            />
            <div className="field-grid">
              <label>
                题目名称
                <input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="例如：二次函数图像与最值"
                  maxLength={100}
                />
              </label>
              <label>
                你的答案
                <input
                  value={userAnswer}
                  onChange={(event) => setUserAnswer(event.target.value)}
                  placeholder="选填"
                  maxLength={10000}
                />
              </label>
            </div>
          </section>
        </div>
        <aside className="upload-side panel">
          <h3>题目分类</h3>
          <p>帮助我们给你更精准的分析</p>
          <label>
            学科
            <select
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
            >
              <option>数学</option>
              <option>物理</option>
              <option>英语</option>
              <option>化学</option>
            </select>
          </label>
          <label>
            章节 / 知识点
            <input
              value={chapter}
              onChange={(e) => setChapter(e.target.value)}
              placeholder="例如：函数"
              maxLength={100}
            />
          </label>
          <label>
            你觉得错在哪里？
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option>概念不清</option>
              <option>计算错误</option>
              <option>审题错误</option>
              <option>方法不熟</option>
              <option>粗心失误</option>
            </select>
          </label>
          <div className="analysis-note">
            <Sparkles size={17} />
            <span>AI 将从解题步骤、知识点和错误原因三个维度分析。</span>
          </div>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary wide" type="submit" disabled={submitting}>
            {submitting ? "提交中…" : "开始智能分析"} {!submitting && <ChevronRight size={17} />}
          </button>
          <small className="cost-note">
            <WalletCards size={13} /> 本次分析消耗 1 次额度
          </small>
        </aside>
      </form>
    </>
  );
}
function MistakeDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const [m, setMistake] = useState<MistakeDetailData>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [masterySaving, setMasterySaving] = useState(false);
  useEffect(() => {
    if (!id) return;
    let active = true;
    let timer: number | undefined;
    const poll = async () => {
      try {
        const data = await api.getMistake(id);
        if (!active) return;
        setMistake(data);
        setError("");
        setLoading(false);
        if (data.status === "queued" || data.status === "analyzing") {
          timer = window.setTimeout(poll, 2000);
        }
      } catch (cause) {
        if (!active) return;
        setError(errorMessage(cause));
        setLoading(false);
      }
    };
    void poll();
    return () => {
      active = false;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [id]);
  if (loading)
    return <p className="empty-inline">正在加载错题详情…</p>;
  if (!m)
    return (
      <div className="empty">
        <h2>没有找到这道错题</h2>
        {error && <p className="form-error" role="alert">{error}</p>}
        <Link to="/mistakes" className="primary">
          返回错题库
        </Link>
      </div>
    );
  return (
    <>
      <button className="back-link" onClick={() => nav("/mistakes")}>
        ← 返回错题库
      </button>
      <PageTitle
        eyebrow={m.subject + (m.chapter ? " · " + m.chapter : "")}
        title={m.title}
        action={<Status status={m.status} />}
      />
      <div className="detail-grid">
        <section className="panel original">
          <div className="panel-head">
            <div>
              <h3>原题记录</h3>
              <p>{formatDate(m.createdAt)} 上传</p>
            </div>
            {m.image && (
              <a
                className="icon-button"
                href={m.image.url}
                target="_blank"
                rel="noreferrer"
                aria-label="在新窗口查看原图"
              >
                <Search size={17} />
              </a>
            )}
          </div>
          {m.image ? (
            <img src={m.image.url} className="original-image" alt={m.title} />
          ) : (
            <div className="text-question">
              <p>{m.questionText || "未提供题目文字"}</p>
              <span>你的答案：{m.userAnswer || "未填写"}</span>
            </div>
          )}
          <div className="answer-box">
            <span>错误类型</span>
            <strong>{m.type}</strong>
          </div>
        </section>
        <section className="panel analysis-panel">
          {m.status === "failed" ? (
            <div className="analysis-loading failed-analysis">
              <div className="pulse"><X size={25} /></div>
              <h3>这道题分析失败</h3>
              <p>{m.failureMessage || "分析服务暂时无法处理这道题。"}</p>
              <Link to="/mistakes/upload" className="secondary">重新上传</Link>
            </div>
          ) : m.status !== "completed" ? (
            <div className="analysis-loading">
              <div className="pulse">
                <Sparkles size={25} />
              </div>
              <h3>正在分析这道题…</h3>
              <p>
                AI 正在识别题目并拆解你的解题思路
                <br />
                通常需要几秒钟，请稍候。
              </p>
              <div className="progress">
                <i />
              </div>
              <small>
                分析任务 {m.status === "queued" ? "排队中" : "进行中"}
              </small>
            </div>
          ) : (
            <>
              <div className="analysis-title">
                <span className="tag tag-purple">
                  <Sparkles size={13} /> AI 分析完成
                </span>
                <span className="confidence">
                  置信度 {m.analysis?.confidence ?? 0}%
                </span>
              </div>
              <h2>这道题，你卡在了哪里？</h2>
              <p className="analysis-summary">{m.analysis?.summary}</p>
              <div className="analysis-section">
                <h4>
                  <span>01</span>涉及知识点
                </h4>
                <div className="knowledge-list">
                  {m.analysis?.knowledge.map((k) => (
                    <span key={k}>{k}</span>
                  ))}
                </div>
              </div>
              <div className="analysis-section">
                <h4>
                  <span>02</span>正确解题思路
                </h4>
                <ol>
                  {m.analysis?.steps.map((s) => (
                    <li key={s}>{s}</li>
                  ))}
                </ol>
              </div>
              <div className="tip-box">
                <Sparkles size={18} />
                <div>
                  <strong>给你的建议</strong>
                  <p>{m.analysis?.suggestion}</p>
                </div>
              </div>
              <div className="answer-box correct">
                <span>参考答案</span>
                <strong>{m.analysis?.answer}</strong>
              </div>
              <button
                className="secondary wide"
                disabled={masterySaving}
                onClick={async () => {
                  setMasterySaving(true);
                  setError("");
                  try {
                    const updated = await api.updateMastery(m.id, !m.mastered);
                    setMistake({ ...m, mastered: updated.mastered });
                  } catch (cause) {
                    setError(errorMessage(cause));
                  } finally {
                    setMasterySaving(false);
                  }
                }}
              >
                <CheckCircle2 size={17} /> {m.mastered ? "取消已掌握" : "标记为已掌握"}
              </button>
              {error && <p className="form-error" role="alert">{error}</p>}
            </>
          )}
        </section>
      </div>
    </>
  );
}
function Payment() {
  const nav = useNavigate();
  const [plans, setPlans] = useState<PaymentPlan[]>([]);
  const [selected, setSelected] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  useEffect(() => {
    let active = true;
    api.paymentPlans()
      .then((items) => {
        if (!active) return;
        setPlans(items);
        setSelected(items.find((item) => item.recommended)?.id || items[0]?.id || "");
      })
      .catch((cause) => {
        if (active) setError(errorMessage(cause));
      });
    return () => {
      active = false;
    };
  }, []);
  const selectedPlan = plans.find((plan) => plan.id === selected);
  return (
    <div className="payment-page">
      <button className="back-link" onClick={() => nav(-1)}>
        ← 返回
      </button>
      <div className="payment-head">
        <span className="icon-chip">
          <WalletCards size={19} />
        </span>
        <p className="eyebrow">LEARNING CREDITS</p>
        <h1>解锁你的分析次数</h1>
        <p>每一次分析，都是一次把问题变清楚的机会。</p>
      </div>
      <div className="plans">
        {plans.map((p) => (
          <button
            key={p.id}
            className={`plan ${selected === p.id ? "selected" : ""}`}
            onClick={() => {
              setSelected(p.id);
              setIdempotencyKey(crypto.randomUUID());
            }}
          >
            {p.recommended && <span className="hot">最受欢迎</span>}
            <span className="radio" />
            <h3>{p.name}</h3>
            <strong>
              <small>¥</small>
              {(p.priceFen / 100).toFixed(2)}
            </strong>
            <span>{p.credits} 次 AI 错题分析</span>
            <small>{p.description}</small>
          </button>
        ))}
        {!error && plans.length === 0 && <p>正在加载套餐…</p>}
      </div>
      <div className="payment-summary">
        <span>模拟支付 · 演示环境</span>
        <strong>¥{selectedPlan ? (selectedPlan.priceFen / 100).toFixed(2) : "—"}</strong>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <button
        className="primary pay-button"
        disabled={!selectedPlan || submitting}
        onClick={async () => {
          if (!selectedPlan) return;
          setSubmitting(true);
          setError("");
          try {
            const baseline = auth.getUser()?.credits ?? 0;
            const order = await api.createMockPayment(selectedPlan.id, idempotencyKey);
            nav(
              `/payment/result?baseline=${baseline}&credits=${order.credits}`,
              { replace: true },
            );
          } catch (cause) {
            setError(errorMessage(cause));
          } finally {
            setSubmitting(false);
          }
        }}
      >
        {submitting ? "正在创建订单…" : "发起模拟支付"} {!submitting && <ChevronRight size={17} />}
      </button>
      <p className="secure-note">🔒 演示模式不会产生真实扣款</p>
    </div>
  );
}
function PaymentResult() {
  const nav = useNavigate();
  const [params] = useSearchParams();
  const baselineParam = params.get("baseline");
  const creditsParam = params.get("credits");
  const baseline = Number(baselineParam);
  const expected = Number(creditsParam);
  const validResult = baselineParam !== null
    && creditsParam !== null
    && Number.isFinite(baseline)
    && Number.isFinite(expected)
    && baseline >= 0
    && expected > 0;
  const target = validResult ? baseline + expected : Number.NaN;
  const [paid, setPaid] = useState(
    Number.isFinite(target) && (auth.getUser()?.credits ?? 0) >= target,
  );
  const [error, setError] = useState(
    validResult ? "" : "缺少支付结果参数，请返回套餐页重新发起。",
  );
  useEffect(() => {
    if (paid || !Number.isFinite(target)) return;
    let active = true;
    let timer: number | undefined;
    let attempts = 0;
    const refresh = async () => {
      try {
        const user = await auth.refreshUser();
        if (!active) return;
        if (user.credits >= target) {
          setPaid(true);
          return;
        }
        attempts += 1;
        if (attempts >= 30) {
          setError("订单仍在处理中，请稍后刷新页面查看最新额度。");
          return;
        }
        timer = window.setTimeout(refresh, 1000);
      } catch (cause) {
        if (active) setError(errorMessage(cause));
      }
    };
    void refresh();
    return () => {
      active = false;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [paid, target]);
  return (
    <div className="result-page">
      <div className="success-icon">
        <CheckCircle2 size={42} />
      </div>
      <p className="eyebrow">{paid ? "PAYMENT SUCCESSFUL" : "PAYMENT PROCESSING"}</p>
      <h1>{paid ? "额度已到账 🎉" : "订单处理中…"}</h1>
      <p>{paid ? "你的分析次数已经更新，现在就去上传一道错题吧。" : "模拟订单已受理，正在等待异步入账。"}</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      {paid && (
        <Link to="/mistakes/upload" className="primary">
          去上传错题 <ChevronRight size={17} />
        </Link>
      )}
      <button className="text-button" onClick={() => nav("/dashboard")}>
        返回学习总览
      </button>
    </div>
  );
}
function Profile() {
  const [user, setUser] = useState(auth.getUser()!);
  const [name, setName] = useState(user.name);
  const [email, setEmail] = useState(user.email);
  const [profileError, setProfileError] = useState("");
  const [profileSaving, setProfileSaving] = useState(false);
  const [avatarFile, setAvatarFile] = useState<File>();
  const [avatarPreview, setAvatarPreview] = useState<string>();
  const [avatarError, setAvatarError] = useState("");
  const [avatarUploading, setAvatarUploading] = useState(false);

  useEffect(() => {
    return () => {
      if (avatarPreview) URL.revokeObjectURL(avatarPreview);
    };
  }, [avatarPreview]);

  const selectAvatar = (file?: File) => {
    setAvatarError("");
    if (!file) return;
    if (!file.type.match(/^image\/(png|jpeg|webp)$/)) {
      setAvatarFile(undefined);
      setAvatarPreview(undefined);
      setAvatarError("仅支持 PNG、JPG、WEBP 图片");
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setAvatarFile(undefined);
      setAvatarPreview(undefined);
      setAvatarError("头像不能超过 5MB");
      return;
    }
    setAvatarFile(file);
    setAvatarPreview(URL.createObjectURL(file));
  };

  const previewUser = avatarPreview ? { ...user, avatarUrl: avatarPreview } : user;
  return (
    <>
      <PageTitle eyebrow="ACCOUNT" title="个人设置" />
      <section className="panel profile-card">
        <Avatar className="profile-avatar" user={user} />
        <div>
          <h2>{user.name}</h2>
          <p>{user.email}</p>
        </div>
        <button
          className="secondary"
          onClick={() => {
            auth.logout();
            window.location.href = "/login";
          }}
        >
          <LogOut size={16} /> 退出登录
        </button>
      </section>
      <section className="panel profile-form avatar-settings">
        <h3>头像</h3>
        <div className="avatar-editor">
          <Avatar className="profile-avatar avatar-preview" user={previewUser} />
          <div className="avatar-editor-actions">
            <label className="secondary avatar-file-button">
              <Camera size={16} /> 选择图片
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(event) => selectAvatar(event.target.files?.[0])}
              />
            </label>
            <small>支持 PNG、JPG、WEBP，最大 5MB</small>
          </div>
          <button
            className="primary"
            type="button"
            disabled={!avatarFile || avatarUploading}
            onClick={async () => {
              if (!avatarFile) return;
              setAvatarUploading(true);
              setAvatarError("");
              try {
                const updatedUser = await auth.updateAvatar(avatarFile);
                setUser(updatedUser);
                setAvatarFile(undefined);
                setAvatarPreview(undefined);
              } catch (cause) {
                setAvatarError(cause instanceof Error ? cause.message : "头像上传失败");
              } finally {
                setAvatarUploading(false);
              }
            }}
          >
            {avatarUploading ? "上传中…" : "保存头像"}
          </button>
        </div>
        {avatarError && <p className="form-error" role="alert">{avatarError}</p>}
      </section>
      <section className="panel profile-form">
        <h3>账户信息</h3>
        <label>
          显示名称
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={30}
            required
          />
        </label>
        <label>
          邮箱地址
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            maxLength={254}
            required
          />
        </label>
        {profileError && <p className="form-error" role="alert">{profileError}</p>}
        <button
          className="primary"
          disabled={profileSaving}
          onClick={async () => {
            setProfileSaving(true);
            setProfileError("");
            try {
              const updated = await auth.updateProfile(name, email);
              setUser(updated);
              setName(updated.name);
              setEmail(updated.email);
            } catch (cause) {
              setProfileError(errorMessage(cause));
            } finally {
              setProfileSaving(false);
            }
          }}
        >
          {profileSaving ? "保存中…" : "保存修改"}
        </button>
      </section>
    </>
  );
}
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Auth />} />
      <Route path="/register" element={<Auth register />} />
      <Route
        path="/payment"
        element={
          <Guard>
            <Payment />
          </Guard>
        }
      />
      <Route
        path="/payment/result"
        element={
          <Guard>
            <PaymentResult />
          </Guard>
        }
      />
      <Route
        path="*"
        element={
          <Guard>
            <Routes>
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/mistakes" element={<Mistakes />} />
              <Route path="/mistakes/upload" element={<UploadPage />} />
              <Route path="/mistakes/:id" element={<MistakeDetail />} />
              <Route path="/profile" element={<Profile />} />
              <Route path="*" element={<Navigate to="/dashboard" />} />
            </Routes>
          </Guard>
        }
      />
    </Routes>
  );
}
