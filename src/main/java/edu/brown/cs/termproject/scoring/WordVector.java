package edu.brown.cs.termproject.scoring;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the word and word vector. This can calculate cosine similarity with
 * other vectors. Note that if multiple threads have access to the same
 * WordVector object, they can both call similarity while still being thread
 * safe. This is because the vector is stored as an immutable list and the
 * reference itself never changes, so the whole class is immutable.
 */
public class WordVector {

  // All fields are immutable.
  private ImmutableList<Double> vector;
  private double magnitude;
  private String word;

  /**
   * Initializes the word vector using the input string. The string is formatted
   * as such: double_1,double_2,double_3,...,double_n. Values are seperated with
   * commas, no whitespaces anywhere. Doubles are represented in scientific
   * notation.
   *
   * @param vectorString
   *          the string representation of the vector.
   */
  public WordVector(String word, String vectorString) {
    this.word = word;

    String[] parts = vectorString.split(",");
    List<Double> tempVector = new ArrayList<>();
    for (String part : parts) {
      try {
        tempVector.add(Double.parseDouble(part));
      } catch (NumberFormatException ex) {
        throw new RuntimeException("ERROR: Incorrect vector formatting.");
      }
    }
    vector = ImmutableList.copyOf(tempVector);

    if (tempVector.size() == 0) {
      throw new RuntimeException("ERROR: WordVector cannot have dimension 0.");
    }

    magnitude = 0;
    for (Double value : vector) {
      magnitude += (value * value);
    }
    magnitude = Math.sqrt(magnitude);
  }

  /**
   * Returns the cosine similarity between this word and the other word. Throws
   * a runtime exception if the vectors differ in length.
   *
   * @param other
   *          the other vector
   * @return the cosine similarity, a double between -1 and 1
   */
  public double similarity(WordVector other) {
    ImmutableList<Double> otherVector = other.getVector();
    if (otherVector.size() != vector.size()) {
      throw new RuntimeException(
          "ERROR: WordVectors have different dimensions.");
    }

    // Computes dot product.
    double dotProduct = 0;
    for (int i = 0; i < vector.size(); i++) {
      dotProduct += (vector.get(i) * otherVector.get(i));
    }

    // Normalizes using magnitudes.
    return dotProduct / (magnitude * other.getMagnitude());
  }

  /**
   * Gets the vector.
   * 
   * @return the vector, as an immutable list of doubles
   */
  public ImmutableList<Double> getVector() {
    return vector;
  }

  /**
   * Gets the magnitude.
   * 
   * @return the magnitude of the vector
   */
  public double getMagnitude() {
    return magnitude;
  }

  /**
   * Gets the word.
   * 
   * @return the word the vector corresponds to
   */
  public String getWord() {
    return word;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof WordVector)) {
      return false;
    }
    return word.equals(((WordVector) obj).getWord());
  }

  @Override
  public int hashCode() {
    return word.hashCode();
  }
}
