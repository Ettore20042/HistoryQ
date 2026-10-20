
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useNotifications } from '@/services/useNotifications'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const {
  notifications,
  loadingNotifications,
  refreshNotifications,
  markAsRead,
  markAllAsRead,
  acceptInvitation,
  declineInvitation,
} = useNotifications()

const errorMessage = ref('')
const invitationLoading = ref<string | null>(null)
const router = useRouter()

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

async function handleInvitationAction(
    id: string,
    action: 'accept' | 'decline'
) {
  errorMessage.value = ''
  invitationLoading.value = id

  try {
    if (action === 'accept') {
      await acceptInvitation(id)
    } else {
      await declineInvitation(id)
    }
  } catch (error) {
    errorMessage.value = error instanceof Error
        ? error.message
        : 'Impossibile aggiornare l’invito.'
  } finally {
    invitationLoading.value = null
  }
}

function openTeam(teamId?: string) {
  if (teamId) {
    emit('close')
    void router.push(`/teams/${teamId}`)
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
                {{ notification.teamName || notification.documentName || notification.title }}
              </h3>

              <p
                  v-if="notification.type === 'TEAM_INVITATION' && notification.invitedByUsername"
                  class="mt-1 text-xs text-slate-500"
              >
                Invitato da {{ notification.invitedByUsername }}
              </p>

              <p class="mt-1 text-sm text-slate-600">
                {{ notification.message }}
              </p>

              <div
                  v-if="notification.type === 'TEAM_INVITATION'"
                  class="mt-3"
              >
                <div
                    v-if="notification.invitationStatus === 'PENDING'"
                    class="flex gap-2"
                >
                  <button
                      type="button"
                      class="rounded-lg bg-green-700 px-3 py-2 text-xs font-semibold text-white hover:bg-green-800 disabled:cursor-not-allowed disabled:opacity-60"
                      :disabled="invitationLoading === notification.id"
                      @click="handleInvitationAction(notification.id, 'accept')"
                  >
                    {{ invitationLoading === notification.id ? 'Aggiornamento...' : 'Accetta' }}
                  </button>
                  <button
                      type="button"
                      class="rounded-lg border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                      :disabled="invitationLoading === notification.id"
                      @click="handleInvitationAction(notification.id, 'decline')"
                  >
                    Rifiuta
                  </button>
                </div>

                <div
                    v-else-if="notification.invitationStatus === 'ACCEPTED'"
                    class="flex items-center gap-3 text-sm font-medium text-green-700"
                >
                  <span>Invito accettato.</span>
                  <button
                      v-if="notification.teamId"
                      type="button"
                      class="text-blue-700 hover:underline"
                      @click="openTeam(notification.teamId)"
                  >
                    Apri Team
                  </button>
                </div>

                <p
                    v-else-if="notification.invitationStatus === 'DECLINED'"
                    class="text-sm font-medium text-slate-500"
                >
                  Invito rifiutato.
                </p>
              </div>

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
