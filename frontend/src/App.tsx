import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { AccountPage } from './auth/AccountPage'
import { LoginPage } from './auth/LoginPage'
import { RegisterPage } from './auth/RegisterPage'
import { DashboardRedirect, ProtectedRoute, PublicOnlyRoute, RoleRoute } from './auth/guards'
import { AppLayout } from './layout/AppLayout'
import { PublicLayout } from './layout/PublicLayout'
import { NotFoundPage } from './pages/NotFoundPage'
import { PlannedFeaturePage } from './pages/PlannedFeaturePage'
import { ProfilePage } from './profile/ProfilePage'
import { NewRequestProfileGate } from './profile/NewRequestProfileGate'
import { StudentRequestsLanding } from './profile/StudentRequestsLanding'
import './styles.css'

function App() {
  return (
    <AuthProvider>
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
            <Route path="/catalogue" element={<PlannedFeaturePage eyebrow="Document services" title="Document catalogue" description="Browse available university documents and understand the requirements for each one." issue={8} />} />
            <Route path="/notifications" element={<PlannedFeaturePage eyebrow="Your updates" title="Notifications" description="Stay informed about changes to your document requests." issue={9} />} />

            <Route element={<RoleRoute allowedRoles={['STUDENT']} />}>
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/requests" element={<StudentRequestsLanding />} />
              <Route path="/requests/new" element={<NewRequestProfileGate />} />
              <Route path="/requests/:requestId" element={<PlannedFeaturePage eyebrow="Student workspace" title="Request details" description="Review your request's current status, history and attached documents." issue={12} />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={['STAFF']} />}>
              <Route path="/staff/requests" element={<PlannedFeaturePage eyebrow="Staff workspace" title="Request work list" description="Review student document requests and manage their progress." issue={13} />} />
              <Route path="/staff/requests/:requestId" element={<PlannedFeaturePage eyebrow="Staff workspace" title="Review request" description="Inspect a request, supporting documents and processing actions." issue={13} />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={['ADMIN']} />}>
              <Route path="/admin/dashboard" element={<PlannedFeaturePage eyebrow="Administration" title="Overview" description="A clear view of request activity and document-delivery progress." issue={17} />} />
              <Route path="/admin/users" element={<PlannedFeaturePage eyebrow="Administration" title="Account management" description="Create and manage staff and administrator accounts and review account status." issue={7} />} />
              <Route path="/admin/document-types" element={<PlannedFeaturePage eyebrow="Administration" title="Document types" description="Manage the documents and requirements available to students." issue={8} />} />
              <Route path="/admin/requests" element={<PlannedFeaturePage eyebrow="Administration" title="Request monitoring" description="Monitor document requests without changing staff workflow states." issue={17} />} />
              <Route path="/admin/requests/:requestId" element={<PlannedFeaturePage eyebrow="Administration" title="Request monitoring detail" description="Review request and student information in a read-only view." issue={17} />} />
              <Route path="/admin/reports" element={<PlannedFeaturePage eyebrow="Administration" title="Reports" description="Filter request records and export a report as CSV." issue={17} />} />
            </Route>
          </Route>
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AuthProvider>
  )
}

export default App
