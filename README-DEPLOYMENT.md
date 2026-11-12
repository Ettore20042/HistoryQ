# Deployment Docker di HistoryQ

Questa guida descrive il deployment di HistoryQ su un server Linux utilizzando Docker Compose e due progetti distinti: uno per HistoryQ e uno per RAGFlow.

## 1. Architettura

L'applicazione utilizza la seguente architettura:

```text
             Utente
                |
          Server Linux
                |
       historyq-nginx
         porta 18080
                |
         +------+------+
         |             |
      Frontend       Backend
       (SPA)          8081
                       |
          +------------+------------+
          |            |            |
       PostgreSQL    Silo        RAGFlow
                     HistoryQ     9380
                                    |
                              OCR esterno
```

I servizi comunicano attraverso reti Docker private.

HistoryQ e RAGFlow utilizzano due progetti Compose separati. RAGFlow mantiene le proprie dipendenze (MySQL, Elasticsearch, Valkey e Silo) nel repository ufficiale.

HistoryQ utilizza invece un Silo indipendente, con credenziali, configurazione e volume dedicati. Il bucket `historygenie-documents` rimane separato da quello utilizzato da RAGFlow.

PostgreSQL e MySQL svolgono funzioni differenti e non sono intercambiabili.

## 2. Requisiti

Il server deve disporre di:

- Sistema operativo Linux.
- Docker Engine.
- Docker Compose v2.
- Almeno 8 vCPU.
- Almeno 16 GB di RAM.
- Spazio disco sufficiente per documenti, database e indici di ricerca.

Installare Docker Engine e Docker Compose seguendo la documentazione ufficiale.

Verificare l'installazione:

```bash
docker --version
docker compose version
```

## 3. Preparazione di HistoryQ

Dalla directory principale della repository, verificare che il file `example.env` contenga tutte le variabili d'ambiente necessarie al deployment.

Creare il file `.env` utilizzato da Docker Compose:

```bash
cp example.env .env
chmod 600 .env
```

Il file `example.env` contiene già la configurazione delle variabili necessarie, tra cui:

- Credenziali PostgreSQL.
- Credenziali Silo HistoryQ.
- JWT secret.
- API key RAGFlow.
- Token e URL del servizio OCR.
- Configurazione delle porte e degli endpoint.

**Importante:** verificare che le credenziali e i secret presenti siano valori definitivi e sicuri per l'ambiente di produzione. Non utilizzare password o token dimostrativi.

Non committare il file `.env` nel repository.

Creare la rete Docker condivisa:

```bash
docker network create historyq-ragflow
```

Questa operazione deve essere eseguita una sola volta.

## 4. Installazione di RAGFlow

RAGFlow viene installato separatamente da HistoryQ, utilizzando la versione 0.27.1.

Clonare il repository ufficiale:

```bash
git clone --branch v0.27.1 --depth 1 https://github.com/infiniflow/ragflow.git
cd ragflow/docker
```

Configurare il file `.env` ufficiale di RAGFlow:

- Immagine `infiniflow/ragflow:v0.27.1`.
- `DEVICE=cpu`.
- `DOC_ENGINE=elasticsearch`.
- Password uniche per MySQL, Elasticsearch, Silo e Valkey.

Utilizzare l'override presente nella repository HistoryQ:

```bash
docker compose \
  -f docker-compose.yml \
  -f /percorso/HistoryQ/deployment/ragflow/compose.override.yaml \
  config
```

Se la configurazione è corretta, avviare RAGFlow:

```bash
docker compose \
  -f docker-compose.yml \
  -f /percorso/HistoryQ/deployment/ragflow/compose.override.yaml \
  up -d
```

L'override:

- Collega RAGFlow alla rete Docker `historyq-ragflow`.
- Rimuove le porte pubblicate sull'host.
- Mantiene tutte le dipendenze ufficiali di RAGFlow.
- Consente al backend HistoryQ di raggiungere RAGFlow tramite la rete Docker.

Attendere che i container siano operativi e configurare l'API key da utilizzare in HistoryQ.

## 5. Installazione di HistoryQ

Dalla directory principale della repository:

Verificare la configurazione:

```bash
docker compose config
```

Costruire le immagini:

```bash
docker compose build
```

Avviare i servizi:

```bash
docker compose up -d
```

Verificare lo stato:

```bash
docker compose ps
```

HistoryQ sarà raggiungibile all'indirizzo:

```text
http://IP_DEL_SERVER:18080
```

La porta `18080` dell'host viene inoltrata alla porta `80` del container Nginx.

Le porte di PostgreSQL, Silo e backend non vengono pubblicate sull'host. I servizi comunicano utilizzando i rispettivi nomi DNS Docker:

- `postgres`
- `silo`
- `ragflow-cpu`

Flyway applica automaticamente le migration del database durante l'avvio del backend.

## 6. Gestione quotidiana

Visualizzare i log di HistoryQ:

```bash
docker compose logs -f backend
docker compose logs -f nginx
```

Visualizzare i log di RAGFlow, dalla directory `ragflow/docker`:

```bash
docker compose \
  -f docker-compose.yml \
  -f /percorso/HistoryQ/deployment/ragflow/compose.override.yaml \
  logs -f ragflow-cpu
```

Riavviare HistoryQ:

```bash
docker compose restart
```

Aggiornare i container:

```bash
docker compose pull
docker compose up -d
```

Per RAGFlow mantenere il tag `v0.27.1` finché non viene verificata la compatibilità con una versione successiva.

**Attenzione:** non utilizzare `docker compose down -v`, perché questa operazione può eliminare i volumi contenenti i dati persistenti.

Camiare l'indirizzo in example.env `APP_CORS_ALLOWED_ORIGIN=http://water.cluelab.di.unisa.it:18080`



creare il bucket Silo di HistoryQ:
docker run --rm \
--network historyq_historyq-internal \
--entrypoint /bin/sh \
-e MINIO_ACCESS_KEY="$(docker compose exec -T silo printenv MINIO_ROOT_USER | tr -d '\r\n')" \
-e MINIO_SECRET_KEY="$(docker compose exec -T silo printenv MINIO_ROOT_PASSWORD | tr -d '\r\n')" \
minio/mc -c '
mc alias set local http://silo:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" &&
mc mb --ignore-existing local/historygenie-documents &&
mc ls local
'


## 7. Backup e ripristino

È necessario effettuare backup periodici di:

- Database PostgreSQL di HistoryQ.
- Volume Silo di HistoryQ.
- Volume MySQL di RAGFlow.
- Volume Elasticsearch di RAGFlow.
- Volume Valkey di RAGFlow.
- Volume Silo di RAGFlow.

Esempio di backup PostgreSQL:

```bash
docker compose exec -T postgres \
  pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" \
  > historyq.sql
```

Backup del volume Silo di HistoryQ:

```bash
docker run --rm \
  -v historyq_historyq-silo-data:/data \
  -v "$PWD:/backup" \
  alpine \
  tar czf /backup/historyq-silo-data.tgz -C /data .
```

I backup devono essere conservati in una destinazione esterna e protetta.

Verificare periodicamente il ripristino dei dati su un ambiente separato.

## 8. Diagnostica

**HistoryQ non si avvia**

Controllare i log:

```bash
docker compose logs backend
docker compose logs nginx
```

Verificare le credenziali nel file `.env` e lo stato di PostgreSQL.

**RAGFlow non è raggiungibile**

Verificare che:

- Il container `ragflow-cpu` sia operativo.
- L'API key sia corretta.
- HistoryQ e RAGFlow siano collegati alla rete `historyq-ragflow`.

**Upload dei documenti non funzionante**

Verificare `client_max_body_size`, i limiti multipart di 50 MB e lo spazio disponibile nel volume Silo.

**OCR non funzionante**

Verificare URL HTTPS, token Bearer, connettività verso il servizio esterno e timeout configurati.

**Frontend restituisce 404 dopo il refresh**

Verificare la configurazione Nginx e il fallback verso `index.html` in `frontend.conf`.

## Configurazione e deployment

Questa sezione descrive le configurazioni necessarie per il corretto funzionamento dei servizi di HistoryQ in ambiente Docker, le integrazioni esterne e le principali misure di sicurezza da adottare durante il deployment.

### 1. Object Storage – Silo

HistoryQ utilizza Silo come servizio di object storage compatibile con le API S3/MinIO, per la memorizzazione dei documenti caricati dagli utenti.

La configurazione prevede:

- **Servizio:** `silo`
- **API endpoint interno:** `http://silo:9000`
- **Console amministrativa:** porta `9001`
- **Volume persistente:** `historyq-silo-data`
- **Bucket:** `historygenie-documents`

Le variabili d'ambiente necessarie sono:

```env
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
MINIO_BUCKET=historygenie-documents
MINIO_URL=http://silo:9000
```

Il bucket deve essere creato prima di effettuare il caricamento dei documenti. La sola configurazione della variabile `MINIO_BUCKET` non comporta la sua creazione automatica.

Per inizializzare il bucket è possibile utilizzare il client MinIO (`mc`):

```bash
docker run --rm \
  --network historyq_historyq-internal \
  --entrypoint /bin/sh \
  -e MINIO_ACCESS_KEY="$(docker compose exec -T silo printenv MINIO_ROOT_USER | tr -d '\r\n')" \
  -e MINIO_SECRET_KEY="$(docker compose exec -T silo printenv MINIO_ROOT_PASSWORD | tr -d '\r\n')" \
  minio/mc -c '
    mc alias set local http://silo:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" &&
    mc mb --ignore-existing local/historygenie-documents &&
    mc ls local
  '
```

Il comando crea il bucket soltanto se non esiste già e successivamente ne verifica la presenza.

**Nota:** il nome della rete Docker deve corrispondere a quello effettivamente creato da Docker Compose.

### 2. Database – PostgreSQL

HistoryQ utilizza PostgreSQL 16 come database relazionale per la gestione dei dati applicativi.

La configurazione viene definita attraverso le seguenti variabili:

```env
POSTGRES_DB=historygenie
POSTGRES_USER=historygenie
POSTGRES_PASSWORD=...

SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/historygenie
SPRING_DATASOURCE_USERNAME=historygenie
SPRING_DATASOURCE_PASSWORD=...
```

Le credenziali utilizzate dal database e dal backend devono essere coerenti.

Il servizio PostgreSQL utilizza un volume Docker persistente:

```yaml
volumes:
  - historyq-postgres-data:/var/lib/postgresql/data
```

Il backend utilizza Flyway per la gestione automatica delle migrazioni dello schema del database.

Il database è accessibile internamente tramite il nome del servizio Docker `postgres` e non deve essere esposto direttamente su Internet.

**Attenzione:** modificare `POSTGRES_PASSWORD` nel file `.env` non modifica automaticamente la password di un database già inizializzato. In questo caso è necessario aggiornare la password anche all'interno di PostgreSQL.

### 3. Backend – Spring Boot

Il backend Spring Boot gestisce la logica applicativa, l'autenticazione, l'accesso al database, la gestione dei documenti e le comunicazioni con i servizi esterni.

Le principali configurazioni sono:

```env
JWT_SECRET=...
JWT_EXPIRATION=...

SPRING_DATASOURCE_URL=...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...

MINIO_URL=http://silo:9000
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
MINIO_BUCKET=historygenie-documents

RAGFLOW_BASE_URL=...
RAGFLOW_API_KEY=...

OCR_SERVICE_URL=...
OCR_API_TOKEN=...
OCR_CONNECT_TIMEOUT=...
OCR_READ_TIMEOUT=...
OCR_JOB_TIMEOUT=...
OCR_JOB_POLL_INTERVAL=...

APP_CORS_ALLOWED_ORIGIN=http://water.cluelab.di.unisa.it:18080
```

Il backend utilizza la rete interna Docker per comunicare con PostgreSQL e Silo.

La porta applicativa `8081` viene esposta esclusivamente all'interno della rete Docker e le richieste provenienti dal frontend vengono inoltrate tramite Nginx.

Il sistema di autenticazione utilizza JWT (JSON Web Token). La variabile `JWT_SECRET` deve contenere un valore casuale e sufficientemente robusto.

### 4. Integrazione con RAGFlow

HistoryQ utilizza RAGFlow per le funzionalità di Retrieval-Augmented Generation (RAG), consentendo l'interrogazione semantica delle collezioni documentali.

L'integrazione avviene attraverso le API HTTP di RAGFlow.

Configurazione:

```env
RAGFLOW_BASE_URL=...
RAGFLOW_API_KEY=...
```

Poiché HistoryQ e RAGFlow vengono eseguiti tramite progetti Docker Compose distinti, è necessario configurare una rete Docker condivisa.

La rete utilizzata è:

```yaml
networks:
  historyq-ragflow:
    name: historyq-ragflow
    external: true
```

Il backend HistoryQ deve essere collegato a questa rete, così come il servizio RAGFlow che espone le API.

L'indirizzo configurato in `RAGFLOW_BASE_URL` deve utilizzare il nome DNS effettivo del servizio RAGFlow e la relativa porta interna.

La chiave API deve essere generata tramite l'interfaccia di RAGFlow e mantenuta riservata.

**Nota:** RAGFlow utilizza una propria infrastruttura di storage e database, indipendente da PostgreSQL e Silo utilizzati da HistoryQ.

### 5. Servizio OCR – FastAPI

Il servizio OCR è responsabile dell'estrazione del testo dai documenti caricati, utilizzando Tesseract e le componenti di elaborazione implementate nel servizio Python.

Il backend Spring Boot comunica con il servizio attraverso API HTTP.

Configurazione:

```env
OCR_SERVICE_URL=...
OCR_API_TOKEN=...
OCR_CONNECT_TIMEOUT=...
OCR_READ_TIMEOUT=...
OCR_JOB_TIMEOUT=...
OCR_JOB_POLL_INTERVAL=...
```

È necessario verificare che:

- Il servizio FastAPI sia correttamente avviato.
- L'endpoint configurato in `OCR_SERVICE_URL` sia raggiungibile dal container backend.
- Il token configurato in `OCR_API_TOKEN` corrisponda a quello previsto dal servizio OCR.
- I timeout siano adeguati alla dimensione dei documenti e al numero di pagine da elaborare.

Il servizio OCR deve essere raggiungibile attraverso la rete Docker prevista dalla configurazione del deployment.

### 6. Frontend e Reverse Proxy – Nginx

Il frontend Vue 3 viene servito tramite Nginx, che svolge anche il ruolo di reverse proxy verso il backend Spring Boot.

L'architettura prevede:

- Frontend: porta interna `80`.
- Backend: porta interna `8081`.
- Nginx: porta interna `80`, pubblicata sull'host tramite la variabile `HISTORYQ_HTTP_PORT`.

Esempio di configurazione:

```env
HISTORYQ_HTTP_PORT=18080
```

Nginx inoltra le richieste dirette agli endpoint `/api/` verso il servizio `backend:8081`, mentre le altre richieste vengono gestite dal frontend.

Il frontend deve utilizzare percorsi API relativi, ad esempio:

```text
/api/auth/login
/api/auth/register
/api/documents
```

Questo permette di utilizzare lo stesso dominio e la stessa origine per frontend e backend, semplificando la configurazione CORS.

La variabile `APP_CORS_ALLOWED_ORIGIN` deve corrispondere esattamente all'origine pubblica utilizzata per accedere all'applicazione, senza percorsi aggiuntivi o slash finale.

Per un deployment esposto pubblicamente è consigliata la configurazione HTTPS mediante certificato TLS.

### 7. Persistenza e backup

I dati persistenti di HistoryQ sono conservati attraverso volumi Docker dedicati:

| Servizio | Volume | Contenuto |
|---|---|---|
| PostgreSQL | `historyq-postgres-data` | Database applicativo |
| Silo | `historyq-silo-data` | Documenti caricati |

I volumi permettono di mantenere i dati anche quando i container vengono arrestati o ricreati.

È necessario predisporre una strategia di backup periodica che includa:

- Backup del database PostgreSQL.
- Backup dei dati contenuti nel volume Silo.
- Backup dei file di configurazione necessari al ripristino del deployment.

La persistenza tramite volumi Docker non sostituisce una strategia di backup.

**Attenzione:** evitare l'utilizzo di `docker compose down -v` durante le normali operazioni di manutenzione, poiché il comando elimina anche i volumi associati al progetto.

### 8. Sicurezza del deployment

Per ridurre i rischi legati all'esposizione dei servizi, è necessario adottare alcune precauzioni:

- Utilizzare password robuste e credenziali differenti per i diversi servizi.
- Non pubblicare direttamente le porte di PostgreSQL e Silo su Internet.
- Non esporre direttamente la porta applicativa del backend.
- Proteggere le API attraverso autenticazione e autorizzazione.
- Utilizzare HTTPS per le comunicazioni con il frontend.
- Mantenere le API key e i token fuori dal repository Git.
- Inserire il file `.env` nel `.gitignore`.
- Evitare di utilizzare credenziali predefinite negli ambienti di produzione.

### 9. Verifica dello stato dei servizi

Per controllare lo stato dei container:

```bash
docker compose ps
```

Per visualizzare le reti Docker:

```bash
docker network ls
```

Per consultare i log dei servizi principali:

```bash
docker compose logs --tail=50 backend silo postgres
```

Per seguire in tempo reale i log del backend:

```bash
docker compose logs -f backend
```

Per verificare la presenza del bucket Silo è possibile utilizzare il comando `mc ls local` dopo aver configurato l'alias MinIO.

