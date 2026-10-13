import { StaffWorkListPage } from '../staff/StaffWorkListPage'

/** Monitoring reuses the tested filter/list presentation, but never staff mutations. */
export function AdminMonitoringPage() {
  return <StaffWorkListPage mode="admin" />
}
