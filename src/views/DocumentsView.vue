<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import DocumentUpload from '../components/DocumentUpload.vue'
import { getDocuments, getDocumentPreview } from '../services/api'


interface DocumentItem {
  id: string
  originalName: string
  fileType: string
  fileSizeBytes: number
  pageCount: number
  status: string
  progress: number
  createdAt: string
  createdBy: string | null
  displayName: string
  description: string
  historicalDate: string
  author: string
  archiveSource: string
}

const previewUrls = ref<Record<string, string>>({})
const documents = ref<DocumentItem[]>([])
const loading = ref(true)
const error = ref('')

async function loadPreviews() {
  // Parallelizzazione: le richieste sono indipendenti
  await Promise.allSettled(
      documents.value.map(async (doc) => {
        try {
          previewUrls.value[doc.id] = await getDocumentPreview(doc.id)
        } catch (err) {
          console.error(`Errore anteprima documento ${doc.id}:`, err)
        }
      })
  )
}

async function loadDocuments() {
  try {
    loading.value = true
    error.value = ''
    documents.value = await getDocuments()
  } catch (err) {
    error.value = 'Impossibile recuperare i documenti.'
    console.error(err)
    return
  } finally {
    loading.value = false
  }
  // Dopo la lista: le anteprime non bloccano la visualizzazione
  loadPreviews()
}

onMounted(loadDocuments)

// Solo se getDocumentPreview restituisce URL.createObjectURL(blob)
onBeforeUnmount(() => {
  Object.values(previewUrls.value).forEach((url) => {
    if (url.startsWith('blob:')) URL.revokeObjectURL(url)
  })
})
</script>
<template>
  <div class="space-y-8">
    <section class="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
      <div>
        <p class="text-xs font-semibold uppercase tracking-[0.16em] text-blue-700">
          Spazio di lavoro
        </p>

        <h1 class="mt-2 text-3xl font-semibold tracking-tight text-[#102a43]">
          I miei documenti
        </h1>

        <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
          Carica e prepara i documenti storici per la ricerca semantica e l'interrogazione.
        </p>
      </div>

      <span
          class="inline-flex w-fit items-center rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500"
      >
        Archivio personale
      </span>
    </section>

    <DocumentUpload />

    <!-- Caricamento -->
    <section
        v-if="loading"
        class="border border-slate-200 bg-white px-5 py-10 text-center shadow-sm sm:px-8"
    >
      <p class="text-sm text-slate-500">
        Caricamento dei documenti...
      </p>
    </section>

    <!-- Errore -->
    <section
        v-else-if="error"
        class="border border-red-200 bg-white px-5 py-10 text-center shadow-sm sm:px-8"
    >
      <p class="text-sm text-red-600">
        {{ error }}
      </p>
    </section>

    <!-- Lista documenti -->
    <section
        v-else-if="documents.length > 0"
        class="space-y-4"
    >
      <div class="flex items-center justify-between">
        <h2 class="text-base font-semibold text-slate-800">
          Documenti caricati
        </h2>

        <span class="text-sm text-slate-500">
          {{ documents.length }} documenti
        </span>
      </div>

      <div class="grid gap-4 md:grid-cols-2">
        <article
            v-for="document in documents"
            :key="document.id"
            class="border border-slate-200 bg-white p-5 shadow-sm hover:cursor-pointer hover:border-green-600 hover:ring-1 hover:ring-blue-600/10"
            @click="() => $router.push({ path: `/documents/${document.id}` })"

        >
          <img
              :src="previewUrls[document.id]"
              :alt="`Anteprima di ${document.displayName }`"
              class="w-full h-48 object-cover rounded-t-xl"
          />
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0">
              <h3 class="truncate font-semibold text-slate-800">
                {{ document.displayName }}
              </h3>

              <p class="mt-1 text-xs text-slate-500">
                {{ document.pageCount }} pagine
              </p>
            </div>

            <span
                class="shrink-0 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600"
            >
              {{ document.status }}
            </span>
          </div>

          <div class="mt-5">
            <div class="flex justify-between text-xs text-slate-500">

            </div>


          </div>

          <div class="mt-4 text-xs text-slate-400">
            Caricato il
            {{ new Date(document.createdAt).toLocaleDateString('it-IT') }}
          </div>
          <div class="mt-2 text-xs text-slate-400">
            Creato da {{ document.createdBy }}
          </div>
        </article>
      </div>
    </section>

    <!-- Nessun documento -->
    <section
        v-else
        class="border border-slate-200 bg-white px-5 py-10 text-center shadow-sm sm:px-8"
    >
      <div
          class="mx-auto flex h-11 w-11 items-center justify-center rounded-lg bg-[#f3eee6] text-[#9b8060]"
      >
        <svg
            class="h-5 w-5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
            aria-hidden="true"
        >
          <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M6 4h9l3 3v13H6V4Zm9 0v4h3M9 13h6m-6 3h4"
          />
        </svg>
      </div>

      <h2 class="mt-4 text-base font-semibold text-slate-800">
        Non hai ancora caricato documenti
      </h2>

      <p class="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
        I documenti elaborati appariranno qui e saranno pronti per essere consultati.
      </p>
    </section>
  </div>
</template>
