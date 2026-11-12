# Override RAGFlow per HistoryQ

Questo file non sostituisce la configurazione ufficiale di RAGFlow. Usare il tag `v0.27.1` del repository `infiniflow/ragflow`, quindi applicare `compose.override.yaml` insieme a `docker/docker-compose.yml` dalla directory `ragflow/docker`.

L'override:

* collega `ragflow-cpu` e `ragflow-gpu` alla rete esterna `historyq-ragflow`;
* rimuove le pubblicazioni host dei servizi RAGFlow, incluse 80, 443 e 9380;
* non rimuove MySQL, Elasticsearch, Valkey, Silo o altre dipendenze ufficiali.

Creare prima la rete:

```bash
docker network create historyq-ragflow
```

Avviare il profilo scelto, di norma CPU:

```bash
cd ragflow/docker
docker compose \
  -f docker-compose.yml \
  -f /percorso/HistoryQ/deployment/ragflow/compose.override.yaml \
  up -d
```

In HistoryQ impostare `RAGFLOW_BASE_URL=http://ragflow-cpu:9380`. Per GPU usare il profilo GPU ufficiale e `http://ragflow-gpu:9380`. Le API key e tutte le password devono essere generate/configurate nell'installazione RAGFlow e non devono essere salvate in questo repository.
