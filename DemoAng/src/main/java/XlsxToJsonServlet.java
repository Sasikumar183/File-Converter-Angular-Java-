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
import java.util.Iterator;
import java.util.Random;

@WebServlet("/XlsxToJsonServlet")
public class XlsxToJsonServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/userDB";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "password";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCORSHeaders(response);

        String userId = request.getParameter("userId");
        String fileName = request.getParameter("filename");

        // Validate input parameters
        if (userId == null || fileName == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"User ID or file name not provided.\"}");
            return;
        }

        System.out.println("Received userId: " + userId + ", fileName: " + fileName); // Log received parameters

        // Fetch XLSX file content from the database
        byte[] xlsxFile = null;
        try {
            xlsxFile = fetchFileFromDatabase(userId, fileName);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error fetching file from database: " + e.getMessage() + "\"}");
            e.printStackTrace();  // Log the stack trace
            return;
        }

        if (xlsxFile == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("{\"error\": \"File not found in the database.\"}");
            return;
        }

        // Convert the fetched XLSX content to JSON
        String jsonString;
        try {
            jsonString = convertXlsxToJson(xlsxFile);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error during conversion: " + e.getMessage() + "\"}");
            e.printStackTrace();  // Log the stack trace
            return;
        }

        // Save the converted JSON file to the database
        String jsonFileName = fileName.substring(0,fileName.length()-5) + generateRandomNumber() + ".json";
        try {
            insertFileToDatabase(userId, jsonFileName, jsonString);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Error saving file to database: " + e.getMessage() + "\"}");
            e.printStackTrace();  // Log the stack trace
            return;
        }

        // Create a JSON response with a download link
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("downloadLink", request.getRequestURL().toString() + "?action=download&fileName=" + jsonFileName);
        response.setContentType("application/json");
        response.getWriter().write(jsonResponse.toString());
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");

        // Handle the download action
        if ("download".equals(action)) {
            String fileName = request.getParameter("fileName");

            if (fileName == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\": \"File name not provided.\"}");
                return;
            }

            // Fetch the JSON file content from the database
            String jsonFileContent = null;
            try {
                jsonFileContent = fetchJsonFileFromDatabase(fileName);
            } catch (SQLException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\": \"Error fetching JSON file from database: " + e.getMessage() + "\"}");
                e.printStackTrace();  // Log the stack trace
                return;
            }

            if (jsonFileContent == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\": \"File not found in the database.\"}");
                return;
            }

            // Set the response content type and download the file
            response.setContentType("application/json");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            response.getWriter().write(jsonFileContent);
        }
    }

    private String fetchJsonFileFromDatabase(String fileName) throws SQLException {
        String jsonContent = null;

        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT file FROM files WHERE filename = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, fileName);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        jsonContent = resultSet.getString("file");
                    }
                }
            }
        }

        return jsonContent;
    }

    private byte[] fetchFileFromDatabase(String userId, String fileName) throws SQLException {
        byte[] xlsxFile = null;

        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT file FROM files WHERE user_id = ? AND filename = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, userId);
                preparedStatement.setString(2, fileName);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        xlsxFile = resultSet.getBytes("file");
                    }
                }
            }
        }

        return xlsxFile;
    }

    private void insertFileToDatabase(String userId, String filename, String fileContent) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            String insertQuery = "INSERT INTO files (user_id, filename, file) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                preparedStatement.setString(1, userId);
                preparedStatement.setString(2, filename);
                preparedStatement.setString(3, fileContent);
                preparedStatement.executeUpdate();
                System.out.println("File saved to the database with name: " + filename);
            }
        }
    }

    @SuppressWarnings("resource")
    private String convertXlsxToJson(byte[] xlsxContent) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxContent));
        XSSFSheet sheet = workbook.getSheetAt(0);

        JSONArray jsonArray = new JSONArray();

        Iterator<Row> rowIterator = sheet.iterator();
        Row headerRow = rowIterator.next(); // The first row contains the headers
        int columnCount = headerRow.getPhysicalNumberOfCells();

        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            JSONObject jsonObject = new JSONObject();
            for (int i = 0; i < columnCount; i++) {
                Cell cell = row.getCell(i);
                String header = headerRow.getCell(i).getStringCellValue();
                String cellValue = cell != null ? cell.toString() : "";
                jsonObject.put(header, cellValue);
            }
            jsonArray.put(jsonObject);
        }

        workbook.close();
        return jsonArray.toString();
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
