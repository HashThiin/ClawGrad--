import { useEffect, useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Result, Statistic, Row, Col, List, Tag, Button, Space, Steps, Spin, Progress } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { pollTaskResult } from '../services/api'
import type { AIGradingResult } from '../services/api'

const ResultPage = () => {
  const { taskId } = useParams()
  const navigate = useNavigate()
  const [result, setResult] = useState<AIGradingResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [status, setStatus] = useState('PROCESSING')
  const [error, setError] = useState('')
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (!taskId) return

    // 立即查询一次
    checkResult()

    // 每3秒轮询一次
    intervalRef.current = setInterval(checkResult, 3000)

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
      }
    }
  }, [taskId])

  const checkResult = async () => {
    try {
      const data = await pollTaskResult(taskId!)
      setStatus(data.status)

      if (data.status === 'COMPLETED') {
        setResult(data.result)
        setLoading(false)
        if (intervalRef.current) {
          clearInterval(intervalRef.current)
        }
      } else if (data.status === 'FAILED') {
        setError(data.error || '批改失败')
        setLoading(false)
        if (intervalRef.current) {
          clearInterval(intervalRef.current)
        }
      }
      // PROCESSING 继续轮询
    } catch (e) {
      console.error('轮询失败:', e)
    }
  }

  // 加载中状态
  if (loading && status === 'PROCESSING') {
    return (
      <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto', textAlign: 'center' }}>
        <Card>
          <Spin size="large" />
          <h3 style={{ marginTop: '16px' }}>AI正在智能批改中...</h3>
          <p style={{ color: '#666' }}>任务ID: {taskId}</p>
          <p style={{ color: '#666' }}>请稍候，AI正在分析题目、评估答案并生成详细批改报告</p>
          <Progress percent={100} status="active" showInfo={false} style={{ maxWidth: '400px', margin: '0 auto' }} />
        </Card>
      </div>
    )
  }

  // 失败状态
  if (status === 'FAILED') {
    return (
      <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
        <Result status="error" title="批改失败" subTitle={error} />
      </div>
    )
  }

  if (!result) {
    return (
      <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
        <Result status="info" title="暂无批改结果" />
      </div>
    )
  }

  // 动态渲染维度评分
  const dimensionEntries: [string, number][] = Object.entries(result.dimensionScores || {}) as [string, number][]

  return (
    <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
      <Card title="AI智能批改结果" style={{ marginBottom: '24px' }}>
        <Result
          status="success"
          title={`AI评分：${result.totalScore} / ${result.maxScore} 分`}
          subTitle={result.feedback}
          extra={
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

        {/* 维度评分 */}
        {dimensionEntries.length > 0 && (
          <Row gutter={[16, 16]} style={{ marginTop: '24px' }}>
            {dimensionEntries.map(([key, value]) => (
              <Col span={8} key={key}>
                <Card>
                  <Statistic
                    title={key}
                    value={value}
                    suffix={`/ ${result.maxScore ?? ''}`}
                    valueStyle={{ color: value >= (result.maxScore ?? 100) * 0.8 ? '#3f8600' : value >= (result.maxScore ?? 100) * 0.6 ? '#1890ff' : '#cf1322' }}
                  />
                </Card>
              </Col>
            ))}
          </Row>
        )}

        {/* 错误点 */}
        {result.errors && result.errors.length > 0 && (
          <Card title="错误分析" style={{ marginTop: '24px' }}>
            <List
              dataSource={result.errors}
              renderItem={(item: any) => (
                <List.Item>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Space>
                      <Tag color="error">{item.errorType}</Tag>
                      <span style={{ fontWeight: 'bold' }}>位置：{item.location}</span>
                    </Space>
                    <p>{item.description}</p>
                    <p style={{ color: '#52c41a' }}>建议：{item.correction}</p>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        )}

        {/* 改进建议 */}
        {result.suggestions && result.suggestions.length > 0 && (
          <Card title="改进建议" style={{ marginTop: '24px' }}>
            <List
              dataSource={result.suggestions}
              renderItem={(item: any) => (
                <List.Item>
                  <Tag color="processing">{item}</Tag>
                </List.Item>
              )}
            />
          </Card>
        )}

        {/* 知识点掌握 */}
        {result.knowledgePoints && result.knowledgePoints.length > 0 && (
          <Card title="知识点掌握情况" style={{ marginTop: '24px' }}>
            <List
              dataSource={result.knowledgePoints}
              renderItem={(item: any) => (
                <List.Item>
                  <Space>
                    <span style={{ fontWeight: 'bold' }}>{item.name}</span>
                    <Tag color={
                      item.masteryLevel === 'mastered' ? 'success' :
                      item.masteryLevel === 'partial' ? 'warning' : 'error'
                    }>
                      {item.masteryLevel === 'mastered' ? '已掌握' :
                       item.masteryLevel === 'partial' ? '部分掌握' : '需加强'}
                    </Tag>
                    <span style={{ color: '#666' }}>{item.description}</span>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        )}

        {/* AI推理过程 */}
        {result.reasoningSteps && result.reasoningSteps.length > 0 && (
          <Card title="AI批改过程" style={{ marginTop: '24px' }}>
            <Steps direction="vertical" size="small">
              {result.reasoningSteps.map((step: string, index: number) => (
                <Steps.Step key={index} title={step} status="finish" />
              ))}
            </Steps>
          </Card>
        )}
      </Card>
    </div>
  )
}

export default ResultPage
