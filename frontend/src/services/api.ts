import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

export interface AIGradingRequest {
  question: string
  answer: string
  attachments?: string[]
  maxScore?: number
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

// 提交AI批改任务（异步）
export const submitGradingTask = async (request: AIGradingRequest): Promise<{taskId: string, status: string}> => {
  const response = await api.post('/grading/ai-grade', request)
  return response.data
}

// 轮询查询AI批改任务结果
export const pollTaskResult = async (taskId: string): Promise<any> => {
  const response = await api.get(`/grading/ai-tasks/${taskId}`)
  return response.data
}

export default api
