
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDocumentPages, getDocumentPage } from '@/services/api'
import { onBeforeUnmount } from 'vue'
import RagChat from '@/components/RagChat.vue'

interface Collection {
  id: string
  name: string
  description: string | null
  visibility: string
  createdAt: string
}

interface CollectionDocument {
  id: string
  displayName: string
  description: string
  originalName: string
  historicalDate: string | null
  author: string | null
  archiveSource: string | null
  pageCount: number
  documentStatus: string
  collectionStatus: string
  addedAt: string
}

const route = useRoute()
const router = useRouter()

const collection = ref<Collection | null>(null)
const documents = ref<CollectionDocument[]>([])
const loading = ref(true)
const error = ref('')
const teamId = typeof route.query.teamId === 'string' ? route.query.teamId : ''

async function loadCollection() {
  loading.value = true
  error.value = ''

  try {
    const token = localStorage.getItem('historyq_token')

    if (!token) {
      throw new Error('Sessione scaduta. Accedi nuovamente.')
    }

    const collectionId = String(route.params.id)
    const headers = {
      Authorization: `Bearer ${token}`,
    }

    const [collectionResponse, documentsResponse] = await Promise.all([
      fetch(`/api/collections/${collectionId}${teamId ? `?teamId=${teamId}` : ''}`, { headers }),
      fetch(`/api/collections/${collectionId}/documents${teamId ? `?teamId=${teamId}` : ''}`, { headers }),
    ])

    if (
        collectionResponse.status === 401 ||
        documentsResponse.status === 401
    ) {
      throw new Error('Sessione scaduta. Accedi nuovamente.')
    }

    if (!collectionResponse.ok || !documentsResponse.ok) {
      throw new Error('Impossibile caricare la collezione.')
    }

    collection.value = await collectionResponse.json() as Collection
    documents.value = await documentsResponse.json() as CollectionDocument[]
    await Promise.all(
        documents.value.map(document => loadCover(document.id)),
    )
  } catch (err) {
    error.value = err instanceof Error
        ? err.message
        : 'Si è verificato un errore.'
  } finally {
    loading.value = false
  }
}

function openDocument(documentId: string) {
  router.push(`/documents/${documentId}${teamId ? `?teamId=${teamId}` : ''}`)
}

function formatDate(value: string | null) {
  if (!value) return 'Non specificata'

  return value
}
const coverUrls = ref<Record<string, string>>({})
const coverErrors = ref<Record<string, boolean>>({})

async function loadCover(documentId: string) {
  if (coverUrls.value[documentId] || coverErrors.value[documentId]) return

  try {
    const pages = await getDocumentPages(documentId)
    const firstPage = pages[0]

    if (!firstPage) {
      coverErrors.value[documentId] = true
      return
    }

    coverUrls.value[documentId] = await getDocumentPage(
        documentId,
        firstPage,
    )
  } catch {
    coverErrors.value[documentId] = true
  }
}

onBeforeUnmount(() => {
  Object.values(coverUrls.value).forEach(URL.revokeObjectURL)
})

onMounted(loadCollection)
</script>

<template>
  <main class="mx-auto max-w-6xl px-4 py-8 sm:px-6">
    <button
        type="button"
        class="mb-6 text-sm font-medium text-blue-700 hover:underline"
        @click="router.push('/collections')"
    >
      ← Torna alle collezioni
    </button>

    <div v-if="loading" class="py-12 text-center text-slate-500">
      Caricamento collezione...
    </div>

    <div
        v-else-if="error"
        class="rounded-xl bg-red-50 p-4 text-red-700"
        role="alert"
    >
      {{ error }}
      <button
          type="button"
          class="ml-3 font-semibold underline"
          @click="loadCollection"
      >
        Riprova
      </button>
    </div>

    <template v-else-if="collection">
      <header class="mb-8">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 class="text-3xl font-bold text-slate-900">
              {{ collection.name }}
            </h1>
            <p class="mt-2 text-slate-600">
              {{ collection.description || 'Nessuna descrizione.' }}
            </p>
          </div>

          <span
              class="rounded-full bg-slate-100 px-3 py-1 text-sm text-slate-700"
          >
            {{ collection.visibility }}
          </span>
        </div>
      </header>

      <section class="grid items-start gap-8 xl:grid-cols-[1.2fr_0.8fr]">
        <div>
          <div class="mb-4 flex items-center justify-between gap-3">
            <h2 class="text-xl font-semibold text-slate-900">
              Documenti
            </h2>
            <span class="text-sm text-slate-500">
              {{ documents.length }} documenti
            </span>
          </div>

          <div
              v-if="documents.length === 0"
              class="rounded-2xl border border-dashed border-slate-300 p-10 text-center"
          >
            <h3 class="font-semibold text-slate-800">
              Nessun documento nella collezione
            </h3>
            <p class="mt-2 text-sm text-slate-600">
              Aggiungi documenti dalla sezione Documenti.
            </p>
          </div>

          <div v-else class="space-y-4">
            <article
                v-for="document in documents"
                :key="document.id"
                class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
            >
              <div class="mb-4 flex h-44 items-center justify-center overflow-hidden rounded-lg bg-slate-100 sm:mb-0 sm:w-36 sm:shrink-0">
                <img
                    v-if="coverUrls[document.id]"
                    :src="coverUrls[document.id]"
                    :alt="`Copertina di ${document.displayName}`"
                    class="h-full w-full object-contain"
                />
                <span v-else class="px-3 text-center text-sm text-slate-500">
                  {{ coverErrors[document.id] ? 'Copertina non disponibile' : 'Caricamento copertina...' }}
                </span>
              </div>
              <div class="flex flex-col gap-4 sm:flex-row">
                <div class="min-w-0">
                  <h3 class="font-semibold text-slate-900">
                    {{ document.displayName }}
                  </h3>

                  <p class="mt-1 text-sm text-slate-600">
                    {{ document.description || 'Nessuna descrizione.' }}
                  </p>

                  <div class="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-500">
                    <span v-if="document.author">
                      Autore: {{ document.author }}
                    </span>
                    <span v-if="document.historicalDate">
                      Data storica: {{ formatDate(document.historicalDate) }}
                    </span>
                    <span v-if="document.archiveSource">
                      Fonte: {{ document.archiveSource }}
                    </span>
                    <span>{{ document.pageCount }} pagine</span>
                  </div>
                </div>

                <div class="flex shrink-0 flex-col items-start gap-3 sm:items-end">
                  <span class="text-xs text-slate-500">
                    Elaborazione: {{ document.documentStatus }}
                  </span>
                  <span
                      class="rounded-full px-2.5 py-1 text-xs font-medium"
                      :class="document.collectionStatus === 'PARSED'
                        ? 'bg-emerald-50 text-emerald-700'
                        : document.collectionStatus === 'ERROR'
                          ? 'bg-red-50 text-red-700'
                          : 'bg-amber-50 text-amber-700'"
                  >
                    Collezione: {{ document.collectionStatus }}
                  </span>

                  <button
                      type="button"
                      class="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                      @click="openDocument(document.id)"
                  >
                    Apri documento
                  </button>
                </div>
              </div>
            </article>
          </div>
        </div>

        <div class="xl:sticky xl:top-6">
          <RagChat
              :collection-id="String(route.params.id)"
              :team-id="teamId || undefined"
          />
        </div>
      </section>
    </template>
  </main>
</template>
