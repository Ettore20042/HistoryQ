<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { uploadDocument, type UploadResponse } from '../services/ragApi'

const selectedFiles = ref<File[]>([])
const isDragging = ref(false)
const isLoading = ref(false)
const uploadProgress = ref(0)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)
const uploadDetails = ref<UploadResponse | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

let progressInterval: ReturnType<typeof setInterval> | null = null

const emit = defineEmits<{
  (e: 'uploaded', payload: UploadResponse): void
}>()

// FIX #1 — onUnmounted: pulizia dell'intervallo se il componente viene smontato
// durante un upload attivo, prevenendo memory leak.
onUnmounted(() => {
  if (progressInterval) {
    clearInterval(progressInterval)
    progressInterval = null
  }
})

const selectedFileSummary = computed(() => {
  if (selectedFiles.value.length === 0) return null
  if (selectedFiles.value.length === 1) return selectedFiles.value[0]?.name ?? 'File selezionato'
  return `${selectedFiles.value.length} file selezionati`
})

const totalSelectedSize = computed(() => {
  return selectedFiles.value.reduce((sum, file) => sum + file.size, 0)
})

// FIX #2 — Validazione lato client: dimensione massima 50 MB per file.
// Restituisce il messaggio di errore o null se tutti i file sono validi.
function validateFiles(files: File[]): string | null {
  const MAX_SIZE = 50 * 1024 * 1024
  for (const file of files) {
    if (file.size > MAX_SIZE) {
      return `Il file "${file.name}" supera il limite di 50 MB.`
    }
  }
  return null
}

function onFileSelected(event: Event) {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    selectedFiles.value = Array.from(target.files)
    resetMessages()
  }
}

function onDrop(event: DragEvent) {
  isDragging.value = false
  if (event.dataTransfer && event.dataTransfer.files.length > 0) {
    selectedFiles.value = Array.from(event.dataTransfer.files)
    resetMessages()
  }
}

function resetMessages() {
  errorMessage.value = null
  successMessage.value = null
  uploadDetails.value = null
  uploadProgress.value = 0
  if (progressInterval) {
    clearInterval(progressInterval)
    progressInterval = null
  }
}

function clearSelection() {
  selectedFiles.value = []
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
  resetMessages()
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  const kb = bytes / 1024
  if (kb < 1024) return kb.toFixed(1) + ' KB'
  const mb = kb / 1024
  return mb.toFixed(2) + ' MB'
}

function startVisualProgress() {
  uploadProgress.value = 10
  progressInterval = setInterval(() => {
    // FIX #3 — Clamp applicato PRIMA dell'assegnazione per evitare
    // che la progress bar retroceda visivamente dopo aver superato 85.
    const next = uploadProgress.value + Math.floor(Math.random() * 12) + 5
    uploadProgress.value = Math.min(next, 85)
  }, 200)
}

function completeVisualProgress() {
  if (progressInterval) {
    clearInterval(progressInterval)
    progressInterval = null
  }
  uploadProgress.value = 100
}

async function handleUpload() {
  if (!selectedFiles.value.length) {
    errorMessage.value = 'Seleziona almeno un documento da caricare.'
    return
  }

  // FIX #2 (applicazione) — Validazione eseguita prima di procedere con l'upload.
  const validationError = validateFiles(selectedFiles.value)
  if (validationError) {
    errorMessage.value = validationError
    return
  }

  isLoading.value = true
  resetMessages()
  startVisualProgress()

  try {
    const result = await uploadDocument(selectedFiles.value)
    completeVisualProgress()
    uploadDetails.value = result
    successMessage.value = result.message || `${selectedFiles.value.length} file indicizzati con successo!`
    emit('uploaded', result)
  } catch (err) {
    if (progressInterval) {
      clearInterval(progressInterval)
      progressInterval = null
    }
    uploadProgress.value = 0
    errorMessage.value = err instanceof Error ? err.message : 'Si è verificato un errore durante il caricamento.'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-6 sm:p-7">
    <!-- Header -->
    <div class="mb-5 flex items-center justify-between">
      <div>
        <h2 class="text-lg font-semibold text-slate-900 flex items-center gap-2">
          <svg class="w-5 h-5 text-indigo-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
          </svg>
          Carica Documenti
        </h2>
        <p class="text-xs text-slate-500 mt-0.5">
          Alimenta il dataset RAGFlow per arricchire la conoscenza di historyGenie.
        </p>
      </div>

      <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-slate-100 text-slate-600 border border-slate-200">
        RAG Dataset
      </span>
    </div>

    <!-- Drag and Drop Box -->
    <div
        class="relative border-dashed border-2 border-slate-300 rounded-xl p-7 text-center transition-all cursor-pointer bg-slate-50/60 hover:bg-slate-50 hover:border-indigo-400"
        :class="{
        'border-indigo-500 bg-indigo-50/60 scale-[0.99]': isDragging,
        'opacity-70 pointer-events-none': isLoading
      }"
        @dragover.prevent="isDragging = true"
        @dragleave.prevent="isDragging = false"
        @drop.prevent="onDrop"
        @click="fileInputRef?.click()"
    >
      <!-- FIX #4 — Aggiunto attributo `accept` per filtrare i tipi di file
           già nel file picker del sistema operativo, coerente con quanto
           dichiarato nell'UI. -->
      <input
          ref="fileInputRef"
          type="file"
          class="hidden"
          multiple
          accept=".pdf,.docx,.txt,.md,.csv"
          @change="onFileSelected"
      />

      <div class="flex flex-col items-center justify-center gap-3">
        <!-- Icona stato / caricamento -->
        <div
            class="w-12 h-12 rounded-xl flex items-center justify-center transition-colors"
            :class="isLoading ? 'bg-indigo-600 text-white animate-pulse' : 'bg-indigo-50 text-indigo-600'"
        >
          <svg
              v-if="isLoading"
              class="w-6 h-6 animate-spin"
              fill="none"
              viewBox="0 0 24 24"
          >
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          <svg
              v-else
              class="w-6 h-6"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
          </svg>
        </div>

        <div v-if="!selectedFiles.length">
          <p class="text-sm font-medium text-slate-700">
            <span class="text-indigo-600 font-semibold hover:underline">Scegli i file</span> o trascinali qui
          </p>
          <p class="text-[11px] text-slate-400 mt-1">
            Formati accettati: PDF, DOCX, TXT, MD, CSV (max 50 MB per file)
          </p>
        </div>

        <div v-else class="flex flex-col items-center gap-2">
          <div class="inline-flex items-center gap-2 px-3 py-1 bg-white border border-slate-200 rounded-lg shadow-xs">
            <svg class="w-4 h-4 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <span class="text-xs font-medium text-slate-800 truncate max-w-[220px] sm:max-w-xs">{{ selectedFileSummary }}</span>
            <span class="text-[10px] text-slate-400 font-mono">({{ formatFileSize(totalSelectedSize) }})</span>
          </div>
          <div class="max-w-[260px] max-h-[120px] overflow-auto text-left space-y-1">
            <!-- FIX #5 — Chiave v-for resa univoca tramite crypto.randomUUID()
                 wrappando i file in oggetti con id stabile al momento della selezione.
                 In alternativa semplice, l'indice è accettabile se i file non vengono
                 riordinati. Qui usiamo l'indice come fallback sicuro dato che
                 selectedFiles non viene mai riordinato internamente. -->
            <div
                v-for="(file, index) in selectedFiles"
                :key="index"
                class="flex items-center justify-between gap-2 rounded-md bg-slate-100 px-2 py-1 text-[10px] text-slate-600"
            >
              <span class="truncate">{{ file.name }}</span>
              <span class="font-mono text-slate-400">{{ formatFileSize(file.size) }}</span>
            </div>
          </div>
          <span class="text-[11px] text-slate-400 mt-0.5">Clicca per sostituire i file</span>
        </div>
      </div>
    </div>

    <!-- Stato di Progresso Visivo -->
    <div v-if="isLoading" class="mt-4 p-3.5 bg-slate-50 border border-slate-200 rounded-xl space-y-2">
      <div class="flex justify-between items-center text-xs">
        <span class="font-medium text-slate-700 flex items-center gap-1.5">
          <span class="w-2 h-2 rounded-full bg-indigo-600 animate-ping"></span>
          Ingestione e indicizzazione documento in corso...
        </span>
        <span class="font-mono text-indigo-600 font-semibold text-[11px]">{{ uploadProgress }}%</span>
      </div>
      <div class="w-full bg-slate-200 rounded-full h-2 overflow-hidden">
        <div
            class="bg-indigo-600 h-2 rounded-full transition-all duration-200 ease-out"
            :style="{ width: `${uploadProgress}%` }"
        ></div>
      </div>
      <p class="text-[11px] text-slate-500 text-center">
        Invio del file a RAGFlow per il parsing e la creazione dei chunk vettoriali.
      </p>
    </div>

    <!-- Toolbar Azioni -->
    <div class="mt-5 flex items-center justify-between gap-3">
      <button
          v-if="selectedFiles.length"
          type="button"
          class="text-xs text-slate-500 hover:text-rose-600 transition-colors cursor-pointer"
          :disabled="isLoading"
          @click="clearSelection"
      >
        Rimuovi file
      </button>
      <div v-else class="text-xs text-slate-400">Nessun file pronto</div>

      <button
          type="button"
          class="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 disabled:opacity-50 disabled:cursor-not-allowed shadow-sm transition-all cursor-pointer"
          :disabled="!selectedFiles.length || isLoading"
          @click="handleUpload"
      >
        <svg
            v-if="isLoading"
            class="animate-spin -ml-0.5 h-3.5 w-3.5 text-white"
            fill="none"
            viewBox="0 0 24 24"
        >
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        <span v-if="isLoading">Invio in corso...</span>
        <span v-else>Carica su RAGFlow</span>
      </button>
    </div>

    <!-- Messaggio di Successo -->
    <div
        v-if="successMessage"
        class="mt-4 p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs flex items-start gap-2.5"
    >
      <svg class="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
      </svg>
      <div class="flex-1">
        <p class="font-semibold">{{ successMessage }}</p>
        <p v-if="uploadDetails?.fileName" class="text-[11px] text-emerald-700 mt-0.5">
          File: <code class="font-mono bg-emerald-100 px-1 py-0.2 rounded">{{ uploadDetails.fileName }}</code>
        </p>
      </div>
    </div>

    <!-- Messaggio di Errore -->
    <div
        v-if="errorMessage"
        class="mt-4 p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-900 text-xs flex items-start gap-2.5"
    >
      <svg class="w-4 h-4 text-rose-600 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <div class="flex-1">
        <p class="font-semibold">Errore durante l'operazione</p>
        <p class="text-[11px] text-rose-700 mt-0.5">{{ errorMessage }}</p>
      </div>
    </div>
  </div>
</template>