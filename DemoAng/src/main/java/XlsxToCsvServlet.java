import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Iterator;

@WebServlet("/XlsxToCsvServlet")
@MultipartConfig
public class XlsxToCsvServlet extends HttpServlet {
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

        byte[] xlsxContent;
        try {
            xlsxContent = fetchFileFromDatabase(userId, fileName);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error fetching file from database: " + e.getMessage() + "\"}");
            return;
        }

        if (xlsxContent == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("{\"error\": \"File not found in the database.\"}");
            return;
        }

        String csvFilePath;
        String csvContent;
        try {
            // Convert XLSX content to CSV
            csvContent = convertXlsxToCsv(new ByteArrayInputStream(xlsxContent));
            csvFilePath = saveConvertedFile(csvContent, userId);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error during conversion: " + e.getMessage() + "\"}");
            return;
        }

        // Save the converted file to the database
        String convertedFileName = "converted_" + generateRandomNumber() + ".csv";
        try {
            insertFileToDatabase(userId, convertedFileName, csvContent);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error saving file to database: " + e.getMessage() + "\"}");
            return;
        }

        // Prepare the response JSON
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("downloadLink", request.getRequestURL().toString() + "?action=download&filePath=" + csvFilePath);
        response.getWriter().write(jsonResponse.toString());
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

        response.setContentType("text/csv");
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
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    private byte[] fetchFileFromDatabase(String userId, String fileName) throws SQLException {
        byte[] fileContent = null;
        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT file FROM files WHERE user_id = ? AND filename = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, userId);
                preparedStatement.setString(2, fileName);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        fileContent = resultSet.getBytes("file");
                    }
                }
            }
        }
        return fileContent;
    }

    private String convertXlsxToCsv(InputStream xlsxInputStream) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook(xlsxInputStream);
        XSSFSheet sheet = workbook.getSheetAt(0);
        StringBuilder csvContent = new StringBuilder();

        Iterator<Row> rowIterator = sheet.iterator();
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            Iterator<Cell> cellIterator = row.iterator();
            StringBuilder rowContent = new StringBuilder();

            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                rowContent.append(cell.toString()).append(",");
            }
            // Remove the trailing comma
            rowContent.setLength(rowContent.length() - 1);
            csvContent.append(rowContent).append("\n");
        }
        workbook.close();
        return csvContent.toString();
    }

    private String saveConvertedFile(String csvContent, String userId) throws IOException {
        Path uploadPath = Paths.get(getServletContext().getRealPath(UPLOAD_DIR), userId);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String convertedFileName = userId + "_converted_" + generateRandomNumber() + ".csv";
        Path filePath = uploadPath.resolve(convertedFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(csvContent);
        }

        return UPLOAD_DIR + userId + "/" + convertedFileName;
    }

    private void insertFileToDatabase(String userId, String convertedFileName, String fileContent) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            String query = "INSERT INTO files (user_id, filename, file) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, userId);
                preparedStatement.setString(2, convertedFileName);
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
}
