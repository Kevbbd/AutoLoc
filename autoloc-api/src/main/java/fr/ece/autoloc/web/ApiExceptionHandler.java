package fr.ece.autoloc.web;

import fr.ece.autoloc.domain.RegleMetierException;
import fr.ece.autoloc.service.exception.RequeteInvalideException;
import fr.ece.autoloc.service.exception.RessourceIntrouvableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Toutes les erreurs de l'API au format RFC 9457 (application/problem+json).
 * La propriété {@code erreurs} associe chaque champ fautif à son message, pour l'afficher sous le bon champ.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> erreurs = new LinkedHashMap<>();
        for (FieldError erreur : ex.getBindingResult().getFieldErrors()) {
            erreurs.putIfAbsent(erreur.getField(), erreur.getDefaultMessage());
        }
        ProblemDetail probleme = probleme(HttpStatus.BAD_REQUEST, "Données invalides", "Certains champs sont invalides.");
        probleme.setProperty("erreurs", erreurs);
        return handleExceptionInternal(ex, probleme, headers, status, request);
    }

    @ExceptionHandler(RequeteInvalideException.class)
    public ProblemDetail requeteInvalide(RequeteInvalideException ex) {
        ProblemDetail probleme = probleme(HttpStatus.BAD_REQUEST, "Données invalides", ex.getMessage());
        if (ex.getChamp() != null) {
            probleme.setProperty("erreurs", Map.of(ex.getChamp(), ex.getMessage()));
        }
        return probleme;
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ProblemDetail introuvable(RessourceIntrouvableException ex) {
        return probleme(HttpStatus.NOT_FOUND, "Introuvable", ex.getMessage());
    }

    @ExceptionHandler(RegleMetierException.class)
    public ProblemDetail regleMetier(RegleMetierException ex) {
        return probleme(HttpStatus.CONFLICT, "Opération impossible", ex.getMessage());
    }

    /** Deux demandes simultanées ont visé la même ressource : l'une gagne, l'autre est invitée à réessayer. */
    @ExceptionHandler({PessimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ProblemDetail conflitConcurrent(RuntimeException ex) {
        logger.warn("Conflit d'accès concurrent : " + ex.getMessage());
        return probleme(HttpStatus.CONFLICT, "Opération impossible",
                "Une autre demande portant sur les mêmes données vient d'être traitée. Réessayez.");
    }

    private static ProblemDetail probleme(HttpStatus statut, String titre, String detail) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(statut, detail);
        probleme.setTitle(titre);
        return probleme;
    }
}
