import { api } from './client';
import type {
  Account,
  AppNotification,
  EsewaCheckout,
  MfaSetup,
  Payment,
  PaymentProvider,
  PaymentStatus,
  Role,
  StaffCredentials,
  StaffMember,
  AdminTouristDetail,
  Application,
  ApplicationStatus,
  Area,
  AuditLogEntry,
  Coordinate,
  GeometryType,
  RoadCondition,
  RoadStatus,
  Dashboard,
  DrivingRoute,
  DocumentType,
  LoginResponse,
  OfficerDashboard,
  Page,
  Permit,
  PermitDocument,
  PermitStatus,
  TouristProfile,
  TouristSummary,
  VerificationLog,
  VerificationResponse,
  VerificationResult,
} from '../types';

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
  q?: string;
}

const clean = <T extends object>(params: T) =>
  Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== '' && v !== null));

// ---------------------------------------------------------------- auth
export const authApi = {
  register: (body: { email: string; password: string; firstName: string; lastName: string }) =>
    api.post<LoginResponse>('/auth/register', body).then((r) => r.data),
  login: (body: { email: string; password: string }) =>
    api.post<LoginResponse>('/auth/login', body).then((r) => r.data),
  loginMfa: (body: { mfaToken: string; code: string }) =>
    api.post<LoginResponse>('/auth/login/mfa', body).then((r) => r.data),
  logout: (refreshToken: string) => api.post('/auth/logout', { refreshToken }),
};

// ---------------------------------------------------------------- public
export const areaApi = {
  listActive: () => api.get<Area[]>('/areas').then((r) => r.data),
  /** Driving route to an area, from the given point or the default start (Pokhara). */
  route: (id: number, from?: Coordinate | null) =>
    api
      .get<DrivingRoute>(`/areas/${id}/route`, { params: from ? { fromLat: from.lat, fromLng: from.lng } : {} })
      .then((r) => r.data),
};

// ---------------------------------------------------------------- own account (any role)
export const accountApi = {
  me: () => api.get<Account>('/account').then((r) => r.data),
  /** Returns a fresh session (all other sessions are signed out). */
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    api.post<LoginResponse>('/account/password', body).then((r) => r.data),
  startMfaSetup: () => api.post<MfaSetup>('/account/mfa/setup').then((r) => r.data),
  enableMfa: (code: string) =>
    api.post<{ recoveryCodes: string[] }>('/account/mfa/enable', { code }).then((r) => r.data.recoveryCodes),
  disableMfa: (code: string) => api.post('/account/mfa/disable', { code }),
  regenerateRecoveryCodes: (code: string) =>
    api.post<{ recoveryCodes: string[] }>('/account/mfa/recovery-codes', { code }).then((r) => r.data.recoveryCodes),
};

// ---------------------------------------------------------------- payments (tourist)
export const paymentApi = {
  startEsewa: (applicationId: number) =>
    api.post<EsewaCheckout>(`/applications/${applicationId}/payments/esewa`).then((r) => r.data),
  forApplication: (applicationId: number) =>
    api.get<Payment[]>(`/applications/${applicationId}/payments`).then((r) => r.data),
  confirmEsewa: (data: string) => api.post<Payment>('/payments/esewa/confirm', { data }).then((r) => r.data),
  check: (transactionUuid: string) =>
    api.post<Payment>(`/payments/${encodeURIComponent(transactionUuid)}/check`).then((r) => r.data),
};

// ---------------------------------------------------------------- notifications (tourist)
export const notificationApi = {
  list: (params: PageQuery = {}) =>
    api.get<Page<AppNotification>>('/notifications', { params: clean(params) }).then((r) => r.data),
  unreadCount: () => api.get<{ count: number }>('/notifications/unread-count').then((r) => r.data.count),
  markRead: (id: number) => api.post<AppNotification>(`/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => api.post('/notifications/read-all'),
};

// ---------------------------------------------------------------- tourist
export type ProfileUpdate = Omit<TouristProfile, 'id' | 'email' | 'complete'>;

export const touristApi = {
  profile: () => api.get<TouristProfile>('/tourists/me').then((r) => r.data),
  updateProfile: (body: ProfileUpdate) => api.put<TouristProfile>('/tourists/me', body).then((r) => r.data),

  applications: (params: PageQuery & { status?: ApplicationStatus }) =>
    api.get<Page<Application>>('/applications', { params: clean(params) }).then((r) => r.data),
  application: (id: number) => api.get<Application>(`/applications/${id}`).then((r) => r.data),
  submitApplication: (body: { areaId: number; purpose: string; startDate: string; endDate: string }) =>
    api.post<Application>('/applications', body).then((r) => r.data),
  cancelApplication: (id: number) => api.post<Application>(`/applications/${id}/cancel`).then((r) => r.data),
  uploadDocument: (id: number, documentType: DocumentType, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post<PermitDocument>(`/applications/${id}/documents`, form, { params: { documentType } })
      .then((r) => r.data);
  },
  deleteDocument: (id: number, documentId: number) => api.delete(`/applications/${id}/documents/${documentId}`),
  documentBlob: (id: number, documentId: number) =>
    api.get<Blob>(`/applications/${id}/documents/${documentId}/content`, { responseType: 'blob' }).then((r) => r.data),

  permits: (params: PageQuery & { status?: PermitStatus }) =>
    api.get<Page<Permit>>('/permits', { params: clean(params) }).then((r) => r.data),
  permit: (id: number) => api.get<Permit>(`/permits/${id}`).then((r) => r.data),
  permitQrBlob: (id: number) => api.get<Blob>(`/permits/${id}/qr`, { responseType: 'blob' }).then((r) => r.data),
  permitPdfBlob: (id: number) => api.get<Blob>(`/permits/${id}/pdf`, { responseType: 'blob' }).then((r) => r.data),
};

// ---------------------------------------------------------------- admin
export type AreaInput = Omit<Area, 'id'>;

export const adminApi = {
  dashboard: () => api.get<Dashboard>('/admin/dashboard').then((r) => r.data),

  tourists: (params: PageQuery) =>
    api.get<Page<TouristSummary>>('/admin/tourists', { params: clean(params) }).then((r) => r.data),
  tourist: (id: number) => api.get<AdminTouristDetail>(`/admin/tourists/${id}`).then((r) => r.data),
  setTouristEnabled: (id: number, enabled: boolean) =>
    api.patch<AdminTouristDetail>(`/admin/tourists/${id}/status`, { enabled }).then((r) => r.data),

  applications: (params: PageQuery & { status?: ApplicationStatus }) =>
    api.get<Page<Application>>('/admin/applications', { params: clean(params) }).then((r) => r.data),
  application: (id: number) => api.get<Application>(`/admin/applications/${id}`).then((r) => r.data),
  startReview: (id: number) => api.post<Application>(`/admin/applications/${id}/review`).then((r) => r.data),
  approve: (id: number) => api.post<Permit>(`/admin/applications/${id}/approve`).then((r) => r.data),
  reject: (id: number, reason: string) =>
    api.post<Application>(`/admin/applications/${id}/reject`, { reason }).then((r) => r.data),
  documentBlob: (documentId: number) =>
    api.get<Blob>(`/admin/documents/${documentId}/content`, { responseType: 'blob' }).then((r) => r.data),
  verifyDocument: (documentId: number) =>
    api.post<PermitDocument>(`/admin/documents/${documentId}/verify`).then((r) => r.data),

  permits: (params: PageQuery & { status?: PermitStatus }) =>
    api.get<Page<Permit>>('/admin/permits', { params: clean(params) }).then((r) => r.data),
  permit: (id: number) => api.get<Permit>(`/admin/permits/${id}`).then((r) => r.data),
  expiringPermits: (days: number) =>
    api.get<Page<Permit>>('/admin/permits/expiring', { params: { days } }).then((r) => r.data),
  cancelPermit: (id: number, reason: string) =>
    api.post<Permit>(`/admin/permits/${id}/cancel`, { reason }).then((r) => r.data),
  completePermit: (id: number) => api.post<Permit>(`/admin/permits/${id}/complete`).then((r) => r.data),
  permitQrBlob: (id: number) => api.get<Blob>(`/admin/permits/${id}/qr`, { responseType: 'blob' }).then((r) => r.data),
  permitPdfBlob: (id: number) =>
    api.get<Blob>(`/admin/permits/${id}/pdf`, { responseType: 'blob' }).then((r) => r.data),
  regenerateQr: (id: number, reason: string) =>
    api.post<Permit>(`/admin/permits/${id}/qr/regenerate`, { reason }).then((r) => r.data),
  revokeQr: (id: number, reason: string) =>
    api.post<Permit>(`/admin/permits/${id}/qr/revoke`, { reason }).then((r) => r.data),

  areas: (params: PageQuery & { active?: boolean }) =>
    api.get<Page<Area>>('/admin/areas', { params: clean(params) }).then((r) => r.data),
  createArea: (body: AreaInput) => api.post<Area>('/admin/areas', body).then((r) => r.data),
  updateArea: (id: number, body: AreaInput) => api.put<Area>(`/admin/areas/${id}`, body).then((r) => r.data),
  uploadAreaPhoto: (id: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api.put<Area>(`/admin/areas/${id}/photo`, form).then((r) => r.data);
  },
  removeAreaPhoto: (id: number) => api.delete<Area>(`/admin/areas/${id}/photo`).then((r) => r.data),
  setAreaActive: (id: number, active: boolean) =>
    api.patch<Area>(`/admin/areas/${id}/status`, { active }).then((r) => r.data),

  payments: (params: PageQuery & { status?: PaymentStatus; provider?: PaymentProvider }) =>
    api.get<Page<Payment>>('/admin/payments', { params: clean(params) }).then((r) => r.data),
  applicationPayments: (id: number) =>
    api.get<Payment[]>(`/admin/applications/${id}/payments`).then((r) => r.data),
  recordOfflinePayment: (id: number, reference: string) =>
    api.post<Payment>(`/admin/applications/${id}/payments/offline`, { reference }).then((r) => r.data),

  staff: (params: PageQuery & { role?: Role }) =>
    api.get<Page<StaffMember>>('/admin/staff', { params: clean(params) }).then((r) => r.data),
  createStaff: (body: { email: string; displayName: string; role: Role }) =>
    api.post<StaffCredentials>('/admin/staff', body).then((r) => r.data),
  updateStaff: (id: number, body: { displayName: string; role: Role }) =>
    api.put<StaffMember>(`/admin/staff/${id}`, body).then((r) => r.data),
  setStaffEnabled: (id: number, enabled: boolean) =>
    api.patch<StaffMember>(`/admin/staff/${id}/status`, { enabled }).then((r) => r.data),
  resetStaffPassword: (id: number) =>
    api.post<StaffCredentials>(`/admin/staff/${id}/reset-password`).then((r) => r.data),
  resetStaffMfa: (id: number) => api.post<StaffMember>(`/admin/staff/${id}/reset-mfa`).then((r) => r.data),

  verifications: (params: PageQuery & { result?: VerificationResult; permitId?: number; from?: string; to?: string }) =>
    api.get<Page<VerificationLog>>('/verifications', { params: clean(params) }).then((r) => r.data),
  auditLogs: (params: PageQuery & { action?: string; entityType?: string; entityId?: string }) =>
    api.get<Page<AuditLogEntry>>('/admin/audit-logs', { params: clean(params) }).then((r) => r.data),
};

// ---------------------------------------------------------------- road conditions
export type RoadConditionInput = {
  title: string;
  description?: string;
  status: RoadStatus;
  geometryType: GeometryType;
  coordinates: Coordinate[];
  areaId?: number | null;
  active: boolean;
};

export const roadApi = {
  /** Active conditions for maps (any signed-in user). */
  active: (areaId?: number) =>
    api.get<RoadCondition[]>('/road-conditions', { params: clean({ areaId }) }).then((r) => r.data),
  adminSearch: (params: PageQuery & { status?: RoadStatus; areaId?: number; active?: boolean }) =>
    api.get<Page<RoadCondition>>('/admin/road-conditions', { params: clean(params) }).then((r) => r.data),
  create: (body: RoadConditionInput) => api.post<RoadCondition>('/admin/road-conditions', body).then((r) => r.data),
  update: (id: number, body: RoadConditionInput) =>
    api.put<RoadCondition>(`/admin/road-conditions/${id}`, body).then((r) => r.data),
  remove: (id: number) => api.delete(`/admin/road-conditions/${id}`),
};

// ---------------------------------------------------------------- officer
export const officerApi = {
  dashboard: () => api.get<OfficerDashboard>('/officer/dashboard').then((r) => r.data),
};

// ---------------------------------------------------------------- scanner (ADMIN / OFFICER)
export const verificationApi = {
  verify: (token: string, opts: { areaId?: number; latitude?: number; longitude?: number } = {}) =>
    api
      .get<VerificationResponse>(`/permits/verify/${encodeURIComponent(token)}`, { params: clean(opts) })
      .then((r) => r.data),
};
