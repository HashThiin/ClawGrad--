import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  List,
  Space,
  Spin,
  Statistic,
  Steps,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import {
  fetchSubmissionDetail,
  type ItemGradingResult,
  type StageProgress,
  type SubmissionDetail,
} from '../services/api'

const { Title, Paragraph } = Typography

const stageStatusToAntd = (
  s?: string
): 'wait' | 'process' | 'finish' | 'error' => {
  if (s === 'completed') return 'finish'
  if (s === 'running') return 'process'
  if (s === 'failed') return 'error'
  return 'wait'
}

const renderStages = (stages?: StageProgress[]) => (
  <Steps
    items={(stages || []).map((stage) => ({
      title: stage.name,
      status: stageStatusToAntd(stage.status),
      description:
        stage.duration != null ? `${(stage.duration / 1000).toFixed(2)}s` : stage.status,
    }))}
  />
)

const renderItems = (items: ItemGradingResult[]) => (
  <List
    bordered
    dataSource={items}
    renderItem={(item) => (
      <List.Item>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space wrap>
            <Tag color="blue">第 {item.index} 题</Tag>
            <strong>
              {item.score} / {item.maxScore}
            </strong>
            {item.correctness && <Tag>{item.correctness}</Tag>}
          </Space>
          {item.question && <Paragraph style={{ marginBottom: 0 }}>{item.question}</Paragraph>}
          {item.feedback && <Alert type="info" message={item.feedback} />}
        </Space>
      </List.Item>
    )}
  />
)

const SubmissionDetailPage = () => {
  const { taskId } = useParams()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [detail, setDetail] = useState<SubmissionDetail | null>(null)

  useEffect(() => {
    if (!taskId) return
    fetchSubmissionDetail(taskId)
      .then(setDetail)
      .finally(() => setLoading(false))
  }, [taskId])

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!detail) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="提交记录不存在" />
      </div>
    )
  }

  const result = detail.result
  const items = result?.items || []

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>
          提交详情
        </Title>
        <Space>
          <Button onClick={() => navigate(`/result/${detail.taskId}`)}>打开轮询结果页</Button>
          <Button onClick={() => navigate('/history')}>返回历史</Button>
        </Space>
      </Space>

      <Card style={{ marginBottom: 16 }}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="任务 ID">{detail.taskId}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag>{detail.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="模型">{detail.modelName || detail.modelId}</Descriptions.Item>
          <Descriptions.Item label="提交时间">
            {detail.createdAt ? dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="题目" span={2}>
            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>
              {detail.question || '(图片作业)'}
            </pre>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {detail.stages && detail.stages.length > 0 && (
        <Card title="批改流水线" style={{ marginBottom: 16 }}>
          {renderStages(detail.stages)}
        </Card>
      )}

      {detail.status === 'FAILED' || detail.status === 'TIMEOUT' ? (
        <Alert
          type={detail.status === 'TIMEOUT' ? 'warning' : 'error'}
          message={detail.error || '批改未完成'}
          style={{ marginBottom: 16 }}
        />
      ) : null}

      {result && (
        <Card title="批改结果" style={{ marginBottom: 16 }}>
          <Statistic
            title="总分"
            value={result.totalScore}
            suffix={`/ ${result.maxScore}`}
            valueStyle={{ color: '#3f8600' }}
          />
          {(result.overallSummary || result.feedback) && (
            <Paragraph style={{ marginTop: 16, whiteSpace: 'pre-wrap' }}>
              {result.overallSummary || result.feedback}
            </Paragraph>
          )}
        </Card>
      )}

      {items.length > 0 && <Card title="逐题详情">{renderItems(items)}</Card>}
    </div>
  )
}

export default SubmissionDetailPage
