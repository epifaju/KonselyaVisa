# Design tokens — KonselyaVisa

Ce document explique comment utiliser `tokens.css` et `tailwind.tokens.ts` (fournis à côté) dans les deux frontends (`citizen-portal`, `admin-portal`), et comment étendre les composants shadcn/ui existants pour respecter `docs/UX_GUIDELINES.md`.

**Installation dans un frontend :**
1. Copier le contenu de `tokens.css` dans `app/globals.css` (ou `src/index.css`), à la place du bloc `:root` / `.dark` généré par `shadcn init`.
2. Fusionner `theme.extend` de `tailwind.tokens.ts` dans `tailwind.config.ts` du projet.
3. Les deux frontends doivent utiliser exactement les mêmes valeurs — en cas de divergence future, factoriser dans un package partagé (`packages/design-tokens`) plutôt que laisser les deux fichiers dériver.

---

## 1. Principe : jamais de couleur en dur

Aucun composant ne doit contenir de couleur littérale (`#3B82F6`, `bg-blue-500`, `rgb(...)`). Toute couleur passe par une classe Tailwind qui référence un token (`bg-primary`, `text-destructive`, `border-warning`). C'est ce qui garantit que le thème sombre fonctionne automatiquement et que citoyen/agent restent visuellement cohérents.

## 2. Table de correspondance token → usage

| Token Tailwind | Rôle | Exemples d'usage observés dans les maquettes |
|---|---|---|
| `primary` | Action principale, sélection active | Bouton "Continuer", étape active d'une timeline, créneau de rendez-vous sélectionné |
| `secondary` | Action secondaire | Bouton "Retour", "Annuler" |
| `muted` | Texte/fond discret | Sous-titres, texte d'aide, étapes non atteintes |
| `accent` | Survol, fond de sélection légère | Fond d'une carte de choix sélectionnée, `bg-accent/muted` dans les mockups |
| `success` | Statut validé | Document accepté, étape terminée, dossier prêt à retirer |
| `warning` | Statut en attente / alerte non bloquante | Vérification en cours, alerte de délai SLA |
| `destructive` | Statut bloquant, erreur | Document refusé, champ invalide, dossier hors délai |
| `card` | Conteneur de contenu | Cartes de résumé, modales |
| `border` | Séparateurs, contours de champs | Toutes les bordures par défaut |

**Règle stricte de mapping statut → token**, cohérente avec `docs/UX_GUIDELINES.md` §3 :
- Vert = `success` — jamais utilisé hors contexte de validation.
- Orange = `warning` — jamais utilisé pour une simple information neutre.
- Rouge = `destructive` — jamais utilisé pour une simple mise en avant visuelle.

## 3. Extension des composants shadcn/ui

Les composants générés par `shadcn add badge` et `shadcn add button` n'ont pas nativement de variante `success`/`warning`. Les ajouter dans le fichier généré (`components/ui/badge.tsx`, `components/ui/button.tsx`), sans créer de nouveau composant parallèle :

```tsx
// components/ui/badge.tsx — ajout dans badgeVariants (cva)
const badgeVariants = cva(
  "inline-flex items-center rounded-md border px-2.5 py-0.5 text-caption font-medium",
  {
    variants: {
      variant: {
        default: "border-transparent bg-primary text-primary-foreground",
        secondary: "border-transparent bg-secondary text-secondary-foreground",
        destructive: "border-transparent bg-destructive text-destructive-foreground",
        success: "border-transparent bg-success/10 text-success",
        warning: "border-transparent bg-warning/10 text-warning",
        outline: "text-foreground",
      },
    },
    defaultVariants: { variant: "default" },
  }
);
```

```tsx
// components/ui/button.tsx — ajout dans buttonVariants (cva)
success: "bg-success text-success-foreground hover:bg-success/90",
warning: "bg-warning text-warning-foreground hover:bg-warning/90",
```

Toute autre variante de couleur (par exemple pour un composant `Timeline` ou `StatusDot` propre à KonselyaVisa, non fourni par shadcn/ui) doit suivre le même principe : un fichier dans `components/ui/`, une définition `cva` avec les tokens ci-dessus, jamais de style inline avec une couleur littérale.

## 4. Typographie

Utiliser les classes `text-caption`, `text-body-sm`, `text-body`, `text-body-lg`, `text-heading-1/2/3` ajoutées dans `tailwind.tokens.ts`, plutôt que les tailles Tailwind par défaut (`text-xs`, `text-sm`...), pour rester alignées avec les tailles utilisées dans les maquettes de référence (`docs/UX_GUIDELINES.md`). Deux graisses seulement : `font-normal` (400) pour le texte courant, `font-medium` (500) pour les titres et labels — jamais `font-semibold` ou `font-bold`.

## 5. Rayon de bordure

- `rounded-sm` : champs de formulaire, petits contrôles
- `rounded-md` : boutons, badges
- `rounded-lg` (12px effectif) : cartes, modales

## 6. Ce que Cursor AI ne doit jamais faire

- Ajouter une couleur hors de cette table sans la faire remonter en token d'abord (dans `tokens.css`, pas localement dans un composant).
- Dupliquer les composants shadcn/ui existants pour ajouter une variante — toujours étendre le fichier généré.
- Utiliser `success`/`warning`/`destructive` pour autre chose qu'un statut métier réel (voir `docs/UX_GUIDELINES.md` §2 et §3).
