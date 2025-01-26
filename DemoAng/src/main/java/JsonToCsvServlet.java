import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Random;

@WebServlet("/JsonToCsvServlet")
@MultipartConfig
public class JsonToCsvServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String UPLOAD_DIR = "/uploads/";
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/userDB";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "password";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setCORSHeaders(response);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String userId = request.getParameter("userId");
        String fileName = request.getParameter("filename");

        if (userId == null || fileName == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"User ID or filename not provided.\"}");
            return;
        }

        String jsonContent;
        try {
            jsonContent = fetchFileFromDatabase(userId, fileName);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error fetching file from database: " + e.getMessage() + "\"}");
            return;
        }

        if (jsonContent == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("{\"error\": \"File not found in the database.\"}");
            return;
        }

        String csvResult;
        try {
            csvResult = convertJsonToCsv(jsonContent);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error during conversion: " + e.getMessage() + "\"}");
            return;
        }

        String convertedFilePath = saveConvertedFile(csvResult, userId);

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("downloadLink", request.getRequestURL().toString() + "?action=download&filePath=" + convertedFilePath);
        jsonResponse.put("fileContent", csvResult);
        response.getWriter().write(jsonResponse.toString());

        // Use a unique filename
        String filename = "converted_" + generateRandomNumber() + ".csv";
        try {
            insertFileToDatabase(userId, filename, csvResult);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error saving file to database: " + e.getMessage() + "\"}");
        }
    }

    private String convertJsonToCsv(String jsonContent) throws IOException {
        JSONArray jsonArray = new JSONArray(jsonContent);
        StringBuilder csvBuilder = new StringBuilder();

        if (jsonArray.length() > 0) {
            // Extract headers from the first JSONObject
            JSONObject firstObject = jsonArray.getJSONObject(0);
            for (String key : firstObject.keySet()) {
                csvBuilder.append(key).append(",");
            }
            csvBuilder.setLength(csvBuilder.length() - 1);  // Remove the last comma
            csvBuilder.append("\n");

            // Extract rows
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                for (String key : firstObject.keySet()) {
                    csvBuilder.append(jsonObject.optString(key)).append(",");
                }
                csvBuilder.setLength(csvBuilder.length() - 1);  // Remove the last comma
                csvBuilder.append("\n");
            }
        }

        return csvBuilder.toString();
    }

    private String saveConvertedFile(String csvResult, String userId) throws IOException {
        Path uploadPath = Paths.get(getServletContext().getRealPath(UPLOAD_DIR), userId);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate a random 3-digit number
        String convertedFileName = userId + "_converted_" + generateRandomNumber() + ".csv"; // Append the random number to the file name

        Path filePath = uploadPath.resolve(convertedFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(csvResult);
        }

        return UPLOAD_DIR + userId + "/" + convertedFileName;
    }

    private void insertFileToDatabase(String userId, String convertedFileName, String fileContent) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            String query = "INSERT INTO files (user_id, filename, file) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, userId);
                preparedStatement.setString(2, convertedFileName); // Use the modified filename
                preparedStatement.setString(3, fileContent);

                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SQLException("Error inserting file into database: " + e.getMessage(), e);
        }
    }

    private int generateRandomNumber() {
        return (int) (Math.random() * 900) + 100; // Generates a random 3-digit number.
    }

    private void setCORSHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*"); // Allow multiple origins or use specific origin.
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    private String fetchFileFromDatabase(String userId, String fileName) throws SQLException {
        String jsonContent = null;
        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT file FROM files WHERE user_id = ? AND filename = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, userId);
                preparedStatement.setString(2, fileName);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        jsonContent = resultSet.getString("file");
                    }
                }
            }
        }
        return jsonContent;
    }

    private void handleFileDownload(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filePath = request.getParameter("filePath");

        if (filePath == null || filePath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"File path not provided.\"}");
            return;
        }

        Path file = Paths.get(getServletContext().getRealPath(filePath));
        if (!Files.exists(file)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("{\"error\": \"File not found.\"}");
            return;
        }

        response.setContentType("application/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getFileName().toString() + "\"");
        response.setContentLength((int) Files.size(file));

        try (InputStream inputStream = Files.newInputStream(file);
             OutputStream outputStream = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        if ("download".equalsIgnoreCase(action)) {
            handleFileDownload(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Invalid action.\"}");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "*"); // Allow multiple origins
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
