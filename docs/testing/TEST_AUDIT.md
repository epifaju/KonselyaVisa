# Audit des tests — KonselyaVisa

Date de l’audit : 2026-10-28. Périmètre : code réellement présent dans le dépôt, pas les modules seulement décrits dans le PRD.

Build backend : Maven, module unique `backend/konselyavisa-api` (Java 21, Spring Boot 3.5.5).  
Frontends : `frontend/citizen-portal` (port 5175) et `frontend/admin-portal` (port 5176), React 18 + Vite + Vitest. Pas de routeur d’URL : la navigation est un état React.  
CI existante : `.github/workflows/ci.yml` (`mvn verify` + SBOM/Trivy). Pas de GitLab CI ni Jenkins. Pas de Playwright.

## Backend — inventaire

| Zone | Constat |
|---|---|
| Packages | `applicant`, `appointment`, `catalog`, `common`, `document`, `dossier`, `eligibility`, `guest`, `identity`, `organization`, `outbox`, `payment`, `privacy`, `redis`, `tenancy` |
| `@Service` | 30 classes. Chacune a une classe de test unitaire dédiée, sauf `StubDocumentExtractionService` dont seule la sanitization statique est testée (`DocumentExtractionSanitizeTest`) ; `saveManual` est couvert par `DocumentExtractionIT` (Testcontainers) |
| Contrôleurs REST | 32 `@RestController` |
| Sécurité | `SecurityConfig` (JWT Keycloak, stateless) + `@PreAuthorize` + `KeycloakRealmRoleConverter` + `TenantRequestFilter` (`organization_id`) |
| Éligibilité | `JsonLogicEligibilityEvaluator` (JSONLogic). Pas de classe `EligibilityService` |
| Workflow | Pas de moteur de workflow séparé. Transitions dans `CaseService`, `DocumentReviewService`, `PaymentService`, `AppointmentService` |
| Paiement | `PaymentService` + `PaymentProvider` (MOCK, MANUAL, Stripe, CinetPay, PayDunya) |
| Stockage | `S3ObjectStorage` (MinIO/S3), testé par mock du client AWS |
| n8n | `N8nOutboxWebhookClient` + `OutboxPublisher` (pas de décision métier dans n8n) |
| Keycloak admin | `HttpKeycloakCitizenDirectory` / stub, tests HTTP mockés |
| Flyway / RLS | Migrations présentes. `OrganizationRlsIT` couvre l’isolation SQL |
| Tests déjà là | 74 classes `*Test` (Mockito, sans contexte Spring pour les services) et 34 classes `*IT` (Testcontainers PostgreSQL, Failsafe) |
| JaCoCo | Plugin 0.8.13, rapport lié à la phase `test` seulement (les IT n’entrent pas dans le HTML/XML). Pas de seuil, pas d’artefact CI |
| Trou structurant | Aucun `@WebMvcTest` / MockMvc. Aucune assertion HTTP 401/403 dans les IT. `KeycloakRealmRoleConverter` sans test. `PaymentProviderRegistry`, `MockPaymentProvider` et `ManualPaymentProvider` sans test direct |

Les contrôles d’appartenance de dossier et de rôle sont déjà exercés dans les services (`CaseServiceTest`, `DocumentReviewServiceTest`, `CompanyCaseServiceTest`, etc.) et en base (`OrganizationRlsIT`). Ils ne sont pas rejoués au niveau du filtre HTTP.

## Frontend — inventaire

| Application | Parcours réellement codés | Tests existants |
|---|---|---|
| Portail citoyen | Accueil public, connexion OIDC, invité, assistant d’éligibilité, suivi de dossier, liste / création / parcours de dossier, compte entreprise, vie privée, rejet du personnel consulaire | Vitest node : `caseReference`, `flagEmoji`, `guestTicket`, `utils` |
| Portail agent | Écran de connexion, refus si rôle insuffisant, file, détail (pièces, correction, paiement manuel), créneaux, catalogue, paramètres d’organisation | Vitest node : `caseReference`, `caseAge`, `utils` |
| `packages/design-tokens` | Couleur de marque | `brandColor.test.ts` |
| Playwright | Absent | — |
| Garde de rôle UI | `isConsularStaff` / `rolesFromSession` | Non testé |

Les appels API passent par `fetch` dans `src/api/client.ts`. L’authentification est OIDC (`oidc-client-ts`, sessionStorage). Il n’y a pas de routes protégées par URL : sans session, le citoyen voit l’accueil public et l’agent voit l’écran de connexion.

## Tableau de couverture fonctionnelle (état avant ajout)

| Module | Composant | Tests existants | Tests manquants | Priorité |
|---|---|---|---|---|
| Dossiers | `CaseService` | Création, éligibilité refusée, outbox, propriété citoyen / entreprise / agent, listes | Slice HTTP 401/403/400 sur `CaseController` | Critique |
| Dossiers | `AgentCaseQueryService`, `CaseHistoryService`, `CompanyCaseService`, `SupervisorSummaryService` | Unitaires + IT liste / visibilité / synthèse | Autorisation HTTP du superviseur et de l’espace entreprise | Critique |
| Documents | `DocumentService`, `DocumentReviewService` | Formats, fichier vide, rollback stockage, revue, dossier terminé | — (déjà dense) | Critique |
| Documents | `StubDocumentExtractionService.saveManual` | Sanitization + IT | Unitaire : citoyen refusé, dossier clos, document absent, persistance chiffrée | Critique |
| Documents | `DocumentHashAlertService`, `DocumentUploadRefusalService` | Unitaires + IT | — | Haute |
| Éligibilité | `JsonLogicEligibilityEvaluator` | Visa, légalisation, apostille, règles vides | Règles nulles, résultat non booléen, règles invalides, `validate` | Critique |
| Éligibilité | `GuestEligibilityService` | Domaine, ticket, conflit d’organisation, version absente | — | Critique |
| Rendez-vous | `AppointmentService` | Créneau, hold Redis, réservation, annulation, idempotence | — | Haute |
| Paiement | `PaymentService` | Checkout, webhook dupliqué, manuel, sync prestataire | — | Critique |
| Paiement | `MockPaymentProvider`, `ManualPaymentProvider`, `PaymentProviderRegistry` | Indirect via `PaymentService` (registry mocké) | Cas nominaux, référence inconnue, webhook incomplet, code inconnu | Haute |
| Catalogue | `ProcedureService`, `CountryService`, `ProcedureAccessGuard` | Publication, version figée, brouillon, locale | — | Haute |
| Sécurité | `SecurityConfig`, `KeycloakRealmRoleConverter` | Filtre tenant et rate limit testés à part | Convertisseur de rôles ; 401 anonyme ; 403 rôle insuffisant ; routes publiques `permitAll` | Critique |
| Multi-tenant | `TenantRequestFilter`, RLS | Unitaires + `OrganizationRlsIT` | — | Critique |
| Organisation | services branding / settings / feature flags | Unitaires + IT | — | Moyenne |
| Vie privée | consentement, DPA, export, purge | Unitaires + IT | — | Haute |
| Invité | site public, suivi, inscription | Unitaires + IT | — | Haute |
| Outbox / n8n | appender, publisher, client webhook | Unitaires + `OutboxPublisherIT` | — | Haute |
| Portail citoyen | accueil, login, éligibilité, suivi, erreur API, session | Libs seulement | Playwright UI (API mockée) + `isConsularStaff` | Critique |
| Portail agent | file, filtre, détail, pièce, rôle insuffisant, anonyme | Libs seulement | Playwright UI (API mockée) | Critique |
| E2E réel Keycloak + API + PostgreSQL | — | — | Non exécutable sans pile démarrée ; ne pas le présenter comme couvert par les mocks | Critique |

## Hors périmètre (non implémenté comme module autonome)

Remboursement, moteur de workflow dédié, `EligibilityService` nommé ainsi, Drools. Les règles d’éligibilité sont des documents JSONLogic évalués par `JsonLogicEligibilityEvaluator`.
