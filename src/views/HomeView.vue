<template>
  <div class="space-y-16 sm:space-y-20">
    <section class="grid items-center gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:gap-20">
      <div>
        <div class="mb-5 inline-flex items-center gap-2 rounded-full border border-[#e7dfd2] bg-[#f3eee6] px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.14em] text-[#7a6752]">
          <span class="h-1.5 w-1.5 rounded-full bg-[#9b8060]"></span>
          Document intelligence
        </div>
        <h1 class="max-w-2xl text-4xl font-semibold leading-[1.1] tracking-tight text-[#102a43] sm:text-5xl">
          Dai valore ai tuoi documenti storici.
        </h1>
        <p class="mt-6 max-w-xl text-base leading-7 text-slate-500 sm:text-lg">
          Carica, elabora e interroga i tuoi archivi con OCR, ricerca semantica e RAG in un unico spazio di lavoro.
        </p>
        <RouterLink
            to="/documents"
            class="mt-8 inline-flex items-center gap-2 rounded-lg bg-[#102a43] px-5 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#173f61]"
        >
          Accedi per iniziare
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M5 12h14m-6-6 6 6-6 6" />
          </svg>
        </RouterLink>
      </div>

      <div class="relative rounded-xl border border-[#e7dfd2] bg-[#f3eee6] p-5 sm:p-7">
        <div class="rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
          <div class="flex items-center justify-between border-b border-slate-100 pb-4">
            <div class="flex items-center gap-3">
              <span class="flex h-9 w-9 items-center justify-center rounded-md bg-blue-50 text-blue-700">
                <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M6 4h9l3 3v13H6V4Zm9 0v4h3M9 13h6m-6 3h4" /></svg>
              </span>
              <div>
                <p class="text-sm font-semibold text-slate-800">Archivio storico</p>
                <p class="text-xs text-slate-400">Spazio di lavoro</p>
              </div>
            </div>
            <span class="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700">Pronto</span>
          </div>
          <div class="space-y-3 py-5">
            <div class="flex items-center gap-3 rounded-md bg-slate-50 p-3">
              <span class="h-8 w-8 rounded bg-[#f3eee6]"></span>
              <span class="h-2 w-32 rounded bg-slate-200"></span>
              <span class="ml-auto h-2 w-10 rounded bg-slate-100"></span>
            </div>
            <div class="flex items-center gap-3 rounded-md bg-slate-50 p-3">
              <span class="h-8 w-8 rounded bg-blue-50"></span>
              <span class="h-2 w-24 rounded bg-slate-200"></span>
              <span class="ml-auto h-2 w-10 rounded bg-slate-100"></span>
            </div>
          </div>
          <div class="border-t border-slate-100 pt-4 text-xs text-slate-400">OCR e indicizzazione semantica attivi</div>
        </div>
      </div>
    </section>

    <section>
      <div class="mb-6">
        <p class="text-xs font-semibold uppercase tracking-[0.16em] text-blue-700">Un flusso più semplice</p>
        <h2 class="mt-2 text-2xl font-semibold tracking-tight text-[#102a43]">Tutto ciò che serve per esplorare un archivio</h2>
      </div>
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div v-for="feature in [
          { title: 'Gestione documenti', text: 'Organizza il tuo archivio in un unico spazio.', icon: '▤' },
          { title: 'OCR affidabile', text: 'Trasforma le immagini in testo ricercabile.', icon: 'T' },
          { title: 'Ricerca semantica', text: 'Trova concetti, non solo parole chiave.', icon: '⌕' },
          { title: 'Interrogazione RAG', text: 'Interroga le fonti con risposte contestuali.', icon: 'Q' },
        ]" :key="feature.title" class="border border-slate-200 bg-white p-5 shadow-sm">
          <span class="text-lg font-semibold text-[#9b8060]">{{ feature.icon }}</span>
          <h3 class="mt-4 text-sm font-semibold text-slate-800">{{ feature.title }}</h3>
          <p class="mt-2 text-sm leading-6 text-slate-500">{{ feature.text }}</p>
        </div>
      </div>
    </section>

    <section class="border-y border-slate-200 py-10 sm:py-12">
      <div class="mb-8">
        <p class="text-xs font-semibold uppercase tracking-[0.16em] text-blue-700">Come funziona</p>
        <h2 class="mt-2 text-2xl font-semibold tracking-tight text-[#102a43]">Dal documento alla conoscenza</h2>
      </div>
      <div class="grid gap-6 md:grid-cols-4">
        <div v-for="(step, index) in [
          ['01', 'Documento', 'Carica le immagini del tuo archivio.'],
          ['02', 'OCR', 'Il contenuto viene convertito in testo.'],
          ['03', 'Elaborazione', 'Il testo viene indicizzato semanticamente.'],
          ['04', 'Interrogazione', 'Cerca e poni domande alle tue fonti.'],
        ]" :key="step[0]" class="relative">
          <span class="text-xs font-bold tracking-widest text-[#9b8060]">{{ step[0] }}</span>
          <h3 class="mt-3 text-base font-semibold text-slate-800">{{ step[1] }}</h3>
          <p class="mt-2 text-sm leading-6 text-slate-500">{{ step[2] }}</p>
          <span v-if="index < 3" class="absolute right-0 top-2 hidden text-slate-300 md:block">→</span>
        </div>
      </div>
    </section>
  </div>
</template>
