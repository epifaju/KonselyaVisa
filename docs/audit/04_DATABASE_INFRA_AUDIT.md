# 04 — Audit base de données et infrastructure

Date : 2026-10-28. Aucune migration n'a été appliquée. Aucun conteneur n'a été démarré. Les statuts « démarré » et « accessible » sont donc non vérifiés pour la pile Compose. Les tests d'intégration du 28 octobre ont, eux, démarré PostgreSQL (et MinIO pour les documents) via Testcontainers.

## 1. PostgreSQL

Migrations Flyway `V1__baseline.sql` à `V31__public_blue_default_clear_demo_brand.sql`. Pas de trou de version. `ddl-auto=validate`.

### Tables créées

| Table | Migration | RLS |
|---|---|---|
| `organizations`, `organization_settings` | V2 | Oui, forcée |
| `feature_flags`, `outbox_events` | V3 | Oui |
| `countries`, `procedure_definitions`, `procedure_versions` | V4 | Catalogue ; les dossiers pointent une version |
| `applicants`, `cases` | V7 | Oui, forcée |
| `documents`, `document_access_logs` | V8 | Oui |
| `orders`, `payments`, `payment_webhook_receipts` | V9 | Oui |
| `appointment_slots`, `appointments` | V11 | Oui |
| `privacy_consents`, `data_deletion_requests` | V15 | Oui |
| `document_upload_refusals` | V26 | Oui |
| `organization_dpa_agreements` | V30 | Oui |

Colonnes ajoutées ensuite : pricing (V9), statuts de revue (V12), URL Stripe (V13), `review_message_key` (V14), créateur (V19), délai d'instruction (V20), profil public (V21), extraction et `ai_confidence` (V23), `extracted_fields_cipher` pgcrypto (V24), domaine de marque (V27).

Extension `pgcrypto` : V2. Chiffrement effectif des champs extraits : `pgp_sym_encrypt` / `pgp_sym_decrypt` (`ExtractedFieldsCrypto`, V24).

Seeds : pays (V5), visa tourisme FR→GW (V6), légalisation et apostille (V16), eVisa, assurance, traduction (V29), flags providers (V17, V18, V28), DPA démo (V30).

### Écarts de modèle par rapport au texte v2

| Attendu v2 | Constat | Gravité MVP |
|---|---|---|
| `outbox_events`, `organization_settings`, `feature_flags`, `document_access_logs` | Présents | Aucun |
| RLS sur `cases`, `documents`, `orders`, `payments` | Présent, étendu à d'autres tables, via `app_current_org()` | Aligné |
| `audit_logs` (cité comme existant depuis la v1) | Table absente | Bloquant si l'audit générique est requis au MVP |
| `workflow_definitions` / `workflow_instances` | Absents. La v2 les reporte à la V2 (Statemachine) | Acceptable pour le MVP |
| Rétention par type de document | Une durée sur le DPA | Partiel |
| Affectation d'agent | Pas de colonne | Non exigé explicitement par la v2 |
| Statut `COMPLETED` | Autorisé par la contrainte `V12`, jamais écrit par le code | Bloquant pour l'événement `CASE_COMPLETED` |
| Statut document `REPLACED` | Autorisé, jamais écrit | Partiel |
| Clé primaire UUID, FK organisation, index org/statut | Présents sur les tables lues (`V7`, `V8`, `V9`) | Aligné |

`cases.status` initial (V7) ne contenait pas `CORRECTION_REQUESTED`. V12 élargit la contrainte. Les seeds et les entités sont cohérents avec V12.

Historisation : `created_at` / `updated_at` sur `BaseEntity`, plus `outbox_events` comme journal d'événements métier. Pas d'historique de lignes séparé.

## 2. Docker Compose

Fichier : `infrastructure/docker-compose.yml`. Un seul réseau `konselyavisa`. Pas de réseau isolé OCR (le service OCR n'existe pas).

| Service PRD | Déclaré | Config lue | Démarré ici | Accessible ici | Intégration prouvée |
|---|---|---|---|---|---|
| postgres 16 | Oui, `konselyavisa-postgres` | Santé `pg_isready`, init SQL, volume | Non | Non | IT Testcontainers, pas ce Compose |
| redis 7 | Oui | AOF, santé ping | Non | Non | `RedisRateLimiterIT` |
| keycloak 26.2 | Oui, import du realm, `start-dev` | Dépend de Postgres | Non | Non | JWT de test uniquement |
| minio | Oui | Santé live, console | Non | Non | IT MinIO Testcontainers |
| n8n 1.81.4 + `n8n-init` | Oui | Webhook, bootstrap | Non | Non | Client unitaire ; pas de bout en bout |
| mailpit | Oui | Ports SMTP et UI | Non | Non | Non |
| backend | Oui | Profil `docker`, Redis, OTel, MinIO, n8n, providers | Non | Non | `mvn verify` hors Compose |
| frontend | Non | Vite local | — | — | Playwright isolé |
| admin | Non | Vite local | — | — | Playwright isolé |
| nginx | Oui | Proxy API seulement | Non | Non | Config lue |
| otel-collector | Oui | `otel-collector.yaml` | Non | Non | Non |
| prometheus + grafana + tempo | Oui | Provisioning Grafana | Non | Non | Endpoint Prometheus testé dans l'appli |
| loki | Non | — | — | — | — |
| ollama / ocr | Non (V2) | — | — | — | — |

La clé de chiffrement des champs extraits et les mots de passe ont des valeurs de développement dans le Compose et les profils. Elles ne doivent pas servir hors poste local. Elles ne sont pas recopiées dans ce rapport.

`n8n-init` crée le propriétaire, la credential SMTP Mailpit et le workflow. Tant que ce conteneur n'a pas réussi, le webhook de production n'existe pas dans n8n. Ce succès n'a pas été constaté ici.

## 3. n8n

| Artefact | Rôle |
|---|---|
| `infrastructure/n8n/konselyavisa-outbox-notify.json` | Webhook `konselyavisa-outbox`, switch, e-mails checklist / facture / relance / générique |
| `infrastructure/n8n/bootstrap.js` | Import au démarrage |
| `samples/case-created-checklist.json` | Payload d'exemple |
| `samples/payment-completed-invoice.json` | Payload d'exemple |
| `samples/correction-requested-relance.json` | Payload d'exemple |

Le workflow ne contient pas de nœud qui modifie un dossier, un prix ou une éligibilité. Les e-mails partent vers l'adresse de notification configurée, pas vers une boîte déduite du citoyen dans tous les cas : le destinataire lu dans le nœud e-mail est l'adresse d'exploitation (`notify@…`), avec le branding dans le corps. Une relance « au citoyen » au sens produit reste à confirmer sur une exécution réelle.

Événements outbox sans branche dédiée (e-mail générique) : `DOCUMENT_UPLOADED`, rendez-vous, vie privée, `ACCOUNT_REGISTERED`, et `CASE_COMPLETED` s'il était émis.

## 4. CI et environnements

`.github/workflows/ci.yml` : jobs `verify` (`./mvnw verify`), `supply-chain` (CycloneDX + Trivy filesystem et image, sévérité CRITICAL), `frontend` (Vitest + Playwright isolé), `e2e-live` seulement en `workflow_dispatch`. Pas de GitLab CI. Le workflow GitHub n'a pas été lancé depuis cette mission. Le rapport de tests indique que `e2e-live` n'a pas tourné.

Profils Spring lus : `application.yml`, `application-docker.yml`, `application-test.yml`.

## 5. Écarts bloquants d'infrastructure pour une démo MVP

1. Les portails ne démarrent pas avec `docker compose up`. Il faut deux processus Vite en plus.
2. La chaîne outbox → n8n → Mailpit est décrite et non observée sur cette mission.
3. Keycloak `start-dev` et secrets de développement : suffisants pour une démo locale, insuffisants pour un hébergement.
4. Pas de réseau Docker séparé pour un futur OCR. À créer en V2 avant d'ajouter le conteneur, pour respecter l'isolation PostgreSQL.
