<!--
-Gestisce la visibilità delle schede (Upload, Chat o Entrambe).

-Controlla lo stato di connessione al backend all'avvio della pagina.

-Ascolta il completamento dell'upload dal componente figlio per mostrare una notifica temporanea di 6 secondi e aggiornare i dati.
-->


<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DocumentUpload from './components/DocumentUpload.vue'
import RagChat from './components/RagChat.vue'
import { checkRagStatus, type UploadResponse, type RagStatusResponse } from './services/ragApi'

type ActiveTab = 'both' | 'upload' | 'chat'
const currentTab = ref<ActiveTab>('both')

const uploadBanner = ref<string | null>(null)
const ragStatus = ref<RagStatusResponse | null>(null)
const isCheckingStatus = ref(false)

async function refreshStatus() {
  isCheckingStatus.value = true
  try {
    ragStatus.value = await checkRagStatus()
  } finally {
    isCheckingStatus.value = false
  }
}

onMounted(() => {
  refreshStatus()
})

function onDocumentUploaded(result: UploadResponse) {
  uploadBanner.value = `Documento "${result.fileName ?? 'file'}" elaborato con successo! È ora pronto per la chat.`
  refreshStatus()
  setTimeout(() => {
    uploadBanner.value = null
  }, 6000)
}
</script>

<template>
  <div class="min-h-screen bg-slate-100 text-slate-900 flex flex-col font-sans">
    <!-- Navbar / Header Stile Dashboard Moderna -->
    <header class="bg-white border-b border-slate-200 sticky top-0 z-10 shadow-xs">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3.5 flex flex-wrap items-center justify-between gap-4">
        <!-- Logo e Titolo -->
        <div class="flex items-center gap-3">
          <div class="w-9 h-9 rounded-xl bg-indigo-600 flex items-center justify-center text-white font-bold text-base shadow-xs">
            ⚡
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-base font-bold text-slate-900 tracking-tight leading-none">
                historyGenie
              </h1>
              <span class="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-100">
                RAG Dashboard
              </span>
            </div>
            <p class="text-xs text-slate-500 mt-0.5">
              Ambiente di Test &bull; Vue 3 + Spring Boot + RAGFlow
            </p>
          </div>
        </div>

        <!-- Stato Live RAGFlow & Selettore Viste -->
        <div class="flex flex-wrap items-center gap-3">
          <!-- Live RAGFlow Status Pill -->
          <div
            class="flex items-center gap-2 px-3 py-1.5 rounded-xl border text-xs cursor-pointer transition-all"
            :class="[
              ragStatus?.connected
                ? 'bg-emerald-50/80 border-emerald-200 text-emerald-800'
                : 'bg-rose-50/80 border-rose-200 text-rose-800'
            ]"
            title="Clicca per aggiornare lo stato di connessione"
            @click="refreshStatus"
          >
            <span
              class="w-2 h-2 rounded-full shrink-0"
              :class="[
                ragStatus?.connected ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'
              ]"
            ></span>

            <span class="font-semibold text-[11px]">
              {{ ragStatus?.connected ? 'RAGFlow Connesso' : 'RAGFlow Non Raggiungibile' }}
            </span>

            <span
              v-if="ragStatus?.connected && ragStatus?.documentCount !== undefined"
              class="hidden sm:inline text-[10px] opacity-80 border-l border-emerald-300/60 pl-1.5"
            >
              {{ ragStatus.documentCount }} doc
            </span>

            <svg
              class="w-3 h-3 opacity-60 hover:opacity-100 transition-opacity ml-0.5"
              :class="{ 'animate-spin': isCheckingStatus }"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </div>

          <!-- Selettore Viste / Tab -->
          <div class="flex items-center bg-slate-100/90 p-1 rounded-xl border border-slate-200 text-xs font-semibold">
            <button
              type="button"
              class="px-3.5 py-1.5 rounded-lg transition-all cursor-pointer"
              :class="currentTab === 'both' ? 'bg-white text-indigo-700 shadow-xs font-bold' : 'text-slate-600 hover:text-slate-900'"
              @click="currentTab = 'both'"
            >
              Vista Completa
            </button>
            <button
              type="button"
              class="px-3.5 py-1.5 rounded-lg transition-all cursor-pointer"
              :class="currentTab === 'upload' ? 'bg-white text-indigo-700 shadow-xs font-bold' : 'text-slate-600 hover:text-slate-900'"
              @click="currentTab = 'upload'"
            >
              Carica Documenti
            </button>
            <button
              type="button"
              class="px-3.5 py-1.5 rounded-lg transition-all cursor-pointer"
              :class="currentTab === 'chat' ? 'bg-white text-indigo-700 shadow-xs font-bold' : 'text-slate-600 hover:text-slate-900'"
              @click="currentTab = 'chat'"
            >
              Chat
            </button>
          </div>
        </div>
      </div>
    </header>

    <!-- Banner di Stato RAGFlow Dettagliato (se non connesso) -->
    <div
      v-if="ragStatus && !ragStatus.connected"
      class="max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 pt-4"
    >
      <div class="bg-amber-50 border border-amber-200 p-3.5 rounded-xl text-amber-900 text-xs flex items-center justify-between gap-3 shadow-xs">
        <div class="flex items-center gap-2">
          <svg class="w-4 h-4 text-amber-600 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <div>
            <span class="font-semibold">RAGFlow non risponde:</span> {{ ragStatus.message }}
            <span class="block text-[11px] text-amber-700 mt-0.5">
              Verifica che il container Docker sia attivo su <code class="font-mono bg-amber-100 px-1 py-0.2 rounded">{{ ragStatus.ragflowBaseUrl }}</code>.
            </span>
          </div>
        </div>
        <button
          type="button"
          class="px-3 py-1.5 bg-amber-200/80 hover:bg-amber-200 text-amber-900 font-semibold rounded-lg text-xs transition-colors cursor-pointer"
          :disabled="isCheckingStatus"
          @click="refreshStatus"
        >
          Riprova
        </button>
      </div>
    </div>

    <!-- Notifica Fluttuante Ingestione -->
    <div v-if="uploadBanner" class="max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 pt-4">
      <div class="bg-indigo-600 text-white text-xs py-2.5 px-4 rounded-xl shadow-sm flex items-center justify-between gap-2">
        <span class="flex items-center gap-2">
          <span>✨</span>
          <span>{{ uploadBanner }}</span>
        </span>
        <button
          type="button"
          class="text-indigo-200 hover:text-white underline font-semibold cursor-pointer"
          @click="currentTab = 'chat'"
        >
          Apri Chat
        </button>
      </div>
    </div>

    <!-- Main Content Area -->
    <main class="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-6">
      <!-- Vista Doppia: Affiancata su desktop, a colonna su mobile -->
      <div v-if="currentTab === 'both'" class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <div class="lg:col-span-5">
          <DocumentUpload @uploaded="onDocumentUploaded" />
        </div>
        <div class="lg:col-span-7">
          <RagChat />
        </div>
      </div>

      <!-- Vista Solo Upload -->
      <div v-else-if="currentTab === 'upload'" class="max-w-2xl mx-auto">
        <DocumentUpload @uploaded="onDocumentUploaded" />
      </div>

      <!-- Vista Solo Chat -->
      <div v-else-if="currentTab === 'chat'" class="max-w-3xl mx-auto">
        <RagChat />
      </div>
    </main>

    <!-- Footer Minimale con info endpoint e dataset collegato -->
    <footer class="border-t border-slate-200 bg-white py-3 text-center text-xs text-slate-400">
      <div class="max-w-7xl mx-auto px-4 flex flex-wrap items-center justify-between gap-2">
        <span>historyGenie &bull; RAGFlow Test Environment</span>
        <span v-if="ragStatus?.connected" class="text-[11px] text-slate-500">
          Dataset: <code class="font-mono text-indigo-600">{{ ragStatus.datasetId }}</code>
          &bull; Host: <code class="font-mono text-slate-600">{{ ragStatus.ragflowBaseUrl }}</code>
        </span>
        <span>
          Endpoints: <code class="font-mono text-slate-600">/api/rag/upload</code> &bull; <code class="font-mono text-slate-600">/api/rag/chat</code>
        </span>
      </div>
    </footer>
  </div>
</template>
