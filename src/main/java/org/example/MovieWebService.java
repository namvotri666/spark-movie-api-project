package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import spark.Spark;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class MovieWebService {

    private static final String DB_URL = "jdbc:sqlite:../Bai2/movies.db";


    public static void main(String[] args) {
        Spark.port(8080);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterAccess(10, TimeUnit.SECONDS)
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .recordStats()
                .build();
        try{
            Thread.sleep(2000);
            System.out.println("Ti le hit rate: " + (cache.stats().hitRate() * 100) + "%");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
  /* Chinh sua tren client*/
        // API Endpoint: GET /api/movies/:id
        Spark.get("/api/movies/:id", (req, res) -> {
            res.type("application/json; charset=utf-8");

            String rawId = req.params(":id");

            String cachedResult = cache.getIfPresent(rawId);
            if(cachedResult != null){
                return cachedResult;
            }
            // 1. Dùng PreparedStatement để chống SQL Injection
            String sql = "SELECT * FROM movies WHERE id = ?";

            try (Connection conn = DriverManager.getConnection(DB_URL);
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {

                // Gán giá trị tham số an toàn
                pstmt.setString(1, rawId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    // Nếu tìm thấy phim trong CSDL
                    if (rs.next()) {
                        Map<String, Object> movie = new HashMap<>();
                        movie.put("id", rs.getInt("id"));
                        movie.put("title", rs.getString("title"));
                        movie.put("release_year", rs.getString("release_year"));
                        movie.put("country", rs.getString("country"));
                        movie.put("genres", rs.getString("genres"));
                        movie.put("directors", rs.getString("directors"));

                        String rawActors = rs.getString("actors");
                        List<String> processedActors = new ArrayList<>();

                        if (rawActors != null && !rawActors.isEmpty()) {
                            String[] actorArray = rawActors.split(",\\s*");

                            // Phục vụ đặt Breakpoint
                            for (String actorName : actorArray) {
                                String trimmedName = actorName.trim();
                                processedActors.add(trimmedName);
                            }
                        }
                        movie.put("actors", processedActors);
                        // Trả về đối tượng JSON duy nhất và lưu vào cache
                        String jsonResult = gson.toJson(movie);
                        cache.put(rawId, jsonResult);
                        return jsonResult;
                    }
                }
                // 2. Nếu không tìm thấy phim tương ứng với ID -> Trả về HTTP 404
                res.status(404);
                return gson.toJson(Map.of("message", "Không tìm thấy phim với ID: " + rawId));

            } catch (Exception e) {
                // 3. Xử lý lỗi phía Server -> Trả về HTTP 500
                res.status(500);
                return gson.toJson(Map.of("error", "Có lỗi xảy ra khi đọc DB: " + e.getMessage()));
            }
        });
        System.out.println("Web service đang chạy tại: http://localhost:8080");
    }
}
