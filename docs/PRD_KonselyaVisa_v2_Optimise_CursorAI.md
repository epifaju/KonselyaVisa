# PRD v2 — KonselyaVisa / Consular Services Platform
## Version optimisée pour développement avec Cursor AI

**Version :** 2.0 (optimisation technique de la v1.0)
**Statut :** Prêt pour découpage en phases Cursor AI
**Architecture cible :** React + Spring Boot + PostgreSQL + Keycloak + MinIO + n8n + Redis + Docker, IA locale (Ollama/OCR) à partir de la V2
**Déploiement initial :** Docker Compose / self-hosted → SaaS multi-tenant

---

## 0. Résumé des changements par rapport à la v1.0

La v1.0 est déjà solide : vision claire, personas bien posés, moteur de règles découplé du code, séparation nette Spring Boot (source de vérité métier) / n8n (orchestration), sécurité et audit pensés dès le départ. Ce document ne la remplace pas, il l'**enrichit** sur les points qui, en pratique, sont ceux qui font dérailler ce type de projet une fois en production :

| # | Sujet | Ajouté / modifié | Pourquoi |
|---|---|---|---|
| 1 | **Redis** | Nouveau service | Cache catalogue/règles, rate-limiting, verrous, files légères, sessions |
| 2 | **Pattern Outbox** | Nouveau | Spring Boot → n8n actuellement en simple webhook HTTP : un événement métier peut se perdre si n8n est down. L'outbox garantit qu'aucun événement n'est perdu |
| 3 | **RGPD / conformité** | Nouvelle section | Le PRD v1.0 traite la sécurité technique mais pas les obligations légales (données de passeport = données sensibles) |
| 4 | **Paiement Afrique** | Étendu | Stripe seul est insuffisant pour la diaspora / les consulats africains (Mobile Money, CinetPay, PayDunya) |
| 5 | **i18n** | Nouvelle section | Plateforme multi-pays = multi-langue dès le MVP (pas seulement le français) |
| 6 | **Multi-tenant renforcé** | Précisé | RLS PostgreSQL + Hibernate filter, pas seulement un `organization_id` contrôlé en Java |
| 7 | **Observabilité** | Avancée en Phase 1 | OpenTelemetry dès le MVP, pas seulement "évolution recommandée" en fin de vie |
| 8 | **Tests** | Nouveau | Testcontainers, jeux de données de règles, contrats d'API |
| 9 | **CI/CD & sécurité supply-chain** | Nouveau | GitHub Actions, scan de vulnérabilités (Trivy), SBOM |
| 10 | **Anti-fraude documentaire** | Nouveau (V2) | Hash, détection de réutilisation de documents entre dossiers |
| 11 | **Feature flags** | Nouveau | Cohérent avec le principe "configuration plutôt que code" déjà posé en v1.0 |
| 12 | **Convention de code Cursor AI** | Alignée sur tes projets existants | `ApiResponse<T>`/`PageResponse<T>`, `BaseEntity`, MapStruct, Lombok, Flyway, JWT refresh rotation — mêmes conventions que ConsulOS / DocuForge AI pour rester cohérent entre projets |

Le reste (personas, parcours citoyen, écrans, principes produit) est conservé tel quel : c'est la partie la plus solide de la v1.0 et il n'y a pas de raison de la retoucher.

---

## 1. Stack technique consolidée

```
Frontend       React 18 + TypeScript + Vite
               shadcn/ui + Tailwind CSS
               TanStack Query v5 (server state)
               Zustand (client state)
               React Hook Form + Zod (formulaires + validation)
               react-i18next (multi-langue dès le MVP)

Backend        Java 21 + Spring Boot 3
               Spring Web, Spring Security (Resource Server OAuth2/JWT),
               Spring Data JPA, Flyway, Validation, Actuator, OpenAPI (springdoc)
               MapStruct + Lombok
               Spring Statemachine (optionnel V2, cf. §7) pour les workflows complexes

Base de données PostgreSQL 16 (+ extension pgcrypto pour le chiffrement au niveau colonne)

Cache/Files    Redis 7 (cache catalogue, rate limiting, verrous optimistes, files légères)

Identité       Keycloak (OIDC), rôles + attributs personnalisés (organization_id)

Stockage       MinIO (S3-compatible), URLs présignées, chiffrement SSE

Orchestration  n8n (automatisations, notifications, intégrations tierces)

IA / OCR       V2 : Ollama (Qwen ou Mistral) + PaddleOCR/Tesseract, service isolé (voir §8)

Observabilité  OpenTelemetry (traces) dès le MVP → Prometheus + Grafana + Loki

Infra          Docker Compose (MVP) → Docker Swarm ou Kubernetes si multi-organisation à volume

CI/CD          GitHub Actions : build, tests, Trivy (scan images), SBOM (CycloneDX)
```

**Conventions de code à respecter (cohérence avec ConsulOS et DocuForge AI) :**
- Toute réponse API passe par `ApiResponse<T>` (succès) et `PageResponse<T>` (listes paginées)
- Toute entité JPA hérite de `BaseEntity` (id UUID, createdAt, updatedAt, createdBy, updatedBy)
- Authentification par JWT avec rotation du refresh token
- Migrations Flyway versionnées, jamais de `ddl-auto: update` en dehors du dev
- Toute nouvelle fonctionnalité va dans de nouveaux fichiers (règle de non-régression) plutôt que dans des fichiers existants modifiés en profondeur

---

## 2. Architecture applicative Spring Boot (monolithe modulaire)

Structure conservée de la v1.0, avec deux modules ajoutés (`tenancy` et `outbox`) :

```
com.konselyavisa
├── identity
├── tenancy            # NOUVEAU — résolution organization_id, RLS, filtres Hibernate
├── organization
├── catalog
├── eligibility
├── case
├── applicant
├── document
├── workflow
├── pricing
├── order
├── payment            # + adaptateurs multi-providers (Stripe, CinetPay, PayDunya, Mobile Money)
├── shipment
├── appointment
├── notification
├── audit
├── outbox             # NOUVEAU — événements métier fiables vers n8n
└── integration
```

### 2.1 Module `tenancy`

Chaque table métier porte un `organization_id`. Deux niveaux de défense :
1. **Applicatif** : un `TenantContext` (ThreadLocal alimenté depuis le JWT Keycloak) injecté dans un filtre Hibernate `@Filter` appliqué à toutes les requêtes.
2. **Base de données** : PostgreSQL **Row-Level Security** sur les tables sensibles (`cases`, `documents`, `orders`, `payments`), en filet de sécurité si un bug applicatif oublie le filtre.

```sql
ALTER TABLE cases ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON cases
  USING (organization_id = current_setting('app.current_org')::uuid);
```
Spring Boot exécute `SET LOCAL app.current_org = ?` en ouverture de transaction (via un `TransactionSynchronization` ou un intercepteur Hibernate).

### 2.2 Module `outbox` (pattern Transactional Outbox)

Problème du PRD v1.0 : `Spring Boot → événements → n8n` est décrit comme un webhook direct. Si n8n est indisponible au moment de l'événement, l'action (email de bienvenue, génération de facture, checklist) est perdue silencieusement.

Solution : chaque événement métier (`CASE_CREATED`, `DOCUMENT_UPLOADED`, `PAYMENT_COMPLETED`, `CORRECTION_REQUESTED`, `CASE_COMPLETED`) est **écrit dans la même transaction** que le changement d'état métier, dans une table `outbox_events`. Un publisher asynchrone (scheduler Spring ou petit poller) lit cette table et déclenche le webhook n8n, avec retry et backoff.

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50),
    aggregate_id UUID,
    event_type VARCHAR(50),
    payload JSONB,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING / SENT / FAILED
    attempts INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP
);
```

Cela transforme "n8n automatise autour de l'état" (principe déjà posé en v1.0) en une garantie réelle : **aucun événement métier n'est perdu**, ce qui compte pour la facturation et les notifications réglementaires.

---

## 3. Modèle PostgreSQL — ajouts

Toutes les tables de la v1.0 sont conservées. Ajouts :

```
outbox_events
organization_settings        -- JSONB : branding, domaine, devise, langues actives (prépare le white-label V2 sans migration)
feature_flags                -- clé/valeur par organisation ou globale
document_access_logs         -- qui a ouvert quel document, quand (au-delà de audit_logs générique)
```

`organization_settings` (JSONB dès le MVP, même vide) évite une migration lourde quand le white-label arrivera en V2 :

```sql
CREATE TABLE organization_settings (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id),
    settings JSONB NOT NULL DEFAULT '{}'::jsonb
    -- ex: {"brandColor": "#0B5D3B", "domain": "visa.acme.com",
    --      "activeLanguages": ["fr","pt","en"], "defaultCurrency": "EUR"}
);
```

**Champs sensibles** (`documents.original_filename` n'est pas concerné, mais un futur champ `passport_number` extrait par OCR l'est) : chiffrement au niveau colonne avec `pgcrypto`, jamais en clair dans les logs applicatifs ni dans `audit_logs.details_json`.

---

## 4. Conformité RGPD (nouvelle section, absente de la v1.0)

Le PRD v1.0 couvre bien la sécurité technique (§39 protection documentaire, §40 audit) mais pas les obligations légales. Or la plateforme traite des données d'identité et de voyage — catégories sensibles au sens RGPD dès lors qu'un consulat européen ou un ressortissant européen est impliqué.

À intégrer dès le MVP :
- **Registre des traitements** : documenté dans `/docs`, pas seulement dans le code
- **Base légale par organisation** : consentement explicite au dépôt du dossier (case à cocher horodatée, stockée)
- **Droit d'accès / portabilité / effacement** : endpoints `GET /me/data-export` et `POST /me/data-deletion-request` (l'effacement réel est soumis à la durée légale de conservation des dossiers consulaires — donc anonymisation différée plutôt que suppression immédiate dans de nombreux cas)
- **Politique de rétention** : durée de conservation par type de document/procédure, purge automatique programmée (déjà listée en §39 comme mesure technique, à formaliser en politique produit avec durées explicites)
- **DPA (Data Processing Agreement)** avec chaque organisation cliente en mode SaaS
- **Notification de violation** : procédure et délai (72h) documentés, même si le MVP n'a pas encore d'incident à gérer
- **Minimisation** : ne pas faire remonter au frontend plus de champs que nécessaire pour l'écran affiché

---

## 5. Paiement — adaptation au contexte réel (Afrique / diaspora)

Le PRD v1.0 mentionne Stripe uniquement. Les personas incluent explicitement Carlos (diaspora) et des consulats africains — Stripe seul est un point de friction réel (couverture géographique, cartes bancaires peu répandues dans certains pays).

Architecture recommandée : interface `PaymentProvider` (même logique que l'abstraction `AIProvider` déjà utilisée sur DocuForge AI), avec plusieurs implémentations activables par organisation :

```java
public interface PaymentProvider {
    CheckoutSession createCheckout(Order order);
    PaymentStatus verify(String providerReference);
    void handleWebhook(WebhookPayload payload);
}
```

Providers à prévoir : **Stripe** (Europe/international), **CinetPay** ou **PayDunya** (mobile money Afrique de l'Ouest — pertinent pour un cas d'usage Guinée-Bissau), **virement/espèce au guichet consulaire** (statut `PENDING_MANUAL` avec validation agent — cas fréquent pour les consulats).

Chaque webhook de paiement doit être traité de façon **idempotente** (clé d'idempotence stockée), point non mentionné en v1.0 et pourtant critique : un retry de webhook Stripe/CinetPay ne doit jamais déclencher deux fois la génération de facture.

---

## 6. Internationalisation (i18n) — nouvelle section

Une plateforme "multi-pays" doit gérer la langue dès le MVP, pas en V2 :
- **Frontend** : `react-i18next`, fichiers de traduction par domaine fonctionnel (pas un seul fichier monolithique — cf. les bugs d'encodage rencontrés sur DocuForge AI après une modification i18n mal isolée)
- **Backend** : messages d'erreur et libellés de règles métier traduisibles (`message_key` plutôt que texte en dur dans `eligibility_rules` et `requirements`)
- **Contenu métier multilingue** : les descriptions de procédures/documents doivent être stockées en JSONB `{"fr": "...", "pt": "...", "en": "..."}` plutôt qu'en colonne unique, car un administrateur métier (persona 6) doit pouvoir éditer le contenu par langue sans intervention développeur
- Langues prioritaires pour le premier cas d'usage réel (Guinée-Bissau) : **français, portugais, anglais**

---

## 7. Moteur de règles et workflow — précisions

Le choix "moteur JSON maison pour le MVP, JSONLogic/Drools à étudier plus tard" (§14 de la v1.0) est le bon choix — Drools serait une sur-ingénierie prématurée. Deux précisions pratiques :
- Utiliser **JSONLogic** directement plutôt qu'un moteur totalement maison dès le MVP : bibliothèque légère, testée, évite de réinventer l'évaluation de conditions imbriquées (AND/OR/comparaisons de dates)
- Pour le moteur de workflow (`workflow_definitions`/`workflow_instances`), une state machine explicite (**Spring Statemachine**) plutôt qu'un enchaînement de statuts géré à la main devient pertinente dès que le nombre de transitions dépasse une dizaine — recommandé pour la V2 quand les procédures se complexifient, pas nécessaire pour les 3 workflows du MVP (§46)

---

## 8. Architecture IA/OCR — isolation dès le MVP

Le PRD v1.0 prévoit `ai/ollama` et `ai/document-processing` comme dossiers dans l'arborescence dès le MVP (§51), mais le module `document` de Spring Boot n'y fait pas explicitement appel avant la V2. Recommandation : concevoir l'interface d'extraction (`DocumentExtractionService`) dès le MVP, avec une implémentation "stub" manuelle (l'agent saisit lui-même les champs), pour brancher l'OCR/LLM en V2 **sans changer le contrat d'API ni le modèle de données** (`document_validations` et les champs `ai_confidence` existent déjà en v1.0, bien vu).

Le service OCR/IA reste un microservice séparé (pas dans le monolithe Spring Boot) : il peut nécessiter du GPU, une mise à l'échelle indépendante, et ne doit jamais avoir d'accès direct en écriture à PostgreSQL — il communique uniquement via l'API Spring Boot, qui reste la seule source de vérité (principe déjà posé en v1.0 §34, à faire respecter aussi au niveau réseau Docker : le conteneur OCR ne doit pas être sur le même réseau que `postgres`).

---

## 9. Anti-fraude documentaire (V2)

Non couvert en v1.0. Pertinent pour une plateforme de visas (faux documents, réutilisation de la même pièce d'identité sur plusieurs dossiers/comptes) :
- Hash SHA-256 de chaque document déjà prévu (§39) → l'exploiter activement : alerte si un hash identique apparaît sur deux dossiers de demandeurs différents
- Vérification de cohérence MRZ ↔ champs saisis (déjà prévu en `MRZ_VALID`)
- Journalisation des tentatives de dépôt refusées (au-delà des dépôts réussis)

---

## 10. Observabilité et tests — avancés en Phase 1

Le PRD v1.0 place Prometheus/Grafana/Loki en "évolution recommandée" après le MVP (§44). Recommandation : au minimum **OpenTelemetry** (traces distribuées Spring Boot → n8n → OCR) dès le MVP, car diagnostiquer un dossier bloqué sans traçabilité technique de bout en bout est un vrai point de douleur avec ce type d'architecture multi-services.

Tests à cadrer dès le squelette du projet :
- **Testcontainers** (PostgreSQL, Keycloak, MinIO) pour les tests d'intégration Spring Boot
- Jeux de données de règles d'éligibilité versionnées et testées (un changement de règle ne doit jamais casser silencieusement un dossier en cours — cf. §20 versionnement déjà prévu)
- Contrats d'API (OpenAPI généré + validation de non-régression du contrat en CI)

---

## 11. Feature flags

Cohérent avec le principe "configuration plutôt que code" déjà posé en v1.0 (§3) : une table simple `feature_flags (key, organization_id nullable, enabled, value_json)` permet d'activer progressivement une nouvelle procédure ou un nouveau provider de paiement par organisation, sans déploiement. Pas besoin d'un outil externe (Unleash) pour le MVP.

---

## 12. Docker — services mis à jour

```
konselyavisa-postgres
konselyavisa-redis          # NOUVEAU
konselyavisa-backend
konselyavisa-frontend
konselyavisa-admin
konselyavisa-keycloak
konselyavisa-minio
konselyavisa-n8n
konselyavisa-ollama          # V2
konselyavisa-ocr             # V2, réseau Docker isolé (pas d'accès direct à postgres)
konselyavisa-mailpit         # dev/test uniquement
konselyavisa-nginx
konselyavisa-otel-collector  # NOUVEAU, dès le MVP
```

---

## 13. Ordre de développement révisé

1. Infrastructure Docker (+ Redis, + collecteur OpenTelemetry)
2. PostgreSQL + activation RLS sur les tables sensibles
3. Keycloak
4. Modèle de données (incl. `outbox_events`, `organization_settings`, `feature_flags`)
5. Squelette Spring Boot (`BaseEntity`, `ApiResponse<T>`, `PageResponse<T>`, MapStruct, Lombok)
6. Module `tenancy` (filtre Hibernate + RLS) — **avant** le catalogue, pas après, pour ne jamais coder une seule requête sans isolation organisationnelle
7. Catalogue pays/procédures (contenu multilingue JSONB dès le début)
8. Moteur de règles (JSONLogic)
9. Gestion des dossiers + module `outbox`
10. Documents et MinIO (+ hash SHA-256, anti-fraude basique)
11. Back-office agent
12. Portail citoyen
13. Workflows n8n (consommant les événements de `outbox_events`)
14. Paiement (interface `PaymentProvider`, Stripe d'abord, CinetPay ensuite)
15. i18n frontend/backend
16. IA documentaire (V2)

---

## 14. Découpage recommandé pour Cursor AI

Pour rester cohérent avec la méthode déjà utilisée sur DocuForge AI (PRD détaillé comme source de vérité, développement phase par phase) et sur ConsulOS (blocs d'implémentation + `.cursorrules`) :

1. Créer un fichier `.cursorrules` à la racine avec : conventions `ApiResponse<T>`/`BaseEntity`, règle "nouveau fichier plutôt que modification profonde d'un fichier existant", rappel du principe tenancy (jamais de requête JPA sans passer par le filtre organisation)
2. Découper ce PRD en **blocs d'implémentation numérotés** (à la manière des 9 blocs ConsulOS), dans l'ordre de la section 13 ci-dessus, un bloc = un prompt Cursor AI ciblé
3. Premier vertical slice identique à celui de la v1.0 (§53 : France → Guinée-Bissau → Visa touristique de bout en bout), en y ajoutant simplement le passage par le module `tenancy` et `outbox` dès cette première itération, pour valider l'architecture cible et non une version simplifiée qui devra être reprise ensuite

---

## 15. Ce qui ne change pas (à conserver tel quel depuis la v1.0)

- Vision produit, propositions de valeur, personas (§1–4)
- Parcours citoyen et maquettes d'écran (§6–13)
- Principe "Spring Boot possède l'état, n8n orchestre" (§27)
- Rôles Keycloak et `@PreAuthorize` (§38)
- Scope du MVP (3 workflows, §45–46) et critères de succès (§54)
- Vision ConsularOS V3 (§48)

---

*Document produit à partir du PRD KonselyaVisa v1.0 fourni, en conservant sa structure et en y intégrant les points techniques nécessaires à une implémentation robuste avec Cursor AI.*
