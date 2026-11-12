# HistoryQ – Deployment Guide

Guida all'installazione e all'avvio di **HistoryQ** su Ubuntu tramite Docker Compose, con RAGFlow come servizio esterno per la gestione delle knowledge base e l'interrogazione semantica.

## 1. Architettura

HistoryQ è composto dai seguenti servizi:

| Servizio | Tecnologia | Funzione |
|---|---|---|
| Frontend | Vue 3, TypeScript, Tailwind | Interfaccia web |
| Backend | Java 21, Spring Boot | API REST e autenticazione JWT |
| Database | PostgreSQL 16 | Persistenza dei dati |
| Object storage | Silo (MinIO-compatible) | Archiviazione dei documenti |
| OCR | FastAPI / Tesseract | Estrazione del testo dai documenti |
| RAG | RAGFlow | Elaborazione e interrogazione semantica |

I servizi HistoryQ e RAGFlow vengono avviati tramite due progetti Docker Compose distinti.

### Rete Docker

I due progetti comunicano attraverso una rete Docker esterna denominata `historyq-ragflow`.

Il backend HistoryQ raggiunge RAGFlow tramite il nome del servizio Docker:

```text
http://ragflow-cpu:9380
```

## 2. Requisiti

Verificare l'installazione:

```bash
docker --version
docker compose version
git --version
```

## 3. Preparazione dell'ambiente

Creare la directory di deployment:

```bash
mkdir -p ~/deploy/HistoryQServer
mkdir -p ~/deploy
```

Copiare il pacchetto di deployment HistoryQ nella seguente posizione:

```text
~/deploy/HistoryQServer/HistoryQ-admin-package
```

Il pacchetto deve contenere almeno:

```text
HistoryQ-admin-package/
├── compose.yaml
├── .env
├── backend/
├── frontend/
└── deployment/
    └── ragflow/
        └── compose.override.yaml
```

Proteggere il file:

```bash
cd ~/deploy/HistoryQServer/HistoryQ-admin-package
chmod 600 .env
```

Non pubblicare il file `.env` su GitHub e non inserirlo nei repository.

## 4. Installazione e avvio di RAGFlow

RAGFlow viene utilizzato come servizio indipendente. La versione di riferimento è **v0.27.1**.

Clonare il repository:

```bash
cd ~/deploy
git clone https://github.com/infiniflow/ragflow.git
cd ragflow
git checkout v0.27.1
```

Spostarsi nella directory Docker:

```bash
cd ~/deploy/ragflow/docker
```

Il file `docker/.env` appartiene alla configurazione del progetto RAGFlow ed è distinto dal file `.env` di HistoryQ. Mantenere la configurazione coerente con la versione RAGFlow utilizzata.

### Creazione della rete condivisa

Creare la rete Docker esterna:

```bash
docker network create historyq-ragflow
```

Se la rete esiste già, non è necessario ricrearla.

### Configurazione Compose

HistoryQ include un file di override per integrare RAGFlow nella rete condivisa e limitare l'esposizione della sua interfaccia web.

Il file si trova in:

```text
~/deploy/HistoryQServer/HistoryQ-admin-package/deployment/ragflow/compose.override.yaml
```

La configurazione prevista espone l'interfaccia RAGFlow soltanto sull'interfaccia locale della VM, alla porta `18081`. L'API RAGFlow sulla porta `9380` rimane accessibile internamente tramite la rete Docker.

### Avvio di RAGFlow

Entrare nella directory Docker di RAGFlow:

```bash
cd ~/deploy/ragflow/docker
```

Avviare RAGFlow e i relativi servizi di supporto:

```bash
docker compose \
  -f docker-compose.yml \
  -f ~/deploy/HistoryQServer/HistoryQ-admin-package/deployment/ragflow/compose.override.yaml \
  up -d
```

Il comando avvia i container necessari, tra cui RAGFlow, Elasticsearch, MySQL, MinIO e Valkey.

### Verifica dello stato dei servizi

Per verificare che i container siano stati avviati:

```bash
docker compose \
  -f docker-compose.yml \
  -f ~/deploy/HistoryQServer/HistoryQ-admin-package/deployment/ragflow/compose.override.yaml \
  ps
```

Attendere che i servizi risultino avviati e che le dipendenze abbiano raggiunto uno stato operativo.

Per visualizzare i log di RAGFlow in tempo reale:

```bash
docker compose \
  -f docker-compose.yml \
  -f ~/deploy/HistoryQServer/HistoryQ-admin-package/deployment/ragflow/compose.override.yaml \
  logs -f --tail=100 ragflow-cpu
```

### Accesso all'interfaccia web di RAGFlow

L'interfaccia web di RAGFlow è esposta sulla porta `18081` della macchina su cui viene eseguito Docker.

Se il server non è direttamente raggiungibile dalla rete locale, è possibile utilizzare un **tunnel SSH** per accedere all'interfaccia da un altro computer senza esporre direttamente la porta del servizio.

Per configurare il tunnel:

1. Verificare che il server sia raggiungibile tramite SSH.
2. Aprire un terminale sul computer dal quale si desidera accedere a RAGFlow.
3. Eseguire un comando SSH con port forwarding, sostituendo i parametri con quelli del proprio ambiente:

```bash
ssh -L PORTA_LOCALE:127.0.0.1:PORTA_SERVER UTENTE@INDIRIZZO_SERVER
```

Dove:

- `PORTA_LOCALE` è la porta disponibile sul computer client sulla quale si desidera aprire il tunnel.
- `PORTA_SERVER` è la porta sulla quale è esposta l'interfaccia web di RAGFlow sul server.
- `UTENTE` è l'utente SSH del server.
- `INDIRIZZO_SERVER` è l'indirizzo IP o il nome DNS del server.

Mantenere aperta la sessione SSH durante l'utilizzo del servizio.

Una volta stabilito il tunnel, aprire il browser sul computer client e visitare:

```text
http://localhost:PORTA_LOCALE
```
Creare un account amministrativo per RAGFlow, se non è già stato fatto. L'account amministrativo consente di configurare i modelli AI e gestire le knowledge base.



### Configurazione iniziale di RAGFlow

Al primo accesso, è necessario configurare i modelli AI utilizzati dalla piattaforma.

1. Accedere alla sezione **Model providers**.
2. Configurare un provider per il modello LLM.
3. Configurare un provider per il modello di embedding.
4. Verificare che entrambi i modelli siano disponibili.
5. Impostare i modelli predefiniti nelle impostazioni di RAGFlow.
6. Generare o recuperare una API Key da utilizzare nella configurazione di HistoryQ.

La chiave API dovrà essere inserita nel parametro `RAGFLOW_API_KEY` del file `.env` di HistoryQ.

L'interfaccia web di RAGFlow serve per la configurazione e la gestione della piattaforma. Il backend HistoryQ comunica invece direttamente con le API RAGFlow attraverso la rete Docker, utilizzando:

```text
http://ragflow-cpu:9380
```

## 5. Configurazione dei modelli AI

RAGFlow richiede almeno un modello LLM per la generazione delle risposte e un modello di embedding per la rappresentazione vettoriale dei documenti.

La configurazione è gestita dall'interfaccia di RAGFlow, nella sezione **Model providers**.

Durante i test di HistoryQ sono stati utilizzati:

- **Groq** per il modello LLM.
- **Jina** per il modello di embedding.

### Modello di embedding locale consigliato: BGE-M3

Per documenti storici in più lingue, è possibile utilizzare **BGE-M3**, un modello multilingua dedicato agli embedding.

Il modello è disponibile tramite Ollama.

Installare Ollama sulla macchina Ubuntu:

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

Configurare Ollama affinché sia raggiungibile dai container Docker. Per esempio, con systemd:

```bash
sudo systemctl edit ollama
```

Inserire:

```ini
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
```

Salvare e riavviare il servizio:

```bash
sudo systemctl daemon-reload
sudo systemctl restart ollama
```

Scaricare il modello:

```bash
ollama pull bge-m3
```

Verificare che sia disponibile:

```bash
ollama list
```

In RAGFlow:

1. Aprire **Model providers**.
2. Selezionare il provider Ollama.
3. Configurare il Base URL con un indirizzo raggiungibile dal container RAGFlow, ad esempio `http://host.docker.internal:11434`, se tale hostname è configurato e risolvibile nell'ambiente Docker.
4. Aggiungere il modello `bge-m3` selezionando il tipo **Embedding**.
5. Verificare il modello.
6. Selezionarlo come modello di embedding predefinito nelle impostazioni di sistema.

Il modello BGE-M3 sostituisce il provider di embedding: non è un modello LLM e non sostituisce il modello necessario per generare le risposte.

## 6. Configurazione di HistoryQ

Prima dell'avvio, verificare che il file `.env` fornito sia presente nella directory principale del progetto.

Alcuni parametri fondamentali della configurazione devono essere coerenti con il deployment:

| Parametro | Valore previsto |
|---|---|
| `RAGFLOW_BASE_URL` | `http://ragflow-cpu:9380` |
| `RAGFLOW_API_KEY` | Chiave API generata da RAGFlow |
| `APP_CORS_ALLOWED_ORIGIN` | `http://water.cluelab.di.unisa.it:18080` |
| Endpoint OCR | `http://water.cluelab.di.unisa.it:18080/` |

Assicurarsi che la variabile dedicata al servizio OCR nel file `.env` contenga l'indirizzo indicato.

La chiave API di RAGFlow deve corrispondere a quella configurata nell'interfaccia RAGFlow.

## 7. Avvio di HistoryQ

Entrare nella directory del progetto:

```bash
cd ~/deploy/HistoryQServer/HistoryQ-admin-package
```

Verificare la configurazione Compose:

```bash
docker compose config -q
```

Il comando verifica la configurazione senza stampare il contenuto delle variabili d'ambiente.

Costruire le immagini e avviare i servizi:

```bash
docker compose up -d --build
```

I servizi avviati sono:

- `postgres`
- `silo`
- `backend`
- `frontend`
- `nginx`

Verificare lo stato:

```bash
docker compose ps
```


## 9. Gestione quotidiana


### HistoryQ

Entrare nella directory:

```bash
cd ~/deploy/HistoryQServer/HistoryQ-admin-package
```

Visualizzare i container:

```bash
docker compose ps
```

Visualizzare i log di tutti i servizi:

```bash
docker compose logs -f --tail=100
```

Visualizzare soltanto i log del backend:

```bash
docker compose logs -f --tail=100 backend
```

Visualizzare i log di PostgreSQL:

```bash
docker compose logs -f --tail=100 postgres
```

Riavviare i servizi:

```bash
docker compose restart
```

Arrestare i servizi senza eliminare i volumi:

```bash
docker compose down
```

### RAGFlow

Entrare nella directory:

```bash
cd ~/deploy/ragflow/docker
```

Visualizzare i log:

```bash
docker compose \
  -f docker-compose.yml \
  -f ~/deploy/HistoryQServer/HistoryQ-admin-package/deployment/ragflow/compose.override.yaml \
  logs -f --tail=100 ragflow-cpu
```

Riavviare i servizi:

```bash
docker compose \
  -f docker-compose.yml \
  -f ~/deploy/HistoryQServer/HistoryQ-admin-package/deployment/ragflow/compose.override.yaml \
  restart
```


## 10. Aggiornamento del codice

Quando cambia il codice sorgente di HistoryQ, ricostruire le immagini interessate.

Per il backend:

```bash
cd ~/deploy/HistoryQServer/HistoryQ-admin-package
docker compose build backend
docker compose up -d backend
```

Per il frontend:

```bash
docker compose build frontend
docker compose up -d frontend
```

Per ricostruire e riavviare entrambi:

```bash
docker compose up -d --build
```

Quando cambia la configurazione Compose, utilizzare `up -d` per applicare le modifiche e ricreare i container interessati. Il semplice comando `restart` non applica le modifiche alla configurazione Compose.

## 11. Diagnostica

### Verifica dei container

```bash
docker ps
```

Per visualizzare anche i container arrestati:

```bash
docker ps -a
```

### Verifica delle reti Docker

```bash
docker network ls
```

Controllare la rete condivisa:

```bash
docker network inspect historyq-ragflow
```
