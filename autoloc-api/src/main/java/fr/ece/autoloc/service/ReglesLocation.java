package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Periode;
import fr.ece.autoloc.service.exception.RequeteInvalideException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Règles de location partagées par la vitrine et la réservation.
 * L'horloge est injectée : les tests peuvent fixer « aujourd'hui ».
 */
@Component
@RequiredArgsConstructor
public class ReglesLocation {

    public static final int DUREE_MAX_JOURS = 30;
    public static final int DELAI_MAX_RESERVATION_JOURS = 365;

    private final Clock clock;

    public LocalDate aujourdhui() {
        return LocalDate.now(clock);
    }

    public LocalDateTime maintenant() {
        return LocalDateTime.now(clock);
    }

    /** Valide une période demandée par un client et la convertit en {@link Periode}. */
    public Periode periodeReservable(LocalDate debut, LocalDate fin) {
        if (debut == null) {
            throw new RequeteInvalideException("dateDebut", "La date de départ est obligatoire.");
        }
        if (fin == null) {
            throw new RequeteInvalideException("dateFin", "La date de retour est obligatoire.");
        }
        if (!fin.isAfter(debut)) {
            throw new RequeteInvalideException("dateFin", "La date de retour doit être postérieure à la date de départ.");
        }
        LocalDate aujourdhui = aujourdhui();
        if (debut.isBefore(aujourdhui)) {
            throw new RequeteInvalideException("dateDebut", "La date de départ ne peut pas être dans le passé.");
        }
        if (debut.isAfter(aujourdhui.plusDays(DELAI_MAX_RESERVATION_JOURS))) {
            throw new RequeteInvalideException("dateDebut",
                    "Les réservations sont ouvertes " + DELAI_MAX_RESERVATION_JOURS + " jours à l'avance au maximum.");
        }
        Periode periode = new Periode(debut, fin);
        if (periode.jours() > DUREE_MAX_JOURS) {
            throw new RequeteInvalideException("dateFin",
                    "La durée d'une location est limitée à " + DUREE_MAX_JOURS + " jours.");
        }
        return periode;
    }

    /** Montant = tarif journalier × nombre de jours, arrondi au centime. */
    public BigDecimal montant(BigDecimal tarifJournalier, Periode periode) {
        return tarifJournalier.multiply(BigDecimal.valueOf(periode.jours())).setScale(2, RoundingMode.HALF_UP);
    }
}
