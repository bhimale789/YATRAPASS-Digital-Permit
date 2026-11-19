// Mirrors the backend DTOs (see /v3/api-docs).

export type Role = 'TOURIST' | 'ADMIN' | 'OFFICER';

export interface UserInfo {
  id: number;
  email: string;
  role: Role;
  /** Client-side flag mirrored from the last login/refresh response. */
  passwordChangeRequired?: boolean;
}

export interface LoginResponse {
  accessToken: string | null;
  refreshToken: string | null;
  tokenType: string | null;
  expiresIn: number | null;
  user: UserInfo;
  /** Second step needed: send the authenticator code with mfaToken to /auth/login/mfa. */
  mfaRequired: boolean;
  mfaToken: string | null;
  passwordChangeRequired: boolean;
}

export interface Account {
  id: number;
  email: string;
  role: Role;
  displayName: string | null;
  mfaEnabled: boolean;
  recoveryCodesRemaining: number;
  passwordChangeRequired: boolean;
  lastLoginAt: string | null;
}

export interface MfaSetup {
  secret: string;
  otpauthUri: string;
  qrPngDataUrl: string;
}

export interface StaffMember {
  id: number;
  email: string;
  displayName: string | null;
  role: Role;
  enabled: boolean;
  mfaEnabled: boolean;
  passwordChangeRequired: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface StaffCredentials {
  staff: StaffMember;
  temporaryPassword: string;
}

export type FeeStatus = 'NOT_REQUIRED' | 'UNPAID' | 'PENDING' | 'PAID';
export type PaymentProvider = 'ESEWA' | 'OFFLINE';
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface PaymentSummary {
  status: FeeStatus;
  amount: number;
  currency: string;
  provider: PaymentProvider | null;
  reference: string | null;
  paidAt: string | null;
  /** True when approval waits for the fee (online payments enabled). */
  requiredForApproval: boolean;
}

export interface Payment {
  id: number;
  transactionUuid: string;
  provider: PaymentProvider;
  amount: number;
  currency: string;
  status: PaymentStatus;
  reference: string | null;
  failureReason: string | null;
  createdAt: string;
  completedAt: string | null;
  applicationNumber?: string | null;
  applicationId?: number | null;
  touristName?: string | null;
}

export interface EsewaCheckout {
  formUrl: string;
  fields: Record<string, string>;
  transactionUuid: string;
}

export type NotificationType = 'APPLICATION_APPROVED' | 'APPLICATION_REJECTED' | 'PERMIT_EXPIRING' | 'PAYMENT_RECEIVED';

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  body: string;
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'UNDISCLOSED';

export interface TouristProfile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  passportNumber: string | null;
  nationality: string | null;
  dateOfBirth: string | null;
  gender: Gender | null;
  phone: string | null;
  address: string | null;
  emergencyContact: string | null;
  complete: boolean;
}

export interface TouristSummary {
  id: number;
  fullName: string;
  email: string;
  nationality: string | null;
  passportNumberMasked: string | null;
  enabled: boolean;
  profileComplete: boolean;
  registeredAt: string;
}

export interface AdminTouristDetail {
  id: number;
  userId: number;
  email: string;
  enabled: boolean;
  firstName: string;
  lastName: string;
  passportNumber: string | null;
  nationality: string | null;
  dateOfBirth: string | null;
  gender: Gender | null;
  phone: string | null;
  address: string | null;
  emergencyContact: string | null;
  profileComplete: boolean;
  applicationCount: number;
  permitCount: number;
  registeredAt: string;
  updatedAt: string;
}

export interface Area {
  id: number;
  name: string;
  code: string;
  description: string | null;
  district: string | null;
  province: string | null;
  permitRequired: boolean;
  maximumDays: number;
  fee: number;
  active: boolean;
  /** Versioned photo URL; absent when no photo was uploaded. */
  photoUrl?: string;
  /** Destination pin (route end point). */
  latitude?: number | null;
  longitude?: number | null;
}

export interface AreaSummary {
  id: number;
  name: string;
  code: string;
  photoUrl?: string;
}

export type ApplicationStatus = 'PENDING' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export type DocumentType = 'PASSPORT' | 'VISA' | 'PHOTO' | 'TRAVEL_INSURANCE' | 'ITINERARY' | 'OTHER';

export interface PermitDocument {
  id: number;
  documentType: DocumentType;
  fileName: string;
  mimeType: string;
  fileSize: number;
  uploadedAt: string;
  verified: boolean;
  verifiedAt: string | null;
}

export interface Application {
  id: number;
  applicationNumber: string;
  area: AreaSummary;
  purpose: string;
  startDate: string;
  endDate: string;
  requestedDays: number;
  status: ApplicationStatus;
  rejectionReason?: string;
  submittedAt: string;
  reviewedAt?: string;
  documents?: PermitDocument[];
  tourist?: TouristSummary;
  permit?: { id: number; permitNumber: string };
  feeAmount?: number;
  payment?: PaymentSummary;
}

export type PermitStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'COMPLETED';

export interface Permit {
  id: number;
  permitNumber: string;
  applicationId: number;
  applicationNumber: string;
  area: AreaSummary;
  issueDate: string;
  validFrom: string;
  validUntil: string;
  status: PermitStatus;
  cancellationReason?: string;
  qrActive: boolean;
  tourist?: TouristSummary;
}

export type VerificationReason =
  | 'INVALID_TOKEN'
  | 'TOKEN_REVOKED'
  | 'PERMIT_NOT_FOUND'
  | 'PERMIT_CANCELLED'
  | 'PERMIT_EXPIRED'
  | 'PERMIT_NOT_ACTIVE'
  | 'PERMIT_NOT_YET_VALID'
  | 'AREA_MISMATCH';

export interface VerificationResponse {
  valid: boolean;
  reason?: VerificationReason;
  message?: string;
  status?: PermitStatus;
  permitNumber?: string;
  touristName?: string;
  nationality?: string;
  area?: string;
  areaCode?: string;
  validFrom?: string;
  validUntil?: string;
  verifiedAt: string;
}

export type VerificationResult = 'VALID' | 'INVALID' | 'EXPIRED' | 'CANCELLED';

export interface VerificationLog {
  id: number;
  verifiedAt: string;
  result: VerificationResult;
  failureReason?: VerificationReason;
  permitId?: number;
  permitNumber?: string;
  area?: string;
  verifiedById: number;
  verifiedByEmail: string;
  ipAddress?: string;
  userAgent?: string;
  latitude?: number;
  longitude?: number;
}

export interface DashboardStats {
  totalTourists: number;
  pendingApplications: number;
  underReviewApplications: number;
  activePermits: number;
  expiredPermits: number;
  cancelledPermits: number;
  completedPermits: number;
  verificationsToday: number;
  validScansToday: number;
  invalidScansToday: number;
}

export interface AreaPermitStats {
  areaId: number;
  areaName: string;
  areaCode: string;
  totalPermits: number;
  activePermits: number;
}

export interface Dashboard {
  stats: DashboardStats;
  recentApplications: Application[];
  recentVerifications: VerificationLog[];
  expiringPermits: Permit[];
  permitsByArea: AreaPermitStats[];
  fees: {
    collectedTotal: number;
    collectedThisMonth: number;
    unpaidOpenApplications: number;
    onlinePaymentsEnabled: boolean;
  };
}

export type RoadStatus = 'OPEN' | 'CAUTION' | 'PARTIALLY_BLOCKED' | 'CLOSED';
export type GeometryType = 'POINT' | 'LINE';

export interface Coordinate {
  lat: number;
  lng: number;
}

export interface RoadCondition {
  id: number;
  title: string;
  description?: string;
  status: RoadStatus;
  geometryType: GeometryType;
  coordinates: Coordinate[];
  area?: AreaSummary;
  active: boolean;
  updatedAt: string;
  /** Admin views only. */
  updatedBy?: string;
}

export interface RoutePlace {
  label: string;
  lat: number;
  lng: number;
}

export interface DrivingRoute {
  from: RoutePlace;
  to: RoutePlace;
  distanceMeters: number;
  durationSeconds: number;
  coordinates: Coordinate[];
  attribution: string;
}

export interface OfficerDashboard {
  stats: {
    scansToday: number;
    validToday: number;
    invalidToday: number;
    totalScans: number;
    permitsValidToday: number;
  };
  recentScans: {
    id: number;
    verifiedAt: string;
    result: VerificationResult;
    failureReason?: VerificationReason;
    permitNumber?: string;
    area?: string;
  }[];
}

export interface AuditLogEntry {
  id: number;
  createdAt: string;
  action: string;
  actorId?: number;
  actorRole?: string;
  entityType?: string;
  entityId?: string;
  ipAddress?: string;
  metadata?: Record<string, unknown>;
}
