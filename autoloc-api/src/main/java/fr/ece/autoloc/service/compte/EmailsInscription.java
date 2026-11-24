package fr.ece.autoloc.service.compte;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * E-mails de l'inscription, envoyés après validation de la transaction et en tâche de fond : la réponse
 * HTTP part tout de suite et prend le même temps, que l'adresse ait déjà un compte ou non.
 */
@Slf4j
@Component
public class EmailsInscription {

    private final Messagerie messagerie;
    private final boolean journaliserLienSiEchec;

    public EmailsInscription(Messagerie messagerie,
            @Value("${autoloc.mot-de-passe.journaliser-lien-si-echec:false}") boolean journaliserLienSiEchec) {
        this.messagerie = messagerie;
        this.journaliserLienSiEchec = journaliserLienSiEchec;
    }

    @Async
    @TransactionalEventListener
    public void confirmerAdresse(LienInscriptionCree evenement) {
        try {
            messagerie.envoyer(evenement.email(), "AutoLoc — Confirmez votre adresse pour créer votre compte", """
                    Bonjour,

                    Pour créer votre compte AutoLoc, confirmez votre adresse e-mail en ouvrant ce lien :
                    %s

                    Vous choisirez ensuite votre mot de passe. Ce lien est valable %d heures et ne fonctionne qu'une seule fois.

                    Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail : aucun compte ne sera créé.

                    — AutoLoc
                    """.formatted(evenement.lien(), evenement.validite().toHours()));
        } catch (RuntimeException e) {
            log.warn("E-mail de confirmation d'inscription non envoyé : {}. Mailpit est-il démarré ?", e.getMessage());
            if (journaliserLienSiEchec) {
                log.warn("[dev] Lien de confirmation d'inscription : {}", evenement.lien());
            }
        }
    }

    @Async
    @TransactionalEventListener
    public void signalerCompteExistant(CompteDejaExistant evenement) {
        try {
            messagerie.envoyer(evenement.email(), "AutoLoc — Vous avez déjà un compte", """
                    Bonjour,

                    Une création de compte AutoLoc a été demandée avec cette adresse, mais vous avez déjà un compte.

                    Connectez-vous avec cette adresse e-mail depuis le site, rubrique « Mon compte ».
                    Mot de passe oublié ? Utilisez le lien « Mot de passe oublié » de la page de connexion.

                    Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail : rien n'a été modifié.

                    — AutoLoc
                    """);
        } catch (RuntimeException e) {
            log.warn("E-mail « compte déjà existant » non envoyé : {}", e.getMessage());
        }
    }
}
