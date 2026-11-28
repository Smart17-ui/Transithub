import { useEffect, useState } from 'react'
import { Button, Group, Loader, Modal, Text } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { api, saveBlob } from '../api/client'
import type { Evidence } from '../api/types'
import { ApiError, evidenceTypeLabels, formatDate } from './common'

/**
 * Shows a document (photo or PDF) inside the page, so people can read it before
 * deciding to download it. The file is fetched with the user's own sign-in and
 * shown from memory; it is never opened at a public address.
 *
 * Only PDF, JPEG and PNG can be stored (the server checks the real file type),
 * so nothing else ever reaches this viewer.
 */
export function DocumentPreview({ token, registrationId, evidence, basePath, onClose }: {
  token: string; registrationId: string; evidence: Evidence | null; basePath?: string; onClose: () => void
}) {
  const file = useQuery({
    queryKey: ['evidence-file', registrationId, evidence?.id],
    queryFn: () => api.evidenceBlob(token, registrationId, evidence!.id, basePath),
    enabled: !!evidence,
    staleTime: 5 * 60_000,
    gcTime: 60_000,
  })
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    if (!file.data) return
    const objectUrl = URL.createObjectURL(file.data)
    setUrl(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [file.data])

  if (!evidence) return null
  const isPdf = evidence.contentType === 'application/pdf'

  return (
    <Modal opened onClose={onClose} size="min(1100px, 96vw)" radius="lg" centered
      title={<div>
        <Text fw={700}>{evidence.fileName}</Text>
        <Text size="xs" c="dimmed">{evidenceTypeLabels[evidence.evidenceType]} · {(evidence.sizeBytes / 1024).toFixed(0)} KB · uploaded {formatDate(evidence.uploadedAt)}</Text>
      </div>}>
      <div className="document-preview">
        {file.isPending && <Group justify="center" py="xl"><Loader color="teal" /></Group>}
        {file.error && <ApiError error={file.error} />}
        {url && (isPdf
          ? <iframe src={url} title={`Preview of ${evidence.fileName}`} className="document-preview-pdf" />
          : <img src={url} alt={`Preview of ${evidence.fileName}`} className="document-preview-image" />)}
      </div>
      <Group justify="flex-end" mt="md">
        <Button variant="default" radius="xl" onClick={onClose}>Close</Button>
        <Button radius="xl" color="teal" disabled={!file.data} onClick={() => saveBlob(file.data!, evidence.fileName)}>Download</Button>
      </Group>
    </Modal>
  )
}
