# HistoryQ — Deployment Guide

Guida operativa per installare e avviare HistoryQ su Ubuntu Server mediante Docker Compose.

## 1. Architettura

Il deployment è composto dai seguenti componenti:

| Componente | Tecnologia | Esecuzione |
|---|---|---|
| Frontend | Vue 3, TypeScript, Tailwind CSS | Docker |
| Backend | Java 21, Spring Boot | Docker |
| Database | PostgreSQL 16 | Docker |
| Object Storage | Silo | Docker |
| Reverse Proxy | Nginx | Docker |
| RAG Engine | RAGFlow v0.27.1 | Docker Compose separato |
| LLM Runtime | Ollama | Ubuntu Server |
| Chat Model | Qwen3 32B | Ollama |
| Embedding Model | Modello dedicato | Provider configurato in RAGFlow |
| OCR | FastAPI / Tesseract | Servizio universitario esterno |

HistoryQ e RAGFlow utilizzano due progetti Docker Compose indipendenti, collegati attraverso la rete Docker esterna `historyq-ragflow`.

Ollama viene eseguito direttamente sul server Ubuntu, al di fuori di Docker.

### Porte utilizzate

| Servizio | Porta | Accesso |
|---|---:|---|
| HistoryQ | 18080 | Porta pubblicata sulla VM |
| RAGFlow Web UI | 18081 | Solo localhost, accesso tramite SSH |
| RAGFlow API | 9380 | Rete Docker interna |
| PostgreSQL | 5432 | Rete Docker interna |
| Silo | 9000 / 9001 | Rete Docker interna |
| Backend | 8081 | Rete Docker interna |

## 2. Prerequisiti

Installare gli strumenti di base:

```bash
sudo apt update
sudo apt install -y git curl ca-certificates
```

Installare Docker Engine e Docker Compose seguendo la documentazione ufficiale:

https://docs.docker.com/engine/install/ubuntu/

Verificare l'installazione:

```bash
docker --version
docker compose version
```

Avviare Docker:

```bash
sudo systemctl enable --now docker
```

## 3. Preparazione delle directory sul server

Accedere al server Ubuntu tramite SSH e creare la directory destinata a contenere i progetti:

```bash
mkdir -p ~/deploy
cd ~/deploy
```

La directory `~/deploy` sarà la cartella principale in cui verranno collocati HistoryQ e RAGFlow.

### 3.1. Copia di HistoryQ

Trasferire sul server la cartella `HistoryQ-deployment-repo/` ricevuta e posizionarla all'interno di `~/deploy/`.

Al termine, la struttura dovrà essere:

```text
~/deploy/
└── HistoryQ-deployment-repo/
    ├── .env
    ├── compose.yaml
    ├── Dockerfile
    ├── backend/
    ├── deployment/
    ├── src/
    └── ...
```

Il file `.env` di HistoryQ deve essere presente nella directory principale del progetto, accanto a `compose.yaml`.

## 4. Installazione di RAGFlow

RAGFlow viene installato separatamente da HistoryQ.

Posizionarsi nella directory di deployment:

```bash
cd ~/deploy
```

Clonare la versione utilizzata dal progetto:

```bash
git clone -b v0.27.1 https://github.com/infiniflow/ragflow.git
```

Accedere alla directory Docker:

```bash
cd ~/deploy/ragflow/docker
```

### 4.1 Creazione della rete Docker

HistoryQ e RAGFlow comunicano attraverso una rete Docker esterna condivisa.

Crearla prima di avviare i servizi:

```bash
docker network create historyq-ragflow
```

Se la rete esiste già, non è necessario ricrearla.

### 4.2 Configurazione Compose

HistoryQ fornisce un file di override per configurare RAGFlow e collegarlo alla rete condivisa.

Verificare che il file sia presente:

```bash
ls ~/deploy/HistoryQ-deployment-repo/deployment/ragflow/compose.override.yaml
```

Verificare la configurazione risultante:

```bash
docker compose \
  -p ragflow \
  -f docker-compose.yml \
  -f ~/deploy/HistoryQ-deployment-repo/deployment/ragflow/compose.override.yaml \
  config --services
```

### 4.3 Avvio di RAGFlow

Avviare i servizi:

```bash
docker compose \
  -p ragflow \
  -f docker-compose.yml \
  -f ~/deploy/HistoryQ-deployment-repo/deployment/ragflow/compose.override.yaml \
  up -d
```

Verificare lo stato:

```bash
docker compose -p ragflow ps
```

Controllare eventuali errori:

```bash
docker compose -p ragflow logs --tail=100
```

## 5. Configurazione dei modelli in RAGFlow

Accedere all'interfaccia RAGFlow tramite il tunnel SSH descritto nella sezione 9.

Al primo accesso, se non esiste ancora un account, procedere con la registrazione.

Effettuare il login con le credenziali appena create.

Non è necessario utilizzare un account RAGFlow preesistente: la registrazione crea un account sulla nuova installazione del server.

Nota: l'indirizzo email viene utilizzato come identificativo dell'account. Non è necessariamente richiesta la verifica tramite email, poiché l'installazione standard non prevede automaticamente un servizio di posta configurato.

Aprire:

**User Settings (icona in alto a destra) → Model Providers**

RAGFlow supporta diversi provider per i modelli LLM e di embedding. È possibile utilizzare sia provider locali, installati e raggiungibili attraverso la propria infrastruttura, sia provider remoti che espongono API accessibili tramite Internet.

La scelta del provider dipende dalle risorse hardware disponibili, dalle prestazioni richieste e dai servizi esterni che si intendono utilizzare.

Tra i provider utilizzabili sono presenti, ad esempio:

- **Groq:** permette di utilizzare modelli LLM attraverso API remote con un free tier.
- **Jina:** permette di utilizzare modelli dedicati alla generazione di embedding attraverso API.

Per configurare un provider, selezionarlo nella sezione Model Providers e inserire le informazioni richieste, come API Key, Base URL e modello, in base al servizio utilizzato.

**Configurazione di Groq**

Quando si utilizza Groq come provider, è necessario aggiungere prima il modello `openai/gpt-oss-120b` nella sezione Model Providers.

Successivamente, si dovrà procedere con la verifica dell' api key, una volta verificata, sarà possibile configurarlo come modello LLM e utilizzarlo nelle impostazioni di sistema.

**Configurazione dei modelli predefiniti**

Dopo aver aggiunto e configurato i modelli desiderati, accedere a:

**Model Providers → System Model Settings**

In questa sezione è necessario impostare i modelli predefiniti utilizzati da RAGFlow.

In particolare, configurare:

- **Default LLM:** modello predefinito utilizzato per la generazione delle risposte. Ad esempio, `openai/gpt-oss-120b`.
- **Default Embedding Model:** modello predefinito utilizzato per convertire i documenti in rappresentazioni vettoriali, necessarie per effettuare le ricerche semantiche all'interno delle Knowledge Base. Ad esempio, `jina-embedding-v4`.

I modelli indicati sono esempi: è possibile utilizzare modelli differenti, purché siano stati precedentemente aggiunti e configurati nel rispettivo provider.

Verificare che entrambi i modelli siano correttamente selezionati e salvare le impostazioni.

## 6. Configurazione dell'integrazione HistoryQ – RAGFlow

HistoryQ comunica con RAGFlow attraverso la rete Docker condivisa.

L'indirizzo utilizzato dal backend è:

```text
http://ragflow-cpu:9380
```

La configurazione è già predisposta nel file `.env` di HistoryQ.

È necessario generare una nuova API Key attraverso l'interfaccia Web di RAGFlow e inserirla nella variabile corrispondente del file `.env` di HistoryQ:

```dotenv
RAGFLOW_API_KEY=NUOVA_API_KEY
```

La chiave deve corrispondere all'istanza RAGFlow effettivamente utilizzata.

## 7. Avvio di HistoryQ

Accedere alla directory del repository:

```bash
cd ~/deploy/HistoryQ-deployment-repo
```

Verificare i servizi definiti:

```bash
docker compose -p historyq config --services
```

I servizi attesi sono:

```text
postgres
silo
backend
frontend
nginx
```

Verificare che i volumi persistenti siano presenti nella configurazione:

```bash
docker compose -p historyq config --volumes
```

Avviare HistoryQ:

```bash
docker compose -p historyq up -d --build
```

Verificare lo stato dei container:

```bash
docker compose -p historyq ps
```

Visualizzare i log:

```bash
docker compose -p historyq logs -f
```

Per controllare un singolo servizio:

```bash
docker compose -p historyq logs -f backend
```

## 8. Accesso alle applicazioni

### 8.1 HistoryQ

HistoryQ è raggiungibile direttamente tramite il seguente indirizzo:

```text
http://INDIRIZZO_SERVER:18080/
```

Sostituire `INDIRIZZO_SERVER` con l'indirizzo IP o hostname effettivo del server Ubuntu.

Non è necessario configurare un tunnel SSH per accedere a questa interfaccia.

Nota: gli indirizzi utilizzati da HistoryQ e dal servizio OCR sono configurati nel file `.env`. Verificare che siano corretti e, se necessario, modificarli in base agli indirizzi effettivi del server e del servizio OCR disponibili nell'ambiente di deployment.

### 8.2 RAGFlow

Aprire un secondo terminale Windows:

```powershell
ssh -L 18081:127.0.0.1:18081 -p PORTA_SSH UTENTE@INDIRIZZO_SERVER
```

Sostituire i parametri con i valori effettivi del server:

- `PORTA_SSH` – Porta SSH del server Ubuntu.
- `UTENTE` – Nome utente per l'accesso SSH.
- `INDIRIZZO_SERVER` – Indirizzo IP o hostname del server Ubuntu.

Aprire:

```text
http://127.0.0.1:18081
```

## 9. Persistenza dei dati

HistoryQ utilizza volumi Docker per conservare i dati applicativi.

| Volume | Contenuto |
|---|---|
| `historyq-postgres-data` | Database PostgreSQL |
| `historyq-silo-data` | Documenti archiviati in Silo |

Sono inoltre utilizzati i seguenti identificatori persistenti:

- Database PostgreSQL: `historyq`.
- Bucket Silo: `historyq-documents`.

È fondamentale mantenere il nome del progetto Compose:

```bash
-p historyq
```

Questo consente di mantenere coerenti i nomi dei container e dei volumi.

Non utilizzare `docker compose down -v` durante gli aggiornamenti ordinari: il comando può eliminare i volumi persistenti e causare la perdita dei dati.

Prima di operazioni invasive è necessario predisporre un backup del database e dei documenti.

## 10. Gestione del deployment

### Visualizzare i container HistoryQ

```bash
docker compose -p historyq ps
```

### Visualizzare i container RAGFlow

```bash
cd ~/deploy/ragflow/docker

docker compose -p ragflow ps
```

### Visualizzare i log HistoryQ

```bash
cd ~/deploy/HistoryQ-deployment-repo

docker compose -p historyq logs -f
```

### Visualizzare i log RAGFlow

```bash
cd ~/deploy/ragflow/docker

docker compose -p ragflow logs -f
```

### Visualizzare l'utilizzo delle risorse

```bash
docker stats
```

### Riavviare HistoryQ

```bash
cd ~/deploy/HistoryQ-deployment-repo

docker compose -p historyq restart
```

### Riavviare RAGFlow

```bash
cd ~/deploy/ragflow/docker

docker compose -p ragflow restart
```

### Riavviare Ollama

```bash
sudo systemctl restart ollama
```

## 11. Risoluzione dei problemi

### HistoryQ non raggiunge RAGFlow

Verificare che entrambi i progetti siano attivi:

```bash
docker ps
```

Verificare la rete condivisa:

```bash
docker network inspect historyq-ragflow
```

Controllare che il backend utilizzi:

```text
http://ragflow-cpu:9380
```

Verificare che `RAGFLOW_API_KEY` corrisponda a una chiave valida dell'istanza RAGFlow utilizzata.

### RAGFlow non raggiunge Ollama

Verificare il servizio:

```bash
sudo systemctl status ollama
```

Verificare la porta:

```bash
ss -lntp | grep 11434
```

Verificare i modelli:

```bash
ollama list
```

Controllare che il Base URL configurato in RAGFlow utilizzi l'indirizzo del server raggiungibile dai container e non `localhost`.

### Problemi di avvio dei container

Verificare lo stato:

```bash
docker ps -a
```

Controllare i log:

```bash
docker compose -p historyq logs --tail=100
```

Per RAGFlow:

```bash
docker compose -p ragflow logs --tail=100
```

### Problemi OCR

HistoryQ utilizza il servizio OCR universitario esterno configurato nel file `.env`.
