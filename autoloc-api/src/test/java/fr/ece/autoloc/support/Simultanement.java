package fr.ece.autoloc.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/** Lance plusieurs appels exactement au même instant (barrière de départ) pour provoquer les collisions. */
public final class Simultanement {

    private Simultanement() {
    }

    public static List<Integer> executer(int nombre, IntFunction<Integer> appel) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(nombre);
        try {
            CountDownLatch depart = new CountDownLatch(1);
            List<Future<Integer>> resultats = new ArrayList<>();
            for (int i = 0; i < nombre; i++) {
                int n = i;
                resultats.add(pool.submit(() -> {
                    depart.await();
                    return appel.apply(n);
                }));
            }
            depart.countDown();
            List<Integer> statuts = new ArrayList<>();
            for (Future<Integer> resultat : resultats) {
                statuts.add(resultat.get(60, TimeUnit.SECONDS));
            }
            return statuts;
        } finally {
            pool.shutdownNow();
        }
    }
}
