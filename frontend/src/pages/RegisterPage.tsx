import { Button, Card, Form, Input, Typography, message } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { register } from '../services/api'

const { Title, Text } = Typography

const RegisterPage = () => {
  const navigate = useNavigate()

  const onFinish = async (values: {
    username: string
    displayName?: string
    password: string
  }) => {
    try {
      await register(values)
      message.success('注册成功')
      navigate('/dashboard', { replace: true })
    } catch (error: any) {
      message.error(error.response?.data?.message || '注册失败')
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        background: '#f5f7fb',
        padding: 24,
      }}
    >
      <Card style={{ width: '100%', maxWidth: 460 }}>
        <Title level={3} style={{ marginBottom: 8 }}>
          注册学生账号
        </Title>
        <Text type="secondary">注册后即可提交作业并保存历史记录</Text>
        <Form layout="vertical" onFinish={onFinish} style={{ marginTop: 24 }}>
          <Form.Item
            label="用户名"
            name="username"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 3, message: '用户名至少 3 个字符' },
            ]}
          >
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item label="昵称" name="displayName">
            <Input />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码至少 6 个字符' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large">
            注册并登录
          </Button>
        </Form>
        <div style={{ marginTop: 16, textAlign: 'center' }}>
          已有账号？ <Link to="/login">去登录</Link>
        </div>
      </Card>
    </div>
  )
}

export default RegisterPage
