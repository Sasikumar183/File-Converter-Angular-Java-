import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet implementation class GetUploadedFilesServlet
 */
@WebServlet("/GetUploadedFilesServlet")
public class GetUploadedFilesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/userDB";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "password";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        String userId = request.getParameter("id");  // Get user ID from query parameter

        if (userId != null) {
            List<String> fileNames = getFilesForUser(userId);  // Fetch files for the user from DB
            if (fileNames != null && !fileNames.isEmpty()) {
                // Send back file names as a JSON response
                StringBuilder fileNamesJson = new StringBuilder();
                for (String fileName : fileNames) {
                    fileNamesJson.append("\"").append(fileName).append("\",");
                }
                if (fileNamesJson.length() > 0) {
                    fileNamesJson.setLength(fileNamesJson.length() - 1);  // Remove last comma
                }
                response.setContentType("application/json");
                response.getWriter().write("{\"files\": [" + fileNamesJson.toString() + "]}");
            } else {
                response.getWriter().write("{\"message\": \"No files found\"}");
            }
        } else {
            response.getWriter().write("{\"message\": \"User ID is required\"}");
        }
    }

    private List<String> getFilesForUser(String userId) {
        List<String> fileNames = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            // SQL query to fetch filenames for the given userId
            String sql = "SELECT filename FROM files WHERE user_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, userId);
                try (ResultSet resultSet = stmt.executeQuery()) {
                    while (resultSet.next()) {
                        fileNames.add(resultSet.getString("filename"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return fileNames;
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
