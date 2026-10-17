
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getPublicDocumentPreview } from '../services/api'

interface PublicDocument {
  id: string
  displayName: string
  description: string
  originalName: string
  fileType: string
  pageCount: number
  createdAt: string
  createdBy: string | null
  historicalDate: string
  author: string
  archiveSource: string
  visibility: 'PUBLIC' | 'PRIVATE'
}

const documents = ref<PublicDocument[]>([])
const previewUrls = ref<Record<string, string>>({})
const search = ref('')
const loading = ref(true)
const error = ref('')

let searchTimer: ReturnType<typeof setTimeout> | undefined

async function loadDocuments() {
  loading.value = true
  error.value = ''

  try {
    const query = search.value.trim()
    const url = query
        ? `/api/public/documents?name=${encodeURIComponent(query)}`
        : '/api/public/documents'

    const response = await fetch(url)

    if (!response.ok) {
      throw new Error(`Errore HTTP ${response.status}`)
    }

    const result = await response.json() as PublicDocument[]
    documents.value = result.filter(doc => doc.visibility === 'PUBLIC')

    for (const doc of documents.value) {
      if (!previewUrls.value[doc.id]) {
        getPublicDocumentPreview(doc.id)
            .then(url => { previewUrls.value[doc.id] = url })
            .catch(err => console.error('Errore anteprima:', err))
      }
    }
  } catch (err) {
    console.error(err)
    error.value = 'Impossibile caricare i documenti pubblici.'
  } finally {
    loading.value = false
  }
}

function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadDocuments, 300)
}

onMounted(loadDocuments)

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
  for (const url of Object.values(previewUrls.value)) URL.revokeObjectURL(url)
})
</script>

<template>
  <main class="mx-auto max-w-6xl space-y-8 px-4 py-8">
    <header class="border-b border-[#d9cdbd] pb-7">
      <p class="text-xs font-semibold uppercase tracking-[0.2em] text-blue-700">
        Archivio condiviso
      </p>
      <h1 class="mt-2 text-4xl font-semibold text-slate-900 sm:text-5xl">
        Documenti pubblici
      </h1>
      <p class="mt-3 max-w-2xl text-sm leading-6 text-slate-500">
        Esplora e consulta i documenti storici condivisi dalla community.
      </p>
    </header>

    <div class="rounded-2xl border border-[#d9cdbd] bg-[#fbf9f5] p-4 shadow-sm sm:p-5">
      <label for="document-search" class="mb-2 block text-sm font-medium text-slate-700">
        Cerca per nome
      </label>
      <input
          id="document-search"
          v-model="search"
          @input="onSearchInput"
          type="search"
          placeholder="Es. Archivio, Garibaldi, Roma..."
          class="w-full rounded-xl border border-[#cfc1b0] bg-white px-4 py-3 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
      />
    </div>

    <p v-if="loading" class="py-8 text-center text-sm text-slate-500">
      Caricamento documenti...
    </p>

    <p v-else-if="error" class="py-8 text-center text-sm text-red-600">
      {{ error }}
    </p>

    <p v-else-if="documents.length === 0" class="py-8 text-center text-sm text-slate-500">
      Nessun documento pubblico trovato.
    </p>

    <section v-else class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      <article
          v-for="doc in documents"
          :key="doc.id"
          class="group overflow-hidden rounded-2xl border border-[#d9cdbd] bg-[#fbf9f5] shadow-sm transition duration-200 hover:-translate-y-1 hover:shadow-lg"
      >
        <img
            v-if="previewUrls[doc.id]"
            :src="previewUrls[doc.id]"
            :alt="`Anteprima di ${doc.displayName}`"
            class="h-48 w-full object-cover grayscale-[15%] transition duration-300 group-hover:scale-[1.02]"
        />

        <div class="space-y-3 p-5">
          <h2 class="text-xl font-semibold text-slate-900">
            {{ doc.displayName }}
          </h2>

          <p class="line-clamp-3 text-sm text-slate-600">
            {{ doc.description }}
          </p>

          <p class="text-xs text-slate-500">
            {{ doc.pageCount }} pagine
            <span v-if="doc.historicalDate">
              · {{ doc.historicalDate }}
            </span>
          </p>

          <RouterLink
              :to="`/documents/${doc.id}`"
              class="inline-flex rounded-lg bg-blue-700 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-800"
          >
            Consulta documento
          </RouterLink>
        </div>
      </article>
    </section>
  </main>
</template>
