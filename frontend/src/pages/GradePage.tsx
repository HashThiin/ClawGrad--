import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Form, Input, Button, Card, message, Space, Spin } from 'antd'
import { submitGradingTask } from '../services/api'

const { TextArea } = Input

const GradePage = () => {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()

  const onFinish = async (values: any) => {
    setLoading(true)
    try {
      const request = {
        question: values.question,
        answer: values.answer,
      }

      // 提交异步批改任务
      const { taskId } = await submitGradingTask(request)
      
      message.success('批改任务已提交，正在由AI处理...')
      
      // 跳转到结果页，通过taskId轮询查询结果
      navigate(`/result/${taskId}`)
      
    } catch (error: any) {
      message.error(`提交失败: ${error.response?.data?.message || error.message}`)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ padding: '24px', maxWidth: '1200px', margin: '0 auto' }}>
      <Card title="AI智能作业批改" style={{ marginBottom: '24px' }}>
        <p style={{ color: '#666', marginBottom: '16px' }}>
          只需输入题目和答案，AI自动识别科目类型并给出专业批改意见。
        </p>
        
        <Spin spinning={loading} tip="正在提交批改任务...">
          <Form form={form} layout="vertical" onFinish={onFinish}>
            <Form.Item
              label="题目"
              name="question"
              rules={[{ required: true, message: '请输入题目' }]}
            >
              <TextArea 
                rows={6} 
                placeholder="请粘贴题目内容...&#10;支持数学、语文、英语、物理、化学、编程等所有科目&#10;AI会自动识别科目类型" 
              />
            </Form.Item>

            <Form.Item
              label="学生答案"
              name="answer"
              rules={[{ required: true, message: '请输入学生答案' }]}
            >
              <TextArea 
                rows={10} 
                placeholder="请粘贴学生答案...&#10;可以是文字、公式、代码等任意形式&#10;AI会自动理解和批改" 
              />
            </Form.Item>

            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" loading={loading} size="large">
                  AI智能批改
                </Button>
                <Button onClick={() => navigate('/')} size="large">
                  返回首页
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Spin>
      </Card>
    </div>
  )
}

export default GradePage
