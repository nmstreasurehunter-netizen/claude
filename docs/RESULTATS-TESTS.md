# Résultats de la v0.1 sur les dix croquis d'essai

Mesures obtenues avec le moteur `core` compilé sur poste de travail
(analyse à 1500 px de côté, réglages par défaut, **sans OCR** — donc sans
lecture des cotes écrites, ce qui explique les échelles estimées).

| Croquis | Style de dessin | Murs | Ouvertures (P/F/autres) | Pièces | Cotes | Symboles | Durée |
|---|---|---|---|---|---|---|---|
| Plan 3 pièces au trait | trait fin simple | 27 | 6 (2 / 0 / 4) | **4 / 4** | 2 | 3 | 0,49 s |
| « Projet maison — idée 2 » | stylo, traits repassés | 41 | 8 (1 / 0 / 7) | 4 / 5 | 2 | 4 | 0,30 s |
| Maison à patio | murs hachurés | 57 | 15 | 2 / 6 | 1 | 7 | 0,28 s |
| 3 chambres + couloir | trait bleu | 49 | 9 (1 / 0 / 8) | **7 / 8** | 2 | 4 | 0,21 s |
| Plan d'architecte noir et blanc | murs pleins, mobilier | 111 | 19 (4 / 4 / 11) | 3 / 12 | 2 | 8 | 0,26 s |
| Plan « étage » hachuré | murs hachurés fins | 104 | 21 | 3 / 9 | 2 | 7 | 0,11 s |
| Plan couleur coté | murs pleins | 116 | 20 | 12 / 11 | 2 | 6 | 0,09 s |
| Plan RDC coloré | murs pleins, mobilier | 115 | 16 (3 / 5 / 8) | 13 / 12 | 2 | 7 | 0,15 s |
| Plan crayon sur papier quadrillé | crayon + couleur | 172 | 23 (2 / 6 / 15) | 12 / 11 | 3 | 12 | 0,46 s |
| Croquis papier millimétré | crayon fin, quadrillage | 354 | 72 | 7 / 14 | 2 | 31 | 0,60 s |

Lecture : « 4 / 5 » signifie 4 pièces fermées trouvées pour 5 attendues.
Sur téléphone, compter environ 3 à 6 fois ces durées.

## Ce qui marche bien

- Les **croquis au trait** (simple ou double) et les **plans à murs pleins** :
  axes de murs justes, épaisseurs mesurées, pièces fermées, portes placées.
- Les **traits repassés plusieurs fois** grâce à la fermeture morphologique
  et à l'élagage du squelette.
- Le **rejet des lignes de cote** : elles ne sont plus prises pour des murs.
- Le **filtrage du papier quadrillé** et millimétré.

## Ce qui demande encore un passage à l'étape 3

- Les **murs hachurés très fins** (plans d'architecte imprimés) : l'axe se
  fragmente, certaines pièces ne se referment pas. Augmenter *Soudure des traits
  repassés* et *Tolérance de raccord* améliore nettement le résultat.
- Les **plans très meublés** : une partie du mobilier reste classée
  « indéterminé », et quelques symboles sont pris pour des percements.
- Sans OCR, l'échelle est estimée : il faut la confirmer avec les points A et B.

## Méthode pour rejouer ces essais

```bash
javac -d build $(find app/src/main/java/com/tracecroquis/core -name '*.java')
# puis un petit programme qui charge l'image avec ImageIO,
# appelle Vectorizer.run(...) et dessine le Plan obtenu.
```

Voir `docs/VECTORISATION.md` pour le détail du pipeline et des réglages.

---

## Correctifs de la v0.1.1

| Symptôme | Cause | Correctif |
|---|---|---|
| « Appli non sécurisée bloquée — conçue pour une version plus ancienne d'Android » | `targetSdk 33`, en dessous du seuil exigé par Google Play Protect | `compileSdk`/`targetSdk` portés à **35**, AGP 8.1.4, AndroidX à jour |
| L'application s'installe mais ne démarre pas | Plusieurs vues utilisaient un style (`T.Label`, `T.Body`…) qui ne fournissait ni `layout_width` ni `layout_height` → `RuntimeException` à l'inflation du premier écran | Les styles portent désormais leurs dimensions ; 104 occurrences corrigées d'un coup |
| Interface sous les barres système sur Android 15 | `targetSdk 35` impose l'affichage bord à bord | Report des encarts système en marges intérieures (`WindowInsetsCompat`) |
| Diagnostic impossible sans câble | — | Journal de plantage local, proposé à la copie au démarrage suivant |

Vérification : `./gradlew testDebugUnitTest` — **5 tests, 0 échec** (affichage des
six écrans, vectorisation d'un croquis de synthèse, exports PDF / SVG / DXF,
recalibrage de l'échelle).
