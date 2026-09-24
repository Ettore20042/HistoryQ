<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { askQuestion } from '../services/ragApi'

interface Message {
  id: string
  role: 'user' | 'assistant'
  text: string
  timestamp: string
  citations?: Array<unknown>
}

const messages = ref<Message[]>([
  {
    id: 'welcome',
    role: 'assistant',
    text: 'Ciao! Sono historyGenie. Poni una domanda sui documenti caricati nel dataset RAGFlow.',
    timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
  },
])

const inputQuery = ref('')
const isLoading = ref(false)
const chatContainerRef = ref<HTMLElement | null>(null)
const errorAlert = ref<string | null>(null)

async function scrollToBottom() {
  await nextTick() // Assicurati che il DOM sia aggiornato prima di scorrere
  if (chatContainerRef.value) {
    chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight
  }
}

async function handleSendMessage() {
  const query = inputQuery.value.trim()
  if (!query || isLoading.value) return

  errorAlert.value = null

  // Inserisci messaggio utente
  const userMessage: Message = {
    id: 'msg-' + Date.now(),
    role: 'user',
    text: query,
    timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
  }
  messages.value.push(userMessage)
  inputQuery.value = ''
  isLoading.value = true
  await scrollToBottom()

  try {
    const response = await askQuestion(query)
    const assistantMessage: Message = {
      id: 'res-' + Date.now(),
      role: 'assistant',
      text: response.answer || 'Nessuna risposta disponibile.',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      citations: response.citations,
    }
    messages.value.push(assistantMessage)
  } catch (err) {
    const errorText = err instanceof Error ? err.message : 'Errore durante la richiesta alla chat.'
    errorAlert.value = errorText
    messages.value.push({
      id: 'err-' + Date.now(),
      role: 'assistant',
      text: `⚠️ Impossibile completare la richiesta: ${errorText}`,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    })
  } finally {
    isLoading.value = false
    await scrollToBottom()
  }
}

function clearChat() {
  messages.value = [
    {
      id: 'welcome',
      role: 'assistant',
      text: 'Cronologia azzerata. Chiedi qualsiasi informazione sui documenti caricati!',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    },
  ]
  errorAlert.value = null
}
</script>

<template>
  <div class="bg-white rounded-xl border border-slate-200 shadow-sm flex flex-col overflow-hidden">
    <!-- Header Chat Box -->
    <div class="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between bg-slate-50/70">
      <div class="flex items-center gap-2.5">
        <div class="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center text-white shadow-xs">
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
          </svg>
        </div>
        <div>
          <h3 class="font-semibold text-slate-800 text-sm leading-tight">historyGenie Chat</h3>
          <div class="flex items-center gap-1 text-[11px] text-emerald-600">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
            RAGFlow attivo
          </div>
        </div>
      </div>

      <button
        type="button"
        class="text-xs text-slate-400 hover:text-slate-600 hover:bg-slate-200/60 px-2.5 py-1 rounded-lg transition-colors cursor-pointer"
        title="Cancella cronologia chat"
        @click="clearChat"
      >
        Azzera conversazione
      </button>
    </div>

    <!-- Area Messaggi: Altezza fissa scrollabile h-[500px] overflow-y-auto -->
    <div
      ref="chatContainerRef"
      class="h-[500px] overflow-y-auto p-5 space-y-4 bg-slate-50/40"
    >
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="flex flex-col"
        :class="msg.role === 'user' ? 'items-end' : 'items-start'"
      >
        <div class="flex items-end gap-2 max-w-[85%] sm:max-w-[78%]">
          <!-- Avatar IA -->
          <div
            v-if="msg.role === 'assistant'"
            class="w-6 h-6 rounded-md bg-indigo-100 text-indigo-700 flex items-center justify-center shrink-0 mb-0.5 text-xs font-bold"
          >
            HG
          </div>

          <!-- Bolla messaggio: Utente (blu/indigo) vs IA (grigio/slate) -->
          <div
            class="p-3.5 text-sm leading-relaxed"
            :class="[
              msg.role === 'user'
                ? 'bg-indigo-600 text-white rounded-2xl rounded-br-xs shadow-xs'
                : 'bg-slate-100 text-slate-800 border border-slate-200/80 rounded-2xl rounded-bl-xs shadow-xs'
            ]"
          >
            <p class="whitespace-pre-wrap select-text">{{ msg.text }}</p>

            <!-- Eventuali citazioni/fonti -->
            <div
              v-if="msg.citations && msg.citations.length > 0"
              class="mt-2.5 pt-2 border-t border-slate-200 text-xs text-slate-500"
            >
              <span class="font-semibold block mb-1">Fonti consultate:</span>
              <ul class="space-y-0.5 list-disc list-inside">
                <li v-for="(cit, idx) in msg.citations" :key="idx" class="truncate">
                  {{ typeof cit === 'string' ? cit : JSON.stringify(cit) }}
                </li>
              </ul>
            </div>
          </div>
        </div>

        <!-- Timestamp -->
        <span class="text-[10px] text-slate-400 mt-1 px-1">
          {{ msg.timestamp }}
        </span>
      </div>

      <!-- Indicatore di digitazione / caricamento risposta IA -->
      <div v-if="isLoading" class="flex items-start gap-2">
        <div class="w-6 h-6 rounded-md bg-indigo-100 text-indigo-700 flex items-center justify-center shrink-0 text-xs font-bold">
          HG
        </div>
        <div class="bg-slate-100 text-slate-800 border border-slate-200/80 px-4 py-3 rounded-2xl rounded-bl-xs shadow-xs">
          <div class="flex items-center gap-1.5">
            <span class="w-1.5 h-1.5 rounded-full bg-indigo-600 animate-bounce"></span>
            <span class="w-1.5 h-1.5 rounded-full bg-indigo-600 animate-bounce [animation-delay:0.2s]"></span>
            <span class="w-1.5 h-1.5 rounded-full bg-indigo-600 animate-bounce [animation-delay:0.4s]"></span>
            <span class="text-xs text-slate-500 ml-2">Elaborazione risposta con RAGFlow...</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Alert errore se presente -->
    <div
      v-if="errorAlert"
      class="px-5 py-2 bg-rose-50 border-t border-rose-100 text-rose-700 text-xs flex items-center justify-between"
    >
      <span>{{ errorAlert }}</span>
      <button
        type="button"
        class="text-rose-500 hover:text-rose-800 font-bold ml-2 cursor-pointer"
        @click="errorAlert = null"
      >
        ✕
      </button>
    </div>

    <!-- Input di testo fisso in basso con bottone di invio -->
    <div class="p-3.5 border-t border-slate-200 bg-white">
      <form class="flex items-center gap-2" @submit.prevent="handleSendMessage">
        <input
          v-model="inputQuery"
          type="text"
          placeholder="Scrivi una domanda sui documenti... (Premi Invio)"
          class="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white transition-all"
          :disabled="isLoading"
          @keyup.enter="handleSendMessage"
        />

        <button
          type="submit"
          class="inline-flex items-center justify-center w-10 h-10 rounded-xl bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white disabled:opacity-40 disabled:cursor-not-allowed shadow-sm transition-all cursor-pointer shrink-0"
          :disabled="!inputQuery.trim() || isLoading"
          title="Invia messaggio"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
          </svg>
        </button>
      </form>
    </div>
  </div>
</template>
