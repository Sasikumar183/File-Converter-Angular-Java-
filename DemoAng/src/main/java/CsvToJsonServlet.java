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
import java.util.ArrayList;
import java.util.List;

@WebServlet("/CsvToJsonServlet")
@MultipartConfig
public class CsvToJsonServlet extends HttpServlet {
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

        String csvContent;
        try {
            csvContent = fetchFileFromDatabase(userId, fileName);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error fetching file from database: " + e.getMessage() + "\"}");
            return;
        }

        if (csvContent == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("{\"error\": \"File not found in the database.\"}");
            return;
        }

        String jsonResult;
        try {
            jsonResult = convertCsvToJson(new ByteArrayInputStream(csvContent.getBytes()));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error during conversion: " + e.getMessage() + "\"}");
            return;
        }

        String convertedFilePath = saveConvertedFile(jsonResult, userId);

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("downloadLink", request.getRequestURL().toString() + "?action=download&filePath=" + convertedFilePath);
        jsonResponse.put("fileContent", jsonResult);
        response.getWriter().write(jsonResponse.toString());

        // Use a unique filename
        String filename = fileName.substring(0,fileName.length()-4) + generateRandomNumber() + ".json";
        try {
            insertFileToDatabase(userId, filename, jsonResult);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error saving file to database: " + e.getMessage() + "\"}");
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

        response.setContentType("application/json");
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

    private void setCORSHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*"); // Allow multiple origins or use specific origin.
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    private String fetchFileFromDatabase(String userId, String fileName) throws SQLException {
        String csvContent = null;
        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT file FROM files WHERE user_id = ? AND filename = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, userId);
                preparedStatement.setString(2, fileName);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        csvContent = resultSet.getString("file");
                    }
                }
            }
        }
        return csvContent;
    }

    private String convertCsvToJson(InputStream csvInputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(csvInputStream));
        String line;
        List<String> headers = new ArrayList<>();
        JSONArray jsonArray = new JSONArray();

        if ((line = reader.readLine()) != null) {
            String[] headerArray = line.split(",");
            for (String header : headerArray) {
                headers.add(header.trim());
            }
        }

        while ((line = reader.readLine()) != null) {
            String[] rowValues = line.split(",");
            JSONObject jsonObject = new JSONObject();
            for (int i = 0; i < headers.size(); i++) {
                jsonObject.put(headers.get(i), rowValues[i].trim());
            }
            jsonArray.put(jsonObject);
        }

        reader.close();
        return jsonArray.toString(4);
    }

    private String saveConvertedFile(String jsonResult, String userId) throws IOException {
        Path uploadPath = Paths.get(getServletContext().getRealPath(UPLOAD_DIR), userId);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate a random 3-digit number
        String convertedFileName = userId + "_converted_" + generateRandomNumber() + ".json"; // Append the random number to the file name

        Path filePath = uploadPath.resolve(convertedFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(jsonResult);
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

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "*"); // Allow multiple origins
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
