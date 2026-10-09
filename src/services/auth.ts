import { reactive } from 'vue'

const TOKEN_KEY = 'historyq_token'
const USERNAME_KEY = 'historyq_username'

interface AuthResponse {
  token: string
  username: string
  role: string
}

interface AuthState {
  token: string | null
  username: string | null
}

function decodePayload(token: string): { sub?: string; exp?: number } {
  try {
    const payload = token.split('.')[1]
    if (!payload) return {}
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return {}
  }
}

function readStoredToken(): string | null {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return null
  const { exp } = decodePayload(token)
  if (exp && exp * 1000 <= Date.now()) {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USERNAME_KEY)
    return null
  }
  return token
}

export const authState = reactive<AuthState>({
  token: readStoredToken(),
  username: localStorage.getItem(USERNAME_KEY),
})

export function isAuthenticated(): boolean {
  return Boolean(authState.token)
}

export async function login(username: string, password: string): Promise<AuthResponse> {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) throw new Error(await readError(response, 'Username o password non validi.'))
  const data = await response.json() as AuthResponse
  setSession(data)
  return data
}

export async function register(username: string, email: string, password: string): Promise<AuthResponse> {
  const response = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email, password }),
  })
  if (!response.ok) throw new Error(await readError(response, 'Impossibile creare l’account.'))
  const data = await response.json() as AuthResponse
  setSession(data)
  return data
}

export function logout(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
  authState.token = null
  authState.username = null
}

export function authenticatedFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  if (authState.token) headers.set('Authorization', `Bearer ${authState.token}`)
  return fetch(input, { ...init, headers })
}

function setSession(data: AuthResponse): void {
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USERNAME_KEY, data.username)
  authState.token = data.token
  authState.username = data.username
}

async function readError(response: Response, fallback: string): Promise<string> {
  try {
    const body = await response.json() as { message?: string; detail?: string }
    return body.message || body.detail || fallback
  } catch {
    return fallback
  }
}
