
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useNotifications } from '@/services/useNotifications'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const {
  notifications,
  loadingNotifications,
  refreshNotifications,
  markAsRead,
  markAllAsRead,
} = useNotifications()

const errorMessage = ref('')

watch(
    () => props.open,
    (isOpen) => {
      if (isOpen) {
        errorMessage.value = ''
        void refreshNotifications()
      }
    }
)

function formatDate(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString('it-IT', {
    dateStyle: 'short',
    timeStyle: 'short',
  })
}

async function handleMarkAsRead(id: string) {
  errorMessage.value = ''

  try {
    await markAsRead(id)
  } catch (error) {
    console.error('Errore durante la lettura della notifica:', error)
    errorMessage.value = 'Impossibile aggiornare la notifica.'
  }
}

async function handleMarkAllAsRead() {
  errorMessage.value = ''

  try {
    await markAllAsRead()
    await refreshNotifications()
  } catch (error) {
    console.error('Errore durante la lettura delle notifiche:', error)
    errorMessage.value = 'Impossibile segnare tutte le notifiche come lette.'
  }
}
</script>

<template>
  <div
      v-if="open"
      class="fixed inset-0 z-40"
      @click.self="emit('close')"
  >
    <div
        class="absolute inset-0 bg-black/30"
        @click="emit('close')"
    />

    <aside
        class="absolute right-0 top-0 flex h-full w-full max-w-md flex-col bg-white shadow-xl"
        aria-label="Pannello notifiche"
    >
      <header class="flex items-center justify-between border-b p-5">
        <h2 class="text-lg font-semibold text-slate-900">
          Notifiche
        </h2>

        <button
            type="button"
            class="rounded-md px-3 py-2 text-slate-500 hover:bg-slate-100"
            aria-label="Chiudi notifiche"
            @click="emit('close')"
        >
          ✕
        </button>
      </header>

      <div class="flex justify-end border-b px-4 py-3">
        <button
            type="button"
            class="text-sm font-medium text-blue-700 hover:underline"
            :disabled="loadingNotifications || notifications.length === 0"
            @click="handleMarkAllAsRead"
        >
          Segna tutte come lette
        </button>
      </div>

      <p
          v-if="errorMessage"
          class="px-5 pt-3 text-sm text-red-600"
          role="alert"
      >
        {{ errorMessage }}
      </p>

      <div class="flex-1 overflow-y-auto">
        <p
            v-if="loadingNotifications"
            class="p-5 text-sm text-slate-500"
        >
          Caricamento notifiche...
        </p>

        <p
            v-else-if="notifications.length === 0"
            class="p-5 text-sm text-slate-500"
        >
          Non ci sono notifiche.
        </p>

        <article
            v-for="notification in notifications"
            :key="notification.id"
            class="border-b p-4"
            :class="notification.read ? 'bg-white' : 'bg-blue-50'"
        >
          <div class="flex items-start gap-3">
            <span
                class="mt-2 h-2 w-2 shrink-0 rounded-full"
                :class="notification.read ? 'bg-transparent' : 'bg-red-600'"
            />

            <div class="min-w-0 flex-1">
              <h3 class="font-medium text-slate-900">
                {{ notification.documentName }}
              </h3>

              <p class="mt-1 text-sm text-slate-600">
                {{ notification.message }}
              </p>

              <p class="mt-2 text-xs text-slate-400">
                {{ formatDate(notification.createdAt) }}
              </p>

              <button
                  v-if="!notification.read"
                  type="button"
                  class="mt-2 text-sm font-medium text-blue-700 hover:underline"
                  @click="handleMarkAsRead(notification.id)"
              >
                Segna come letta
              </button>
            </div>
          </div>
        </article>
      </div>
    </aside>
  </div>
</template>
