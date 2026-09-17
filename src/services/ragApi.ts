export interface UploadResponse {
  status: string
  fileName?: string
  fileNames?: string[]
  uploadedCount?: number
  message?: string
  rawResponse?: string
}

export interface ChatResponse {
  status: string
  answer?: string
  citations?: Array<unknown>
  rawResponse?: string
}

export interface RagStatusResponse {
  connected: boolean
  status: 'ONLINE' | 'OFFLINE' | 'ERROR'
  ragflowBaseUrl?: string
  datasetId?: string
  chatId?: string
  documentCount?: number
  message?: string
}

/**
 * Check live connection status to RAGFlow via backend
 */
export async function checkRagStatus(): Promise<RagStatusResponse> {
  try {
    const response = await fetch('/api/rag/status')
    if (!response.ok) {
      return {
        connected: false,
        status: 'OFFLINE',
        message: `Backend non pronto (HTTP ${response.status})`,
      }
    }
    return await response.json()
  } catch (err) {
    return {
      connected: false,
      status: 'OFFLINE',
      message: err instanceof Error ? err.message : 'Connessione al backend fallita',
    }
  }
}

/**
 * Upload one or more files to Spring Boot backend -> RAGFlow.
 */
export async function uploadDocument(file: File | File[]): Promise<UploadResponse> {
  const formData = new FormData()
  const files = Array.isArray(file) ? file : [file]

  files.forEach((item) => {
    formData.append('files', item)
  })

  const response = await fetch('/api/rag/upload', {
    method: 'POST',
    body: formData,
  })

  const data = await response.json().catch(() => ({}))

  if (!response.ok) {
    throw new Error(data.message || `Errore di upload (Status ${response.status})`)
  }

  return data
}

/**
 * Send a question to Spring Boot backend -> RAGFlow
 */
export async function askQuestion(question: string): Promise<ChatResponse> {
  const response = await fetch('/api/rag/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ question: question.trim() }),
  })

  const data = await response.json().catch(() => ({}))

  if (!response.ok) {
    throw new Error(data.message || `Errore nella risposta della chat (Status ${response.status})`)
  }

  return data
}
