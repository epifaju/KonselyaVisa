# 06 — Fonctionnalités manquantes, partielles ou défaillantes

Date : 2026-10-28. Les identifiants renvoient à `01_PRD_TRACEABILITY_MATRIX.md`. Les estimations sont des ordres de grandeur pour un développeur qui connaît déjà le dépôt (jours ouvrés). Elles ne sont pas un engagement.

Aucune fonctionnalité n'est classée BROKEN : la pile Docker n'a pas été rejouée. Une exigence peut être PARTIAL tout en ayant des tests verts sur la partie qui existe.

Priorités :

- **P0** — empêche un parcours critique du MVP v2.
- **P1** — nécessaire pour une première version exploitable au-delà de la démo développeur.
- **P2** — qualité MVP, parcours principal déjà possible.
- **P3** — V2, V3 ou ultérieur.

## P0 — Bloquant MVP

| ID | Fonctionnalité | État | Travail restant | Dépendances | Complexité | Estimation | Critères d'acceptation |
|---|---|---|---|---|---|---:|---|
| KON-FUNC-003, KON-FUNC-042 | Clôture de dossier et événement `CASE_COMPLETED` | Statuts `COMPLETED` et `CANCELLED` autorisés en base, jamais écrits. Pas d'endpoint ni de bouton. | Règles de transition dans un service Java (pièces acceptées, paiement si le tarif l'exige, rendez-vous si la procédure l'exige). Écriture outbox dans la même transaction. Bouton agent. IT. | Revue document et paiement déjà en place | Moyenne | 4–6 j | Un dossier visa de test passe à `COMPLETED` une fois. Un second appel est refusé. Une ligne `outbox_events` `CASE_COMPLETED` est `PENDING` puis `SENT`. `CANCELLED` est réservé aux rôles prévus et bloque upload et paiement. |
| KON-FUNC-033, KON-FUNC-046 | Parcours citoyen prouvé de bout en bout | Code présent. Playwright live non exécuté. Formulaire complet et paiement hors suite isolée. | Lancer la pile, jouer le visa FR→GW, ajouter le scénario `e2e-live` jusqu'à la clôture. | Clôture P0, Compose, Keycloak | Haute | 5–8 j | Un scénario automatique, sans `page.route` sur l'API métier, crée un dossier, dépose une pièce, paie en MOCK, et lit le statut. |
| KON-SRC-001 | Périmètre v1 absent | Personas, maquettes, 3 workflows, critères de succès et V3 non lisibles | Obtenir le fichier v1 et réconcilier la matrice. Ne pas inventer les écrans manquants. | Aucune | Produit | 1–2 j de relecture, plus le delta inconnu | Chaque exigence v1 a un ID, un statut, et les contradictions avec la v2 sont tranchées. |

## P1 — Indispensable pour un MVP exploitable

| ID | Fonctionnalité | État | Travail restant | Dépendances | Complexité | Estimation | Critères d'acceptation |
|---|---|---|---|---|---|---:|---|
| KON-FUNC-036 | Stockage MinIO conforme (SSE, URLs présignées) | Upload et download authentifié via l'API. Pas de SSE. Pas de presign. | Chiffrement SSE à l'écriture. Décider si le téléchargement reste proxifié (contrôle d'accès déjà là) ou devient une URL courte. Aligner le PRD si le proxy est retenu. | MinIO | Moyenne | 3–5 j | Un objet déposé est chiffré au repos. Un téléchargement sans JWT échoue. Un IT MinIO couvre le nouvel en-tête. |
| KON-FUNC-043 | Remplacement et rejet de pièce | Second upload laisse l'ancien en `UPLOADED`. `POST reject` sans UI. | Marquer l'ancienne pièce `REPLACED`. Bouton rejeter avec `message_key`. | Clôture (les pièces rejetées bloquent `COMPLETED`) | Moyenne | 3–4 j | Deux dépôts du même code : un seul est courant. Le rejet s'affiche chez le citoyen comme une correction à refaire. |
| KON-FUNC-049 | Journal d'audit | Table absente. L'historique agent lit l'outbox. | Table `audit_logs`, écriture transactionnelle, sans données de pièce d'identité en clair, RLS. | Tenancy déjà en place | Haute | 5–8 j | Consultation, téléchargement, revue et paiement créent une ligne. Un test vérifie l'absence de numéro de passeport dans `details_json`. |
| KON-FUNC-007 | Rétention par type de document | Une durée DPA (1825 jours) après demande d'effacement | Politique par code de pièce ou par procédure, purge qui la respecte | DPA, `RetentionPurgeService` | Moyenne | 4–6 j | Deux types avec deux durées produisent deux dates de purge dans un IT. |
| KON-FUNC-008 | Acceptation DPA dans l'admin | API et seed DEMO | Écran admin : lecture, acceptation, version | API existante | Basse | 2 j | Un `BUSINESS_ADMIN` accepte. L'ancien accord passe `SUPERSEDED`. |
| KON-FUNC-010 à 014 | Paiement hors simulation | Adaptateurs et IT factices. MOCK appelé par le navigateur. | Feature flag MOCK off par défaut hors profil local. Parcours Stripe ou manuel documenté avec des clés de test. Interdire le webhook MOCK si le flag est off (déjà le cas si le registry refuse le provider : à vérifier en durcissement). | Flags V17 | Moyenne | 3–5 j | Avec MOCK désactivé, `POST /webhooks/MOCK` ne marque aucune commande payée. Un paiement manuel agent reste possible. |
| KON-FUNC-032 | Portails dans la démo Compose | Vite manuel, nginx sans frontend | Images ou service statique `citizen` et `admin`, proxy nginx | Dockerfiles frontend | Moyenne | 3–4 j | `docker compose up` sert les deux UI et l'API. |
| KON-FUNC-037 | Notification réellement délivrée | Workflow JSON + bootstrap | Exécuter les trois échantillons, puis un événement applicatif, et lire Mailpit | Pile n8n | Basse | 1–2 j | Trois sujets distincts dans Mailpit. Aucun statut de dossier modifié par n8n. |
| KON-FUNC-029 | Testcontainers Keycloak | JWT forgé dans les tests | Un IT avec le realm importé, login citoyen, appel `GET /me` | Docker | Haute | 4–6 j | L'IT échoue si le realm perd le rôle `CITIZEN` ou le claim d'organisation. |
| KON-FUNC-035 | Affectation agent | Absente du schéma et de l'UI. Non écrite dans la v2. À confirmer avec la v1. | Si la v1 l'exige : colonne, file « mes dossiers », garde d'accès | KON-SRC-001 | Moyenne | 4–6 j si retenu | Un agent ne voit que ses dossiers ; un superviseur voit l'organisation. |

## P2 — Amélioration MVP

| ID | Fonctionnalité | État | Travail restant | Dépendances | Complexité | Estimation | Critères d'acceptation |
|---|---|---|---|---|---|---:|---|
| KON-FUNC-040 | Zustand, React Hook Form, Zod | Absents. Query et i18next présents. | Introduire seulement sur les formulaires dossier et catalogue, après accord explicite (règle dépôt : signaler toute dépendance hors stack). | Aucune | Moyenne | 5–8 j | Les formulaires citoyen rejettent les mêmes cas que l'API, avec messages i18n. |
| KON-FUNC-039 | Rotation du refresh token | Silent renew client | Vérifier la politique du realm et un test de renouvellement | Keycloak | Basse | 1–2 j | Après expiration d'access token, l'appel API réussit sans réafficher le login. Le refresh précédent est invalidé. |
| KON-FUNC-041 | Admin des pays et archivage | API seule | Écrans minimaux | Catalogue | Basse | 2–3 j | Archiver une version la retire du choix citoyen et laisse les dossiers déjà créés sur l'ancienne version. |
| KON-FUNC-016 | Preuve i18n | Fichiers fr/pt/en | Un scénario qui bascule `pt` et lit un libellé seedé | Playwright | Basse | 1–2 j | Le nom de procédure portugais s'affiche. |
| KON-FUNC-009 | Runbook violation 72 h | Un paragraphe | Procédure opératoire (qui, quoi, délai), hors code métier | Registre | Basse | 1 j | Le document nomme le responsable et les preuves à extraire (sans données réelles). |
| KON-FUNC-001 | Sessions Redis | Cache, rate-limit et holds seulement | Trancher : la session OIDC `sessionStorage` suffit. Sinon documenter l'écart dans le PRD. | Produit | Basse | 0,5 j | Décision écrite. |
| KON-FUNC-024 | Règle d'éligibilité vide = éligible | Choix de code | Confirmer ou refuser la publication d'une version sans règle | Catalogue | Basse | 1 j | Une version sans règle est refusée à la publication, ou le PRD acte le « éligible ». |
| KON-FUNC-031 | Exécution supply-chain | Workflow écrit | Lancer le job et archiver le résultat | GitHub | Basse | 0,5 j | Trivy CRITICAL et SBOM produits sur la branche. |
| KON-FUNC-028 | Traces jusqu'à n8n | Collector et Tempo déclarés | Un trace id visible de l'API au webhook | Pile | Moyenne | 2–3 j | Une création de dossier montre un span HTTP vers n8n. |
| KON-FUNC-038 | MapStruct sur les réponses dossier | `CaseResponseAssembler` manuel | Mapper généré, même contrat JSON | Aucune | Basse | 2 j | `OpenApiContractIT` inchangé sur les champs publics. |

## P3 — V2 / ultérieur

| ID | Fonctionnalité | Phase | État | Travail restant | Dépendances | Complexité | Estimation | Critères d'acceptation |
|---|---|---|---|---|---|---|---:|---|
| KON-FUNC-052 | Spring Statemachine | V2 | Absent | L'introduire seulement si les transitions dépassent la dizaine | Clôture MVP | Haute | 8–15 j | Les  transitions MVP restent dans les services tant que le seuil n'est pas atteint. |
| KON-FUNC-053 | OCR isolé | V2 | Stub manuel | Service séparé, réseau Docker sans Postgres, appels uniquement vers l'API | Stub et colonnes déjà là | Haute | 15–25 j | Le conteneur OCR n'a pas de route vers Postgres. L'API enregistre `ai_confidence`. |
| KON-FUNC-054 | Ollama | V2 | Absent | Idem, après l'interface d'extraction | OCR | Haute | 10–20 j | Le modèle répond via l'API Spring, pas en SQL. |
| KON-FUNC-055 | Alerte de hash inter-demandeurs | V2 | Déjà largement codée | Vérifier le faux positif même demandeur et le texte agent | Documents | Basse | 1–2 j | Deux demandeurs, même SHA-256 : alerte. Même demandeur : pas d'alerte. |
| KON-FUNC-056 | MRZ | V2 | Absent | Contrôle après extraction réelle | OCR | Haute | 8–12 j | Incohérence MRZ / saisie visible par l'agent, sans numéro en clair dans les logs. |
| KON-FUNC-057 | Journal des refus d'upload | V2 | Codé | Vérification live | Documents | Basse | 1 j | Un MIME interdit crée `document_upload_refusals` et s'affiche. |
| KON-FUNC-058 | Loki | V2 | Absent | Service et dashboards | OTel | Moyenne | 3–5 j | Les logs API sont requêtables dans Grafana. |
| KON-FUNC-059 | White-label | V2 | UI et mails déjà avancés | DNS/TLS multi-tenant au-delà de l'exemple nginx | Settings | Moyenne | 5–10 j | Deux domaines locaux montrent deux chartes. |
| KON-FUNC-060 | Expédition | V2 | Absent | Modèle, API, écran | Dossier clôturé | Haute | 8–12 j | Un envoi est un état Spring, notifié par outbox. |
| KON-FUNC-061 | Swarm ou Kubernetes | Ultérieur | Compose | Hors MVP | Volume réel | Haute | non estimé ici | Critère de volume non défini dans la v2. |
| KON-SRC-001 §48 | Vision ConsularOS V3 | V3 | Texte absent | Lire la v1 avant tout chiffrage | Fichier v1 | — | — | Aucun développement V3 tant que le texte n'est pas dans le dépôt. |

## Hors score, déjà livré au-delà du texte v2

À conserver, pas à recompter comme du MVP manquant : site public, suivi par référence, inscription invité, espace entreprise, procédures eVisa / assurance / traduction / légalisation / apostille, charte d'organisation.

## Synthèse des statuts scorés

| Statut (colonne de la matrice) | MVP (51) | V2 et ultérieur (10) |
|---|---:|---:|
| DONE | 17 | 0 |
| PARTIAL | 32 | 4 |
| NOT_STARTED | 2 (`CASE_COMPLETED`, `audit_logs`) | 6 |
| BROKEN | 0 | 0 |

DONE au sens « 100 % dans la matrice » : registre, consentement, export/effacement, interface `PaymentProvider`, idempotence, JSONB i18n, tenancy, settings, feature flags, JSONLogic, version figée, stub d'extraction, colonnes IA, contrat OpenAPI, hash SHA-256, principe Spring/n8n, Flyway. Plusieurs de ces lignes ont un pourcentage vérifié inférieur à 100 parce que la pile réelle n'a pas été rejouée. Le détail est dans la matrice.
