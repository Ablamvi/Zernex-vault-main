# ZERNEX Vault — Coffre-fort local sécurisé

**ZERNEX Vault** cache et chiffre photos, vidéos, documents et fichiers sur ton téléphone.  
Rien n’apparaît dans la galerie, les fichiers ou Google Photos.

## Sécurité

| Élément | Détail |
|---------|--------|
| Stockage | Répertoire **privé de l’app** (`filesDir/vault`) — inaccessible aux autres apps |
| Chiffrement fichiers | **AES-256-GCM** via Android Keystore (`security-crypto`) |
| PIN / Schéma | Hash **PBKDF2-HMAC-SHA256** (120 000 itérations) + sel aléatoire |
| Préférences | `EncryptedSharedPreferences` |
| Backup | **Désactivé** (pas de sauvegarde cloud / transfert des secrets) |
| Anti-bruteforce | Verrouillage temporaire après 5+ échecs |
| Relock | Verrouillage auto quand l’app passe en arrière-plan |

## Verrouillage

1. **PIN** (4–8 chiffres)  
2. **Schéma** (min. 4 points)

## Récupération (si oubli)

1. **2 questions secrètes** (définies à l’installation)  
2. **Clé de récupération** affichée une seule fois (format `XXXX-XXXX-XXXX-XXXX`)

→ Puis création d’un nouveau PIN ou schéma.

## Fonctionnalités

- Import multi-fichiers (SAF — tous types)
- Catégories : Images, Vidéos, Audio, Documents, Autres
- Recherche
- Suppression définitive du coffre
- UI Material 3 sombre (identité ZERNEX orange / cyan)

## Ce qui n’est **pas** visible

Les fichiers importés sont **copiés chiffrés** dans le stockage privé de l’app.  
Ils **ne s’affichent plus** dans :
- Galerie / Photos
- Fichiers système
- Autres applications

> Astuce : après import réussi, tu peux supprimer manuellement l’original depuis ta galerie pour qu’il ne reste que dans le coffre.

## Build APK

### Android Studio
1. Ouvre le dossier  
2. Sync Gradle  
3. **Build → Build APK(s)**

### GitHub Actions
Workflow `Build ZERNEX Vault APK (32-bit)` inclus.

## Permissions

**Minimales** : pas de `READ_MEDIA_*` global.  
L’import passe par le **sélecteur de documents système** (SAF).  
Biométrie déclarée pour évolutions futures.

## Stack

- Kotlin + Jetpack Compose + Material 3  
- `androidx.security:security-crypto` (MasterKey + EncryptedFile + EncryptedSharedPreferences)  
- PBKDF2 pour secrets utilisateur  
- 100 % local, zéro réseau  

---

**ZERNEX Vault** — Tes fichiers, chiffrés, invisibles, à toi seuls.
