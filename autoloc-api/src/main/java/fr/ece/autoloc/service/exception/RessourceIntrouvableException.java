package fr.ece.autoloc.service.exception;

/** La ressource demandée n'existe pas (ou n'est pas visible pour l'appelant) : HTTP 404. */
public class RessourceIntrouvableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
