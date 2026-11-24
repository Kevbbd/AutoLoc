package fr.ece.autoloc.domain;

import java.util.EnumSet;
import java.util.Set;

public enum StatutReservation {
    EN_ATTENTE("en attente"),
    CONFIRMEE("confirmée"),
    ANNULEE("annulée"),
    TERMINEE("terminée");

    /** Statuts qui bloquent le véhicule sur la période réservée. */
    public static final Set<StatutReservation> ACTIFS = EnumSet.of(EN_ATTENTE, CONFIRMEE);

    private final String libelle;

    StatutReservation(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    public boolean estActif() {
        return ACTIFS.contains(this);
    }
}
