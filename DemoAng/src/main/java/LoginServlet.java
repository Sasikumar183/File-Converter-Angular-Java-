import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import org.json.JSONObject;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Enable CORS
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        // Set response type
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            System.out.println(json);
        }

        try {
            // Parse JSON
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

                // Verify user credentials
                String query = "SELECT id FROM users WHERE username = ? AND password = ?";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                    preparedStatement.setString(1, username);
                    preparedStatement.setString(2, password);

                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        if (resultSet.next()) {
                            int userId = resultSet.getInt("id");

                            // Respond with user ID
                            JSONObject jsonResponse = new JSONObject();
                            jsonResponse.put("id", userId);
                            jsonResponse.put("message", "Login successful");
                            out.print(jsonResponse.toString());
                        } else {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            out.print("{\"message\":\"Invalid username or password\"}");
                        }
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
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
