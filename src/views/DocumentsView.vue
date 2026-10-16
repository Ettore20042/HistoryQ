<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import DocumentUpload from '../components/DocumentUpload.vue'
import { getDocuments, getDocumentPreview,toggleVisibility as updateVisibilityApi } from '../services/api'


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
  visibility: 'PUBLIC' | 'PRIVATE'
}

const previewUrls = ref<Record<string, string>>({})
const documents = ref<DocumentItem[]>([])
const loading = ref(true)
const error = ref('')
const visibilityLoading = ref('')
const visibilityError = ref('')
const router = useRouter()

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
    visibilityError.value = ''
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
async function refreshDocuments() {
  await loadDocuments()
}

async function toggleVisibility(document: DocumentItem) {
  try {
    visibilityLoading.value = document.id
    visibilityError.value = ''
    await updateVisibilityApi(document)
  } catch (err) {
    console.error(
        `Errore aggiornamento visibilità documento ${document.id}:`,
        err
    )
    visibilityError.value = 'Impossibile modificare la visibilità del documento.'
  } finally {
    visibilityLoading.value = ''
  }
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    READY: 'Pronto',
    PROCESSING: 'In elaborazione',
    OCR_COMPLETED: 'OCR completato',
    RAGFLOW_PROCESSING: 'Indicizzazione',
    ERROR: 'Errore',
  }

  return labels[status] ?? status
}

function statusClasses(status: string): string {
  if (status === 'READY') return 'bg-emerald-50 text-emerald-700 ring-emerald-600/20'
  if (status === 'ERROR') return 'bg-red-50 text-red-700 ring-red-600/20'
  return 'bg-amber-50 text-amber-700 ring-amber-600/20'
}

function visibilityClasses(document: DocumentItem): string {
  return document.visibility === 'PUBLIC'
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : 'border-red-200 bg-red-50 text-red-700'
}

function handleCardKeydown(event: KeyboardEvent, documentId: string) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    navigateToDocument(documentId)
  }
}

function navigateToDocument(documentId: string) {
  void router.push({ path: `/documents/${documentId}` })
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

    <DocumentUpload @uploaded="refreshDocuments" />

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
        class="rounded-2xl border border-red-200 bg-red-50 px-5 py-10 text-center shadow-sm sm:px-8"
    >
      <p class="text-sm font-medium text-red-700">
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

      <p
          v-if="visibilityError"
          class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
          role="alert"
      >
        {{ visibilityError }}
      </p>

      <div class="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        <article
            v-for="document in documents"
            :key="document.id"
            class="group overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-blue-300 hover:shadow-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2"
            role="link"
            tabindex="0"
            @click="navigateToDocument(document.id)"
            @keydown="handleCardKeydown($event, document.id)"
        >
          <div class="relative h-48 overflow-hidden bg-slate-100">
            <img
                v-if="previewUrls[document.id]"
                :src="previewUrls[document.id]"
                :alt="`Anteprima di ${document.displayName}`"
                class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
            />
            <div
                v-else
                class="flex h-full items-center justify-center text-slate-400"
                aria-label="Anteprima non disponibile"
            >
              <svg class="h-12 w-12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 3.75h8.25L18 7.5v12.75H6V3.75Zm8.25 0V7.5H18M8.5 11h7M8.5 14h7M8.5 17h4" />
              </svg>
            </div>
            <span
                class="absolute left-3 top-3 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold shadow-sm ring-1 ring-inset"
                :class="statusClasses(document.status)"
            >
              <span class="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true"></span>
              {{ statusLabel(document.status) }}
            </span>
          </div>

          <div class="space-y-4 p-5">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <h3 class="truncate text-base font-semibold text-slate-900">
                  {{ document.displayName }}
                </h3>
                <p class="mt-1 text-xs text-slate-500">
                  {{ document.pageCount }} {{ document.pageCount === 1 ? 'pagina' : 'pagine' }}
                  <span v-if="document.author"> · {{ document.author }}</span>
                </p>
              </div>
              <span
                  class="shrink-0 rounded-full border px-2.5 py-1 text-[11px] font-semibold"
                  :class="visibilityClasses(document)"
              >
                {{ document.visibility === 'PUBLIC' ? 'Pubblico' : 'Privato' }}
              </span>
            </div>

            <p v-if="document.description" class="line-clamp-2 min-h-10 text-sm leading-5 text-slate-600">
              {{ document.description }}
            </p>
            <p v-else class="min-h-10 text-sm italic leading-5 text-slate-400">
              Nessuna descrizione
            </p>

            <div class="flex items-center justify-between gap-3 border-t border-slate-100 pt-4">
              <div class="text-xs text-slate-400">
                <p>Caricato il {{ new Date(document.createdAt).toLocaleDateString('it-IT') }}</p>
                <p v-if="document.createdBy" class="mt-1">Da {{ document.createdBy }}</p>
              </div>

              <button
                  type="button"
                  role="switch"
                  :aria-checked="document.visibility === 'PUBLIC'"
                  :aria-label="document.visibility === 'PUBLIC' ? 'Rendi il documento privato' : 'Rendi il documento pubblico'"
                  :disabled="visibilityLoading === document.id"
                  class="inline-flex shrink-0 items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition hover:shadow-sm disabled:cursor-wait disabled:opacity-60"
                  :class="document.visibility === 'PUBLIC'
                    ? 'border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
                    : 'border-red-200 bg-red-50 text-red-700 hover:bg-red-100'"
                  @click.stop="toggleVisibility(document)"
              >
                <span
                    class="relative h-4 w-7 rounded-full transition"
                    :class="document.visibility === 'PUBLIC' ? 'bg-emerald-500' : 'bg-red-300'"
                >
                  <span
                      class="absolute top-0.5 h-3 w-3 rounded-full bg-white shadow transition"
                      :class="document.visibility === 'PUBLIC' ? 'left-3.5' : 'left-0.5'"
                  ></span>
                </span>
                {{ visibilityLoading === document.id
                  ? 'Salvataggio...'
                  : document.visibility === 'PUBLIC' ? 'Pubblico' : 'Privato' }}
              </button>
            </div>
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
