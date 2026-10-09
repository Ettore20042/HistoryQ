import { authenticatedFetch } from './auth'

interface DocumentResponse{

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
}
interface DocumentTranscriptionResponse{
    transcription: string
}
export interface ChatResponse{
    status:string
    answer:string
    citations: Array<string | Record<string, unknown>>

}

export async function uploadDocument(files:File[], displayName: string,description:string,historicalDate:string,author:string,archive_source:string):Promise<DocumentResponse> {
  const formData = new FormData();
    for (const file of files) {
        formData.append('files', file)
    }
    formData.append('displayName', displayName)
    formData.append('description', description)
    formData.append('historicalDate', historicalDate)
    formData.append('author', author)
    formData.append('archive_source', archive_source)

    const response = await authenticatedFetch('/api/documents/upload', {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      throw new Error('Failed to upload document');
    }
    const data = await response.json();
    return data;

}
interface DocumentStatusResponse{
    status: string;
    progress:number;
}
export function getDocumentStatus(documentId: string): Promise<DocumentStatusResponse> {
  return authenticatedFetch(`/api/documents/${documentId}/status`)
    .then(response => {
      if (!response.ok) {
        throw new Error('Failed to get document status');
      }
      return response.json();
    });
}
export async function getDocuments(): Promise<DocumentResponse[]> {
    const response = await authenticatedFetch('/api/documents')

    if (!response.ok) {
        throw new Error('Failed to get documents')
    }

    return response.json()
}
export function getDocumentPreviewUrl(documentId: string): string {
    return `/api/documents/${documentId}/preview`
}
export async function getDocumentPreview(
    documentId: string
): Promise<string> {
    const token = localStorage.getItem('historyq_token')

    const response = await fetch(
        `/api/documents/${documentId}/preview`,
        {
            headers: {
                Authorization: `Bearer ${token}`,
            },
        }
    )

    if (!response.ok) {
        throw new Error('Failed to load document preview')
    }

    const blob = await response.blob()

    return URL.createObjectURL(blob)
}
export async function getDocumentTranscription(documentId:string): Promise<string> {
    const response = await authenticatedFetch(`/api/documents/${documentId}/transcription`)

            if (!response.ok) {
                throw new Error('Failed to get document transcription')
            }
            const data: DocumentTranscriptionResponse = await response.json()
            return data.transcription

}

export interface ChatResponse {
    status: string
    answer: string
    citations: Array<string | Record<string, unknown>>
}

export async function askDocumentQuestion(
    documentId: string,
    question: string
): Promise<ChatResponse> {
    const response = await authenticatedFetch(
        `/api/documents/${documentId}/chat`,
        {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ question }),
        }
    )

    if (!response.ok) {
        const errorText = await response.text()
        throw new Error(errorText || 'Errore durante la richiesta alla chat.')
    }

    return await response.json() as ChatResponse
}

export async function getDocumentPages(
    documentId: string
): Promise<string[]> {
    const response = await authenticatedFetch(
        `/api/documents/${documentId}/pages`
    )

    if (!response.ok) {
        throw new Error('Impossibile recuperare le pagine del documento.')
    }

    return await response.json() as string[]
}

export async function getDocumentPage(
    documentId: string,
    filename: string
): Promise<string> {
    const response = await authenticatedFetch(
        `/api/documents/${documentId}/pages/image/${encodeURIComponent(filename)}`
    )

    if (!response.ok) {
        throw new Error('Impossibile caricare questa pagina.')
    }

    const blob = await response.blob()

    return URL.createObjectURL(blob)
}
