# Deployment Docker di HistoryQ

Questa guida descrive il deployment su un server Linux usando due progetti Compose distinti:

```text
Internet/reverse proxy universitario
              |
       historyq-nginx :80
          /          \
 frontend (SPA)   backend :8081
                    |       \
              PostgreSQL16  Silo (HistoryQ)
                    |
                RAGFlow :9380 (rete Docker privata)
                    |
              OCR HTTPS esterno
```

Il progetto RAGFlow mantiene le proprie dipendenze (MySQL, Elasticsearch, Valkey e Silo) nel repository ufficiale. HistoryQ usa invece un Silo separato con credenziali e volume dedicati: in questo modo non riutilizza le credenziali RAGFlow e il bucket `historygenie-documents` resta isolato. PostgreSQL e MySQL non sono intercambiabili.

## Requisiti

Usare una distribuzione Linux supportata da Docker Engine, Docker Compose v2, almeno 8 vCPU, 16 GB RAM e spazio disco sufficiente per Elasticsearch, gli indici RAG e i documenti. Per collezioni grandi aumentare RAM e storage secondo la documentazione RAGFlow 0.27.1. Installare Docker Engine e il plugin Compose seguendo la documentazione ufficiale Docker; verificare:

```bash
docker --version
docker compose version
```

Verificare con l’amministratore universitario che le porte 80/443 non siano già occupate da un reverse proxy, che il server possa raggiungere `https://fire.cluelab.di.unisa.it:8420`, e che siano disponibili DNS e certificati per il dominio HistoryQ.

## Preparazione

Dalla directory della repository:

```bash
cp .env.example .env
chmod 600 .env
openssl rand -base64 32
```

Inserire nel file `.env` password uniche e robuste, il JWT secret generato, l’API key RAGFlow e il token OCR ricevuti dagli amministratori. `OCR_SERVICE_URL` deve essere HTTPS; `RAGFLOW_BASE_URL` deve rimanere `http://ragflow-cpu:9380` quando si usa il profilo CPU ufficiale. Non committare `.env`.

Creare una sola volta la rete condivisa:

```bash
docker network create historyq-ragflow
```

## RAGFlow 0.27.1

Da una directory di amministrazione separata:

```bash
git clone --branch v0.27.1 --depth 1 https://github.com/infiniflow/ragflow.git
cd ragflow/docker
cp .env .env.backup
```

Modificare `.env` ufficiale per produzione: usare `RAGFLOW_IMAGE=infiniflow/ragflow:v0.27.1`, password uniche per MySQL, Elasticsearch, Silo e Valkey, `DEVICE=cpu`, `DOC_ENGINE=elasticsearch`, e rimuovere le pubblicazioni host non necessarie. Avviare il progetto con l’override di questa repository:

```bash
docker compose -f docker-compose.yml \
  -f /percorso/HistoryQ/deployment/ragflow/compose.override.yaml \
  config
docker compose -f docker-compose.yml \
  -f /percorso/HistoryQ/deployment/ragflow/compose.override.yaml \
  up -d
```

L’override aggiunge `ragflow-cpu` alla rete `historyq-ragflow` e rimuove le porte host di RAGFlow, inclusi 80, 443 e 9380. Le dipendenze ufficiali non vengono rimosse. Attendere che RAGFlow sia pronto, accedere alla UI tramite il canale amministrativo previsto e creare l’API key da usare in HistoryQ. Se si usa GPU, avviare il profilo ufficiale `gpu` e impostare `RAGFLOW_BASE_URL=http://ragflow-gpu:9380` nel `.env` di HistoryQ.

## HistoryQ

Dalla root di HistoryQ:

```bash
docker compose config
docker compose build
docker compose up -d
docker compose ps
```

Il gateway è pubblicato sulla porta indicata da `HISTORYQ_HTTP_PORT` (8080 di esempio), non direttamente su 80. PostgreSQL e Silo non pubblicano porte sull’host; il backend usa i nomi DNS Compose `postgres`, `silo` e `ragflow-cpu`. Flyway applica le migration già presenti all’avvio. Non usare `docker compose down -v`: i volumi contengono i dati.

## HTTPS e dominio

La configurazione inclusa è HTTP per non inventare dominio o certificati. In produzione, terminare TLS nel reverse proxy universitario oppure montare certificati reali in un Nginx dedicato davanti al gateway. Il proxy deve inoltrare `Host`, `X-Forwarded-Proto`, `X-Forwarded-For` e la porta HTTP interna; impostare `APP_CORS_ALLOWED_ORIGIN` all’origine HTTPS effettiva. Se il gateway deve ascoltare direttamente su 80/443, cambiare solo `HISTORYQ_HTTP_PORT` dopo avere verificato che siano libere.

## Operazioni quotidiane

```bash
# Log HistoryQ
docker compose logs -f backend
docker compose logs -f nginx

# Log RAGFlow: eseguire dalla directory ragflow/docker
docker compose -f docker-compose.yml -f /percorso/HistoryQ/deployment/ragflow/compose.override.yaml logs -f ragflow-cpu

# Riavvio senza rimuovere dati
docker compose restart
docker compose up -d
```

Per aggiornare le immagini, fare prima un backup, poi usare `docker compose pull` e `docker compose up -d`; per RAGFlow mantenere esplicitamente il tag `v0.27.1` finché non viene validato un aggiornamento. Non eseguire `down -v`.

## Backup e ripristino

Eseguire backup PostgreSQL, Silo HistoryQ e i volumi del progetto RAGFlow in una destinazione esterna e cifrata:

```bash
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > historyq.sql
docker run --rm -v historyq_historyq-silo-data:/data -v "$PWD:/backup" alpine \
  tar czf /backup/historyq-silo-data.tgz -C / data
```

Verificare periodicamente il ripristino su un host separato. Per PostgreSQL ripristinare in un database vuoto con `psql`; per Silo ripristinare il contenuto del volume solo a container fermo. Eseguire backup anche dei volumi `mysql_data`, `esdata01`, `redis_data` e `minio_data` del progetto RAGFlow secondo la sua documentazione.

## Diagnostica

* **Backend non parte:** controllare `docker compose logs backend`, le credenziali `.env`, lo stato di `postgres` e che la rete `historyq-ragflow` esista.
* **Errore RAGFlow:** verificare che il servizio attivo sia `ragflow-cpu` (o `ragflow-gpu`), che l’API key sia valida e che entrambi i container siano sulla rete condivisa.
* **Upload fallisce:** verificare `client_max_body_size` e i limiti multipart da 50 MB; controllare spazio del volume Silo.
* **OCR fallisce:** verificare URL HTTPS, token Bearer, DNS/firewall e i timeout `OCR_*`; il servizio OCR non è gestito da questo Compose.
* **SPA restituisce 404 dopo refresh:** verificare che il gateway e `frontend.conf` siano quelli della repository e che il fallback punti a `index.html`.

## Informazioni richieste all’amministratore

Servono: dominio definitivo, modalità di terminazione TLS, policy di backup, dimensionamento previsto dei documenti, eventuale proxy universitario e scelta CPU/GPU per RAGFlow. Nessuno di questi valori è incorporato nei file versionati.
