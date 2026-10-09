
<script setup lang="ts">

import { uploadDocument, getDocumentStatus } from '../services/api'
import { ref } from 'vue'

const selectedFiles = ref<File[]>([])
const uploadProgress = ref<number>(0)
const uploadStatus = ref<string>('')
const description = ref<string>('')
const displayName = ref<string>('')
const historicalDate = ref<string>('')
const author = ref<string>('')
const archiveSource = ref<string>('')

function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement

  if (input.files) {
    selectedFiles.value = Array.from(input.files)
  }
}

async function handleUpload() {
  if (selectedFiles.value.length > 0) {
    if (!displayName.value || !description.value || !historicalDate.value || !author.value || !archiveSource.value) {
      console.error('All fields are required.')
      return
    }
    const document = await uploadDocument(selectedFiles.value, displayName.value, description.value,historicalDate.value,author.value,archiveSource.value)
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
  <section class="overflow-hidden rounded-3xl border border-slate-200/80 bg-white shadow-[0_18px_50px_-28px_rgba(15,23,42,0.35)]">
    <div class="border-b border-slate-100 bg-gradient-to-br from-green-50/80 via-white to-white px-5 py-6 sm:px-8">
      <div class="flex items-start gap-4">
        <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-green-700 text-white shadow-lg shadow-green-700/20">
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 16V4m0 0 4 4m-4-4L8 8m-5 5v4a3 3 0 0 0 3 3h12a3 3 0 0 0 3-3v-4" />
          </svg>
        </div>
        <div>
          <p class="text-xs font-bold uppercase tracking-[0.18em] text-green-700">Archivio HistoryQ</p>
          <h2 class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">
            Carica documento
          </h2>
          <p class="mt-2 max-w-xl text-sm leading-6 text-slate-500">
            Compila i dettagli e aggiungi le immagini del documento storico per iniziare l'elaborazione.
          </p>
        </div>
      </div>
    </div>

    <div class="space-y-7 px-5 py-6 sm:px-8 sm:py-8">
      <input
          id="document-file"
          type="file"
          accept=".jpg,.jpeg,.png,.tiff,.webp"
          @change="onFileSelected"
          class="hidden"
          multiple
      />
      <div class="grid gap-5 sm:grid-cols-2">
        <label class="space-y-2">
          <span class="text-sm font-semibold text-slate-700">Nome del documento</span>
          <input
              v-model="displayName"
              placeholder="Es. Lettera del 1850"
              class="w-full rounded-xl border border-slate-200 bg-slate-50/60 px-4 py-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-green-500 focus:bg-white focus:ring-4 focus:ring-green-500/10"
          />
        </label>

        <label class="space-y-2">
          <span class="text-sm font-semibold text-slate-700">Data storica</span>
          <input
              v-model="historicalDate"
              placeholder="Es. 1850"
              class="w-full rounded-xl border border-slate-200 bg-slate-50/60 px-4 py-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-green-500 focus:bg-white focus:ring-4 focus:ring-green-500/10"
          />
        </label>

        <label class="space-y-2">
          <span class="text-sm font-semibold text-slate-700">Autore</span>
          <input
              v-model="author"
              placeholder="Nome dell'autore"
              class="w-full rounded-xl border border-slate-200 bg-slate-50/60 px-4 py-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-green-500 focus:bg-white focus:ring-4 focus:ring-green-500/10"
          />
        </label>

        <label class="space-y-2">
          <span class="text-sm font-semibold text-slate-700">Fonte dell'archivio</span>
          <input
              v-model="archiveSource"
              placeholder="Es. Archivio comunale"
              class="w-full rounded-xl border border-slate-200 bg-slate-50/60 px-4 py-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-green-500 focus:bg-white focus:ring-4 focus:ring-green-500/10"
          />
        </label>

        <label class="space-y-2 sm:col-span-2">
          <span class="text-sm font-semibold text-slate-700">Descrizione</span>
          <textarea
              v-model="description"
              rows="3"
              placeholder="Aggiungi una breve descrizione del documento"
              class="w-full resize-y rounded-xl border border-slate-200 bg-slate-50/60 px-4 py-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-green-500 focus:bg-white focus:ring-4 focus:ring-green-500/10"
          ></textarea>
        </label>
      </div>

      <label
          for="document-file"
          class="group flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50/70 px-5 py-9 text-center transition-all hover:border-green-400 hover:bg-green-50/40"
      >
        <span class="flex h-14 w-14 items-center justify-center rounded-2xl bg-white text-green-600 shadow-sm ring-1 ring-slate-200 transition-all group-hover:-translate-y-0.5 group-hover:text-green-700">
          <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 16V4m0 0 4 4m-4-4L8 8m-5 5v4a3 3 0 0 0 3 3h12a3 3 0 0 0 3-3v-4" />
          </svg>
        </span>
        <span class="mt-4 text-base font-semibold text-slate-800">Seleziona i file da archiviare</span>
        <span class="mt-1 max-w-md text-sm leading-6 text-slate-500">JPG, PNG, TIFF o WEBP · puoi selezionare più immagini contemporaneamente</span>
        <span class="mt-5 rounded-xl bg-green-700 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all group-hover:bg-green-800 group-hover:shadow-md">
          Scegli file
        </span>
      </label>

      <div v-if="selectedFiles.length > 0" class="overflow-hidden rounded-2xl border border-green-100 bg-green-50/30">
        <div class="flex items-center justify-between border-b border-green-100 px-4 py-3">
          <p class="text-sm font-semibold text-slate-800">File selezionati</p>
          <span class="rounded-full bg-green-100 px-2.5 py-1 text-xs font-semibold text-green-700">
            {{ selectedFiles.length }} {{ selectedFiles.length === 1 ? 'file' : 'file' }}
          </span>
        </div>
        <ul class="divide-y divide-slate-100">
          <li
              v-for="file in selectedFiles"
              :key="file.name"
              class="flex items-center gap-3 border-white/70 px-4 py-3"
          >
            <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white text-[10px] font-bold uppercase text-green-700 shadow-sm ring-1 ring-green-100">
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
            ? 'bg-green-700 text-white shadow-lg shadow-green-700/20 hover:bg-green-800 focus:ring-green-500'
            : 'cursor-not-allowed bg-slate-100 text-slate-400'"
          class="flex w-full items-center justify-center gap-2 rounded-xl px-5 py-3.5 text-sm font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-offset-2 sm:w-auto"
      >
        <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 16V4m0 0 4 4m-4-4L8 8m-5 5v4a3 3 0 0 0 3 3h12a3 3 0 0 0 3-3v-4" />
        </svg>
        <span>Carica Documento</span>
      </button>

      <div v-if="uploadStatus" class="rounded-2xl border border-slate-200 bg-slate-50/70 p-4 sm:p-5">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <span
                class="h-2 w-2 rounded-full"
                :class="uploadStatus === 'ERROR' ? 'bg-red-500' : uploadStatus === 'READY' ? 'bg-emerald-500' : 'bg-green-600'"
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
              class="h-full rounded-full bg-green-600 transition-all duration-500"
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
