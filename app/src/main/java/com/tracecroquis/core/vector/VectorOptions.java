package com.tracecroquis.core.vector;

/**
 * Parametres de la vectorisation.
 *
 * <p>Toutes les valeurs "Frac" sont des fractions de la plus petite dimension
 * de l'image analysee : les reglages restent valables quelle que soit la
 * resolution du croquis.</p>
 */
public class VectorOptions {

    // --- pretraitement ---------------------------------------------------
    /** Cote maximal de l'image analysee (px). */
    public int maxDimension = 1500;
    /** Sensibilite de la binarisation locale (0,12 a 0,40). */
    public double binarizeK = 0.22;
    /** Demi-fenetre de binarisation (0 = automatique). */
    public int binarizeWindow = 0;
    /** Tolerance au-dessus du seuil global (petit = quadrillage mieux filtre). */
    public int binarizeMargin = 30;
    /** Surface minimale d'une tache conservee (px). */
    public int minComponentArea = 10;
    /** Suppression des lignes de quadrillage traversant la feuille. */
    public boolean removeGrid = true;
    /** Fermeture morphologique (soude les traits repasses) ; -1 = automatique. */
    public int closingRadius = -1;
    /** Longueur maximale d'une barbule elaguee, en multiples de l'epaisseur du trait. */
    public double pruneFactor = 2.2;
    /** Taux de remplissage a partir duquel l'encre est vue comme un mur plein. */
    public double bandDensity = 0.45;
    /** Rayon d'analyse de densite ; -1 = automatique. */
    public int bandRadius = -1;

    // --- elements recherches ---------------------------------------------
    public boolean detectWalls = true;
    public boolean detectOpenings = true;
    public boolean detectRooms = true;
    public boolean detectDims = true;
    public boolean detectFurniture = true;
    public boolean detectTextures = true;

    // --- murs -------------------------------------------------------------
    /** Longueur minimale d'un mur, en fraction de la petite dimension. */
    public double minWallLenFrac = 0.035;
    /** Tolerance de fusion des extremites. */
    public double snapTolFrac = 0.013;
    /** Distance maximale de prolongement d'un mur pour rejoindre un voisin. */
    public double extendFrac = 0.05;
    /** Ecartement maximal de deux traits formant un mur double. */
    public double doubleWallMaxGapFrac = 0.035;
    /** Ecart angulaire maximal entre deux traits apparies (degres). */
    public double parallelTolDeg = 9;
    /** Conserver les angles du croquis (false = orthogonalisation). */
    public boolean keepAngles = true;
    /** Tolerance d'alignement sur les axes lors de l'orthogonalisation (degres). */
    public double angleSnapDeg = 14;
    /** Epaisseur par defaut d'un mur exterieur (m) si l'echelle est inconnue. */
    public double defaultExteriorWallM = 0.20;
    /** Epaisseur par defaut d'une cloison (m). */
    public double defaultPartitionM = 0.07;

    // --- ouvertures --------------------------------------------------------
    /** Rayon minimal d'un arc de porte (fraction). */
    public double doorRadiusMinFrac = 0.012;
    /** Rayon maximal d'un arc de porte (fraction). */
    public double doorRadiusMaxFrac = 0.16;
    /** Taux d'encre en dessous duquel on considere un percement. */
    public double openingInkRatio = 0.45;
    /** Largeur minimale d'un percement detecte (fraction). */
    public double openingMinWidthFrac = 0.012;

    // --- textes -------------------------------------------------------------
    /** Hauteur maximale d'un caractere (fraction). */
    public double textMaxHeightFrac = 0.055;
    /** Hauteur minimale d'un caractere (fraction). */
    public double textMinHeightFrac = 0.006;
    /** Distance de regroupement des caracteres en ligne de texte (x hauteur). */
    public double textGroupFactor = 1.6;

    // --- symboles ------------------------------------------------------------
    /** Nombre minimal de marches pour reconnaitre un escalier. */
    public int stairMinSteps = 3;
    /** Espacement maximal des hachures (fraction). */
    public double hatchMaxSpacingFrac = 0.03;

    public VectorOptions copy() {
        VectorOptions o = new VectorOptions();
        o.maxDimension = maxDimension;
        o.binarizeK = binarizeK; o.binarizeWindow = binarizeWindow; o.binarizeMargin = binarizeMargin;
        o.minComponentArea = minComponentArea; o.removeGrid = removeGrid;
        o.closingRadius = closingRadius; o.pruneFactor = pruneFactor;
        o.bandDensity = bandDensity; o.bandRadius = bandRadius;
        o.detectWalls = detectWalls; o.detectOpenings = detectOpenings; o.detectRooms = detectRooms;
        o.detectDims = detectDims; o.detectFurniture = detectFurniture; o.detectTextures = detectTextures;
        o.minWallLenFrac = minWallLenFrac; o.snapTolFrac = snapTolFrac; o.extendFrac = extendFrac;
        o.doubleWallMaxGapFrac = doubleWallMaxGapFrac; o.parallelTolDeg = parallelTolDeg;
        o.keepAngles = keepAngles; o.angleSnapDeg = angleSnapDeg;
        o.defaultExteriorWallM = defaultExteriorWallM; o.defaultPartitionM = defaultPartitionM;
        o.doorRadiusMinFrac = doorRadiusMinFrac; o.doorRadiusMaxFrac = doorRadiusMaxFrac;
        o.openingInkRatio = openingInkRatio; o.openingMinWidthFrac = openingMinWidthFrac;
        o.textMaxHeightFrac = textMaxHeightFrac; o.textMinHeightFrac = textMinHeightFrac;
        o.textGroupFactor = textGroupFactor;
        o.stairMinSteps = stairMinSteps; o.hatchMaxSpacingFrac = hatchMaxSpacingFrac;
        return o;
    }
}
