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
