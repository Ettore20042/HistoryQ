
<script setup lang="ts">
import { authState, logout } from './services/auth'
import router from './router'
import { useNotifications } from '@/services/useNotifications.ts'
import NotificationPanel from '@/views/panels/NotificationsPanels.vue'
import { ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { isAuthenticated } from './services/auth'

const panelOpen = ref(false)
const { unreadCount } = useNotifications()

function handleLogout() {
  panelOpen.value = false
  logout()
  router.push('/')
}
</script>

<template>
  <div class="flex min-h-screen flex-col bg-[#f8f8f6] text-slate-900">
    <header class="border-b border-[#d9cdbd] bg-[#fbf9f5]/95 backdrop-blur">
      <div
          class="mx-auto flex h-18 w-full max-w-6xl items-center justify-between px-5 sm:px-8"
      >
        <RouterLink
            to="/"
            class="flex items-center gap-3"
            aria-label="HistoryQ home"
        >
          <div class="flex items-center gap-3">
            <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#29251f] shadow-sm"
            >
              <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 40 40"
                  fill="none"
                  class="h-7 w-7"
                  aria-label="HistoryQ"
                  role="img"
              >
                <!-- Documento -->
                <path
                    d="M9 5.5H22L29 12.5V31H9V5.5Z"
                    stroke="#E8D6B5"
                    stroke-width="1.8"
                    stroke-linejoin="round"
                />

                <!-- Angolo piegato -->
                <path
                    d="M22 5.5V12.5H29"
                    stroke="#E8D6B5"
                    stroke-width="1.8"
                    stroke-linejoin="round"
                />

                <!-- Righe del documento -->
                <path
                    d="M13 16H23M13 20H21M13 24H18"
                    stroke="#E8D6B5"
                    stroke-width="1.6"
                    stroke-linecap="round"
                />

                <!-- Lente di ricerca -->
                <circle
                    cx="27"
                    cy="27"
                    r="5.5"
                    fill="#29251f"
                    stroke="#C6A36A"
                    stroke-width="2"
                />
                <path
                    d="M31 31L35 35"
                    stroke="#C6A36A"
                    stroke-width="2.5"
                    stroke-linecap="round"
                />
              </svg>
            </div>

            <span class="text-lg font-semibold tracking-tight">
    History<span class="text-[#9A7952]">Q</span>
  </span>
          </div>

        </RouterLink>

        <nav
            class="flex items-center gap-1 sm:gap-3"
            aria-label="Navigazione principale"
        >
          <RouterLink
              to="/"
              class="rounded-md px-3 py-2 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-[#102a43]"
              active-class="bg-slate-100 text-[#102a43]"
          >
            Home
          </RouterLink>

          <!-- Accessibile a tutti, anche senza login -->
          <RouterLink
              to="/public/documents"
              class="rounded-md px-3 py-2 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-[#102a43]"
              active-class="bg-slate-100 text-[#102a43]"
          >
            Esplora documenti
          </RouterLink>

          <template v-if="authState.token">
            <RouterLink
                v-if="isAuthenticated()"
                to="/collections"
                class="rounded-md px-3 py-2 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-[#102a43]"
                active-class="bg-slate-100 text-[#102a43]"
            >
              Collezioni
            </RouterLink>
            <RouterLink
                to="/teams"
                class="rounded-md px-3 py-2 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-[#102a43]"
                active-class="bg-slate-100 text-[#102a43]"
            >
              Team
            </RouterLink>
            <button
                type="button"
                class="relative inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-[#102a43]"
                :class="{ 'bg-slate-100 text-[#102a43]': panelOpen }"
                aria-label="Apri notifiche"
                :aria-expanded="panelOpen"
                @click="panelOpen = !panelOpen"
            >
              <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
              >
                <path
                    d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"
                />
                <path d="M10 21h4" />
              </svg>

              <span>Notifiche</span>

              <span
                  v-if="unreadCount > 0"
                  class="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-red-600 px-1.5 text-xs font-semibold text-white"
              >
                {{ unreadCount > 99 ? '99+' : unreadCount }}
              </span>
            </button>

            <RouterLink
                to="/documents"
                class="rounded-md px-3 py-2 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-[#102a43]"
                active-class="bg-slate-100 text-[#102a43]"
            >
              Documenti
            </RouterLink>

            <span
                class="hidden border-l border-slate-200 pl-3 text-sm text-slate-500 sm:inline"
            >
              {{ authState.username }}
            </span>

            <button
                type="button"
                class="rounded-md px-3 py-2 text-sm font-semibold text-[#102a43] hover:bg-slate-50"
                @click="handleLogout"
            >
              Esci
            </button>
          </template>

          <template v-else>
            <RouterLink
                to="/login"
                class="rounded-md px-3 py-2 text-sm font-medium text-slate-500 hover:bg-slate-50 hover:text-[#102a43]"
            >
              Accedi
            </RouterLink>

            <RouterLink
                to="/register"
                class="rounded-lg bg-[#102a43] px-3 py-2 text-sm font-semibold text-white hover:bg-[#173f61]"
            >
              Registrati
            </RouterLink>
          </template>
        </nav>
      </div>
    </header>

    <NotificationPanel
        :open="panelOpen"
        @close="panelOpen = false"
    />

    <main
        class="mx-auto w-full max-w-[1350px] flex-1 px-3 py-6 sm:px-4 sm:py-8 lg:px-5"
    >
      <RouterView />
    </main>

    <footer class="border-t border-[#d9cdbd] bg-[#fbf9f5]">
      <div
          class="mx-auto flex w-full max-w-6xl flex-col gap-2 px-5 py-6 sm:flex-row sm:items-center sm:justify-between sm:px-8"
      >
        <p class="text-sm font-medium text-slate-500">
          © 2026 HistoryQ
        </p>

        <p class="text-sm text-slate-400">
          Gestione e interrogazione intelligente di documenti storici.
        </p>
      </div>
    </footer>
  </div>
</template>
