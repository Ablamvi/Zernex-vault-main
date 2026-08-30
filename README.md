# ZERNEX Vault 3.3.1

Coffre-fort local chiffré (AES-256-GCM).

## 3.3.1 — Lecteur vidéo

- Sliders verticaux luminosité (gauche) / volume (droite), style maquette
- Double-tap ±10 s avec animation + disparition auto (~0,7 s)
- Zones de geste stables (seuil anti micro-mouvement)
- Barre de contrôles arrondie, vitesse, cadenas
- Écran allumé pendant la lecture
- Reprise de position

## CI

Push sur `main`/`master` → artifact **ZERNEX-Vault-release** uniquement (pas de debug).

```bash
./gradlew assembleRelease
```
