# 07 — Feuille de route de finalisation

Date : 2026-10-28. Les sprints font une semaine. L'équipe de référence est un développeur backend et un développeur frontend, avec le dépôt déjà construit. Les charges reprennent `06_MISSING_FEATURES.md`. Le chemin critique est un dossier visa qui se termine, puis la preuve sur la vraie pile.

Hypothèse : le PRD v1, une fois retrouvé, ne rajoute pas un second parcours obligatoire. Si les « 3 workflows » de la v1 diffèrent du visa, de la légalisation et de l'apostille déjà seedés, insérer un sprint de cadrage avant le sprint 2.

## Chemin critique vers un MVP démontrable

1. Obtenir ou écarter officiellement la v1 (sinon le « terminé » reste indémontrable).
2. Clôturer un dossier dans Spring Boot (`COMPLETED` + outbox).
3. Montrer le même dossier dans les deux portails, avec MinIO, Keycloak et le paiement MOCK réservé au profil local.
4. Montrer un e-mail Mailpit issu de l'outbox, sans décision métier dans n8n.
5. Automatiser ce fil dans `e2e-live`.

Le dépôt permet déjà de démontrer création, pièces, paiement simulé, rendez-vous et correction. Il ne permet pas de démontrer la fin du dossier.

## Sprint 0 — Cadrage (2–3 jours)

- Objectif : figer le référentiel.
- Fonctionnalités : KON-SRC-001.
- Modules : aucun code.
- Dépendances : le fichier v1, ou une décision écrite « la v2 et cette matrice font foi ».
- Livrable : addendum de matrice, ou confirmation que les 61 lignes scorées suffisent.
- Tests : aucun.
- Critère : plus aucune exigence « à inventer ».
- Charge : 1–2 j.

## Sprint 1 — Fin de dossier

- Objectif : le parcours visa a une dernière étape.
- Fonctionnalités : KON-FUNC-003, KON-FUNC-042, amorces de KON-FUNC-043 (rejet et `REPLACED`, parce que la clôture en dépend).
- Modules : `dossier`, `document`, `outbox`, `admin-portal`, `citizen-portal`.
- Dépendances : sprint 0.
- Livrables : endpoint de clôture et d'annulation, garde métier, événement `CASE_COMPLETED`, boutons, pièce remplacée, bouton rejeter.
- Tests : IT de transition (succès, double clôture, pièce rejetée, dossier d'une autre organisation), scénario Playwright isolé de clôture.
- Critère : un IT crée le dossier visa, paie, accepte les pièces, clôture, et lit l'événement outbox. Le citoyen voit le statut terminé.
- Charge : environ 8–10 j cumulés.

## Sprint 2 — Preuve sur la pile

- Objectif : la démo ne repose plus seulement sur Testcontainers et sur `page.route`.
- Fonctionnalités : KON-FUNC-033, KON-FUNC-046, KON-FUNC-037, KON-FUNC-032 (au moins un chemin documenté ; les images frontend peuvent glisser au sprint 4).
- Modules : Compose, Playwright `e2e-live`, n8n, Mailpit.
- Dépendances : sprint 1.
- Livrables : script de démo, scénario live jusqu'à `COMPLETED`, capture Mailpit des trois événements principaux.
- Tests : `npx playwright test -c playwright.live.config.ts` vert sur le parcours visa. Ne pas modifier les données d'un environnement partagé : base de démo jetable.
- Critère : le job `e2e-live` a un résultat archivé.
- Charge : environ 8 j cumulés.

## Sprint 3 — Exploitabilité

- Objectif : sortir de la démo développeur.
- Fonctionnalités : MOCK désactivable (KON-FUNC-010), SSE MinIO (KON-FUNC-036), DPA dans l'admin (KON-FUNC-008), décision sur la règle vide (KON-FUNC-024).
- Modules : `payment`, `document.storage`, `admin-portal`.
- Dépendances : sprint 2 pour ne pas casser la démo MOCK.
- Livrables : flag, SSE, écran DPA, test de publication sans règle.
- Tests : IT webhook MOCK refusé quand le flag est off ; IT objet chiffré ; Playwright simulé de l'écran DPA.
- Critère : un profil `docker` de démo garde MOCK ; un profil « exploitable » le coupe et conserve le paiement manuel agent.
- Charge : environ 8–10 j cumulés.

## Sprint 4 — Conformité et livraison

- Objectif : combler les écarts v2 encore ouverts pour une première exploitation.
- Fonctionnalités : `audit_logs` (KON-FUNC-049), rétention par type (KON-FUNC-007), portails dans Compose (KON-FUNC-032), runbook 72 h (KON-FUNC-009).
- Modules : nouveau package `audit` (fichier nouveau, pas une réécriture), `privacy`, `infrastructure`.
- Dépendances : tenancy et DPA déjà livrés.
- Livrables : migration Flyway additive, RLS, écran ou export d'audit pour le superviseur, deux durées de rétention, nginx servant les UI.
- Tests : IT d'audit sans secret en clair ; IT de purge à deux horloges ; Compose health.
- Critère : les lignes KON-FUNC-007, 009, 032 et 049 passent à un statut vérifié supérieur à 80 % dans une mise à jour de la matrice.
- Charge : environ 12–15 j cumulés.

## Sprint 5 — Filet de non-régression

- Objectif : rapprocher « vérifié » de « implémenté ».
- Fonctionnalités : Testcontainers Keycloak (KON-FUNC-029), trace OTel jusqu'au webhook (KON-FUNC-028), bascule de langue (KON-FUNC-016), exécution Trivy (KON-FUNC-031).
- Modules : tests, collector, CI.
- Dépendances : sprints 2 et 3.
- Livrables : IT realm, dashboard Tempo d'une création de dossier, scénario `pt`, artefact SBOM.
- Tests : ceux du sprint.
- Critère : le pourcentage vérifié MVP, recalculé avec la même pondération, gagne les points aujourd'hui à 15–50 % sur ces lignes.
- Charge : environ 8–10 j cumulés.

## Ensuite — V2

Ordre conseillé, après le MVP démontré et exploitable :

1. KON-FUNC-055 et 057 : finir la preuve anti-fraude déjà codée (1 sprint court).
2. KON-FUNC-059 : DNS/TLS white-label sur l'exemple nginx existant.
3. KON-FUNC-053 : conteneur OCR sur un réseau sans Postgres, en réutilisant `StubDocumentExtractionService` comme contrat.
4. KON-FUNC-056 puis KON-FUNC-054 : MRZ et Ollama, seulement derrière ce contrat.
5. KON-FUNC-052 : Statemachine seulement si les transitions dépassent la dizaine.
6. KON-FUNC-060 : expédition si un consulat l'exige.
7. KON-FUNC-058 : Loki.
8. V3 : aucun sprint tant que le §48 n'est pas dans le dépôt.

## Hors de cette feuille

Ne pas ajouter Drools. Ne pas déplacer une règle d'éligibilité, un tarif ou une transition dans n8n. Ne pas donner à un futur conteneur OCR une chaîne de connexion PostgreSQL.

## Charge totale indicative

Sprints 1 à 5 : environ 45–55 jours-personne, soit **6 à 10 semaines calendaires à deux développeurs**, plus le delta v1. Le MVP démontrable (sprints 0 à 2) est plus court : **environ 3 à 4 semaines calendaires** si la v1 ne rouvre pas le périmètre.
