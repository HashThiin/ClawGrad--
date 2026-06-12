import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import {
  fetchSubmissionDetail,
  type SubmissionDetail,
} from '../services/api'
import ResultViewer from '../components/ResultViewer'

const { Title } = Typography

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

      {detail.status === 'FAILED' || detail.status === 'TIMEOUT' ? (
        <Alert
          type={detail.status === 'TIMEOUT' ? 'warning' : 'error'}
          message={detail.error || '批改未完成'}
          style={{ marginBottom: 16 }}
        />
      ) : null}

      <ResultViewer
        result={detail.result}
        organized={detail.organizedHomework}
        stages={detail.stages}
        currentStage={detail.currentStage}
        status={detail.status}
        modelName={detail.modelName}
      />
    </div>
  )
}

export default SubmissionDetailPage
