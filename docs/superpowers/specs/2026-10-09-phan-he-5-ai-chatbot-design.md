# Phân hệ 5 — AI Chatbot: Thiết kế

> **Ghi chú quy trình.** Phân hệ này được brainstorm/spec/plan/triển khai trong lúc người dùng đã
> đi ngủ, theo uỷ quyền đứng rõ ràng đã đưa ra trong phiên: *"xong thì cứ tự merge tự chạy, đừng
> hỏi... cái gì k rõ thì tra web xong cứ thế mà làm. đừng hỏi"* và *"xong thì cũng tự chạy phase sau
> nhé"*. Vì vậy tài liệu này **không đi qua vòng hỏi-đáp làm rõ thông thường** của quy trình
> brainstorming — mọi điểm cần quyết định đều được tự nghiên cứu (web) và tự quyết, ghi lại thành
> **Ruling** ngay tại chỗ với lý do, giống cách ledger của Phân hệ 4 đã làm. Người dùng đọc lại tài
> liệu này khi quay lại và có toàn quyền đảo ngược bất kỳ Ruling nào.

## 1. Bối cảnh & Mục tiêu

Roadmap tổng (`docs/superpowers/specs/2026-09-27-education-erp-architecture-design.md`, mục 8) ghi
toàn bộ phạm vi Phân hệ 5 trong đúng một câu: *"chat cho học viên, RAG trên danh mục khóa học đang
mở, cần dữ liệu từ Phân hệ 2."* Đây là toàn bộ văn bản gốc có sẵn — không có mục nào khác trong
roadmap nhắc tới AI/chat/RAG/embedding (đã grep toàn bộ file để xác nhận).

**Mục tiêu**: học viên (và admin, để hỗ trợ/kiểm thử) đặt câu hỏi bằng tiếng Việt tự nhiên về các
khóa học ĐANG MỞ (`Course.active == true`) của trung tâm — ví dụ "có khóa tiếng Anh giao tiếp buổi
tối không", "khóa Toán 9 học phí bao nhiêu" — và nhận câu trả lời được chatbot tổng hợp, dựa trên dữ
liệu khóa học thật lấy từ `modules.courses`, không bịa đặt thông tin không có trong hệ thống.

**Đối tượng dùng**: học viên (role `STUDENT`) là đối tượng chính nêu trong roadmap. Admin được cấp
quyền thêm để kiểm thử/hỗ trợ (nhất quán với mọi phân hệ trước — admin luôn có mọi quyền ở scope
`ORGANIZATION`). Giáo viên KHÔNG được cấp quyền — roadmap không nhắc tới giáo viên dùng tính năng
này, và không có bằng chứng nào trong hồ sơ nghiệp vụ Teacher cần nó.

## 2. Phạm vi (In / Out)

**Trong phạm vi:**
- Một endpoint chat: học viên gửi một câu hỏi (kèm lịch sử hội thoại hiện tại của phiên trình
  duyệt), nhận lại MỘT câu trả lời đầy đủ (không streaming — xem Ruling R4).
- Truy xuất (retrieval) các khóa học đang mở liên quan tới câu hỏi bằng full-text search của chính
  Postgres đang dùng (xem Ruling R1) — không thêm hạ tầng vector DB.
- Sinh câu trả lời (generation) bằng một LLM thật qua Spring AI, mặc định Anthropic Claude Sonnet 5
  (xem Ruling R2), with pluggable thiết kế để đổi nhà cung cấp sau này mà không đổi code nghiệp vụ.
- Giới hạn tần suất gọi theo tài khoản (rate limit) bằng Redis sẵn có — gọi LLM thật tốn tiền thật,
  đây là biện pháp bảo vệ chi phí/chống lạm dụng, không phải tính năng thừa.
- Luật chống bịa đặt (anti-hallucination): system prompt bắt buộc chatbot chỉ nói về khóa học có
  trong ngữ cảnh truy xuất được, và phải nói rõ "chưa tìm thấy khóa phù hợp" khi truy xuất rỗng thay
  vì đoán.
- Frontend: một trang chat cho học viên, mirror đúng cấu trúc `entities/academic` +
  `modules/academic` vừa hoàn thành ở Phân hệ 4, có mục điều hướng riêng (xem Ruling R5).

**Ngoài phạm vi (ghi rõ để không ai hiểu nhầm là bỏ sót):**
- **Không lưu lịch sử hội thoại ở backend.** Mỗi phiên chat chỉ tồn tại trong bộ nhớ trình duyệt;
  tải lại trang là mất lịch sử. Lý do: dữ liệu học viên nhạy cảm (có thể là trẻ vị thành niên),
  chưa có yêu cầu nghiệp vụ nào cần xem lại lịch sử, và một bảng `chat_messages` không giới hạn kích
  thước là một cam kết vận hành (lưu trữ, chính sách xoá) không nên nhận khi chưa ai yêu cầu.
- **Không streaming token-by-token.** Trả lời đầy đủ một lần. Đây là một tinh giản UX, không phải
  cắt bớt giá trị cốt lõi ("chat cho học viên, RAG" không bắt buộc streaming).
- **Không embeddings/vector search** — xem Ruling R1.
- **Không chat về bất kỳ chủ đề nào khác** ngoài danh mục khóa học đang mở — không trả lời câu hỏi
  chung chung không liên quan tới trung tâm (system prompt giới hạn phạm vi).
- **Không có giao diện admin xem lại nội dung chat** của học viên (hệ quả trực tiếp của việc không
  lưu).

## 3. Kiến trúc — hai phương án đã cân nhắc

**Phương án A (đã chọn): Postgres full-text search + Spring AI ChatClient qua một facade mỏng.**
Retrieval là một method mới trên `CoursesManagement` dùng `tsvector`/GIN có sẵn trong Postgres (đã
dùng ở dự án: `postgres:16` cả prod lẫn test). Generation là một `@Service` mỏng trong
`integrations.ai` bọc quanh `ChatClient` của Spring AI (thư viện chính thức của Spring, GA từ
5/2025, dòng 1.0.x tương thích Spring Boot 3.4/3.5). Không có bảng DB mới cho hội thoại.

**Phương án B (đã xem xét, không chọn): pgvector + embedding model + lưu lịch sử hội thoại.**
Chất lượng truy xuất ngữ nghĩa tốt hơn (bắt được từ đồng nghĩa), nhưng đòi hỏi: đổi image Postgres
production sang bản có pgvector, thêm một lệnh gọi embedding model (phụ thuộc bên ngoài thứ hai,
tốn thêm tiền mỗi lần), và một bảng lưu hội thoại nếu muốn phân tích dùng embedding đó về sau. Với
quy mô danh mục khóa học của MỘT trung tâm dạy thêm (ước tính vài chục khóa, không phải hàng nghìn),
lợi ích ngữ nghĩa không bù được chi phí vận hành/triển khai tăng thêm.

**Quyết định**: Phương án A. YAGNI — chọn giải pháp đơn giản nhất đáp ứng đúng yêu cầu ở quy mô thực
tế, giữ một đường nâng cấp rõ ràng (đổi cài đặt bên trong facade `searchOpenCourses`/`AiChatClient`,
không đổi hợp đồng gọi) nếu danh mục khóa học lớn lên đáng kể sau này.

### 3.1. Backend — module mới `integrations.ai`

Mirror đúng cấu trúc `integrations.payment` (interface/provider/config/secret-qua-env-var), nhưng
đơn giản hơn MỘT bậc: Spring AI's `ChatClient` **đã tự là** lớp trừu tượng đa nhà cung cấp (đổi từ
Anthropic sang OpenAI/Ollama chỉ là đổi dependency + cấu hình, không đổi code gọi `ChatClient`), nên
không cần một `interface` + nhiều `@Component` triển khai như VNPay/MoMo — chỉ cần MỘT `@Service`
bọc `ChatClient` để `modules.chat` không phải import thẳng type của Spring AI (đúng quy ước
"integrations.* bọc thư viện bên thứ ba", mirror cách `modules.billing` không bao giờ import SDK
VNPay/MoMo trực tiếp).

```java
package com.eduerp.integrations.ai;

public record AiChatTurn(Role role, String content) {
    public enum Role { USER, ASSISTANT }
}

public class AiProviderException extends RuntimeException { /* bọc lỗi timeout/HTTP từ provider */ }

@Service
public class AiChatClient {
    // groundingContext = văn bản mô tả các khóa học truy xuất được, chèn vào system prompt.
    // history = toàn bộ lượt hỏi/đáp trước đó trong phiên (gửi lên từ frontend, không lưu).
    public String reply(String groundingContext, List<AiChatTurn> history) { ... }
}
```

**Ruling R2 — nhà cung cấp LLM & model mặc định.** Dùng Spring AI's Anthropic starter
(`spring-ai-starter-model-anthropic`, BOM `spring-ai-bom:1.0.9` — bản patch mới nhất dòng 1.0.x tại
thời điểm viết, tương thích Spring Boot 3.4/3.5), model mặc định `claude-sonnet-5` (theo đúng hướng
dẫn "khi xây dựng ứng dụng AI, mặc định dùng model Claude mới nhất và mạnh nhất" — Sonnet là lựa
chọn cân bằng chi phí/chất lượng hợp lý cho một chatbot CSKH, không cần tới Opus). **Đây là điểm
DUY NHẤT trong toàn phân hệ cần người dùng tự cung cấp thật khi quay lại máy**: biến môi trường
`ANTHROPIC_API_KEY` (mặc định rỗng trong `application.yml`, mirror đúng cách `MOMO_SECRET_KEY`/
`VNPAY_HASH_SECRET` để trống — "secret thật đọc từ biến môi trường, không hardcode"). Không có khoá
thật, `AiChatClient` thật sẽ ném `AiProviderException` khi gọi — test dùng một `AiChatClient` giả
lập (subclass ghi đè `reply(...)`, mirror cách `PaymentGatewayClientResolverTest` dùng
`record StubClient implements PaymentGatewayClient`), nên toàn bộ test suite biên dịch và chạy xanh
mà không cần khoá thật.

### 3.2. Backend — module mới `modules.chat`

```
modules/chat/
  ChatConstants.java          -- Resources.CHAT, Actions.*, Limits.MAX_MESSAGE_LENGTH,
                                  Limits.MAX_MESSAGES_PER_MINUTE, Limits.MAX_RETRIEVED_COURSES
  ChatException.java          -- sealed root (mirror AcademicException)
  TooManyMessagesException.java      -- 429, rate limit vượt quá
  MessageTooLongException.java       -- 400
  package-info.java
  dto/
    ChatTurnPayload.java       -- (role: "USER"|"ASSISTANT", content: String) - lịch sử từ client
    AskChatbotRequest.java     -- (message: String, history: List<ChatTurnPayload>)
    AskChatbotResponse.java    -- (reply: String)
    package-info.java          -- @NamedInterface("dto")
  usecase/
    AskChatbot.java            -- gọi CoursesManagement.searchOpenCourses, dựng system prompt,
                                   gọi AiChatClient.reply, kiểm rate limit TRƯỚC khi gọi LLM
    package-info.java
  internal/
    rules/ChatPromptRules.java -- HÀM THUẦN: dựng system prompt + groundingContext từ
                                   List<OpenCourseSummary>, không I/O (mirror GradeRules)
    ChatRateLimiter.java        -- Redis INCR + EXPIRE theo accountId, cửa sổ 1 phút
                                   (mirror PermissionCacheService dùng StringRedisTemplate)
  web/
    ChatAdminController.java   -- POST /api/chat/messages, @PreAuthorize(AccessRules.CREATE_CHAT)
```

**Luồng `AskChatbot.execute`:**
1. Kiểm độ dài tin nhắn (`MessageTooLongException` nếu vượt `MAX_MESSAGE_LENGTH`, chặn một học
   viên gửi một đoạn văn khổng lồ làm phình prompt/tốn tiền).
2. `ChatRateLimiter.checkAndIncrement(actorAccountId)` — ném `TooManyMessagesException` (429) nếu
   vượt `MAX_MESSAGES_PER_MINUTE` (Ruling R3: giá trị khởi điểm là 10 tin nhắn/phút/tài khoản — đủ
   cho một cuộc hội thoại tự nhiên, đủ thấp để một tài khoản bị lộ không gây hoá đơn API bất ngờ).
3. `courses.searchOpenCourses(request.message(), ChatConstants.Limits.MAX_RETRIEVED_COURSES)` —
   gọi facade `modules.courses` (xem mục 3.3).
4. `ChatPromptRules.buildGroundingContext(matchedCourses)` — hàm thuần, dựng đoạn văn bản liệt kê
   các khóa học truy xuất được (tên, mô tả, học phí, số buổi chuẩn), hoặc một câu cố định
   "Không tìm thấy khóa học nào khớp" nếu danh sách rỗng — **không bao giờ để trống hoàn toàn**,
   vì một groundingContext rỗng dễ khiến LLM tự bịa.
5. `aiChatClient.reply(groundingContext, request.history() + tin nhắn mới)`.
6. Bọc bước 5 trong try/catch: `AiProviderException` → ném tiếp một `ChatException` nghiệp vụ
   (`ChatbotUnavailableException`, 503) để controller trả lỗi có `errorCode` thay vì 500 trống —
   đúng bài học Critical #1 của Phân hệ 4 (không bao giờ để lỗi hạ tầng rò ra thành 500 vô nghĩa).

**`ChatPromptRules` system prompt (luật chống bịa đặt, cố định, không cấu hình qua UI):**
> "Bạn là trợ lý tư vấn của một trung tâm dạy thêm tại Việt Nam. CHỈ trả lời dựa trên danh sách khóa
> học dưới đây — không bịa thêm khóa học, lịch học hay học phí nào không có trong danh sách. Nếu
> không có khóa nào phù hợp với câu hỏi, hãy nói rõ là chưa tìm thấy và đề nghị học viên liên hệ
> trung tâm. Luôn trả lời bằng tiếng Việt."

### 3.3. Mở rộng facade `CoursesManagement` (mirror tiền lệ Phân hệ 4)

Facade hiện tại KHÔNG có method liệt kê/tìm kiếm hàng loạt — mọi method hiện có nhận một id đơn lẻ.
Thêm đúng MỘT method đọc mới, theo đúng tiền lệ `getGradeColumns` của Phân hệ 4 (thêm facade method
khi có nơi gọi thật, không phải "lỡ thêm cho tiện"):

```java
public record OpenCourseSummary(UUID id, String code, String name, String description,
        Integer standardSessionCount, BigDecimal tuitionFee) {}

@Transactional(readOnly = true)
public List<OpenCourseSummary> searchOpenCourses(String query, int limit) {
    return courses.searchActiveByFullText(query, limit).stream().map(...).toList();
}
```

Repository (`CourseRepository`) thêm một query native:

```java
@Query(value = """
        SELECT * FROM courses
        WHERE active = true
          AND search_vector @@ plainto_tsquery('simple', :query)
        ORDER BY ts_rank(search_vector, plainto_tsquery('simple', :query)) DESC
        LIMIT :limit
        """, nativeQuery = true)
List<Course> searchActiveByFullText(@Param("query") String query, @Param("limit") int limit);
```

**Ruling R1 — full-text search thay vì vector embeddings**, đã giải thích ở mục 3. Chi tiết kỹ
thuật: cấu hình `'simple'` (không phải `'english'`) — Postgres không có cấu hình tiếng Việt dựng
sẵn, và `'simple'` chỉ tách từ/viết thường, không áp luật stemming tiếng Anh sẽ làm sai lệch từ
tiếng Việt. Cột `search_vector` là `GENERATED ALWAYS ... STORED` (tự động đồng bộ mỗi khi
`name`/`description` đổi, không cần code nào tự tay cập nhật nó), có index `GIN` riêng (migration
V26, xem mục 3.4).

**Trường hợp truy xuất rỗng** (câu hỏi không khớp khóa nào): `searchOpenCourses` trả danh sách
rỗng, không ném lỗi — cùng triết lý với `getGradeColumns` của Phân hệ 4 ("khóa không tồn tại/không
khớp là một danh sách rỗng có nghĩa, không phải một lỗi").

### 3.4. Migration mới

```sql
-- V26__add_course_search_vector.sql
ALTER TABLE courses ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(description, ''))
    ) STORED;

CREATE INDEX idx_courses_search_vector ON courses USING GIN (search_vector);
```

```sql
-- V27__seed_chat_rbac.sql
-- Mirror V25: KHÔNG tái dùng group "Tự phục vụ cơ bản" (group đó cũng gắn cho TEACHER) - một group
-- RIÊNG chỉ gắn cho STUDENT, đúng tinh thần "quyền của học viên không tự động chảy sang giáo viên
-- và ngược lại" đã thiết lập ở Phân hệ 4.
INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'CHAT', 'CREATE');

INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource = 'CHAT';  -- admin, group có sẵn từ V5

INSERT INTO permission_groups (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000004', 'Học viên hỏi chatbot',
     'Gửi câu hỏi cho chatbot tư vấn khóa học, scope PERSONAL');

INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000004', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'CHAT';

INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000004'); -- STUDENT
```

`AccessConstants.AccessRules.CREATE_CHAT` mang marker `@ORGANIZATION` như MỌI rule khác trong dự án
(khác với `*_GRADE` của Phân hệ 4) — ở đây KHÔNG có khái niệm "đúng lớp của ai", mọi học viên có
quyền `CHAT:CREATE` ở scope nào cũng được phép hỏi chatbot (không cần guard kiểm sở hữu như
`ClassGradeAccessGuard`).

### 3.5. Frontend

Mirror chính xác cấu trúc `entities/academic` + `modules/academic`:

```
entities/chat/
  model/chat-schema.ts    -- askChatbotResponseSchema, ChatTurn type, AskChatbotPayload interface
  api/chat-api.ts          -- chatApi.ask(payload)
  api/chat-keys.ts         -- không cần TanStack Query cache (mỗi câu hỏi là một mutation, không
                               phải một query có thể cache) - chỉ cần api object, không cần hook
                               useQuery riêng ở entity layer (khác academic, vì chat không "đọc lại"
                               cùng một dữ liệu nhiều lần theo key)
  index.ts

modules/chat/
  hooks/use-chat-controller.ts   -- state: mảng ChatTurn trong React state (KHÔNG persist), hàm
                                     gửi tin nhắn (useMutation), loading/error
  ui/chat-message-list.tsx        -- render thuần danh sách lượt hỏi/đáp
  ui/chat-input.tsx                -- ô nhập + nút gửi, Enter để gửi
  pages/chat-page.tsx              -- bọc <RequirePermission {...ACCESS_RULE.createChat}>
  index.ts                          -- export ChatPage
```

**Ruling R5 — có mục điều hướng riêng (khác tiền lệ Phân hệ 4).** Hai trang sổ điểm/tiến trình của
Phân hệ 4 cố tình KHÔNG có mục nav riêng vì chúng vào từ danh sách lớp. Chat KHÔNG có "danh sách" tự
nhiên nào để vào từ đó — học viên đăng nhập và cần thấy ngay lối vào. Thêm `APP_ROUTE.chat` +
`NAV_SECTIONS` một mục "Hỏi đáp khóa học", hiện ra chỉ khi `usePermissions().allows(ACCESS_RULE
.createChat)` (học viên và admin thấy, giáo viên không thấy — đúng RBAC).

**Ruling R4 — không streaming.** `use-chat-controller.ts` dùng `useMutation` thường
(`chatApi.ask`), không `EventSource`/SSE. Trạng thái "đang trả lời..." hiện bằng
`mutation.isPending`, không có hiệu ứng gõ-từng-chữ.

## 4. Luồng dữ liệu tổng quan

```
Học viên gõ câu hỏi
  → ChatPage giữ lịch sử hội thoại trong React state (không gửi lên server để lưu)
  → POST /api/chat/messages { message, history }
  → ChatAdminController (@PreAuthorize CREATE_CHAT)
  → AskChatbot.execute
      1. kiểm độ dài tin nhắn
      2. ChatRateLimiter (Redis) — 429 nếu vượt hạn mức
      3. CoursesManagement.searchOpenCourses(message, 5)  [modules.courses, full-text search]
      4. ChatPromptRules.buildGroundingContext(...)        [hàm thuần]
      5. AiChatClient.reply(groundingContext, history)     [integrations.ai, Spring AI → Anthropic]
  → trả { reply: "..." }
  → ChatPage nối reply vào lịch sử hiển thị, KHÔNG gửi gì khác lên server
```

## 5. Xử lý lỗi

| Tình huống | HTTP | errorCode |
|---|---|---|
| Tin nhắn rỗng/quá dài | 400 | `CHAT_MESSAGE_TOO_LONG` |
| Vượt hạn mức tần suất | 429 | `CHAT_TOO_MANY_MESSAGES` |
| LLM provider lỗi/timeout/chưa cấu hình API key | 503 | `CHAT_PROVIDER_UNAVAILABLE` |
| Không có quyền `CHAT:CREATE` | 403 | (chuẩn Spring Security, không cần errorCode riêng) |

Tất cả theo RFC 9457 `ProblemDetail`, nhất quán Hard Rule #9 của AGENTS.md.

## 6. Testing strategy

- **`ChatPromptRulesTest`** — hàm thuần: groundingContext đúng khi có kết quả, câu cố định khi rỗng,
  không bao giờ in "null".
- **`ChatRateLimiterTest`** — dùng Redis thật qua Testcontainers (mirror `PermissionCacheService`'s
  test precedent), xác nhận đúng ngưỡng, đúng cửa sổ thời gian, hai tài khoản không ảnh hưởng nhau.
- **`AskChatbotTest`** — mock `CoursesManagement`, `AiChatClient` (dùng
  `mock(AiChatClient.class)`/subclass giả lập trả lời cố định), `ChatRateLimiter` — xác nhận đúng
  thứ tự gọi (rate limit TRƯỚC khi gọi LLM, không lãng phí một lệnh gọi tốn tiền cho một request sẽ
  bị chặn), và việc dịch `AiProviderException` → lỗi nghiệp vụ.
- **`CoursesManagementIT`** (mở rộng) — `searchOpenCourses` trả đúng khóa khớp từ khoá tiếng Việt có
  dấu, không trả khóa `active=false`, danh sách rỗng khi không khớp.
- **`ChatAdminControllerIT`** — HTTP thật qua quyền thật: học viên gọi được, giáo viên bị 403, vượt
  hạn mức trả 429, dùng một `AiChatClient` giả lập (bean test override, mirror cách dự án đã override
  bean trong các IT khác) để không gọi Anthropic thật trong test.
- **`AiChatClientTest`** — chỉ test được phần KHÔNG cần API key thật: cấu trúc request/exception
  mapping. Việc gọi Anthropic thật **không thể test tự động trong phiên này** vì chưa có
  `ANTHROPIC_API_KEY` — đây là giới hạn đã nêu ở Ruling R2, không phải một lỗ hổng test bị bỏ sót.

## 7. Review Focus (tự lập, vì không có review-by-user giữa chừng)

1. **Rate limit kiểm TRƯỚC khi gọi LLM** — một request bị chặn không được phép vẫn tốn một lệnh gọi
   API trả phí.
2. **GroundingContext rỗng vẫn phải là một chuỗi có nghĩa** ("không tìm thấy khóa phù hợp"), không
   phải chuỗi rỗng — chuỗi rỗng dễ khiến LLM tự suy diễn.
3. **Khóa học `active=false` không bao giờ lọt vào kết quả truy xuất** dù khớp từ khoá.
4. **Giáo viên bị 403** dù đăng nhập hợp lệ — RBAC seed không được vô tình cấp nhầm qua group dùng
   chung.
5. **Lỗi hạ tầng AI (timeout, chưa cấu hình key) phải là 503 có errorCode**, không phải 500 trống —
   bài học trực tiếp từ Critical #1 của Phân hệ 4.
6. **Độ dài tin nhắn vượt giới hạn bị chặn ở backend**, không chỉ ở frontend (frontend có thể bị bỏ
   qua bằng cách gọi API trực tiếp).

## 8. Global Constraints

- Không lưu nội dung hội thoại ở backend (mục 2).
- System prompt chống bịa đặt là bắt buộc, không tắt được qua cấu hình.
- `ANTHROPIC_API_KEY` đọc từ biến môi trường, không hardcode, mặc định rỗng.
- Rate limit mặc định 10 tin nhắn/phút/tài khoản (Ruling R3), cấu hình được qua
  `ChatConstants.Limits` (hằng số Java, không phải một bảng cấu hình động qua UI — chưa ai cần đổi
  runtime).
- Cyclomatic complexity < 15, component < 200 dòng, tách logic khỏi render (3 Mandate của CLAUDE.md)
  áp dụng y như mọi phân hệ trước.
- Commit tiếng Anh, Conventional Commits, không có dòng đồng-tác-giả AI trong commit (theo memory
  đã ghi), merge `--no-ff` vào `develop`, xoá nhánh sau khi merge.

## 9. Việc người dùng cần tự làm khi quay lại máy

1. Đăng ký một API key Anthropic thật, gán vào biến môi trường `ANTHROPIC_API_KEY` (dev: file env
   cục bộ; prod: secret của môi trường triển khai, mirror cách `VNPAY_HASH_SECRET`/`MOMO_SECRET_KEY`
   đã được cấu hình).
2. Xác nhận ngưỡng rate limit (10 tin nhắn/phút/tài khoản) có hợp lý với ngân sách API thật hay
   không — đây là một con số khởi điểm hợp lý, không phải một con số đã được xác nhận với người
   dùng.
3. Xem lại Ruling R1 (full-text search thay vì vector embeddings) — nếu trung tâm có kế hoạch mở
   rộng danh mục khóa học lên quy mô lớn (hàng trăm+ khóa) hoặc cần chatbot hiểu được diễn đạt rất
   tự do/đồng nghĩa, đây là điểm đầu tiên cần nâng cấp.
4. Xem lại phần "Ngoài phạm vi" (mục 2) — nếu trung tâm muốn xem lại lịch sử hội thoại của học viên
   (ví dụ để cải thiện câu trả lời hoặc phát hiện nhu cầu tư vấn thật), đây là một thay đổi phạm vi
   có chủ đích cần quyết định rõ (ảnh hưởng tới chính sách lưu trữ dữ liệu học viên).
