
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getDocuments,
  getDocumentPages,
  getDocumentPage,
  getDocumentStatus,
  getPageTranscription,
  updatePageTranscription,
  getPublicDocument,
  getPublicDocumentPage,
  getPublicDocumentPages,
  getPublicPageTranscription,
  getTeamDocument,
  getTeamDocumentPages, getTeamDocumentPage, getTeamPageTranscription, type TeamDocumentResponse, downloadTeamDocument,reprocessDocument, type OcrEngine,
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
const teamId = computed(() => typeof route.query.teamId === 'string' ? route.query.teamId : '')
const isOwner = ref(false)
const document = ref<DocumentItem | null>(null)

const loading = ref(true)
const error = ref('')

const pageNames = ref<string[]>([])
const currentPageIndex = ref(0)
const currentPageName = computed(
    () => pageNames.value[currentPageIndex.value] ?? ''
)
const pageInput = ref('1')
const pageUrls = ref<Record<string, string>>({})
const pageLoading = ref(false)
const pageError = ref('')

const pageTranscription = ref('')
const savingPageTranscription = ref(false)
const pageTranscriptionLoading = ref(false)
const pageTranscriptionError = ref('')
const pageTranscriptionMessage = ref('')
const selectedOcrEngine = ref<OcrEngine>('tesseract')
const reprocessing = ref(false)
const reprocessError = ref('')



const statusTimer = ref<ReturnType<typeof setTimeout> | null>(null)
let componentUnmounted = false

async function loadDocument() {
  loading.value = true
  error.value = ''
  pageError.value = ''
  isOwner.value = false

  try {
    let foundDocument: DocumentItem | null = null

    if (teamId.value) {
      foundDocument = await getTeamDocument(teamId.value, documentId.value) as DocumentItem
      isOwner.value = false
    }

    // Prima cerchiamo tra i documenti dell'utente autenticato.
    try {
      const documents = foundDocument ? [] : await getDocuments()
      foundDocument =
          documents.find(item => item.id === documentId.value) ?? null

      if (foundDocument) {
        isOwner.value = true
      }
    } catch {
      // L'utente potrebbe non essere autenticato.
    }

    // Se non è un documento personale, proviamo con l'archivio pubblico.
    if (!foundDocument) {
      try {
        foundDocument = await getPublicDocument(documentId.value)
        isOwner.value = false
      } catch {
        throw new Error('Documento non trovato o non accessibile.')
      }
    }

    if (!foundDocument) {
      throw new Error('Documento non trovato o non accessibile.')
    }

    document.value = foundDocument

    // Il proprietario usa le API private; il visitatore quelle pubbliche.
    try {
      pageNames.value = teamId.value
          ? await getTeamDocumentPages(teamId.value, documentId.value)
          : isOwner.value
              ? await getDocumentPages(documentId.value)
          : await getPublicDocumentPages(documentId.value)

      console.log('Pagine disponibili:', pageNames.value)

      if (pageNames.value.length > 0) {
        await loadPage(0)
      } else {
        pageError.value = 'Il documento non contiene immagini.'
      }
    } catch (err) {
      console.error('Errore nel caricamento delle pagine:', err)
      pageError.value = 'Impossibile recuperare le pagine del documento.'
    }

    // Lo stato viene aggiornato periodicamente solo per il proprietario.
    if (isOwner.value) {
      void pollDocumentStatus()
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

async function pollDocumentStatus(): Promise<void> {
  if (componentUnmounted || !document.value || !isOwner.value) return

  try {
    const result = await getDocumentStatus(documentId.value)

    if (document.value) {
      document.value.status = result.status
      document.value.progress = result.progress
    }

    if (result.status === 'READY' || result.status === 'ERROR') {
      return
    }
  } catch (err) {
    if (err instanceof Error && (err.message.includes('401') || err.message.includes('403'))) {
      return
    }
    console.error(
        'Errore nel recupero dello stato del documento:',
        err
    )
  }

  if (!componentUnmounted && isOwner.value) {
    statusTimer.value = setTimeout(() => {
      void pollDocumentStatus()
    }, 1500)
  }
}

async function reprocessCurrentDocument() {
  if (!isOwner.value || !document.value || teamId.value) {
    return
  }

  if (reprocessing.value) {
    return
  }

  reprocessing.value = true
  reprocessError.value = ''

  try {
    const result = await reprocessDocument(
        documentId.value,
        selectedOcrEngine.value
    )

    // Apri il nuovo documento senza modificare quello originale.
    const newPath = route.path.replace(
        documentId.value,
        result.documentId
    )

    await router.push({
      path: newPath,
      query: {},
    })
  } catch (err) {
    reprocessError.value =
        err instanceof Error
            ? err.message
            : 'Errore durante la rielaborazione del documento.'
  } finally {
    reprocessing.value = false
  }
}

async function loadPage(index: number) {
  if (index < 0 || index >= pageNames.value.length) return

  const filename = pageNames.value[index]
  if (!filename) return

  currentPageIndex.value = index
  pageInput.value = String(index + 1)
  pageError.value = ''

  await loadPageTranscription(filename)

  if (!pageUrls.value[filename]) {
    pageLoading.value = true

    try {
      const url = teamId.value
          ? await getTeamDocumentPage(teamId.value, documentId.value, filename)
          : isOwner.value
              ? await getDocumentPage(documentId.value, filename)
          : await getPublicDocumentPage(documentId.value, filename)

      pageUrls.value[filename] = url
    } catch (err) {
      pageError.value =
          err instanceof Error
              ? err.message
              : 'Impossibile caricare la pagina.'
    } finally {
      pageLoading.value = false
    }
  }

  void preloadPage(index + 1)
}

async function preloadPage(index: number) {
  if (index < 0 || index >= pageNames.value.length) return

  const filename = pageNames.value[index]
  if (!filename || pageUrls.value[filename]) return

  try {
    const url = isOwner.value
        ? await getDocumentPage(documentId.value, filename)
        : await getPublicDocumentPage(documentId.value, filename)

    pageUrls.value[filename] = url
  } catch {
    // Il precaricamento è facoltativo.
  }
}

function previousPage() {
  void loadPage(currentPageIndex.value - 1)
}

function nextPage() {
  void loadPage(currentPageIndex.value + 1)
}

function goBack() {
  router.back()
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

async function loadPageTranscription(filename: string) {
  pageTranscriptionLoading.value = true
  pageTranscriptionError.value = ''
  pageTranscriptionMessage.value = ''
  pageTranscription.value = ''

  try {
    const response = teamId.value
        ? await getTeamPageTranscription(teamId.value, documentId.value, filename)
        : isOwner.value
            ? await getPageTranscription(documentId.value, filename)
        : await getPublicPageTranscription(documentId.value, filename)

    pageTranscription.value = response.ocrText ?? ''
  } catch (err) {
    pageTranscriptionError.value =
        err instanceof Error
            ? err.message
            : 'Errore durante il caricamento della trascrizione.'
  } finally {
    pageTranscriptionLoading.value = false
  }
}
const sharedDocumentsError = ref('')
async function downloadSharedDocument(
    documentId: string,
    displayName: string
) {
  const currentTeamId = teamId.value

  if (!currentTeamId) return

  try {
    await downloadTeamDocument(currentTeamId, documentId, displayName)
  } catch (error) {
    sharedDocumentsError.value =
        error instanceof Error
            ? error.message
            : 'Impossibile scaricare il documento.'
  }
}


async function savePageTranscription() {
  // Controllo aggiuntivo lato frontend.
  if (!isOwner.value) return

  const filename = currentPageName.value
  if (!filename) return

  savingPageTranscription.value = true
  pageTranscriptionError.value = ''
  pageTranscriptionMessage.value = ''

  try {
    const response = await updatePageTranscription(
        documentId.value,
        filename,
        pageTranscription.value
    )

    pageTranscription.value = response.ocrText ?? ''
    pageTranscriptionMessage.value =
        'Trascrizione salvata correttamente.'
  } catch (err) {
    pageTranscriptionError.value =
        err instanceof Error
            ? err.message
            : 'Errore durante il salvataggio della trascrizione.'
  } finally {
    savingPageTranscription.value = false
  }
}

function goToCitationPage(filename: string) {
  const pageIndex = pageNames.value.indexOf(filename)

  if (pageIndex >= 0) {
    void loadPage(pageIndex)
  }
}

onMounted(() => {
  void loadDocument()
})

watch(documentId, () => {
  if (statusTimer.value !== null) {
    clearTimeout(statusTimer.value)
    statusTimer.value = null
  }
  for (const url of Object.values(pageUrls.value)) URL.revokeObjectURL(url)
  pageUrls.value = {}
  pageNames.value = []
  currentPageIndex.value = 0
  pageTranscription.value = ''
  void loadDocument()
})

onBeforeUnmount(() => {
  componentUnmounted = true

  if (statusTimer.value !== null) {
    clearTimeout(statusTimer.value)
    statusTimer.value = null
  }

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

      <span class="text-sm text-slate-500">HistoryQ</span>
    </header>

    <section class="mb-6">
      <div
          v-if="loading"
          class="rounded-lg border border-slate-200 bg-white p-5 text-slate-600"
      >
        Caricamento del documento...
      </div>

      <div
          v-else-if="error"
          class="rounded-lg border border-red-200 bg-red-50 p-5 text-red-700"
      >
        {{ error }}
      </div>

      <div
          v-else-if="document"
          class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <div class="min-w-0">
          <h1 class="text-4xl font-bold text-slate-900">
            {{ document.displayName || document.originalName }}
          </h1>

          <p class="mt-1 text-sm text-slate-500">
            {{ document.pageCount }} pagine
            <span v-if="document.author"> · {{ document.author }}</span>
            <span v-if="document.historicalDate">
              · {{ document.historicalDate }}
            </span>
          </p>

          <p
              v-if="document.description"
              class="mt-2 text-sm text-slate-600 line-clamp-3"
          >
            {{ document.description }}
          </p>
        </div>

        <div class="flex flex-col items-end gap-2 self-start sm:self-center">
          <span
              class="rounded-full bg-indigo-100 px-3 py-1 text-sm font-medium text-indigo-700"
          >
            {{ document.status }}
          </span>

          <button
              v-if="teamId && document"
              type="button"
              class="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
              @click="
                downloadSharedDocument(
                  document.id,
                  document.displayName || document.originalName
                )
              "
          >
            Scarica
          </button>
        </div>
      </div>

    </section>







    <section
        v-if="!loading && !error && document"
        class="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1.1fr)_minmax(360px,0.9fr)]"
    >
      <!-- Colonna sinistra: documento e trascrizione -->
      <div class="overflow-hidden rounded-2xl border border-[#d9cdbd] bg-[#fbf9f5] shadow-sm">
        <div
            class="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-4"
        >
          <h2 class="font-semibold text-slate-800">
            Visualizzatore del documento
          </h2>

          <span class="text-sm text-slate-500">
            Pagina {{ pageNames.length > 0 ? currentPageIndex + 1 : 0 }}
            di {{ pageNames.length }}
          </span>
        </div>

        <div class="flex min-h-80 items-center justify-center bg-[#eee8df] p-4">
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

        <div
            class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-4 py-3"
        >
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
              :disabled="
              currentPageIndex >= pageNames.length - 1 ||
              pageLoading ||
              pageNames.length === 0
            "
              @click="nextPage"
          >
            Successiva →
          </button>
        </div>

        <div class="border-t border-slate-200 px-5 py-4">
          <div
              v-if="isOwner && !teamId"
              class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white p-3"
          >
            <label for="ocr-engine" class="text-sm text-slate-600">
              Motore OCR
            </label>

            <select
                id="ocr-engine"
                v-model="selectedOcrEngine"
                :disabled="reprocessing || document?.status === 'PROCESSING'"
                class="min-w-0 flex-1 rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm text-slate-800"
            >
              <option value="tesseract">Tesseract</option>
              <option value="churro">Churro 3B</option>
              <option value="lighton-1b">LightOnOCR 1B</option>
              <option value="lighton-4b">LightOnOCR 4B</option>
            </select>

            <button
                type="button"
                :disabled="reprocessing || document?.status === 'PROCESSING'"
                class="rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
                @click="reprocessCurrentDocument"
            >
              {{ reprocessing ? 'Avvio...' : 'Rielabora' }}
            </button>

            <p
                v-if="reprocessError"
                class="w-full text-xs text-red-600"
                role="alert"
            >
              {{ reprocessError }}
            </p>
          </div>
          <div class="mt-2 border-t border-slate-200 pt-5">
            <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
              <h3 class="text-lg font-semibold text-slate-900">
                Trascrizione della pagina {{ currentPageIndex + 1 }}
              </h3>

              <span
                  v-if="pageTranscriptionLoading"
                  class="text-sm text-slate-500"
              >
                Caricamento...
              </span>
            </div>

            <p
                v-if="pageTranscriptionError"
                class="mb-3 text-sm text-red-600"
                role="alert"
            >
              {{ pageTranscriptionError }}
            </p>

            <template v-else>
              <textarea
                  v-model="pageTranscription"
                  :disabled="
                  !isOwner ||
                  pageTranscriptionLoading ||
                  savingPageTranscription
                "
                  :placeholder="
                  isOwner
                    ? 'La trascrizione OCR della pagina apparirà qui...'
                    : 'Trascrizione OCR della pagina'
                "
                  rows="12"
                  class="w-full resize-y rounded-lg border border-slate-300 bg-white p-3 text-sm leading-6 text-slate-800 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60"
              ></textarea>

              <div class="mt-3 flex flex-wrap items-center gap-3">
                <button
                    v-if="isOwner"
                    type="button"
                    @click="savePageTranscription"
                    :disabled="
                    pageTranscriptionLoading ||
                    savingPageTranscription ||
                    !currentPageName
                  "
                    class="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {{
                    savingPageTranscription
                        ? 'Salvataggio...'
                        : 'Salva trascrizione'
                  }}
                </button>

                <span
                    v-if="pageTranscriptionMessage"
                    class="text-sm text-green-700"
                    role="status"
                >
                  {{ pageTranscriptionMessage }}
                </span>
              </div>
            </template>
          </div>
        </div>
      </div>

      <div class="min-w-0">
        <RagChat
            :public-document="!isOwner"
                :team-id="teamId || undefined"
            @citation-page="goToCitationPage"
        />
      </div>
    </section>
  </main>
</template>
