package flightapp;

import java.io.IOException;
import java.net.IDN;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.naming.spi.DirStateFactory.Result;

/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  //
  // Canned queries
  //
  private static final String FLIGHT_CAPACITY_SQL = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement flightCapacityStmt;
  private static final String CLEAR_TABLE = "DELETE FROM Reservations_jachi; DELETE FROM Users_jachi;";
  private PreparedStatement clearTablePrepStatement;
  private static final String GET_USERPASS = "SELECT password FROM Users_jachi WHERE username = ?;";
  private PreparedStatement userStatement;
  private static final String LOGIN = "INSERT INTO Users_jachi VALUES(?, ?, ?);";
  private PreparedStatement loginStatement;
  private String currentUsername;
  private static final String DIRECTFLIGHT = "SELECT TOP (?) day_of_month as day1, fid as fid1, carrier_id as carrier1, flight_num as flight_num1, origin_city as origin_city1, dest_city as dest_city1, actual_time as actual_time1, capacity as capacity1, price as price1 " 
  + "FROM Flights WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? AND CANCELED = 0 ORDER BY actual_time ASC, fid ASC;";
  private PreparedStatement directFlightStatement;
  private static final String TOGETHER =  "SELECT TOP (?)" + " f1.fid as fid1, f1.month_id as month1, f1.day_of_month as day1, f1.carrier_id as carrier1, " +
  "f1.flight_num as flight_num1, f1.origin_city as origin_city1, f1.capacity as capacity1, f1.dest_city as dest_city1, " +
  "f1.actual_time as actual_time1, f1.price as price1, " +
  "f2.fid as fid2, f2.month_id as month2, f2.day_of_month as day2, f2.carrier_id as carrier2, " +
  "f2.flight_num as flight_num2, f2.origin_city as origin_city2, f2.capacity as capacity2, f2.origin_state as origin_state2, f2.dest_city as dest_city2, " +
  "f2.dest_state as dest_state2, f2.actual_time as actual_time2, f2.price as price2 " +
  "FROM flights f1, flights f2 " +
  "WHERE f1.origin_city = ? AND f2.dest_city = ? AND f1.dest_city = f2.origin_city AND f1.day_of_month = ? AND f2.day_of_month = f1.day_of_month " +
  "AND f1.canceled = 0 AND f2.canceled = 0 " +
  "AND f1.month_id = f2.month_id " +
  "ORDER BY f1.actual_time + f2.actual_time, f1.fid ASC, f2.fid ASC;";
  private PreparedStatement togetherStatement;

  private static final String CHECKRES = "SELECT COUNT(*) as count FROM RESERVATIONS_jachi";
  private PreparedStatement checkReservationStatement;

  private static final String GETRES = "SELECT * FROM RESERVATIONS_jachi WHERE username = ?";
  private PreparedStatement getReservationStatement;

  private static final String BOOK = "INSERT INTO Reservations_jachi VALUES(?,?,?,?,?)";
  private PreparedStatement bookStatement;
  private static final String FINDUSER = "SELECT COUNT(*) FROM Reservations_jachi WHERE username = ?";
  private PreparedStatement findUserStatement;
  private static final String FINDBALANCE = "SELECT balance as balance FROM Users_jachi WHERE username = ?";
  private PreparedStatement findBalanceStatement;
  private static final String FINDFLIGHT = "SELECT fid_1, fid_2 FROM Reservations_jachi WHERE rid = ? AND paid = 0";
  private PreparedStatement findFlightStatement;
  private static final String FINDEVERYTHING = "SELECT * FROM Flights WHERE fid = ?";
  private PreparedStatement findEverythingStatement;
  private static final String UPDATEPAY = "UPDATE Users_jachi SET balance = ? WHERE username = ?";
  private PreparedStatement updatePayStatement;
  private static final String UPDATEPAIDRES = "Update Reservations_jachi SET paid = 1 WHERE rid = ?";
  private PreparedStatement updatePaidReservationStatement;
  private static final String FINDRESERVATIONS = "SELECT rid, fid_1, fid_2, paid FROM Reservations_jachi WHERE username = ?";
  private PreparedStatement findReservationStatement;
  private static final String FIND_EXACT_FLIGHT = "SELECT fid, day_of_month, carrier_id, flight_num, origin_city, dest_city, actual_time, capacity, price FROM Flights WHERE fid = ?";
  private PreparedStatement findExactFlight;
  private static final String USEREXISTS = "SELECT * FROM Users_jachi WHERE username = ?";
  private PreparedStatement checkUser;
  private static final String FINDNUMRES = "SELECT COUNT(*) as count FROM Reservations_jachi WHERE fid1 = ? OR (fid2 IS NOT NULL and fid2 = ?)";
  private PreparedStatement findNumResStatement;
  
  private static final String FINDCAPACITY = "SELECT COUNT(*) as count FROM Reservations_jachi WHERE username = ? AND fid = ?";
  private PreparedStatement findCapacityStatement;

  // private static final String UPDATECAPACITY = "UPDATE Flight SET capacity = capacity - 1 WHERE fid = ?";
  // private PreparedStatement updateCapacityStatement;

  private static final String CHECKCAPACITY1 = "SELECT COUNT(*) as count FROM Reservations_jachi WHERE fid_1 = ?";
  private PreparedStatement checkCapacity1Statement;

  private static final String CHECKCAPACITY2 = "SELECT COUNT(rid) as count FROM Reservations_jachi WHERE fid_1 = ? AND fid_2 = ?";
  private PreparedStatement checkCapacity2Statement;
  
  //
  // Instance variables
  //
  private boolean logIn;
  private List<Itinerary> itineraries;
  private int numSearches;
  private int currentRes;

  protected Query() throws SQLException, IOException {
    prepareStatements();
    this.itineraries = new ArrayList<>();
    this.logIn = false;
    this.numSearches = 0;
    this.currentRes = 0;
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      clearTablePrepStatement.executeUpdate();
      clearTablePrepStatement.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);
    clearTablePrepStatement = conn.prepareStatement(CLEAR_TABLE);
    userStatement = conn.prepareStatement(GET_USERPASS);
    loginStatement = conn.prepareStatement(LOGIN);
    directFlightStatement = conn.prepareStatement(DIRECTFLIGHT);
    togetherStatement = conn.prepareStatement(TOGETHER);
    checkReservationStatement = conn.prepareStatement(CHECKRES);
    getReservationStatement = conn.prepareStatement(GETRES);
    bookStatement = conn.prepareStatement(BOOK);
    findUserStatement = conn.prepareStatement(FINDUSER);
    findBalanceStatement = conn.prepareStatement(FINDBALANCE);
    findFlightStatement = conn.prepareStatement(FINDFLIGHT);
    findEverythingStatement = conn.prepareStatement(FINDEVERYTHING);
    updatePayStatement = conn.prepareStatement(UPDATEPAY);
    updatePaidReservationStatement = conn.prepareStatement(UPDATEPAIDRES);
    findReservationStatement = conn.prepareStatement(FINDRESERVATIONS);
    findExactFlight = conn.prepareStatement(FIND_EXACT_FLIGHT);
    checkUser = conn.prepareStatement(USEREXISTS);
    findNumResStatement = conn.prepareStatement(FINDNUMRES);
    findCapacityStatement = conn.prepareStatement(FINDCAPACITY);
    // updateCapacityStatement = conn.prepareStatement(UPDATECAPACITY);
    checkCapacity1Statement = conn.prepareStatement(CHECKCAPACITY1);
    checkCapacity2Statement = conn.prepareStatement(CHECKCAPACITY2);
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n".  For all
   *         other errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    if (this.currentUsername != null) {
      this.logIn = true;
      return "User already logged in\n";
    }
    try { 
      userStatement.clearParameters();
      userStatement.setString(1, username);
      ResultSet currentUser = userStatement.executeQuery();
      while(currentUser.next()) {
        byte[] passwordBytes = currentUser.getBytes("password");
        if(PasswordUtils.plaintextMatchesHash(password, passwordBytes)) {
          this.logIn = true;
          this.itineraries.clear();
          this.currentUsername = username;
          return "Logged in as " + username.toLowerCase() + "\n";
        } 
      }
      currentUser.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return "Login failed\n";
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try {
      if(initAmount < 0) {
        return "Failed to create user\n";
      }
      // conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      conn.setAutoCommit(false);
      checkUser.clearParameters();
      checkUser.setString(1, username);
      ResultSet exists = checkUser.executeQuery();
      if(exists.next()) {
        conn.setAutoCommit(true);
        return "Failed to create user\n";
      }
      exists.close();
      byte[] hashedPass = PasswordUtils.hashPassword(password);
      loginStatement.clearParameters();
      loginStatement.setString(1, username);
      loginStatement.setBytes(2, hashedPass);
      loginStatement.setInt(3, initAmount);
      loginStatement.execute();
      conn.commit();
      conn.setAutoCommit(true);
      return "Created user " + username + "\n";
    } catch (SQLException e) {
      boolean ifDeadlock = isDeadlock(e);
      try {
        conn.setAutoCommit(true);
        if(ifDeadlock) {
          return transaction_createCustomer(username, password, initAmount);
        }
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      e.printStackTrace();
    }
    return "Failed to create user\n";
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given
   * day of the month. If {@code directFlight} is true, it only searches for direct flights,
   * otherwise is searches for direct flights and flights with two "hops." Only searches for up
   * to the number of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return, must be positive
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */

  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    StringBuffer sb = new StringBuffer();
    try {
      // one hop itineraries
      itineraries.clear();
      List<Itinerary> direct = directFlights(originCity, destinationCity, dayOfMonth, numberOfItineraries);
      if(direct == null) {
        return "No flights match your selection\n";
      }
      this.itineraries.addAll(direct);
      int itineraryTracker = itineraries.size();
      List<Itinerary> indirect = new ArrayList<>();
      if(!directFlight && (numberOfItineraries - itineraryTracker) > 0) {
        indirect = indirectFlights(originCity, destinationCity, dayOfMonth, numberOfItineraries);
        if(indirect == null) {
          return "No flights match your selection\n";
        }
        this.itineraries.addAll(indirect);
      }
      Collections.sort(itineraries);
      for (int i = 0; i < itineraries.size(); i++) {
        sb.append("Itinerary " + i + ": ");
        Itinerary current = itineraries.get(i);
        if (current.direct) {
          sb.append("1 flight(s), " + current.totalTime + " minutes\n" + current.f1.toString() + "\n");
        } else {
          sb.append("2 flight(s), " + current.totalTime + " minutes\n" + current.f1.toString() + "\n" + current.f2.toString() + "\n");
        }
      }
      numSearches = itineraries.size() - 1;
      return sb.toString();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return "Failed to search\n";
  }

  private List<Itinerary> directFlights(String originCity, String destinationCity, int dayOfMonth, int numberOfItineraries) throws SQLException {
    directFlightStatement.clearParameters();
    directFlightStatement.setInt(1, numberOfItineraries);
    directFlightStatement.setString(2, originCity);
    directFlightStatement.setString(3, destinationCity);
    directFlightStatement.setInt(4, dayOfMonth);
    List<Itinerary> itineraries = new ArrayList<>();
    ResultSet oneHopResults = directFlightStatement.executeQuery();
    if (!oneHopResults.isBeforeFirst() ) {    
      return null;
    } 
    while (oneHopResults.next()) {
      int direct_dayOfMonth = oneHopResults.getInt("day1");
      int direct_fid = oneHopResults.getInt("fid1");
      String direct_carrierId = oneHopResults.getString("carrier1");
      String direct_flightNum = oneHopResults.getString("flight_num1");
      String direct_originCity = oneHopResults.getString("origin_city1");
      String direct_destCity = oneHopResults.getString("dest_city1");
      int direct_time = oneHopResults.getInt("actual_time1");
      int direct_capacity = oneHopResults.getInt("capacity1");
      int direct_price = oneHopResults.getInt("price1");
      if (direct_time > 0){
        Flight f1 = new Flight(direct_fid, direct_dayOfMonth, direct_carrierId, direct_flightNum, direct_originCity, direct_destCity, direct_time, direct_capacity, direct_price);
        Itinerary itinerary = new Itinerary(f1, null, true, direct_time);
        itineraries.add(itinerary);
      }
    }
    oneHopResults.close();
    return itineraries;
  }

  private List<Itinerary> indirectFlights(String originCity, String destinationCity, int dayOfMonth, int numberOfItineraries) throws SQLException {
    togetherStatement.clearParameters();
    togetherStatement.setInt(1, numberOfItineraries);
    togetherStatement.setString(2, originCity);
    togetherStatement.setString(3, destinationCity);
    togetherStatement.setInt(4, dayOfMonth);
    ResultSet oneHopResults = togetherStatement.executeQuery();
    List<Itinerary> itineraries = new ArrayList<>();;
    if (!oneHopResults.isBeforeFirst() ) {    
      return null;
    } 
    while(oneHopResults.next()) {
      int direct_dayOfMonth = oneHopResults.getInt("day1");
      int direct_fid = oneHopResults.getInt("fid1");
      String direct_carrierId = oneHopResults.getString("carrier1");
      String direct_flightNum = oneHopResults.getString("flight_num1");
      String direct_originCity = oneHopResults.getString("origin_city1");
      String direct_destCity = oneHopResults.getString("dest_city1");
      int direct_time = oneHopResults.getInt("actual_time1");
      int direct_capacity = oneHopResults.getInt("capacity1");
      int direct_price = oneHopResults.getInt("price1");
      Flight f1 = new Flight(direct_fid, direct_dayOfMonth, direct_carrierId, direct_flightNum, direct_originCity, direct_destCity, direct_time, direct_capacity, direct_price);
      int direct_dayOfMonth2 = oneHopResults.getInt("day2");
      int direct_fid2 = oneHopResults.getInt("fid2");
      String direct_carrierId2 = oneHopResults.getString("carrier2");
      String direct_flightNum2 = oneHopResults.getString("flight_num2");
      String direct_originCity2 = oneHopResults.getString("origin_city2");
      String direct_destCity2 = oneHopResults.getString("dest_city2");
      int direct_time2 = oneHopResults.getInt("actual_time2");
      int direct_capacity2 = oneHopResults.getInt("capacity2");
      int direct_price2 = oneHopResults.getInt("price2");
      Flight f2 = new Flight(direct_fid2, direct_dayOfMonth2, 
      direct_carrierId2, direct_flightNum2, direct_originCity2, direct_destCity2, direct_time2,
      direct_capacity2, direct_price2);
      int total = direct_time + direct_time2;
      if(f1.flightNum.equals(f2.flightNum)) {
        Itinerary itinerary = new Itinerary(f1, f2, false, total);
        itineraries.add(itinerary);
      }
    }      
    oneHopResults.close();
    return itineraries;
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search
   *                    in the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged
   *         in\n". If the user is trying to book an itinerary with an invalid ID or without
   *         having done a search, then return "No such itinerary {@code itineraryId}\n". If the
   *         user already has a reservation on the same day as the one that they are trying to
   *         book now, then return "You cannot book two flights in the same day\n". For all
   *         other errors, return "Booking failed\n".
   *
   *         If booking succeeds, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from
   *         1 and increments by 1 each time a successful reservation is made by any user in
   *         the system.
   */

   public String transaction_book(int itineraryId) {
    try {
      if(this.itineraries == null) {
        return "No such itinerary" + itineraryId + "\n";
      }
      if(this.currentUsername == null) {
        return "Cannot book reservations, not logged in\n";
      }
      if(itineraryId < 0 || itineraryId >= this.itineraries.size()) {
        return "No such itinerary " + itineraryId + "\n";
      }
      if(this.itineraries.size() < 1) {
        return "Booking failed\n";
      }
      conn.setAutoCommit(false);
      Itinerary current = itineraries.get(itineraryId);
      if (current == null) {
        return "No such itinerary " + itineraryId + "\n";
      }
        getReservationStatement.clearParameters();
        getReservationStatement.setString(1, this.currentUsername);
        ResultSet getResResult = getReservationStatement.executeQuery();
        int currentReservation = 0;
        while(getResResult.next()) {
          currentReservation = getResResult.getInt("fid_1");
          if(current.f1.fid == currentReservation) {
            conn.rollback();
            conn.setAutoCommit(true);
            return "You cannot book two flights in the same day\n";
          }
        }
        findEverythingStatement.clearParameters();
        findEverythingStatement.setInt(1, currentReservation);
        ResultSet findDay = findEverythingStatement.executeQuery();
        while(findDay.next()) {
          int day = findDay.getInt("day_of_month");
          if(current.f1.dayOfMonth == day) {
            conn.rollback();
            conn.setAutoCommit(true);
            return "You cannot book two flights in the same day\n";
          }
        }
        getResResult.close();
        checkCapacity1Statement.clearParameters();
        checkCapacity1Statement.setInt(1, current.f1.fid);
        ResultSet resCap = checkCapacity1Statement.executeQuery();
        resCap.next();
        int resCapNum = resCap.getInt("count");
        if(current.f1.capacity - resCapNum < 1) {
          conn.rollback();
          conn.setAutoCommit(true);
          return "Booking failed\n";
        }
        resCap.close();
          int checkCap = checkFlightCapacity(current.f1.fid);
          if(checkCap == 0) {
            conn.rollback();
            conn.setAutoCommit(true);
            return "Booking failed\n";
          }
          ResultSet currentCount = checkReservationStatement.executeQuery();
          currentCount.next();
          int reservations = currentCount.getInt("count");
          currentCount.close();
          bookStatement.clearParameters();
          bookStatement.setInt(1, reservations + 1);
          bookStatement.setString(2, this.currentUsername);
          bookStatement.setInt(3, current.f1.fid);
          bookStatement.setNull(4, java.sql.Types.INTEGER);
          bookStatement.setInt(5, 0);
          if(current.f2 != null) {
            bookStatement.setInt(4, current.f2.fid);
          }
          bookStatement.executeUpdate();
          conn.commit();
          conn.setAutoCommit(true);
          return "Booked flight(s), reservation ID: " + (reservations + 1) + "\n";
          
    } catch (SQLException e) {
      boolean ifDeadlock = isDeadlock(e);
      try {
        conn.setAutoCommit(true);
        if(ifDeadlock) {
          return transaction_book(itineraryId);
        }
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      e.printStackTrace();
    }
    return "Booking failed\n";
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n". If the
   *         reservation is not found / not under the logged in user's name, then return
   *         "Cannot find unpaid reservation [reservationId] under user: [username]\n".  If
   *         the user does not have enough money in their account, then return
   *         "User has only [balance] in account but itinerary costs [cost]\n".  For all other
   *         errors, return "Failed to pay for reservation [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay(int reservationId) {
    if(!this.logIn) {
      return "Cannot pay, not logged in\n";
    }
    if(this.currentUsername == null) {
      return "Cannot pay, not logged in\n";
    }
    try {
      conn.setAutoCommit(false);
      findFlightStatement.clearParameters();
      findFlightStatement.setInt(1, reservationId);
      ResultSet flightSet = findFlightStatement.executeQuery();
      if(!flightSet.next()) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Cannot find unpaid reservation " + reservationId +
        " under user: " + this.currentUsername + "\n";
      }
      findBalanceStatement.clearParameters();
      findBalanceStatement.setString(1, this.currentUsername);
      ResultSet balanceSet = findBalanceStatement.executeQuery();
      balanceSet.next();
      int balance = balanceSet.getInt("balance");
      int f1Num = flightSet.getInt("fid_1");
      findEverythingStatement.clearParameters();
      findEverythingStatement.setInt(1, f1Num);
      ResultSet findFlightSet = findEverythingStatement.executeQuery();
      findFlightSet.next();
      int dayOfMonth = findFlightSet.getInt("day_of_month");
      String carrierId = findFlightSet.getString("carrier_id");
      String flightNum = findFlightSet.getString("flight_num");
      String origin = findFlightSet.getString("origin_city");
      String dest = findFlightSet.getString("dest_city");
      int time = findFlightSet.getInt("actual_time");
      int capacity = findFlightSet.getInt("capacity");
      int price = findFlightSet.getInt("price");
      Flight f1 = new Flight(reservationId, dayOfMonth, carrierId, flightNum, 
      origin, dest, time , capacity, price);
      int totalCost = f1.price;
      int f2Num = flightSet.getInt("fid_2");
      findEverythingStatement.clearParameters();
      findEverythingStatement.setInt(1, f2Num);
      ResultSet findFlightSet2 = findEverythingStatement.executeQuery();
      if (findFlightSet2.next()) {
        int dayOfMonth2 = findFlightSet2.getInt("day_of_month");
        String carrierId2 = findFlightSet2.getString("carrier_id");
        String flightNum2 = findFlightSet2.getString("flight_num");
        String origin2 = findFlightSet2.getString("origin_city");
        String dest2 = findFlightSet2.getString("dest_city");
        int time2 = findFlightSet2.getInt("actual_time");
        int capacity2 = findFlightSet2.getInt("capacity");
        int price2 = findFlightSet2.getInt("price");
        if(flightSet.wasNull()) {
          Flight f2 = new Flight(reservationId, dayOfMonth2, carrierId2, flightNum2, 
          origin2, dest2, time2, capacity2, price2);
          totalCost += f2.price;
        }
      }
      if(balance < totalCost) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "User has only " + balance + " in account but itinerary costs " + totalCost + "\n";
      }
      updatePayStatement.clearParameters();
      updatePayStatement.setInt(1, balance - totalCost);
      updatePayStatement.setString(2, this.currentUsername);
      updatePayStatement.executeUpdate();
      updatePaidReservationStatement.clearParameters();
      updatePaidReservationStatement.setInt(1, reservationId);
      updatePaidReservationStatement.executeUpdate();
      conn.commit();
      conn.setAutoCommit(true);
      return  "Paid reservation: " + reservationId + " remaining balance: " + (balance - totalCost) + "\n";
    } catch (SQLException e) {
      e.printStackTrace();
      boolean ifDeadlock = isDeadlock(e);
      try {
        conn.setAutoCommit(true);
        if(ifDeadlock) {
          return transaction_pay(reservationId);
        }
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      e.printStackTrace();
    }
    return "Failed to pay for reservation " + reservationId + "\n";
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    if(this.currentUsername == null) {
      return "Cannot view reservations, not logged in\n";
    }
    if(!logIn) {
      return "Cannot view reservations, not logged in\n";
    } 
    try {
      conn.setAutoCommit(false);
      findUserStatement.clearParameters();
      findUserStatement.setString(1, this.currentUsername);
      ResultSet hasUser = findUserStatement.executeQuery();
      if(!hasUser.next()){
        conn.rollback();
        conn.setAutoCommit(true);
        return "No reservations found\n";
      }
      StringBuffer sb = new StringBuffer();
      findReservationStatement.clearParameters();
      findReservationStatement.setString(1, this.currentUsername);
      ResultSet users = findReservationStatement.executeQuery();
      while(users.next()) {
        int rid = users.getInt(1);
        int fid_1 = users.getInt(2);
        int fid_2 = users.getInt(3);
        int paid = users.getInt(4);
        String ifPaid = "false";
        if(paid == 1){ 
          ifPaid = "true";
        }
        sb.append("Reservation " + (rid) + " paid: " + ifPaid + ":\n");
        findExactFlight.clearParameters();
        findExactFlight.setInt(1, fid_1);
        ResultSet firstFlight = findExactFlight.executeQuery();
        firstFlight.next();
        int f1_fid = firstFlight.getInt(1);
        int f1_day = firstFlight.getInt(2);
        String f1_carrier = firstFlight.getString(3);
        String f1_fnum = firstFlight.getString(4);
        String f1_origin = firstFlight.getString(5);
        String f1_dest = firstFlight.getString(6);
        int f1_time = firstFlight.getInt(7);
        int f1_capacity = firstFlight.getInt(8);
        int f1_price = firstFlight.getInt(9);
        firstFlight.close();
        if(f1_time > 0) {
          Flight f1 = new Flight(f1_fid, f1_day, f1_carrier, f1_fnum, f1_origin, f1_dest, f1_time, f1_capacity, f1_price);
          sb.append(f1.toString() + "\n");
        }
        findExactFlight.clearParameters();
        findExactFlight.setInt(1, fid_2);
        ResultSet secondFlight = findExactFlight.executeQuery();
        if(secondFlight.next()) {
          int f2_fid = secondFlight.getInt(1);
          int f2_day = secondFlight.getInt(2);
          String f2_carrier = secondFlight.getString(3);
          String f2_fnum = secondFlight.getString(4);
          String f2_origin = secondFlight.getString(5);
          String f2_dest = secondFlight.getString(6);
          int f2_time = secondFlight.getInt(7);
          int f2_capacity = secondFlight.getInt(8);
          int f2_price = secondFlight.getInt(9);
          Flight f2 = new Flight(f2_fid, f2_day, f2_carrier, f2_fnum, f2_origin, f2_dest, f2_time, f2_capacity, f2_price);
          sb.append(f2.toString() + "\n");
        }
        secondFlight.close();
      }
      conn.commit();
      conn.setAutoCommit(true);
      return sb.toString();
    } catch (SQLException e) {
      boolean ifDeadlock = isDeadlock(e);
      try {
        conn.setAutoCommit(true);
        if(ifDeadlock) {
          return transaction_reservations();
        }
      } catch (SQLException e1) {
        e1.printStackTrace();
      }
      e.printStackTrace();
    }
    return "Failed to retrieve reservations\n";
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return e.getErrorCode() == 1205;
  }

  public class Itinerary implements Comparable<Itinerary> {
    private Flight f1;
    private Flight f2;
    private boolean direct;
    private int totalTime;

    public Itinerary(Flight f1, Flight f2, boolean direct, int totalTime) {
        this.f1 = f1;
        this.f2 = f2;
        this.direct = direct;
        this.totalTime = totalTime;
    }

    @Override
    public int compareTo(Itinerary other) {
      if(this.totalTime != other.totalTime) {
        return Integer.compare(this.totalTime, other.totalTime);
      }
      return Integer.compare(this.f1.fid, other.f1.fid);
    }
  }

  /**
   * A class to store information about a single flight
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String fnum, String origin, String dest, int tm,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      flightNum = fnum;
      originCity = origin;
      destCity = dest;
      time = tm;
      capacity = cap;
      price = pri;
    }
    
    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
