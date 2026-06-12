import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import HomePage from './pages/HomePage'
import GradePage from './pages/GradePage'
import ResultPage from './pages/ResultPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import HistoryPage from './pages/HistoryPage'
import SubmissionDetailPage from './pages/SubmissionDetailPage'
import ProtectedRoute from './components/ProtectedRoute'
import StudentLayout from './components/StudentLayout'

const protectedPage = (page: JSX.Element) => (
  <ProtectedRoute>
    <StudentLayout>{page}</StudentLayout>
  </ProtectedRoute>
)

function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <Router>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/dashboard" element={protectedPage(<DashboardPage />)} />
          <Route path="/grade" element={protectedPage(<GradePage />)} />
          <Route path="/result/:taskId" element={protectedPage(<ResultPage />)} />
          <Route path="/history" element={protectedPage(<HistoryPage />)} />
          <Route
            path="/submissions/:taskId"
            element={protectedPage(<SubmissionDetailPage />)}
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Router>
    </ConfigProvider>
  )
}

export default App
