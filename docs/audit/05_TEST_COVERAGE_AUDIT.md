# 05 — Audit des tests et de la couverture

Date : 2026-10-28. Cette mission n'a pas relancé Maven, Vitest ni Playwright. Les chiffres d'exécution proviennent de `docs/testing/TEST_EXECUTION_REPORT.md`, produit le même jour à partir de `mvn clean verify` et de `npx playwright test`. Ils sont repris comme preuve, pas recalculés. La couverture JaCoCo n'est pas un pourcentage d'avancement PRD.

## 1. Ce qui a été exécuté (rapport du 28 octobre)

| Suite | Commande rapportée | Tests | Réussis | Échecs | Ignorés |
|---|---|---:|---:|---:|---:|
| Surefire | `mvn clean verify` | 472 | 472 | 0 | 0 |
| Failsafe (`*IT.java`) | même commande | 56 | 56 | 0 | 0 |
| Vitest citoyen | `npm test` | 13 | 13 | 0 | 0 |
| Vitest admin | `npm test` | 7 | 7 | 0 | 0 |
| Vitest design tokens | `npm test` | 3 | 3 | 0 | 0 |
| Playwright isolé | `npx playwright test` | 21 | 21 | 0 | 0 |
| Playwright live | `playwright.live.config.ts` | — | — | — | Non exécuté |
| Job GitHub Actions | — | — | — | — | Non exécuté |
| Trivy / SBOM en CI | job `supply-chain` | — | — | — | Non exécuté ici |

Surefire exclut `**/*IT.java`. Les IT demandent Docker (Testcontainers PostgreSQL, MinIO pour l'extraction et les uploads).

JaCoCo, mêmes sources `target/site/jacoco/jacoco.csv` :

| Périmètre | Lignes | Branches |
|---|---:|---:|
| 12 packages inclus dans le contrôle | 3 679 / 3 944 = 93,28 % | 1 340 / 1 659 = 80,77 % |

Seuils : lignes ≥ 85 %, branches ≥ 80 %, `jacoco.haltOnFailure=true`, contrôle vert sur cet exécutable. Exclusions documentées : classe d'application, `*MapperImpl`. Ce ratio mesure le code Java instrumenté, pas les exigences produit.

Le rapport cite des classes encore sous la moyenne de branches : adaptateurs HTTP Stripe, CinetPay et PayDunya. Ils restent dans le rapport. Deux `catch` cryptographiques de `CinetPayHmac` et `DocumentFiles` sont décrits comme inatteignables sans remplacer le provider JVM.

## 2. Backend — inventaire

| Famille | Présence | Exécution du 28 octobre |
|---|---|---|
| JUnit 5 + Mockito | 84 classes `*Test.java` d'après le rapport | 472 tests verts |
| Intégration Testcontainers | 34 classes `*IT.java` | 56 tests verts |
| Sécurité | `ApiAuthorizationSliceTest`, `ResourceAuthorizationSliceTest`, `KeycloakRealmRoleConverterTest` | Inclus dans Surefire |
| Règles | `JsonLogicEligibilityEvaluatorTest` | Inclus |
| Repositories | Exercés via les IT (RLS, dossiers, documents, paiements) | Inclus |
| OpenAPI | `OpenApiContractIT` contre `required-operations.json` | Inclus dans Failsafe |
| Keycloak réel | Absent | Impossible dans la suite actuelle |
| JaCoCo | Configuré dans `pom.xml` | Rapport produit |

IT représentatives et exigence associée :

- `OrganizationRlsIT` — isolation.
- `CaseCreationIT`, `CatalogPublishFrozenCaseIT` — dossier et version figée.
- `GuestEligibilityIT` — éligibilité invité.
- `DocumentUploadIT`, `DocumentReviewIT`, `DocumentExtractionIT`, `DocumentUploadRefusalIT` — pièces.
- `StripeCheckoutIT`, `CinetPayCheckoutIT`, `PayDunyaCheckoutIT`, `PaymentCheckoutIT`, `PaymentProviderFlagIT` — paiement.
- `OutboxPublisherIT` — publication.
- `PrivacyRightsIT`, `RetentionPurgeIT`, `OrganizationDpaIT` — RGPD.
- `AppointmentBookingIT`, `AppointmentSlotHoldIT` — rendez-vous.
- `RedisRateLimiterIT`, `PrometheusEndpointIT`.

Aucun test ne démontre l'émission de `CASE_COMPLETED` : le comportement est absent du code de production.

## 3. Frontend

Playwright isolé (API et OIDC simulés) :

| Fichier | Ce qu'il prouve | Ce qu'il ne prouve pas |
|---|---|---|
| `citizen/authentication.spec.ts` | Accueil, redirection OIDC simulée, assistant sans compte | Realm Keycloak |
| `citizen/eligibility.spec.ts` | Passeport court, refus API, mots de passe | JSONLogic réel |
| `citizen/case-management.spec.ts` | Suivi 404, liste vide, erreur, session expirée, déconnexion | PostgreSQL |
| `citizen/documents.spec.ts` | Dépôt d'une pièce contre une réponse simulée | MinIO |
| `citizen/privacy.spec.ts` | Notice, export, demande d'anonymisation | Purge réelle |
| `citizen/company.spec.ts` | Liste entreprise | Isolation SQL |
| `admin/dashboard.spec.ts` | Anonyme, rôle refusé, file, filtre, correction, déconnexion | File réelle |
| `admin/security.spec.ts` | Menus agent vs admin métier | JWT Keycloak |
| `admin/slots.spec.ts` | Création d'un créneau simulée | Concurrence Redis |

Absents de cette suite, d'après le rapport de tests : formulaire de dossier complet, édition du catalogue, paiement, accès inter-organisation réel.

Vitest : helpers (`caseReference`, `guestTicket`, `flagEmoji`, `caseAge`, `isConsularStaff`, couleur de marque). Pas de test de composant d'écran.

## 4. Limites

- Un test vert sur API simulée ne rend pas l'écran opérationnel en intégration.
- Un IT Testcontainers ne rend pas le Compose local « démarré aujourd'hui ».
- 93 % de lignes JaCoCo ne signifient pas 93 % du PRD.
- Le job `e2e-live` est le seul scénario prévu contre l'API réelle. Il n'a pas de résultat.
- Cette mission n'a pas ouvert `playwright-report/index.html` ni recompté les fichiers Surefire. En cas d'écart avec un `mvn verify` ultérieur, le rapport du 28 octobre prévaut jusqu'à une nouvelle exécution.
