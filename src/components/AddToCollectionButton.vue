
<script setup lang="ts">
import { ref } from 'vue'
import { authenticatedFetch } from '../services/auth'


interface Props {
  documentId: string
  documentName: string
}

interface CollectionItem {
  id: string
  name: string
  description: string | null
  visibility: string
  createdAt: string
}

const props = defineProps<Props>()

const isOpen = ref(false)
const collections = ref<CollectionItem[]>([])
const selectedCollectionId = ref('')
const loadingCollections = ref(false)
const submitting = ref(false)
const showCreateForm = ref(false)
const name = ref('')
const description = ref('')
const error = ref('')
const success = ref('')

function authHeaders(): HeadersInit {
  const token = localStorage.getItem('historyq_token')

  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

async function requestError(response: Response, fallback: string): Promise<string> {
  if (response.status === 401) {
    return 'Sessione scaduta. Accedi nuovamente.'
  }

  try {
    const body = await response.json() as {
      detail?: string
      message?: string
      title?: string
    }
    return body.detail || body.message || body.title || fallback
  } catch {
    return fallback
  }
}

async function openModal() {
  isOpen.value = true
  error.value = ''
  success.value = ''
  showCreateForm.value = false
  selectedCollectionId.value = ''

  const token = localStorage.getItem('historyq_token')
  if (!token) {
    collections.value = []
    error.value = 'Registrati o accedi per aggiungere documenti alle collezioni.'
    return
  }

  await loadCollections()
}

async function loadCollections() {
  loadingCollections.value = true


  try {
    const response = await authenticatedFetch('/api/collections/my')

    if (!response.ok) {
      throw new Error(await requestError(
          response,
          `Impossibile caricare le collezioni (HTTP ${response.status}).`,
      ))
    }

    collections.value = await response.json() as CollectionItem[]
  } catch (err) {
    error.value = err instanceof Error
        ? err.message
        : 'Impossibile caricare le collezioni.'
  } finally {
    loadingCollections.value = false
  }
}


async function addDocument(collectionId: string) {
  const token = localStorage.getItem('historyq_token')

  if (!token) {
    throw new Error('Registrati o accedi per aggiungere documenti alle collezioni.')
  }

  const response = await authenticatedFetch(
      `/api/collections/${collectionId}/documents/${props.documentId}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      },
  )

  if (!response.ok) {
    throw new Error(await requestError(
        response,
        `Impossibile aggiungere il documento (HTTP ${response.status}).`,
    ))
  }
}


async function addToSelectedCollection() {
  if (submitting.value) {
    return
  }

  if (!selectedCollectionId.value) {
    error.value = 'Seleziona una collezione.'
    return
  }

  submitting.value = true
  error.value = ''
  success.value = ''

  try {
    await addDocument(selectedCollectionId.value)
    success.value = 'Documento aggiunto alla collezione.'
  } catch (err) {
    error.value = err instanceof Error
        ? err.message
        : 'Impossibile aggiungere il documento.'
  } finally {
    submitting.value = false
  }
}

async function createCollectionAndAddDocument() {
  if (submitting.value) {
    return
  }

  if (!name.value.trim()) {
    error.value = 'Inserisci il nome della collezione.'
    return
  }

  submitting.value = true
  error.value = ''
  success.value = ''

  try {
    const response = await authenticatedFetch('/api/collections', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: name.value.trim(),
        description: description.value.trim() || null,
      }),
    })

    if (!response.ok) {
      throw new Error(await requestError(
          response,
          `Impossibile creare la collezione (HTTP ${response.status}).`,
      ))
    }

    const collection = await response.json() as CollectionItem

    try {
      await addDocument(collection.id)
      success.value = 'Collezione creata e documento aggiunto.'
      collections.value.push(collection)
      selectedCollectionId.value = collection.id
      name.value = ''
      description.value = ''
      showCreateForm.value = false

    } catch (err) {
      await loadCollections()

      if (err instanceof Error) {
        throw err
      }

      throw new Error(
          'Collezione creata, ma non è stato possibile aggiungere il documento.',
      )
    }

  } catch (err) {
    error.value = err instanceof Error
        ? err.message
        : 'Impossibile creare la collezione.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <button
      type="button"
      :aria-label="`Aggiungi ${documentName} a una collezione`"
      title="Aggiungi a una collezione"
      class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-blue-200 bg-white text-xl font-medium text-blue-700 shadow-sm transition hover:bg-blue-50 disabled:opacity-50"
      @click.stop="openModal"
  >
    +
  </button>

  <Teleport to="body">
    <div
        v-if="isOpen"
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4"
        @click.self="isOpen = false"
    >
      <section
          class="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
          role="dialog"
          aria-modal="true"
          aria-labelledby="collection-modal-title"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2
                id="collection-modal-title"
                class="text-xl font-semibold text-slate-900"
            >
              Aggiungi a una collezione
            </h2>
            <p class="mt-1 text-sm text-slate-500">
              {{ documentName }}
            </p>
          </div>

          <button
              type="button"
              class="rounded-lg px-3 py-1 text-xl text-slate-500 hover:bg-slate-100"
              aria-label="Chiudi finestra"
              @click="isOpen = false"
          >
            ×
          </button>
        </div>

        <p
            v-if="error"
            class="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700"
            role="alert"
        >
          {{ error }}
        </p>

        <p
            v-if="success"
            class="mt-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-700"
            role="status"
        >
          {{ success }}
        </p>

        <p v-if="loadingCollections" class="py-6 text-sm text-slate-500">
          Caricamento collezioni...
        </p>

        <template v-else>
          <div v-if="!showCreateForm" class="mt-5 space-y-4">
            <p v-if="collections.length === 0" class="text-sm text-slate-500">
              Non hai ancora creato collezioni.
            </p>

            <label
                v-else
                for="collection-select"
                class="block text-sm font-medium text-slate-700"
            >
              Seleziona una collezione
            </label>

            <select
                v-if="collections.length > 0"
                id="collection-select"
                v-model="selectedCollectionId"
                class="w-full rounded-xl border border-slate-300 bg-white px-3 py-3 text-sm"
            >
              <option value="" disabled>Seleziona...</option>
              <option
                  v-for="collection in collections"
                  :key="collection.id"
                  :value="collection.id"
              >
                {{ collection.name }}
              </option>
            </select>

            <button
                v-if="collections.length > 0"
                type="button"
                :disabled="submitting || !selectedCollectionId"
                class="w-full rounded-xl bg-blue-700 px-4 py-3 text-sm font-semibold text-white hover:bg-blue-800 disabled:opacity-50"
                @click="addToSelectedCollection"
            >
              {{ submitting ? 'Aggiunta in corso...' : 'Aggiungi documento' }}
            </button>

            <button
                type="button"
                class="w-full rounded-xl border border-slate-300 px-4 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                @click="showCreateForm = true; error = ''; success = ''"
            >
              + Crea collezione
            </button>
          </div>

          <form
              v-else
              class="mt-5 space-y-4"
              @submit.prevent="createCollectionAndAddDocument"
          >
            <label class="block text-sm font-medium text-slate-700">
              Nome
              <input
                  v-model="name"
                  required
                  maxlength="255"
                  class="mt-1 w-full rounded-xl border border-slate-300 px-3 py-3 text-sm"
                  placeholder="Es. Storia medievale"
              />
            </label>

            <label class="block text-sm font-medium text-slate-700">
              Descrizione (facoltativa)
              <textarea
                  v-model="description"
                  rows="3"
                  class="mt-1 w-full rounded-xl border border-slate-300 px-3 py-3 text-sm"
                  placeholder="Descrivi la collezione..."
              />
            </label>

            <div class="flex gap-3">
              <button
                  type="button"
                  class="flex-1 rounded-xl border border-slate-300 px-4 py-3 text-sm font-semibold text-slate-700"
                  :disabled="submitting"
                  @click="showCreateForm = false; error = ''"
              >
                Indietro
              </button>

              <button
                  type="submit"
                  class="flex-1 rounded-xl bg-blue-700 px-4 py-3 text-sm font-semibold text-white hover:bg-blue-800 disabled:opacity-50"
                  :disabled="submitting"
              >
                {{ submitting ? 'Creazione...' : 'Crea e aggiungi' }}
              </button>
            </div>
          </form>
        </template>
      </section>
    </div>
  </Teleport>
</template>
