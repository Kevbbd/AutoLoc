package fr.ece.autoloc.service.compte;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envoi réel, par le serveur SMTP configuré ({@code spring.mail.*}) : Mailpit en local. */
@Component
public class MessagerieSmtp implements Messagerie {

    private final JavaMailSender mailSender;
    private final String expediteur;

    public MessagerieSmtp(JavaMailSender mailSender, @Value("${autoloc.mail.expediteur}") String expediteur) {
        this.mailSender = mailSender;
        this.expediteur = expediteur;
    }

    @Override
    public void envoyer(String destinataire, String sujet, String texte) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(destinataire);
        message.setSubject(sujet);
        message.setText(texte);
        mailSender.send(message);
    }
}
