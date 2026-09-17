# historyGenie - RAGFlow Test Environment

Ambiente essenziale di test e sviluppo per l'integrazione tra un'interfaccia frontend **Vue 3** e un backend **Spring Boot** collegato a **RAGFlow**.

## Architettura

- **Frontend (`src/`):** Vue 3 (Composition API) + TypeScript + Tailwind CSS + Vite.
  - `DocumentUpload.vue`: Form per l'upload di documenti con feedback di stato e invio via multipart a `/api/rag/upload`.
  - `RagChat.vue`: Interfaccia chat interattiva con cronologia messaggi e invio via JSON a `/api/rag/chat`.
  - `services/ragApi.ts`: Client per le chiamate REST al backend.
- **Backend (`backend/`):** Spring Boot 3.4 (Java 21)
  - `RagFlowController.java`: Endpoint REST (`POST /api/rag/upload`, `POST /api/rag/chat`).
  - `RagFlowService.java`: Client HTTP (`RestClient`) verso le API di RAGFlow.
  - `WebConfig.java`: Configurazione CORS per abilitare le richieste dal dev server di Vue (`http://localhost:5173`).
  - `application.properties`: Configurazione delle variabili d'ambiente di RAGFlow (`base.url`, `api.key`, `dataset.id`).

---

## Configurazione RAGFlow

Nel file `backend/src/main/resources/application.properties` (o tramite variabili d'ambiente):

```properties
ragflow.api.key=${RAGFLOW_API_KEY:your_api_key}
ragflow.base.url=${RAGFLOW_BASE_URL:http://127.0.0.1:9380}
ragflow.dataset.id=${RAGFLOW_DATASET_ID:your_dataset_id}
```

---

## Avvio del Progetto

### 1. Avvio Backend (Spring Boot)

```bash
cd backend
./mvnw spring-boot:run
# oppure (con Maven installato nel sistema):
mvn spring-boot:run
```
Il backend sarà in ascolto su `http://127.0.0.1:8081`.

### 2. Avvio Frontend (Vue 3)

```bash
npm install
npm run dev
```
Il frontend sarà in ascolto su `http://localhost:5173` e instraderà le chiamate `/api` verso il backend tramite il proxy Vite.
