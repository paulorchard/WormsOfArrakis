package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Splits players into groups by distance. Players are taken from the highest score down; each one not yet in a group
 * starts a group made of every unassigned player within the radius of them. Horizontal distance only.
 * Cost is O(n squared) distance checks per call, which is nothing at 20 players and still small at a few hundred;
 * a spatial hash would be the next step beyond that.
 */
final class GroupFinder {

    /** One candidate player. {@code recency} is higher the more recently the player gained aggro. */
    record Member(UUID id, double x, double z, double score, long recency) {
    }

    private GroupFinder() {
    }

    /** Highest score first; a tie goes to whoever gained aggro most recently. */
    static final Comparator<Member> RANKING =
            Comparator.comparingDouble(Member::score).reversed().thenComparing(Comparator.comparingLong(Member::recency).reversed());

    /** Each group is sorted with {@link #RANKING}. */
    static List<List<Member>> find(List<Member> members, double radius) {
        List<Member> sorted = new ArrayList<>(members);
        sorted.sort(RANKING);
        boolean[] taken = new boolean[sorted.size()];
        double radiusSquared = radius * radius;
        List<List<Member>> groups = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (taken[i]) {
                continue;
            }
            Member seed = sorted.get(i);
            List<Member> group = new ArrayList<>();
            for (int j = i; j < sorted.size(); j++) {
                if (taken[j]) {
                    continue;
                }
                Member other = sorted.get(j);
                double dx = other.x() - seed.x();
                double dz = other.z() - seed.z();
                if (dx * dx + dz * dz <= radiusSquared) {
                    taken[j] = true;
                    group.add(other);
                }
            }
            groups.add(group);
        }
        return groups;
    }

    static double total(List<Member> group) {
        double sum = 0;
        for (Member m : group) {
            sum += m.score();
        }
        return sum;
    }
}
