import { useEffect, useState } from 'react'
import { Button, Card, Col, Row, Statistic, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { fetchSubmissions, getCurrentUser, type SubmissionSummary } from '../services/api'

const { Title, Text } = Typography

const DashboardPage = () => {
  const navigate = useNavigate()
  const user = getCurrentUser()
  const [items, setItems] = useState<SubmissionSummary[]>([])

  useEffect(() => {
    fetchSubmissions().then(setItems).catch(() => setItems([]))
  }, [])

  const completed = items.filter((item) => item.status === 'COMPLETED').length
  const processing = items.filter((item) => item.status === 'PROCESSING').length

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Card style={{ marginBottom: 24 }}>
        <Title level={3} style={{ marginBottom: 8 }}>
          欢迎回来，{user?.displayName || user?.username}
        </Title>
        <Text type="secondary">这里是你的学生批改工作台。</Text>
      </Card>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="提交总数" value={items.length} />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="已完成" value={completed} valueStyle={{ color: '#3f8600' }} />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="处理中" value={processing} valueStyle={{ color: '#1890ff' }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card title="开始一次新批改">
            <Text type="secondary">提交文本答案或作业图片，批改完成后会自动进入历史记录。</Text>
            <div style={{ marginTop: 16 }}>
              <Button type="primary" onClick={() => navigate('/grade')}>
                去批改
              </Button>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="查看提交历史">
            <Text type="secondary">回看每次提交的流水线进度、得分、错误点和建议。</Text>
            <div style={{ marginTop: 16 }}>
              <Button onClick={() => navigate('/history')}>打开历史</Button>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default DashboardPage
