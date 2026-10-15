import { ref, watch } from 'vue'
import { authState } from './auth'

const unreadCount = ref<number>(0)
let pollingInterval: ReturnType<typeof setInterval> | undefined
let initialized = false

async function fetchUnreadCount(): Promise<void> {
    const token = authState.token
    console.log('Token presente:', Boolean(token))
    console.log('Lunghezza token:', token?.length ?? 0)
    if (!token) {
        unreadCount.value = 0
        return
    }
    try {
        const response = await fetch('/api/notifications/unread-count', {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${token}` },
        })
        if (!response.ok)
            throw new Error(`HTTP ${response.status} - Failed to fetch unread notifications count`)
        const data: { count: number } = await response.json()
        unreadCount.value = Number(data.count)
    } catch (error) {
        console.error('Error fetching unread notifications count:', error)
    }
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
}

export function useNotifications() {
    if (!initialized) {
        initialized = true
        // Reagisce immediatamente al login/logout senza aspettare il prossimo poll
        watch(
            () => authState.token,
            (token) => {
                if (token) {
                    startPolling()
                } else {
                    stopPolling()
                }
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
    }
}



export interface NotificationItem {
    id: string
    title: string
    message: string
    type: string
    read: boolean
    createdAt: string
    documentName?: string

}

const notifications = ref<NotificationItem[]>([])
const loadingNotifications = ref(false)

async function fetchNotifications(): Promise<void> {
    const token = authState.token
    if (!token) {
        notifications.value = []
        return
    }

    loadingNotifications.value = true
    try {
        const response = await fetch('/api/notifications', {
            headers: { Authorization: `Bearer ${token}` },
        })

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`)
        }

        notifications.value = await response.json()
    } catch (error) {
        console.error('Errore nel recupero delle notifiche:', error)
    } finally {
        loadingNotifications.value = false
    }
}

async function markAsRead(id: string): Promise<void> {
    const token = authState.token
    if (!token) return

    const response = await fetch(
        `/api/notifications/${encodeURIComponent(id)}/read`,
        {
            method: 'PATCH',
            headers: { Authorization: `Bearer ${token}` },
        },
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
    const token = authState.token
    if (!token) return

    const response = await fetch('/api/notifications/read-all', {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${token}` },
    })

    if (!response.ok) {
        throw new Error(`Errore nella lettura delle notifiche: HTTP ${response.status}`)
    }

    notifications.value.forEach(item => {
        item.read = true
    })
    await fetchUnreadCount()
}

