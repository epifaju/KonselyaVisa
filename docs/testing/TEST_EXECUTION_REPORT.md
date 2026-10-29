# Rapport d'exécution des tests — KonselyaVisa

Date : 2026-10-28. Périmètre : code réellement présent dans le dépôt. L'audit préalable est dans `docs/testing/TEST_AUDIT.md` (état avant ajout).

**STATUS : PARTIAL**

`mvn clean verify` (2026-10-28, vers 23:26) est vert. Lignes métier 93,28 % et branches métier 80,77 % : les deux seuils JaCoCo sont atteints, et `jacoco.haltOnFailure` est maintenant `true` (`jacoco:check@check` code 0 sur cet exécutable). Les 21 scénarios Playwright isolés sont verts. Le fumigène réel `tests/playwright.live.config.ts` n'a pas été exécuté.

## 1. Inventaire

| Mesure | Valeur |
|---|---:|
| Services `@Service` | 30 |
| Services couverts par une classe de test dédiée | 30 |
| Classes `*Test.java` | 84 |
| Classes `*IT.java` (inchangées) | 34 |
| Nouvelles classes unitaires | 12 |
| Classes de test déjà présentes | conservées, aucune suppression |
| Contrôleurs REST | 32 |
| Slices HTTP d'autorisation | 2 (`ApiAuthorizationSliceTest`, `ResourceAuthorizationSliceTest`) |

Nouvelles classes :

- `identity/security/KeycloakRealmRoleConverterTest`
- `identity/security/ApiAuthorizationSliceTest`
- `identity/CaseCreatorSnapshotTest`
- `payment/provider/PaymentProviderRegistryTest`
- `payment/provider/MockPaymentProviderTest`
- `payment/provider/ManualPaymentProviderTest`
- `payment/api/PayDunyaWebhookControllerTest`
- `document/StubDocumentExtractionServiceTest`
- `document/crypto/ExtractedFieldsCryptoTest`
- `dossier/CaseResponseAssemblerTest`
- `guest/PublicSiteSettingsTest`
- `identity/security/ResourceAuthorizationSliceTest`

Classes existantes étendues : `JsonLogicEligibilityEvaluatorTest`, `CurrentUserSubjectTest`, `CinetPayWebhookControllerTest`, `OrganizationBrandingSettingsTest`, `S3ObjectStorageTest`.

Il n'existe pas de classe `EligibilityService`. L'éligibilité est `JsonLogicEligibilityEvaluator`. Il n'y a pas de moteur de workflow séparé ni de remboursement implémenté.

## 2. Backend

Exécution le 2026-10-28, JDK `C:\Program Files\Java\jdk-21.0.10` (Maven suit `JAVA_HOME` ; un JDK 17 fait échouer `release 21`).

| Suite | Preuve | Tests | Réussis | Échecs | Erreurs | Ignorés |
|---|---|---:|---:|---:|---:|---:|
| Surefire (`mvn clean verify`) | 84 fichiers `target/surefire-reports/*.txt` | 472 | 472 | 0 | 0 | 0 |
| Failsafe (même `mvn clean verify`) | 34 fichiers `target/failsafe-reports/*.txt` | 56 | 56 | 0 | 0 | 0 |

Surefire exclut `**/*IT.java`. Après `clean`, aucun rapport d'intégration ne reste dans `surefire-reports`. `DocumentExtractionIT` est dans les 56 tests Failsafe.

Le journal du slice affiche un `ERROR` `Unhandled API error` / `IllegalStateException: boom`. C'est le test du code HTTP 500, pas un échec de suite.

### JaCoCo

Source : `backend/konselyavisa-api/target/site/jacoco/jacoco.csv`, produit par `mvn clean verify` (pas d'exécutable précédent réutilisé).

| Périmètre | Lignes | Branches |
|---|---:|---:|
| Métier (12 packages du contrôle JaCoCo) | 3 679 / 3 944 = **93,28 %** | 1 340 / 1 659 = **80,77 %** |

Objectifs : lignes métier ≥ 85 % (atteint), branches métier ≥ 80 % (atteint).

Rapports : `backend/konselyavisa-api/target/site/jacoco/index.html`, `jacoco.xml`, `jacoco.csv`.

Exclusions documentées : `KonselyaVisaApplication`, classes MapStruct `*MapperImpl`. `jacoco.haltOnFailure=true`. Le contrôle `jacoco:check@check` sur cet exécutable s'est terminé avec le code 0.

Services critiques (lignes / branches, même CSV) :

| Classe | Lignes | Branches |
|---|---:|---:|
| `CaseService` | 134 / 134 | 58 / 60 |
| `PaymentService` | 152 / 154 | 39 / 44 |
| `AppointmentService` | 118 / 122 | 40 / 44 |
| `PayDunyaIpnPayload` | 49 / 49 | 34 / 34 |
| `CinetPayHmac` | 31 / 33 | 14 / 14 |
| `DocumentFiles` | 19 / 21 | 16 / 16 |

Les deux lignes manquantes de `CinetPayHmac` et `DocumentFiles` sont les `catch` de `NoSuchAlgorithmException` / `Mac`, inatteignables sans remplacer le fournisseur cryptographique de la JVM.

Le slice `ApiAuthorizationSliceTest` couvre, sans base : 401 anonyme sur les dossiers, 403 rôle insuffisant (entreprise, superviseur), 200 citoyen / entreprise / superviseur, 400 validation, 404, 409, 500, et le fait que les routes publiques du filtre (`/api/v1/public/**`, webhook Stripe, santé Actuator) ne répondent pas 401 dans ce slice (404, contrôleurs absents du slice).

## 3. Frontend

### Vitest (2026-10-28, vers 22:48)

| Projet | Fichiers | Tests | Résultat |
|---|---:|---:|---|
| `frontend/citizen-portal` | 5 | 13 | 13 réussis |
| `frontend/admin-portal` | 3 | 7 | 7 réussis |
| `packages/design-tokens` | 1 | 3 | 3 réussis |

Le test `isConsularStaff` est nouveau. Les autres fichiers Vitest existaient déjà.

### Playwright

21 scénarios isolés, exécution du 2026-10-28 vers 23:16. Rapport HTML : `playwright-report/index.html`. Le passage s'est terminé par `21 passed`.

| Fichier | Scénarios exécutés |
|---|---|
| `tests/e2e/citizen/authentication.spec.ts` | Accueil, redirection OIDC, assistant sans compte |
| `tests/e2e/citizen/eligibility.spec.ts` | Passeport trop court, éligibilité refusée par l'API, mots de passe différents |
| `tests/e2e/citizen/case-management.spec.ts` | Suivi 404, liste vide puis erreur API, session expirée, déconnexion |
| `tests/e2e/admin/dashboard.spec.ts` | Anonyme, rôle citoyen refusé, file / filtre / pièce / correction, déconnexion |
| `tests/e2e/admin/security.spec.ts` | Menus agent vs administrateur métier |
| `tests/e2e/citizen/documents.spec.ts` | Dépôt d'une pièce |
| `tests/e2e/citizen/privacy.spec.ts` | Notice publique, export, demande d'anonymisation |
| `tests/e2e/citizen/company.spec.ts` | Liste entreprise |
| `tests/e2e/admin/slots.spec.ts` | Création d'un créneau |

Ce ne sont pas des tests bout en bout. `page.route` simule la découverte OIDC et les réponses API. Le fumigène réel est `tests/e2e/live/stack.spec.ts`, lancé seulement par `npx playwright test -c playwright.live.config.ts` ou par le job CI `e2e-live` (`workflow_dispatch`). Il n'a pas été exécuté.

Parcours encore hors de la suite isolée : formulaire de dossier complet, édition du catalogue, paiement. L'accès inter-organisation réel n'est pas rejoué.

## 4. Corrections

| Défaut | Nature | Correctif |
|---|---|---|
| `N8nOutboxWebhookClient` a deux constructeurs, aucun `@Autowired` | Câblage Spring : les IT `@SpringBootTest` ne démarraient pas (`No default constructor`) | `@Autowired` sur le constructeur public à deux arguments. Le constructeur package-private de test est inchangé. Aucune règle métier modifiée |
| Premier passage Playwright (3 échecs) | Tests : libellé ambigu, menu de déconnexion, nom accessible du mot de passe | Locators resserrés. Les 16 scénarios ont ensuite réussi |
| Assertion d'erreur de liste citoyen | Le libellé réel de `error.http` est « L'API a renvoyé une erreur. » | Assertion alignée sur la traduction, pas sur un texte inventé |

Aucun test n'a été supprimé, ignoré ou affaibli pour obtenir un résultat vert. Aucune migration, aucune API publique et aucun service métier n'ont été réécrits. Aucun commit ni push.

Fichiers de production modifiés : `N8nOutboxWebhookClient.java` uniquement.

Autres fichiers modifiés : `backend/konselyavisa-api/pom.xml` (JaCoCo, exclusion Surefire `*IT`, seuil bloquant), `.github/workflows/ci.yml` (artefacts, job `frontend`, job manuel `e2e-live`), `.gitignore`.

### Risques résiduels

- Les adaptateurs HTTP Stripe, CinetPay et PayDunya restent sous la moyenne de branches. Ils restent dans le rapport JaCoCo.
- Les 21 scénarios Playwright ne prouvent pas l'intégration Keycloak + API + PostgreSQL + MinIO. Le job `e2e-live` est prêt et n'a pas été lancé.
- Le catalogue et le paiement n'ont pas de slice HTTP dédié. L'isolation SQL reste couverte par `OrganizationRlsIT`.

## 5. Commandes

PowerShell, depuis la racine du dépôt. JDK 21 obligatoire pour Maven.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

cd backend\konselyavisa-api
.\mvnw.cmd test
.\mvnw.cmd verify
```

Sous PowerShell, un `-Dtest=A,B` non quoté est découpé sur la virgule. Écrire `.\mvnw.cmd "-Dtest=ClasseA,ClasseB" test`.

Le chiffre de branches de ce rapport vient de `.\mvnw.cmd clean verify`, puis de `target\site\jacoco\jacoco.csv`.

```powershell
cd frontend\citizen-portal
npm test
cd ..\admin-portal
npm test
cd ..\..\packages\design-tokens
npm test
cd ..\..\tests
npm install
npx playwright install chromium
npx playwright test
```

`npx playwright test` démarre les deux Vite (ports 5175 et 5176) via `tests/playwright.config.ts` et ne lance pas le fumigène réel. Celui-ci se lance avec `npx playwright test -c playwright.live.config.ts` pendant que l'API écoute sur le port 18083. Docker est requis pour `mvn verify` (Testcontainers PostgreSQL, et MinIO pour l'extraction). Les tests unitaires n'utilisent ni Docker ni PostgreSQL.

## 6. Suite

Réalisé sur un `mvn clean verify` : exclusion Surefire des `*IT`, branches métier à 80,77 %, seuil JaCoCo bloquant, slice documents / paiement / rendez-vous, parcours isolés dépôt, vie privée, entreprise et créneaux, job `e2e-live` séparé.

Encore ouvert : lancer le job `e2e-live` sur une pile démarrée, couvrir le formulaire de dossier complet et le paiement dans Playwright, et les branches restantes des passerelles live sans les retirer de JaCoCo.

## 7. CI

`.github/workflows/ci.yml` conserve les jobs `verify` (`./mvnw verify`) et `supply-chain`. Ajouts : artefact `backend-test-reports`, job `frontend` (Vitest puis Playwright isolé), job `e2e-live` uniquement sur `workflow_dispatch`. Pas de GitLab CI ni de Jenkins. Le workflow GitHub n'a pas été lancé depuis ici.
