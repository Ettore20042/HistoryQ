import { authenticatedFetch } from './auth'

interface DocumentResponse{
    id: string;
}

export async function uploadDocument(files:File[]):Promise<DocumentResponse> {
  const formData = new FormData();
    for (const file of files) {
        formData.append('files', file)
    }

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
