# 03 — Audit frontend

Date : 2026-10-28. Deux applications Vite, React 18, TypeScript. Aucune navigation `react-router` : l'état local choisit l'écran. Les URLs réelles sont `/` et `/callback` (retour OIDC). Cette mission n'a pas ouvert les portails dans un navigateur. Le classement « opérationnel » s'appuie sur le code et sur les 21 scénarios Playwright isolés du 2026-10-28 (API simulée par `page.route`).

## 1. Applications

| App | Dossier | Port documenté | Dépendances alignées PRD | Écarts de stack |
|---|---|---|---|---|
| Portail citoyen | `frontend/citizen-portal` | 5175 | React 18, TypeScript, Vite, TanStack Query 5, react-i18next, Tailwind, oidc-client-ts, primitives Radix | Zustand, React Hook Form et Zod absents. Formulaires en `useState`. |
| Portail agent | `frontend/admin-portal` (`package.json` name `agent-portal`) | 5176 | Idem | Idem. Moins de primitives Radix. |

Les deux portails partagent `@konselyavisa/design-tokens`. Ils ne sont pas des services Docker. Nginx (`infrastructure/nginx/nginx.conf`) proxifie `/api/`, la santé et OpenAPI. Il ne sert pas le HTML des portails.

Session : `oidc-client-ts`, `automaticSilentRenew: true`, jetons dans `sessionStorage`. Déconnexion : `signoutRedirect`. Garde citoyen : absence d'utilisateur → site public ; staff consulaire → `StaffPortalGate` (renvoi vers le portail agent). Garde admin : rôles `AGENT`, `SUPERVISOR`, `BUSINESS_ADMIN`, `PLATFORM_ADMIN`.

i18n : français, portugais, anglais, fichiers séparés par domaine (`locales/*/wizard.json`, `journey.json`, `case.json`, etc.).

## 2. Portail citoyen — écrans

| Écran | Fichier | API | Formulaires / états | Classement |
|---|---|---|---|---|
| Accueil public | `screens/public/PublicHome.tsx` | `GET /api/v1/public/site` | Chargement et erreur via React Query | Code branché. Playwright : accueil. Pile réelle non vérifiée. |
| Connexion | `LoginScreen.tsx` | Redirection OIDC | Erreur de bootstrap | Playwright : redirection simulée. Login Keycloak réel non joué. |
| Assistant d'éligibilité | `EligibilityAssistant.tsx` | Procédures, notice, ticket invité ou `POST /cases` | Validation manuelle (passeport court testé) | Partiel. Playwright : refus d'éligibilité et mots de passe. Parcours complet non couvert. |
| Inscription invité | `GuestAccountRegister.tsx` | `POST /public/account-registrations` | Champs contrôlés à la main | Code branché. Non couvert par le fumigène live. |
| Suivi public | `PublicTrackCase.tsx` | `POST /public/case-status` | Référence + secret | Playwright : suivi 404 simulé. |
| Pages légales | `PublicLegalPage.tsx` | Notice de confidentialité | — | Playwright privacy : notice. |
| Liste dossiers | `CaseList.tsx` | `GET /cases` | Vide, erreur, session expirée | Playwright `case-management`. |
| Liste entreprise | `CompanyCaseList.tsx` | `GET /cases`, `GET /company/cases/summary` | Compteurs | Playwright `company.spec.ts` simulé. |
| Nouveau dossier connecté | `NewCase.tsx` | `POST /cases` | Consentement | Hors suite Playwright « formulaire complet ». |
| Rattachement ticket | `GuestTicketAttach.tsx` | `POST /cases` | — | Code branché. |
| Parcours dossier | `CaseJourney.tsx` + `journey/*` | Dossier, procédure, documents, commandes, créneaux | Upload, paiement MOCK, sync, rendez-vous | Partiel. Playwright documents : dépôt simulé. Paiement hors suite. |
| Compte vie privée | `PrivacyAccount.tsx` | Export, effacement, notice | États mutation | Playwright `privacy.spec.ts` simulé. |
| Porte staff | `StaffPortalGate.tsx` | — | — | Test unitaire `isConsularStaff`. |

Étapes du parcours demandé par la mission, telles que le code les enchaîne :

1. Recherche / accueil : présent (`PublicHome`, catalogue public).
2. Éligibilité : présente, JSONLogic côté API.
3. Dossier : création après éligibilité et consentement.
4. Formulaire : faits saisis dans l'assistant ou `NewCase`, pas de formulaire dynamique généré depuis un schéma de procédure.
5. Documents : `JourneyDocuments`.
6. Paiement : `JourneyPayment`, simulation MOCK, sync Stripe.
7. Soumission : il n'y a pas d'action « soumettre » distincte de la création. Le dossier naît `CREATED`.
8. Suivi : liste, détail, timeline, suivi public.
9. Clôture : absente. Le statut `COMPLETED` n'est jamais posé par l'API.

`CaseNextActionResolver` (backend) pilote l'étape affichée : pièces, correction, paiement, rendez-vous. Ce n'est pas une maquette statique : les mutations appellent l'API.

## 3. Portail agent — écrans

| Écran | Fichier | API | Classement |
|---|---|---|---|
| File | `CaseQueue.tsx` | `GET /agent/cases`, `GET /supervisor/summary` | Filtres statut et retard. Playwright : file, filtre, pièce, correction (API simulée). |
| Détail | `CaseDetail.tsx` | Dossier, documents, commandes, alertes hash, refus, rendez-vous | Acceptation, correction, extraction, confirmation manuelle. Pas de rejet. Pas d'affectation. |
| Historique | `CaseHistory.tsx` | `GET /cases/{id}/history` | Branché sur l'outbox. |
| Correction | `CorrectionPanel.tsx` | `request-correction` | Motifs dans `data/correctionReasons.ts`. |
| Hash | `DocumentHashAlertPanel.tsx` | `GET hash-alerts` | Branché. |
| Refus d'upload | `DocumentUploadRefusalPanel.tsx` | `GET upload-refusals` | Branché. |
| Extraction | `DocumentExtractionPanel.tsx` | `POST extraction` | Saisie agent (stub). |
| Créneaux | `Slots.tsx` | CRUD léger des slots | Playwright `slots.spec.ts` simulé. |
| Catalogue | `CatalogList.tsx`, `CatalogEditor.tsx` | Procédures, versions, publish | Édition branchée. Pas de bouton archiver. Pas de test Playwright. |
| Réglages org | `OrganizationSettings.tsx` | settings, logo, favicon | Branché. Pas d'écran DPA. |
| Alerte superviseur | `SupervisorAlert.tsx` | Données du summary | Libellé de dépassement de délai, pas une procédure de violation RGPD. |

Menus : agent voit file et créneaux ; admin métier voit aussi catalogue et réglages (`admin/security.spec.ts`, API simulée).

## 4. Données factices et trous d'UI

- Playwright isole tout le backend (`tests/fixtures/api.ts`). Ce n'est pas le comportement de production.
- `completeMockPayment` est une action réelle vers le webhook MOCK, pas un bouton mort.
- Aucun `TODO` / `FIXME` produit n'a été retenu comme fonctionnalité simulée dans les écrans lus. Les occurrences `mock` du dépôt sont des tests.
- Pays : l'API existe, l'UI admin ne gère pas les pays.
- Organisations : pas d'écran de création plateforme.
- DPA : pas d'écran d'acceptation.
- Rejet de pièce : endpoint sans bouton.
- Archivage de version : endpoint sans bouton.
- Zustand, React Hook Form, Zod : absents des `package.json`. La validation visible est manuelle (longueur de passeport, mots de passe) plus les `message_key` renvoyés par l'API.

## 5. Classement synthétique

| Surface | Classement |
|---|---|
| Accueil, login OIDC (code), listes, dépôt, correction, créneaux, vie privée, entreprise | Code branché API. Vérifié seulement avec API simulée. |
| Paiement citoyen, édition catalogue, formulaire de dossier complet | Code présent. Hors suite Playwright isolée. Non vérifié en pile. |
| Clôture de dossier, affectation, rejet, archivage, admin pays, acceptation DPA | Absent côté UI. |
| Maquette à données figées | Aucun écran majeur identifié comme purement statique. Les textes viennent des fichiers de langue ; les dossiers viennent de l'API. |
