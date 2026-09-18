package com.tracecroquis.core.model;

/** Types metier du plan (vocabulaire batiment francais). */
public final class Enums {

    /** Nature d'un element lineaire. */
    public enum WallType {
        VOILE_EXT("Voile ext.", 0.20),
        VOILE("Voile", 0.16),
        MUR_PORTEUR("Mur porteur", 0.20),
        CLOISON("Cloison", 0.07),
        CLOISON_DOUBLAGE("Doublage", 0.10),
        GARDE_CORPS("Garde-corps", 0.05),
        LIMITE("Limite / projection", 0.02);

        public final String label;
        public final double defaultThickness;

        WallType(String label, double defaultThickness) {
            this.label = label;
            this.defaultThickness = defaultThickness;
        }
    }

    /** Nature d'une ouverture dans un mur. */
    public enum OpeningType {
        PORTE("Porte", 0.83),
        PORTE_DOUBLE("Porte double", 1.40),
        PORTE_COULISSANTE("Porte coulissante", 0.90),
        PORTE_GARAGE("Porte de garage", 2.40),
        FENETRE("Fenetre", 1.20),
        BAIE("Baie vitree", 2.40),
        PASSAGE("Passage libre", 0.90);

        public final String label;
        public final double defaultWidth;

        OpeningType(String label, double defaultWidth) {
            this.label = label;
            this.defaultWidth = defaultWidth;
        }

        public boolean isDoor() {
            return this == PORTE || this == PORTE_DOUBLE || this == PORTE_COULISSANTE || this == PORTE_GARAGE;
        }

        public boolean isWindow() {
            return this == FENETRE || this == BAIE;
        }
    }

    /** Symboles et remplissages detectes dans le croquis. */
    public enum SymbolType {
        ESCALIER("Escalier"),
        MOBILIER("Mobilier"),
        SANITAIRE("Sanitaire"),
        VEHICULE("Vehicule"),
        CARRELAGE("Carrelage"),
        TERRASSE("Terrasse"),
        ISOLANT("Isolant"),
        HACHURE("Hachure"),
        CHEMINEE("Cheminee"),
        NORD("Nord"),
        INDETERMINE("Indetermine");

        public final String label;

        SymbolType(String label) {
            this.label = label;
        }
    }

    /** Role d'un texte reconnu. */
    public enum TextRole {
        NOM_PIECE("Nom de piece"),
        SURFACE("Surface"),
        COTE("Cote"),
        TITRE("Titre"),
        LIBRE("Texte libre");

        public final String label;

        TextRole(String label) {
            this.label = label;
        }
    }

    private Enums() {
    }
}
