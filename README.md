# HistoryGenie

> Piattaforma web per la consultazione di documenti storici tramite intelligenza artificiale conversazionale.

HistoryGenie permette di caricare collezioni di documenti storici e interrogarli attraverso un'interfaccia chat basata su **Retrieval-Augmented Generation (RAG)**. Il sistema recupera i passaggi rilevanti dai documenti indicizzati e genera risposte contestuali tramite un Large Language Model, citando le fonti utilizzate.

---

## Architettura

```
Vue 3 (SPA)
    │
    │  REST / WebSocket (STOMP)
    ▼
Spring Boot 3 + Spring Security
    │                    │
    ▼                    ▼
PostgreSQL           RAGFlow
                         │
                    LLM (Ollama / API esterna)
```

| Layer | Tecnologia |
|---|---|
| Frontend | Vue 3 (Composition API) · TypeScript · Tailwind CSS · Vite |
| Backend | Spring Boot 3.4 · Spring Security · Java 21 |
| Database | PostgreSQL |
| Pipeline RAG | RAGFlow (Docker) |
| LLM (sviluppo) | Ollama (LLaMA 3.1 8B) |
| LLM (produzione) | OpenAI API / Anthropic API |

---

## Prerequisiti

- **Java 21**
- **Maven** (o usare lo script `mvnw` incluso)
- **Node.js** LTS + npm
- **Docker** e **Docker Compose** (per RAGFlow e PostgreSQL)
- Un'istanza **RAGFlow** accessibile (vedi sezione configurazione)

---

## Avvio rapido

### 1. RAGFlow

```bash
# Clona e avvia RAGFlow
git clone https://github.com/infiniflow/ragflow.git
cd ragflow
docker compose up -d
```

RAGFlow sarà disponibile su `http://localhost:9380`. Dalla sua interfaccia amministrativa:
1. Crea un dataset
2. Genera una API key
3. Annota dataset ID e API key per la configurazione del backend

### 2. Backend (Spring Boot)

Configura le credenziali in `backend/src/main/resources/application.properties`:

```properties
# RAGFlow
ragflow.api.key=${RAGFLOW_API_KEY:your_api_key}
ragflow.base.url=${RAGFLOW_BASE_URL:http://localhost:9380}
ragflow.dataset.id=${RAGFLOW_DATASET_ID:your_dataset_id}

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/historygenie
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}

# JWT
app.jwt.secret=${JWT_SECRET:your_secret_key}
app.jwt.expiration=86400000
```

Avvio:

```bash
cd backend
./mvnw spring-boot:run
```

Il backend ascolta su `http://localhost:8081`.

### 3. Frontend (Vue 3)

```bash
# dalla root del progetto
npm install
npm run dev
```

Il dev server Vite ascolta su `http://localhost:5173` e proxia automaticamente le chiamate `/api` verso il backend.

---

## Endpoint principali

| Metodo | Endpoint | Descrizione |
|---|---|---|
| `POST` | `/api/auth/login` | Autenticazione, restituisce JWT |
| `GET` | `/api/collections` | Lista collezioni dell'utente |
| `POST` | `/api/collections` | Crea nuova collezione |
| `POST` | `/api/documents/upload` | Carica documento e lo indicizza su RAGFlow |
| `GET` | `/api/documents/{id}/status` | Stato elaborazione documento in RAGFlow |
| `POST` | `/api/chat/{conversationId}/message` | Invia messaggio, risposta via RAG |
| `WS` | `/ws/collection/{id}/chat` | Chat collaborativa in tempo reale (STOMP) |

---

## Struttura del progetto

```
historygenie/
├── src/                        # Frontend Vue 3
│   ├── components/
│   │   ├── DocumentUpload.vue
│   │   ├── RagChat.vue
│   │   └── CollectionChat.vue  # Chat collaborativa
│   ├── services/
│   │   ├── ragApi.ts
│   │   └── wsClient.ts         # Client WebSocket STOMP
│   ├── stores/                 # Pinia stores
│   └── router/
├── backend/                    # Spring Boot
│   └── src/main/java/
│       ├── controller/
│       ├── service/
│       │   └── RagFlowClient.java
│       ├── repository/
│       └── security/
└── docker-compose.yml
```

---

## Variabili d'ambiente

| Variabile | Default | Descrizione |
|---|---|---|
| `RAGFLOW_API_KEY` | — | API key generata da RAGFlow |
| `RAGFLOW_BASE_URL` | `http://localhost:9380` | URL dell'istanza RAGFlow |
| `RAGFLOW_DATASET_ID` | — | ID del dataset RAGFlow di default |
| `DB_USER` | `postgres` | Utente PostgreSQL |
| `DB_PASSWORD` | `postgres` | Password PostgreSQL |
| `JWT_SECRET` | — | Chiave segreta per la firma dei JWT |

---

## Stato del progetto

| Funzionalità | Stato |
|---|---|
| Upload documenti e indicizzazione RAGFlow | ✅ MVP |
| Interfaccia chat RAG | ✅ MVP |
| Autenticazione JWT | 🔧 In sviluppo |
| Gestione collezioni e permessi | 🔧 In sviluppo |
| Chat collaborativa tra utenti (WebSocket) | 📋 Pianificato |
| Metadati strutturati e tagging | 📋 Pianificato |
| Import batch e OCR avanzato | 📋 Pianificato |

---

## Contribuire

1. Fork del repository
2. Crea un branch: `git checkout -b feature/nome-funzionalita`
3. Commit descrittivi: `git commit -m "feat: descrizione"`
4. Apri una Pull Request verso `main`

Per bug e proposte usa la sezione [Issues](../../issues).

---

## Licenza

Da definire — specificare il file `LICENSE` nella root del repository.