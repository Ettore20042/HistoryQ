# HistoryQ — Deployment Guide

Guida operativa per installare e avviare HistoryQ su Ubuntu Server mediante Docker Compose.

Questa guida descrive l'installazione di HistoryQ e RAGFlow su un server Ubuntu. Ogni installazione deve utilizzare le proprie credenziali e configurazioni: i file contenenti informazioni sensibili non sono inclusi nel repository.

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
| Chat Model | Qwen3 | Ollama |
| Embedding Model | nomic-embed-text | Ollama |
| OCR | FastAPI / Tesseract | Servizio universitario esterno |

HistoryQ e RAGFlow utilizzano due progetti Docker Compose indipendenti, collegati attraverso la rete Docker esterna `historyq-ragflow`.

Ollama viene eseguito direttamente sul server Ubuntu, al di fuori di Docker.

### Porte utilizzate

| Servizio | Porta | Accesso |
|---|---:|---|
| HistoryQ | 18080 | Porta pubblicata sulla VM |
| RAGFlow Web UI | 18081 | Solo localhost, accesso tramite SSH |
| RAGFlow API | 9380 | Rete Docker interna |
| Ollama | 11434 | Server Ubuntu |
| PostgreSQL | 5432 | Rete Docker interna |
| Silo | 9000 / 9001 | Rete Docker interna |
| Backend | 8081 | Rete Docker interna |

## 2. Prerequisiti

È necessario disporre di:

- Un server Ubuntu con accesso SSH.
- Docker Engine e Docker Compose.
- Git e curl.
- Risorse hardware adeguate ai modelli LLM scelti.
- Accesso al servizio OCR universitario, se necessario per l'elaborazione dei documenti.

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

La sua creazione è descritta nella sezione 3.2.

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
- Nome del modello Chat, se configurabile tramite variabile di ambiente.

Non lasciare valori segnaposto nelle variabili obbligatorie.

Salvare il file e limitarne i permessi:

```bash
chmod 600 .env
```

**Importante:** non pubblicare mai il file `.env` su GitHub e non condividerlo con altri utenti. Ogni installazione deve utilizzare credenziali proprie.

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

Se la versione scaricata fornisce un file modello anziché `.env`, seguire le istruzioni ufficiali di RAGFlow per creare e configurare il file.

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

## 5. Installazione e configurazione di Ollama

Ollama viene eseguito direttamente su Ubuntu, utilizzando le risorse hardware del server.

Installarlo:

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### 5.1. Scaricare il modello Chat

Il modello utilizzato come configurazione predefinita è Qwen3 32B:

```bash
ollama pull qwen3:32b
```

Il modello occupa circa 20 GB di spazio su disco e richiede risorse hardware considerevoli. Per un utilizzo più agevole sono indicativamente consigliati almeno 32 GB di RAM di sistema, oppure una GPU con memoria video adeguata.

Se le risorse hardware sono insufficienti, è possibile utilizzare una delle seguenti alternative:

**Qwen3 8B** – circa 5,2 GB di download; consigliati 16 GB di RAM di sistema.

```bash
ollama pull qwen3:8b
```

**Qwen3 4B** – circa 2,5 GB di download; adatto a sistemi con risorse più limitate.

```bash
ollama pull qwen3:4b
```

Se viene scelto un modello alternativo, ricordarsi di configurare lo stesso nome anche in RAGFlow e nell'eventuale variabile del backend HistoryQ che identifica il modello Chat.

Verificare i modelli installati:

```bash
ollama list
```

### 5.2. Configurazione dell'accesso da Docker

Per consentire a RAGFlow di comunicare con Ollama, il servizio deve essere raggiungibile attraverso l'interfaccia di rete del server.

Modificare la configurazione:

```bash
sudo systemctl edit ollama
```

Inserire:

```ini
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
```

Applicare le modifiche:

```bash
sudo systemctl daemon-reload
sudo systemctl restart ollama
```

Verificare il funzionamento:

```bash
curl http://localhost:11434/api/tags
```

**Nota di sicurezza:** Ollama non richiede autenticazione nella configurazione standard. Non esporre la porta 11434 indiscriminatamente su Internet. Limitare l'accesso tramite firewall alle sole reti o macchine che devono utilizzare il servizio.

## 6. Configurazione dei modelli in RAGFlow

Accedere all'interfaccia RAGFlow tramite il tunnel SSH descritto nella sezione 9.

### 6.1. Primo accesso e creazione dell'account

Al primo accesso, se non esiste ancora un account, procedere con la registrazione:

1. Selezionare **Sign Up** nella schermata iniziale.
2. Inserire i dati richiesti per creare un account.
3. Completare la registrazione, se abilitata.
4. Effettuare il login con le credenziali appena create.

Non è necessario utilizzare un account RAGFlow preesistente: la registrazione crea un account sulla nuova installazione del server.

### 6.2. Configurazione del provider Ollama

Aprire:

**User Settings → Model Providers**

Configurare il provider Ollama utilizzando l'indirizzo del server raggiungibile dall'interno dei container Docker:

| Parametro | Valore |
|---|---|
| Provider | Ollama |
| Instance name | Ollama Server |
| Base URL | `http://IP_SERVER:11434` |
| API Key | Vuota, se non richiesta |

Sostituire `IP_SERVER` con l'indirizzo IP del server Ubuntu raggiungibile dai container.

Non utilizzare `localhost`, perché dall'interno del container indicherebbe il container stesso e non il sistema Ubuntu sul quale è installato Ollama.

### 6.3. Configurazione del modello Chat

Aggiungere il modello Chat scaricato in precedenza.

| Parametro | Valore |
|---|---|
| Model name | `qwen3:32b` |
| Model type | Chat |

Se è stato scelto un modello alternativo, inserire il relativo nome, ad esempio `qwen3:8b` oppure `qwen3:4b`.

Verificare il modello e salvare la configurazione.

### 6.4. Configurazione del modello di embedding

Il modello di embedding viene utilizzato da RAGFlow per convertire i documenti in rappresentazioni vettoriali, necessarie per effettuare le ricerche semantiche all'interno delle Knowledge Base.

Per questo deployment viene utilizzato `nomic-embed-text`, un modello dedicato alla generazione di embedding.

Scaricarlo tramite Ollama sul server Ubuntu:

```bash
ollama pull nomic-embed-text
```

Successivamente, accedere all'interfaccia Web di RAGFlow e aprire:

**User Settings → Model Providers → Ollama**

Aggiungere `nomic-embed-text` come modello di tipo **Embedding**, utilizzando l'istanza Ollama precedentemente configurata.

Infine, aprire **System Model Settings** e impostare `nomic-embed-text` come modello di embedding predefinito.

Durante la creazione delle Knowledge Base, selezionare il modello di embedding configurato.

**Nota:** il modello di embedding deve rimanere coerente con quello utilizzato per indicizzare i documenti. Un'eventuale sostituzione richiede la rigenerazione degli embedding.

## 7. Configurazione dell'integrazione HistoryQ – RAGFlow

HistoryQ comunica con RAGFlow attraverso la rete Docker condivisa.

L'indirizzo utilizzato dal backend è:

```text
http://ragflow-cpu:9380
```

Questo indirizzo deve essere configurato nella variabile corrispondente del file `.env` di HistoryQ.

Per autenticare le richieste, HistoryQ utilizza un'API Key generata dall'istanza RAGFlow.

Accedere all'interfaccia Web di RAGFlow e generare una nuova API Key tramite la sezione dedicata alla gestione delle chiavi API.

Inserire la chiave nel file `.env` di HistoryQ:

```dotenv
RAGFLOW_API_KEY=INSERIRE_LA_PROPRIA_API_KEY
```

La chiave deve appartenere all'istanza RAGFlow effettivamente utilizzata.

Non utilizzare API Key appartenenti ad altre installazioni.

Dopo aver modificato il file `.env`, ricreare il backend di HistoryQ:

```bash
cd ~/deploy/HistoryQ-deployment-repo

docker compose -p historyq up -d --no-deps backend
```

## 8. Avvio di HistoryQ

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

## 9. Accesso alle applicazioni

### 9.1. HistoryQ

Se il server è configurato per pubblicare l'applicazione sul dominio previsto, HistoryQ è raggiungibile tramite:

```text
http://water.cluelab.di.unisa.it:18080/
```

Per installazioni su server differenti, utilizzare l'indirizzo e la porta pubblicati dal proprio deployment.

Non è necessario configurare un tunnel SSH se la porta di HistoryQ è già raggiungibile dall'esterno.

### 9.2. RAGFlow

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

## 10. Persistenza dei dati

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

## 11. Gestione del deployment

### Visualizzare i container HistoryQ

```bash
docker compose -p historyq ps
```

### Visualizzare i container RAGFlow

```bash
cd ~/deploy/ragflow/docker
docker compose -p ragflow ps
```

Per RAGFlow, ricordarsi di utilizzare anche il file di override indicato nella sezione 4 quando si eseguono operazioni che richiedono la configurazione completa del deployment.

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

## 12. Risoluzione dei problemi

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

Controllare che il Base URL configurato in RAGFlow utilizzi un indirizzo del server raggiungibile dai container e non `localhost`.

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

---

## 13. Note sulla configurazione e sulla sicurezza

- Non pubblicare file `.env`, password, token o API Key nel repository.
- Ogni installazione deve utilizzare credenziali e API Key proprie.
- Conservare una copia sicura dei file di configurazione.
- Limitare l'esposizione delle porte dei servizi interni.
- Predisporre backup periodici del database e dei documenti.
- Non eliminare i volumi Docker durante gli aggiornamenti ordinari.

Per ulteriori informazioni sui componenti utilizzati:

- [Docker Engine](https://docs.docker.com/engine/)
- [RAGFlow](https://github.com/infiniflow/ragflow)
- [Ollama](https://ollama.com/)
- [Qwen3](https://ollama.com/library/qwen3)
- [nomic-embed-text](https://ollama.com/library/nomic-embed-text)