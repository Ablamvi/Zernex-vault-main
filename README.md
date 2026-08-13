# ZERNEX Vault 3.0.1

Coffre-fort local chiffré (AES-256-GCM).

## Correctifs 3.0.1

- Suppression de `ExperimentalFoundationApi` / `combinedClickable` (erreur CI Kotlin)
- Clic simple sur les vignettes de la grille
- Dépendance `fragment-ktx` pour la biométrie (`FragmentActivity`)

## Fonctions 3.x

- Miniatures images + frame vidéo
- Grille / liste, tri, espace coffre
- Déplacer / copier à l’import
- Restaurer sur le téléphone
- Lecteurs image / audio / vidéo
- Empreinte + FLAG_SECURE
- GitHub Actions : **debug + release**

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## CI

Push sur `main`/`master` → artifacts `ZERNEX-Vault-debug` et `ZERNEX-Vault-release`.
