import { Layout, Menu, Button, Space, Typography } from 'antd'
import {
  DashboardOutlined,
  EditOutlined,
  HistoryOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { getCurrentUser, logout } from '../services/api'

const { Header, Content } = Layout
const { Text } = Typography

const StudentLayout = ({ children }: { children: JSX.Element }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const user = getCurrentUser()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh', background: '#f5f7fb' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 24,
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
          padding: '0 24px',
        }}
      >
        <div
          onClick={() => navigate('/dashboard')}
          style={{ fontWeight: 700, fontSize: 18, cursor: 'pointer' }}
        >
          ClawGrad
        </div>
        <Menu
          mode="horizontal"
          selectedKeys={[
            location.pathname.startsWith('/history') ||
            location.pathname.startsWith('/submissions')
              ? '/history'
              : location.pathname.startsWith('/grade') ||
                location.pathname.startsWith('/result')
              ? '/grade'
              : '/dashboard',
          ]}
          style={{ flex: 1, borderBottom: 'none' }}
          items={[
            {
              key: '/dashboard',
              icon: <DashboardOutlined />,
              label: 'Dashboard',
              onClick: () => navigate('/dashboard'),
            },
            {
              key: '/grade',
              icon: <EditOutlined />,
              label: 'Start grading',
              onClick: () => navigate('/grade'),
            },
            {
              key: '/history',
              icon: <HistoryOutlined />,
              label: 'History',
              onClick: () => navigate('/history'),
            },
          ]}
        />
        <Space>
          <Text type="secondary">{user?.displayName || user?.username}</Text>
          <Button icon={<LogoutOutlined />} onClick={handleLogout}>
            Logout
          </Button>
        </Space>
      </Header>
      <Content>{children}</Content>
    </Layout>
  )
}

export default StudentLayout
