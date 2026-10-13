import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { AppErrorBoundary } from './components/AppErrorBoundary'
import { AdminUsersPage } from './admin/AdminUsersPage'
import { DocumentCataloguePage } from './catalogue/DocumentCataloguePage'
import { AdminDocumentTypesPage } from './catalogue/AdminDocumentTypesPage'
import { AccountPage } from './auth/AccountPage'
import { LoginPage } from './auth/LoginPage'
import { RegisterPage } from './auth/RegisterPage'
import { DashboardRedirect, ProtectedRoute, PublicOnlyRoute, RoleRoute } from './auth/guards'
import { AppLayout } from './layout/AppLayout'
import { PublicLayout } from './layout/PublicLayout'
import { NotFoundPage } from './pages/NotFoundPage'
import { AdminDashboardPage } from './admin/AdminDashboardPage'
import { AdminMonitoringPage } from './admin/AdminMonitoringPage'
import { AdminRequestDetailPage } from './admin/AdminRequestDetailPage'
import { AdminReportsPage } from './admin/AdminReportsPage'
import { NotificationsPage } from './notifications/NotificationsPage'
import { ProfilePage } from './profile/ProfilePage'
import { NewStudentRequestPage } from './requests/NewStudentRequestPage'
import { MyRequestsPage } from './requests/MyRequestsPage'
import { StudentRequestDetailPage } from './requests/StudentRequestDetailPage'
import { StaffWorkListPage } from './staff/StaffWorkListPage'
import { StaffRequestDetailPage } from './staff/StaffRequestDetailPage'
import './styles.css'

function App() {
  const location = useLocation()
  return (
    <AuthProvider>
      <AppErrorBoundary key={location.pathname}>
      <Routes>
        <Route element={<PublicOnlyRoute />}>
          <Route element={<PublicLayout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardRedirect />} />
            <Route path="/account" element={<AccountPage />} />
            <Route path="/catalogue" element={<DocumentCataloguePage />} />
            <Route path="/notifications" element={<NotificationsPage />} />

            <Route element={<RoleRoute allowedRoles={['STUDENT']} />}>
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/requests" element={<MyRequestsPage />} />
              <Route path="/requests/new" element={<NewStudentRequestPage />} />
              <Route path="/requests/:requestId" element={<StudentRequestDetailPage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={['STAFF']} />}>
              <Route path="/staff/requests" element={<StaffWorkListPage />} />
              <Route path="/staff/requests/:requestId" element={<StaffRequestDetailPage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={['ADMIN']} />}>
              <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
              <Route path="/admin/users" element={<AdminUsersPage />} />
              <Route path="/admin/document-types" element={<AdminDocumentTypesPage />} />
              <Route path="/admin/requests" element={<AdminMonitoringPage />} />
              <Route path="/admin/requests/:requestId" element={<AdminRequestDetailPage />} />
              <Route path="/admin/reports" element={<AdminReportsPage />} />
            </Route>
          </Route>
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      </AppErrorBoundary>
    </AuthProvider>
  )
}

export default App
