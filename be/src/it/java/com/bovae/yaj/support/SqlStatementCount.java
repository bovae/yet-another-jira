package com.bovae.yaj.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

public final class SqlStatementCount {

    private SqlStatementCount() {}

    /**
     * Flushes and clears the persistence context so the first-level cache cannot mask reads, resets
     * the statistics, runs the action, and returns the number of prepared statements executed.
     */
    public static long around(EntityManagerFactory emf, EntityManager em, Runnable action) {
        em.flush();
        em.clear();
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        action.run();
        return stats.getPrepareStatementCount();
    }

    public static void assertSingleStatement(EntityManagerFactory emf, EntityManager em, Runnable action) {
        assertEquals(1L, around(emf, em, action), "expected exactly one SQL statement (no per-row lazy loading)");
    }

    public static void assertCountIndependentOfRows(
            EntityManagerFactory emf, EntityManager em, Runnable seedOneMore, Runnable read, int extraRows) {
        long withFew = around(emf, em, read);
        for (int i = 0; i < extraRows; i++) {
            seedOneMore.run();
        }
        long withMany = around(emf, em, read);
        assertEquals(withFew, withMany, "SQL statement count must not grow with the number of seeded rows");
    }
}
