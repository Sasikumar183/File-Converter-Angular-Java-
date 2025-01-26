import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.sql.*;
import java.util.Random;

@WebServlet("/JsonToXlsxServlet")
public class JsonToXlsxServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/userDB";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "password";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCORSHeaders(response);

        String userId = request.getParameter("userId");
        String fileName = request.getParameter("filename");

        if (userId == null || fileName == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"User ID or file name not provided.\"}");
            return;
        }

        // Fetch JSON file content from the database
        String jsonContent = null;
        try {
            jsonContent = fetchFileFromDatabase(userId, fileName);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error fetching file from database: " + e.getMessage() + "\"}");
            return;
        }

        if (jsonContent == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("{\"error\": \"File not found in the database.\"}");
            return;
        }

        // Convert the fetched JSON content to XLSX
        byte[] xlsxFile;
        try {
            xlsxFile = convertJsonToXlsx(jsonContent);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error during conversion: " + e.getMessage() + "\"}");
            return;
        }

        // Save the converted XLSX file to the database
        String filename = fileName.substring(0,fileName.length()-5) + generateRandomNumber() + ".xlsx";

        try {
            insertFileToDatabase(userId, filename, xlsxFile);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error saving file to database: " + e.getMessage() + "\"}");
            return;
        }

        // Create a JSON response with a download link
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("downloadLink", request.getRequestURL().toString() + "?action=download&fileName=" + filename);
        response.setContentType("application/json");
        response.getWriter().write(jsonResponse.toString());
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

    private void insertFileToDatabase(String userId, String filename, byte[] fileContent) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            String insertQuery = "INSERT INTO files (user_id, filename, file) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setString(1, userId);
                preparedStatement.setString(2, filename);
                preparedStatement.setBytes(3, fileContent);
                preparedStatement.executeUpdate();
            }
        }
    }

    @SuppressWarnings("resource")
	private byte[] convertJsonToXlsx(String jsonString) throws Exception {
        JSONArray jsonArray = new JSONArray(jsonString);
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Sheet 1");

        if (jsonArray.length() == 0) {
            throw new Exception("JSON array is empty.");
        }

        // Create headers
        JSONObject firstObject = jsonArray.getJSONObject(0);
        Row headerRow = sheet.createRow(0);
        int cellIndex = 0;
        for (String key : firstObject.keySet()) {
            Cell cell = headerRow.createCell(cellIndex++);
            cell.setCellValue(key);
        }

        // Create rows
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            Row row = sheet.createRow(i + 1); // Skip header row
            cellIndex = 0;
            for (String key : firstObject.keySet()) {
                Cell cell = row.createCell(cellIndex++);
                cell.setCellValue(jsonObject.optString(key, ""));
            }
        }

        // Convert to byte array
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        workbook.write(byteArrayOutputStream);
        workbook.close();

        return byteArrayOutputStream.toByteArray();
    }

    private String generateRandomNumber() {
        Random random = new Random();
        return String.valueOf(random.nextInt(1000)); // Generate a random number
    }

    private void setCORSHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
