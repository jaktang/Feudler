package edu.brown.cs.termproject.scoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

/**
 * A singleton used to get the optimal threshold for Suggestions.
 *
 * @author asekula
 */
final class SuggestionThresholdFinder {

  public static void main(String[] args) {
    System.out.println(getOptimalThreshold("data/cluster_these.txt",
        "data/dont_cluster_these.txt"));
  }

  static void printSimilarities(String pathToGroups) {

    List<Pair<Double, Set<Suggestion>>> groups = makeGroups(pathToGroups);

    for (Pair<Double, Set<Suggestion>> group : groups) {
      List<Suggestion> words = new ArrayList<>(group.getRight());

      for (int i = 0; i < words.size(); i++) {
        for (int j = i + 1; j < words.size(); j++) {
          System.out.println(
              words.get(i).getResponse() + "/" + words.get(j).getResponse()
                  + ": " + words.get(i).similarity(words.get(j)));
        }
      }
    }
  }

  /**
   * Gets the best threshold for similarity, as a value between 0 and 1.
   *
   * @param pathToCluster
   *          the path to the file containing the desired clusterings and their
   *          weights
   * @param pathToDontCluster
   *          the path to the file containing the undesired clusterings and
   *          their weights
   * @return the threshold that minimizes the loss, as a tuple: (threshold,
   *         score)
   */
  static Pair<Double, Double> getOptimalThreshold(String pathToCluster,
      String pathToDontCluster) {

    List<Pair<Double, Set<Suggestion>>> goodGroups = makeGroups(pathToCluster);
    List<Pair<Double, Set<Suggestion>>> badGroups = makeGroups(
        pathToDontCluster);

    // threshold, score
    Pair<Double, Double> best = Pair.of(0.0, 0.0);

    for (double threshold = 0; threshold <= 1.0; threshold += 0.01) {
      double good = getAccuracy(goodGroups, threshold);
      double bad = getAccuracy(badGroups, threshold);
      double score = (good + (1 - bad)) / 2;

      System.out.println(bad);

      if (score > best.getRight()) {
        best = Pair.of(threshold, score);
      }
    }

    return best;
  }

  /*
   * Converts the text file into a List of tuples, where each tuple contains the
   * priority and a set of strings.
   */
  private static List<Pair<Double, Set<Suggestion>>> makeGroups(String path) {

    List<Pair<Double, Set<Suggestion>>> groups = new ArrayList<>();
    boolean justSawDash = true;
    Set<Suggestion> currentSet = null;

    try {
      Iterator<String> lines = Files.lines(Paths.get(path)).iterator();

      while (lines.hasNext()) {
        String line = lines.next();

        if (justSawDash) {
          currentSet = new HashSet<>();
          groups.add(Pair.of(Double.parseDouble(line), currentSet));
          justSawDash = false;
        } else if (line.charAt(0) == '-') {
          justSawDash = true;
        } else {
          currentSet
              .add(new Suggestion(Word2VecModel.model.tokenize(line), line, 1));
        }
      }
    } catch (IOException ex) {
      throw new RuntimeException("Unable to read file at " + path);
    }

    return groups;
  }

  /*
   * Gets the accuracy of the groups given the Suggestion similarity and the
   * threshold.
   */
  private static double getAccuracy(List<Pair<Double, Set<Suggestion>>> groups,
      double threshold) {

    double total = 0;
    double correct = 0;

    for (Pair<Double, Set<Suggestion>> group : groups) {
      double priority = group.getLeft();
      List<Suggestion> words = new ArrayList<>(group.getRight());

      for (int i = 0; i < words.size(); i++) {
        for (int j = i + 1; j < words.size(); j++) {
          if (words.get(i).similarity(words.get(j)) >= threshold) {
            correct += priority;
            if (Math.abs(threshold - 1.4) < 0.01) {
              System.out
                  .println("Clustered: " + words.get(i) + ":" + words.get(j));
            }
          } else {
            if (Math.abs(threshold - 1.4) < 0.01) {
              System.out.println(
                  "Didn't cluster: " + words.get(i) + ":" + words.get(j));
            }
          }
          total += priority;
        }
      }
    }

    return total == 0 ? 0 : correct / total;
  }
}
