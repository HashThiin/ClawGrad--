import { Navigate, useLocation } from 'react-router-dom'
import { isAuthenticated } from '../services/api'

const ProtectedRoute = ({ children }: { children: JSX.Element }) => {
  const location = useLocation()

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return children
}

export default ProtectedRoute
