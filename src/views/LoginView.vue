<script setup lang="ts">
import { ref } from 'vue'
import { login } from '@/services/auth'
import router from '@/router'

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await login(username.value, password.value)
    await router.push('/')
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : 'Impossibile completare l’accesso.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-md">
    <div class="mb-8 text-center">
      <div class="mx-auto flex h-11 w-11 items-center justify-center rounded-lg bg-[#102a43] font-bold text-white">H</div>
      <h1 class="mt-5 text-2xl font-semibold tracking-tight text-[#102a43]">Accedi a HistoryQ</h1>
      <p class="mt-2 text-sm text-slate-500">Entra nel tuo spazio di lavoro documentale.</p>
    </div>
    <form class="border border-slate-200 bg-white p-6 shadow-sm sm:p-8" @submit.prevent="submit">
      <div v-if="error" class="mb-5 border border-red-100 bg-red-50 px-3 py-2.5 text-sm text-red-700">{{ error }}</div>
      <label class="block text-sm font-medium text-slate-700">Username
        <input v-model="username" required autocomplete="username" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" />
      </label>
      <label class="mt-5 block text-sm font-medium text-slate-700">Password
        <input v-model="password" required type="password" autocomplete="current-password" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" />
      </label>
      <button :disabled="loading" class="mt-6 w-full rounded-lg bg-[#102a43] px-4 py-3 text-sm font-semibold text-white hover:bg-[#173f61] disabled:cursor-not-allowed disabled:opacity-60">
        {{ loading ? 'Accesso in corso…' : 'Accedi' }}
      </button>
      <p class="mt-6 text-center text-sm text-slate-500">Non hai un account?
        <RouterLink to="/register" class="font-semibold text-blue-700 hover:text-blue-800">Registrati</RouterLink>
      </p>
    </form>
  </div>
</template>
