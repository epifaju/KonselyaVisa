# UX Guidelines — KonselyaVisa

Document de référence ergonomique, à lire à côté de `docs/PRD_KonselyaVisa_v2_Optimise_CursorAI.md` et de `.cursorrules`. Il fixe les règles d'interface qui s'appliquent à tout écran, existant ou à venir, et sert de référence à Cursor AI pour rester cohérent entre les blocs de développement.

---

## 1. Trois contraintes qui gouvernent toute l'interface

1. **Anxiété du statut** — l'utilisateur ne sait jamais spontanément où il en est. Chaque écran doit répondre à "où j'en suis / combien de temps encore" avant d'exiger une action.
2. **Asymétrie de compétence** — le citoyen (usage ponctuel, souvent mobile, littératie numérique variable) et l'agent/superviseur (usage intensif, clavier, toute la journée) n'ont pas les mêmes besoins d'interface pour le même contenu métier.
3. **Erreur coûteuse** — une pièce jointe illisible ou un champ mal rempli retarde un dossier de plusieurs jours. L'ergonomie doit prévenir l'erreur avant qu'elle ne coûte un aller-retour, pas seulement la signaler après coup.

---

## 2. Règles transverses (s'appliquent à tout écran)

- **Une seule action primaire visible par écran.** Un seul bouton plein (accent), tout le reste en secondaire/ghost — pour qu'un utilisateur peu à l'aise ne se demande jamais lequel cliquer.
- **Mobile-first côté citoyen.** Formulaires longs découpés en étapes/sections, jamais un scroll de 15 champs. Le citoyen dépose souvent ses documents depuis son téléphone.
- **Densité assumée côté agent/superviseur.** Tableaux compacts, tri/filtre rapide, aucune animation qui ralentit un usage répété huit heures par jour. Ce n'est pas le même design system visuel que le portail citoyen, même si les composants de base (badges, boutons) sont partagés.
- **Accessibilité non négociable.** Contraste AA minimum, navigation clavier complète, aucun message d'erreur qui repose sur la couleur seule (toujours icône + texte).
- **Vocabulaire simple côté citoyen.** Pas de jargon administratif ("MRZ", "apostille de La Haye") sans reformulation en langage courant — d'autant plus important pour un public naviguant en portugais ou en anglais.
- **Contenu conditionnel affiché en contexte, jamais en modale bloquante.** Une conséquence d'un choix (ex. séjour de plus de 90 jours) s'affiche immédiatement à côté du champ concerné.
- **Aucune donnée déjà connue n'est reressaisie.** Ce qui a été collecté à une étape (identité, email) pré-remplit les étapes suivantes.
- **Motifs structurés plutôt que texte libre.** Toute justification adressée au citoyen (motif de refus, motif de correction) part d'un choix structuré et traduisible (`message_key`), avec un champ libre en complément facultatif seulement.

---

## 3. Code couleur des statuts (identique citoyen / agent)

Un agent et un citoyen doivent parler le même langage visuel de statut :

| Couleur | Signification | Exemples d'usage |
|---|---|---|
| Vert (`success`) | Validé, terminé, prêt | Document accepté, étape franchie, dossier prêt à retirer |
| Orange (`warning`) | En attente, à surveiller | Vérification en cours, alerte de dépassement de délai |
| Rouge (`danger`) | Action requise, bloquant | Document refusé, dossier hors délai SLA |
| Bleu (`accent`) | Action/sélection en cours | Étape active d'un parcours, option sélectionnée |
| Gris (`neutral`) | Pas encore atteint, informatif | Étape future d'une timeline, statut neutre en cours de traitement |

Ne jamais réutiliser le rouge ou le vert à d'autres fins (décoratif, catégorisation) : leur seule signification dans toute la plateforme est le statut.

---

## 4. Catalogue d'écrans

### 4.1 Portail citoyen

**Accueil / assistant d'éligibilité**
- Point d'entrée = assistant conversationnel court (3-4 questions max), jamais un formulaire vide.
- Une seule décision par étape, en cartes cliquables, jamais un menu déroulant caché.
- Barre de progression visible, réponses modifiables en revenant en arrière.

**Dépôt de documents**
- Statut affiché document par document, jamais un bouton unique "envoyer les fichiers".
- Trois statuts distincts par document : validé / en cours de vérification (neutre) / refusé (motif spécifique et actionnable, jamais "document invalide").
- L'action de correction (renvoyer) est au plus près du document concerné.

**Suivi de dossier**
- Timeline horizontale avec étapes passées/actuelle/à venir, jamais un simple libellé de statut textuel.
- Estimation de délai chiffrée et datée, affichée à côté de l'étape en cours.

**Connexion**
- L'identité de l'organisation cliente (consulat, agence) est visible en premier, avant la marque KonselyaVisa.
- Option "continuer sans compte" pour ne pas bloquer un utilisateur qui ne sait pas encore s'il est éligible.
- Connexion agent/personnel reléguée discrètement en bas de l'écran, jamais mise en avant sur un portail public.

**Création de compte**
- Déclenchée après une première information utile obtenue (éligibilité confirmée), jamais en tout premier écran.
- Champs déjà connus pré-remplis et non modifiables si sensibles (nom), seuls email/mot de passe restent à saisir.
- Consentement RGPD explicite, horodaté, stocké (base légale du traitement).
- Message explicite que le dossier déjà commencé sera rattaché au compte.

**Paiement**
- Toujours montrer le détail de ce qui est facturé avant de demander un moyen de paiement.
- Plusieurs moyens de paiement adaptés au contexte réel (carte, Mobile Money, espèces au guichet), pas seulement une carte bancaire.
- Le bouton final précise l'action déclenchée ("Payer et transmettre le dossier"), jamais un simple "Payer".
- Si le moyen choisi ne déclenche pas de paiement immédiat (espèces au guichet), le libellé du bouton change en conséquence ("Réserver ce mode de paiement").
- Rassurance de sécurité et de séquencement ("le dossier n'est transmis qu'après confirmation") toujours visible près du bouton.

**Formulaire dynamique par procédure**
- Découpage en sections nommées avec navigation latérale, jamais un long formulaire plat.
- La navigation par section est générée depuis la même définition JSON que les champs — jamais codée en dur pour une procédure donnée.
- Un champ ou une exigence conditionnelle apparaît en contexte immédiatement, sans interrompre la saisie.

**Prise de rendez-vous**
- Créneaux réellement disponibles regroupés par jour, jamais une grille calendrier vide de sens.
- Informations pratiques (adresse, pièces à apporter) visibles sur le même écran que le choix du créneau.
- Le créneau sélectionné est verrouillé côté serveur dès le clic (courte expiration), pas seulement à la confirmation.
- Le bouton de confirmation répète le créneau choisi en toutes lettres.

**Espace entreprise**
- Vue liste multi-dossiers avec compteurs par statut, jamais l'écran "un seul dossier" du citoyen transposé tel quel.
- Colonne indiquant qui, parmi les collaborateurs, a créé chaque dossier et avec quel rôle.
- Un collaborateur non-admin ne voit et ne gère que les dossiers qu'il a lui-même créés.

### 4.2 Back-office agent / superviseur

**File de dossiers (agent)**
- Vue liste dense (tableau), pas de cartes aérées façon portail citoyen.
- Filtres/onglets de statut en haut, tri par ancienneté visible directement dans la liste.
- L'ancienneté d'un dossier en attente est mise en évidence visuellement (couleur) au-delà d'un certain seuil.

**Modale de correction**
- Motif choisi dans une liste structurée par type de document (configurable, pas codé en dur), champ libre en complément facultatif uniquement.
- Envoi déclenche l'événement métier `CORRECTION_REQUESTED` (pattern outbox) qui notifie le citoyen dans sa langue via le `message_key` du motif.

**Historique / audit d'un dossier**
- Chaque ligne répond sans ambiguïté à qui, quoi, quand — phrases lisibles, jamais un journal technique brut (noms de champs, UUID).
- Acteur identifié par icône/couleur constante (citoyen, agent, système).
- Alimenté directement par `audit_logs` et `outbox_events` — aucune donnée supplémentaire à modéliser pour cet écran.

**Dashboard superviseur**
- L'anomalie est visuellement plus grande que la normalité : une alerte de dépassement de délai (SLA) est en tête d'écran, en rouge, avec une action directe.
- Les indicateurs neutres (dossiers actifs, délai moyen) sont en cartes simples, sans effet visuel qui rivalise avec l'alerte.
- La charge par agent utilise la couleur pour signaler un déséquilibre, pas seulement des barres identiques.

---

## 5. Points d'implémentation à ne pas perdre (rappel pour Cursor AI)

- Les motifs de refus/correction, les libellés de procédure et les exigences documentaires sont des **données de configuration multilingues** (JSONB `{"fr":..., "pt":..., "en":...}`), jamais du texte codé en dur dans un composant.
- Le verrouillage d'un créneau de rendez-vous passe par Redis avec expiration courte, pas uniquement par une validation applicative au moment de la confirmation.
- Le consentement RGPD à la création de compte doit être stocké de façon opposable (horodatage, texte exact accepté).
- Tout événement déclenchant une notification ou une action externe (correction demandée, paiement confirmé, dossier transmis) passe par la table `outbox_events`, jamais par un appel direct synchrone depuis l'écran ou le contrôleur.
- Les composants de statut (badges vert/orange/rouge) sont partagés entre portail citoyen et back-office — un seul set de composants shadcn/ui, pas deux implémentations parallèles.

---

*Document à conserver dans `docs/UX_GUIDELINES.md`, à mettre à jour à chaque nouvel écran ou pattern validé.*
