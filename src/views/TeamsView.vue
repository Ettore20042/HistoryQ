<script setup lang="ts">
import { ref,onMounted } from 'vue'
import{useRouter} from 'vue-router'
import{createTeam,getTeams} from '@/services/api.ts';
import type{TeamResponse} from "@/services/api.ts";

const router = useRouter()
const teams = ref<TeamResponse[]>([])
const loading = ref(true)
const creating = ref(false)
const showCreateForm = ref(false)

const name=ref('')
const description=ref('')

const errorMessage = ref('')
const successMessage = ref('')

async function fetchTeams() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getTeams()
    teams.value = response
  } catch (error) {
    errorMessage.value = 'Impossibile caricare i team.'
  } finally {
    loading.value = false
  }
}
async function handleCreateTeam() {
  creating.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await createTeam({ name: name.value, description: description.value })
    successMessage.value = 'Team creato con successo!'
    name.value = ''
    description.value = ''
    showCreateForm.value = false
    await fetchTeams()
  } catch (error) {
    errorMessage.value = 'Errore durante la creazione del team.Inserisci un nome e una descrizione validi.'
  } finally {
    creating.value = false
  }
}
function openTeam(teamId: string) {
  router.push(`/teams/${teamId}`)
}
function openCreateForm() {
  showCreateForm.value = true
  errorMessage.value = ''
  successMessage.value = ''
}
onMounted(fetchTeams)
</script>









<template>
  <main class="mx-auto min-h-screen max-w-6xl px-4 py-8 sm:px-8 sm:py-10">
    <!-- Intestazione -->
    <header
        class="mb-8 flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between"
    >
      <div>
        <p class="mb-2 text-sm font-medium uppercase tracking-widest text-[#8b735b]">
          Collaborazione
        </p>

        <h1 class="text-3xl font-bold tracking-tight text-[#102a43] sm:text-4xl">
          I tuoi team
        </h1>

        <p class="mt-2 max-w-xl text-sm leading-6 text-slate-500 sm:text-base">
          Organizza le tue collezioni documentali e collabora con gli altri utenti
          in spazi di lavoro condivisi.
        </p>
      </div>

      <button
          @click="showCreateForm ? (showCreateForm = false) : openCreateForm()"
          class="inline-flex min-h-11 items-center justify-center self-start rounded-xl bg-[#3f352b] px-5 py-3 text-sm font-semibold text-white shadow-sm transition-all duration-200 hover:bg-[#5a4639] hover:shadow-md focus:outline-none focus:ring-4 focus:ring-[#8b735b]/20 sm:self-auto"
      >
        <svg
            v-if="!showCreateForm"
            xmlns="http://www.w3.org/2000/svg"
            class="mr-2 h-5 w-5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
        >
          <path d="M12 5v14" />
          <path d="M5 12h14" />
        </svg>

        <svg
            v-else
            xmlns="http://www.w3.org/2000/svg"
            class="mr-2 h-5 w-5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
        >
          <path d="M18 6 6 18" />
          <path d="m6 6 12 12" />
        </svg>

        {{ showCreateForm ? 'Annulla' : 'Crea nuovo team' }}
      </button>
    </header>

    <!-- Messaggi -->
    <div
        v-if="errorMessage"
        role="alert"
        class="mb-6 flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
    >
      <svg
          xmlns="http://www.w3.org/2000/svg"
          class="mt-0.5 h-5 w-5 shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
      >
        <circle cx="12" cy="12" r="10" />
        <path d="M12 8v4" />
        <path d="M12 16h.01" />
      </svg>

      <p>{{ errorMessage }}</p>
    </div>

    <div
        v-if="successMessage"
        role="status"
        class="mb-6 flex items-start gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800"
    >
      <svg
          xmlns="http://www.w3.org/2000/svg"
          class="mt-0.5 h-5 w-5 shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
      >
        <path d="m5 12 4 4L19 6" />
      </svg>

      <p>{{ successMessage }}</p>
    </div>

    <!-- Form di creazione -->
    <div
        v-if="showCreateForm"
        class="mb-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8"
    >
      <div class="mb-6">
        <h2 class="text-xl font-semibold tracking-tight text-slate-800">
          Crea un nuovo team
        </h2>

        <p class="mt-1 text-sm leading-6 text-slate-500">
          Crea uno spazio condiviso per organizzare documenti e collaborare con altri utenti.
        </p>
      </div>

      <form
          class="space-y-5"
          @submit.prevent="handleCreateTeam"
      >
        <div>
          <label
              for="team-name"
              class="mb-2 block text-sm font-medium text-slate-700"
          >
            Nome del team
          </label>

          <input
              id="team-name"
              v-model="name"
              type="text"
              required
              maxlength="100"
              placeholder="Es. Ricerca storica"
              class="w-full rounded-xl border border-slate-300 bg-slate-50 px-4 py-3 text-sm text-slate-800 placeholder:text-slate-400 transition-colors focus:border-[#8b735b] focus:bg-white focus:outline-none focus:ring-4 focus:ring-[#8b735b]/10"
          />
        </div>

        <div>
          <label
              for="team-description"
              class="mb-2 block text-sm font-medium text-slate-700"
          >
            Descrizione
            <span class="font-normal text-slate-400">(facoltativa)</span>
          </label>

          <textarea
              id="team-description"
              v-model="description"
              rows="4"
              placeholder="Descrivi brevemente lo scopo del team..."
              class="w-full resize-y rounded-xl border border-slate-300 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-800 placeholder:text-slate-400 transition-colors focus:border-[#8b735b] focus:bg-white focus:outline-none focus:ring-4 focus:ring-[#8b735b]/10"
          ></textarea>
        </div>

        <div class="flex justify-end border-t border-slate-100 pt-5">
          <button
              type="submit"
              :disabled="creating"
              class="inline-flex min-h-11 items-center justify-center rounded-xl bg-[#3f352b] px-5 py-3 text-sm font-medium text-white shadow-sm transition-all duration-200 hover:bg-[#5a4639] hover:shadow-md focus:outline-none focus:ring-4 focus:ring-[#8b735b]/20 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <svg
                v-if="!creating"
                xmlns="http://www.w3.org/2000/svg"
                class="mr-2 h-4 w-4"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
            >
              <path d="M12 5v14" />
              <path d="M5 12h14" />
            </svg>

            <svg
                v-else
                xmlns="http://www.w3.org/2000/svg"
                class="mr-2 h-4 w-4 animate-spin"
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

            {{ creating ? 'Creazione in corso...' : 'Crea team' }}
          </button>
        </div>
      </form>
    </div>

    <!-- Elenco dei team -->
    <section aria-labelledby="team-list-title">
      <div class="mb-5 flex items-center justify-between gap-4">
        <h2
            id="team-list-title"
            class="text-lg font-semibold text-slate-800"
        >
          I tuoi spazi di lavoro
        </h2>

        <span
            v-if="!loading"
            class="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600"
        >
          {{ teams.length }} {{ teams.length === 1 ? 'team' : 'team' }}
        </span>
      </div>

      <!-- Caricamento -->
      <div
          v-if="loading"
          class="flex flex-col items-center justify-center rounded-2xl border border-slate-200 bg-white px-6 py-14 text-center"
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
          Caricamento dei team...
        </p>

        <p class="mt-1 text-sm text-slate-500">
          Attendi mentre recuperiamo i tuoi spazi di lavoro.
        </p>
      </div>

      <!-- Stato vuoto -->
      <div
          v-else-if="teams.length === 0"
          class="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-14 text-center"
      >
        <div
            class="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-[#f3eee8] text-[#6f5845]"
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
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
            <circle cx="9" cy="7" r="4" />
            <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
        </div>

        <h3 class="text-lg font-semibold text-slate-800">
          Non hai ancora creato un team
        </h3>

        <p class="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
          Crea il tuo primo spazio di lavoro per iniziare a organizzare e condividere
          documenti con altri utenti.
        </p>

        <button
            @click="openCreateForm"
            class="mt-6 inline-flex min-h-11 items-center justify-center rounded-xl bg-[#3f352b] px-5 py-3 text-sm font-medium text-white transition-colors hover:bg-[#5a4639] focus:outline-none focus:ring-4 focus:ring-[#8b735b]/20"
        >
          Crea il primo team
        </button>
      </div>

      <!-- Griglia dei team -->
      <ul
          v-else
          class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3"
      >
        <li
            v-for="team in teams"
            :key="team.id"
            class="min-w-0"
        >
          <button
              type="button"
              @click="openTeam(team.id)"
              class="group flex h-full w-full flex-col rounded-2xl border border-slate-200 bg-white p-5 text-left shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:border-[#c6b29d] hover:shadow-md focus:outline-none focus:ring-4 focus:ring-[#8b735b]/15"
          >
            <div class="mb-4 flex w-full items-start justify-between gap-3">
              <div
                  class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#f3eee8] text-[#6f5845] transition-colors group-hover:bg-[#e9dfd3]"
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
                  <path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                </svg>
              </div>

              <svg
                  xmlns="http://www.w3.org/2000/svg"
                  class="mt-1 h-5 w-5 shrink-0 text-slate-400 transition-all group-hover:translate-x-1 group-hover:text-[#6f5845]"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
              >
                <path d="M5 12h14" />
                <path d="m12 5 7 7-7 7" />
              </svg>
            </div>

            <h3
                class="w-full break-words text-base font-semibold text-[#102a43] transition-colors group-hover:text-[#6f5845]"
            >
              {{ team.name }}
            </h3>

            <p
                class="mt-2 w-full flex-1 break-words text-sm leading-6 text-slate-500"
            >
              {{ team.description || 'Nessuna descrizione disponibile.' }}
            </p>

            <div
                class="mt-5 flex items-center border-t border-slate-100 pt-4 text-xs font-medium text-slate-500"
            >
              <span>Apri team</span>
            </div>
          </button>
        </li>
      </ul>
    </section>
  </main>
</template>
