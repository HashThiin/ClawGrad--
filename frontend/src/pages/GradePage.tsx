import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Form,
  Input,
  Button,
  Card,
  message,
  Space,
  Spin,
  Select,
  Upload,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import { InboxOutlined, PictureOutlined } from '@ant-design/icons'
import type { UploadFile } from 'antd/es/upload/interface'
import {
  fetchModels,
  submitGradingTask,
  submitGradingTaskMultipart,
  type ModelInfo,
} from '../services/api'

const { TextArea } = Input
const { Dragger } = Upload
const { Text } = Typography

const GradePage = () => {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()
  const [models, setModels] = useState<ModelInfo[]>([])
  const [selectedModelId, setSelectedModelId] = useState<string | undefined>(undefined)
  const [fileList, setFileList] = useState<UploadFile[]>([])

  useEffect(() => {
    fetchModels()
      .then((list) => {
        setModels(list)
        const def = list.find((m) => m.default) || list[0]
        if (def) setSelectedModelId(def.id)
      })
      .catch((e) => {
        console.warn('获取模型列表失败，将使用后端默认模型', e)
      })
  }, [])

  const selectedModel = models.find((m) => m.id === selectedModelId)
  const hasImages = fileList.length > 0

  const onFinish = async (values: any) => {
    setLoading(true)
    try {
      const trimmedAnswer = (values.answer || '').trim()
      // 校验：答案与图片至少一项
      if (!trimmedAnswer && !hasImages) {
        message.warning('请填写学生答案，或上传作业图片')
        setLoading(false)
        return
      }
      // 视觉模型校验
      if (hasImages && selectedModel && !selectedModel.supportsVision) {
        message.warning(`当前模型「${selectedModel.name}」不支持图片，请切换到支持视觉的模型`)
        setLoading(false)
        return
      }

      let taskId: string
      if (hasImages) {
        const files = fileList
          .map((f) => (f.originFileObj ? (f.originFileObj as File) : null))
          .filter((f): f is File => !!f)
        const res = await submitGradingTaskMultipart({
          question: values.question || '',  // undefined 转空字符串
          answer: trimmedAnswer || undefined,
          modelId: selectedModelId,
          files,
        })
        taskId = res.taskId
      } else {
        const res = await submitGradingTask({
          question: values.question || '',  // undefined 转空字符串
          answer: trimmedAnswer,
          modelId: selectedModelId,
        })
        taskId = res.taskId
      }

      message.success('批改任务已提交，正在由AI处理...')
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
          支持文本和<strong>图片</strong>两种方式提交作业，可选择不同模型进行批改（数学/语文/英语/物理/化学/编程/历史等全科目）。
        </p>

        <Spin spinning={loading} tip="正在提交批改任务...">
          <Form form={form} layout="vertical" onFinish={onFinish}>
            {/* 模型选择 */}
            <Form.Item label="批改模型" required>
              <Select
                value={selectedModelId}
                onChange={setSelectedModelId}
                placeholder="选择模型"
                style={{ width: '100%' }}
                options={models.map((m) => ({
                  value: m.id,
                  label: (
                    <Space>
                      <span>{m.name}</span>
                      <Tag color={m.supportsVision ? 'green' : 'default'}>
                        {m.supportsVision ? '多模态' : '文本'}
                      </Tag>
                      {m.default && <Tag color="blue">默认</Tag>}
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {m.provider}
                      </Text>
                    </Space>
                  ),
                }))}
              />
              {selectedModel?.description && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {selectedModel.description}
                </Text>
              )}
            </Form.Item>

            <Form.Item
              label="题目（可选）"
              name="question"
              extra="上传作业图片时可不填，图片中包含题目和学生答案"
            >
              <TextArea
                rows={5}
                placeholder="可粘贴题目内容...&#10;如果上传的是作业拍照（包含题目和答案），此框可留空"
              />
            </Form.Item>

            <Form.Item label="学生答案（文字）" name="answer" extra="若仅上传图片可不填">
              <TextArea
                rows={6}
                placeholder="请粘贴学生答案...&#10;可以是文字、公式、代码等任意形式；如学生答案为图片，可不填本框，下方上传图片即可"
              />
            </Form.Item>

            {/* 图片上传 */}
            <Form.Item
              label={
                <Space>
                  <PictureOutlined />
                  作业图片（可选，多模态批改）
                </Space>
              }
              extra={
                selectedModel && !selectedModel.supportsVision ? (
                  <Text type="warning">
                    当前模型不支持图片，若需上传图片请切换到带「多模态」标签的模型
                  </Text>
                ) : (
                  <Text type="secondary">支持 PNG / JPG / WEBP，单张不超过 20MB，最多 5 张</Text>
                )
              }
            >
              <Dragger
                fileList={fileList}
                multiple
                accept="image/*"
                maxCount={5}
                beforeUpload={() => false}
                onChange={({ fileList: fl }) => setFileList(fl)}
                onRemove={(file) =>
                  setFileList((prev) => prev.filter((f) => f.uid !== file.uid))
                }
              >
                <p className="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p className="ant-upload-text">点击或拖拽图片到此处上传</p>
                <p className="ant-upload-hint">
                  上传作业拍照后，由具备视觉能力的模型识别并批改
                </p>
              </Dragger>
            </Form.Item>

            <Form.Item>
              <Space>
                <Tooltip
                  title={
                    !selectedModelId
                      ? '请先选择批改模型'
                      : hasImages && selectedModel && !selectedModel.supportsVision
                      ? '当前模型不支持图片'
                      : ''
                  }
                >
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={loading}
                    size="large"
                    disabled={!selectedModelId}
                  >
                    AI智能批改
                  </Button>
                </Tooltip>
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
