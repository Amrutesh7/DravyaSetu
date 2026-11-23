package com.dravya.setu.plants;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Conservative typo fallback for the small plant catalogue. Never changes plant identity. */
final class PlantSearch {
    private static final int MAX_RESULTS = 5;
    private static final double MIN_SIMILARITY = 0.75;

    private PlantSearch() { }

    static boolean isFuzzyQuery(String term) {
        String query = normalize(term);
        // IDs must not be corrected into a different plant's identity.
        return !term.contains("%") && query.codePointCount(0, query.length()) >= 4
                && !query.matches("plant(?:\\s.*|\\d+)?");
    }

    static List<Plant> similarPlants(List<Plant> plants, String term) {
        if (!isFuzzyQuery(term)) return List.of();
        String query = normalize(term);
        int[] letters = query.codePoints().toArray();
        int maxEdits = letters.length >= 8 ? 2 : 1;
        int words = query.split(" ").length;
        List<Match> matches = new ArrayList<>();

        for (Plant plant : plants) {
            double best = 0;
            int bestSource = Integer.MAX_VALUE;
            for (var name : comparisonNames(plant, words).entrySet()) {
                int[] candidate = name.getKey().codePoints().toArray();
                if (Math.abs(candidate.length - letters.length) > maxEdits) continue;
                int distance = editDistance(letters, candidate, maxEdits);
                if (distance > maxEdits) continue;
                double similarity = 1.0 - (double) distance / Math.max(letters.length, candidate.length);
                if (similarity > best || similarity == best && name.getValue() < bestSource) {
                    best = similarity;
                    bestSource = name.getValue();
                }
            }
            if (best >= MIN_SIMILARITY) matches.add(new Match(plant, best, bestSource));
        }
        return matches.stream()
                .sorted(Comparator.comparingDouble(Match::similarity).reversed()
                        .thenComparingInt(Match::sourcePriority)
                        .thenComparing(match -> match.plant().getPlantCode()))
                .limit(MAX_RESULTS).map(Match::plant).toList();
    }

    private static Map<String, Integer> comparisonNames(Plant plant, int queryWords) {
        List<String> names = new ArrayList<>();
        names.add(plant.getPlantIdentifier());
        names.add(plant.getCommonName());
        if (plant.getAyurvedicRegionalNames() != null) {
            names.addAll(Arrays.asList(plant.getAyurvedicRegionalNames()));
        }
        Map<String, Integer> comparisons = new LinkedHashMap<>();
        for (String name : names) addName(comparisons, name, queryWords, 0);
        addName(comparisons, plant.getScientificName(), queryWords, 1);
        return comparisons;
    }

    private static void addName(Map<String, Integer> comparisons, String name, int queryWords, int priority) {
        if (name == null || name.isBlank()) return;
        String normalized = normalize(name);
        if (normalized.isEmpty()) return;
        comparisons.merge(normalized, priority, Math::min);
        String[] words = normalized.split(" ");
        // Include nearby word groups when a scientific name has a botanical author suffix.
        for (int width = Math.max(1, queryWords - 1); width <= queryWords + 1; width++) {
            for (int start = 0; start + width <= words.length; start++) {
                comparisons.merge(String.join(" ", Arrays.copyOfRange(words, start, start + width)), priority, Math::min);
            }
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ").strip();
    }

    /** Banded optimal-string-alignment distance: insertion, deletion, substitution or adjacent swap. */
    private static int editDistance(int[] left, int[] right, int limit) {
        int[][] distance = new int[left.length + 1][right.length + 1];
        for (int[] row : distance) Arrays.fill(row, limit + 1);
        for (int i = 0; i <= left.length; i++) distance[i][0] = i;
        for (int j = 0; j <= right.length; j++) distance[0][j] = j;
        for (int i = 1; i <= left.length; i++) {
            for (int j = Math.max(1, i - limit); j <= Math.min(right.length, i + limit); j++) {
                int substitution = distance[i - 1][j - 1] + (left[i - 1] == right[j - 1] ? 0 : 1);
                distance[i][j] = Math.min(substitution,
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1));
                if (i > 1 && j > 1 && left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1]) {
                    distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + 1);
                }
            }
        }
        return distance[left.length][right.length];
    }

    private record Match(Plant plant, double similarity, int sourcePriority) { }
}
