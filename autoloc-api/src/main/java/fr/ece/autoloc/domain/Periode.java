package fr.ece.autoloc.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Période de location semi-ouverte [debut, fin) : le véhicule part le jour {@code debut}
 * et revient le jour {@code fin}, où il peut repartir pour une nouvelle location.
 * Le nombre de jours facturés est donc {@code fin - debut} (au moins 1).
 */
public record Periode(LocalDate debut, LocalDate fin) {

    public Periode {
        Objects.requireNonNull(debut, "debut");
        Objects.requireNonNull(fin, "fin");
        if (!fin.isAfter(debut)) {
            throw new IllegalArgumentException("La fin doit être postérieure au début : " + debut + " → " + fin);
        }
    }

    public long jours() {
        return ChronoUnit.DAYS.between(debut, fin);
    }

    /** Deux périodes semi-ouvertes se chevauchent si et seulement si chacune commence avant la fin de l'autre. */
    public boolean chevauche(Periode autre) {
        return debut.isBefore(autre.fin) && autre.debut.isBefore(fin);
    }

    public boolean contient(LocalDate jour) {
        return !jour.isBefore(debut) && jour.isBefore(fin);
    }
}
