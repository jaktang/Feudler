package edu.brown.cs.termproject.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import edu.brown.cs.termproject.queryResponses.QueryResponses;
import edu.brown.cs.termproject.queryResponses.Response;
import edu.brown.cs.termproject.scoring.Clustering;
import edu.brown.cs.termproject.scoring.Suggestion;
import edu.brown.cs.termproject.scoring.Word2VecModel;

public class DBConnector {
  private static final Set<String> TABLE_NAMES = new TreeSet<String>(
      Arrays.asList("queries", "answers"));

  public DBConnector(String dbPath) throws SQLException {
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
    String urlToDB = "jdbc:sqlite:" + dbPath;
    // set database connection
    conn = DriverManager.getConnection(urlToDB);
    Statement stat = conn.createStatement();
    stat.executeUpdate("PRAGMA foreign_keys = ON;");
    // get tables to check if they are valid
    ResultSet rs = conn.getMetaData().getTables(null, null, null, null);

    List<String> tables = new ArrayList<>();

    while (rs.next()) {

      tables.add(rs.getString(3));
    }
    rs.close();
    for (String tName : TABLE_NAMES) {
      if (!tables.contains(tName)) {
        conn = null;
        String errMessage = "Database file must have following table names: ";
        throw new IllegalArgumentException(
            "Database file did not have the required table \"" + tName + "\".");
      }
    }
  }

  private Connection conn;

  /**
   * Checks if the database contains the given query.
   * 
   * @param query
   *          - the query to check for the presence of
   * @return - boolean corresponding to whether or not the query is contained
   */
  public boolean containsQuery(String query) {
    try (PreparedStatement prep = conn
        .prepareStatement("SELECT COUNT(*) FROM queries WHERE query = ?;");) {
      prep.setString(1, query);
      ResultSet rs = prep.executeQuery();
      int count = rs.getInt(1);
      if (count == 0) {
        prep.close();
        return false;
      } else {
        prep.close();
        return true;
      }
    } catch (SQLException e) {
      return false;
    }
  }

  /**
   * Returns queryNum random Queries in the form of QueryResponses datatype, or,
   * if QueryNum > the number of queries in the database, returns all queries in
   * the database in QueryResponses form.
   * 
   * @param queryNum
   *          - Number of queries to return
   * @return - queryNum random Queries
   * @throws SQLException
   *           - If SQL statements encounter issues.
   */
  public List<QueryResponses> nRandomQueries(int queryNum) throws SQLException {
    PreparedStatement prep = conn
        .prepareStatement("SELECT * FROM queries ORDER BY RANDOM() LIMIT ?;");
    prep.setInt(1, queryNum);
    ResultSet rs = prep.executeQuery();
    List<QueryResponses> queries = new ArrayList<>();
    while (rs.next()) {
      String query = rs.getString("query");
      int qID = rs.getInt("ID");
      List<String> responses = new ArrayList<>();
      PreparedStatement resPrep = conn.prepareStatement(
          "SELECT answer FROM answers WHERE queryID = ? ORDER BY score;");
      resPrep.setInt(1, qID);
      ResultSet resRs = resPrep.executeQuery();

      while (resRs.next()) {
        responses.add(resRs.getString(1));
      }
      resPrep.close();
      queries.add(new QueryResponses(qID, query,
          Clustering.newSuggestionClustering(responses, Word2VecModel.model)));
    }
    prep.close();
    // ResultSets closed when you close their prep;
    return queries;
  }

  /**
   * Returns queryNum random Queries in the form of QueryResponses datatype, or,
   * if QueryNum > the number of queries in the database, returns all queries in
   * the database in QueryResponses form.
   * 
   * @param queryNum
   *          - Number of queries to return
   * @return - queryNum random Queries
   * @throws SQLException
   *           - If SQL statements encounter issues.
   */
  public List<QueryResponses> nRandomMetaModeQueries(int queryNum)
      throws SQLException {
    PreparedStatement prep = conn.prepareStatement(
        "select * from queries where ID in " + "(select queryID from "
            + "(select queryID, count(*) as c from guesses group by queryID) where c > 3) "
            + "order by random() limit ?;");
    prep.setInt(1, queryNum);
    ResultSet rs = prep.executeQuery();
    List<QueryResponses> queries = new ArrayList<>();
    while (rs.next()) {
      String query = rs.getString("query");
      int qID = rs.getInt("ID");
      List<String> responses = new ArrayList<>();
      PreparedStatement resPrep = conn.prepareStatement(
          "SELECT answer FROM guesses WHERE queryID = ? ORDER BY score DESC LIMIT 10;");
      resPrep.setInt(1, qID);
      ResultSet resRs = resPrep.executeQuery();

      while (resRs.next()) {
        responses.add(resRs.getString(1));
      }
      resPrep.close();
      queries.add(new QueryResponses(qID, query,
          Clustering.newSuggestionClustering(responses, Word2VecModel.model)));
    }
    prep.close();
    // ResultSets closed when you close their prep;
    return queries;
  }

  /**
   * Inserts a QueryResponses into the database Note, this method currently
   * assumes that the QueryResponses is valid. Please check that the minimum
   * number of responses is present before using this function and that query is
   * not null (or we can add that to this function later).
   *
   * NOTE: The database currently specifies that queries are unique. If you're
   * trying to replace a query with new results, you should use an update
   * function, or delete then insert.
   *
   * Also note: The id of the queryresponse is not used here, and we expect it
   * to be zero.
   * 
   * @param qr
   *          - A QueryResponses to add to the database
   * @throws SQLException
   */
  public void insertQuery(QueryResponses qr) throws SQLException {
    PreparedStatement prep = conn
        .prepareStatement("INSERT INTO queries (query) values (?);");
    prep.setString(1, qr.getQuery());
    prep.execute();
    prep.close();
    prep = conn.prepareStatement("SELECT ID FROM queries WHERE query = ?;");
    prep.setString(1, qr.getQuery());
    ResultSet idSet = prep.executeQuery();
    idSet.next();
    int qID = idSet.getInt("ID");
    prep.close();
    prep = conn.prepareStatement(
        "INSERT INTO answers (answer, score, queryID) values (?, ?, ?);");
    for (Suggestion sug : qr.getResponses().asList()) {
      prep.setString(1, sug.getResponse());
      prep.setInt(2, sug.getScore());
      prep.setInt(3, qID);
      prep.addBatch();
    }
    prep.executeBatch();
    prep.close();
  }

}
