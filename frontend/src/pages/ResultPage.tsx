import { useEffect, useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Result,
  Statistic,
  Row,
  Col,
  List,
  Tag,
  Button,
  Space,
  Steps,
  Spin,
  Descriptions,
  Collapse,
  Alert,
  Empty,
} from 'antd'
import {
  ArrowLeftOutlined,
  CheckCircleTwoTone,
  CloseCircleTwoTone,
  ExclamationCircleTwoTone,
} from '@ant-design/icons'
import { pollTaskResult } from '../services/api'
import type {
  AIGradingResult,
  OrganizedHomework,
  StageProgress,
  ItemGradingResult,
} from '../services/api'

// 4 阶段元信息：固定顺序，对应后端 Skill 模板
const STAGE_META: { key: string; title: string; desc: string }[] = [
  { key: 'upload', title: '上传 Upload', desc: '接收文本/图片，归档到任务空间' },
  { key: 'organize', title: '整理 Organize', desc: '多模态识别与题目结构化拆分' },
  { key: 'grading', title: '批改 Grading', desc: '一次调用 AI 批改全部题目' },
  { key: 'feedback', title: '反馈 Feedback', desc: '聚合分数、错点、知识点与建议' },
]

const stageStatusToAntd = (
  s?: string
): 'wait' | 'process' | 'finish' | 'error' => {
  switch (s) {
    case 'running':
      return 'process'
    case 'completed':
      return 'finish'
    case 'failed':
      return 'error'
    default:
      return 'wait'
  }
}

const correctnessTag = (c?: string) => {
  if (c === 'correct')
    return (
      <Tag icon={<CheckCircleTwoTone twoToneColor="#52c41a" />} color="success">
        正确
      </Tag>
    )
  if (c === 'partial')
    return (
      <Tag
        icon={<ExclamationCircleTwoTone twoToneColor="#faad14" />}
        color="warning"
      >
        部分正确
      </Tag>
    )
  if (c === 'wrong')
    return (
      <Tag icon={<CloseCircleTwoTone twoToneColor="#ff4d4f" />} color="error">
        错误
      </Tag>
    )
  return null
}

const ResultPage = () => {
  const { taskId } = useParams()
  const navigate = useNavigate()
  const [result, setResult] = useState<AIGradingResult | null>(null)
  const [organized, setOrganized] = useState<OrganizedHomework | null>(null)
  const [stages, setStages] = useState<StageProgress[]>([])
  const [currentStage, setCurrentStage] = useState<string | undefined>()
  const [status, setStatus] = useState<string>('PROCESSING')
  const [error, setError] = useState('')
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (!taskId) return
    checkResult()
    intervalRef.current = setInterval(checkResult, 2500)
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId])

  const checkResult = async () => {
    try {
      const data = await pollTaskResult(taskId!)
      setStatus(data.status)
      if (data.stages) setStages(data.stages)
      if (data.currentStage) setCurrentStage(data.currentStage)
      if (data.organizedHomework) setOrganized(data.organizedHomework)

      if (data.status === 'COMPLETED') {
        setResult(data.result)
        if (intervalRef.current) clearInterval(intervalRef.current)
      } else if (data.status === 'FAILED') {
        setError(data.error || '批改失败')
        if (intervalRef.current) clearInterval(intervalRef.current)
      }
    } catch (e) {
      console.error('轮询失败:', e)
    }
  }

  // 当前正在跑的阶段索引
  const currentIndex = (() => {
    if (status === 'COMPLETED') return STAGE_META.length
    const idx = STAGE_META.findIndex((m) => m.key === currentStage)
    return idx >= 0 ? idx : 0
  })()

  // 找出 stages 数组里指定 key
  const findStage = (key: string) => stages.find((s) => s.name === key)

  // 顶部 Skill 流水线进度卡
  const renderPipelineCard = () => (
    <Card
      title={
        <Space>
          <span>ClawGrad Skill 批改流水线</span>
          {status === 'PROCESSING' && <Spin size="small" />}
          {status === 'COMPLETED' && <Tag color="success">已完成</Tag>}
          {status === 'FAILED' && <Tag color="error">已失败</Tag>}
        </Space>
      }
      style={{ marginBottom: 24 }}
    >
      <Steps
        current={currentIndex}
        status={status === 'FAILED' ? 'error' : 'process'}
        items={STAGE_META.map((m) => {
          const sp = findStage(m.key)
          return {
            title: m.title,
            description: (
              <div style={{ fontSize: 12, color: '#888' }}>
                <div>{m.desc}</div>
                {sp?.duration != null && sp.status === 'completed' && (
                  <div style={{ color: '#3f8600' }}>
                    耗时 {(sp.duration / 1000).toFixed(2)}s
                  </div>
                )}
                {sp?.status === 'running' && (
                  <div style={{ color: '#1890ff' }}>运行中…</div>
                )}
                {sp?.status === 'failed' && (
                  <div style={{ color: '#cf1322' }}>失败</div>
                )}
              </div>
            ),
            status: stageStatusToAntd(sp?.status),
          }
        })}
      />
    </Card>
  )

  // OCR 识别结果卡
  const renderOrganizedCard = () => {
    if (!organized) return null
    const items = organized.items || []
    return (
      <Card
        title="AI 识别到的作业内容"
        size="small"
        style={{ marginBottom: 24, background: '#fafafa' }}
        extra={
          <Space>
            {organized.fromImage && <Tag color="blue">图片 OCR</Tag>}
            {organized.subject && (
              <Tag color="geekblue">{organized.subject}</Tag>
            )}
            {organized.totalMaxScore != null && (
              <Tag color="purple">总满分 {organized.totalMaxScore}</Tag>
            )}
            <Tag>{items.length} 题</Tag>
          </Space>
        }
      >
        {organized.remark && (
          <Alert
            type="info"
            showIcon
            message={organized.remark}
            style={{ marginBottom: 12 }}
          />
        )}
        {items.length > 0 ? (
          <Collapse
            items={items.map((it) => ({
              key: String(it.index),
              label: (
                <Space>
                  <Tag color="blue">第 {it.index} 题</Tag>
                  {it.maxScore != null && <Tag>满分 {it.maxScore}</Tag>}
                  <span style={{ color: '#666' }}>
                    {(it.question || '').slice(0, 40)}
                    {(it.question || '').length > 40 ? '…' : ''}
                  </span>
                </Space>
              ),
              children: (
                <Descriptions column={1} size="small" bordered>
                  <Descriptions.Item label="题目原文">
                    <pre
                      style={{
                        margin: 0,
                        whiteSpace: 'pre-wrap',
                        fontFamily: 'inherit',
                      }}
                    >
                      {it.question || '(未识别)'}
                    </pre>
                  </Descriptions.Item>
                  <Descriptions.Item label="学生答案">
                    <pre
                      style={{
                        margin: 0,
                        whiteSpace: 'pre-wrap',
                        fontFamily: 'inherit',
                      }}
                    >
                      {it.answer || '(未识别)'}
                    </pre>
                  </Descriptions.Item>
                </Descriptions>
              ),
            }))}
          />
        ) : (
          <Empty description="尚未识别到题目" />
        )}
      </Card>
    )
  }

  // 逐题批改结果（Collapse）
  const renderItemsCard = (items: ItemGradingResult[]) => (
    <Card title="逐题批改详情" style={{ marginTop: 24 }}>
      <Collapse
        defaultActiveKey={items.map((i) => String(i.index))}
        items={items.map((it) => {
          const ratio =
            it.maxScore && it.maxScore > 0 ? it.score / it.maxScore : 0
          const color =
            ratio >= 0.8 ? '#3f8600' : ratio >= 0.6 ? '#1890ff' : '#cf1322'
          return {
            key: String(it.index),
            label: (
              <Space wrap>
                <Tag color="blue">第 {it.index} 题</Tag>
                <span style={{ fontWeight: 600, color }}>
                  {it.score} / {it.maxScore} 分
                </span>
                {correctnessTag(it.correctness)}
                <span style={{ color: '#888' }}>
                  {(it.question || '').slice(0, 40)}
                  {(it.question || '').length > 40 ? '…' : ''}
                </span>
              </Space>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                {it.question && (
                  <Descriptions column={1} size="small" bordered>
                    <Descriptions.Item label="题目">
                      <pre
                        style={{
                          margin: 0,
                          whiteSpace: 'pre-wrap',
                          fontFamily: 'inherit',
                        }}
                      >
                        {it.question}
                      </pre>
                    </Descriptions.Item>
                    {it.answer && (
                      <Descriptions.Item label="学生答案">
                        <pre
                          style={{
                            margin: 0,
                            whiteSpace: 'pre-wrap',
                            fontFamily: 'inherit',
                          }}
                        >
                          {it.answer}
                        </pre>
                      </Descriptions.Item>
                    )}
                    {it.feedback && (
                      <Descriptions.Item label="批改点评">
                        {it.feedback}
                      </Descriptions.Item>
                    )}
                  </Descriptions>
                )}
                {it.errors && it.errors.length > 0 && (
                  <List
                    size="small"
                    header={<b>错误点</b>}
                    bordered
                    dataSource={it.errors}
                    renderItem={(e) => (
                      <List.Item>
                        <Space direction="vertical" style={{ width: '100%' }}>
                          <Space wrap>
                            <Tag color="error">{e.errorType}</Tag>
                            <span style={{ fontWeight: 600 }}>
                              位置：{e.location}
                            </span>
                          </Space>
                          <span>{e.description}</span>
                          <span style={{ color: '#52c41a' }}>
                            建议：{e.correction}
                          </span>
                        </Space>
                      </List.Item>
                    )}
                  />
                )}
              </Space>
            ),
          }
        })}
      />
    </Card>
  )

  // ----------- 渲染 -----------
  // 失败
  if (status === 'FAILED') {
    return (
      <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
        {renderPipelineCard()}
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

  // 进行中：展示流水线 + 已识别内容（如有）
  if (status !== 'COMPLETED') {
    return (
      <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
        {renderPipelineCard()}
        {organized ? (
          renderOrganizedCard()
        ) : (
          <Card style={{ textAlign: 'center' }}>
            <Spin size="large" />
            <h3 style={{ marginTop: 16 }}>AI 正在批改中…</h3>
            <p style={{ color: '#666' }}>任务ID：{taskId}</p>
          </Card>
        )}
      </div>
    )
  }

  // 已完成
  if (!result) {
    return (
      <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
        <Result status="info" title="暂无批改结果" />
      </div>
    )
  }

  const dimensionEntries: [string, number][] = Object.entries(
    result.dimensionScores || {}
  ) as [string, number][]
  const items = result.items || []

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      {renderPipelineCard()}

      <Card style={{ marginBottom: 24 }}>
        <Result
          status="success"
          title={`AI 总评分：${result.totalScore} / ${result.maxScore} 分`}
          subTitle={result.overallSummary || result.feedback}
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
      </Card>

      {/* OCR 识别原文 */}
      {renderOrganizedCard()}

      {/* 维度评分 */}
      {dimensionEntries.length > 0 && (
        <Card title="多维度评分" style={{ marginBottom: 24 }}>
          <Row gutter={[16, 16]}>
            {dimensionEntries.map(([key, value]) => (
              <Col span={8} key={key}>
                <Card size="small">
                  <Statistic
                    title={key}
                    value={value}
                    suffix={`/ ${result.maxScore ?? ''}`}
                    valueStyle={{
                      color:
                        value >= (result.maxScore ?? 100) * 0.8
                          ? '#3f8600'
                          : value >= (result.maxScore ?? 100) * 0.6
                          ? '#1890ff'
                          : '#cf1322',
                    }}
                  />
                </Card>
              </Col>
            ))}
          </Row>
        </Card>
      )}

      {/* 多题逐题展开 */}
      {items.length > 0 && renderItemsCard(items)}

      {/* 综合反馈（多题） */}
      {result.overallSummary && (
        <Card title="综合分析" style={{ marginTop: 24 }}>
          <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
            {result.overallSummary}
          </p>
        </Card>
      )}

      {/* 单题/全局错误（兼容旧数据） */}
      {items.length === 0 && result.errors && result.errors.length > 0 && (
        <Card title="错误分析" style={{ marginTop: 24 }}>
          <List
            dataSource={result.errors}
            renderItem={(item: any) => (
              <List.Item>
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Space>
                    <Tag color="error">{item.errorType}</Tag>
                    <span style={{ fontWeight: 'bold' }}>
                      位置：{item.location}
                    </span>
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
        <Card title="改进建议" style={{ marginTop: 24 }}>
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
        <Card title="知识点掌握情况" style={{ marginTop: 24 }}>
          <List
            dataSource={result.knowledgePoints}
            renderItem={(item: any) => (
              <List.Item>
                <Space>
                  <span style={{ fontWeight: 'bold' }}>{item.name}</span>
                  <Tag
                    color={
                      item.masteryLevel === 'mastered'
                        ? 'success'
                        : item.masteryLevel === 'partial'
                        ? 'warning'
                        : 'error'
                    }
                  >
                    {item.masteryLevel === 'mastered'
                      ? '已掌握'
                      : item.masteryLevel === 'partial'
                      ? '部分掌握'
                      : '需加强'}
                  </Tag>
                  <span style={{ color: '#666' }}>{item.description}</span>
                </Space>
              </List.Item>
            )}
          />
        </Card>
      )}
    </div>
  )
}

export default ResultPage
