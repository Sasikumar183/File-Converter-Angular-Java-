import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import org.json.JSONObject;

@WebServlet("/SignupServlet")
public class SignupServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Enable CORS
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        }

        try {
            JSONObject jsonObject = new JSONObject(json.toString());
            String username = jsonObject.getString("username");
            String password = jsonObject.getString("password");

            // Database connection details
            String jdbcURL = "jdbc:mysql://localhost:3306/userDB";
            String dbUser = "root";
            String dbPassword = "password";

            // Connect to the database
            try (Connection connection = DriverManager.getConnection(jdbcURL, dbUser, dbPassword)) {
                System.out.println("Database connected successfully");

                // Check if username already exists
                String checkQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
                try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
                    checkStmt.setString(1, username);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            // Username already exists
                            response.setStatus(HttpServletResponse.SC_CONFLICT);
                            out.print("{\"message\":\"Username already exists. Please choose a different username.\"}");
                            return;
                        }
                    }
                }

                // Insert user data into the database
                String insertQuery = "INSERT INTO users (username, password) VALUES (?, ?)";
                try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                    preparedStatement.setString(1, username);
                    preparedStatement.setString(2, password);

                    int affectedRows = preparedStatement.executeUpdate();
                    if (affectedRows > 0) {
                        // Retrieve generated user ID
                        try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                int userId = generatedKeys.getInt(1);

                                // Respond with user ID
                                JSONObject jsonResponse = new JSONObject();
                                jsonResponse.put("id", userId);
                                jsonResponse.put("message", "Signup successful");
                                out.print(jsonResponse.toString());
                            } else {
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                out.print("{\"message\":\"Signup failed, user ID not generated.\"}");
                            }
                        }
                    } else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        out.print("{\"message\":\"Signup failed, no rows affected.\"}");
                    }
                }
            } catch (SQLException e) {
                System.out.println("Error connecting to the database: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"message\":\"Database connection error: " + e.getMessage() + "\"}");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"message\":\"Invalid input: " + e.getMessage() + "\"}");
        } finally {
            out.flush();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
