
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

interface CollectionItem {
  id: string
  name: string
  description: string | null
  visibility: string
  createdAt: string
}

const collections = ref<CollectionItem[]>([])
const loading = ref(true)
const error = ref('')
const router = useRouter()

function openCollection(collectionId: string) {
  router.push(`/collections/${collectionId}`)
}
async function loadCollections() {
  loading.value = true
  error.value = ''

  try {
    const token = localStorage.getItem('historyq_token')

    if (!token) {
      throw new Error('Sessione scaduta. Accedi nuovamente.')
    }

    const response = await fetch('/api/collections/my', {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })

    if (response.status === 401) {
      throw new Error('Sessione scaduta. Accedi nuovamente.')
    }

    if (!response.ok) {
      throw new Error('Impossibile caricare le collezioni.')
    }

    collections.value = await response.json() as CollectionItem[]
  } catch (err) {
    error.value = err instanceof Error
        ? err.message
        : 'Si è verificato un errore.'
  } finally {
    loading.value = false
  }
}

onMounted(loadCollections)
</script>

<template>
  <main class="mx-auto max-w-6xl px-4 py-8 sm:px-6">
    <header class="mb-8">
      <h1 class="text-3xl font-bold text-slate-900">
        Le mie collezioni
      </h1>
      <p class="mt-2 text-slate-600">
        Organizza i tuoi documenti storici e preparati a interrogarli
        con l'intelligenza artificiale.
      </p>
    </header>

    <div
        v-if="loading"
        class="py-12 text-center text-slate-500"
    >
      Caricamento collezioni...
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
          @click="loadCollections"
      >
        Riprova
      </button>
    </div>

    <section
        v-else-if="collections.length === 0"
        class="rounded-2xl border border-dashed border-slate-300 p-10 text-center"
    >
      <h2 class="text-lg font-semibold text-slate-800">
        Non hai ancora collezioni
      </h2>
      <p class="mt-2 text-sm text-slate-600">
        Puoi crearne una dalla sezione documenti, usando il pulsante
        per aggiungere un documento a una collezione.
      </p>
    </section>

    <section
        v-else
        class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3"
    >
      <article
          v-for="collection in collections"
          :key="collection.id"
          class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
      >
        <h2 class="text-lg font-semibold text-slate-900">
          {{ collection.name }}
        </h2>

        <p class="mt-2 min-h-10 text-sm text-slate-600">
          {{ collection.description || 'Nessuna descrizione.' }}
        </p>

        <div class="mt-4 flex items-center justify-between">
          <span class="text-xs text-slate-500">
            {{ collection.visibility }}
          </span>

          <button
              type="button"

              title="Disponibile nel prossimo passaggio"
              class="rounded-lg bg-blue-700 px-3 py-2 text-sm font-medium text-white opacity-60"
              @click="openCollection(collection.id)"
          >
            Apri con AI
          </button>
        </div>
      </article>
    </section>
  </main>
</template>
