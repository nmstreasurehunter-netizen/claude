# Pipeline de vectorisation — TraceCroquis 0.1

Tout le moteur est dans `app/src/main/java/com/tracecroquis/core/`, en **Java pur**
(aucune dépendance Android), ce qui permet de le compiler et de le tester sur poste
de travail avant de l'embarquer.

Entrée : les pixels ARGB du croquis redressé.
Sortie : un objet `Plan` (nœuds, murs, ouvertures, pièces, textes, cotes, symboles)
et un facteur d'échelle `pxPerMeter`.

---

## 1. Préparation de l'image — `img/Gray`, `img/Prep`

- **Carte d'encre** (`Gray.inkFromArgb`) : au lieu d'une luminance classique, on
  combine luminance et canal minimum. Un trait de stylo bleu ou noir reste sombre
  alors qu'un quadrillage bleu clair remonte vers le blanc.
- **Binarisation de Sauvola** (`Prep.binarize`) : seuil local
  `T = m · (1 − k · (1 − s/128))`, calculé par image intégrale, **borné par le seuil
  global d'Otsu + marge**. C'est cette double contrainte qui élimine les papiers
  millimétrés sans perdre les traits fins.
  - `binarizeK` : sensibilité locale (0,22 par défaut).
  - `binarizeMargin` : tolérance au-dessus d'Otsu. Le baisser filtre davantage le quadrillage.
- **Nettoyage** : suppression des taches (`despeckle`) et des lignes de trame
  traversant toute la feuille (`removeGridLines`).

## 2. Composantes et séparation texte / dessin — `img/Cc`, `img/Dt`, `vector/TextAnalyzer`

- Étiquetage en 8-connexité, avec pour chaque composante : boîte, surface, centre
  et **épaisseur de trait** mesurée par carte de distance chanfrein (`Dt`).
- Si l'OCR a fourni des boîtes, elles font autorité pour dire ce qui est du texte.
  Sinon un critère géométrique est utilisé (hauteur, remplissage, finesse).
- Les composantes de texte sont retirées avant la squelettisation : elles ne
  peuvent donc pas être confondues avec des murs.

## 3. Squelettisation et polylignes — `img/Thin`, `img/Trace`

- **Fermeture morphologique** de rayon adaptatif (≈ 0,9 × épaisseur médiane) :
  soude les hachures d'un mur, les traits repassés plusieurs fois et les doubles
  traits très serrés → un seul axe par mur.
- **Amincissement de Zhang-Suen**, puis extraction des polylignes élémentaires
  coupées à chaque nœud (extrémité ou intersection).
- **Élagage des barbules** : les courtes branches nées d'une intersection et
  finissant dans le vide sont supprimées, puis le squelette est re-parcouru.
  C'est indispensable sur les croquis à main levée.
- Chaque polyligne porte l'épaisseur d'encre mesurée (2 × distance au fond).

## 4. Segments et arcs — `vector/Fit`

- Découpe de la polyligne **aux angles vifs** (> 42°) : sépare le vantail d'une
  porte de son arc de débattement, et décompose un rectangle en quatre côtés.
- Sur chaque morceau : tentative d'**ajustement de cercle** (Kāsa) si la courbure
  est régulière et la flèche significative → *arc de porte* ; sinon
  Douglas-Peucker puis **ajustement de droite par ACP** → segments.
- Les segments issus d'une bande plus épaisse que le seuil `bandThick`
  (≈ 0,6 % de la petite dimension) sont marqués « mur plein » : leur épaisseur est
  fiable et ils deviennent directement des murs.

## 5. Cotes — `vector/DimensionDetector`

Les cotes sont cherchées **avant** les murs, sinon une ligne de cote devient un mur.

Un segment est retenu comme cote s'il porte **deux embouts obliques ou flèches**,
ou bien **une flèche et un texte numérique** à proximité de son milieu.
La valeur est analysée (`10,20 m`, `15m40`, `3,00 m`, `240 cm`) et
`pxPerMeter = médiane(longueur / valeur)` après rejet des valeurs aberrantes.

À défaut de cote, l'échelle peut venir des **surfaces annoncées** (`20 m²`) :
`pxPerMeter = √(aire_pixels / aire_m²)`.

## 6. Murs — `vector/WallBuilder`

1. **Appariement des traits doubles** : deux segments quasi parallèles, se
   recouvrant sur plus de la moitié de leur longueur, écartés de moins de
   `doubleWallMaxGapFrac`, et dont l'écartement reste constant → un mur d'axe
   médian et d'épaisseur égale à l'écartement. Les paires trop larges par rapport
   à l'épaisseur dominante (médiane) sont rejetées : deux murs de part et d'autre
   d'un couloir ne sont pas fusionnés.
2. **Traits simples** restants → murs d'épaisseur provisoire.
3. **Assemblage du graphe** : fusion des extrémités proches, prolongement des
   extrémités libres jusqu'au mur qu'elles visent, découpe des murs traversés
   (jonctions en T), recollement des extrémités libres sur le mur le plus proche,
   **soudure des nœuds confondus**, fusion des murs colinéaires, suppression des
   moignons, dédoublonnage.
4. **Orthogonalisation** facultative : orientation dominante du bâtiment par
   histogramme pondéré, alignement itératif des murs quasi axiaux, puis
   regroupement des coordonnées proches. Désactivée quand
   « Respecter les angles du croquis » est actif.

## 7. Ouvertures — `vector/OpeningDetector`

- **Pontage des percements** : deux extrémités de murs alignées, séparées par un
  vide sans encre continue → insertion d'un **mur virtuel** portant une ouverture.
  C'est ce qui referme le contour des pièces tout en gardant la baie.
- **Fenêtres** : petit tronçon parallèle logé dans l'épaisseur d'un mur
  (rectangle de menuiserie du croquis) → l'ouverture est reclassée en fenêtre.
- **Portes** : chaque arc retenu cherche le mur le plus proche de son centre
  (le gond). Largeur = rayon, position, côté du gond et sens d'ouverture déduits
  de l'orientation de l'arc. Deux vantaux opposés voisins → porte double.
- **Interruptions d'encre** le long d'un mur continu → percement.
- Une fois l'échelle connue, les largeurs passent en mètres et le type est affiné :
  < 1,15 m porte, < 1,9 m porte double, au-delà porte de garage ou baie.

## 8. Pièces — `vector/RoomBuilder`

Extraction des **faces du graphe planaire** : arêtes orientées triées par angle
autour de chaque nœud, parcours de face par rotation. La face d'aire maximale est
l'extérieur (ses murs sont marqués « façade »), les autres deviennent des pièces.
Chaque contour est ensuite **rentré d'une demi-épaisseur de mur** pour donner la
surface utile. Les textes intérieurs fournissent le nom et la surface annoncée.

## 9. Symboles — `vector/SymbolDetector`

- **Familles de traits parallèles équidistants** : nombreux et courts → hachures ;
  assez longs et réguliers → escalier.
- Classement des hachures selon le contexte : dans l'épaisseur d'un mur → isolant ;
  dans une pièce → carrelage ; hors emprise ou pièce extérieure → terrasse.
- Le reste des traits non consommés, regroupé par composante connexe, devient
  mobilier — véhicule dans un garage, sanitaire dans une salle de bain ou un WC.

---

## Réglages exposés dans l'application

| Réglage | Effet | Quand le modifier |
|---|---|---|
| Sensibilité du trait (`binarizeK`) | seuil local | crayon clair, photo sous-exposée |
| Filtrage du quadrillage (`binarizeMargin`) | tolérance / Otsu | papier millimétré visible |
| Soudure des traits repassés (`closingRadius`) | fermeture morphologique | croquis hachuré, traits multiples |
| Seuil mur plein (`bandRadius`) | bande → mur épais | murs noirs ou hachurés mal détectés |
| Longueur minimale d'un mur | filtre | petits murs ignorés / bruit pris pour un mur |
| Tolérance de raccord | soudure du graphe | pièces non fermées |
| Écartement max. d'un mur double | appariement | murs doubles restés séparés |
| Tolérance de parallélisme | appariement | croquis très irrégulier |
| Redressement des angles | orthogonalisation | plan à l'équerre souhaité |
| Rayon max. d'un arc de porte | portes | grandes portes non vues |
| Hauteur max. d'un texte | séparation texte / dessin | gros titres pris pour des murs |
| Résolution d'analyse | finesse / vitesse | croquis très détaillé |

---

## Banc d'essai hors appareil

Le paquet `core` étant indépendant d'Android, on peut le compiler et l'exécuter
avec un simple JDK :

```bash
javac -d build $(find app/src/main/java/com/tracecroquis/core -name '*.java')
```

Il suffit ensuite d'écrire un petit programme qui charge une image avec
`ImageIO`, appelle `Vectorizer.run(pixels, w, h, new VectorOptions(), null, null)`
et dessine le `Plan` obtenu. C'est la méthode qui a servi à régler cette v0.1 sur
les dix croquis d'exemple.
