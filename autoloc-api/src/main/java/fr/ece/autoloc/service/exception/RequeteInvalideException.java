package fr.ece.autoloc.service.exception;

/** Données syntaxiquement correctes mais incohérentes (dates inversées, période trop longue…) : HTTP 400. */
public class RequeteInvalideException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String champ;

    public RequeteInvalideException(String champ, String message) {
        super(message);
        this.champ = champ;
    }

    public String getChamp() {
        return champ;
    }
}
