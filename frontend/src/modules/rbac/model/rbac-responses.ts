import { z } from "zod";

/**
 * Hai endpoint tạo của RBAC trả về thân là chính id vừa tạo (một chuỗi UUID trần, không bọc object).
 * Kiểm bằng schema thay vì ép kiểu: nếu hợp đồng đổi thành { id } thì lỗi nổ ngay tại lời gọi API,
 * chứ không lặng lẽ truyền một object đi khắp nơi dưới cái tên "string".
 */
export const createdIdSchema = z.string().uuid();
