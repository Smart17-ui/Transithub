import type { AuditPage, CompanyDetails, Registration } from './types'

const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

async function request<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null) as
      | { title?: string; detail?: string; code?: string; missing?: string[] }
      | null
    const error = new Error(body?.detail || body?.title || `Request failed (${response.status})`)
    Object.assign(error, { status: response.status, code: body?.code, missing: body?.missing })
    throw error
  }
  return response.json() as Promise<T>
}

export const api = {
  startRegistration: (token: string, details: CompanyDetails) =>
    request<Registration>('/api/v1/registrations', token, {
      method: 'POST',
      body: JSON.stringify({ channel: 'ONLINE', details }),
    }),
  getRegistration: (token: string, id: string) =>
    request<Registration>(`/api/v1/registrations/${encodeURIComponent(id)}`, token),
  updateDetails: (token: string, registration: Registration, details: CompanyDetails) =>
    request<Registration>(`/api/v1/registrations/${encodeURIComponent(registration.id)}/details`, token, {
      method: 'PUT',
      body: JSON.stringify({ version: registration.version, details }),
    }),
  submit: (token: string, registration: Registration) =>
    request<{ registration: Registration }>(
      `/api/v1/registrations/${encodeURIComponent(registration.id)}/submission`, token,
      { method: 'POST', body: JSON.stringify({ version: registration.version }) },
    ),
  withdraw: (token: string, registration: Registration, reason: string) =>
    request<Registration>(
      `/api/v1/registrations/${encodeURIComponent(registration.id)}/withdrawal`, token,
      {
        method: 'POST',
        body: JSON.stringify({ version: registration.version, reason, representativeInstructionConfirmed: true }),
      },
    ),
  audit: (token: string, id: string) =>
    request<AuditPage>(`/api/v1/registrations/${encodeURIComponent(id)}/audit-events?limit=100&offset=0`, token),
}
