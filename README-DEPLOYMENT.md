# HistoryQ — Deployment Guide

Guida operativa per installare e avviare HistoryQ su un server Ubuntu mediante Docker Compose.

Questa guida descrive l'installazione e la configurazione di HistoryQ e RAGFlow su un server Ubuntu. I due progetti vengono eseguiti mediante Docker Compose e comunicano attraverso una rete Docker condivisa.

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
| Chat Model | gpt-oss-120B | API Groq |
| Embedding Model | Jina Embeddings v4 | API Jina |
| OCR | FastAPI / Tesseract | Servizio universitario esterno |

HistoryQ e RAGFlow utilizzano due progetti Docker Compose indipendenti, collegati attraverso la rete Docker esterna `historyq-ragflow`.

### Porte utilizzate

| Servizio | Porta | Accesso |
|---|---:|---|
| HistoryQ | 18080 | Porta pubblicata sul server |
| RAGFlow Web UI | 18081 | Accesso tramite SSH |
| RAGFlow API | 9380 | Rete Docker interna |
| PostgreSQL | 5432 | Rete Docker interna |
| Silo | 9000 / 9001 | Rete Docker interna |
| Backend | 8081 | Rete Docker interna |

## 2. Prerequisiti

È necessario disporre di:

- Un server Ubuntu con accesso SSH.
- Docker Engine e Docker Compose.
- Git e curl.
- Accesso a Internet per scaricare le immagini Docker e raggiungere le API esterne.
- Una API Key Groq.
- Una API Key Jina.
- Accesso al servizio OCR universitario.

Non sono richieste GPU, installazioni di runtime per modelli locali o risorse hardware dedicate all'esecuzione degli LLM.

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

Se si desidera utilizzare Docker senza `sudo`, aggiungere l'utente al gruppo Docker:

```bash
sudo usermod -aG docker $USER
```

Dopo l'esecuzione del comando, effettuare nuovamente l'accesso al server affinché la modifica abbia effetto.

**Attenzione:** l'appartenenza al gruppo Docker conferisce privilegi equivalenti a quelli di root. Aggiungere soltanto utenti fidati.

## 3. Preparazione delle directory sul server

Accedere al server Ubuntu tramite SSH e creare la directory destinata a contenere i progetti:

```bash
mkdir -p ~/deploy
cd ~/deploy
```

La directory `~/deploy` sarà la cartella principale in cui verranno collocati HistoryQ e RAGFlow.

### 3.1. Download di HistoryQ

Clonare il repository:

```bash
cd ~/deploy
git clone https://github.com/Ettore20042/HistoryQ.git HistoryQ-deployment-repo
```

La struttura attesa è:

```text
~/deploy/
└── HistoryQ-deployment-repo/
    ├── .env.example
    ├── compose.yaml
    ├── Dockerfile
    ├── backend/
    ├── deployment/
    ├── src/
    └── ...
```

Il repository contiene il file `.env.example`, che documenta le variabili necessarie per configurare l'applicazione.

Il file `.env` effettivo non viene distribuito nel repository per evitare la pubblicazione di credenziali e informazioni sensibili.

### 3.2. Configurazione delle variabili di ambiente di HistoryQ

Accedere alla directory del progetto:

```bash
cd ~/deploy/HistoryQ-deployment-repo
```

Creare il file `.env` partendo dal modello fornito:

```bash
cp .env.example .env
```

Aprire il file:

```bash
nano .env
```

Compilare tutte le variabili richieste utilizzando i valori appropriati per il proprio server.

In particolare, verificare:

- Credenziali e configurazione del database PostgreSQL.
- Credenziali e configurazione di Silo.
- Parametri di autenticazione del backend.
- URL del servizio OCR.
- URL dell'istanza RAGFlow.
- API Key dell'istanza RAGFlow.

Per l'integrazione con RAGFlow, verificare la presenza e la corretta configurazione delle seguenti variabili:

```dotenv
RAGFLOW_BASE_URL=http://ragflow-cpu:9380
RAGFLOW_API_KEY=INSERIRE_LA_PROPRIA_API_KEY
```

La prima variabile identifica l'indirizzo interno dell'istanza RAGFlow, mentre la seconda permette al backend di autenticare le richieste.

Le API Key di Groq e Jina non devono essere inserite nel `.env` di HistoryQ: vengono configurate direttamente nell'interfaccia di RAGFlow.

Salvare il file e limitarne i permessi:

```bash
chmod 600 .env
```

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

Verificare la presenza del file di configurazione `.env`:

```bash
ls -la
```

RAGFlow utilizza un proprio file `.env`, separato da quello di HistoryQ. Le impostazioni di questo file devono essere mantenute indipendenti.

Documentazione ufficiale:

https://github.com/infiniflow/ragflow

### 4.1. Creazione della rete Docker

HistoryQ e RAGFlow comunicano attraverso una rete Docker esterna condivisa.

Crearla prima di avviare i servizi:

```bash
docker network create historyq-ragflow
```

Se la rete esiste già, non è necessario ricrearla.

### 4.2. Configurazione Compose

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

### 4.3. Avvio di RAGFlow

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

Attendere che i servizi necessari siano correttamente avviati prima di procedere con la configurazione dei modelli.

## 5. Configurazione dei provider AI in RAGFlow

RAGFlow utilizza provider esterni per eseguire le operazioni di generazione del testo e di indicizzazione semantica.

In particolare, vengono configurati due provider distinti:

- Groq per il modello Chat.
- Jina AI per il modello Embedding.

Le API Key devono essere ottenute dai rispettivi servizi e configurate nell'interfaccia di RAGFlow.

### 5.1. Primo accesso a RAGFlow

Accedere all'interfaccia RAGFlow tramite il tunnel SSH descritto nella sezione 9.

Al primo accesso, se non esiste ancora un account:

1. Selezionare **Sign Up** nella schermata iniziale.
2. Inserire i dati richiesti per creare un account.
3. Completare la registrazione, se abilitata.
4. Effettuare il login con le credenziali appena create.

La registrazione crea un account sulla nuova installazione del server.

### 5.2. Configurazione del provider Groq

Per utilizzare il modello Chat è necessario disporre di una API Key Groq.

La chiave può essere generata dalla piattaforma:

https://console.groq.com/keys

Dopo aver ottenuto la chiave, accedere a RAGFlow e aprire:

**User Settings(in alto a destra) → Model Providers**

Selezionare **Groq** tra i provider disponibili.

Creare una nuova istanza e configurare le informazioni richieste:

| Parametro | Valore |
|---|---|
| Provider | Groq |
| Instance name | Groq |
| API Key | API Key personale Groq |
| Model | `openai/gpt-oss-120b` |
| Model type | Chat |

Se il modello non compare nell'elenco, aggiungerlo come modello personalizzato utilizzando l'identificativo esatto esposto dal provider.

Verificare il modello attraverso la funzionalità di verifica di RAGFlow e salvare la configurazione.

### 5.3. Configurazione del provider Jina

Il modello di embedding viene utilizzato per trasformare il testo dei documenti e delle domande in rappresentazioni vettoriali.

Queste rappresentazioni permettono di confrontare il significato delle informazioni e recuperare i contenuti semanticamente più pertinenti.

Per questo deployment viene utilizzato Jina Embeddings v4.

Ottenere una API Key dalla piattaforma:

https://jina.ai/

Accedere nuovamente a:

**User Settings → Model Providers**

Selezionare **Jina** tra i provider disponibili.

Creare una nuova istanza e configurare:

| Parametro | Valore |
|---|---|
| Provider | Jina |
| Instance name | Jina |
| API Key | API Key personale Jina |
| Model | `jina-embeddings-v4` |
| Model type | Embedding |

Se il modello non compare nell'elenco, aggiungerlo come modello personalizzato, utilizzando l'identificativo esatto del modello supportato dal provider.

Verificare il funzionamento e salvare la configurazione.

### 5.4. Impostazione dei modelli predefiniti

Dopo aver configurato entrambi i provider, accedere alla sezione:

 **Set Default Models**, in base alla versione dell'interfaccia.

Impostare i modelli predefiniti:

| Impostazione | Modello |
|---|---|
| Default Chat Model / LLM | Groq – `openai/gpt-oss-120b` |
| Default Embedding Model | Jina – `jina-embeddings-v4` |

Salvare le impostazioni.

A questo punto RAGFlow dispone dei provider necessari per generare risposte e indicizzare semanticamente i documenti.

**Nota importante:** il modello di embedding deve rimanere coerente con quello utilizzato per indicizzare i documenti. Un'eventuale sostituzione del modello richiede la rigenerazione degli embedding delle Knowledge Base interessate.

## 6. Configurazione dell'integrazione HistoryQ – RAGFlow

HistoryQ comunica con RAGFlow attraverso la rete Docker condivisa.

L'indirizzo utilizzato dal backend è:

```text
http://ragflow-cpu:9380
```

Questo indirizzo deve essere configurato nella variabile `RAGFLOW_BASE_URL` del file `.env` di HistoryQ.

Per autenticare le richieste, HistoryQ utilizza un'API Key generata dall'istanza RAGFlow.

Accedere all'interfaccia Web di RAGFlow e generare una nuova API Key tramite la sezione dedicata alla gestione delle chiavi API.

Inserire la chiave nel file `.env` di HistoryQ:

```dotenv
RAGFLOW_API_KEY=INSERIRE_LA_PROPRIA_API_KEY
```

La chiave deve appartenere all'istanza RAGFlow effettivamente utilizzata.

**Attenzione:** questa API Key è distinta dalle chiavi Groq e Jina. Serve esclusivamente per autenticare HistoryQ verso RAGFlow.

Dopo aver modificato il file `.env`, ricreare il backend di HistoryQ:

```bash
cd ~/deploy/HistoryQ-deployment-repo

docker compose -p historyq up -d --no-deps backend
```

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

Verificare la configurazione:

```bash
docker compose -p historyq config --quiet
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

### 8.1. HistoryQ

L'applicazione è raggiungibile tramite:

```text
http://water.cluelab.di.unisa.it:18080/
```

Per installazioni su server differenti, utilizzare l'indirizzo e la porta pubblicati dal proprio deployment.

Non è necessario configurare un tunnel SSH se la porta di HistoryQ è già raggiungibile dall'esterno.

### 8.2. RAGFlow

Per accedere all'interfaccia Web di RAGFlow, utilizzare un tunnel SSH.

Dal terminale PowerShell di Windows:

```powershell
ssh -L 18081:localhost:18081 -p PORTA_SSH UTENTE@INDIRIZZO_SERVER
```

Sostituire i parametri con i valori effettivi del server:

- `PORTA_SSH`: porta SSH del server Ubuntu.
- `UTENTE`: nome utente per l'accesso SSH.
- `INDIRIZZO_SERVER`: indirizzo IP o hostname del server Ubuntu.

Mantenere aperta la connessione SSH durante l'utilizzo di RAGFlow.

Aprire il browser e visitare:

```text
http://localhost:18081
```

## 9. Persistenza dei dati

HistoryQ utilizza volumi Docker per conservare i dati applicativi.

| Volume | Contenuto |
|---|---|
| `historyq-postgres-data` | Database PostgreSQL |
| `historyq-silo-data` | Documenti archiviati in Silo |

Sono inoltre utilizzati i seguenti identificatori:

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

### RAGFlow non riesce a utilizzare Groq

Verificare:

1. Che la API Key Groq sia corretta.
2. Che l'account Groq disponga di quota e accesso al modello richiesto.
3. Che il modello `openai/gpt-oss-120b` sia configurato come modello Chat.
4. Che la verifica del modello in RAGFlow abbia esito positivo.
5. Che il servizio RAGFlow possa raggiungere le API esterne tramite HTTPS.

Controllare anche i log:

```bash
docker compose -p ragflow logs --tail=100
```

### RAGFlow non riesce a utilizzare Jina

Verificare:

1. Che la API Key Jina sia corretta.
2. Che il modello `jina-embeddings-v4` sia disponibile per la propria API Key.
3. Che il modello sia configurato come Embedding.
4. Che la verifica del modello in RAGFlow abbia esito positivo.
5. Che il server possa raggiungere le API Jina tramite HTTPS.

Controllare i log di RAGFlow:

```bash
docker compose -p ragflow logs --tail=100
```

### Problemi di avvio dei container

Verificare lo stato:

```bash
docker ps -a
```

Controllare i log di HistoryQ:

```bash
cd ~/deploy/HistoryQ-deployment-repo
docker compose -p historyq logs --tail=100
```

Per RAGFlow:

```bash
cd ~/deploy/ragflow/docker

docker compose \
  -p ragflow \
  -f docker-compose.yml \
  -f ~/deploy/HistoryQ-deployment-repo/deployment/ragflow/compose.override.yaml \
  logs --tail=100
```

### Problemi OCR

HistoryQ utilizza il servizio OCR universitario esterno configurato nel file `.env`.

In caso di timeout, verificare la raggiungibilità del servizio e l'eventuale necessità di accesso alla rete universitaria.

Non modificare l'endpoint senza aver verificato la disponibilità di un servizio alternativo.

## 12. Note sulla configurazione e sulla sicurezza

- Non pubblicare file `.env`, password, token o API Key nel repository.
- Ogni installazione deve utilizzare credenziali e API Key proprie.
- Configurare le API Key Groq e Jina direttamente nell'istanza RAGFlow.
- Conservare una copia sicura dei file di configurazione.
- Limitare l'esposizione delle porte dei servizi interni.
- Predisporre backup periodici del database e dei documenti.
- Non eliminare i volumi Docker durante gli aggiornamenti ordinari.
- Verificare periodicamente la disponibilità e la quota dei servizi API esterni.

### Documentazione dei componenti

- [Docker Engine](https://docs.docker.com/engine/)
- [Docker Compose](https://docs.docker.com/compose/)
- [RAGFlow](https://github.com/infiniflow/ragflow)
- [Groq Console](https://console.groq.com/)
- [Groq API Documentation](https://console.groq.com/docs)
- [Jina AI](https://jina.ai/)
- [Jina Embeddings](https://jina.ai/embeddings/)
- [Jina API Documentation](https://jina.ai/embeddings/)

