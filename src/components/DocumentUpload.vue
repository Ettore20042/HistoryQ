
<script setup lang="ts">

import { uploadDocument, getDocumentStatus } from '../services/api'
import { ref } from 'vue'

const selectedFiles = ref<File[]>([])
const uploadProgress = ref<number>(0)
const uploadStatus = ref<string>('')

function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement

  if (input.files) {
    selectedFiles.value = Array.from(input.files)
  }
}

async function handleUpload() {
  if (selectedFiles.value.length > 0) {
    const document = await uploadDocument(selectedFiles.value)
    const id = document.id

    await pollDocumentStatus(id)

    selectedFiles.value = []
    uploadStatus.value = ''
    uploadProgress.value = 0
  } else {
    console.log('No file selected for upload.')
  }
}

function pollDocumentStatus(id: string) {
  return new Promise<void>((resolve, reject) => {
    const interval = setInterval(async () => {
      try {
        const statusResponse = await getDocumentStatus(id)

        uploadStatus.value = statusResponse.status
        uploadProgress.value = statusResponse.progress

        if (statusResponse.status === 'READY') {
          clearInterval(interval)
          resolve()
        } else if (statusResponse.status === 'ERROR') {
          clearInterval(interval)
          reject(new Error('Document processing failed.'))
        }
      } catch (error) {
        clearInterval(interval)
        reject(error)
      }
    }, 1000)
  })
}

</script>

<template>
  <section class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
    <div class="border-b border-slate-100 px-5 py-5 sm:px-8">
      <div class="flex items-start gap-4">
        <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-700">
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 16V4m0 0 4 4m-4-4L8 8m-5 5v4a3 3 0 0 0 3 3h12a3 3 0 0 0 3-3v-4" />
          </svg>
        </div>
        <div>
          <p class="text-xs font-semibold uppercase tracking-widest text-blue-700">Archivio HistoryQ</p>
          <h2 class="mt-1 text-xl font-semibold tracking-tight text-slate-900">
            Carica documento
          </h2>
          <p class="mt-1 text-sm leading-6 text-slate-500">
            Aggiungi una o più immagini per iniziare l'elaborazione del documento storico.
          </p>
        </div>
      </div>
    </div>

    <div class="space-y-6 px-5 py-6 sm:px-8 sm:py-8">
      <input
          id="document-file"
          type="file"
          accept=".jpg,.jpeg,.png,.tiff,.webp"
          @change="onFileSelected"
          class="hidden"
          multiple
      />

      <label
          for="document-file"
          class="group flex cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50/70 px-5 py-8 text-center transition-colors hover:border-blue-400 hover:bg-blue-50/40"
      >
        <span class="flex h-12 w-12 items-center justify-center rounded-full bg-white text-slate-500 shadow-sm ring-1 ring-slate-200 transition-colors group-hover:text-blue-700">
          <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 16V4m0 0 4 4m-4-4L8 8m-5 5v4a3 3 0 0 0 3 3h12a3 3 0 0 0 3-3v-4" />
          </svg>
        </span>
        <span class="mt-4 text-sm font-semibold text-slate-800">Seleziona i file da archiviare</span>
        <span class="mt-1 text-sm text-slate-500">JPG, PNG, TIFF o WEBP · selezione multipla disponibile</span>
        <span class="mt-5 rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm transition-colors group-hover:border-blue-200 group-hover:text-blue-700">
          Scegli file
        </span>
      </label>

      <div v-if="selectedFiles.length > 0" class="rounded-xl border border-slate-200 bg-white">
        <div class="flex items-center justify-between border-b border-slate-100 px-4 py-3">
          <p class="text-sm font-semibold text-slate-800">File selezionati</p>
          <span class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">
            {{ selectedFiles.length }} {{ selectedFiles.length === 1 ? 'file' : 'file' }}
          </span>
        </div>
        <ul class="divide-y divide-slate-100">
          <li
              v-for="file in selectedFiles"
              :key="file.name"
              class="flex items-center gap-3 px-4 py-3"
          >
            <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-[10px] font-bold uppercase text-blue-700">
              {{ file.name.split('.').pop() }}
            </span>
            <span class="min-w-0 flex-1 truncate text-sm font-medium text-slate-700">{{ file.name }}</span>
            <span class="text-xs text-slate-400">Immagine</span>
          </li>
        </ul>
      </div>

      <button
          type="button"
          @click="handleUpload"
          :class="selectedFiles.length > 0
            ? 'bg-blue-700 text-white shadow-sm hover:bg-blue-800 focus:ring-blue-500'
            : 'cursor-not-allowed bg-slate-100 text-slate-400'"
          class="flex w-full items-center justify-center gap-2 rounded-lg px-4 py-3 text-sm font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 sm:w-auto"
      >
        <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 16V4m0 0 4 4m-4-4L8 8m-5 5v4a3 3 0 0 0 3 3h12a3 3 0 0 0 3-3v-4" />
        </svg>
        <span>Carica Documento</span>
      </button>

      <div v-if="uploadStatus" class="rounded-xl border border-slate-200 bg-slate-50/70 p-4 sm:p-5">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <span
                class="h-2 w-2 rounded-full"
                :class="uploadStatus === 'ERROR' ? 'bg-red-500' : uploadStatus === 'READY' ? 'bg-emerald-500' : 'bg-blue-600'"
            ></span>
            <span v-if="uploadStatus === 'PROCESSING'" class="text-sm font-semibold text-slate-800">Elaborazione documento</span>
            <span v-else-if="uploadStatus === 'OCR_PROCESSING'" class="text-sm font-semibold text-slate-800">Riconoscimento del testo</span>
            <span v-else-if="uploadStatus === 'OCR_COMPLETED'" class="text-sm font-semibold text-slate-800">Testo riconosciuto</span>
            <span v-else-if="uploadStatus === 'RAGFLOW_PROCESSING'" class="text-sm font-semibold text-slate-800">Indicizzazione documento</span>
            <span v-else-if="uploadStatus === 'READY'" class="text-sm font-semibold text-emerald-700">Documento pronto</span>
            <span v-else-if="uploadStatus === 'ERROR'" class="text-sm font-semibold text-red-700">Elaborazione non riuscita</span>
            <span v-else class="text-sm font-semibold text-slate-800">{{ uploadStatus }}</span>
          </div>
          <span class="text-sm font-semibold text-slate-700">{{ uploadProgress }}%</span>
        </div>
        <div class="mt-3 h-2 w-full overflow-hidden rounded-full bg-slate-200">
          <div
              class="h-full rounded-full bg-blue-600 transition-all duration-500"
              :class="{ 'bg-emerald-500': uploadStatus === 'READY', 'bg-red-500': uploadStatus === 'ERROR' }"
              :style="{ width: uploadProgress + '%' }"
          ></div>
        </div>
        <p class="mt-2 text-xs text-slate-500">
          <span v-if="uploadStatus === 'ERROR'">Controlla il documento e riprova.</span>
          <span v-else-if="uploadProgress === 100">Caricamento completato</span>
          <span v-else>Il documento è in elaborazione, non chiudere questa pagina.</span>
        </p>
      </div>
    </div>
  </section>
</template>
