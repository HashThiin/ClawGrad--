import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
})

export interface AIGradingRequest {
  question: string
  answer: string
  attachments?: string[]
  maxScore?: number
  modelId?: string
}

export interface AIGradingResult {
  totalScore: number
  maxScore: number
  dimensionScores: Record<string, number>
  feedback: string
  errors: ErrorPoint[]
  suggestions: string[]
  knowledgePoints: KnowledgePoint[]
  reasoningSteps: string[]
  items?: ItemGradingResult[]
  overallSummary?: string
}

export interface ItemGradingResult {
  index: number
  question?: string
  answer?: string
  score: number
  maxScore: number
  feedback?: string
  errors?: ErrorPoint[]
  correctness?: 'correct' | 'partial' | 'wrong' | string
}

export interface HomeworkItem {
  index: number
  question?: string
  answer?: string
  maxScore?: number
}

export interface OrganizedHomework {
  subject?: string
  question?: string
  answer?: string
  maxScore?: number
  fromImage: boolean
  remark?: string
  items?: HomeworkItem[]
  totalMaxScore?: number
}

export interface StageProgress {
  name: string
  /** pending / running / completed / failed */
  status: 'pending' | 'running' | 'completed' | 'failed' | string
  duration?: number
}

export interface TaskResultResponse {
  taskId: string
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'TIMEOUT' | 'NOT_FOUND'
  result?: AIGradingResult
  organizedHomework?: OrganizedHomework
  stages?: StageProgress[]
  currentStage?: string
  error?: string
  message?: string
  /** 超时重试用 */
  question?: string
  answer?: string
  suggestFastModel?: boolean
}

export interface ErrorPoint {
  location: string
  errorType: string
  description: string
  correction: string
}

export interface KnowledgePoint {
  name: string
  masteryLevel: string
  description: string
}

export interface ModelInfo {
  id: string
  name: string
  provider: string
  supportsVision: boolean
  description: string
  default: boolean
  recommended: boolean
  contextWindow?: number
  maxOutput?: number
  reasoning?: boolean
}

// 获取可用模型列表
export const fetchModels = async (): Promise<ModelInfo[]> => {
  const response = await api.get('/grading/models')
  return response.data
}

// 提交AI批改任务（异步，纯文本）
export const submitGradingTask = async (
  request: AIGradingRequest
): Promise<{ taskId: string; status: string }> => {
  const response = await api.post('/grading/ai-grade', request)
  return response.data
}

// 提交AI批改任务（异步，multipart：文本+图片）
export const submitGradingTaskMultipart = async (params: {
  question: string
  answer?: string
  maxScore?: number
  modelId?: string
  files?: File[]
}): Promise<{ taskId: string; status: string; uploadedImages?: number }> => {
  const fd = new FormData()
  fd.append('question', params.question)
  if (params.answer) fd.append('answer', params.answer)
  if (params.maxScore != null) fd.append('maxScore', String(params.maxScore))
  if (params.modelId) fd.append('modelId', params.modelId)
  if (params.files && params.files.length > 0) {
    params.files.forEach((f) => fd.append('files', f))
  }
  const response = await api.post('/grading/ai-grade-multipart', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data
}

// 轮询查询AI批改任务结果
export const pollTaskResult = async (taskId: string): Promise<any> => {
  const response = await api.get(`/grading/ai-tasks/${taskId}`)
  return response.data
}

export default api
