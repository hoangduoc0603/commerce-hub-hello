-- G0-27 bước 7: đặt tên rõ cho ràng buộc độ dài name; greeting_name_check là tên PostgreSQL tự sinh.
-- Mức khóa: ACCESS EXCLUSIVE trên greeting (đo trên postgres:18.7, G0-27), chặn cả SELECT. Chỉ đổi catalog nên giữ
-- khóa rất ngắn, nhưng phải CHỜ mọi transaction đang đọc bảng; trong lúc chờ, truy vấn mới xếp hàng sau nó.
-- lock_timeout 5 s (spring.flyway.init-sqls) giới hạn khoảng chờ đó; lỗi thì Job thử lại (backoffLimit 2).
-- Tương thích ngược: code không dùng tên ràng buộc, bản N-1 chạy được trên schema này.
ALTER TABLE greeting RENAME CONSTRAINT greeting_name_check TO greeting_name_length;
