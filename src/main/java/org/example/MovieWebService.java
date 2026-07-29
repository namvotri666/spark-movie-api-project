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

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

public class MovieWebService {
    private static final String DB_URL = "jdbc:sqlite:../movies.db";
    private static final Map<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();

    private static boolean isAllowed(String user) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestHistory.computeIfAbsent(user, k -> new LinkedList<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > 60000) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= 10) {
                return false;
            }
            int recentCount = 0;
            for (Long ts : timestamps) {
                if (now - ts <= 5000) {
                    recentCount++;
                }
            }
            if (recentCount >= 2) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    public static void main(String[] args) {
        Spark.port(8080);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterAccess(10, TimeUnit.SECONDS)
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .recordStats()
                .build();
        try {
            Thread.sleep(2000);
            System.out.println("Ti le hit rate: " + (cache.stats().hitRate() * 100) + "%");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        /* Chinh sua tren server github */
        Spark.post("/login", (req, res) -> {
            res.type("application/json; charset=utf-8");
            try {
                // 1. Đọc req.body() và parse thành LoginRequest Object bằng Gson
                LoginRequest loginReq = gson.fromJson(req.body(), LoginRequest.class);
                // Kiểm tra xem body truyền lên có bị null không
                if (loginReq == null || loginReq.getUsername() == null || loginReq.getPassword() == null) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "Username và password không được để trống"));
                }
                String username = loginReq.getUsername();
                String password = loginReq.getPassword();
                // 2. Kiểm tra thông tin đăng nhập
                if ("nam".equals(username) && "admin".equals(password)) {
                    // Lưu thông tin vào Session
                    req.session(true).attribute("user", username);
                    return gson.toJson(Map.of("message", "Login successful for user: " + username));
                }

                // 3. Đúng định dạng nhưng sai tài khoản/mật khẩu
                res.status(401);
                return gson.toJson(Map.of("error", "Invalid username or password"));

            } catch (Exception e) {
                // 4. Bắt lỗi nếu client gửi JSON sai cú pháp
                res.status(400);
                return gson.toJson(Map.of("error", "Định dạng JSON không hợp lệ"));
            }
        });

        Spark.before("/api/*", (req, res) -> {
            String user = req.session().attribute("user");
            if (user == null) {
                Spark.halt(401, "{\"error\": \"Unauthorized. Please login first at /login\"}");
            }
            if (!isAllowed(user)) {
                Spark.halt(429, "{\"error\": \"Too Many Requests. Max 2 per 5s and 10 per 60s.\"}");
            }
            res.type("application/json; charset=utf-8");
        });

        // API Endpoint: GET /api/movies/:id
        Spark.get("/api/movies/:id", (req, res) -> {
            res.type("application/json; charset=utf-8");

            String rawId = req.params(":id");

            String cachedResult = cache.getIfPresent(rawId);
            if (cachedResult != null) {
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
