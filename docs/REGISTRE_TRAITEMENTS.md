# Registre des traitements — KonselyaVisa (MVP)

Document d’information (art. 30 RGPD). Ce n’est **pas** un DPA signé avec une organisation cliente.

| | |
|---|---|
| Dernière mise à jour | 2026-09-19 |
| Responsable de traitement | l’organisation cliente (consulat, agence) pour les dossiers qu’elle instruit |
| Sous-traitant technique | l’opérateur de la plateforme KonselyaVisa (hébergement Spring Boot / PostgreSQL / MinIO) |

## Traitements

| Finalité | Base légale | Données | Personnes | Destinataires | Durée |
|---|---|---|---|---|---|
| Instruire un dossier de formalité consulaire (visa, etc.) | Consentement explicite au dépôt (`privacy_consents`, horodatage + texte + version) | Identité déclarée, faits d’éligibilité, pièces, paiement, rendez-vous | Citoyens / usagers de l’organisation | Agents de l’organisation, prestataire de paiement (Stripe/MOCK/manuel), stockage objets (MinIO) | Conservation du dossier : **5 ans** (1825 jours) après la demande d’effacement, puis anonymisation différée |
| Authentification | Exécution du service / intérêt légitime de sécuriser l’accès | Identifiant Keycloak (`sub`), rôles, organisation | Utilisateurs connectés | Keycloak | Durée de la session OIDC |
| Notification autour d’un événement métier | Intérêt légitime (suivi du dossier) | Identifiants de dossier, type d’événement (pas de n° de passeport) | Usager concerné | n8n (e-mail) | Durée de rétention mail / outbox |

Les numéros de passeport et données d’identité extractibles ne sont **pas** stockés en clair dans `applicant_facts` ni dans `audit_logs` / logs applicatifs.

## Droits des personnes

- Accès / portabilité : `GET /api/v1/me/data-export` (JSON, compte authentifié).
- Effacement : `POST /api/v1/me/data-deletion-request` — **anonymisation différée** à `scheduled_anonymize_at` (conservation consulaire), pas de suppression immédiate du dossier.
- Opposition / retrait : nouveau dossier exige un nouveau consentement ; le consentement passé reste opposable pour la période d’instruction.

## Sous-traitants techniques (MVP local / compose)

PostgreSQL, Redis (holds / cache / rate-limit, pas de pièce d’identité), MinIO, Keycloak, n8n (notifications), Stripe si l’organisation active ce fournisseur.

## Violation de données

Notification à l’autorité et aux personnes concernées **dans les 72 heures** après prise de connaissance, dès lors que le risque pour les droits et libertés est avéré. Procédure opérationnelle à tenir par l’opérateur (hors code).

## DPA

Un accord de sous-traitance signé avec chaque organisation SaaS est **hors livrable code** (bloc 17).
