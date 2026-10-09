<script setup lang="ts">
import { ref } from 'vue'
import { register } from '@/services/auth'
import router from '@/router'

const username = ref('')
const email = ref('')
const password = ref('')
const confirmation = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  if (password.value.length < 8) {
    error.value = 'La password deve contenere almeno 8 caratteri.'
    return
  }
  if (password.value !== confirmation.value) {
    error.value = 'Le password non coincidono.'
    return
  }
  loading.value = true
  try {
    await register(username.value, email.value, password.value)
    await router.push('/')
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : 'Impossibile creare l’account.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-md">
    <div class="mb-8 text-center">
      <div class="mx-auto flex h-11 w-11 items-center justify-center rounded-lg bg-[#102a43] font-bold text-white">H</div>
      <h1 class="mt-5 text-2xl font-semibold tracking-tight text-[#102a43]">Creazione account</h1>
      <p class="mt-2 text-sm text-slate-500">Crea il tuo spazio privato per gli archivi.</p>
    </div>
    <form class="border border-slate-200 bg-white p-6 shadow-sm sm:p-8" @submit.prevent="submit">
      <div v-if="error" class="mb-5 border border-red-100 bg-red-50 px-3 py-2.5 text-sm text-red-700">{{ error }}</div>
      <label class="block text-sm font-medium text-slate-700">Username
        <input v-model="username" required maxlength="64" autocomplete="username" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" />
      </label>
      <label class="mt-4 block text-sm font-medium text-slate-700">Email
        <input v-model="email" required type="email" autocomplete="email" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" />
      </label>
      <label class="mt-4 block text-sm font-medium text-slate-700">Password
        <input v-model="password" required type="password" minlength="8" autocomplete="new-password" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" />
      </label>
      <label class="mt-4 block text-sm font-medium text-slate-700">Conferma password
        <input v-model="confirmation" required type="password" autocomplete="new-password" class="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" />
      </label>
      <button :disabled="loading" class="mt-6 w-full rounded-lg bg-[#102a43] px-4 py-3 text-sm font-semibold text-white hover:bg-[#173f61] disabled:cursor-not-allowed disabled:opacity-60">
        {{ loading ? 'Registrazione in corso…' : 'Registrati' }}
      </button>
      <p class="mt-6 text-center text-sm text-slate-500">Hai già un account?
        <RouterLink to="/login" class="font-semibold text-blue-700 hover:text-blue-800">Accedi</RouterLink>
      </p>
    </form>
  </div>
</template>
