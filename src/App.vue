<script setup lang="ts">
import { authState, logout } from './services/auth'
import router from './router'

function handleLogout() {
  logout()
  router.push('/')
}
</script>

<template>
  <div class="flex min-h-screen flex-col bg-[#f8f8f6] text-slate-900">
    <header class="border-b border-slate-200/80 bg-white">
      <div class="mx-auto flex h-18 w-full max-w-6xl items-center justify-between px-5 sm:px-8">
        <RouterLink to="/" class="flex items-center gap-3" aria-label="HistoryQ home">
          <span class="flex h-9 w-9 items-center justify-center rounded-lg bg-[#102a43] text-sm font-bold text-white shadow-sm">
            H
          </span>
          <span>
            <span class="block text-base font-bold tracking-tight text-[#102a43]">HistoryQ</span>
            <span class="hidden text-[10px] font-medium uppercase tracking-[0.18em] text-slate-400 sm:block">
              Document intelligence
            </span>
          </span>
        </RouterLink>

        <nav class="flex items-center gap-1 sm:gap-3" aria-label="Navigazione principale">
          <RouterLink
              to="/"
              class="rounded-md px-3 py-2 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-[#102a43]"
              active-class="bg-slate-100 text-[#102a43]"
          >
            Home
          </RouterLink>
          <template v-if="authState.token">
            <RouterLink
                to="/documents"
                class="rounded-md px-3 py-2 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-[#102a43]"
                active-class="bg-slate-100 text-[#102a43]"
            >Documenti</RouterLink>
            <span class="hidden border-l border-slate-200 pl-3 text-sm text-slate-500 sm:inline">{{ authState.username }}</span>
            <button type="button" class="rounded-md px-3 py-2 text-sm font-semibold text-[#102a43] hover:bg-slate-50" @click="handleLogout">Esci</button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="rounded-md px-3 py-2 text-sm font-medium text-slate-500 hover:bg-slate-50 hover:text-[#102a43]">Accedi</RouterLink>
            <RouterLink to="/register" class="rounded-lg bg-[#102a43] px-3 py-2 text-sm font-semibold text-white hover:bg-[#173f61]">Registrati</RouterLink>
          </template>
        </nav>
      </div>
    </header>

    <main class="mx-auto w-full max-w-6xl flex-1 px-5 py-10 sm:px-8 sm:py-14">
      <RouterView />
    </main>

    <footer class="border-t border-slate-200/80 bg-white">
      <div class="mx-auto flex w-full max-w-6xl flex-col gap-2 px-5 py-6 sm:flex-row sm:items-center sm:justify-between sm:px-8">
        <p class="text-sm font-medium text-slate-500">© 2026 HistoryQ</p>
        <p class="text-sm text-slate-400">Gestione e interrogazione intelligente di documenti storici.</p>
      </div>
    </footer>
  </div>
</template>
