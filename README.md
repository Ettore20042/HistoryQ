
# HistoryQ

> Piattaforma web per la conservazione, la trascrizione tramite OCR e la consultazione di documenti storici mediante intelligenza artificiale conversazionale (RAG).

HistoryQ consente di raccogliere ed elaborare collezioni documentali e storiche, estraendone il testo tramite **Churro OCR** e rendendole interrogabili attraverso un'interfaccia chat basata su **Retrieval-Augmented Generation (RAG)**. Il sistema gestisce l'intero ciclo di vita del documento (estrazione pagine, archiviazione fisica su storage a oggetti, trascrizione asincrona e indicizzazione vettoriale su **RAGFlow**), fornendo risposte contestuali arricchite con citazioni ed evidenziamento delle fonti originali.

---

## Architettura del Sistema


Vue 3 (SPA + TypeScript)
│
│  REST API (JSON) + Bearer JWT
▼
Spring Boot 3.4 (Java 21)
│
├──► MinIO (Object Storage - PDF, Immagini Pagine)
├──► PostgreSQL + Flyway + pgcrypto (Metadati, Pagine, Inviti, Notifiche)
├──► Churro OCR (FastAPI Microservice - Trascrizione Asincrona Batch)
└──► RAGFlow Engine (Dataset, Vector Indexing, Chat Completions)

| Componente | Tecnologia / Librerie |
|---|---|
| **Frontend** | Vue 3 (Composition API) · TypeScript · Vite · Tailwind CSS · Pinia · Vue Router |
| **Backend Core** | Spring Boot 3.4.1 · Java 21 · Spring Security · Spring Data JPA · Jackson |
| **Object Storage** | MinIO (Bucket `historygenie-documents`) via SDK Java 8.5 |
| **Database Relazionale** | PostgreSQL (con estensione `pgcrypto` per UUID e migrazioni **Flyway V1–V17**) |
| **Modulo OCR** | Churro OCR (Microservizio FastAPI / Python per elaborazione asincrona batch) |
| **RAG Engine** | RAGFlow Orchestrator (Parsing, Chunking, Embeddings e LLM Integration) |

---

## Caratteristiche Principali

- **Autenticazione Stateless:** Gestione utenti con Spring Security e token JWT (HS256) salvati in `localStorage`.
- **Disaccoppiamento Storage & Metadati:** Archiviazione fisica di PDF e singole pagine ad alta risoluzione su MinIO, con metadati e percorsi strutturati su PostgreSQL.
- **Pipeline OCR Asincrona (Churro OCR):** Scomposizione automatica dei PDF in immagini, invio batch asincrono al servizio OCR, tracciamento dello stato (`PROCESSING` → `OCR_PROCESSING` → `OCR_COMPLETED`) e salvataggio delle trascrizioni editabili per pagina.
- **Indicizzazione & Chat RAG (RAGFlow):** Sincronizzazione automatica del testo trascritto nei dataset RAGFlow, parsing/chunking vettoriale, gestione sessioni di chat e restituzione di risposte con citazioni trasparenti delle fonti.
- **Gestione Collezioni e Visibilità:** Organizzazione logica dei documenti in collezioni tematiche con supporto alla visibilità `PRIVATE` o `PUBLIC`.
- **Collaborazione nei Team:** Creazione di team di ricerca, sistema di inviti a scadenza (7 giorni, stati `PENDING`, `ACCEPTED`, `DECLINED`), notifiche di sistema in-app ed elaborazione congiunta delle risorse condivise.

---

## Prerequisiti

- **Java 21**
- **Maven 3.9+** (oppure utilizzare il wrapper `./mvnw` incluso)
- **Node.js LTS (v18+)** + npm
- **Docker** e **Docker Compose** (per avviare PostgreSQL, MinIO, Churro OCR e RAGFlow)

---

## Avvio Rapido

### 1. Servizi Infrastrutturali (Docker)

Assicurati che i servizi esterni siano in esecuzione. Esempio di configurazione dei moduli:

```bash
# Clone e avvio di RAGFlow
git clone [https://github.com/infiniflow/ragflow.git](https://github.com/infiniflow/ragflow.git)
cd ragflow
docker compose up -d

# Avvio di MinIO e PostgreSQL (se configurati tramite docker-compose locale)
docker compose up -d postgres minio churro-ocr

2. Backend (Spring Boot 3.4)
Configura le credenziali o le variabili d'ambiente in backend/src/main/resources/application.properties:
# Server
server.port=8081

# Database PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/historygenie
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}

# Flyway Migration
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true

# MinIO Object Storage
minio.url=${MINIO_URL:http://localhost:9000}
minio.access.key=${MINIO_ACCESS_KEY:minioadmin}
minio.secret.key=${MINIO_SECRET_KEY:minioadmin}
minio.bucket.name=historygenie-documents

# Churro OCR Service
ocr.service.url=${OCR_SERVICE_URL:http://localhost:8000}
ocr.engine.default=churro

# RAGFlow Engine
ragflow.base.url=${RAGFLOW_BASE_URL:http://localhost:9380}
ragflow.api.key=${RAGFLOW_API_KEY:your_api_key}

# JWT Security
app.jwt.secret=${JWT_SECRET:your_base64_256bit_secret_key}
app.jwt.expiration=86400000

Esegui il backend (le migrazioni Flyway verranno applicate automaticamente fino alla versione V17):
cd backend
./mvnw spring-boot:run

Il backend sarà in ascolto su http://localhost:8081.
3. Frontend (Vue 3 + Vite)
# Dalla root del progetto
npm install
npm run dev

Il dev server Vite sarà disponibile su http://localhost:5173. Le chiamate verso /api vengono proxate verso l'istanza Spring Boot su http://localhost:8081.
Endpoint Principali API REST
| Metodo | Endpoint | Descrizione |
|---|---|---|
| POST | /api/auth/register | Registrazione nuovo utente (ruolo ROLE_USER) |
| POST | /api/auth/login | Autenticazione e restituzione token JWT |
| GET | /api/collections | Lista delle collezioni dell'utente loggato o del team |
| POST | /api/collections | Creazione di una nuova collezione |
| POST | /api/documents/upload | Caricamento multipart documento (PDF/Immagini) |
| GET | /api/documents/{id}/status | Polling dello stato di elaborazione (OCR, RAGFlow, READY) |
| GET | /api/documents/{id}/pages | Lettura pagine estratte e trascrizioni OCR per pagina |
| PATCH | /api/documents/{id}/visibility | Modifica visibilità documento (PRIVATE / PUBLIC) |
| POST | /api/documents/{id}/chat | Interrogazione semantica RAG sul singolo documento |
| POST | /api/teams | Creazione di un nuovo team di ricerca |
| POST | /api/teams/{teamId}/invitations | Invio di un invito tramite email a un utente registrato |
| GET | /api/notifications | Lettura notifiche in-app dell'utente (inviti, documenti pronti) |
| GET | /api/public/documents | Consultazione pubblica dei documenti contrassegnati come PUBLIC |
Struttura del Progetto
HistoryQ/
├── src/                          # Frontend Vue 3 (TypeScript)
│   ├── assets/                   # Stili Tailwind CSS e asset grafici
│   ├── components/               # Componenti UI (Visore Documenti, Chat RAG, Team, NotificationCenter)
│   ├── services/                 # Moduli TypeScript per chiamata API (auth.ts, documentService.ts)
│   ├─             
│   ├── views/                    # Viste applicative (Dashboard, DocumentView, TeamView)
│   └── router/                   # Rotte e Navigation Guard JWT
├── backend/                      # Backend Spring Boot 3.4
│   └── src/main/
│       ├── java/it/unisa/historyq/
│       │   ├── config/           # SecurityConfig, MinioConfig, AsyncConfig
│       │   ├── controller/       # REST Controllers (Auth, Document, Collection, Team, Public)
│       │   ├── model/            # Entità JPA (User, Document, DocumentPage, Team, Invitation, etc.)
│       │   ├── repository/       # Spring Data JPA Repositories
│       │   ├── security/         # JwtAuthenticationFilter, JwtService
│       │   └── service/          # Business logic (DocumentProcessing, ChurroOcrService, RagFlowService)
│       └── resources/
│           ├── db/migration/     # Script di migrazione SQL Flyway (V1__init.sql ... V17__...)
│           └── application.properties
└── docker-compose.yml


Licenza
Questo progetto è rilasciato sotto licenza MIT. Per ulteriori dettagli, consultare il file LICENSE presente nella root del repository.

