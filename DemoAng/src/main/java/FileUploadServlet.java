import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.json.JSONObject;

@MultipartConfig
public class FileUploadServlet extends HttpServlet {

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

        try {
            // Extract the file and user_id from the request
            Part filePart = request.getPart("file"); // Extract the file part
            Part userIdPart = request.getPart("user_id"); // Extract the user_id part

            // Extract file details
            String fileName = getFileName(filePart);
            InputStream fileContent = filePart.getInputStream(); // Get the file content as an InputStream

            // Extract user ID (parse from string to int)
            int userId = Integer.parseInt(getValueFromPart(userIdPart));
            System.out.println(userId+"  "+fileName);
            System.out.println(fileContent);
            insertFileIntoDatabase(userId, fileName, fileContent);

            // Send a success response
            response.getWriter().write("{\"status\": \"success\"}");
        } catch (Exception e) {
            e.printStackTrace();
            response.getWriter().write("{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("Content-Disposition");
        for (String cd : contentDisposition.split(";")) {
            if (cd.trim().startsWith("filename")) {
                return cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }

    private String getValueFromPart(Part part) throws IOException {
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(part.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                value.append(line);
            }
        }
        return value.toString();
    }

    private void insertFileIntoDatabase(int userId, String fileName, InputStream fileContent) throws SQLException {
        // Database connection parameters
        String jdbcURL = "jdbc:mysql://localhost:3306/userDB";
        String dbUser = "root";
        String dbPassword = "password";

        // SQL query to insert the file into the database
        String sql = "INSERT INTO files (user_id, filename, file) VALUES (?, ?, ?)";

        try (Connection connection = DriverManager.getConnection(jdbcURL, dbUser, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            // Set the parameters
            statement.setInt(1, userId);    // Set the user ID
            statement.setString(2, fileName); // Set the file name
            statement.setBlob(3, fileContent); // Store the file content as a BLOB

            // Execute the insert
            statement.executeUpdate();
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
