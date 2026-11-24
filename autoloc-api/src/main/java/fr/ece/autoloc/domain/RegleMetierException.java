package fr.ece.autoloc.domain;

/**
 * Violation d'une règle métier : l'opération est bien formée mais impossible dans l'état actuel
 * (véhicule déjà réservé, réservation déjà annulée, paiement supérieur au reste dû…).
 * Traduite en HTTP 409 Conflict par la couche web.
 */
public class RegleMetierException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RegleMetierException(String message) {
        super(message);
    }
}
