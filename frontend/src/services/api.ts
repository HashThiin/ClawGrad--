import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
})

const AUTH_TOKEN_KEY = 'clawgrad_token'
const AUTH_USER_KEY = 'clawgrad_user'

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers = config.headers || {}
    ;(config.headers as any).Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuth()
      const path = window.location.pathname
      if (path !== '/login' && path !== '/register') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export interface AuthUser {
  userId: number
  username: string
  displayName?: string
  role: string
}

export interface AuthResponse extends AuthUser {
  token: string
  tokenType: string
}

export const setAuth = (auth: AuthResponse) => {
  localStorage.setItem(AUTH_TOKEN_KEY, auth.token)
  localStorage.setItem(
    AUTH_USER_KEY,
    JSON.stringify({
      userId: auth.userId,
      username: auth.username,
      displayName: auth.displayName,
      role: auth.role,
    })
  )
}

export const clearAuth = () => {
  localStorage.removeItem(AUTH_TOKEN_KEY)
  localStorage.removeItem(AUTH_USER_KEY)
}

export const getCurrentUser = (): AuthUser | null => {
  const raw = localStorage.getItem(AUTH_USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    clearAuth()
    return null
  }
}

export const isAuthenticated = () => !!localStorage.getItem(AUTH_TOKEN_KEY)

export const login = async (params: {
  username: string
  password: string
}): Promise<AuthResponse> => {
  const response = await api.post('/auth/login', params)
  setAuth(response.data)
  return response.data
}

export const register = async (params: {
  username: string
  password: string
  displayName?: string
}): Promise<AuthResponse> => {
  const response = await api.post('/auth/register', params)
  setAuth(response.data)
  return response.data
}

export const logout = () => clearAuth()

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
  /** 使用的模型ID */
  modelId?: string
  /** 使用的模型展示名 */
  modelName?: string
  /** 超时重试用 */
  question?: string
  answer?: string
  suggestFastModel?: boolean
}

export interface SubmissionSummary {
  taskId: string
  status: string
  questionPreview: string
  modelId?: string
  modelName?: string
  totalScore?: number
  maxScore?: number
  createdAt: string
  updatedAt: string
}

export interface SubmissionDetail extends TaskResultResponse {
  question?: string
  answer?: string
  totalScore?: number
  maxScore?: number
  createdAt?: string
  updatedAt?: string
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

export const fetchSubmissions = async (): Promise<SubmissionSummary[]> => {
  const response = await api.get('/submissions')
  return response.data
}

export const fetchSubmissionDetail = async (
  taskId: string
): Promise<SubmissionDetail> => {
  const response = await api.get(`/submissions/${taskId}`)
  return response.data
}

export default api
