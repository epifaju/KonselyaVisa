# 08 — Synthèse décisionnelle

Date : 2026-10-28. Audit en lecture seule. Référence : `docs/PRD_KonselyaVisa_v2_Optimise_CursorAI.md`. La v1 citée par ce document est absente du dépôt.

**Statut de l'audit : PARTIAL.**

Le texte v2 a été décomposé (61 exigences scorées). Le corpus fonctionnel v1 (personas, maquettes, 3 workflows, critères de succès, vision V3) n'a pas pu être tracé. Aucune exigence n'a été inventée pour combler ce trou.

## Pourcentages

Méthode, détaillée dans `01_PRD_TRACEABILITY_MATRIX.md` :

- DONE = 100, PARTIAL = pourcentage estimé de la matrice, NOT_STARTED = 0.
- Poids : critique 5, haute 3, moyenne 2, basse 1.
- Estimé = somme(poids × implémentation) / somme(poids).
- Vérifié = somme(poids × preuve d'exécution) / somme(poids).
- La preuve d'exécution est le rapport `docs/testing/TEST_EXECUTION_REPORT.md` du 2026-10-28 (472 + 56 tests backend verts, 21 Playwright isolés verts) plus la lecture du code. Cette mission n'a pas relancé les tests ni démarré Compose. Le fumigène live et GitHub Actions n'ont pas de résultat.
- Drools (interdit) et le corpus v1 absent sont hors dénominateur.
- V3 : non calculable.

Ce ne sont pas des mesures exactes. Deux lecteurs peuvent déplacer un PARTIAL de 10 points sans changer le constat.

| Phase | Exigences scorées | Poids | Avancement estimé | Avancement vérifié |
|---|---:|---:|---:|---:|
| MVP | 51 | 188 | 80 % | 60 % |
| V2 et ultérieur immédiat | 10 | 27 | 37 % | 25 % |
| V3 | 0 (texte §48 absent) | — | non calculable | non calculable |
| Global MVP+V2 | 61 | 215 | 75 % | 56 % |

Calcul contrôlé : MVP 80,4 % et 59,8 % ; V2 37,4 % et 25,2 % ; global 75,0 % et 55,5 %, arrondis à l'entier le plus proche. La couverture JaCoCo (93,28 % de lignes sur 12 packages) mesure autre chose et ne doit pas être lue comme l'avancement produit.

## Modules terminés

Au sens « exigence v2 couverte et testée sur la partie livrée » :

- Socle Spring : `ApiResponse` / `PageResponse`, `BaseEntity`, Flyway, `ddl-auto=validate`, OpenAPI de contrat.
- Multi-tenant : filtre Hibernate, `set_config`, RLS forcée, test `OrganizationRlsIT`.
- Catalogue multilingue et version figée à la création du dossier.
- Éligibilité JSONLogic.
- Outbox (écriture, retry, FAILED) pour les événements émis aujourd'hui.
- Paiement : interface provider, idempotence, MOCK / manuel / adaptateurs Stripe, CinetPay, PayDunya (ces trois derniers prouvés contre des serveurs factices).
- Consentement, export, demande d'effacement, registre des traitements.
- Hash SHA-256, stub d'extraction, chiffrement pgcrypto des champs extraits.
- Principe tenu dans le code : n8n envoie des e-mails, Spring décide.

## Modules partiels

- Cycle de dossier : création, lecture, liste, correction, historique. Pas de clôture ni d'annulation effective.
- Documents : dépôt, acceptation, correction. Rejet sans écran. Remplacement sans statut `REPLACED`. Download proxifié, sans SSE ni URL présignée.
- Portails React branchés sur l'API, sans routeur d'URL, sans Zustand ni React Hook Form ni Zod. Preuve Playwright majoritairement simulée.
- Back-office : file, filtres, détail, catalogue, créneaux, charte. Pas d'admin des pays, pas d'acceptation DPA, pas d'affectation.
- Redis utile (cache, quota, holds), pas comme magasin de session.
- Observabilité : Compose OTel, Prometheus, Grafana, Tempo. Loki absent. Traces jusqu'à n8n non observées.
- i18n : fichiers fr/pt/en présents, bascule non prouvée.
- Conteneurs : la pile backend est décrite. Les deux UI restent des process Vite.
- Anti-fraude V2 déjà commencée (hash croisé, refus d'upload). MRZ et OCR absents.
- White-label : en avance sur l'annonce « V2 », DNS/TLS encore exemplaire.

## Modules absents

- Émission de `CASE_COMPLETED`.
- Table et module `audit_logs`.
- Packages `workflow`, `shipment`, `integration`, `notification` (la notification est n8n).
- Spring Statemachine, Ollama, microservice OCR, Loki, Swarm/Kubernetes.
- Texte V3.

## Blocages

1. Impossible de déclarer le MVP « conforme au produit » tant que la v1 n'est pas dans le dépôt ou explicitement écartée.
2. Un dossier ne peut pas se terminer. L'événement prévu pour la facture finale et la notification de fin n'existe que comme constante.
3. La démo Compose ne lance pas les portails.
4. Le paiement de démo confirme une commande en appelant un webhook public MOCK depuis le navigateur. À couper hors profil local avant toute exposition.
5. La chaîne n8n / Mailpit / Keycloak réel n'a pas été observée pendant cet audit. Les tests d'intégration ne remplacent pas cette observation.

## Risques

- Confondre 93 % JaCoCo ou 21 Playwright verts avec un parcours réel.
- Une règle d'éligibilité vide rend la procédure éligible. Une publication accidentelle ouvre le dépôt à tous les faits.
- Le publisher outbox lit toutes les organisations en mode plateforme. Tout bug de payload peut mélanger des données dans un e-mail.
- Secrets de développement dans le Compose : réservés au poste local.
- Six procédures sont seedées alors que la v1 parle de 3 workflows. Le périmètre démontré est peut-être déjà plus large, ou les « workflows » v1 sont autre chose que ces procédures.

## Prochaines actions

1. Déposer la v1 à un emplacement stable, ou décider par écrit que la matrice v2 fait foi.
2. Implémenter la clôture et `CASE_COMPLETED` (sprint 1 de `07_MVP_COMPLETION_ROADMAP.md`).
3. Jouer le visa France → Guinée-Bissau sur la pile, jusqu'à Mailpit.
4. Couper MOCK hors démo et chiffrer les objets MinIO.
5. Ajouter `audit_logs` et la durée de rétention par type de pièce.
6. Ne pas ouvrir l'OCR, Ollama ou Statemachine avant ce socle.

## Fourchette de finalisation du MVP

Écarts connus de la v2 (P0 et P1) : environ **45 à 55 jours-personne**, soit **6 à 10 semaines calendaires à deux développeurs**. Un MVP démontrable (clôture + pile + e-mail) est plus court : **3 à 4 semaines calendaires**. Si la v1 impose des parcours absents du code, la fourchette s'allonge d'un sprint de cadrage plus le delta, non chiffrable ici.

## Livrables de cet audit

- `docs/audit/01_PRD_TRACEABILITY_MATRIX.md`
- `docs/audit/02_BACKEND_AUDIT.md`
- `docs/audit/03_FRONTEND_AUDIT.md`
- `docs/audit/04_DATABASE_INFRA_AUDIT.md`
- `docs/audit/05_TEST_COVERAGE_AUDIT.md`
- `docs/audit/06_MISSING_FEATURES.md`
- `docs/audit/07_MVP_COMPLETION_ROADMAP.md`
- `docs/audit/08_EXECUTIVE_SUMMARY.md`

Aucun code applicatif, aucune migration et aucun fichier Compose n'ont été modifiés.
