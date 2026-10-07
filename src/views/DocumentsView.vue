<script setup lang="ts">
import DocumentUpload from '../components/DocumentUpload.vue'
</script>

<template>
  <div class="space-y-8">
    <section class="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
      <div>
        <p class="text-xs font-semibold uppercase tracking-[0.16em] text-blue-700">Spazio di lavoro</p>
        <h1 class="mt-2 text-3xl font-semibold tracking-tight text-[#102a43]">I miei documenti</h1>
        <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
          Carica e prepara i documenti storici per la ricerca semantica e l'interrogazione.
        </p>
      </div>
      <span class="inline-flex w-fit items-center rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-500">
        Archivio personale
      </span>
    </section>

    <DocumentUpload />

    <section class="border border-slate-200 bg-white px-5 py-10 text-center shadow-sm sm:px-8">
      <div class="mx-auto flex h-11 w-11 items-center justify-center rounded-lg bg-[#f3eee6] text-[#9b8060]">
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M6 4h9l3 3v13H6V4Zm9 0v4h3M9 13h6m-6 3h4" />
        </svg>
      </div>
      <h2 class="mt-4 text-base font-semibold text-slate-800">Non hai ancora caricato documenti</h2>
      <p class="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
        I documenti elaborati appariranno qui e saranno pronti per essere consultati.
      </p>
    </section>
  </div>
</template>
