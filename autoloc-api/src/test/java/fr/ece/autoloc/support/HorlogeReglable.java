package fr.ece.autoloc.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Horloge de test qu'on fait avancer à la main : pas d'attente réelle pour tester une expiration. */
public final class HorlogeReglable extends Clock {

    private Instant instant;

    public HorlogeReglable(Instant depart) {
        this.instant = depart;
    }

    public void avancer(Duration duree) {
        instant = instant.plus(duree);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
