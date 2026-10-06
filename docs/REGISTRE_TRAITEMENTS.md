# Registre des traitements — KonselyaVisa (MVP)

Document d’information (art. 30 RGPD). Le **DPA (art. 28)** signé / accepté par organisation cliente est formalisé en base (`organization_dpa_agreements`) ; ce fichier reste le registre opérateur.

| | |
|---|---|
| Dernière mise à jour | 2026-09-20 |
| Responsable de traitement | l’organisation cliente (consulat, agence) pour les dossiers qu’elle instruit |
| Sous-traitant technique | l’opérateur de la plateforme KonselyaVisa (hébergement Spring Boot / PostgreSQL / MinIO) |

## Traitements

| Finalité | Base légale | Données | Personnes | Destinataires | Durée |
|---|---|---|---|---|---|
| Instruire un dossier de formalité consulaire (visa, etc.) | Consentement explicite au dépôt (`privacy_consents`, horodatage + texte + version) | Identité déclarée, faits d’éligibilité, pièces, paiement, rendez-vous | Citoyens / usagers de l’organisation | Agents de l’organisation, prestataire de paiement (Stripe/MOCK/manuel/CinetPay/PayDunya), stockage objets (MinIO) | Conservation : **jours du DPA actif** (défaut **1825**) après demande d’effacement, puis **purge automatique** (anonymisation) |
| Authentification | Exécution du service / intérêt légitime de sécuriser l’accès | Identifiant Keycloak (`sub`), rôles, organisation | Utilisateurs connectés | Keycloak | Durée de la session OIDC |
| Notification autour d’un événement métier | Intérêt légitime (suivi du dossier) | Identifiants de dossier, type d’événement (pas de n° de passeport) | Usager concerné | n8n (e-mail) | Durée de rétention mail / outbox |

Les numéros de passeport et données d’identité extractibles ne sont **pas** stockés en clair dans `applicant_facts` ni dans `audit_logs` / logs applicatifs. Les champs extraits chiffrés sont effacés à la purge.

## Droits des personnes

- Accès / portabilité : `GET /api/v1/me/data-export` (JSON, compte authentifié).
- Effacement : `POST /api/v1/me/data-deletion-request` — planification à `scheduled_anonymize_at` (rétention DPA ou `konselyavisa.privacy.anonymize-after`).
- Purge : scheduler `RetentionPurgeService` (activé si `konselyavisa.privacy.purge-enabled=true`) — anonymise demandeur + faits dossier + champs extraits, statut `COMPLETED`, outbox `DATA_DELETION_COMPLETED`.
- Opposition / retrait : nouveau dossier exige un nouveau consentement ; le consentement passé reste opposable pour la période d’instruction.

## Sous-traitants techniques (MVP local / compose)

PostgreSQL, Redis (holds / cache / rate-limit, pas de pièce d’identité), MinIO, Keycloak, n8n (notifications), Stripe / CinetPay / PayDunya si l’organisation active ces fournisseurs.

## Violation de données

Notification à l’autorité et aux personnes concernées **dans les 72 heures** après prise de connaissance, dès lors que le risque pour les droits et libertés est avéré. Procédure opérationnelle à tenir par l’opérateur (hors code).

## DPA (art. 28)

- Catalogue canonique : version `OrganizationDpaCatalog.CURRENT_VERSION` (`2026.09`).
- Enregistrement par org : table `organization_dpa_agreements` (un seul `ACTIVE` ; acceptation → `SUPERSEDED` de l’ancien).
- API : `GET /api/v1/organizations/me/dpa` ; `POST /api/v1/organizations/me/dpa/accept` (`BUSINESS_ADMIN` / `PLATFORM_ADMIN`).
- Org DEMO : DPA seedé (rétention 1825 jours).
- La signature manuscrite / PDF juridique peut être référencée via `document_uri` ; l’acceptation applicative matérialise l’accord ops.
