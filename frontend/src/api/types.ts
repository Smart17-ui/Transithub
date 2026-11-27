export type CompanyDetails = {
  legalName: string | null
  tradingName: string | null
  registrationNumber: string | null
  businessType: string | null
  province: string | null
  district: string | null
  physicalAddress: string | null
  postalAddress: string | null
  contactEmail: string | null
  contactPhone: string | null
}

export type Registration = {
  id: string
  reference: string
  status: string
  channel: string
  capturedBy: string
  contentRevision: number
  version: number
  details: CompanyDetails
  representatives: Array<{ id: string; fullName: string; position: number }>
  evidence: Array<{ id: string; fileName: string; evidenceType: string }>
  missingForSubmission: string[]
  allowedActions: string[]
  createdAt: string
  updatedAt: string
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

