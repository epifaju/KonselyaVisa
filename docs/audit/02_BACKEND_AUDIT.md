# 02 — Audit backend

Date : 2026-10-28. Périmètre lu : `backend/konselyavisa-api`. Aucun endpoint n'a été appelé sur une pile démarrée pendant cette mission. « Testé » signifie couvert par la campagne `mvn clean verify` du 2026-10-28 rapportée dans `docs/testing/TEST_EXECUTION_REPORT.md`, sauf mention contraire.

## 1. Cartographie

Monolithe Maven `konselyavisa-api`, Java 21, Spring Boot 3. Point d'entrée `KonselyaVisaApplication`.

| Package | Rôle réel |
|---|---|
| `common` | `BaseEntity`, `ApiResponse`, `PageResponse`, `RestExceptionHandler`, `BusinessException` |
| `identity` | `MeController`, annuaire Keycloak citoyen, convertisseur de rôles realm |
| `identity.security` | `SecurityConfig`, resource server JWT |
| `tenancy` | `TenantContext`, filtre Hibernate, `TenantSessionBinder` |
| `organization` | Organisations, settings, feature flags, branding |
| `catalog` | Pays, procédures, versions, cache Redis |
| `eligibility` | `JsonLogicEligibilityEvaluator` |
| `dossier` | Dossiers (`cases`), historique, file agent, synthèse entreprise et superviseur |
| `applicant` | Entité `Applicant` |
| `document` | Upload MinIO, revue, hash, refus, extraction stub, pgcrypto |
| `payment` | Commandes, providers, webhooks |
| `appointment` | Créneaux, réservations, holds |
| `outbox` | Append + publisher n8n |
| `privacy` | Consentement, export, effacement, DPA, purge |
| `guest` | Site public, ticket d'éligibilité, suivi, inscription |
| `redis` | Cache, rate-limit, holds |

Absents : `workflow`, `shipment`, `notification`, `audit`, `integration`. Pas de dépendance Spring Statemachine dans le code parcouru.

Environ 30 classes `@Service` (chiffre du rapport de tests du 28 octobre). 32 `@RestController`. Entités JPA sur le socle d'audit (`id`, dates, acteurs) via `BaseEntity` ou `TenantAwareEntity`. MapStruct : mappers catalogue, organisation, dossier, document, rendez-vous, commande, DPA. Assemblage manuel restant : `CaseResponseAssembler`.

`spring.jpa.hibernate.ddl-auto=validate` dans `application.yml` et `application-docker.yml`. Schéma : Flyway `V1` à `V31`.

## 2. Sécurité

`SecurityConfig` :

- Public : `/actuator/health`, `/actuator/prometheus`, OpenAPI et Swagger, `/api/v1/payments/webhooks/**`, `/api/v1/public/**`, `GET /api/v1/privacy-notices/current`.
- Le reste est authentifié.
- Rôles extraits du realm Keycloak (`KeycloakRealmRoleConverter`).
- `@PreAuthorize` sur les opérations d'écriture et les vues agent, superviseur, entreprise, admin.
- `TenantRequestFilter` alimente `TenantContext` depuis le JWT.
- `CaseService.assertCanView` restreint le citoyen à ses dossiers et le collaborateur entreprise aux siens. Le rôle seul ne suffit pas sur la lecture d'un dossier.

Écarts :

- `POST /api/v1/payments/webhooks/MOCK` est public, comme les autres webhooks. Le portail citoyen le déclenche lui-même pour simuler un paiement (`completeMockPayment`). Tant que le provider MOCK est activé par feature flag, un client qui connaît la référence peut confirmer le paiement. Les IT prouvent l'idempotence, pas le durcissement de ce provider hors démo.
- `/actuator/prometheus` et Swagger sont publics. Acceptable en local. À fermer devant un reverse proxy d'exposition.
- Le publisher outbox s'élève en `platformAdmin` le temps de lire les événements dus. C'est voulu pour franchir la RLS. Le composant doit rester interne.
- Pas de rotation de refresh token côté serveur. Le client OIDC renouvelle en silence. Le realm n'a pas été inspecté en fonctionnement.
- Aucune table `audit_logs`. L'exigence « ne pas journaliser un numéro de passeport dans `audit_logs.details_json` » n'a pas de support. `ApplicantFactsGuard` refuse des clés sensibles (`passportnumber`, etc.) dans les faits JSON.

Rôles vus dans le code : `CITIZEN`, `COMPANY_USER`, `AGENT`, `SUPERVISOR`, `BUSINESS_ADMIN`, `PLATFORM_ADMIN`. Le détail des matrices de la v1 (§38) n'est pas dans le dépôt. Le realm importé est `infrastructure/keycloak/konselyavisa-realm.json` (non rejoué ici).

## 3. Services métier principaux

| Service | Décision métier |
|---|---|
| `JsonLogicEligibilityEvaluator` | Éligible si la règle JSONLogic est vraie. Règle vide ou nulle : éligible. |
| `CaseService.create` | Exige une procédure active, offerte aux citoyens, version publiée, éligibilité vraie, consentement. Fige la version. Émet `CASE_CREATED`. |
| `DocumentService` | Contrôle le code d'exigence, le type MIME, le hash, le doublon inter-demandeurs, stocke dans MinIO, émet `DOCUMENT_UPLOADED`. |
| `DocumentReviewService` | Accepte, demande une correction (`CORRECTION_REQUESTED` + outbox), rejette la pièce. Ne clôt pas le dossier. |
| `PaymentService` | Prix figé depuis `procedure_versions.pricing`. Checkout provider. Webhook idempotent. Paiement réussi : commande `PAID`, dossier `IN_PROGRESS`, outbox `PAYMENT_COMPLETED`. |
| `AppointmentService` | Hold Redis, réservation, annulation, outbox rendez-vous. |
| `OutboxPublisher` | Retry / FAILED. Aucune transition de dossier. |
| `RetentionPurgeService` | Anonymisation différée, outbox `DATA_DELETION_COMPLETED`. |
| `FeatureFlagService` | Active procédures et providers par organisation. |
| `StubDocumentExtractionService` | Saisie agent, chiffrement pgcrypto, `ai_confidence` manuel. |

Il n'existe pas de `EligibilityService` séparé, ni de machine à états, ni de remboursement.

## 4. Inventaire REST

Légende tests : « IT » = test d'intégration de la campagne du 28 octobre ; « slice » = `ApiAuthorizationSliceTest` ou `ResourceAuthorizationSliceTest`. Usage UI : portail qui appelle la route.

| Méthode | URL | Contrôleur | Auth | Usage UI | Test |
|---|---|---|---|---|---|
| GET | `/api/v1/me` | `MeController` | JWT | Citoyen et admin | slice / IT |
| GET | `/api/v1/me/data-export` | `MePrivacyController` | authentifié | `PrivacyAccount` | `PrivacyRightsIT` |
| POST | `/api/v1/me/data-deletion-request` | `MePrivacyController` | authentifié | `PrivacyAccount` | `PrivacyRightsIT` |
| GET | `/api/v1/privacy-notices/current` | `PrivacyNoticeController` | public | Assistant, pages légales | IT privacy |
| GET | `/api/v1/public/org` | `PublicOrgController` | public | Les deux portails | `PublicSiteIT` |
| GET | `/api/v1/public/site` | `PublicSiteController` | public | `PublicHome` | `PublicSiteIT` |
| POST | `/api/v1/public/case-status` | `PublicSiteController` | public | `PublicTrackCase` | IT guest |
| GET | `/api/v1/public/procedures` | `PublicGuestController` | public | Assistant invité | `GuestEligibilityIT` |
| POST | `/api/v1/public/eligibility-tickets` | `PublicGuestController` | public | Assistant invité | `GuestEligibilityIT` |
| POST | `/api/v1/public/account-registrations` | `PublicGuestAccountController` | public | `GuestAccountRegister` | `GuestAccountRegistrationIT` |
| GET | `/api/v1/public/org/branding/{asset}` | `PublicBrandingAssetController` | public | Logos | `OrganizationBrandingAssetIT` |
| GET | `/api/v1/countries` | `CountryController` | JWT | Aucun écran | IT catalogue |
| GET | `/api/v1/countries/{id}` | `CountryController` | JWT | Aucun | IT catalogue |
| POST | `/api/v1/countries` | `CountryController` | `PLATFORM_ADMIN` | Aucun | IT |
| PATCH | `/api/v1/countries/{id}` | `CountryController` | admin plateforme ou métier | Aucun | IT |
| GET | `/api/v1/procedures` | `ProcedureController` | JWT | Catalogue et assistant | `CatalogProcedureIT` |
| GET | `/api/v1/procedures/{id}` | `ProcedureController` | JWT | Éditeur et parcours | IT |
| POST | `/api/v1/procedures` | `ProcedureController` | admin métier / plateforme | Pas de création depuis l'éditeur | IT |
| PATCH | `/api/v1/procedures/{id}` | `ProcedureController` | idem | `CatalogEditor` | IT |
| POST | `/api/v1/procedures/{id}/versions` | `ProcedureController` | idem | `CatalogEditor` | IT |
| PATCH | `.../versions/{versionId}` | `ProcedureController` | idem | `CatalogEditor` | IT |
| POST | `.../publish` | `ProcedureController` | idem | `CatalogEditor` | `CatalogPublishFrozenCaseIT` |
| POST | `.../archive` | `ProcedureController` | idem | Aucun bouton | IT |
| GET | `/api/v1/organizations/me` | `OrganizationController` | JWT | Réglages admin | IT org |
| GET | `/api/v1/organizations` | `OrganizationController` | `PLATFORM_ADMIN` | Aucun | IT |
| GET | `/api/v1/organizations/{id}` | `OrganizationController` | selon garde | Aucun | IT |
| POST | `/api/v1/organizations` | `OrganizationController` | `PLATFORM_ADMIN` | Aucun | IT |
| PATCH | `/api/v1/organizations/{id}` | `OrganizationController` | admin | Aucun (le portail patche `/me/settings`) | IT |
| PATCH | `/api/v1/organizations/me/settings` | `OrganizationSettingsController` | admin métier / plateforme | `OrganizationSettings` | `OrganizationSettingsWriteIT` |
| POST/DELETE | `/api/v1/organizations/me/branding/{asset}` | `OrganizationBrandingAssetController` | idem | `OrganizationSettings` | IT branding |
| GET | `/api/v1/organizations/me/dpa` | `OrganizationDpaController` | staff | Aucun | `OrganizationDpaIT` |
| POST | `/api/v1/organizations/me/dpa/accept` | `OrganizationDpaController` | admin | Aucun | `OrganizationDpaIT` |
| POST | `/api/v1/cases` | `CaseController` | citoyen, entreprise, staff | `NewCase`, `GuestTicketAttach` | `CaseCreationIT` |
| GET | `/api/v1/cases` | `CaseController` | idem | Listes | `CaseCitizenListIT` |
| GET | `/api/v1/cases/{id}` | `CaseController` | idem + propriété | Parcours et détail | IT |
| GET | `/api/v1/cases/{id}/history` | `CaseHistoryController` | staff | `CaseHistory` | IT dossier |
| GET | `/api/v1/agent/cases` | `AgentCaseController` | staff | `CaseQueue` | IT + Playwright simulé |
| GET | `/api/v1/supervisor/summary` | `SupervisorSummaryController` | superviseur et admins | `CaseQueue` | `SupervisorSummaryIT` |
| GET | `/api/v1/company/cases/summary` | `CompanyCaseController` | `COMPANY_USER` | `CompanyCaseList` | `CompanyCaseVisibilityIT` |
| POST/GET | `/api/v1/cases/{caseId}/documents` | `CaseDocumentController` | propriétaire ou staff | Parcours | `DocumentUploadIT` |
| GET | `.../documents/{id}/content` | `CaseDocumentController` | idem | Téléchargement | IT |
| POST | `.../accept` | `DocumentReviewController` | staff | `CaseDetail` | `DocumentReviewIT` |
| POST | `.../request-correction` | `DocumentReviewController` | staff | `CorrectionPanel` | IT |
| POST | `.../reject` | `DocumentReviewController` | staff | Aucun | IT |
| POST | `.../extraction` | `DocumentExtractionController` | staff | `DocumentExtractionPanel` | `DocumentExtractionIT` |
| GET | `.../hash-alerts` | `DocumentHashAlertController` | staff | `DocumentHashAlertPanel` | IT |
| GET | `.../upload-refusals` | `DocumentUploadRefusalController` | staff | `DocumentUploadRefusalPanel` | `DocumentUploadRefusalIT` |
| POST/GET | `/api/v1/cases/{caseId}/orders` | `CaseOrderController` | propriétaire ou staff | `JourneyPayment`, détail | IT paiement |
| POST | `/api/v1/payments/webhooks/{providerCode}` | `PaymentController` | public | Citoyen pour MOCK | IT |
| POST | `/api/v1/payments/webhooks/STRIPE` | `StripeWebhookController` | public | — | `StripeCheckoutIT` |
| POST | `/api/v1/payments/{id}/sync` | `StripeWebhookController` | authentifié large | `CaseJourney` | IT |
| POST | `/api/v1/payments/webhooks/CINETPAY` | `CinetPayWebhookController` | public | — | IT |
| POST | `/api/v1/payments/webhooks/PAYDUNYA` | `PayDunyaWebhookController` | public | — | IT |
| POST | `/api/v1/payments/{id}/confirm-manual` | `PaymentController` | staff | `CaseDetail` | unit provider manuel |
| POST/GET | `/api/v1/cases/{caseId}/appointments` | `AppointmentController` | large authentifié | `JourneyAppointment` | `AppointmentBookingIT` |
| POST | `/api/v1/appointments/{id}/cancel` | `AppointmentController` | idem | Parcours | IT |
| POST | `/api/v1/cases/{caseId}/appointment-holds` | `AppointmentHoldController` | idem | Parcours | `AppointmentSlotHoldIT` |
| POST/GET | `/api/v1/appointment-slots` | `AppointmentSlotController` | lecture large, écriture staff | `Slots`, parcours | IT |
| POST | `/api/v1/appointment-slots/{id}/cancel` | `AppointmentSlotController` | staff | `Slots` | IT |

Endpoints déclarés et couverts par des tests, sans écran : pays, création d'organisation, archivage de version, DPA, rejet de document.

Aucun appel frontend vers une route inexistante n'a été trouvé dans `frontend/**/src`. Le contrat suivi par l'UI correspond aux contrôleurs ci-dessus.

Incohérences de contrat :

- `CASE_COMPLETED` est un type d'événement documenté et absent de toute écriture.
- Le statut document `REPLACED` est dans la contrainte SQL et inutilisé.
- `completeMockPayment` parle au webhook public au lieu d'une action métier de confirmation de simulation.
- Les réponses passent par `ApiResponse` ou `PageResponse`.

## 5. Intégrations

| Intégration | Code | Preuve d'exécution | Écart |
|---|---|---|---|
| PostgreSQL | Flyway + JPA | IT Testcontainers | Pile Compose non sondée ici |
| Redis | Cache, rate-limit, holds | `RedisRateLimiterIT` | Pas de sessions |
| Keycloak | JWT + admin API inscription (`HttpKeycloakCitizenDirectory`) | Slices JWT simulés ; IT inscription avec admin désactivé | Pas de Testcontainers Keycloak |
| MinIO | AWS SDK `S3Client` | IT avec conteneur MinIO | Pas de SSE ni d'URL présignée |
| n8n | Webhook signé `X-Konselya-Signature` | Test unitaire du client | Livraison Mailpit non rejouée |
| Stripe / CinetPay / PayDunya | Adaptateurs HTTP | IT sur serveurs factices | Pas d'appel marchand réel |
| OTel | Endpoint OTLP configuré dans Compose | `PrometheusEndpointIT` | Traces vers n8n et OCR non démontrées |

## 6. Manques backend pour le MVP v2

1. Transition vers `COMPLETED` (et usage réel de `CANCELLED`) avec événement outbox unique.
2. Journal `audit_logs`.
3. Durée de conservation par type de document.
4. SSE MinIO et, si le PRD reste la référence, URLs présignées.
5. Garde produit sur le provider MOCK hors démonstration.
6. Affectation d'agent : absente du schéma. La v1 ne permet pas de dire si elle est obligatoire.
7. Remplacement explicite (`REPLACED`) lors d'un second dépôt.
8. Testcontainers Keycloak.
9. Packages `audit`, `workflow`, `shipment`, `integration`.
