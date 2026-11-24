package fr.ece.autoloc.service.compte;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * Prévient le titulaire d'un compte que son mot de passe vient d'être changé. Sans cet e-mail, quelqu'un
 * qui aurait accès à sa boîte de réception pourrait changer le mot de passe sans qu'il s'en aperçoive.
 * Envoyé après validation de la transaction et en tâche de fond, comme le lien de réinitialisation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlerteMotDePasseModifie {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final Messagerie messagerie;

    @Async
    @TransactionalEventListener
    public void prevenir(MotDePasseModifie evenement) {
        try {
            messagerie.envoyer(evenement.email(), "AutoLoc — Votre mot de passe a été modifié", texte(evenement));
        } catch (RuntimeException e) {
            log.warn("Alerte de changement de mot de passe non envoyée pour le compte « {} » : {}",
                    evenement.compte(), e.getMessage());
        }
    }

    static String texte(MotDePasseModifie e) {
        return """
                Bonjour,

                Le mot de passe du compte « %s » de %s a été modifié le %s.

                Si c'est vous, il n'y a rien à faire.

                Si ce n'est pas vous, faites immédiatement une demande « Mot de passe oublié » pour reprendre la main
                sur votre compte, et prévenez %s.

                — AutoLoc
                """.formatted(e.compte(), e.client() ? "votre espace client AutoLoc" : "l'espace agence AutoLoc",
                e.le().format(FORMAT), e.client() ? "votre agence AutoLoc" : "votre responsable");
    }
}
