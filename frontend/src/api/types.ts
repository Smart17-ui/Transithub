export type CompanyDetails = {
  legalName: string | null
  tradingName: string | null
  registrationNumber: string | null
  /** RTSA Road Service Licence number. */
  operatingLicenceNumber: string | null
  businessType: string | null
  province: string | null
  district: string | null
  physicalAddress: string | null
  postalAddress: string | null
  contactEmail: string | null
  contactPhone: string | null
}

export type RegistrationStatus =
  | 'DRAFT' | 'SUBMITTED' | 'UNDER_VERIFICATION' | 'AWAITING_INFORMATION'
  | 'RECOMMENDED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'

export type Acknowledgement = {
  outcome: string
  declarationVersion: string | null
  contentRevision: number
  refusalReason: string | null
  recordedBy: string
  recordedAt: string
}

export type Representative = {
  id: string
  position: number
  fullName: string
  idDocumentType: string | null
  idDocumentNumber: string | null
  capacity: string | null
  phone: string | null
  email: string | null
  acknowledgementRequired: boolean
  primaryContact: boolean
  acknowledgement: Acknowledgement | null
}

export type IdDocumentType = 'NRC' | 'PASSPORT' | 'OTHER'

/** What the officer enters for a representative; the server adds ids and signatures. */
export type RepresentativeInput = {
  fullName: string
  idDocumentType: IdDocumentType | null
  idDocumentNumber: string | null
  capacity: string | null
  phone: string | null
  email: string | null
  acknowledgementRequired: boolean
  primaryContact: boolean
}

export type Declaration = { version: string; text: string }

export type EvidenceType =
  | 'CERTIFICATE_OF_INCORPORATION' | 'TAX_CLEARANCE' | 'OPERATING_LICENCE'
  | 'REPRESENTATIVE_IDENTITY' | 'PROOF_OF_ADDRESS' | 'OTHER'

export type Evidence = {
  id: string
  evidenceType: EvidenceType
  fileName: string
  contentType: string
  sizeBytes: number
  sha256: string
  uploadedBy: string
  uploadedAt: string
}

export type Registration = {
  id: string
  reference: string
  status: RegistrationStatus
  channel: string
  capturedBy: string
  contentRevision: number
  version: number
  details: CompanyDetails
  representatives: Representative[]
  evidence: Evidence[]
  missingForSubmission: string[]
  allowedActions: string[]
  createdAt: string
  updatedAt: string
  /** Set by the app for a self-service application: its commands go to the applicant's own API. */
  basePath?: string
}

/** A self-service application as its applicant sees it (Release 1 §9, SS-7). */
export type Application = Omit<Registration, 'channel' | 'capturedBy' | 'contentRevision'> & {
  timeline: { status: RegistrationStatus; at: string; note: string | null }[]
  appealDeadline: string | null
}

export type ApplicationSummary = {
  id: string
  reference: string
  status: RegistrationStatus
  legalName: string | null
  createdAt: string
  updatedAt: string
}

export type RegistrationListItem = {
  id: string
  reference: string
  status: RegistrationStatus
  legalName: string | null
  district: string | null
  capturedBy: string
  capturedByName: string | null
  assignedVerifier: string | null
  assignedVerifierName: string | null
  updatedAt: string
}

export type RegistrationList = { items: RegistrationListItem[]; total: number; page: number; size: number }

export type StaffMember = { id: string; displayName: string }

export type VerificationAssignment = {
  id: string
  assignee: string
  assignedBy: string
  reason: string | null
  assignedAt: string
  endedAt: string | null
  endReason: string | null
}

export type AssessmentOutcome = 'ACCEPTED' | 'INSUFFICIENT' | 'INVALID'

export type Assessment = {
  id: string
  evidenceId: string
  outcome: AssessmentOutcome
  findings: string | null
  assessedBy: string
  assessedAt: string
}

export type InformationRequest = {
  id: string
  description: string
  dueDate: string
  requestedBy: string
  requestedAt: string
  status: 'OPEN' | 'ANSWERED' | string
  response: string | null
  respondedBy: string | null
  respondedAt: string | null
}

export type Recommendation = {
  id: string
  recommendation: 'APPROVE' | 'REJECT'
  reasons: string
  recommendedBy: string
  recommendedAt: string
  supersededAt: string | null
}

export type VerificationSummary = {
  assignedVerifier: string | null
  assignments: VerificationAssignment[]
  assessments: Assessment[]
  informationRequests: InformationRequest[]
  recommendations: Recommendation[]
  unreviewedDuplicateFlags: string[]
  registryChecks: RegistryCheck[]
  /** What still blocks approval; empty when the checker may approve. */
  outstandingForApproval: string[]
}

export type RegistrySource = 'PACRA' | 'RTSA' | 'ZRA' | 'OTHER'
export type RegistryResult = 'MATCH' | 'MISMATCH' | 'NOT_FOUND' | 'COULD_NOT_CHECK'

export type RegistryCheck = {
  id: string
  source: RegistrySource
  reference: string
  result: RegistryResult
  note: string | null
  checkedBy: string
  checkedAt: string
}

export type DuplicateFlag = {
  id: string
  matchKind: string
  matchedId: string
  matchedReference: string
  matchReason: string
  flaggedAt: string
}

export type RecommendationReview = {
  id: string
  recommendationId: string
  outcome: 'ENDORSE' | 'CHALLENGE'
  comments: string | null
  reviewedBy: string
  reviewedAt: string
}

export type Decision = {
  id: string
  outcome: 'APPROVED' | 'REJECTED' | string
  reasons: string
  decidedBy: string
  decidedAt: string
  appealDeadline: string | null
  companyId: string | null
}

export type DecisionSummary = { reviews: RecommendationReview[]; decision: Decision | null }

export type Company = {
  id: string
  companyCode: string
  legalName: string
  operatingLicenceNumber: string | null
  operationalStatus: string
  registrationId: string
}

export type AuditEvent = {
  id: string
  occurredAt: string
  actorId: string
  action: string
  entityType: string
  entityId: string
  companyId: string | null
  reason: string | null
  details: unknown
  correlationId: string | null
}

export type AuditPage = { events: AuditEvent[]; total: number; limit: number; offset: number }

/** The signed-in user (GET /api/v1/me): used to shape screens; the server still checks every command. */
export type Me = {
  registered: boolean
  userId: string | null
  displayName: string | null
  active: boolean
  roles: string[]
  permissions: string[]
  /** Set by the app, not the server: signed up, but the email address is not confirmed yet. */
  emailNotConfirmed?: boolean
}

export type EnquiryTopic = 'COMPANY_REGISTRATION' | 'TRAVEL' | 'PARTNERSHIP' | 'OTHER'

/** What a visitor sends from the Contact page. `website` is a hidden anti-spam field; people leave it empty. */
export type NewEnquiry = {
  topic: EnquiryTopic
  fullName: string
  email: string
  phone: string | null
  companyName: string | null
  message: string
  consentGiven: boolean
  website: string
}

export type Enquiry = {
  id: string
  reference: string
  topic: EnquiryTopic
  fullName: string
  email: string
  phone: string | null
  companyName: string | null
  message: string
  status: 'NEW' | 'RESOLVED'
  receivedAt: string
  resolvedAt: string | null
  resolvedBy: string | null
}

export type EnquiryPage = { items: Enquiry[]; total: number; limit: number; offset: number }
