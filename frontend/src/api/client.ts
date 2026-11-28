import type {
  Application, ApplicationSummary, AssessmentOutcome, AuditPage, Company, CompanyDetails, Declaration, DecisionSummary, DuplicateFlag, Enquiry, EnquiryPage, EvidenceType, Me, NewEnquiry,
  Registration, RegistrationList, RegistrationStatus, RegistryResult, RegistrySource, RepresentativeInput, StaffMember,
  VerificationSummary,
} from './types'

const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

/** An error from the API, carrying the RFC 9457 problem `code` the backend sets on every error. */
export class ApiProblem extends Error {
  readonly status: number
  readonly code?: string
  readonly missing?: string[]

  constructor(message: string, status: number, code?: string, missing?: string[]) {
    super(message)
    this.status = status
    this.code = code
    this.missing = missing
  }
}

async function send(path: string, token: string, init: RequestInit = {}): Promise<Response> {
  const isForm = init.body instanceof FormData
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      ...(init.body && !isForm ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null) as
      | { title?: string; detail?: string; code?: string; missing?: string[] }
      | null
    throw new ApiProblem(body?.detail || body?.title || `Request failed (${response.status})`,
      response.status, body?.code, body?.missing)
  }
  return response
}

async function request<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  return (await send(path, token, init)).json() as Promise<T>
}

const reg = (id: string) => `/api/v1/registrations/${encodeURIComponent(id)}`

/**
 * Where a registration's commands go: the staff API, or for a self-service
 * application the applicant's own API (`basePath`), so the same capture dialogs
 * serve both.
 */
const at = (r: { id: string; basePath?: string }) =>
  `${r.basePath ?? '/api/v1/registrations'}/${encodeURIComponent(r.id)}`

function post<T>(path: string, token: string, body: unknown): Promise<T> {
  return request<T>(path, token, { method: 'POST', body: JSON.stringify(body) })
}

export type ListQuery = { statuses: RegistrationStatus[]; search?: string; page?: number; size?: number }

export const APPLICATIONS = '/api/v1/my/applications'

export const api = {
  // Registrations
  listRegistrations: (token: string, query: ListQuery) => {
    const params = new URLSearchParams()
    query.statuses.forEach((status) => params.append('status', status))
    if (query.search?.trim()) params.set('search', query.search.trim())
    params.set('page', String(query.page ?? 0))
    params.set('size', String(query.size ?? 25))
    return request<RegistrationList>(`/api/v1/registrations?${params}`, token)
  },
  startRegistration: (token: string, details: CompanyDetails) =>
    post<Registration>('/api/v1/registrations', token, { channel: 'ONLINE', details }),
  getRegistration: (token: string, id: string) => request<Registration>(reg(id), token),
  updateDetails: (token: string, registration: Registration, details: CompanyDetails) =>
    request<Registration>(`${at(registration)}/details`, token, {
      method: 'PUT',
      body: JSON.stringify({ version: registration.version, details }),
    }),
  submit: (token: string, registration: Registration) =>
    post<{ registration: Registration }>(`${reg(registration.id)}/submission`, token, { version: registration.version }),
  withdraw: (token: string, registration: Registration, reason: string) =>
    post<Registration>(`${reg(registration.id)}/withdrawal`, token,
      { version: registration.version, reason, representativeInstructionConfirmed: true }),
  // Draft capture: representatives, evidence, signatures (AC-R1-CAP-02, -06, AC-R1-ACK-01…04)
  addRepresentative: (token: string, r: Registration, rep: RepresentativeInput) =>
    post<Registration>(`${at(r)}/representatives`, token, { version: r.version, ...rep }),
  updateRepresentative: (token: string, r: Registration, representativeId: string, rep: RepresentativeInput) =>
    request<Registration>(`${at(r)}/representatives/${encodeURIComponent(representativeId)}`, token,
      { method: 'PUT', body: JSON.stringify({ version: r.version, ...rep }) }),
  removeRepresentative: (token: string, r: Registration, representativeId: string) =>
    request<Registration>(`${at(r)}/representatives/${encodeURIComponent(representativeId)}?version=${r.version}`, token,
      { method: 'DELETE' }),
  attachEvidence: (token: string, r: Registration, evidenceType: EvidenceType, file: File) => {
    const form = new FormData()
    form.set('version', String(r.version))
    form.set('evidenceType', evidenceType)
    form.set('file', file)
    return request<Registration>(`${at(r)}/evidence`, token, { method: 'POST', body: form })
  },
  removeEvidence: (token: string, r: Registration, evidenceId: string) =>
    request<Registration>(`${at(r)}/evidence/${encodeURIComponent(evidenceId)}?version=${r.version}`, token,
      { method: 'DELETE' }),
  declaration: (token: string) => request<Declaration>('/api/v1/registrations/declaration', token),
  acknowledge: (token: string, r: Registration, representativeId: string, declarationVersion: string, signature: Blob) => {
    const form = new FormData()
    form.set('version', String(r.version))
    form.set('declarationVersion', declarationVersion)
    form.set('signature', signature, 'signature.png')
    return request<Registration>(`${at(r)}/representatives/${encodeURIComponent(representativeId)}/acknowledgement`, token,
      { method: 'POST', body: form })
  },
  refuse: (token: string, r: Registration, representativeId: string, reason: string) =>
    post<Registration>(`${at(r)}/representatives/${encodeURIComponent(representativeId)}/refusal`, token,
      { version: r.version, reason }),

  duplicateFlags: (token: string, id: string) => request<DuplicateFlag[]>(`${reg(id)}/duplicate-flags`, token),
  audit: (token: string, id: string) => request<AuditPage>(`${reg(id)}/audit-events?limit=100&offset=0`, token),

  /** Downloads an evidence file through the authenticated API and hands it to the browser. */
  downloadEvidence: async (token: string, registrationId: string, evidenceId: string, fileName: string, basePath?: string) => {
    const blob = await (await send(`${at({ id: registrationId, basePath })}/evidence/${encodeURIComponent(evidenceId)}/content`,
      token, { headers: { Accept: '*/*' } })).blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
  },

  // Verification (AC-R1-VER-01…07)
  verification: (token: string, id: string) => request<VerificationSummary>(`${reg(id)}/verification`, token),
  verifierCandidates: (token: string, id: string) => request<StaffMember[]>(`${reg(id)}/verifier-candidates`, token),
  assignVerifier: (token: string, r: Registration, verifierId: string, reason: string) =>
    post<Registration>(`${reg(r.id)}/verification/assignment`, token,
      { version: r.version, verifierId, reason: reason || null }),
  assessEvidence: (token: string, r: Registration, evidenceId: string, outcome: AssessmentOutcome, findings: string) =>
    post<Registration>(`${reg(r.id)}/verification/evidence/${encodeURIComponent(evidenceId)}/assessment`, token,
      { version: r.version, outcome, findings: findings || null }),
  reviewDuplicate: (token: string, r: Registration, flagId: string, outcome: 'NOT_DUPLICATE' | 'DUPLICATE', note: string) =>
    post<Registration>(`${reg(r.id)}/duplicate-flags/${encodeURIComponent(flagId)}/review`, token,
      { version: r.version, outcome, note }),
  requestInformation: (token: string, r: Registration, description: string, dueDate: string) =>
    post<Registration>(`${reg(r.id)}/information-requests`, token, { version: r.version, description, dueDate }),
  attachRequestedEvidence: (token: string, r: Registration, requestId: string, evidenceType: EvidenceType, file: File) => {
    const form = new FormData()
    form.set('version', String(r.version))
    form.set('evidenceType', evidenceType)
    form.set('file', file)
    return request<Registration>(`${reg(r.id)}/information-requests/${encodeURIComponent(requestId)}/evidence`, token,
      { method: 'POST', body: form })
  },
  provideInformation: (token: string, r: Registration, requestId: string, response: string) =>
    post<Registration>(`${reg(r.id)}/information-requests/${encodeURIComponent(requestId)}/response`, token,
      { version: r.version, response }),
  takeVerification: (token: string, r: Registration) =>
    post<Registration>(`${reg(r.id)}/verification/take`, token, { version: r.version }),
  recordRegistryCheck: (token: string, r: Registration, source: RegistrySource, reference: string, result: RegistryResult, note: string) =>
    post<Registration>(`${reg(r.id)}/verification/registry-checks`, token,
      { version: r.version, source, reference, result, note: note || null }),

  // Decision by the checker (AC-R1-DEC-03…05)
  decision: (token: string, id: string) => request<DecisionSummary>(`${reg(id)}/decision`, token),
  approve: (token: string, r: Registration, rationale: string) =>
    post<{ registration: Registration; company: Company }>(`${reg(r.id)}/decision/approval`, token,
      { version: r.version, rationale }),
  reject: (token: string, r: Registration, reasons: string) =>
    post<Registration>(`${reg(r.id)}/decision/rejection`, token, { version: r.version, reasons }),

  getCompany: (token: string, companyId: string) =>
    request<Company>(`/api/v1/companies/${encodeURIComponent(companyId)}`, token),

  // Website enquiries
  /** Sent by visitors who are not signed in, so no token is attached. */
  submitEnquiry: async (enquiry: NewEnquiry) => {
    const response = await fetch(`${baseUrl}/api/v1/public/enquiries`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(enquiry),
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null) as { detail?: string; title?: string; code?: string } | null
      throw new ApiProblem(body?.detail || body?.title || `Request failed (${response.status})`, response.status, body?.code)
    }
    return response.json() as Promise<{ reference: string | null }>
  },
  enquiries: (token: string, status: 'NEW' | 'RESOLVED' | null) =>
    request<EnquiryPage>(`/api/v1/enquiries?limit=200${status ? `&status=${status}` : ''}`, token),
  resolveEnquiry: (token: string, id: string) =>
    post<Enquiry>(`/api/v1/enquiries/${encodeURIComponent(id)}/resolution`, token, {}),

  // Self-service applications (Release 1 §9): the applicant's own, nothing else.
  myApplications: (token: string) => request<ApplicationSummary[]>(APPLICATIONS, token),
  startApplication: (token: string) => post<Application>(APPLICATIONS, token, {}),
  getApplication: (token: string, id: string) => request<Application>(`${APPLICATIONS}/${encodeURIComponent(id)}`, token),
  submitApplication: (token: string, a: Application) =>
    post<Application>(`${APPLICATIONS}/${encodeURIComponent(a.id)}/submission`, token, { version: a.version }),
  takeBackApplication: (token: string, a: Application) =>
    post<Application>(`${APPLICATIONS}/${encodeURIComponent(a.id)}/take-back`, token, { version: a.version }),
  withdrawApplication: (token: string, a: Application, reason: string) =>
    post<Application>(`${APPLICATIONS}/${encodeURIComponent(a.id)}/withdrawal`, token, { version: a.version, reason }),

  // Staff
  me: (token: string) => request<Me>('/api/v1/me', token),
  /** Self-service sign-up: makes a newly signed-up person a TransitHub user (no roles). Safe to repeat. */
  createAccount: (token: string) => post<{ userId: string; created: boolean }>('/api/v1/me/account', token, {}),
  staffNames: (token: string, ids: string[]) => {
    const params = new URLSearchParams()
    ids.forEach((id) => params.append('ids', id))
    return request<Record<string, string>>(`/api/v1/staff/names?${params}`, token)
  },
}
