import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class JDBCDem {
    public static void main(String[] args) {
        String jdbcURL = "jdbc:mysql://localhost:3306/userDB";
        String dbUser = "root";
        String dbPassword = "password";
        String sql = "INSERT INTO users(username, password) VALUES('Sasikumar', '1234')";

        try (Connection connection = DriverManager.getConnection(jdbcURL, dbUser, dbPassword)) {
            System.out.println("Database connected successfully");

            // Use executeUpdate for data-modifying queries
            Statement st = connection.createStatement();
            int rowsAffected = st.executeUpdate(sql);

            System.out.println("Rows updated: " + rowsAffected);
        } catch (SQLException e) {
            System.out.println("Error connecting to the database: " + e.getMessage());
        }
    }
}
