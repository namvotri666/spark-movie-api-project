# Phân tích Yêu Cầu Bài 4: Tự thiết kế Cache có tính năng TTL (Time-To-Live)

## 1. Mục tiêu chung
Xây dựng một bộ nhớ đệm (Cache) tùy chỉnh cho web service của "Bài 3" nhằm giảm tải số lượng truy vấn xuống cơ sở dữ liệu (Database). Khi người dùng truy cập vào một URL (tương ứng với một ID phim) đã từng được gọi trước đó, hệ thống sẽ trả về dữ liệu trực tiếp từ Cache thay vì phải query lại vào Database.

## 2. Các tính năng chính của Cache
Cache được yêu cầu hoạt động dựa trên cấu trúc dữ liệu `Map<>` và có cơ chế **TTL (Thời gian sống)**. Cụ thể có 2 cơ chế giới hạn thời gian sống cho một phần tử trong cache:
- **Write TTL (sau m giây):** Phần tử sẽ bị xóa sau `m` giây kể từ lúc được thêm mới (`put`) vào Cache, bất kể nó có được đọc hay không. Điều này đảm bảo dữ liệu không bị cũ (stale data).
- **Idle TTL / Read TTL (sau n giây):** Nếu trong vòng `n` giây mà phần tử không có bất kỳ request đọc nào (không có thao tác `get`), phần tử đó cũng sẽ tự động bị xóa để giải phóng bộ nhớ.

**Lưu ý:** Bất cứ điều kiện nào (`m` hoặc `n`) đến giới hạn trước, phần tử đó sẽ bị xóa.

## 3. Cấu trúc thiết kế Class (Class Signature)
Yêu cầu bắt buộc là tạo ra một class: `CacheTTL<K, V> implements Map<K, V>`.

Các phương thức (methods) cụ thể cần đảm bảo:
- `CacheTTL(int n, int m)`: Hàm khởi tạo (Constructor) nhận 2 tham số:
  - `n`: Thời gian sống tính từ lần truy cập (get) cuối cùng theo giây.
  - `m`: Thời gian sống tính từ lúc phần tử được ghi vào theo giây.
- `V get(K key)`: Trả về `value` tương ứng với `key` trong cache. Khi hàm này được gọi thành công, thời gian truy cập của phần tử phải được "làm mới" để không vi phạm quy tắc `n` giây.
- `void put(K key, V value)`: Đẩy một cặp khóa - giá trị vào Cache. *(Lưu ý: interface `Map` chuẩn của Java quy định hàm `put` trả về kiểu `V`, tuy nhiên theo mô tả đề bài thì dùng `void`. Khi code thực tế bạn có thể trả về `V` theo đúng chuẩn interface, hoặc trả `null` ngầm).*
- `Map<K, V> getMap()`: Lấy ra cấu trúc Map hiện tại chứa tất cả các phần tử vẫn còn đang hiệu lực (chưa bị xóa do quá hạn TTL).
- `int getHitRate()`: Hàm tính và trả về tỷ lệ Cache Hit (Tỷ lệ trúng cache). 
  - **Hit:** Tìm thấy dữ liệu trong Cache.
  - **Miss:** Không tìm thấy dữ liệu, phải gọi xuống DB.
  - **Công thức:** `(Tổng số lần Hit / Tổng số lần gọi hàm get) * 100`.

## 4. Phân tích các Thách thức & Hướng giải quyết Kỹ thuật (Technical Aspects)

1. **Lưu trữ mốc thời gian cho từng phần tử:**
   - Để biết khi nào cần xóa, mỗi phần tử được lưu không chỉ là dữ liệu `V` (ví dụ chuỗi JSON) mà cần được bọc (wrap) bằng một class/record nội bộ chứa thêm 2 trường: `createdTime` (thời điểm ghi) và `lastAccessedTime` (thời điểm truy cập cuối).

2. **Cách dọn dẹp Cache (Eviction Policy):** Có 2 hướng thiết kế:
   - *Thụ động (Lazy/Passive Expire):* Mỗi lần gọi `get()`, `put()`, hoặc `getMap()`, hệ thống sẽ kiểm tra xem (các) phần tử đó đã quá `m` hoặc `n` giây chưa. Nếu quá thì tiến hành xóa ngay lúc đó rồi coi như không tồn tại. Dễ code, ít tốn tài nguyên chạy ngầm.
   - *Chủ động (Active Expire):* Sử dụng một luồng chạy ngầm (Daemon Thread) quét toàn bộ Cache định kỳ (vd: mỗi 1 giây) và gỡ bỏ các phần tử quá hạn. Phức tạp hơn nhưng đúng với khái niệm "Cache tự xóa đi" của đề bài hơn.

3. **Xử lý Đa luồng (Concurrency):**
   - Web service (ví dụ sử dụng Spark Java) luôn hoạt động ở chế độ đa luồng, có thể nhận nhiều request cùng một thời điểm.
   - Vì thế class `CacheTTL` phải **Thread-safe** (an toàn đa luồng). Nếu sử dụng `HashMap` thông thường làm lõi lưu trữ sẽ gây lỗi hệ thống. Giải pháp là bọc toàn bộ bằng khối `synchronized` hoặc sử dụng các class hỗ trợ như `ConcurrentHashMap`.

4. **Tích hợp vào Web Service (Bài 3):**
   - CacheTTL sẽ được khởi tạo như một biến toàn cục (ví dụ `CacheTTL<String, String> cache = new CacheTTL<>(60, 300);`) nằm bên ngoài Route `Spark.get(...)`.
   - Mỗi khi user gửi GET request: 
     - **Bước 1:** Gọi `cache.get(id)`. Nếu có dữ liệu -> Trả về lập tức (Hit ++).
     - **Bước 2:** Nếu `cache.get(id)` bằng null -> Lấy từ Database (Miss). Nếu DB có dữ liệu -> Chuyển thành JSON -> Đưa vào `cache.put(id, jsonResult)` -> Trả về cho user.
