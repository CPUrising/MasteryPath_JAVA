export type KnowledgeType = "memory" | "concept" | "procedure" | "design";
export type Mode = "outline" | "study" | "review";
export type ObjectiveStatus = "new" | "learning" | "mastered";

export interface MapEntry {
  name: string;
  module_id: string;
  module_name: string;
  type: KnowledgeType;
  status: ObjectiveStatus;
  source: "system" | "learner" | "";
  mastery: number;
  threshold: number;
  grounding: "grounded" | "ungrounded";
}

export interface NextStep {
  action: string;
  module_id: string;
  module_name: string;
  knowledge_point_id: string;
  knowledge_point_name: string;
  knowledge_point_type: KnowledgeType;
  status: string;
  mastery: number;
  threshold: number;
  reason: string;
  pending_prompt?: string;
}

export interface PendingQuestion {
  question_id: string;
  knowledge_point_id: string;
  prompt: string;
  question_type: "choice" | "short" | "open";
  options: { label: string; body: string }[];
  objective_name?: string;
  attempt?: number;
  card_kind?: "mastery";
  turn_id?: string;
  objective?: { id: string; name: string };
}

/** `POST /learning/paths/{pathId}/grade` 与 `messages.data.grade` 的判分结果。 */
export interface GradeOutcome {
  ok: boolean;
  content: string;
  status: string;
  result: string;
  question_id?: string;
  is_correct?: boolean;
  explanation?: string;
  reason?: string;
  mastery_before?: number;
  mastery_after?: number;
  gate_cleared?: boolean;
}

export interface ReviewItem {
  id: string;
  knowledge_point_id: string;
  knowledge_point_name: string;
  knowledge_type: KnowledgeType;
  due_at: number;
  due: boolean;
  forgetting_risk: number;
  reason: string;
  interval_index: number;
  scheduled_after_failure: boolean;
  desired_retention: number;
  provenance: "system" | "learner";
}

export interface Source {
  id: string;
  kind: string;
  source_id: string;
  label: string;
  excerpt: string;
  available: boolean;
}

export interface Gap {
  id: string;
  kind: string;
  ref: string;
  reason: string;
  resolution: string | null;
}

export interface PathState {
  status: string;
  path_id: string;
  name: string;
  goal: string;
  version: number;
  map: Record<string, MapEntry>;
  counts: { mastered: number; learning: number; new: number; total: number };
  next: NextStep;
  sources: Source[];
  knowledge_bases: { id: string; name: string; ready: boolean }[];
  gaps: Gap[];
  reviews: ReviewItem[];
  review_settings: { desired_retention: number; scope: string };
  session_count: number;
  learner_profile: Record<string, string> | null;
  pending_question?: PendingQuestion | null;
}

export interface TopicSession {
  session_id: string;
  path_id: string;
  title: string;
  mode: Mode;
  status: string;
  message_count: number;
  last_message: string;
  has_pending_question: boolean;
  created_at: number;
  updated_at: number;
}

export type QuizQuestionType = "choice" | "short" | "open";

/** 练习题卷的单题投影；答案字段只在提交后出现。 */
export interface QuizQuestion {
  question_id: string;
  seq: number;
  prompt: string;
  question_type: QuizQuestionType;
  options: { label: string; body: string }[];
  difficulty?: string;
  learner_answer?: string;
  is_correct?: boolean;
  result?: string;
  correct_answer?: string;
  explanation?: string;
}

/** `quiz_set` 事件与 `GET /quiz-sets/{setId}` 的题卷投影。 */
export interface QuizSet {
  set_id: string;
  path_id?: string;
  title?: string;
  topic?: string;
  status: "open" | "submitted" | string;
  question_count: number;
  score?: number | null;
  created_at?: number;
  submitted_at?: number | null;
  questions: QuizQuestion[];
}

/** `deep_question` 回合开始时的生成计划。 */
export interface QuizPlan {
  set_id: string;
  path_id?: string;
  topic: string;
  count: number;
  templates: { question_type: QuizQuestionType; difficulty?: string }[];
}

/** `POST /quiz-sets/{setId}/submit` 的结果。 */
export interface QuizSetSubmitOutcome extends QuizSet {
  /** 后端返回 `"<question_id>: <reason>"` 形式的字符串列表。 */
  ungraded_reasons?: string[];
}

export interface SessionMessage {
  id: number;
  turn_id: string;
  role: "user" | "assistant";
  content: string;
  data: { card?: PendingQuestion; grade?: GradeOutcome; quiz_set?: QuizSet };
  created_at: number;
}

export interface TurnEvent {
  type: string;
  data: Record<string, unknown>;
}

export interface LLMSelection {
  provider_id: string;
  model_id: string;
}

export interface Provider {
  id: string;
  name: string;
  base_url: string;
  api_key: string;
  protocol?: ProviderProtocol;
  detection?: ProviderDetection;
}

export type ProviderProtocol = "openai" | "anthropic" | "ollama" | "custom";

export type ModelService = "llm" | "task" | "embedding";

export interface DetectedModel {
  id: string;
  services: ModelService[];
}

export interface ProviderDetection {
  status: string;
  models: DetectedModel[];
  http_status?: number | null;
  warning?: string;
  checked_at?: string;
}

export interface CatalogProvider {
  id: string;
  name: string;
  base_url: string;
  protocol: ProviderProtocol;
  services: ModelService[];
  requires_key: boolean;
  /** Two-letter monogram shown when there is no brand icon. */
  monogram: string;
}

export interface ModelEntry {
  id: string;
  provider_id: string;
  model: string;
  name: string;
  dimension?: number;
  services?: ModelService[];
}

export interface ModelSection {
  models: ModelEntry[];
  active_id: string;
}

export interface EmbeddingModelRef {
  model_id?: string;
  provider_id?: string;
  name?: string;
  model?: string;
  dimension?: number;
}

export interface LinkedFolder {
  id: string;
  path: string;
  file_count: number;
  added_at: number;
  added_at_iso?: string;
  last_sync?: number | null;
}

export interface IndexConfig {
  chunk_size: number;
  chunk_overlap: number;
  tokenizer?: string;
}

export interface KnowledgeBase {
  id: string;
  name: string;
  description: string;
  mode: "import" | "link";
  rag_provider: string;
  search_mode: string;
  embedding_model: EmbeddingModelRef;
  status: string;
  error: string;
  created_at: number;
  updated_at: number;
  last_indexed_at: number | null;
  created_at_iso: string;
  updated_at_iso: string;
  last_indexed_at_iso: string;
  disk_path: string;
  doc_count: number;
  chunk_count: number;
  skipped_count: number;
  linked_folders: LinkedFolder[];
  index_config: Partial<IndexConfig>;
  indexed_config: Partial<IndexConfig>;
  effective_config: IndexConfig;
  index_config_source: "kb" | "global";
  needs_reindex: boolean;
}

export interface KnowledgeFile {
  path: string;
  size: number;
  modified_at: string;
  linked?: boolean;
  folder_id?: string;
}

export interface KnowledgeProgress {
  run_id: string;
  status: string;
  phase: string;
  indexed_files: number;
  total_files: number;
  chunks: number;
  error: string;
  started_at: number;
  updated_at: number;
  percent: number | null;
}

export interface ChatSessionSummary {
  session_id: string;
  title: string;
  model_id: string;
  kb_ids: string[];
  message_count: number;
  last_message: string;
  created_at: number;
  updated_at: number;
}

export interface ChatAttachment {
  name: string;
}

export interface ChatMessage {
  id: number;
  session_id: string;
  role: "user" | "assistant";
  content: string;
  attachments: ChatAttachment[];
  created_at: number;
}

export interface ChatSessionDetail {
  session: ChatSessionSummary;
  messages: ChatMessage[];
}

export interface ChatTurnEvent {
  type: string;
  data: Record<string, unknown>;
}

export type ToolActivityStatus = "running" | "ok" | "failed";

/** 一次工具调用在对话中的实时展示记录（不落库）。 */
export interface ToolActivityEntry {
  /** 同一回合内唯一（`name-index`）。 */
  id: string;
  name: string;
  arguments: Record<string, unknown>;
  status: ToolActivityStatus;
  /** `tool_result.content`；`running` 时为空。 */
  content?: string;
}

export interface QuestionEntry {
  entry_id: string;
  path_id: string;
  path_name?: string;
  knowledge_point_id?: string;
  knowledge_point_name: string;
  question: string;
  question_type: string;
  options?: { label: string; body: string }[];
  correct_answer: string;
  user_answer: string;
  explanation: string;
  difficulty?: string;
  is_correct: boolean | null;
  result: string;
  tags: string[];
  created_at: number;
  updated_at?: number;
}

/** 题库标签汇总（`GET /questions/tags`）。 */
export interface QuestionTagSummary {
  tag: string;
  count: number;
}

/** 刷题卡投影：提交前不含答案字段。 */
export interface PracticeCard {
  entry_id: string;
  path_id: string;
  path_name?: string;
  knowledge_point_id?: string;
  knowledge_point_name?: string;
  question: string;
  question_type: string;
  options: { label: string; body: string }[];
  difficulty?: string;
  tags: string[];
  result: string;
}

/** `POST /questions/{entryId}/attempt` 的结果。 */
export interface QuestionAttemptOutcome {
  status: "graded" | "ungraded";
  entry_id: string;
  result?: string;
  is_correct?: boolean;
  correct_answer?: string;
  explanation?: string;
  reason?: string;
}

/** 标签重命名 / 合并 / 删除的变更条数。 */
export interface TagMutationResult {
  updated: number;
}
