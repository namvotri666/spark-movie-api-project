# Movie Cache Service 🎬

Một Web Service RESTful hạng nhẹ được viết bằng **Java (Spark Framework)**, cung cấp API tra cứu thông tin phim từ cơ sở dữ liệu SQLite, đồng thời được tối ưu hóa hiệu suất phản hồi thông qua **Guava Cache**.

## 🚀 Tính năng chính
- **Tra cứu Phim (REST API):** Cung cấp endpoint `GET /api/movies/:id` để lấy thông tin chi tiết phim dưới định dạng JSON.
- **Tối ưu truy xuất (Caching):** Tích hợp thư viện Guava Cache để lưu tạm dữ liệu, giảm tải cho Database:
  - Dữ liệu phim được lưu vào bộ nhớ cache sau lần truy vấn thành công đầu tiên.
  - **TTL (Time-to-Live):** Tự động xóa cache của một bộ phim nếu:
    - Không có request nào truy cập tới nó trong vòng **10 giây** (Expire After Access).
    - Hoặc đã tồn tại trong cache được **20 giây** kể từ lúc tạo (Expire After Write).
  - Hỗ trợ in thống kê Hit Rate định kỳ ra Console.
- **Bảo mật Database:** Truy vấn dữ liệu an toàn bằng `PreparedStatement` để phòng chống SQL Injection.

## 🛠 Các công nghệ sử dụng
- **Spark Java:** Framework web backend cực nhẹ.
- **SQLite JDBC:** Kết nối và thao tác với Database SQLite.
- **Google Gson:** Chuyển đổi dữ liệu giữa Map/Object và chuỗi JSON.
- **Google Guava:** Triển khai cơ chế In-memory Caching với các chiến lược TTL phức tạp.

## ⚙️ Yêu cầu hệ thống
- **Java 21+**
- **Maven**
- **Cơ sở dữ liệu:** File `movies.db` phải tồn tại ở đường dẫn tương đối `../Bai2/movies.db` (so với thư mục gốc khi chạy project).

## 📡 Cấu trúc API

**Endpoint:** `GET http://localhost:8080/api/movies/:id`

**Response thành công (200 OK):**
```json
{
  "country": "USA",
  "actors": [
    "Tom Holland",
    "Zendaya"
  ],
  "release_year": "2021",
  "directors": "Jon Watts",
  "id": 1,
  "genres": "Action, Adventure",
  "title": "Spider-Man: No Way Home"
}
```

**Response lỗi (404 Not Found):**
```json
{
  "message": "Không tìm thấy phim với ID: 9999"
}
```

**Response lỗi (500 Internal Server Error):**
```json
{
  "error": "Có lỗi xảy ra khi đọc DB: [Nội dung lỗi chi tiết]"
}
```
