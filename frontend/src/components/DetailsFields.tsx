import type { ChangeEvent } from 'react'
import { SimpleGrid, TextInput, Textarea } from '@mantine/core'
import type { CompanyDetails } from '../api/types'

export const emptyDetails: CompanyDetails = {
  legalName: '', tradingName: '', registrationNumber: '', operatingLicenceNumber: '', businessType: '', province: '', district: '',
  physicalAddress: '', postalAddress: '', contactEmail: '', contactPhone: '',
}

/** Blank fields are sent as null, so "cleared" and "never filled" look the same to the server. */
export function normalizeDetails(details: CompanyDetails): CompanyDetails {
  return Object.fromEntries(Object.entries(details).map(([key, value]) => [key, value?.trim() || null])) as CompanyDetails
}

export function DetailsFields({ value, onChange, disabled = false }: {
  value: CompanyDetails; onChange: (value: CompanyDetails) => void; disabled?: boolean
}) {
  const set = (key: keyof CompanyDetails) => (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    onChange({ ...value, [key]: event.currentTarget.value })
  return (
    <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">
      <TextInput label="Legal name" placeholder="e.g. Banda Transport Limited" value={value.legalName || ''} onChange={set('legalName')} disabled={disabled} />
      <TextInput label="Trading name" placeholder="Optional" value={value.tradingName || ''} onChange={set('tradingName')} disabled={disabled} />
      <TextInput label="PACRA registration number" placeholder="From the certificate of incorporation" value={value.registrationNumber || ''} onChange={set('registrationNumber')} disabled={disabled} />
      <TextInput label="Road Service Licence number (RTSA)" placeholder="The company's licence to carry passengers" value={value.operatingLicenceNumber || ''} onChange={set('operatingLicenceNumber')} disabled={disabled} />
      <TextInput label="Business type" placeholder="e.g. Bus operator" value={value.businessType || ''} onChange={set('businessType')} disabled={disabled} />
      <TextInput label="Province" placeholder="Province" value={value.province || ''} onChange={set('province')} disabled={disabled} />
      <TextInput label="District" placeholder="District" value={value.district || ''} onChange={set('district')} disabled={disabled} />
      <Textarea label="Physical address" placeholder="Street, area, town" value={value.physicalAddress || ''} onChange={set('physicalAddress')} disabled={disabled} autosize minRows={2} />
      <Textarea label="Postal address" placeholder="Optional" value={value.postalAddress || ''} onChange={set('postalAddress')} disabled={disabled} autosize minRows={2} />
      <TextInput label="Contact email" type="email" placeholder="name@company.zm" value={value.contactEmail || ''} onChange={set('contactEmail')} disabled={disabled} />
      <TextInput label="Contact phone" type="tel" placeholder="+260 …" value={value.contactPhone || ''} onChange={set('contactPhone')} disabled={disabled} />
    </SimpleGrid>
  )
}
