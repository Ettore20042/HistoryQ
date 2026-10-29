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
export async function askTeamDocumentQuestion(teamId: string, documentId: string, question: string): Promise<ChatResponse> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/documents/${documentId}/chat`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  })
  if (!response.ok) throw new Error(await response.text() || 'Errore durante la richiesta alla chat.')
  return await response.json() as ChatResponse
}
export async function getTeamDocumentPages(teamId: string, documentId: string): Promise<string[]> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/documents/${documentId}/pages`)
  if (!response.ok) throw new Error('Impossibile recuperare le pagine del documento.')
  return await response.json() as string[]
}
export async function getTeamDocumentPage(teamId: string, documentId: string, filename: string): Promise<string> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/documents/${documentId}/pages/image/${encodeURIComponent(filename)}`)
  if (!response.ok) throw new Error('Impossibile caricare la pagina.')
  return URL.createObjectURL(await response.blob())
}
export async function getTeamPageTranscription(teamId: string, documentId: string, filename: string): Promise<PageTranscriptionResponse> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/documents/${documentId}/page-transcriptions/${encodeURIComponent(filename)}`)
  if (!response.ok) throw new Error('Impossibile recuperare la trascrizione.')
  return await response.json() as PageTranscriptionResponse
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
  if (!response.ok) throw new Error('Impossibile recuperare la trascrizione della pagina o rielaborazione in corso.')
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
    question: string,
    teamId?: string
): Promise<ChatResponse> {
  const response = await authenticatedFetch(
      `/api/collections/${collectionId}/chat${teamId ? `?teamId=${teamId}` : ''}`,
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

export interface TeamCollectionResponse {
  id: string
  name: string
  description: string | null
  visibility: string
  createdAt: string
  addedBy?: string | null
}
export async function getTeamCollections(teamId: string): Promise<TeamCollectionResponse[]> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/collections`)
  if (!response.ok) throw new Error('Impossibile recuperare le collezioni condivise.')
  return await response.json() as TeamCollectionResponse[]
}
export async function getTeamCollectionCandidates(teamId: string, source = 'ALL', query = ''): Promise<TeamCollectionResponse[]> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/collections/candidates?source=${source}&query=${encodeURIComponent(query)}`)
  if (!response.ok) throw new Error('Impossibile recuperare le collezioni disponibili.')
  return await response.json() as TeamCollectionResponse[]
}
export async function addTeamCollections(teamId: string, collectionIds: string[]): Promise<TeamCollectionResponse[]> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/collections`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ collectionIds }),
  })
  if (!response.ok) throw new Error(await response.text() || 'Impossibile condividere le collezioni.')
  return await response.json() as TeamCollectionResponse[]
}
export async function removeTeamCollection(teamId: string, collectionId: string): Promise<void> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/collections/${collectionId}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(await response.text() || 'Impossibile rimuovere la collezione.')
}

export interface TeamResponse {
    id: string
    name: string
    description:string|null
    createdBy:unknown
}
export interface CreateTeamRequest{
    name: string
    description:string|null
}
export async function getTeam(teamId:string): Promise<TeamResponse> {
    const response = await authenticatedFetch(`/api/teams/${teamId}`)
    if (!response.ok) throw new Error('Failed to get team')
    return await response.json() as TeamResponse
}

export async function getTeams(): Promise<TeamResponse[]> {
    const response = await authenticatedFetch('/api/teams')
    if (!response.ok) throw new Error('Failed to get teams')
    return await response.json() as TeamResponse[]
}
export async function createTeam(team: CreateTeamRequest): Promise<TeamResponse> {
    const response = await authenticatedFetch('/api/teams', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(team),
    })
    if (!response.ok) throw new Error('Failed to create team')
    return await response.json() as TeamResponse
}

export interface TeamMemberResponse {
  userId: string
  username: string
  role: 'OWNER' | 'ADMIN' | 'MEMBER'
}

export async function getTeamMembers(
    teamId: string
): Promise<TeamMemberResponse[]> {
  const response = await authenticatedFetch(
      `/api/teams/${teamId}/members`
  )

  if (!response.ok) {
    throw new Error(
        `Impossibile recuperare i membri del team (${response.status})`
    )
  }

  return await response.json() as TeamMemberResponse[]
}
export interface TeamInvitationResponse {
  id: string;
  email: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED';
}

export async function inviteTeamMember(
    teamId: string,
    email: string
): Promise<TeamInvitationResponse> {
  const response = await authenticatedFetch(
      `/api/teams/${teamId}/invitations`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ email })
      }
  );

  if (!response.ok) {
    let message = "Impossibile inviare l'invito.";

    try {
      const errorBody = await response.json();
      if (typeof errorBody.message === 'string') {
        message = errorBody.message;
      } else if (typeof errorBody.detail === 'string') {
        message = errorBody.detail;
      }
    } catch {
      // La risposta potrebbe non contenere JSON.
    }

    if (message === "Impossibile inviare l'invito.") {
      if (response.status === 400) {
        message = "Inserisci un indirizzo email valido.";
      } else if (response.status === 403) {
        message = "Non hai i permessi per invitare utenti in questo Team.";
      } else if (response.status >= 500) {
        message = "Si è verificato un errore interno. Riprova più tardi.";
      }
    }

    throw new Error(message);
  }

  return await response.json() as TeamInvitationResponse;
}

export interface TeamDocumentResponse {
  id: string
  displayName: string
  originalName: string
  author: string | null
  createdAt: string
  visibility: 'PUBLIC' | 'PRIVATE'
  status: string
  addedBy: string | null
}

async function readTeamDocumentResponse(response: Response): Promise<TeamDocumentResponse[]> {
  if (!response.ok) {
    throw new Error(`Errore documenti condivisi (${response.status})`)
  }
  return await response.json() as TeamDocumentResponse[]
}

export async function getTeamDocuments(teamId: string): Promise<TeamDocumentResponse[]> {
  return readTeamDocumentResponse(
      await authenticatedFetch(`/api/teams/${teamId}/documents`)
  )
}
export async function getTeamDocument(teamId: string, documentId: string): Promise<DocumentResponse> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/documents/${documentId}`)
  if (!response.ok) throw new Error('Documento non trovato o non accessibile.')
  return await response.json() as DocumentResponse
}

export async function getTeamDocumentCandidates(
    teamId: string,
    source: 'ALL' | 'PERSONAL' | 'PUBLIC',
    query = ''
): Promise<TeamDocumentResponse[]> {
  return readTeamDocumentResponse(
      await authenticatedFetch(
          `/api/teams/${teamId}/documents/candidates?source=${source}&query=${encodeURIComponent(query)}`
      )
  )
}

export async function addTeamDocuments(
    teamId: string,
    documentIds: string[]
): Promise<TeamDocumentResponse[]> {
  const response = await authenticatedFetch(`/api/teams/${teamId}/documents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ documentIds }),
  })
  return readTeamDocumentResponse(response)
}

export async function removeTeamDocument(
    teamId: string,
    documentId: string
): Promise<void> {
  const response = await authenticatedFetch(
      `/api/teams/${teamId}/documents/${documentId}`,
      { method: 'DELETE' }
  )
  if (!response.ok) {
    throw new Error(`Impossibile rimuovere il documento (${response.status})`)
  }
}

export async function getTeamDocumentPreview(
    teamId: string,
    documentId: string
): Promise<string> {
  const response = await authenticatedFetch(
      `/api/teams/${teamId}/documents/${documentId}/preview`
  )
  if (!response.ok) throw new Error('Impossibile caricare l’anteprima.')
  return URL.createObjectURL(await response.blob())
}

export async function downloadTeamDocument(
    teamId: string,
    documentId: string,
    displayName: string
): Promise<void> {
  const response = await authenticatedFetch(
      `/api/teams/${teamId}/documents/${documentId}/download`
  )
  if (!response.ok) throw new Error('Impossibile scaricare il documento.')
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = `${displayName}.zip`
  link.click()
  URL.revokeObjectURL(url)
}

export type OcrEngine =
    | 'tesseract'
    | 'churro'
    | 'lighton-1b'
    | 'lighton-4b'

export interface ReprocessDocumentResponse {
  documentId: string
  displayName: string
  status: string
  engine: OcrEngine
}

export async function reprocessDocument(
    documentId: string,
    engine: OcrEngine
): Promise<ReprocessDocumentResponse> {
  const response = await authenticatedFetch(
      `/api/documents/${documentId}/reprocess`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ engine }),
      }
  )

  if (!response.ok) {
    const message = await response.text()
    throw new Error(
        message || `Errore durante la rielaborazione (${response.status})`
    )
  }

  return await response.json() as ReprocessDocumentResponse
}
