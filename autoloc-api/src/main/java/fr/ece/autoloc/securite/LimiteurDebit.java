package fr.ece.autoloc.securite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Au plus {@code maximum} requêtes par clé (adresse IP) et par fenêtre fixe de {@code duree}.
 * Au-delà, renvoie le temps à attendre avant la fenêtre suivante.
 */
public final class LimiteurDebit {

    static final int CLES_MAX = 10_000;

    private record Fenetre(Instant debut, int requetes) {
    }

    private final ConcurrentHashMap<String, Fenetre> fenetres = new ConcurrentHashMap<>();
    private final int maximum;
    private final Duration duree;
    private final Clock horloge;

    public LimiteurDebit(int maximum, Duration duree, Clock horloge) {
        if (maximum < 1) {
            throw new IllegalArgumentException("maximum doit être au moins 1");
        }
        this.maximum = maximum;
        this.duree = duree;
        this.horloge = horloge;
    }

    /** Compte la requête ; vide si elle est acceptée, sinon le temps à attendre. */
    public Optional<Duration> enregistrer(String cle) {
        Instant maintenant = horloge.instant();
        Fenetre fenetre = fenetres.compute(cle, (k, actuelle) ->
                actuelle == null || !maintenant.isBefore(actuelle.debut().plus(duree))
                        ? new Fenetre(maintenant, 1)
                        : new Fenetre(actuelle.debut(), actuelle.requetes() + 1));
        if (fenetres.size() > CLES_MAX) {
            fenetres.entrySet().removeIf(entree -> !maintenant.isBefore(entree.getValue().debut().plus(duree)));
        }
        return fenetre.requetes() > maximum
                ? Optional.of(Duration.between(maintenant, fenetre.debut().plus(duree)))
                : Optional.empty();
    }
}
