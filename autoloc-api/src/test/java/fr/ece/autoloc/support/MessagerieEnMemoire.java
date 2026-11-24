package fr.ece.autoloc.support;

import fr.ece.autoloc.service.compte.Messagerie;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Boîte de réception des tests : remplace l'envoi SMTP (profil « test ») pour lire les e-mails
 * envoyés. Les envois étant asynchrones, la lecture attend leur arrivée, avec un délai maximal.
 */
@Component
@Primary
@Profile("test")
public class MessagerieEnMemoire implements Messagerie {

    public record Email(String destinataire, String sujet, String texte) {
    }

    private final BlockingQueue<Email> boite = new LinkedBlockingQueue<>();

    @Override
    public void envoyer(String destinataire, String sujet, String texte) {
        boite.add(new Email(destinataire, sujet, texte));
    }

    public void vider() {
        boite.clear();
    }

    /** Attend le prochain e-mail ; échoue s'il n'arrive pas à temps. */
    public Email attendre(Duration delai) throws InterruptedException {
        Email email = boite.poll(delai.toMillis(), TimeUnit.MILLISECONDS);
        if (email == null) {
            throw new AssertionError("Aucun e-mail reçu en " + delai.toSeconds() + " s");
        }
        return email;
    }

    /** Laisse aux envois en cours le temps d'arriver, puis renvoie tout ce qui a été reçu. */
    public List<Email> recusApres(Duration attente) throws InterruptedException {
        Thread.sleep(attente.toMillis());
        List<Email> recus = new ArrayList<>();
        boite.drainTo(recus);
        return recus;
    }
}
