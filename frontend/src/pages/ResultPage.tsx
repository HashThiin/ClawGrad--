import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Card, Result, Space, Spin, message } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import ResultViewer from '../components/ResultViewer'
import { pollTaskResult, submitGradingTask } from '../services/api'
import type {
  AIGradingResult,
  OrganizedHomework,
  StageProgress,
} from '../services/api'

const ResultPage = () => {
  const { taskId } = useParams()
  const navigate = useNavigate()
  const [result, setResult] = useState<AIGradingResult | null>(null)
  const [organized, setOrganized] = useState<OrganizedHomework | null>(null)
  const [stages, setStages] = useState<StageProgress[]>([])
  const [currentStage, setCurrentStage] = useState<string | undefined>()
  const [status, setStatus] = useState<string>('PROCESSING')
  const [error, setError] = useState('')
  const [modelName, setModelName] = useState('')
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const [timeoutData, setTimeoutData] = useState<{
    question?: string
    answer?: string
    suggestFastModel?: boolean
  } | null>(null)

  useEffect(() => {
    if (!taskId) return
    checkResult()
    intervalRef.current = setInterval(checkResult, 2500)
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId])

  const stopPolling = () => {
    if (intervalRef.current) clearInterval(intervalRef.current)
  }

  const checkResult = async () => {
    try {
      const data = await pollTaskResult(taskId!)
      setStatus(data.status)
      if (data.stages) setStages(data.stages)
      if (data.currentStage) setCurrentStage(data.currentStage)
      if (data.organizedHomework) setOrganized(data.organizedHomework)
      if (data.modelName) setModelName(data.modelName)

      if (data.status === 'COMPLETED') {
        setResult(data.result)
        stopPolling()
      } else if (data.status === 'TIMEOUT') {
        setError(data.error || '批改超时')
        setTimeoutData({
          question: data.question,
          answer: data.answer,
          suggestFastModel: data.suggestFastModel,
        })
        stopPolling()
      } else if (data.status === 'FAILED') {
        setError(data.error || '批改失败')
        stopPolling()
      }
    } catch (e) {
      console.error('轮询失败:', e)
    }
  }

  const handleRetryWithFastModel = async () => {
    if (!timeoutData) return
    try {
      const res = await submitGradingTask({
        question: timeoutData.question || '',
        answer: timeoutData.answer || '',
        modelId: 'bailian-token-plan/qwen3.6-flash',
      })
      setTimeoutData(null)
      navigate(`/result/${res.taskId}`)
    } catch (e: any) {
      message.error('重试提交失败: ' + (e.response?.data?.message || e.message))
    }
  }

  if (status === 'TIMEOUT') {
    return (
      <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
        <ResultViewer
          organized={organized}
          stages={stages}
          currentStage={currentStage}
          status={status}
          modelName={modelName}
        />
        <Card style={{ marginBottom: 24 }}>
          <Result
            status="warning"
            title="思考时间过长"
            subTitle={
              <span>
                {error}
                <br />
                当前模型在处理本题时超过了响应限制。
              </span>
            }
            extra={
              <Space direction="vertical" style={{ width: '100%', maxWidth: 400 }}>
                {timeoutData?.suggestFastModel && (
                  <Button type="primary" size="large" block onClick={handleRetryWithFastModel}>
                    更换快速模型重试（Qwen3.6 Flash）
                  </Button>
                )}
                <Space>
                  <Button onClick={() => navigate('/grade')} block>
                    返回批改页面重新配置
                  </Button>
                  <Button danger block onClick={() => navigate('/')}>
                    退出
                  </Button>
                </Space>
              </Space>
            }
          />
        </Card>
      </div>
    )
  }

  if (status === 'FAILED') {
    return (
      <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
        <ResultViewer
          organized={organized}
          stages={stages}
          currentStage={currentStage}
          status={status}
          modelName={modelName}
        />
        <Result
          status="error"
          title="批改失败"
          subTitle={error}
          extra={
            <Button type="primary" onClick={() => navigate('/grade')}>
              重新提交
            </Button>
          }
        />
      </div>
    )
  }

  if (status !== 'COMPLETED') {
    return (
      <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
        <ResultViewer
          organized={organized}
          stages={stages}
          currentStage={currentStage}
          status={status}
          modelName={modelName}
        />
        {!organized && (
          <Card style={{ textAlign: 'center' }}>
            <Spin size="large" />
            <h3 style={{ marginTop: 16 }}>AI 正在批改中...</h3>
            <p style={{ color: '#666' }}>任务 ID：{taskId}</p>
          </Card>
        )}
      </div>
    )
  }

  if (!result) {
    return (
      <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
        <Result status="info" title="暂无批改结果" />
      </div>
    )
  }

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <ResultViewer
        result={result}
        organized={organized}
        stages={stages}
        currentStage={currentStage}
        status={status}
        modelName={modelName}
        resultExtra={
          <Space>
            <Button type="primary" onClick={() => navigate('/grade')}>
              继续批改
            </Button>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/')}>
              返回首页
            </Button>
          </Space>
        }
      />
    </div>
  )
}

export default ResultPage
