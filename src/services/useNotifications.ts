import { ref, watch } from 'vue'
import { authenticatedFetch, authState } from './auth'

const unreadCount = ref<number>(0)
let pollingInterval: ReturnType<typeof setInterval> | undefined
let initialized = false

export interface NotificationItem {
    id: string
    title: string
    message: string
    type: string
    read: boolean
    createdAt: string
    documentName?: string
    teamId?: string
    teamName?: string
    invitedByUsername?: string
    invitationStatus?: 'PENDING' | 'ACCEPTED' | 'DECLINED'
}

const notifications = ref<NotificationItem[]>([])
const loadingNotifications = ref(false)

async function fetchUnreadCount(): Promise<void> {
    if (!authState.token) {
        unreadCount.value = 0
        return
    }

    try {
        const response = await authenticatedFetch('/api/notifications/unread-count')
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`)
        }
        const data: { count: number } = await response.json()
        unreadCount.value = Number(data.count)
    } catch (error) {
        console.error('Errore nel conteggio delle notifiche:', error)
    }
}

async function fetchNotifications(): Promise<void> {
    if (!authState.token) {
        notifications.value = []
        return
    }

    loadingNotifications.value = true
    try {
        const response = await authenticatedFetch('/api/notifications')
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`)
        }
        notifications.value = await response.json() as NotificationItem[]
    } catch (error) {
        console.error('Errore nel recupero delle notifiche:', error)
    } finally {
        loadingNotifications.value = false
    }
}

async function markAsRead(id: string): Promise<void> {
    if (!authState.token) return

    const response = await authenticatedFetch(
        `/api/notifications/${encodeURIComponent(id)}/read`,
        { method: 'PATCH' },
    )

    if (!response.ok) {
        throw new Error(`Errore nella lettura della notifica: HTTP ${response.status}`)
    }

    const notification = notifications.value.find(item => item.id === id)
    if (notification && !notification.read) {
        notification.read = true
        await fetchUnreadCount()
    }
}

async function markAllAsRead(): Promise<void> {
    if (!authState.token) return

    const response = await authenticatedFetch('/api/notifications/read-all', {
        method: 'PATCH',
    })

    if (!response.ok) {
        throw new Error(`Errore nella lettura delle notifiche: HTTP ${response.status}`)
    }

    notifications.value.forEach(item => {
        item.read = true
    })
    await fetchUnreadCount()
}

async function handleInvitation(
    id: string,
    action: 'accept' | 'decline'
): Promise<NotificationItem> {
    const response = await authenticatedFetch(
        `/api/notifications/${encodeURIComponent(id)}/${action}-invitation`,
        { method: 'POST' }
    )

    if (!response.ok) {
        let message = action === 'accept'
            ? "Impossibile accettare l'invito."
            : "Impossibile rifiutare l'invito."
        try {
            const body = await response.json() as { message?: string }
            if (body.message) message = body.message
        } catch {
            // La risposta potrebbe non contenere JSON.
        }
        throw new Error(message)
    }

    const updated = await response.json() as NotificationItem
    const index = notifications.value.findIndex(item => item.id === id)
    if (index >= 0) notifications.value[index] = updated
    await fetchUnreadCount()
    return updated
}

function startPolling(): void {
    if (pollingInterval) clearInterval(pollingInterval)
    void fetchUnreadCount()
    pollingInterval = setInterval(fetchUnreadCount, 60000)
}

function stopPolling(): void {
    if (pollingInterval) {
        clearInterval(pollingInterval)
        pollingInterval = undefined
    }
    unreadCount.value = 0
    notifications.value = []
}

export function useNotifications() {
    if (!initialized) {
        initialized = true
        watch(
            () => authState.token,
            (token) => {
                if (token) startPolling()
                else stopPolling()
            },
            { immediate: true }
        )
    }

    return {
        unreadCount,
        refreshUnreadCount: fetchUnreadCount,
        notifications,
        loadingNotifications,
        refreshNotifications: fetchNotifications,
        markAsRead,
        markAllAsRead,
        acceptInvitation: (id: string) => handleInvitation(id, 'accept'),
        declineInvitation: (id: string) => handleInvitation(id, 'decline'),
    }
}
