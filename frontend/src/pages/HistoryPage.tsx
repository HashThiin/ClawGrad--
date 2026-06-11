import { useEffect, useState } from 'react'
import { Button, Card, Empty, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { fetchSubmissions, type SubmissionSummary } from '../services/api'

const { Title } = Typography

const statusColor = (status: string) => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'TIMEOUT') return 'warning'
  return 'processing'
}

const HistoryPage = () => {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [items, setItems] = useState<SubmissionSummary[]>([])

  const load = async () => {
    setLoading(true)
    try {
      setItems(await fetchSubmissions())
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const columns: ColumnsType<SubmissionSummary> = [
    {
      title: '题目',
      dataIndex: 'questionPreview',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (status: string) => <Tag color={statusColor(status)}>{status}</Tag>,
    },
    {
      title: '得分',
      width: 120,
      render: (_, row) =>
        row.totalScore != null ? `${row.totalScore} / ${row.maxScore ?? '-'}` : '-',
    },
    {
      title: '模型',
      dataIndex: 'modelName',
      width: 180,
    },
    {
      title: '提交时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '操作',
      width: 120,
      render: (_, row) => (
        <Button type="link" onClick={() => navigate(`/submissions/${row.taskId}`)}>
          查看详情
        </Button>
      ),
    },
  ]

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>
          提交历史
        </Title>
        <Button type="primary" onClick={() => navigate('/grade')}>
          新建批改
        </Button>
      </Space>
      <Card>
        {items.length === 0 && !loading ? (
          <Empty description="还没有提交记录" />
        ) : (
          <Table
            rowKey="taskId"
            loading={loading}
            columns={columns}
            dataSource={items}
            pagination={{ pageSize: 8 }}
          />
        )}
      </Card>
    </div>
  )
}

export default HistoryPage
