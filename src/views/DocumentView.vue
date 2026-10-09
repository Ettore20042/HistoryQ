
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getDocuments,
  getDocumentTranscription,
    getDocumentPages,
    getDocumentPage,
} from '@/services/api'
import RagChat from '@/components/RagChat.vue'

interface DocumentItem {
  id: string
  originalName: string
  displayName: string
  description: string
  fileType: string
  fileSizeBytes: number
  pageCount: number
  status: string
  progress: number
  createdAt: string
  createdBy: string | null
  historicalDate: string
  author: string
  archiveSource: string
}

const route = useRoute()
const router = useRouter()

const documentId = computed(() => String(route.params.id))

const document = ref<DocumentItem | null>(null)

const transcription = ref('')
const showTranscription = ref(false)

const loading = ref(true)

const error = ref('')
const transcriptionError = ref('')

const pageNames = ref<string[]>([])
const currentPageIndex = ref(0)
const currentPageName = computed(() => pageNames.value[currentPageIndex.value] ?? '')
const pageInput=ref('1')
const pageUrls=ref<Record<string,string>>({})
const pageLoading=ref(false)
const pageError=ref('')
/*
  Carica il documento e le informazioni correlate.
  Parallelizza le richieste di anteprima e trascrizione.
*/
async function loadDocument() {
  loading.value = true
  error.value = ''
  pageError.value = ''
  transcriptionError.value = ''

  try {
    const documents = await getDocuments()
    const foundDocument = documents.find(
        item => item.id === documentId.value
    )

    if (!foundDocument) {
      throw new Error('Documento non trovato o non accessibile.')
    }

    document.value = foundDocument
    const [pagesResult, transcriptionResult] = await Promise.allSettled([
      getDocumentPages(documentId.value),
      getDocumentTranscription(documentId.value),
    ])

    if (pagesResult.status === 'fulfilled') {
      pageNames.value = pagesResult.value
      console.log('Pagine disponibili in Silo:', pageNames.value)

      if (pageNames.value.length > 0) {
        await loadPage(0)
      } else {
        pageError.value = 'Il documento non contiene immagini.'
      }
    } else {
      pageError.value = 'Impossibile recuperare le pagine del documento.'
    }

    if (transcriptionResult.status === 'fulfilled') {
      transcription.value = transcriptionResult.value
    } else {
      transcriptionError.value = 'La trascrizione non è disponibile al momento.'
    }
  } catch (err) {
    error.value =
        err instanceof Error
            ? err.message
            : 'Impossibile caricare il documento.'
  } finally {
    loading.value = false
  }
}

async function loadPage(index: number) {
  if (index < 0 || index >= pageNames.value.length) return

  const filename = pageNames.value[index]

  if (!filename) return

  currentPageIndex.value = index
  pageInput.value = String(index + 1)
  pageError.value = ''

  if (pageUrls.value[filename]) return

  pageLoading.value = true

  try {
    const url = await getDocumentPage(documentId.value, filename)
    pageUrls.value[filename] = url
  } catch (err) {
    pageError.value =
        err instanceof Error
            ? err.message
            : 'Impossibile caricare la pagina.'
  } finally {
    pageLoading.value = false
  }
  void preloadPage(index + 1)
}
async function preloadPage(index: number) {
  if (index < 0 || index >= pageNames.value.length) return

  const filename = pageNames.value[index]

  if (!filename || pageUrls.value[filename]) return

  try {
    const url = await getDocumentPage(documentId.value, filename)
    pageUrls.value[filename] = url
  } catch {
    // Il precaricamento è facoltativo:
    // un errore verrà gestito quando l'utente aprirà la pagina.
  }
}

function previousPage() {
  void loadPage(currentPageIndex.value - 1)
}

function nextPage() {
  void loadPage(currentPageIndex.value + 1)
}

function goBack() {
  router.push('/documents')
}
function goToPage() {
  const pageNumber = Number(pageInput.value)

  if (
      !Number.isInteger(pageNumber) ||
      pageNumber < 1 ||
      pageNumber > pageNames.value.length
  ) {
    pageError.value = `Inserisci un numero da 1 a ${pageNames.value.length}.`
    return
  }

  void loadPage(pageNumber - 1)
}

function goToCitationPage(filename: string) {
  const pageIndex = pageNames.value.indexOf(filename)
  if (pageIndex >= 0) {
    void loadPage(pageIndex)
  }
}

onMounted(loadDocument)


onBeforeUnmount(() => {
  for (const url of Object.values(pageUrls.value)) {
    URL.revokeObjectURL(url)
  }
})
</script>

<template>
  <main class="min-h-screen w-full bg-slate-50 p-3 sm:p-4 lg:p-5">
    <header class="mb-6 flex items-center justify-between gap-4">
      <button
          type="button"
          class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
          @click="goBack"
      >
        ← Torna ai documenti
      </button>

      <span class="text-sm text-slate-500">
        HistoryGenie
      </span>
    </header>

    <section class="mb-6">
      <div v-if="loading" class="rounded-lg border border-slate-200 bg-white p-5 text-slate-600">
        Caricamento del documento...
      </div>

      <div v-else-if="error" class="rounded-lg border border-red-200 bg-red-50 p-5 text-red-700">
        {{ error }}
      </div>

      <div v-else-if="document" class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div class="min-w-0">
          <h1 class="text-4xl font-bold text-slate-900 sm:text-4xl">
            {{ document.displayName || document.originalName }}
          </h1>

          <p class="mt-1 text-sm text-slate-500">
            {{ document.pageCount }} pagine
            <span v-if="document.author"> · {{ document.author }}</span>
            <span v-if="document.historicalDate"> · {{ document.historicalDate }}</span>
          </p>
          <p v-if="document.description" class="mt-2 text-1xl text-slate-600 line-clamp-3">
            {{ document.description }}
          </p>
        </div>

        <span class="self-start rounded-full bg-indigo-100 px-3 py-1 text-sm font-medium text-indigo-700 sm:self-auto">
          {{ document.status }}
        </span>
      </div>
    </section>

    <section
        v-if="!loading && !error && document"
        class="grid grid-cols-1 gap-6 lg:grid-cols-2"
    >
      <!-- Colonna sinistra: documento -->

      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div class="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
          <h2 class="font-semibold text-slate-800">
            Visualizzatore del documento
          </h2>

          <span class="text-sm text-slate-500">
      Pagina {{ pageNames.length > 0 ? currentPageIndex + 1 : 0 }}
      di {{ pageNames.length }}
    </span>
        </div>

        <div class="flex min-h-80 items-center justify-center bg-slate-100 p-4">
          <img
              v-if="pageNames.length > 0 && pageUrls[currentPageName]"
              :src="pageUrls[currentPageName]"
              :alt="`Pagina ${currentPageIndex + 1} del documento`"
              class="max-h-[650px] max-w-full rounded-md object-contain"
          />

          <p v-else-if="pageLoading" class="text-sm text-slate-500">
            Caricamento della pagina...
          </p>

          <p v-else-if="pageError" class="text-sm text-red-600">
            {{ pageError }}
          </p>

          <p v-else class="text-sm text-slate-500">
            Nessuna pagina disponibile.
          </p>
        </div>


        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-4 py-3">
          <button
              type="button"
              class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="currentPageIndex === 0 || pageLoading"
              @click="previousPage"
          >
            ← Precedente
          </button>

          <form class="flex items-center gap-2" @submit.prevent="goToPage">
            <label for="page-number" class="text-sm text-slate-600">
              Vai a:
            </label>

            <input
                id="page-number"
                v-model="pageInput"
                type="number"
                min="1"
                :max="pageNames.length"
                class="w-20 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800"
                :disabled="pageLoading || pageNames.length === 0"
            />

            <button
                type="submit"
                class="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-40"
                :disabled="pageLoading || pageNames.length === 0"
            >
              Vai
            </button>
          </form>

          <button
              type="button"
              class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="currentPageIndex >= pageNames.length - 1 || pageLoading || pageNames.length === 0"
              @click="nextPage"
          >
            Successiva →
          </button>
        </div>

        <!-- Qui inseriremo la trascrizione OCR nel prossimo passaggio -->
      </div>


      <!-- Colonna destra: chat AI -->
      <div class="min-w-0">
        <RagChat @citation-page="goToCitationPage" />
      </div>

    </section>
  </main>
</template>