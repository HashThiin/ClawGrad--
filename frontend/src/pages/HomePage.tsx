import { Card, Button, Row, Col, Typography, Space } from 'antd'
import { useNavigate } from 'react-router-dom'
import { CodeOutlined, ExperimentOutlined } from '@ant-design/icons'

const { Title, Paragraph } = Typography

const HomePage = () => {
  const navigate = useNavigate()

  return (
    <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
      <Title level={2} style={{ textAlign: 'center', marginBottom: '40px' }}>
        ClawGrad 智能作业批改系统
      </Title>
      
      <Row gutter={[24, 24]}>
        <Col xs={24} md={12}>
          <Card
            hoverable
            onClick={() => navigate('/grade')}
            style={{ height: '200px' }}
          >
            <Space direction="vertical" size="large" style={{ width: '100%' }}>
              <CodeOutlined style={{ fontSize: '48px', color: '#1890ff' }} />
              <div>
                <Title level={4}>开始批改</Title>
                <Paragraph type="secondary">
                  提交代码作业，AI智能批改，即时获取评分和反馈
                </Paragraph>
              </div>
            </Space>
          </Card>
        </Col>

        <Col xs={24} md={12}>
          <Card
            hoverable
            style={{ height: '200px' }}
          >
            <Space direction="vertical" size="large" style={{ width: '100%' }}>
              <ExperimentOutlined style={{ fontSize: '48px', color: '#52c41a' }} />
              <div>
                <Title level={4}>功能特性</Title>
                <Paragraph type="secondary">
                  支持多种编程语言，智能分析代码质量，提供改进建议
                </Paragraph>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card style={{ marginTop: '24px' }}>
        <Title level={5}>支持的编程语言</Title>
        <Space wrap>
          <Button>Java</Button>
          <Button>Python</Button>
          <Button>C++</Button>
          <Button>JavaScript</Button>
          <Button>Go</Button>
        </Space>
      </Card>
    </div>
  )
}

export default HomePage
