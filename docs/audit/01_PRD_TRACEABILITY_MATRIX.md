# 01 — Matrice de traçabilité PRD

Date de l'audit : 2026-10-28. Lecture seule. Aucun service Docker n'a été démarré, aucun test n'a été relancé pendant cette mission.

## Document de référence

| Document | Présence | Rôle |
|---|---|---|
| `docs/PRD_KonselyaVisa_v2_Optimise_CursorAI.md` | Présent, 317 lignes | Référence officielle. Version 2.0, optimisation technique de la v1.0. Désigné par `.cursorrules` comme source de vérité produit et architecture. |
| `PRD_Consulixa.md`, `PRD_Konselya.md`, `PRD_GlobalVisa_Consular_Services_Platform.md`, `prd.md`, `docs/PRD.md` | Absents | — |
| PRD KonselyaVisa v1.0 | Absent du dépôt | Cité par la v2 comme texte conservé pour la vision, les personas (§1–4), les parcours et maquettes (§6–13), les rôles Keycloak détaillés (§38), les 3 workflows MVP (§45–46), les critères de succès (§54), la tranche France → Guinée-Bissau → visa touristique (§53) et la vision ConsularOS V3 (§48). |

La v2 dit explicitement qu'elle n'est pas un remplacement de la v1. Les exigences inventoriées ci-dessous sont uniquement celles écrites dans la v2, plus les contrôles de la mission lorsqu'un symbole v2 permet de les rattacher (événement `CASE_COMPLETED`, modules listés, tranche verticale nommée). Les écrans, personas et critères de succès de la v1 ne reçoivent pas d'identifiant : leur texte n'est pas dans le dépôt.

Documents connexes, hors périmètre de fusion : `docs/REGISTRE_TRAITEMENTS.md`, `docs/WHITE_LABEL.md`, `docs/UX_GUIDELINES.md`, `README.md`. Ils décrivent l'implémentation. Ils ne complètent pas le PRD.

### Contradictions internes à la v2

1. La section 9 classe l'anti-fraude documentaire en V2. L'ordre de développement (§13, étape 10) demande déjà le hash SHA-256 et une anti-fraude basique avec les documents.
2. La section 8 demande des dossiers `ai/ollama` et `ai/document-processing` dès l'arborescence MVP, puis précise que l'OCR ne doit pas être appelé avant la V2. Le contrat `DocumentExtractionService` est MVP ; le microservice OCR est V2.
3. La section 1 vise « OpenTelemetry dès le MVP → Prometheus + Grafana + Loki ». La section 10 rend OpenTelemetry obligatoire au MVP et laisse Prometheus/Grafana/Loki comme suite de l'observabilité v1. Loki n'est pas dans Compose.
4. La section 12 nomme `konselyavisa-frontend` et `konselyavisa-admin`. Le Compose actuel ne construit pas ces deux images. Les portails se lancent avec Vite.
5. Le white-label est préparé au MVP par `organization_settings` et annoncé pour la V2. L'interface d'identité visuelle est déjà livrée.

## Méthode de statut

| Statut | Sens |
|---|---|
| DONE | Exigence couverte par le code et par une preuve d'exécution citée, ou par un document dont la présence suffit (registre). |
| PARTIAL | Une partie du comportement existe. Le pourcentage d'implémentation est une estimation justifiée, pas une mesure. |
| NOT_STARTED | Aucune implémentation utile. |
| BROKEN | Code présent et comportement défaillant constaté. Aucun cas n'a été classé BROKEN : la pile réelle n'a pas été rejouée ici. |
| NOT_VERIFIED | Réservé aux éléments dont le code n'a pas été lu assez pour estimer. Aucune exigence scorée n'est dans ce statut. Les preuves d'exécution manquantes abaissent la colonne « vérifié ». |
| NOT_APPLICABLE | Exigence explicitement hors solution (Drools). |

Preuve d'exécution acceptée : `docs/testing/TEST_EXECUTION_REPORT.md` du 2026-10-28 (`mvn clean verify` : 472 tests Surefire + 56 tests Failsafe, 0 échec ; Playwright isolé : 21 scénarios, API simulée). Le fumigène `tests/e2e/live/stack.spec.ts` n'a pas été exécuté. Cette mission ne relance pas ces suites.

## Exigences non scorées

| ID | Sujet | Traitement |
|---|---|---|
| KON-SRC-001 | Corpus fonctionnel v1 (§1–4, §6–13, §38 détaillé, §45–46, §54, §48) | Source absente. Exclu des pourcentages. |
| KON-FUNC-NA-001 | Ne pas introduire Drools | NOT_APPLICABLE. Le moteur retenu est JSONLogic (`JsonLogicEligibilityEvaluator`). Exclu des pourcentages. |

## Pondération

Critique = 5, haute = 3, moyenne = 2, basse = 1.

Pourcentage estimé = somme(poids × % implémentation) / somme(poids).
Pourcentage vérifié = somme(poids × % vérifié) / somme(poids).
Les deux pourcentages sont des estimations. Le détail du calcul est dans `08_EXECUTIVE_SUMMARY.md`.

## Matrice

| ID | Exigence PRD | Phase | Poids | Backend | Frontend | Tests | Statut | Impl. | Vérifié |
|---|---|---|---:|---|---|---|---|---:|---:|
| KON-FUNC-001 | Redis : cache catalogue, rate-limit, verrous | MVP | 3 | `redis/*`, cache `ProcedureService`, holds créneaux et tickets invité | — | `RedisRateLimiterIT`, tests unitaires rate-limit | PARTIAL | 80 | 70 |
| KON-FUNC-002 | Outbox transactionnelle, publisher, retry, statut FAILED | MVP | 5 | `OutboxAppender`, `OutboxPublisher` | — | `OutboxPublisherIT`, `OutboxAppenderTest` | PARTIAL | 85 | 75 |
| KON-FUNC-003 | Émission de `CASE_COMPLETED` | MVP | 5 | Constante seulement dans `OutboxEventTypes` | Aucun écran de clôture | Aucun test d'émission | NOT_STARTED | 0 | 0 |
| KON-FUNC-004 | Registre des traitements dans `/docs` | MVP | 2 | — | — | Document `docs/REGISTRE_TRAITEMENTS.md` | DONE | 100 | 100 |
| KON-FUNC-005 | Consentement horodaté au dépôt | MVP | 5 | `PrivacyConsentService.recordCaseDeposit` | Case à cocher dans `EligibilityAssistant` / `NewCase` | `PrivacyRightsIT`, `CaseCreationIT` | DONE | 100 | 85 |
| KON-FUNC-006 | `GET /me/data-export` et `POST /me/data-deletion-request` | MVP | 5 | `MePrivacyController`, `PersonalDataRightsService` | `PrivacyAccount.tsx` | `PrivacyRightsIT`, Playwright `privacy.spec.ts` (API simulée) | DONE | 100 | 85 |
| KON-FUNC-007 | Rétention par type de document et purge planifiée | MVP | 3 | `RetentionPurgeService`, une durée DPA (défaut 1825 jours) | — | `RetentionPurgeIT` | PARTIAL | 50 | 40 |
| KON-FUNC-008 | DPA par organisation | MVP | 3 | `OrganizationDpaController` | Pas d'écran d'acceptation | `OrganizationDpaIT` | PARTIAL | 90 | 75 |
| KON-FUNC-009 | Procédure de violation sous 72 h | MVP | 2 | — | — | Paragraphe dans le registre, pas de runbook opératoire | PARTIAL | 55 | 55 |
| KON-FUNC-010 | Interface `PaymentProvider` | MVP | 5 | `payment/provider/PaymentProvider` | — | `PaymentProviderRegistryTest` | DONE | 100 | 90 |
| KON-FUNC-011 | Prestataire Stripe | MVP | 3 | `StripeWebhookController`, checkout | Retour `?stripe=success` | `StripeCheckoutIT` (serveur factice) | PARTIAL | 70 | 55 |
| KON-FUNC-012 | Prestataire CinetPay | MVP | 3 | Adaptateur + webhook | Retour `?cinetpay=return` | `CinetPayCheckoutIT` | PARTIAL | 70 | 55 |
| KON-FUNC-013 | Prestataire PayDunya | MVP | 3 | `PayDunyaPaymentProvider` | Retour `?paydunya=return` | `PayDunyaCheckoutIT` | PARTIAL | 70 | 55 |
| KON-FUNC-014 | Paiement guichet `PENDING_MANUAL` | MVP | 3 | `confirm-manual` | Bouton dans `CaseDetail.tsx` | Couvert par les tests paiement / provider manuel | PARTIAL | 85 | 60 |
| KON-FUNC-015 | Webhooks de paiement idempotents | MVP | 5 | Table `payment_webhook_receipts` | — | Tests de webhook Stripe, CinetPay, PayDunya, MOCK | DONE | 100 | 80 |
| KON-FUNC-016 | i18n frontend fr / pt / en, fichiers par domaine | MVP | 3 | — | `src/locales/{fr,pt,en}/*.json`, `react-i18next` | Pas de scénario de bascule de langue | PARTIAL | 85 | 35 |
| KON-FUNC-017 | Erreurs et motifs en `message_key` | MVP | 3 | `RestExceptionHandler`, `review_message_key` | Clés `error.*` | Tests d'erreurs métier dispersés | PARTIAL | 75 | 50 |
| KON-FUNC-018 | Contenu métier JSONB fr / pt / en | MVP | 3 | `procedure_definitions`, exigences documentaires | `loc()` | `CatalogMvpProceduresIT`, `CatalogExtendedProceduresIT` | DONE | 100 | 80 |
| KON-FUNC-019 | Tenancy : filtre Hibernate, RLS, `app.current_org` | MVP | 5 | `TenantAwareEntity`, `TenantSessionBinder` | — | `OrganizationRlsIT`, `TenantSessionBinderTest` | DONE | 100 | 85 |
| KON-FUNC-020 | Table `organization_settings` | MVP | 2 | `OrganizationSettings` | Réglages dans `OrganizationSettings.tsx` | `OrganizationSettingsWriteIT` | DONE | 100 | 80 |
| KON-FUNC-021 | Table `feature_flags` | MVP | 3 | `FeatureFlagService` | Indirect (procédures et providers activés) | `PaymentProviderFlagIT` | DONE | 100 | 80 |
| KON-FUNC-022 | `document_access_logs` | MVP | 2 | Écrit à la lecture du contenu | Téléchargement via `downloadDocument` | IT documents | PARTIAL | 90 | 55 |
| KON-FUNC-023 | Chiffrement pgcrypto des champs extraits | MVP | 3 | `ExtractedFieldsCrypto` | Saisie agent dans `DocumentExtractionPanel` | `ExtractedFieldsCryptoTest`, `DocumentExtractionIT` | PARTIAL | 90 | 70 |
| KON-FUNC-024 | Éligibilité JSONLogic, pas de Drools | MVP | 5 | `JsonLogicEligibilityEvaluator` | `EligibilityAssistant` | `JsonLogicEligibilityEvaluatorTest`, `GuestEligibilityIT` | DONE | 100 | 90 |
| KON-FUNC-025 | Version de procédure figée à la création du dossier | MVP | 5 | `CaseService.create` copie la version publiée | — | `CatalogPublishFrozenCaseIT` | DONE | 100 | 90 |
| KON-FUNC-026 | `DocumentExtractionService` stub MVP | MVP | 2 | `StubDocumentExtractionService` | Panneau d'extraction agent | `StubDocumentExtractionServiceTest`, `DocumentExtractionIT` | DONE | 100 | 80 |
| KON-FUNC-027 | Colonnes `document_validations` et `ai_confidence` | MVP | 2 | Migration `V23` | Affichage limité | `DocumentExtractionIT` | DONE | 100 | 70 |
| KON-FUNC-028 | OpenTelemetry MVP (traces Spring → n8n → OCR) | MVP | 3 | Export OTLP dans Compose | — | `PrometheusEndpointIT` seulement | PARTIAL | 70 | 35 |
| KON-FUNC-029 | Testcontainers PostgreSQL, Keycloak, MinIO | MVP | 3 | PostgreSQL et MinIO dans les IT | — | Pas de conteneur Keycloak | PARTIAL | 65 | 65 |
| KON-FUNC-030 | Contrat OpenAPI en CI | MVP | 3 | `OpenApiContractIT` | — | Inclus dans `mvn verify` local ; job GitHub non lancé ici | DONE | 100 | 75 |
| KON-FUNC-031 | GitHub Actions, Trivy, SBOM CycloneDX | MVP | 3 | `.github/workflows/ci.yml` | Job `frontend` présent | Workflow non exécuté depuis cet audit | PARTIAL | 100 | 25 |
| KON-FUNC-032 | Services Docker du MVP | MVP | 5 | Compose : Postgres, Redis, Keycloak, MinIO, n8n, Mailpit, backend, nginx, OTel, Prometheus, Grafana, Tempo | Pas d'images frontend ni admin | Santé des conteneurs non sondée ici | PARTIAL | 75 | 30 |
| KON-FUNC-033 | Tranche France → Guinée-Bissau → visa touristique | MVP | 5 | Seed `V6__seed_visa_tourism_fr_gw.sql` | Parcours `CaseJourney` | IT création / documents / paiement ; e2e live non joué | PARTIAL | 68 | 40 |
| KON-FUNC-034 | Keycloak OIDC, JWT resource server, rôles | MVP | 5 | `SecurityConfig`, realm importé | `oidc-client-ts`, `userManager.ts` | Slices d'autorisation avec JWT de test ; login réel non rejoué | PARTIAL | 88 | 50 |
| KON-FUNC-035 | Propriété du dossier et isolation organisation | MVP | 5 | `CaseService.assertCanView`, RLS | File agent et liste citoyen | `OrganizationRlsIT`, `CaseCitizenListIT`, `CompanyCaseVisibilityIT` | PARTIAL | 85 | 70 |
| KON-FUNC-036 | MinIO, URL présignées, SSE | MVP | 5 | `S3ObjectStorage.put/get` via l'API | Téléchargement authentifié | `DocumentUploadIT`, `S3ObjectStorageTest` | PARTIAL | 60 | 50 |
| KON-FUNC-037 | n8n notifie sans décider | MVP | 5 | `N8nOutboxWebhookClient` | — | `N8nOutboxWebhookClientTest` ; Mailpit non sondé | PARTIAL | 80 | 30 |
| KON-FUNC-038 | Conventions `ApiResponse`, `PageResponse`, `BaseEntity`, MapStruct, Flyway | MVP | 3 | Présents ; `CaseResponseAssembler` reste manuel | — | IT de contrat | PARTIAL | 80 | 70 |
| KON-FUNC-039 | Rotation du refresh token | MVP | 2 | Le serveur ne stocke pas de refresh | `automaticSilentRenew: true` | Non prouvé sur le realm | PARTIAL | 55 | 15 |
| KON-FUNC-040 | Stack UI : shadcn, TanStack Query, Zustand, RHF, Zod, i18next | MVP | 2 | — | Query, i18next, Tailwind, quelques primitives Radix. Zustand, React Hook Form et Zod absents | Vitest sur des helpers | PARTIAL | 45 | 25 |
| KON-FUNC-041 | Catalogue pays, procédures, exigences, versions, admin | MVP | 5 | `CountryController`, `ProcedureController` | `CatalogList`, `CatalogEditor`. Pas d'écran pays ni d'archivage | `CatalogProcedureIT` | PARTIAL | 88 | 70 |
| KON-FUNC-042 | Cycle de vie du dossier | MVP | 5 | Création, lecture, liste, statuts partiels | Liste, détail, pas de clôture | `CaseCreationIT` | PARTIAL | 55 | 45 |
| KON-FUNC-043 | Documents : dépôt, checklist, revue, correction, rejet, remplacement | MVP | 5 | Upload, accept, correction, reject API. `REPLACED` jamais écrit | Dépôt et correction. Pas de bouton rejeter | `DocumentUploadIT`, `DocumentReviewIT`, Playwright documents simulé | PARTIAL | 72 | 55 |
| KON-FUNC-044 | Back-office agent | MVP | 5 | File, filtres, historique, superviseur | `CaseQueue`, `CaseDetail`, `CaseHistory` | Playwright `dashboard.spec.ts` simulé | PARTIAL | 78 | 45 |
| KON-FUNC-045 | Tarif figé sur la commande | MVP | 5 | `FrozenPricing` depuis la version de procédure | Affichage dans l'assistant et le paiement | `FrozenPricingTest`, IT checkout | PARTIAL | 90 | 75 |
| KON-FUNC-046 | Portail citoyen de bout en bout | MVP | 5 | API du parcours | Vues dans `citizen-portal/src/App.tsx` | Playwright simulé partiel ; formulaire complet et paiement hors suite | PARTIAL | 68 | 35 |
| KON-FUNC-047 | Hash SHA-256 de chaque document | MVP | 3 | `DocumentFiles.sha256Hex` | — | `DocumentFilesTest` | DONE | 100 | 80 |
| KON-FUNC-048 | Module rendez-vous | MVP | 3 | `AppointmentService`, holds Redis | `JourneyAppointment`, `Slots` | `AppointmentBookingIT`, `AppointmentSlotHoldIT`, Playwright slots simulé | PARTIAL | 85 | 60 |
| KON-FUNC-049 | Journal `audit_logs` | MVP | 3 | Aucune table, aucun module `audit` | `UX_GUIDELINES.md` le cite ; l'écran lit l'outbox | — | NOT_STARTED | 0 | 0 |
| KON-FUNC-050 | Spring Boot décide, n8n orchestre | MVP | 5 | Règles, tarifs et statuts dans les services Java | — | README et workflow n8n sans écriture métier | DONE | 95 | 70 |
| KON-FUNC-051 | Migrations Flyway, `ddl-auto` hors `update` | MVP | 2 | `ddl-auto: validate`, V1–V31 | — | IT Testcontainers appliquent Flyway | DONE | 100 | 80 |
| KON-FUNC-052 | Spring Statemachine | V2 | 2 | Absent | — | — | NOT_STARTED | 0 | 0 |
| KON-FUNC-053 | Microservice OCR isolé, sans PostgreSQL | V2 | 5 | Stub dans le monolithe | Saisie manuelle | IT du stub | PARTIAL | 15 | 10 |
| KON-FUNC-054 | Ollama (Qwen ou Mistral) | V2 | 3 | Absent | — | — | NOT_STARTED | 0 | 0 |
| KON-FUNC-055 | Alerte hash identique entre demandeurs | V2 | 5 | `DocumentHashAlertService` | `DocumentHashAlertPanel` | IT documents | PARTIAL | 85 | 60 |
| KON-FUNC-056 | Cohérence MRZ | V2 | 3 | Aucun contrôle `MRZ_VALID` | — | — | NOT_STARTED | 0 | 0 |
| KON-FUNC-057 | Journal des dépôts refusés | V2 | 3 | `DocumentUploadRefusalService` | `DocumentUploadRefusalPanel` | `DocumentUploadRefusalIT` | PARTIAL | 90 | 70 |
| KON-FUNC-058 | Loki | V2 | 1 | Absent de Compose | — | — | NOT_STARTED | 0 | 0 |
| KON-FUNC-059 | White-label (prévu V2, table dès le MVP) | V2 | 3 | Branding, domaine, assets | `OrganizationSettings.tsx`, thème citoyen | `OrganizationBrandingIT` | PARTIAL | 80 | 40 |
| KON-FUNC-060 | Module expédition `shipment` | V2 | 1 | Package absent | — | — | NOT_STARTED | 0 | 0 |
| KON-FUNC-061 | Docker Swarm ou Kubernetes | ultérieur | 1 | Compose uniquement | — | — | NOT_STARTED | 0 | 0 |

V3 : aucune exigence textuelle disponible (KON-SRC-001, §48). Pourcentage non calculé.

## Preuves par exigence

### KON-FUNC-001 — Redis

`RedisInfrastructureConfig` active un `RedisCacheManager`. `ProcedureService` annote le catalogue (`catalog-procedures`, `catalog-procedure`). `RedisRateLimitFilter` limite les appels. `RedisSlotHoldStore` pose un verrou de créneau. `RedisGuestEligibilityTicketStore` garde le ticket d'éligibilité invité. Il n'y a pas de session HTTP dans Redis : la session OIDC est dans `sessionStorage` (`userManager.ts`). Il n'y a pas de file Redis générale ; l'outbox est en PostgreSQL.

### KON-FUNC-002 et KON-FUNC-003 — Outbox

`OutboxAppender` écrit dans la même transaction métier, statut `PENDING`. `OutboxPublisher.publishDue` envoie le webhook, passe à `SENT`, ou à `FAILED` après `maxAttempts`, avec backoff (`OutboxBackoff`). Événements réellement émis : `CASE_CREATED`, `DOCUMENT_UPLOADED`, `PAYMENT_COMPLETED`, `CORRECTION_REQUESTED`, `APPOINTMENT_BOOKED`, `APPOINTMENT_CANCELLED`, `DATA_DELETION_REQUESTED`, `DATA_DELETION_COMPLETED`, `ACCOUNT_REGISTERED`. `CASE_COMPLETED` est déclaré et lu par `CaseHistoryService` pour l'affichage, et n'est jamais produit : aucun `setStatus(CaseStatus.COMPLETED)` dans les services métier.

### KON-FUNC-005 à 009 — RGPD

Consentement : `privacy_consents`, finalité `CASE_DEPOSIT`, lié au dossier. Export et demande d'effacement : `MePrivacyController`. La purge anonymise après la durée du DPA actif (`RetentionPurgeService`) ; elle n'a pas de durée par type de pièce. DPA : table `organization_dpa_agreements`, `GET` et `POST /accept`. L'admin React n'appelle pas ces routes. Violation : `docs/REGISTRE_TRAITEMENTS.md`, section « Violation de données », délai 72 h, procédure « hors code ».

### KON-FUNC-010 à 015 — Paiement

`PaymentProvider` expose création de checkout, vérification et webhook. Implémentations : MOCK, manuel, Stripe, CinetPay, PayDunya, activables par feature flag (`V17`, `V18`, `V28`). L'idempotence est la table `payment_webhook_receipts`. Les IT utilisent des serveurs HTTP factices. Aucune clé live n'a été exercée. Le portail citoyen appelle `POST /api/v1/payments/webhooks/MOCK` depuis le navigateur (`completeMockPayment`).

### KON-FUNC-016 à 018 — i18n

Fichiers citoyen : `common`, `wizard`, `journey`, `cases`, `error`. Fichiers admin : `common`, `case`, `queue`, `catalog`, `slots`, `supervisor`, `orgSettings`. Les noms de procédures seedées sont des objets `fr` / `pt` / `en`. Les règles JSONLogic restent des structures, pas des textes traduits ; le refus renvoie `error.eligibility.not_met`.

### KON-FUNC-019 — Multi-tenant

`TenantSessionBinder` exécute `set_config('app.current_org', ?, true)`. Les policies RLS utilisent `app_current_org()` et `app_is_platform_admin()`, avec `FORCE ROW LEVEL SECURITY` sur les tables sensibles. Le filtre Hibernate est `TenantAwareEntity`.

### KON-FUNC-024 — Règle vide

`JsonLogicEligibilityEvaluator.evaluate` retourne `true` si `rules` est null ou vide. Le PRD v2 ne fixe pas ce comportement. C'est un choix d'implémentation à confirmer produit : une procédure publiée sans règle est éligible pour tout fait.

### KON-FUNC-033 — Tranche verticale

Seed `VISA_TOURISM_FR_GW` (France vers Guinée-Bissau). Le code permet création, dépôt, commande, paiement simulé, rendez-vous et correction. La clôture `COMPLETED` manque. Le parcours n'a pas été rejoué sur la pile Docker pendant cet audit.

### KON-FUNC-036 — MinIO

`S3ObjectStorage.put` envoie `PutObjectRequest` sans SSE. `get` lit les octets et l'API les renvoie sur `GET /content` avec JWT. Il n'y a pas de méthode presign dans ce module.

### KON-FUNC-037 — n8n

Workflow versionné `infrastructure/n8n/konselyavisa-outbox-notify.json`. Switch sur `CASE_CREATED`, `PAYMENT_COMPLETED`, `CORRECTION_REQUESTED`, sinon e-mail générique. `n8n-init` importe le workflow (`bootstrap.js`). Les trois échantillons JSON sont des payloads de démonstration. Mailpit n'a pas été consulté.

### KON-FUNC-042 — Dossier

`CaseStatus` : `CREATED`, `IN_PROGRESS`, `CORRECTION_REQUESTED`, `COMPLETED`, `CANCELLED`. Transitions observées : création → `CREATED` ; upload, paiement ou rendez-vous → `IN_PROGRESS` ; correction document → `CORRECTION_REQUESTED`. `COMPLETED` et `CANCELLED` ne sont jamais affectés. Pas de colonne d'affectation d'agent. `CaseController` n'a pas de `PATCH`. L'historique lit `outbox_events`.

### KON-FUNC-043 — Documents

Un nouvel upload crée une ligne `UPLOADED`. L'ancien fichier reste `UPLOADED`. Le statut `REPLACED` existe dans `V12` et dans `CaseNextActionResolver`, et n'est écrit nulle part. `POST .../reject` existe ; aucun écran ne l'appelle.

### KON-FUNC-049 — Audit

Recherche `audit_logs` : aucune migration `CREATE TABLE`. Le registre RGPD mentionne ce journal comme cible d'interdiction de données en clair. L'historique agent est l'outbox, pas un audit générique.

### KON-FUNC-050

Les services Java évaluent l'éligibilité, figent le prix et changent les statuts. Le workflow n8n formate des e-mails. Aucun nœud n8n n'appelle l'API pour modifier un dossier.

## Fonctionnalités présentes hors texte v2

Elles ne sont pas entrées dans les pourcentages.

- Site public, suivi de dossier par référence, inscription invité : package `guest`.
- Espace entreprise : `CompanyCaseController`, `CompanyCaseList.tsx`.
- Procédures seedées en plus du visa : légalisation, apostille (`V16`), eVisa, assurance, traduction (`V29`).
- Profil public d'organisation et charte déjà branchés sur le portail.

## Modules d'architecture absents

La section 2 nomme `workflow`, `shipment`, `notification`, `audit`, `integration`, `pricing`. Présents sous un autre nom : `dossier` (au lieu de `case`), tarif dans `payment.FrozenPricing`, notifications portées par n8n. Absents : `workflow`, `shipment`, `audit`, `integration`. Packages supplémentaires : `privacy`, `guest`, `redis`.
