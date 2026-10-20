import { computed } from 'vue'

<script setup lang="ts">
import { ref, onMounted,computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  addTeamDocuments,
  downloadTeamDocument,
  getTeam,
  getTeamDocumentCandidates,
  getTeamDocuments,
  getTeamMembers,
  getTeamDocumentPreview,
  inviteTeamMember,
  removeTeamDocument,
  getTeamCollections, getTeamCollectionCandidates, addTeamCollections,
  removeTeamCollection,
} from '@/services/api'
import type {
  TeamDocumentResponse,
  TeamResponse,
  TeamMemberResponse,
  TeamCollectionResponse,
} from '@/services/api'

const route = useRoute()
const router = useRouter()

const team = ref<TeamResponse | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const members = ref<TeamMemberResponse[]>([])
const membersLoading = ref(false)
const membersError = ref('')

const inviteEmail = ref('')
const inviteLoading = ref(false)
const inviteError = ref('')
const inviteSuccess = ref('')
const sharedDocuments = ref<TeamDocumentResponse[]>([])
const sharedDocumentsLoading = ref(false)
const sharedDocumentsError = ref('')
const documentCandidates = ref<TeamDocumentResponse[]>([])
const candidatesLoading = ref(false)
const selectedDocumentIds = ref<string[]>([])
const documentsSource = ref<'ALL' | 'PERSONAL' | 'PUBLIC'>('ALL')
const documentsQuery = ref('')
const documentsActionLoading = ref(false)
const documentPreviewUrls = ref<Record<string, string>>({})
const sharedCollections = ref<TeamCollectionResponse[]>([])
const collectionCandidates = ref<TeamCollectionResponse[]>([])
const selectedCollectionIds = ref<string[]>([])
const collectionsError = ref('')
const collectionsActionLoading = ref(false)

// Usato esclusivamente per mostrare o nascondere il modulo.
// Il backend resta responsabile dell'autorizzazione effettiva.
const currentUsername = localStorage.getItem('historyq_username')

const canInviteMembers = computed(() =>
    members.value.some(
        member =>
            member.username === currentUsername &&
            (member.role === 'OWNER' || member.role === 'ADMIN')
    )
)

const canManageDocuments = computed(() =>
    members.value.some(
        member =>
            member.username === currentUsername &&
            (member.role === 'OWNER' || member.role === 'ADMIN')
    )
)

const canAddDocuments = computed(() =>
    members.value.some(member => member.username === currentUsername)
)

async function submitInvitation() {
  inviteError.value = ''
  inviteSuccess.value = ''

  const email = inviteEmail.value.trim()
  const teamId = route.params.teamId

  if (typeof teamId !== 'string' || !teamId) {
    inviteError.value = 'ID del team non valido.'
    return
  }

  if (!email) {
    inviteError.value = 'Inserisci un indirizzo email.'
    return
  }

  inviteLoading.value = true

  try {
    const invitation = await inviteTeamMember(teamId, email)

    inviteSuccess.value =
        `Invito creato per ${invitation.email}. ` +
        (invitation.status === 'PENDING'
            ? 'In attesa di risposta.'
            : '')

    inviteEmail.value = ''
  } catch (error) {
    inviteError.value =
        error instanceof Error
            ? error.message
            : "Impossibile inviare l'invito."
  } finally {
    inviteLoading.value = false
  }
}

async function fetchTeam() {
  loading.value = true
  errorMessage.value = ''

  try {
    const teamId = route.params.teamId

    if (typeof teamId !== 'string' || !teamId) {
      throw new Error('ID del team non valido.')
    }

    team.value = await getTeam(teamId)
  } catch {
    errorMessage.value =
        'Impossibile caricare il team. Potresti non avere i permessi per accedervi.'
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/teams')
}

async function fetchTeamMembers() {
  membersLoading.value = true
  membersError.value = ''

  try {
    const teamId = route.params.teamId

    if (typeof teamId !== 'string' || !teamId) {
      throw new Error('ID del team non valido.')
    }

    members.value = await getTeamMembers(teamId)
  } catch {
    membersError.value =
        'Impossibile caricare i membri del team. Potresti non avere i permessi per accedervi.'
  } finally {
    membersLoading.value = false
  }
}

async function fetchSharedDocuments() {
  const teamId = route.params.teamId
  if (typeof teamId !== 'string' || !teamId) return
  sharedDocumentsLoading.value = true
  sharedDocumentsError.value = ''
  try {
    sharedDocuments.value = await getTeamDocuments(teamId)
    await Promise.allSettled(sharedDocuments.value.map(async document => {
      try {
        documentPreviewUrls.value[document.id] =
            await getTeamDocumentPreview(teamId, document.id)
      } catch {
        // L'anteprima non blocca la lista dei documenti.
      }
    }))
  } catch (error) {
    sharedDocumentsError.value = error instanceof Error
        ? error.message
        : 'Impossibile recuperare i documenti condivisi.'
  } finally {
    sharedDocumentsLoading.value = false
  }
}

async function fetchDocumentCandidates() {
  const teamId = route.params.teamId
  if (typeof teamId !== 'string' || !teamId) return
  candidatesLoading.value = true
  try {
    documentCandidates.value = await getTeamDocumentCandidates(
        teamId,
        documentsSource.value,
        documentsQuery.value
    )
    selectedDocumentIds.value = selectedDocumentIds.value.filter(id =>
        documentCandidates.value.some(document => document.id === id)
    )
  } catch (error) {
    sharedDocumentsError.value = error instanceof Error
        ? error.message
        : 'Impossibile cercare i documenti.'
  } finally {
    candidatesLoading.value = false
  }
}

async function addSelectedDocuments() {
  const teamId = route.params.teamId
  if (typeof teamId !== 'string' || !teamId || selectedDocumentIds.value.length === 0) return
  documentsActionLoading.value = true
  try {
    await addTeamDocuments(teamId, selectedDocumentIds.value)
    selectedDocumentIds.value = []
    await fetchSharedDocuments()
    await fetchDocumentCandidates()
  } catch (error) {
    sharedDocumentsError.value = error instanceof Error
        ? error.message
        : 'Impossibile condividere i documenti.'
  } finally {
    documentsActionLoading.value = false
  }
}

async function removeSharedDocument(documentId: string) {
  const teamId = route.params.teamId
  if (typeof teamId !== 'string' || !teamId) return
  documentsActionLoading.value = true
  try {
    await removeTeamDocument(teamId, documentId)
    const preview = documentPreviewUrls.value[documentId]
    if (preview) URL.revokeObjectURL(preview)
    delete documentPreviewUrls.value[documentId]
    await fetchSharedDocuments()
    await fetchDocumentCandidates()
  } catch (error) {
    sharedDocumentsError.value = error instanceof Error
        ? error.message
        : 'Impossibile rimuovere il documento condiviso.'
  } finally {
    documentsActionLoading.value = false
  }
}

async function downloadSharedDocument(document: TeamDocumentResponse) {
  const teamId = route.params.teamId
  if (typeof teamId !== 'string' || !teamId) return
  try {
    await downloadTeamDocument(teamId, document.id, document.displayName)
  } catch (error) {
    sharedDocumentsError.value = error instanceof Error
        ? error.message
        : 'Impossibile scaricare il documento.'
  }
}

function openDocumentPreview(documentId: string) {
  const preview = documentPreviewUrls.value[documentId]
  if (preview) window.open(preview, '_blank', 'noopener,noreferrer')
}

async function fetchSharedCollections() {
  const id = route.params.teamId
  if (typeof id !== 'string') return
  try {
    sharedCollections.value = await getTeamCollections(id)
    collectionCandidates.value = await getTeamCollectionCandidates(id)
  } catch (error) {
    collectionsError.value = error instanceof Error ? error.message : 'Impossibile recuperare le collezioni.'
  }
}
async function addSelectedCollections() {
  const id = route.params.teamId
  if (typeof id !== 'string' || !selectedCollectionIds.value.length) return
  collectionsActionLoading.value = true
  try {
    await addTeamCollections(id, selectedCollectionIds.value)
    selectedCollectionIds.value = []
    await fetchSharedCollections()
  } catch (error) {
    collectionsError.value = error instanceof Error ? error.message : 'Impossibile condividere le collezioni.'
  } finally { collectionsActionLoading.value = false }
}
async function removeSharedCollection(collectionId: string) {
  const id = route.params.teamId
  if (typeof id !== 'string') return
  try {
    await removeTeamCollection(id, collectionId)
    await fetchSharedCollections()
  } catch (error) {
    collectionsError.value = error instanceof Error ? error.message : 'Impossibile rimuovere la collezione.'
  }
}

onMounted(() => {
  fetchTeam()
  fetchTeamMembers()
  fetchSharedDocuments()
  fetchDocumentCandidates()
  fetchSharedCollections()
})
</script>

<template>
  <main class="mx-auto min-h-screen max-w-6xl px-4 py-8 sm:px-8 sm:py-10">
    <!-- Torna all'elenco -->
    <button
        type="button"
        @click="goBack"
        class="mb-6 inline-flex items-center gap-2 rounded-lg text-sm font-medium text-slate-500 transition-colors hover:text-[#6f5845] focus:outline-none focus:ring-4 focus:ring-[#8b735b]/15"
    >
      <svg
          xmlns="http://www.w3.org/2000/svg"
          class="h-4 w-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
      >
        <path d="m15 18-6-6 6-6" />
      </svg>

      Torna ai tuoi team
    </button>

    <!-- Caricamento -->
    <div
        v-if="loading"
        class="flex flex-col items-center justify-center rounded-2xl border border-slate-200 bg-white px-6 py-16 text-center"
        role="status"
    >
      <svg
          xmlns="http://www.w3.org/2000/svg"
          class="mb-4 h-8 w-8 animate-spin text-[#8b735b]"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
      >
        <circle
            class="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            stroke-width="4"
        />
        <path
            class="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z"
        />
      </svg>

      <p class="text-sm font-medium text-slate-700">
        Caricamento del team...
      </p>
    </div>

    <!-- Errore -->
    <div
        v-else-if="errorMessage"
        class="rounded-2xl border border-red-200 bg-red-50 p-6 sm:p-8"
        role="alert"
    >
      <h1 class="text-lg font-semibold text-red-800">
        Team non disponibile
      </h1>

      <p class="mt-2 text-sm leading-6 text-red-700">
        {{ errorMessage }}
      </p>

      <button
          type="button"
          @click="fetchTeam"
          class="mt-5 rounded-xl bg-[#3f352b] px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-[#5a4639] focus:outline-none focus:ring-4 focus:ring-[#8b735b]/20"
      >
        Riprova
      </button>
    </div>

    <!-- Dettaglio del team -->
    <template v-else-if="team">
      <section
          class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8"
      >
        <div class="flex flex-col gap-5 sm:flex-row sm:items-start">
          <div
              class="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-[#f3eee8] text-[#6f5845]"
          >
            <svg
                xmlns="http://www.w3.org/2000/svg"
                class="h-7 w-7"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
            >
              <path
                  d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
              />
            </svg>
          </div>

          <div class="min-w-0 flex-1">
            <p
                class="mb-2 text-xs font-semibold uppercase tracking-widest text-[#8b735b]"
            >
              Spazio di lavoro
            </p>

            <h1
                class="break-words text-2xl font-bold tracking-tight text-[#102a43] sm:text-3xl"
            >
              {{ team.name }}
            </h1>

            <p class="mt-3 whitespace-pre-line break-words text-sm leading-7 text-slate-600">
              {{ team.description || 'Questo team non ha ancora una descrizione.' }}
            </p>
          </div>
        </div>
      </section>

      <!-- Sezioni del team -->
      <section class="mt-8">
        <h2 class="text-lg font-semibold text-slate-800">
          Panoramica del team
        </h2>

        <p class="mt-1 text-sm text-slate-500">
          Da qui potrai gestire i membri e le risorse condivise.
        </p>
        <article
            v-if="canInviteMembers"
            class="mb-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
        >
          <h3 class="font-semibold text-slate-800">
            Invita una persona
          </h3>

          <p class="mt-1 text-sm leading-6 text-slate-500">
            Inserisci l'email dell'account che vuoi invitare nel team.
          </p>

          <form
              class="mt-4 flex flex-col gap-3 sm:flex-row"
              @submit.prevent="submitInvitation"
          >
            <label class="sr-only" for="team-invitation-email">
              Email dell'utente da invitare
            </label>

            <input
                id="team-invitation-email"
                v-model="inviteEmail"
                type="email"
                required
                maxlength="255"
                autocomplete="email"
                placeholder="nome@esempio.it"
                :disabled="inviteLoading"
                class="min-w-0 flex-1 rounded-xl border border-slate-300 px-4 py-3 text-sm outline-none transition focus:border-[#8b735b] focus:ring-4 focus:ring-[#8b735b]/10 disabled:bg-slate-100"
            />

            <button
                type="submit"
                :disabled="inviteLoading"
                class="rounded-xl bg-[#3f352b] px-5 py-3 text-sm font-medium text-white transition hover:bg-[#5a4639] focus:outline-none focus:ring-4 focus:ring-[#8b735b]/20 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {{ inviteLoading ? 'Invio...' : 'Invita' }}
            </button>
          </form>

          <p
              v-if="inviteSuccess"
              class="mt-3 text-sm text-green-700"
              role="status"
          >
            {{ inviteSuccess }}
          </p>

          <p
              v-if="inviteError"
              class="mt-3 text-sm text-red-600"
              role="alert"
          >
            {{ inviteError }}
          </p>
        </article>

        <div class="mt-5 grid grid-cols-1 gap-4 md:grid-cols-3">

          <article
              class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:col-span-3"
          >
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h3 class="font-semibold text-slate-800">
                  Membri del team
                </h3>
                <p class="mt-1 text-sm text-slate-500">
                  Persone che hanno accesso a questo spazio di lavoro.
                </p>
              </div>

              <span
                  v-if="!membersLoading && !membersError"
                  class="text-sm font-medium text-slate-500"
              >
      {{ members.length }}
      {{ members.length === 1 ? 'membro' : 'membri' }}
    </span>
            </div>

            <div
                v-if="membersLoading"
                class="mt-5 text-sm text-slate-500"
                role="status"
            >
              Caricamento dei membri...
            </div>

            <div v-else-if="membersError" class="mt-5">
              <p class="text-sm text-red-600" role="alert">
                {{ membersError }}
              </p>

              <button
                  type="button"
                  @click="fetchTeamMembers"
                  class="mt-3 rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                Riprova
              </button>
            </div>

            <p
                v-else-if="members.length === 0"
                class="mt-5 text-sm text-slate-500"
            >
              Nessun membro presente nel team.
            </p>

            <ul v-else class="mt-5 divide-y divide-slate-100">
              <li
                  v-for="member in members"
                  :key="member.userId"
                  class="flex flex-col gap-2 py-4 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
              >
                <div class="flex min-w-0 items-center gap-3">
                  <div
                      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#f3eee8] font-semibold text-[#6f5845]"
                      aria-hidden="true"
                  >
                    {{ member.username.charAt(0).toUpperCase() }}
                  </div>

                  <span class="break-all text-sm font-medium text-slate-800">
          {{ member.username }}
        </span>
                </div>

                <span
                    class="w-fit rounded-full px-3 py-1 text-xs font-semibold"
                    :class="{
          'bg-amber-100 text-amber-800': member.role === 'OWNER',
          'bg-blue-100 text-blue-800': member.role === 'ADMIN',
          'bg-slate-100 text-slate-700': member.role === 'MEMBER',
        }"
                >
        {{
                    member.role === 'OWNER'
                        ? 'Proprietario'
                        : member.role === 'ADMIN'
                            ? 'Amministratore'
                            : 'Membro'
                  }}
      </span>
              </li>
            </ul>
          </article>


          <article
              class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:col-span-3"
          >
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h3 class="font-semibold text-slate-800">
                  Documenti condivisi
                </h3>
                <p class="mt-1 text-sm text-slate-500">
                  Documenti personali o pubblici già presenti su HistoryQ.
                </p>
              </div>
              <span class="text-sm text-slate-500">
                {{ sharedDocuments.length }} documenti
              </span>
            </div>

            <div
                v-if="canAddDocuments"
                class="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4"
            >
              <div class="flex flex-col gap-3 sm:flex-row">
                <select
                    v-model="documentsSource"
                    class="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
                    @change="fetchDocumentCandidates"
                >
                  <option value="ALL">Personali e pubblici</option>
                  <option value="PERSONAL">I miei documenti</option>
                  <option value="PUBLIC">Documenti pubblici</option>
                </select>
                <input
                    v-model="documentsQuery"
                    type="search"
                    placeholder="Cerca per nome"
                    class="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    @keyup.enter="fetchDocumentCandidates"
                />
                <button
                    type="button"
                    class="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700"
                    :disabled="candidatesLoading"
                    @click="fetchDocumentCandidates"
                >
                  Cerca
                </button>
              </div>

              <div v-if="candidatesLoading" class="mt-3 text-sm text-slate-500">
                Caricamento documenti...
              </div>
              <div
                  v-else-if="documentCandidates.length"
                  class="mt-3 max-h-48 space-y-2 overflow-y-auto"
              >
                <label
                    v-for="document in documentCandidates"
                    :key="document.id"
                    class="flex cursor-pointer items-center gap-3 rounded-lg bg-white p-3 text-sm"
                >
                  <input
                      v-model="selectedDocumentIds"
                      type="checkbox"
                      :value="document.id"
                  />
                  <span class="min-w-0 flex-1">
                    <span class="block truncate font-medium text-slate-800">
                      {{ document.displayName }}
                    </span>
                    <span class="text-xs text-slate-500">
                      {{ document.visibility === 'PUBLIC' ? 'Pubblico' : 'Personale' }}
                      <span v-if="document.author"> · {{ document.author }}</span>
                    </span>
                  </span>
                </label>
              </div>
              <p v-else class="mt-3 text-sm text-slate-500">
                Nessun documento disponibile.
              </p>

              <button
                  type="button"
                  class="mt-3 rounded-lg bg-[#3f352b] px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                  :disabled="documentsActionLoading || selectedDocumentIds.length === 0"
                  @click="addSelectedDocuments"
              >
                {{ documentsActionLoading ? 'Aggiornamento...' : 'Condividi selezionati' }}
              </button>
            </div>

            <p v-if="sharedDocumentsLoading" class="mt-5 text-sm text-slate-500">
              Caricamento documenti condivisi...
            </p>
            <p v-else-if="sharedDocumentsError" class="mt-5 text-sm text-red-600" role="alert">
              {{ sharedDocumentsError }}
            </p>
            <p v-else-if="!sharedDocuments.length" class="mt-5 text-sm text-slate-500">
              Nessun documento condiviso nel Team.
            </p>
            <ul v-else class="mt-5 grid gap-3 sm:grid-cols-2">
              <li
                  v-for="document in sharedDocuments"
                  :key="document.id"
                  class="flex gap-3 rounded-xl border border-slate-200 p-3"
              >
                <img
                    v-if="documentPreviewUrls[document.id]"
                    :src="documentPreviewUrls[document.id]"
                    :alt="`Anteprima di ${document.displayName}`"
                    class="h-16 w-16 rounded-lg object-cover"
                />
                <div class="min-w-0 flex-1">
                  <p class="truncate font-medium text-slate-800">
                    {{ document.displayName }}
                  </p>
                  <p class="mt-1 text-xs text-slate-500">
                    {{ document.visibility === 'PUBLIC' ? 'Pubblico' : 'Privato' }}
                    <span v-if="document.addedBy"> · aggiunto da {{ document.addedBy }}</span>
                  </p>
                  <div class="mt-2 flex flex-wrap gap-3 text-xs font-medium">
                    <button
                        type="button"
                        class="text-blue-700 hover:underline"
                        @click="router.push(`/documents/${document.id}?teamId=${route.params.teamId}`)"
                    >
                      Apri
                    </button>
                    <button
                        type="button"
                        class="text-blue-700 hover:underline"
                        :disabled="!documentPreviewUrls[document.id]"
                        @click="openDocumentPreview(document.id)"
                    >
                      Anteprima
                    </button>
                    <button
                        type="button"
                        class="text-blue-700 hover:underline"
                        @click="downloadSharedDocument(document)"
                    >
                      Scarica
                    </button>
                    <button
                        v-if="canManageDocuments"
                        type="button"
                        class="text-red-700 hover:underline"
                        :disabled="documentsActionLoading"
                        @click="removeSharedDocument(document.id)"
                    >
                      Rimuovi
                    </button>
                  </div>
                </div>
              </li>
            </ul>
          </article>

          <article
              class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
          >
            <div
                class="mb-4 flex h-10 w-10 items-center justify-center rounded-xl bg-[#f3eee8] text-[#6f5845]"
            >
              <svg
                  xmlns="http://www.w3.org/2000/svg"
                  class="h-5 w-5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
              >
                <path d="M4 4h6v6H4z" />
                <path d="M14 4h6v6h-6z" />
                <path d="M4 14h6v6H4z" />
                <path d="M14 14h6v6h-6z" />
              </svg>
            </div>

            <h3 class="font-semibold text-slate-800">
              Collezioni
            </h3>

            <p class="mt-2 text-sm leading-6 text-slate-500">
              Seleziona collezioni esistenti e rendile interrogabili per il Team.
            </p>
            <p v-if="collectionsError" class="mt-2 text-sm text-red-700">{{ collectionsError }}</p>
            <ul v-if="sharedCollections.length" class="mt-4 space-y-2">
              <li v-for="collection in sharedCollections" :key="collection.id"
                  class="flex items-center justify-between rounded-lg bg-slate-50 p-3">
                <button type="button" class="text-left font-medium text-blue-700 hover:underline"
                    @click="router.push(`/collections/${collection.id}?teamId=${route.params.teamId}`)">
                  {{ collection.name }}
                </button>
                <button v-if="canManageDocuments" type="button" class="text-xs text-red-700"
                    @click="removeSharedCollection(collection.id)">Rimuovi</button>
              </li>
            </ul>
            <div v-if="canAddDocuments" class="mt-4">
              <select v-model="selectedCollectionIds" multiple class="min-h-24 w-full rounded-lg border border-slate-300 p-2 text-sm">
                <option v-for="candidate in collectionCandidates" :key="candidate.id" :value="candidate.id">
                  {{ candidate.name }} ({{ candidate.visibility }})
                </option>
              </select>
              <button type="button" class="mt-3 rounded-lg bg-[#6f5845] px-3 py-2 text-sm text-white"
                  :disabled="collectionsActionLoading" @click="addSelectedCollections">
                Condividi selezionate
              </button>
            </div>
          </article>
        </div>
      </section>
    </template>
  </main>
</template>
