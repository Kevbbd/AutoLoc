package fr.ece.autoloc.service.compte;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie l'e-mail contenant le lien.
 *
 * <ul>
 *   <li><b>Après la validation de la transaction</b> ({@code @TransactionalEventListener}) : on n'envoie
 *   jamais un lien dont le jeton n'aurait finalement pas été enregistré.</li>
 *   <li><b>En tâche de fond</b> ({@code @Async}) : la réponse HTTP part sans attendre le serveur de
 *   mail. Le temps de réponse est donc le même, que l'adresse corresponde à un compte ou non : on ne
 *   peut pas le mesurer pour deviner quelles adresses existent.</li>
 * </ul>
 */
@Slf4j
@Component
public class EnvoiLienReinitialisation {

    private final Messagerie messagerie;
    private final boolean journaliserLienSiEchec;

    public EnvoiLienReinitialisation(Messagerie messagerie,
            @Value("${autoloc.mot-de-passe.journaliser-lien-si-echec:false}") boolean journaliserLienSiEchec) {
        this.messagerie = messagerie;
        this.journaliserLienSiEchec = journaliserLienSiEchec;
    }

    @Async
    @TransactionalEventListener
    public void envoyer(LienReinitialisationCree evenement) {
        try {
            messagerie.envoyer(evenement.email(), "AutoLoc — Réinitialisation de votre mot de passe", texte(evenement));
            log.info("Lien de réinitialisation envoyé pour le compte « {} »", evenement.compte());
        } catch (RuntimeException e) {
            log.warn("E-mail de réinitialisation non envoyé pour le compte « {} » : {}. "
                    + "Le serveur de mail de test (Mailpit) est-il démarré ? Voir scripts\\monitoring-demarrer.cmd",
                    evenement.compte(), e.getMessage());
            if (journaliserLienSiEchec) {
                // Profil dev uniquement : permet de tester sans Docker. Jamais en production.
                log.warn("[dev] Lien de réinitialisation pour « {} » : {}", evenement.compte(), evenement.lien());
            }
        }
    }

    static String texte(LienReinitialisationCree e) {
        return """
                Bonjour,

                Une réinitialisation du mot de passe du compte « %s » a été demandée sur %s.

                Pour choisir un nouveau mot de passe, ouvrez ce lien :
                %s

                Ce lien est valable %d minutes et ne fonctionne qu'une seule fois.

                Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail : votre mot de passe reste inchangé.

                — AutoLoc
                """.formatted(e.compte(), e.client() ? "le site AutoLoc" : "l'espace agence AutoLoc", e.lien(),
                e.validite().toMinutes());
    }
}
