package fr.ece.autoloc.securite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compte les échecs par clé (identifiant ou adresse IP) : au {@code maximum}-ième échec dans la
 * {@code fenetre}, la clé est bloquée pendant {@code blocage}. Un succès remet la clé à zéro.
 *
 * <p>En mémoire : suffisant pour une instance. Plusieurs instances demanderaient un stockage partagé (Redis).
 * Le nombre de clés suivies est borné pour qu'un attaquant ne puisse pas épuiser la mémoire.
 */
public final class CompteurEchecs {

    static final int CLES_MAX = 10_000;

    private record Etat(int echecs, Instant debut, Instant bloqueJusqua) {
    }

    private final ConcurrentHashMap<String, Etat> etats = new ConcurrentHashMap<>();
    private final int maximum;
    private final Duration fenetre;
    private final Duration blocage;
    private final Clock horloge;

    public CompteurEchecs(int maximum, Duration fenetre, Duration blocage, Clock horloge) {
        if (maximum < 1) {
            throw new IllegalArgumentException("maximum doit être au moins 1");
        }
        this.maximum = maximum;
        this.fenetre = fenetre;
        this.blocage = blocage;
        this.horloge = horloge;
    }

    /** Temps restant si la clé est bloquée. */
    public Optional<Duration> blocageRestant(String cle) {
        Etat etat = etats.get(cle);
        Instant maintenant = horloge.instant();
        if (etat == null || etat.bloqueJusqua() == null || !maintenant.isBefore(etat.bloqueJusqua())) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(maintenant, etat.bloqueJusqua()));
    }

    /** Enregistre un échec ; renvoie {@code true} si c'est cet échec qui déclenche le blocage. */
    public boolean echec(String cle) {
        Instant maintenant = horloge.instant();
        boolean[] declenche = {false};
        // compute() est atomique par clé : deux échecs simultanés sont bien comptés deux fois.
        etats.compute(cle, (k, actuel) -> {
            if (actuel != null && actuel.bloqueJusqua() != null && maintenant.isBefore(actuel.bloqueJusqua())) {
                return actuel;
            }
            boolean nouvelleFenetre = actuel == null || actuel.debut() == null
                    || !maintenant.isBefore(actuel.debut().plus(fenetre));
            int echecs = (nouvelleFenetre ? 0 : actuel.echecs()) + 1;
            if (echecs >= maximum) {
                declenche[0] = true;
                return new Etat(echecs, null, maintenant.plus(blocage));
            }
            return new Etat(echecs, nouvelleFenetre ? maintenant : actuel.debut(), null);
        });
        if (etats.size() > CLES_MAX) {
            etats.entrySet().removeIf(entree -> estOublie(entree.getValue(), maintenant));
        }
        return declenche[0];
    }

    public void reussite(String cle) {
        etats.remove(cle);
    }

    int clesSuivies() {
        return etats.size();
    }

    private boolean estOublie(Etat etat, Instant maintenant) {
        boolean bloque = etat.bloqueJusqua() != null && maintenant.isBefore(etat.bloqueJusqua());
        boolean fenetreOuverte = etat.debut() != null && maintenant.isBefore(etat.debut().plus(fenetre));
        return !bloque && !fenetreOuverte;
    }
}
