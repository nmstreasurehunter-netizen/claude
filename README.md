# TraceCroquis — v0.1.1

Application Android qui **vectorise un croquis de plan dessiné à main levée** et le
transforme en **plan à l'échelle** avec cartouche, exportable en **PDF, DXF et SVG**
pour être joint à un dossier de permis de construire ou à un dossier d'exécution.

Tout le traitement est réalisé **sur l'appareil** : aucune image n'est envoyée.

---

## Parcours en 4 étapes

| Étape | Écran | Ce qu'on y fait |
|------|-------|-----------------|
| 1 | **Importer le croquis** | Photo ou image, cadrage par 4 coins, rotation, redressement perspectif, contraste |
| 2 | **Vectoriser le croquis** | Analyse, aperçu Croquis / Vectorisé / Superposé, choix des éléments détectés, relance |
| 3 | **Vérifier et corriger** | Édition des murs, ouvertures, cotes et textes ; calibrage de l'échelle A↔B |
| 4 | **Exporter le plan** | Format de papier et échelle automatiques, cartouche, PDF / DXF / SVG |

---

## Ce que la vectorisation reconnaît

- **Murs et cloisons** : traits simples, traits doubles, bandes pleines ou hachurées,
  traits repassés plusieurs fois. L'épaisseur mesurée est convertie en voile
  (15–20 cm), mur porteur, doublage ou cloison (7 cm) une fois l'échelle connue.
- **Portes** : arc de débattement + vantail, portes simples, doubles, coulissantes,
  portes de garage. Le gond et le sens d'ouverture sont déduits de l'arc.
- **Fenêtres et baies** : menuiserie logée dans l'épaisseur du mur, percements.
- **Passages** : interruptions de mur reliées par un mur virtuel porteur de l'ouverture
  (c'est ce qui permet de refermer les pièces).
- **Pièces** : extraction des faces du graphe des murs, contour ramené au nu intérieur,
  surface calculée en m².
- **Noms de pièces et surfaces** : lus par OCR puis recalés sur un vocabulaire métier
  (CHAMBRE, SÉJOUR, DÉGAGEMENT, SdB…) tolérant aux fautes de reconnaissance.
- **Cotes manuscrites** : lignes à flèches ou à bouts obliques, valeur associée
  (`10,20 m`, `15m40`, `3,00 m`) → **calibrage automatique de l'échelle**.
- **Escaliers** : familles de marches parallèles régulièrement espacées.
- **Hachures** : isolant (dans l'épaisseur d'un mur), carrelage (dans une pièce),
  terrasse (hors emprise ou pièce extérieure).
- **Mobilier, sanitaires, véhicules** : tout ce qui reste à l'intérieur d'une pièce,
  classé selon le nom de la pièce (garage → véhicule, SdB/WC → sanitaire).

Quand aucune cote n'est lisible, l'échelle est estimée puis **confirmée à l'étape 3**
en plaçant deux points A et B sur une longueur connue.

---

## Compiler avec AndroidIDE (JDK 17)

1. Décompresser l'archive et ouvrir le dossier `TraceCroquis` dans **AndroidIDE**.
2. Vérifier dans *Preferences → Build & Run* que le **JDK 17** est sélectionné.
3. `Build → Assemble Debug` (ou `./gradlew assembleDebug`).
4. L'APK est produit dans `app/build/outputs/apk/debug/app-debug.apk`.

Configuration utilisée : AGP **8.1.4**, Gradle **8.2**, `compileSdk 35`, `minSdk 24`,
`targetSdk 35`, Java 17, **aucun code natif propre au projet**, **aucun Kotlin**
(compilation plus rapide et plus sûre sur téléphone). L'APK ne conserve que les
architectures `arm64-v8a` et `armeabi-v7a` : c'est ce qui divise sa taille par deux.

> `targetSdk 35` est nécessaire : en dessous, Google Play Protect bloque
> l'installation avec le message « Appli non sécurisée bloquée — conçue pour une
> version plus ancienne d'Android ».

### Compilation hors ligne

La seule dépendance non essentielle est la reconnaissance de texte ML Kit.
Elle est appelée **par réflexion** : l'application compile et fonctionne sans elle.
Pour l'exclure, ajouter dans `gradle.properties` :

```properties
tracecroquis.mlkit=false
```

Les textes restent alors à saisir à l'étape 3 ; tout le reste (murs, ouvertures,
pièces, cotes, exports) fonctionne à l'identique.

---

## Organisation du code

```
app/src/main/java/com/tracecroquis/
├── core/                  moteur, Java pur, sans dépendance Android
│   ├── geom/G             géométrie (projections, polygones, ajustements)
│   ├── img/               Gray, Mask, Prep (binarisation Sauvola), Cc, Dt, Thin, Trace
│   ├── model/Plan         modèle du plan (nœuds, murs, ouvertures, pièces, cotes…)
│   └── vector/            Fit, WallBuilder, OpeningDetector, RoomBuilder,
│                          TextAnalyzer, DimensionDetector, SymbolDetector, Vectorizer
├── export/                Layout (papier/échelle auto), PlanRenderer, SheetRenderer,
│                          TitleBlock (cartouche), PdfExporter, VectorExporter (SVG/DXF)
├── ocr/OcrReader          ML Kit par réflexion (facultatif)
├── store/                 sauvegarde JSON des projets
├── ui/                    MainActivity + Step1..Step4, Projets, Réglages
└── util/                  images, formats, tâches de fond
```

Le paquet `core` ne dépend d'aucune API Android : il peut être compilé et testé
sur poste de travail (c'est ainsi que la v0.1 a été mise au point sur les croquis
d'exemple).

---

## Affiner la vectorisation (objectif de cette v0.1)

L'onglet **Réglages** expose les paramètres du moteur. Marche à suivre :

1. Étape 2, vue **Superposé** : les vecteurs doivent coller au croquis.
2. Un mur manque → baisser *Longueur minimale d'un mur*.
3. Des murs doubles restent séparés → augmenter *Écartement max. d'un mur double*.
4. Croquis très hachuré ou repassé → augmenter *Soudure des traits repassés*.
5. Papier millimétré visible → baisser *Filtrage du quadrillage*.
6. Pièces non trouvées → augmenter *Tolérance de raccord* (les murs doivent se toucher).
7. Relancer la vectorisation et comparer.

Le détail du pipeline et de chaque paramètre est dans
[`docs/VECTORISATION.md`](docs/VECTORISATION.md).

---

## En cas de problème

L'application enregistre les erreurs fatales dans un fichier local. Au démarrage
suivant, un **rapport de plantage** s'affiche avec un bouton *Copier* : il contient
le modèle de l'appareil, la version d'Android et la trace exacte de l'erreur.

Un test automatique (`./gradlew testDebugUnitTest`) vérifie que les six écrans
s'affichent sans erreur, puis vectorise un croquis de synthèse et produit les
trois exports. C'est le filet de sécurité contre les mises en page invalides —
c'est exactement ce type de défaut qui empêchait la v0.1 de démarrer.

## Limites connues de la v0.1

- La reconnaissance de l'écriture manuscrite dépend de ML Kit : les chiffres
  soignés passent bien, l'écriture cursive rapide beaucoup moins. Les textes
  restent modifiables à l'étape 3.
- Sur les plans très denses (mobilier dessiné, hachures partout), une partie des
  symboles est classée « mobilier » sans finesse.
- Les murs courbes sont approchés par des segments.
- Un seul niveau par projet (pas encore de superposition RDC / étage).
