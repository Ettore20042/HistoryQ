import { authenticatedFetch } from './auth'

export interface DocumentResponse {
  id: string
  originalName: string
  fileType: string
  fileSizeBytes: number
  pageCount: number
  status: string
  progress: number
  createdAt: string
  createdBy: string | null
  description: string
  displayName: string
  historicalDate: string
  author: string
  archiveSource: string
  visibility: 'PUBLIC' | 'PRIVATE'
}

export async function uploadDocument(files: File[], displayName: string, description: string, historicalDate: string, author: string, archive_source: string): Promise<DocumentResponse> {
  const formData = new FormData()
  for (const file of files) formData.append('files', file)
  formData.append('displayName', displayName)
  formData.append('description', description)
  formData.append('historicalDate', historicalDate)
  formData.append('author', author)
  formData.append('archive_source', archive_source)
  const response = await authenticatedFetch('/api/documents/upload', { method: 'POST', body: formData })
  if (!response.ok) throw new Error('Failed to upload document')
  return await response.json() as DocumentResponse
}

interface DocumentStatusResponse {
  status: string
  progress: number
}

export function getDocumentStatus(documentId: string): Promise<DocumentStatusResponse> {
  return authenticatedFetch(`/api/documents/${documentId}/status`).then(async response => {
    if (!response.ok) throw new Error(`Failed to get document status (${response.status})`)
    return await response.json() as DocumentStatusResponse
  })
}

export async function getDocuments(): Promise<DocumentResponse[]> {
  const response = await authenticatedFetch('/api/documents')
  if (!response.ok) throw new Error('Failed to get documents')
  return await response.json() as DocumentResponse[]
}

export function getDocumentPreviewUrl(documentId: string): string {
  return `/api/documents/${documentId}/preview`
}

export async function getDocumentPreview(documentId: string): Promise<string> {
  const response = await authenticatedFetch(`/api/documents/${documentId}/preview`)
  if (!response.ok) throw new Error('Failed to load document preview')
  return URL.createObjectURL(await response.blob())
}

export interface ChatResponse {
  status: string
  answer: string
  citations: Array<string | Record<string, unknown>>
}

export async function askDocumentQuestion(documentId: string, question: string): Promise<ChatResponse> {
  const response = await authenticatedFetch(`/api/documents/${documentId}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  })
  if (!response.ok) throw new Error(await response.text() || 'Errore durante la richiesta alla chat.')
  return await response.json() as ChatResponse
}
export async function askPublicDocumentQuestion(documentId: string, question: string): Promise<ChatResponse> {
  const response = await fetch(`/api/public/documents/${documentId}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  })
  if (!response.ok) throw new Error(await response.text() || 'Errore durante la richiesta alla chat pubblica.')
  return await response.json() as ChatResponse
}

export async function getDocumentPages(documentId: string): Promise<string[]> {
  const response = await authenticatedFetch(`/api/documents/${documentId}/pages`)
  if (!response.ok) throw new Error('Impossibile recuperare le pagine del documento.')
  return await response.json() as string[]
}

export async function getDocumentPage(documentId: string, filename: string): Promise<string> {
  const response = await authenticatedFetch(`/api/documents/${documentId}/pages/image/${encodeURIComponent(filename)}`)
  if (!response.ok) throw new Error('Impossibile caricare questa pagina.')
  return URL.createObjectURL(await response.blob())
}

export interface PageTranscriptionResponse {
  id: string
  documentId: string
  filename: string
  ocrText: string | null
}

export async function getPageTranscription(documentId: string, filename: string): Promise<PageTranscriptionResponse> {
  const response = await authenticatedFetch(`/api/documents/${documentId}/page-transcriptions/${encodeURIComponent(filename)}`)
  if (!response.ok) throw new Error('Impossibile recuperare la trascrizione della pagina.')
  return await response.json() as PageTranscriptionResponse
}

export async function updatePageTranscription(documentId: string, filename: string, ocrText: string): Promise<PageTranscriptionResponse> {
  const response = await authenticatedFetch(`/api/documents/${documentId}/page-transcriptions/${encodeURIComponent(filename)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ocrText }),
  })
  if (!response.ok) throw new Error('Impossibile salvare la trascrizione della pagina.')
  return await response.json() as PageTranscriptionResponse
}

export async function toggleVisibility(document: DocumentResponse): Promise<void> {
  const newVisibility = document.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'
  const response = await authenticatedFetch(`/api/documents/${document.id}/visibility`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ visibility: newVisibility }),
  })
  if (!response.ok) throw new Error(`Errore HTTP ${response.status}`)
  document.visibility = (await response.json() as DocumentResponse).visibility
}

export async function getPublicDocumentPreview(documentId: string): Promise<string> {
  const response = await fetch(`/api/public/documents/${documentId}/preview`)
  if (!response.ok) throw new Error(`Errore anteprima: HTTP ${response.status}`)
  return URL.createObjectURL(await response.blob())
}

export async function getPublicDocument(documentId: string): Promise<DocumentResponse> {
  const response = await fetch(`/api/public/documents/${documentId}`)
  if (!response.ok) throw new Error('Documento pubblico non trovato.')
  return await response.json() as DocumentResponse
}

export async function getPublicDocumentPages(documentId: string): Promise<string[]> {
  const response = await fetch(`/api/public/documents/${documentId}/pages`)
  if (!response.ok) throw new Error('Impossibile recuperare le pagine pubbliche.')
  return await response.json() as string[]
}

export async function getPublicDocumentPage(documentId: string, filename: string): Promise<string> {
  const response = await fetch(`/api/public/documents/${documentId}/pages/image/${encodeURIComponent(filename)}`)
  if (!response.ok) throw new Error('Impossibile caricare l’immagine della pagina.')
  return URL.createObjectURL(await response.blob())
}

export async function getPublicPageTranscription(documentId: string, filename: string): Promise<PageTranscriptionResponse> {
  const response = await fetch(`/api/public/documents/${documentId}/page-transcriptions/${encodeURIComponent(filename)}`)
  if (!response.ok) throw new Error('Impossibile recuperare la trascrizione pubblica.')
  return await response.json() as PageTranscriptionResponse
}

export async function askCollectionQuestion(
    collectionId: string,
    question: string
): Promise<ChatResponse> {
  const response = await authenticatedFetch(
      `/api/collections/${collectionId}/chat`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ question }),
      }
  )

  if (!response.ok) {
    throw new Error(
        await response.text() ||
        'Errore durante la richiesta alla chat della collezione.'
    )
  }

  return await response.json() as ChatResponse
}
