package edu.brown.cs.termproject.scoring;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Represents a set of clusters for either a set of suggestions or a set of
 * guesses. Used to group suggestions during the game that have roughly the same
 * meaning, and used to group guesses for meta-mode that also have roughly the
 * same meaning. This class is very not immutable -- as phrases get added, the
 * clusters inside the clustering change, and more clusters may be created.
 * Example of how to use this: Given a list of Strings representing phrases and
 * an EmbeddingCluster factory, do new Clustering[EmbeddingCluster](list,
 * factory) and that will create a clustering of Embedding clusters. Then to
 * check if a word is in a cluster, call .clusterOf(word) and it will return
 * Optional.of(cluster) if it's in a cluster, and absent otherwise.
 *
 * @author asekula
 *
 * @param <T>
 *          the type of the cluster, either WordCluster or EmbeddingCluster
 */
public class Clustering<T extends Cluster> {

  private List<T> clusters; // Note that the ordering matters here.
  private Word2VecModel model;
  private ClusterFactory<T> factory;
  private int total;

  /**
   * Factory pattern for constructing a suggestion clustering. Note that model
   * is last, because that's the constructor we want to use.
   *
   * @param answers
   *          the suggestions that google returns, as a list of strings
   * @param model
   *          the word2vec model
   * @return a clustering of the suggestions
   */
  public static Clustering<Suggestion> newSuggestionClustering(
      List<String> answers, Word2VecModel model) {
    return new Clustering<Suggestion>(answers, new SuggestionFactory(), model);
  }

  /**
   * Makes a suggestion clustering out of a pre-existing suggestion clustering.
   * Does not bother with comparing the suggestions with each other. Still
   * tokenizes and vectorizes the suggestions for use when comparing with
   * guesses.
   *
   * @param responseScores
   *          a list of tuples, where the first item is the string of the
   *          suggestion, the second item is the score
   * @param model
   *          the word2vec model
   * @return a clustering of suggestions
   */
  public static Clustering<Suggestion> newExistingSuggestionClustering(
      List<Pair<String, Integer>> responseScores, Word2VecModel model) {
    List<Suggestion> suggestions = responseScores.stream()
        .map((Pair<String, Integer> p) -> new Suggestion(
            model.tokenize(p.getLeft()), p.getLeft(), p.getRight()))
        .collect(Collectors.toList());
    return new Clustering<Suggestion>(suggestions, model,
        new SuggestionFactory());
  }

  public static Clustering<Suggestion> newExistingSuggestionClustering(
      List<Suggestion> suggestions) {
    return new Clustering<Suggestion>(suggestions, Word2VecModel.model,
        new SuggestionFactory());
  }

  /**
   * Creates a clustering out of the pre-existing clusters. Used when adding a
   * guess to the database.
   *
   * @param clusters
   *          the pre-existing clusters, that make up the clustering
   */
  private Clustering(List<T> clusters, Word2VecModel model,
      ClusterFactory<T> factory) {
    total = 0;
    this.clusters = new ArrayList<>(clusters);
    this.model = model;
    this.factory = factory;
  }

  /**
   * Groups the answers into clusters, sorted such that if two answers become a
   * single cluster, the cluster word/embedding is the higher ranked answer.
   * Used when getting suggestions from google, to group them for
   * playing/storing. Stores the resulting clusters.
   * 
   * @param answers
   *          the phrases that google suggests
   */
  private Clustering(List<String> answers, ClusterFactory<T> factory,
      Word2VecModel model) {
    total = 0;
    this.clusters = new ArrayList<>();
    this.model = model;
    this.factory = factory;
    answers.forEach(this::add);
  }

  /**
   * Adds the guess/answer to the clustering, creating a new cluster if need be,
   * and returns the cluster it was added to (the updated cluster if the cluster
   * gets updated). Used when making a cluster out of suggestions returned from
   * Google, or when adding a guess to a pre-existing cluster. Important: if the
   * phrase matches *any* cluster, it adds the phrase to the clustering that is
   * closest. If it matches none, and consists of only stopwords, then it
   * returns absent and doesn't add the phrase to the clustering.
   * 
   * @param phrase
   *          the guess or answer to add to the cluster
   * @return the updated cluster that the word was put into
   */
  public Optional<T> add(String phrase) {
    List<WordVector> vectors = model.tokenize(phrase);
    Optional<T> bestMatch = clusterOf(vectors);

    if (bestMatch.isPresent()) {
      bestMatch.get().add(vectors);
      return bestMatch;
    } else {
      T newCluster = factory.newInstance(vectors, phrase, total);
      if (newCluster.getVectors().isEmpty()) {
        // Checks that the response isn't just stopwords.
        return Optional.absent();
      }
      total += 1;
      clusters.add(newCluster);
      return Optional.of(newCluster);
    }
  }

  /**
   * Gets the first cluster in the clustering that the word belongs to, returns
   * absent if no such cluster exists. Used to check if a player gets points for
   * their guess. See clusterOf(List[WordVector]) for the real functionality.
   *
   * @param phrase
   *          the user's guess while playing the game
   * @return the cluster that the word belongs to, none if no such cluster
   *         exists in the clustering. does not make a new cluster out of the
   *         word
   */
  public Optional<T> clusterOf(String phrase) {
    return clusterOf(model.tokenize(phrase));
  }

  /*
   * Helper for clusterOf and add.
   */
  private Optional<T> clusterOf(List<WordVector> vectors) {
    Optional<Pair<T, Double>> bestMatch = Optional.absent();

    for (T cluster : clusters) {
      double similarity = cluster.similarity(vectors);
      // Makes sure that it picks the closest cluster.
      if (similarity >= cluster.similarityThreshold()) {
        if (bestMatch.isPresent()) {
          if (bestMatch.get().getRight() < similarity) {
            bestMatch = Optional.of(Pair.of(cluster, similarity));
          }
        } else {
          bestMatch = Optional.of(Pair.of(cluster, similarity));
        }
      }
    }

    if (bestMatch.isPresent()) {
      return Optional.of(bestMatch.get().getLeft());
    }
    return Optional.absent();
  }

  /**
   * Gets the size of the clustering.
   * 
   * @return the number of unique clusters in the clustering
   */
  public int size() {
    return total;
  }

  /**
   * Gets the clusters as a list. Returns an immutable list.
   *
   * @return a list of the clusters
   */
  public List<T> asList() {
    return ImmutableList.copyOf(clusters);
  }
}
