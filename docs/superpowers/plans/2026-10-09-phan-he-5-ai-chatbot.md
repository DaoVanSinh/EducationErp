# Phân hệ 5 — AI Chatbot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Học viên (và admin) hỏi bằng tiếng Việt tự nhiên về các khóa học ĐANG MỞ và nhận một câu trả lời do LLM tổng hợp từ đúng dữ liệu khóa học thật của `modules.courses`, không bịa đặt, không lưu lịch sử hội thoại ở backend.

**Architecture:** Retrieval dùng full-text search của chính Postgres (`tsvector` STORED generated column + GIN index trên `courses`), lộ ra ngoài qua đúng MỘT method đọc mới trên facade `CoursesManagement.searchOpenCourses`. Generation nằm trong module mới `integrations.ai` — một `@Service AiChatClient` bọc `ChatClient` của Spring AI, không có interface đa-provider (Spring AI tự là lớp trừu tượng đó). Module nghiệp vụ mới `modules.chat` ghép ba thứ lại trong một usecase `AskChatbot`: chặn độ dài, chặn tần suất qua Redis, dựng system prompt chống bịa đặt bằng một hàm thuần, rồi gọi `AiChatClient`. Không bảng DB mới cho hội thoại; lịch sử do trình duyệt giữ và gửi lên theo từng request.

**Tech Stack:** Java 21, Spring Boot 3.4.1, Spring Modulith 1.3.1, **Spring AI 1.0.9** (`spring-ai-bom` + `spring-ai-starter-model-anthropic`, dependency MỚI), PostgreSQL 16 full-text search + Flyway (V26, V27), Redis (`StringRedisTemplate`, dependency đã có sẵn), JUnit 5 + Mockito + Testcontainers (Postgres + Redis, đều đã có sẵn); React 19 + Vite + TypeScript + TanStack Query v5 + Zod 3 + Tailwind v4.

**Spec:** `docs/superpowers/specs/2026-10-09-phan-he-5-ai-chatbot-design.md`

## Global Constraints

Mọi task đều mang theo các ràng buộc dưới đây; giá trị trích nguyên văn từ spec.

- **KHÔNG lưu nội dung hội thoại ở backend** — "Mỗi phiên chat chỉ tồn tại trong bộ nhớ trình duyệt; tải lại trang là mất lịch sử" (spec mục 2 + mục 8). Không bảng `chat_messages`, không entity, không repository, không migration tạo bảng hội thoại, không `ChatMemory`/`ChatMemoryRepository` của Spring AI.
- **KHÔNG streaming token-by-token** — "Trả lời đầy đủ một lần" (spec Ruling R4). Frontend dùng `useMutation` thường, không `EventSource`/SSE; backend không gọi `ChatClient.stream()`.
- **KHÔNG embeddings/vector DB** — "không thêm hạ tầng vector DB" (spec Ruling R1). Không pgvector, không embedding model, không `VectorStore`.
- **KHÔNG chat về chủ đề nào khác ngoài danh mục khóa học đang mở** — system prompt giới hạn phạm vi (spec mục 2).
- **KHÔNG giao diện admin xem lại nội dung chat** — hệ quả trực tiếp của việc không lưu (spec mục 2).
- **System prompt chống bịa đặt là bắt buộc, không tắt được qua cấu hình** (spec mục 8). Nó là một `public static final String` trong `ChatPromptRules`, không phải một property, không phải một bảng cấu hình.
- **`groundingContext` rỗng vẫn phải là một chuỗi có nghĩa** — "không bao giờ để trống hoàn toàn, vì một groundingContext rỗng dễ khiến LLM tự bịa" (spec mục 3.2 bước 4).
- **Rate limit kiểm TRƯỚC khi gọi LLM** — "một request bị chặn không được phép vẫn tốn một lệnh gọi API trả phí" (spec mục 7.1). Mặc định **10 tin nhắn/phút/tài khoản** (spec Ruling R3), cấu hình bằng **hằng số Java** trong `ChatConstants.Limits`, KHÔNG phải property trong `application.yml` và KHÔNG phải bảng cấu hình động qua UI (spec mục 8).
- **Khóa học `active = false` không bao giờ lọt vào kết quả truy xuất** dù khớp từ khoá (spec mục 7.3). Điều kiện `active = true` nằm trong chính câu SQL, không phải một lượt filter ở tầng Java có thể bị quên.
- **Cấu hình full-text search là `'simple'`, không phải `'english'`** — "Postgres không có cấu hình tiếng Việt dựng sẵn, và `'simple'` chỉ tách từ/viết thường, không áp luật stemming tiếng Anh sẽ làm sai lệch từ tiếng Việt" (spec Ruling R1).
- **`ANTHROPIC_API_KEY` đọc từ biến môi trường, không hardcode, mặc định rỗng** (spec mục 8 + Ruling R2).
- **KHÔNG CÓ TEST NÀO ĐƯỢC GỌI ANTHROPIC THẬT.** "Việc gọi Anthropic thật **không thể test tự động trong phiên này** vì chưa có `ANTHROPIC_API_KEY` — đây là giới hạn đã nêu ở Ruling R2, không phải một lỗ hổng test bị bỏ sót" (spec mục 6). Mọi test chạm tới `AiChatClient` phải dùng mock/test double. Người thực thi **không được** cố dựng một "integration test thật" cho Anthropic, không được nhúng một khoá thật vào repo, và không được coi một test đỏ vì thiếu khoá là "chấp nhận được". Đường đi mạng thật của `AiChatClient` chỉ được xác nhận bằng tay sau khi người dùng tự cung cấp khoá (spec mục 9.1).
- **Giáo viên KHÔNG được cấp quyền chat** — "roadmap không nhắc tới giáo viên dùng tính năng này" (spec mục 1). Không seed quyền `CHAT` cho role `TEACHER`, và không đi qua group dùng chung `'11111111-0000-0000-0000-000000000002'` ("Tự phục vụ cơ bản") mà cả TEACHER và STUDENT đang giữ (spec mục 3.4).
- **Có mục điều hướng riêng** — khác tiền lệ Phân hệ 4: "Chat KHÔNG có 'danh sách' tự nhiên nào để vào từ đó — học viên đăng nhập và cần thấy ngay lối vào" (spec Ruling R5).
- **Lỗi hạ tầng AI phải là 503 có `errorCode`, không phải 500 trống** (spec mục 5 + 7.5). Bảng lỗi chốt đúng ba `errorCode`: `CHAT_MESSAGE_TOO_LONG` (400), `CHAT_TOO_MANY_MESSAGES` (429), `CHAT_PROVIDER_UNAVAILABLE` (503). Tất cả theo RFC 9457 `ProblemDetail` qua `GlobalExceptionHandler` (Hard Rule #9 của `AGENTS.md`).
- Ba mandate của repo (`CLAUDE.md`): không hardcode string (backend gom vào `ChatConstants` static inner class, frontend gom vào `@/shared/constants/`); frontend tách logic khỏi render (logic ở `hooks/use-*.ts`, `ui/*.tsx` chỉ render); mọi hàm/method/component cyclomatic complexity < 15, component < 200 dòng.
- Frontend KHÔNG có test runner (`package.json` không có vitest/jest). Cổng kiểm tra của mọi task frontend là `npm run lint` + `npm run typecheck` + `npm run build`.
- Commit tiếng Anh, Conventional Commits, KHÔNG dòng đồng-tác-giả AI (spec mục 8 + memory của repo). Nhánh làm việc hiện tại là `feat/ai-chatbot` (đã có, `git status` sạch) — không commit trực tiếp vào `main`/`develop`.

### Ba quyết định kỹ thuật phải đọc trước khi bắt đầu

Ba điểm dưới đây là chỗ plan này **cố ý khác chữ nghĩa của spec**, vì làm đúng y chữ sẽ tạo ra một hệ thống không chạy được. Mỗi điểm đều có test chốt lại trong task sở hữu code.

1. **`AccessRules.CREATE_CHAT` KHÔNG mang marker `@ORGANIZATION`.** Spec mục 3.4 tự mâu thuẫn: câu đầu nói rule mang marker `@ORGANIZATION`, câu sau nói "mọi học viên có quyền `CHAT:CREATE` ở scope nào cũng được phép hỏi chatbot", và V27 của chính spec seed cho STUDENT ở scope `PERSONAL`. `ScopedPermissionEvaluator` đọc marker làm **scope tối thiểu** (`rules.isBroaderOrEqual(granted, minimumScope)`), nên marker `@ORGANIZATION` sẽ chặn đúng người dùng chính của phân hệ này ở ngay cửa. Plan chọn câu thứ hai (ý định rõ ràng, khớp seed): `CREATE_CHAT` không marker, giống ba rule `*_GRADE` của Phân hệ 4. Chốt bằng `AccessConstantsTest.chatRuleDeliberatelyOmitsTheOrganizationScopeMarker` (Task 1) và `ChatAdminControllerIT.allowsAStudentWhoOnlyHasPersonalScopedChatPermission` (Task 12).
2. **Câu truy vấn full-text dùng ngữ nghĩa OR, không phải AND.** `plainto_tsquery('simple', 'có khóa tiếng anh giao tiếp buổi tối không')` nối MỌI từ bằng `&`, nên một câu hỏi tự nhiên gần như chắc chắn trả về rỗng — "truy xuất rỗng" sẽ là trường hợp THƯỜNG, không phải ngoại lệ, và cả phân hệ vô dụng. Plan đổi tsquery thành OR bằng đúng built-in của Postgres: `replace(plainto_tsquery('simple', :query)::text, '&', '|')::tsquery`. An toàn vì `plainto_tsquery` đã làm sạch dấu câu và cấu hình `'simple'` không bao giờ sinh lexeme chứa `&`; chuỗi rỗng vẫn cast được thành tsquery rỗng nên câu hỏi toàn dấu câu trả về danh sách rỗng chứ không ném lỗi. `ts_rank` + `LIMIT` lo phần "khớp nhiều từ thì lên trước". Chốt bằng `CourseRepositoryIT.matchesANaturalLanguageQuestionWithExtraWords` (Task 3).
3. **`RestClientCustomizer` toàn cục của `integrations.payment` phải thôi là toàn cục trước khi thêm Spring AI.** `PaymentHttpClientConfig` hiện đăng ký một `@Bean RestClientCustomizer` đặt read timeout **10 giây** cho MỌI `RestClient.Builder` tự cấu hình của Boot. `AnthropicChatAutoConfiguration` của Spring AI 1.0.9 nhận `ObjectProvider<RestClient.Builder>` và dùng chính builder đó (đã đọc source), nên mọi lệnh gọi Anthropic sẽ bị cắt ở 10 giây — ngắn hơn thời gian trả lời bình thường của một model chat. Task 5 chuyển timeout của payment vào đúng chỗ dùng nó (`MomoPaymentGatewayClient`) và Task 6 đăng ký một customizer riêng cho đường AI với read timeout 60 giây. Hai task này phải chạy TRƯỚC Task 12.

### Hai điểm plan cố ý KHÔNG làm, nói rõ để không ai tưởng là bỏ sót

- **KHÔNG có `ChatEvents` và KHÔNG ghi audit cho tin nhắn chat.** Đã đọc lại spec mục 5 (bảng lỗi) và mục 8 (Global Constraints): không mục nào yêu cầu audit hội thoại, và mục 2 cấm lưu nội dung hội thoại ở backend. Một `AuditLog` mang `entityId` là nội dung câu hỏi (hoặc bất cứ thứ gì truy ngược được về câu hỏi) chính là lưu hội thoại bằng cửa sau, vi phạm lý do đã nêu ("dữ liệu học viên nhạy cảm, có thể là trẻ vị thành niên"). Vì vậy KHÔNG tạo `ChatEvents`, KHÔNG thêm `@ApplicationModuleListener` nào vào `AuditEventListeners`, KHÔNG thêm `AuditConstants.EntityTypes`/`Actions` nào. Task 17 có một bước grep chốt lại điều này.
- **KHÔNG có `ChatManagement` facade.** Chưa module nào đọc từ `modules.chat` — đúng rule "chỉ tạo facade khi có nơi gọi thật", mirror cách `modules.academic` của Phân hệ 4 không có `AcademicManagement`.

## Review Focus

Sáu mục dưới đây copy nguyên văn từ spec mục 7, mỗi mục đã được gắn vào một task cụ thể có test thật.

1. **Rate limit kiểm TRƯỚC khi gọi LLM** — một request bị chặn không được phép vẫn tốn một lệnh gọi API trả phí. → Task 10 (`AskChatbotTest.checksTheRateLimitBeforeSpendingAnyMoneyOnTheProvider`), Task 12 (`ChatAdminControllerIT.returnsTooManyRequestsOnTheEleventhMessageInAMinute`).
2. **GroundingContext rỗng vẫn phải là một chuỗi có nghĩa** ("không tìm thấy khóa phù hợp"), không phải chuỗi rỗng — chuỗi rỗng dễ khiến LLM tự suy diễn. → Task 8 (`ChatPromptRulesTest.returnsAMeaningfulSentenceWhenNothingMatched`, `...neverReturnsABlankGroundingContext`), Task 10 (`AskChatbotTest.stillSendsANonBlankSystemTextWhenRetrievalFoundNothing`).
3. **Khóa học `active=false` không bao giờ lọt vào kết quả truy xuất** dù khớp từ khoá. → Task 3 (`CourseRepositoryIT.neverReturnsAnInactiveCourseEvenWhenItMatches`), Task 4 (`CoursesManagementIT.searchOpenCoursesSkipsAnInactiveCourseThatMatches`), Task 12 (`ChatAdminControllerIT.neverPutsAnInactiveCourseIntoTheSystemTextSentToTheProvider`).
4. **Giáo viên bị 403** dù đăng nhập hợp lệ — RBAC seed không được vô tình cấp nhầm qua group dùng chung. → Task 11 (`ChatRbacSeedIT.teacherGetsNoChatPermissionAtAll`), Task 12 (`ChatAdminControllerIT.refusesATeacherEntirely`).
5. **Lỗi hạ tầng AI (timeout, chưa cấu hình key) phải là 503 có errorCode**, không phải 500 trống. → Task 6 (`AiChatClientTest.translatesAProviderFailureIntoAiProviderException`), Task 10 (`AskChatbotTest.translatesAiProviderExceptionIntoABusinessFailure`), Task 12 (`ChatAdminControllerIT.reportsAProviderFailureAsServiceUnavailableWithAnErrorCode`).
6. **Độ dài tin nhắn vượt giới hạn bị chặn ở backend**, không chỉ ở frontend (frontend có thể bị bỏ qua bằng cách gọi API trực tiếp). → Task 8 (`ChatPromptRulesTest.rejectsAMessageLongerThanTheLimit`), Task 10 (`AskChatbotTest.rejectsATooLongMessageBeforeTouchingTheRateLimiterOrTheProvider`), Task 12 (`ChatAdminControllerIT.rejectsATooLongMessageWithAProblemDetail`).

Thêm bốn lớp đầu vào mà spec hàm ý nhưng không nói thẳng, mỗi lớp cũng đã có test trong task sở hữu code:

7. **`history` do frontend gửi lên dài vô hạn.** Spec không đặt trần nào cho `history`; lịch sử nằm trong bộ nhớ trình duyệt nên nó LỚN DẦN suốt một phiên, và mỗi lượt gửi lại toàn bộ — tiền token tăng tuyến tính theo độ dài hội thoại, và một client cố ý có thể gửi 10.000 lượt trong một request mà rate limit (đếm *số request*) không chặn được. Quyết định: **cắt bớt, không từ chối** — chỉ `ChatConstants.Limits.MAX_HISTORY_TURNS = 20` lượt GẦN NHẤT được gửi cho provider (từ chối bằng 400 sẽ làm một hội thoại dài tự nhiên đột ngột hỏng), cộng `@Size(max = MAX_MESSAGE_LENGTH)` trên `ChatTurnPayload.content` để một lượt lịch sử không dài hơn mức một tin nhắn từng được phép. Hai lớp đó chặn prompt ở trần cứng 20 × 2000 ký tự. → Task 8 (`ChatPromptRulesTest.keepsOnlyTheMostRecentTurnsWhenTheHistoryIsAbsurdlyLong`), Task 10 (`AskChatbotTest.truncatesAnAbsurdlyLongHistoryBeforeCallingTheProvider`).
8. **Redis sập đúng lúc kiểm rate limit.** Spec không nói. Quyết định: **fail CLOSED** — `ChatRateLimiter` dịch mọi lỗi Redis thành `ChatbotUnavailableException` (503, `CHAT_PROVIDER_UNAVAILABLE`), KHÔNG cho request đi tiếp. Lý do: cả mục đích tồn tại của bộ đếm này là chặn hoá đơn API bất ngờ (spec mục 2), nên "đo không được thì cứ gọi" là đúng cái tình huống nó sinh ra để ngăn. Mất tính năng chat vài phút là một cái giá nhỏ hơn một hoá đơn không giới hạn, và khi Redis sập thì `PermissionCacheService` cũng đã sập theo nên gần như không ai đi tới được bước này. 503 có `errorCode` vẫn đúng tinh thần "không bao giờ 500 trống". → Task 9 (`ChatRateLimiterTest.failsClosedWhenRedisIsUnreachable`).
9. **Provider trả về câu trả lời rỗng/`null`.** `CallResponseSpec.content()` khai báo trả `String` nhưng có thể là `null` khi response không có generation nào (bị cắt bởi `max_tokens`, bị từ chối). Trả `{"reply": null}` xuống frontend sẽ làm Zod `z.string()` ném lỗi parse ở client — một lỗi vô nghĩa thay cho một lỗi rõ ràng. Quyết định: coi nó là lỗi provider (`AiProviderException` → 503). → Task 6 (`AiChatClientTest.treatsAnEmptyAnswerAsAProviderFailure`).
10. **Câu hỏi chỉ gồm dấu câu/khoảng trắng.** `plainto_tsquery` trả tsquery rỗng → `@@` false → danh sách rỗng, không ném; và `ChatPromptRules.isMessageAcceptable` chặn câu rỗng sau `strip()` trước cả khi tới DB. Hai lớp này phải cùng được chứng minh, nếu không một chuỗi `"???"` sẽ hoặc nổ ở Postgres hoặc tiêu một lệnh gọi LLM vô nghĩa. → Task 3 (`CourseRepositoryIT.returnsAnEmptyListForAQuestionMadeOfPunctuationOnly`), Task 8 (`ChatPromptRulesTest.rejectsAMessageThatIsOnlyWhitespace`).

---

## Task 1: `AccessConstants` — resource `CHAT` + rule `CREATE_CHAT`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/access/AccessConstants.java`
- Test: `backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java`

**Interfaces:**
- Consumes: `AccessConstants.Resources`, `AccessConstants.Actions`, `AccessConstants.AccessRules.CHECK_PREFIX/CHECK_SEPARATOR/CHECK_SUFFIX/MINIMUM_SCOPE` (private, đã có).
- Produces: `AccessConstants.Resources.CHAT = "CHAT"`, `AccessConstants.AccessRules.CREATE_CHAT` — biểu thức `@PreAuthorize` KHÔNG mang marker scope tối thiểu.

- [ ] **Step 1: Write the failing test**

Thêm vào cuối `AccessConstantsTest` (trước dấu `}` cuối file):

```java
    /**
     * Quyết định kỹ thuật #1 của plan: spec mục 3.4 tự mâu thuẫn, và câu đúng là câu thứ hai ("mọi
     * học viên có quyền CHAT:CREATE ở scope nào cũng được phép hỏi chatbot"). Nếu ai đó "sửa cho
     * nhất quán" bằng cách thêm marker @ORGANIZATION vào đây thì học viên - người dùng chính của cả
     * phân hệ, được V27 seed ở scope PERSONAL - sẽ bị 403 ngay ở cửa. Test này biến việc đó thành
     * build đỏ thay vì một màn hình trắng không ai hiểu vì sao.
     */
    @Test
    void chatRuleDeliberatelyOmitsTheOrganizationScopeMarker() {
        assertThat(AccessConstants.AccessRules.CREATE_CHAT)
                .contains(AccessConstants.Resources.CHAT, AccessConstants.Actions.CREATE)
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
    }

    /** Ba rule *_GRADE của Phân hệ 4 cũng không có marker - CHAT là rule thứ tư, không phải một
     * ngoại lệ đơn độc đáng nghi. */
    @Test
    void theOnlyRulesWithoutAMinimumScopeAreTheGradeRulesAndChat() {
        assertThat(AccessConstants.AccessRules.READ_GRADE)
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
        assertThat(AccessConstants.AccessRules.UPDATE_GRADE)
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
        assertThat(AccessConstants.AccessRules.CREATE_GRADE)
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
        assertThat(AccessConstants.AccessRules.CREATE_CHAT)
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AccessConstantsTest`
Expected: COMPILATION FAILURE — `cannot find symbol: variable CHAT` / `variable CREATE_CHAT`.

- [ ] **Step 3: Write minimal implementation**

Trong `AccessConstants.Resources`, thêm sau `public static final String GRADE = "GRADE";`:

```java
        public static final String CHAT = "CHAT";
```

Trong `AccessConstants.AccessRules`, thêm sau `CREATE_GRADE`:

```java
        /**
         * Rule thứ TƯ (và cuối cùng ở Phân hệ 5) không mang {@link #MINIMUM_SCOPE}, và đây là chủ
         * đích. Khác lý do của {@code *_GRADE}: ở đó marker bị bỏ để giáo viên scope
         * {@code PERSONAL} qua được cửa rồi {@code ClassGradeAccessGuard} hỏi lại "có phải lớp của
         * anh không". Ở đây KHÔNG có khái niệm sở hữu nào để hỏi lại - học viên được seed
         * {@code CHAT:CREATE:PERSONAL} (V27) và chỉ cần có quyền ở BẤT KỲ scope nào là được hỏi
         * chatbot, nên không có guard nào trong {@code modules.chat}. Thêm
         * {@code @ORGANIZATION} vào đây là chặn đúng người dùng chính của phân hệ.
         */
        public static final String CREATE_CHAT = CHECK_PREFIX + Resources.CHAT + CHECK_SEPARATOR
                + Actions.CREATE + CHECK_SUFFIX;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AccessConstantsTest`
Expected: PASS — toàn bộ test của class, gồm hai test mới.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/access/AccessConstants.java \
        backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java
git commit -m "feat(access): add the CHAT resource and the scope-free CREATE_CHAT rule"
```

---

## Task 2: Spring AI 1.0.9 — dependency + cấu hình, context phải khởi động được với khoá rỗng

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/eduerp/integrations/ai/AnthropicStarterIT.java`

**Interfaces:**
- Consumes: không.
- Produces: bean `org.springframework.ai.chat.model.ChatModel` (do `AnthropicChatAutoConfiguration` tạo, implement bởi `AnthropicChatModel`) có sẵn trong context; property `spring.ai.anthropic.api-key` mặc định RỖNG; property `spring.ai.anthropic.chat.options.model` mặc định `claude-sonnet-5`.

**Ghi chú plan (đã xác minh bằng source Spring AI 1.0.9, không phải suy đoán):**
`AnthropicChatAutoConfiguration.anthropicApi(...)` truyền thẳng `connectionProperties.getApiKey()` vào `AnthropicApi.Builder.apiKey(String)`, và builder chỉ `Assert.notNull` (`SimpleApiKey` cũng chỉ `Assert.notNull`), **không** `Assert.hasText`. Vì vậy khoá RỖNG (`${ANTHROPIC_API_KEY:}` → `""`) khởi động context bình thường, và lỗi xuất hiện đúng lúc gọi: 401 → `org.springframework.ai.retry.NonTransientAiException`. Đó chính xác là hành vi bảng lỗi của spec mục 5 mô tả ("chưa cấu hình API key → 503"). Không được đặt một khoá giả khác rỗng vào `application.yml`.
`spring.ai.retry.max-attempts` mặc định là **10** trong Spring AI 1.0.9 (`SpringAiRetryProperties`), quá nhiều cho một request HTTP đồng bộ của người dùng — hạ xuống 2. `on-client-errors` mặc định `false` nên 4xx (gồm 401 thiếu khoá) KHÔNG bị retry, đúng mong muốn.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/integrations/ai/AnthropicStarterIT.java`:

```java
package com.eduerp.integrations.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Khẳng định quan trọng nhất của task này KHÔNG phải "gọi được Anthropic" (không thể, chưa có khoá
 * thật - Global Constraint) mà là "thêm starter vào KHÔNG làm chết context của toàn bộ 60+ IT đang
 * có". Khoá rỗng là trạng thái mặc định của repo và của CI, nên nó phải là một trạng thái khởi động
 * được; chỉ lệnh gọi thật mới được phép thất bại.
 */
@Testcontainers
@SpringBootTest
class AnthropicStarterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    ChatModel chatModel;

    @Value("${spring.ai.anthropic.api-key}")
    String apiKey;

    @Value("${spring.ai.anthropic.chat.options.model}")
    String configuredModel;

    @Test
    void theContextStartsAndExposesAChatModelEvenWithAnEmptyApiKey() {
        assertThat(apiKey).as("mặc định phải RỖNG - khoá thật chỉ đến từ biến môi trường").isEmpty();
        assertThat(chatModel).isNotNull();
    }

    /** Ruling R2 chốt model mặc định; đổi nó là một quyết định về chi phí/chất lượng, không phải một
     * lần sửa nhỏ, nên nó được chốt bằng test. */
    @Test
    void theDefaultModelIsTheOneRulingR2Chose() {
        assertThat(configuredModel).isEqualTo("claude-sonnet-5");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AnthropicStarterIT`
Expected: COMPILATION FAILURE — `package org.springframework.ai.chat.model does not exist`.

- [ ] **Step 3: Add the dependency**

Trong `backend/pom.xml`, thêm vào `<properties>` (sau `<poi.version>`):

```xml
    <spring-ai.version>1.0.9</spring-ai.version>
```

Trong `<dependencyManagement><dependencies>` (sau BOM của Spring Modulith):

```xml
      <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-bom</artifactId>
        <version>${spring-ai.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
```

Trong `<dependencies>`, thêm ngay sau `poi-ooxml`:

```xml
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-anthropic</artifactId>
    </dependency>
```

- [ ] **Step 4: Add the configuration**

Trong `backend/src/main/resources/application.yml`, thêm vào khối `spring:` (ngay sau `data:`/trước `mail:`, thụt đúng 2 space dưới `spring:`):

```yaml
  ai:
    anthropic:
      # Secret thật CHỈ đến từ biến môi trường, mặc định rỗng - mirror MOMO_SECRET_KEY/
      # VNPAY_HASH_SECRET (spec Ruling R2). Khoá rỗng vẫn khởi động được context (AnthropicApi chỉ
      # Assert.notNull), lệnh gọi thật sẽ 401 -> NonTransientAiException -> 503
      # CHAT_PROVIDER_UNAVAILABLE, đúng bảng lỗi của spec mục 5.
      api-key: ${ANTHROPIC_API_KEY:}
      chat:
        options:
          model: ${ANTHROPIC_CHAT_MODEL:claude-sonnet-5}
          # Mặc định của Spring AI là 500 token - đủ để cắt ngang một câu trả lời tư vấn.
          max-tokens: ${ANTHROPIC_MAX_TOKENS:1024}
          # Tư vấn khóa học cần đúng dữ liệu, không cần sáng tạo - nhiệt độ thấp giảm thêm nguy cơ
          # bịa đặt mà system prompt đang chặn.
          temperature: 0.2
    retry:
      # Mặc định của Spring AI là 10 lần - một request HTTP đồng bộ của người dùng không được phép
      # giữ thread Tomcat qua 10 lượt backoff. 4xx (gồm 401 thiếu khoá) không bị retry vì
      # spring.ai.retry.on-client-errors mặc định false.
      max-attempts: ${ANTHROPIC_MAX_ATTEMPTS:2}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AnthropicStarterIT`
Expected: PASS — 2 tests.

- [ ] **Step 6: Prove the new dependency did not break any existing context**

Run: `cd backend && ./mvnw test -Dtest='CoursesManagementIT,GradeAdminControllerIT,PermissionCacheServiceIT'`
Expected: PASS — ba IT này dùng ba kiểu context khác nhau (`@SpringBootTest`, `@SpringBootTest` + MockMvc, `@SpringBootTest` + Redis). Nếu Spring AI 1.0.9 (build trên Boot 3.5) lệch API với Boot 3.4.1 thì nó nổ ở đây, không phải ở Task 12.

Nếu một trong ba đỏ vì `NoSuchMethodError`/`ClassNotFoundException` đến từ `org.springframework.ai.*` hoặc `org.springframework.boot.http.client.*`: hạ `<spring-ai.version>` xuống `1.0.3` (bản 1.0.x cuối cùng phát hành khi Boot 3.4 còn là dòng hiện hành) rồi chạy lại Step 5 + Step 6. KHÔNG nâng `spring-boot-starter-parent` — đó là một thay đổi ngoài phạm vi Phân hệ 5.

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml \
        backend/src/test/java/com/eduerp/integrations/ai/AnthropicStarterIT.java
git commit -m "build: add the Spring AI Anthropic starter with an env-var-only api key"
```

---

## Task 3: Migration V26 — `courses.search_vector` + `CourseRepository.searchActiveByFullText`

**Files:**
- Create: `backend/src/main/resources/db/migration/V26__add_course_search_vector.sql`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/internal/repository/CourseRepository.java`
- Test: `backend/src/test/java/com/eduerp/modules/courses/internal/repository/CourseRepositoryIT.java`

**Interfaces:**
- Consumes: `Course` (entity hiện có: `getId()`, `getCode()`, `getName()`, `getDescription()`, `getStandardSessionCount()`, `getTuitionFee()`, `isActive()`, `setActive(boolean)`), `CourseRepository extends JpaRepository<Course, UUID>`.
- Produces: `CourseRepository.searchActiveByFullText(String query, int limit): List<Course>`.

**Ghi chú plan:** cột `search_vector` KHÔNG cần field Java nào trên `Course`. Lý do đã kiểm: nó chỉ xuất hiện trong `WHERE`/`ORDER BY` của một native query, và `spring.jpa.hibernate.ddl-auto: validate` chỉ kiểm "mọi cột ĐƯỢC MAP có tồn tại trong bảng", không kiểm ngược lại. Danh sách cột trong `SELECT` được liệt kê tường minh (không `SELECT *`) để Hibernate không bao giờ phải đọc một giá trị `tsvector` mà nó không có kiểu cho.
`to_tsvector(regconfig, text)` (dạng 2 tham số, cấu hình là literal) là `IMMUTABLE` nên dùng được trong `GENERATED ALWAYS ... STORED`; dạng 1 tham số chỉ `STABLE` và sẽ bị Postgres từ chối — đừng rút ngắn nó.

- [ ] **Step 1: Write the failing test**

Thêm vào `CourseRepositoryIT` (sau các test hiện có, trước dấu `}` cuối file). Thêm luôn các import còn thiếu ở đầu file: `java.util.List`.

```java
    private Course saveSearchableCourse(String code, String name, String description, boolean active) {
        var course = new Course(code, name, description, 24, new BigDecimal("5000000"));
        course.setActive(active);
        return courses.saveAndFlush(course);
    }

    private List<String> searchCodes(String query) {
        return courses.searchActiveByFullText(query, 10).stream().map(Course::getCode).toList();
    }

    /** Tiếng Việt có dấu phải khớp nguyên dạng - cấu hình 'simple' không stem, không bỏ dấu
     * (spec Ruling R1). */
    @Test
    void matchesAVietnameseKeywordWithDiacritics() {
        saveSearchableCourse("FTS-TA", "Tiếng Anh giao tiếp buổi tối", "Luyện phản xạ nói", true);
        saveSearchableCourse("FTS-TOAN", "Toán 9 nâng cao", "Ôn thi vào 10", true);

        assertThat(searchCodes("tiếng Anh")).containsExactly("FTS-TA");
    }

    /**
     * Quyết định kỹ thuật #2 của plan. Với plainto_tsquery nguyên bản (nối mọi từ bằng &), câu hỏi
     * này trả về RỖNG vì không khóa nào chứa đủ cả "có", "không", "buổi"... - tức mọi câu hỏi tự
     * nhiên đều rỗng và cả phân hệ vô dụng. Test này là chỗ duy nhất chứng minh ngữ nghĩa OR.
     */
    @Test
    void matchesANaturalLanguageQuestionWithExtraWords() {
        saveSearchableCourse("FTS-TA2", "Tiếng Anh giao tiếp buổi tối", "Luyện phản xạ nói", true);

        assertThat(searchCodes("có khóa tiếng anh giao tiếp buổi tối nào không")).contains("FTS-TA2");
    }

    /** Review Focus #3 ở tầng thấp nhất: điều kiện active = true nằm trong chính câu SQL, không phải
     * một lượt filter ở Java có thể bị quên. */
    @Test
    void neverReturnsAnInactiveCourseEvenWhenItMatches() {
        saveSearchableCourse("FTS-OFF", "Tiếng Nhật sơ cấp", "Đã đóng khóa", false);
        saveSearchableCourse("FTS-ON", "Tiếng Nhật trung cấp", "Đang mở", true);

        assertThat(searchCodes("tiếng Nhật")).containsExactly("FTS-ON");
    }

    @Test
    void returnsAnEmptyListWhenNothingMatches() {
        saveSearchableCourse("FTS-NONE", "Tiếng Hàn sơ cấp", "Mô tả", true);

        assertThat(searchCodes("lập trình nhúng")).isEmpty();
    }

    /** Review Focus #10: câu hỏi chỉ gồm dấu câu phải ra danh sách rỗng, KHÔNG được ném lỗi SQL -
     * plainto_tsquery trả tsquery rỗng và chuỗi rỗng vẫn cast được về tsquery. */
    @Test
    void returnsAnEmptyListForAQuestionMadeOfPunctuationOnly() {
        saveSearchableCourse("FTS-PUNCT", "Tiếng Pháp sơ cấp", "Mô tả", true);

        assertThat(searchCodes("??? !!! ...")).isEmpty();
    }

    /** Khóa chưa có mô tả vẫn tìm được theo tên - coalesce(description, '') lo phần đó. */
    @Test
    void matchesACourseWhoseDescriptionIsNull() {
        saveSearchableCourse("FTS-NODESC", "Luyện chữ đẹp lớp 1", null, true);

        assertThat(searchCodes("luyện chữ")).containsExactly("FTS-NODESC");
    }

    /** Hạn mức truyền vào phải được tôn trọng - đây là trần chi phí token của prompt. */
    @Test
    void respectsTheRequestedLimit() {
        saveSearchableCourse("FTS-L1", "Guitar cơ bản", "Guitar", true);
        saveSearchableCourse("FTS-L2", "Guitar nâng cao", "Guitar", true);
        saveSearchableCourse("FTS-L3", "Guitar đệm hát", "Guitar", true);

        assertThat(courses.searchActiveByFullText("guitar", 2)).hasSize(2);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CourseRepositoryIT`
Expected: COMPILATION FAILURE — `cannot find symbol: method searchActiveByFullText(String,int)`.

- [ ] **Step 3: Write the migration**

Create `backend/src/main/resources/db/migration/V26__add_course_search_vector.sql`:

```sql
-- Retrieval của Phân hệ 5 (spec Ruling R1): full-text search của chính Postgres, KHÔNG vector DB.
--
-- Cấu hình 'simple' chứ không 'english': Postgres không có cấu hình tiếng Việt dựng sẵn, và
-- 'english' sẽ áp luật stemming tiếng Anh lên từ tiếng Việt ("tiếng" -> "tieng"? "giao" -> "giao"?)
-- làm lệch kết quả. 'simple' chỉ tách từ + viết thường, đúng việc cần làm.
--
-- GENERATED ALWAYS ... STORED: cột tự đồng bộ mỗi khi name/description đổi, không code Java nào
-- phải nhớ cập nhật nó - và vì vậy KHÔNG có field nào tương ứng trên entity Course.
-- Bắt buộc dùng dạng 2 tham số to_tsvector('simple', ...): chỉ dạng đó là IMMUTABLE, dạng 1 tham số
-- là STABLE và Postgres sẽ từ chối nó trong một generated column.
ALTER TABLE courses ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(description, ''))
    ) STORED;

-- GIN là index đúng cho tsvector: một danh mục vài chục khóa chưa cần nó về hiệu năng, nhưng toán tử
-- @@ không dùng được index B-tree nên không có GIN thì câu truy vấn là seq scan mãi mãi.
CREATE INDEX idx_courses_search_vector ON courses USING GIN (search_vector);
```

- [ ] **Step 4: Write minimal implementation**

Thay toàn bộ `backend/src/main/java/com/eduerp/modules/courses/internal/repository/CourseRepository.java`:

```java
package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.internal.model.Course;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    Optional<Course> findByCode(String code);

    Page<Course> findAll(Pageable pageable);

    /**
     * Truy xuất của chatbot (spec mục 3.3). Ba chi tiết đều có chủ đích:
     *
     * <p>1. {@code replace(plainto_tsquery(...)::text, '&', '|')::tsquery} - plainto_tsquery nối MỌI
     * từ bằng {@code &}, nên một câu hỏi tự nhiên ("có khóa tiếng anh giao tiếp buổi tối không") sẽ
     * đòi khóa học chứa đủ cả "có" và "không", tức luôn rỗng. Đổi sang {@code |} cho ngữ nghĩa OR,
     * rồi {@code ts_rank} + {@code LIMIT} lo phần "khớp nhiều từ thì lên trước". An toàn vì
     * plainto_tsquery đã làm sạch dấu câu và cấu hình 'simple' không bao giờ sinh lexeme chứa
     * {@code &}; câu hỏi toàn dấu câu cho tsquery RỖNG, {@code @@} trả false, danh sách rỗng.
     *
     * <p>2. {@code active = true} nằm trong SQL, không ở tầng Java (Review Focus #3).
     *
     * <p>3. Liệt kê cột tường minh thay vì {@code SELECT *}: cột {@code search_vector} là tsvector
     * và không có field nào trên entity, không đưa nó vào result set là không bao giờ phải lo
     * Hibernate đọc một kiểu nó không biết.
     */
    @Query(value = """
            WITH q AS (
                SELECT replace(plainto_tsquery('simple', :query)::text, '&', '|')::tsquery AS tsq
            )
            SELECT c.id, c.code, c.name, c.description, c.standard_session_count,
                   c.tuition_fee, c.active
            FROM courses c, q
            WHERE c.active = true
              AND c.search_vector @@ q.tsq
            ORDER BY ts_rank(c.search_vector, q.tsq) DESC, c.code ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Course> searchActiveByFullText(@Param("query") String query, @Param("limit") int limit);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CourseRepositoryIT`
Expected: PASS — 10 tests (3 cũ + 7 mới). Flyway chạy V26 trên Postgres thật trong container.

- [ ] **Step 6: Verify the generated column did not break anything that writes courses**

Run: `cd backend && ./mvnw test -Dtest='CourseAdminControllerIT,CourseGradeColumnRepositoryIT,CourseGradeColumnIntegrityIT'`
Expected: PASS — `ddl-auto: validate` chấp nhận một cột trong bảng mà entity không map, và `INSERT`/`UPDATE` vào `courses` không được phép ghi vào cột generated.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V26__add_course_search_vector.sql \
        backend/src/main/java/com/eduerp/modules/courses/internal/repository/CourseRepository.java \
        backend/src/test/java/com/eduerp/modules/courses/internal/repository/CourseRepositoryIT.java
git commit -m "feat(courses): add a stored tsvector column and a full-text search query for open courses"
```

---

## Task 4: `CoursesManagement.searchOpenCourses` — đúng MỘT method đọc mới trên facade

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/courses/CoursesManagementIT.java`

**Interfaces:**
- Consumes: `CourseRepository.searchActiveByFullText(String, int)` (Task 3).
- Produces: `CoursesManagement.OpenCourseSummary(UUID id, String code, String name, String description, Integer standardSessionCount, BigDecimal tuitionFee)` và `CoursesManagement.searchOpenCourses(String query, int limit): List<OpenCourseSummary>`. Đây là hai type duy nhất `modules.chat` được phép biết về `modules.courses`.

- [ ] **Step 1: Write the failing test**

Thêm vào `CoursesManagementIT` (trước dấu `}` cuối file):

```java
    private Course saveOpenCourse(String code, String name, String description, boolean active) {
        var course = new Course(code, name, description, 24, new BigDecimal("7000000"));
        course.setActive(active);
        return courses.saveAndFlush(course);
    }

    /** Hợp đồng ra ngoài module: modules.chat chỉ được thấy OpenCourseSummary, không bao giờ thấy
     * entity Course. */
    @Test
    void searchOpenCoursesReturnsTheSummaryTheChatbotNeeds() {
        var saved = saveOpenCourse("FCD-S1", "Tiếng Anh thiếu nhi", "Lớp 1 đến lớp 5", true);

        var found = coursesManagement.searchOpenCourses("tiếng Anh thiếu nhi", 5);

        assertThat(found).hasSize(1);
        var summary = found.get(0);
        assertThat(summary.id()).isEqualTo(saved.getId());
        assertThat(summary.code()).isEqualTo("FCD-S1");
        assertThat(summary.name()).isEqualTo("Tiếng Anh thiếu nhi");
        assertThat(summary.description()).isEqualTo("Lớp 1 đến lớp 5");
        assertThat(summary.standardSessionCount()).isEqualTo(24);
        assertThat(summary.tuitionFee()).isEqualByComparingTo(new BigDecimal("7000000"));
    }

    /** Review Focus #3 ở tầng facade. */
    @Test
    void searchOpenCoursesSkipsAnInactiveCourseThatMatches() {
        saveOpenCourse("FCD-S2", "Vẽ cho bé đã đóng", "Mô tả", false);
        saveOpenCourse("FCD-S3", "Vẽ cho bé đang mở", "Mô tả", true);

        assertThat(coursesManagement.searchOpenCourses("vẽ cho bé", 5))
                .extracting(CoursesManagement.OpenCourseSummary::code)
                .containsExactly("FCD-S3");
    }

    /** Cùng triết lý với getGradeColumns: "không khớp khóa nào" là một danh sách rỗng có nghĩa, không
     * phải một lỗi (spec mục 3.3). */
    @Test
    void searchOpenCoursesIsAnEmptyListWhenNothingMatches() {
        saveOpenCourse("FCD-S4", "Cờ vua nhập môn", "Mô tả", true);

        assertThat(coursesManagement.searchOpenCourses("hoá học hữu cơ", 5)).isEmpty();
    }

    /** Khóa chưa chốt giá/chưa cấu hình số buổi vẫn phải truy xuất được - chatbot sẽ nói "chưa công
     * bố học phí" thay vì không thấy khóa đó (xem ChatPromptRules). */
    @Test
    void searchOpenCoursesReturnsACourseWithoutAFeeOrASessionCount() {
        var course = new Course("FCD-S5", "Thư pháp cơ bản", "Mô tả", null, null);
        courses.saveAndFlush(course);

        var summary = coursesManagement.searchOpenCourses("thư pháp", 5).get(0);

        assertThat(summary.standardSessionCount()).isNull();
        assertThat(summary.tuitionFee()).isNull();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CoursesManagementIT`
Expected: COMPILATION FAILURE — `cannot find symbol: class OpenCourseSummary` / `method searchOpenCourses`.

- [ ] **Step 3: Write minimal implementation**

Trong `CoursesManagement`, thêm record sau `CourseTuitionResponse`:

```java
    /**
     * Ảnh chụp một khóa học ĐANG MỞ cho chatbot (spec mục 3.3). Không có {@code active} vì method
     * duy nhất sinh ra nó chỉ trả về khóa đang mở - mang theo một cờ luôn bằng {@code true} là mời
     * nơi gọi tự filter lại một lần nữa, và một lần filter sai ở đó là một khóa đã đóng lọt vào câu
     * trả lời của chatbot (Review Focus #3).
     *
     * <p>{@code description}/{@code standardSessionCount}/{@code tuitionFee} đều có thể null (khóa
     * chưa chốt giá, chưa cấu hình số buổi) - {@code ChatPromptRules} dịch từng trường hợp thành một
     * câu tiếng Việt, không bao giờ in ra "null".
     */
    public record OpenCourseSummary(UUID id, String code, String name, String description,
            Integer standardSessionCount, BigDecimal tuitionFee) {
    }
```

Thêm method sau `getGradeColumns`:

```java
    /**
     * Truy xuất khóa học đang mở theo từ khoá tự nhiên, cho chatbot của {@code modules.chat}
     * (spec mục 3.3). {@code limit} do nơi gọi quyết định vì nó là trần chi phí token của prompt,
     * không phải một chi tiết của danh mục khóa học.
     *
     * <p>Không khớp khóa nào là một danh sách RỖNG, không phải một lỗi - cùng triết lý với
     * {@link #getGradeColumns(UUID)}.
     */
    @Transactional(readOnly = true)
    public List<OpenCourseSummary> searchOpenCourses(String query, int limit) {
        return courses.searchActiveByFullText(query, limit).stream()
                .map(course -> new OpenCourseSummary(course.getId(), course.getCode(), course.getName(),
                        course.getDescription(), course.getStandardSessionCount(), course.getTuitionFee()))
                .toList();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CoursesManagementIT`
Expected: PASS — 13 tests (9 cũ + 4 mới).

- [ ] **Step 5: Update the module javadoc so the new consumer is documented**

Trong `backend/src/main/java/com/eduerp/modules/courses/package-info.java`, thêm vào cuối đoạn javadoc đầu tiên (ngay trước `<p>Vai trò từng package:`):

```java
 * <p>Người đọc mới ở Phân hệ 5: {@code modules.chat} gọi
 * {@code CoursesManagement.searchOpenCourses} để truy xuất khóa học ĐANG MỞ cho chatbot, và chỉ thấy
 * {@code OpenCourseSummary} - không module nào bên ngoài biết bảng {@code courses} có cột
 * {@code search_vector} (V26) hay rằng việc tìm kiếm là full-text search của Postgres.
```

- [ ] **Step 6: Verify the module boundaries still hold**

Run: `cd backend && ./mvnw test -Dtest=ModularityTests`
Expected: PASS — 2 tests. Chưa có module mới nào nên danh sách 19 module không đổi.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java \
        backend/src/main/java/com/eduerp/modules/courses/package-info.java \
        backend/src/test/java/com/eduerp/modules/courses/CoursesManagementIT.java
git commit -m "feat(courses): expose searchOpenCourses on the facade for chatbot retrieval"
```

---

## Task 5: Tháo `RestClientCustomizer` toàn cục của payment (bắt buộc trước khi AI dùng HTTP)

**Files:**
- Modify: `backend/src/main/java/com/eduerp/integrations/payment/PaymentHttpClientConfig.java`
- Modify: `backend/src/main/java/com/eduerp/integrations/payment/MomoPaymentGatewayClient.java:73-76`
- Test: `backend/src/test/java/com/eduerp/integrations/payment/PaymentHttpClientConfigTest.java`

**Interfaces:**
- Consumes: `MomoPaymentGatewayClient(MomoProperties, RestClient.Builder)` (constructor hiện có).
- Produces: `PaymentHttpClientConfig.timeoutRequestFactory(): ClientHttpRequestFactory` (static, package-private) + hai hằng `CONNECT_TIMEOUT`/`READ_TIMEOUT` giữ nguyên giá trị. Không còn `@Bean RestClientCustomizer` nào trong `integrations.payment`.

**Ghi chú plan:** đây là quyết định kỹ thuật #3. Hành vi của payment KHÔNG đổi (cùng connect 5s / read 10s, cùng `ClientHttpRequestFactoryBuilder.detect()`), chỉ đổi phạm vi áp dụng từ "mọi `RestClient.Builder` trong app" thành "đúng client gọi MoMo". Javadoc cũ của file nói thẳng giả định đã hết đúng: "`MomoPaymentGatewayClient` là nơi DUY NHẤT trong app tiêm `RestClient.Builder` (đã xác nhận bằng grep)" — Spring AI là người tiêm thứ hai.

- [ ] **Step 1: Write the failing test**

Thay toàn bộ `backend/src/test/java/com/eduerp/integrations/payment/PaymentHttpClientConfigTest.java`:

```java
package com.eduerp.integrations.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Final review Important #4 của Phân hệ 3: {@code MomoPaymentGatewayClient} gọi {@code RestClient}
 * bên trong một usecase {@code @Transactional} ({@code InitiateOnlinePayment}). Không có timeout thì
 * một kết nối treo tới MoMo giữ một connection HikariCP vô thời hạn. Test này chốt lại CÓ một giới
 * hạn thời gian thật.
 *
 * <p>Phân hệ 5 đổi CÁCH áp giới hạn đó, không đổi giá trị: trước đây là một
 * {@code RestClientCustomizer} toàn cục, mà Spring AI cũng nhận chính
 * {@code ObjectProvider<RestClient.Builder>} của Boot - nên 10 giây của MoMo sẽ cắt ngang mọi lệnh
 * gọi Anthropic. Giờ request factory được áp tại đúng chỗ dùng nó.
 */
class PaymentHttpClientConfigTest {

    @Test
    void connectAndReadTimeoutsAreBoundedNotInfinite() {
        assertThat(PaymentHttpClientConfig.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
        assertThat(PaymentHttpClientConfig.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void theRequestFactoryIsCleanlyBuildableAndAppliesToAnyBuilder() {
        var builder = RestClient.builder();

        assertThatCode(() -> builder.requestFactory(PaymentHttpClientConfig.timeoutRequestFactory()))
                .doesNotThrowAnyException();
        assertThatCode(builder::build).doesNotThrowAnyException();
    }

    /** Một builder "trần" (đúng thứ Spring AI sẽ nhận khi không còn customizer toàn cục) vẫn dựng
     * được MoMo client, và chính client đó tự áp timeout của mình. */
    @Test
    void theMomoClientAppliesTheTimeoutItselfFromAPlainBuilder() {
        var properties = new MomoProperties("PARTNER", "access", "secret",
                "https://example.test/create", "https://example.test/return",
                "https://example.test/ipn");

        assertThatCode(() -> new MomoPaymentGatewayClient(properties, RestClient.builder()))
                .doesNotThrowAnyException();
    }
}
```

(`MomoProperties` là một record sáu component theo đúng thứ tự `partnerCode, accessKey, secretKey,
endpoint, redirectUrl, ipnUrl` — đã kiểm, khớp dòng khởi tạo trong `MomoPaymentGatewayClientTest`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=PaymentHttpClientConfigTest`
Expected: COMPILATION FAILURE — `cannot find symbol: method timeoutRequestFactory()`.

- [ ] **Step 3: Write minimal implementation**

Thay toàn bộ `backend/src/main/java/com/eduerp/integrations/payment/PaymentHttpClientConfig.java`:

```java
package com.eduerp.integrations.payment;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Giới hạn thời gian của riêng đường gọi cổng thanh toán.
 *
 * <p>Final review Important #4 của Phân hệ 3: trước bản vá, không timeout nào được cấu hình - một
 * kết nối treo tới MoMo giữ một connection HikariCP vô thời hạn, vì lệnh gọi này nằm trong một
 * usecase {@code @Transactional} ({@code InitiateOnlinePayment}).
 *
 * <p>Phân hệ 5 đổi cơ chế áp dụng: bản vá cũ dùng một {@code @Bean RestClientCustomizer}, và Boot áp
 * customizer cho MỌI {@code RestClient.Builder} tự cấu hình. Khi đó giả định ghi trong javadoc cũ
 * ("MomoPaymentGatewayClient là nơi DUY NHẤT trong app tiêm RestClient.Builder") còn đúng. Spring AI
 * 1.0.9 phá giả định đó: {@code AnthropicChatAutoConfiguration} nhận
 * {@code ObjectProvider<RestClient.Builder>} và dùng chính builder của Boot, nên read timeout 10
 * giây hợp lý cho một API thanh toán sẽ cắt ngang mọi lệnh gọi model chat - thứ bình thường mất
 * 15-30 giây. Vì vậy request factory giờ được áp tại đúng nơi dùng nó
 * ({@code MomoPaymentGatewayClient}) và không còn bean toàn cục nào ở đây.
 */
final class PaymentHttpClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private PaymentHttpClientConfig() {
    }

    static ClientHttpRequestFactory timeoutRequestFactory() {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(CONNECT_TIMEOUT)
                        .withReadTimeout(READ_TIMEOUT));
    }
}
```

Trong `MomoPaymentGatewayClient`, thay constructor (dòng 73-76):

```java
    MomoPaymentGatewayClient(MomoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        // Timeout áp tại đây chứ không qua một RestClientCustomizer toàn cục - xem javadoc của
        // PaymentHttpClientConfig: customizer toàn cục sẽ cắt luôn lệnh gọi của Spring AI.
        this.restClient = restClientBuilder
                .requestFactory(PaymentHttpClientConfig.timeoutRequestFactory())
                .build();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest='PaymentHttpClientConfigTest,MomoPaymentGatewayClientTest,PaymentContractTest'`
Expected: PASS — timeout vẫn đúng giá trị, MoMo client vẫn dựng và ký đúng như trước.

- [ ] **Step 5: Verify no payment behaviour regressed end-to-end**

Run: `cd backend && ./mvnw test -Dtest='PaymentCallbackControllerIT,InvoiceAdminControllerIT,ComboPaymentFlowIT'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/integrations/payment/PaymentHttpClientConfig.java \
        backend/src/main/java/com/eduerp/integrations/payment/MomoPaymentGatewayClient.java \
        backend/src/test/java/com/eduerp/integrations/payment/PaymentHttpClientConfigTest.java
git commit -m "refactor(payment): apply the gateway http timeout at the call site instead of globally"
```

---

## Task 6: Module `integrations.ai` — `AiChatTurn`, `AiProviderException`, `AiChatClient`, timeout riêng

**Files:**
- Create: `backend/src/main/java/com/eduerp/integrations/ai/package-info.java`
- Create: `backend/src/main/java/com/eduerp/integrations/ai/AiChatTurn.java`
- Create: `backend/src/main/java/com/eduerp/integrations/ai/AiProviderException.java`
- Create: `backend/src/main/java/com/eduerp/integrations/ai/AiChatClient.java`
- Create: `backend/src/main/java/com/eduerp/integrations/ai/AiHttpClientConfig.java`
- Modify: `backend/src/test/java/com/eduerp/ModularityTests.java`
- Test: `backend/src/test/java/com/eduerp/integrations/ai/AiChatClientTest.java`
- Test: `backend/src/test/java/com/eduerp/integrations/ai/AiHttpClientConfigTest.java`

**Interfaces:**
- Consumes: `org.springframework.ai.chat.model.ChatModel` (bean từ Task 2), `org.springframework.ai.chat.client.ChatClient` + các nested interface `ChatClient.ChatClientRequestSpec`/`ChatClient.CallResponseSpec`, `org.springframework.ai.chat.prompt.Prompt`, `org.springframework.ai.chat.messages.{Message, SystemMessage, UserMessage, AssistantMessage}`, `org.springframework.ai.retry.NonTransientAiException`.
- Produces: `AiChatTurn(AiChatTurn.Role role, String content)` với `AiChatTurn.Role { USER, ASSISTANT }`; `AiProviderException(String message, Throwable cause) extends RuntimeException`; `AiChatClient.reply(String groundingContext, List<AiChatTurn> history): String` (public) + `AiChatClient.toPrompt(String, List<AiChatTurn>): Prompt` (static, package-private, cho test).

**Ghi chú plan (chữ ký đã xác minh trên source Spring AI v1.0.9, không suy đoán):** `ChatClient.prompt(Prompt)` → `ChatClientRequestSpec`; `ChatClientRequestSpec.call()` → `CallResponseSpec`; `CallResponseSpec.content()` → `String`. `new Prompt(List<Message>)`, `new SystemMessage(String)`, `new UserMessage(String)`, `new AssistantMessage(String)` đều tồn tại. Phía test: `Prompt.getInstructions()` → `List<Message>`, và `Message extends org.springframework.ai.content.Content` nên `getText()` → `String` (KHÔNG phải `getContent()` như các bản 0.8.x). Dùng `ChatClient.create(ChatModel)` (đúng ví dụ trong tài liệu 1.0) thay vì tiêm `ChatClient.Builder`, để `AiChatClient` chỉ phụ thuộc một bean mà autoconfiguration của Anthropic chắc chắn tạo ra.
`groundingContext` là **toàn bộ** nội dung system message (luật chống bịa đặt + danh sách khóa học), do `modules.chat` dựng. `integrations.ai` không được chứa một câu tiếng Việt nghiệp vụ nào — đúng quy ước "integration là cơ chế thuần, không biết nghiệp vụ" của `integrations.payment`.

- [ ] **Step 1: Write the failing test for message assembly and failure translation**

Create `backend/src/test/java/com/eduerp/integrations/ai/AiChatClientTest.java`:

```java
package com.eduerp.integrations.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;

/**
 * Giới hạn đã nêu ở spec Ruling R2: KHÔNG có cách nào test tự động một lệnh gọi Anthropic THẬT trong
 * phiên này (chưa có ANTHROPIC_API_KEY), nên class này chỉ test đúng hai thứ test được mà không cần
 * khoá: hình dạng prompt gửi đi, và việc dịch mọi thất bại của provider thành MỘT kiểu lỗi mà
 * modules.chat biết bắt. Đó không phải một lỗ hổng test bị bỏ sót.
 *
 * <p>Chuỗi fluent của Spring AI được mock ở đúng ba mức vì {@code AiChatClient} cố ý dùng overload
 * {@code prompt(Prompt)} thay vì {@code prompt().system(...).messages(...)}: một Prompt dựng sẵn vừa
 * khiến việc lắp message trở thành một hàm thuần kiểm được bằng captor, vừa tránh thứ tự message bị
 * trộn giữa {@code system()}/{@code user()} và {@code messages()} (một lỗi đã biết của ChatClient).
 */
class AiChatClientTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    private final AiChatClient client = new AiChatClient(chatClient);

    @BeforeEach
    void wireTheFluentChain() {
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
    }

    private Prompt capturePrompt() {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(captor.capture());
        return captor.getValue();
    }

    @Test
    void returnsTheProviderAnswer() {
        when(responseSpec.content()).thenReturn("Trung tâm đang mở khóa Tiếng Anh giao tiếp buổi tối.");

        assertThat(client.reply("ngữ cảnh", List.of(new AiChatTurn(AiChatTurn.Role.USER, "có khóa nào không"))))
                .isEqualTo("Trung tâm đang mở khóa Tiếng Anh giao tiếp buổi tối.");
    }

    /** System message phải đứng ĐẦU và mang nguyên văn groundingContext mà nơi gọi dựng - nếu nó bị
     * đẩy xuống sau lịch sử, luật chống bịa đặt mất hiệu lực với phần hội thoại trước đó. */
    @Test
    void putsTheGroundingContextIntoTheFirstSystemMessage() {
        when(responseSpec.content()).thenReturn("ok");

        client.reply("CHỈ trả lời dựa trên danh sách sau: Toán 9", List.of());

        var messages = capturePrompt().getInstructions();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("CHỈ trả lời dựa trên danh sách sau: Toán 9");
    }

    /** Hội thoại nhiều lượt: thứ tự và vai của từng lượt phải giữ nguyên, nếu không model đọc câu trả
     * lời của chính nó như là câu hỏi của học viên. */
    @Test
    void mapsEveryHistoryTurnToTheMatchingSpringAiMessageTypeInOrder() {
        when(responseSpec.content()).thenReturn("ok");

        client.reply("ngữ cảnh", List.of(
                new AiChatTurn(AiChatTurn.Role.USER, "có khóa toán không"),
                new AiChatTurn(AiChatTurn.Role.ASSISTANT, "Có khóa Toán 9 nâng cao."),
                new AiChatTurn(AiChatTurn.Role.USER, "học phí bao nhiêu")));

        var messages = capturePrompt().getInstructions();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("có khóa toán không");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2).getText()).isEqualTo("Có khóa Toán 9 nâng cao.");
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(3).getText()).isEqualTo("học phí bao nhiêu");
    }

    /**
     * Review Focus #5: một lỗi 401 vì thiếu khoá (tình huống MẶC ĐỊNH của repo hiện tại) phải ra một
     * kiểu lỗi mà modules.chat biết bắt, không phải một NonTransientAiException lọt ra tới
     * GlobalExceptionHandler thành 500 trống.
     */
    @Test
    void translatesAProviderFailureIntoAiProviderException() {
        when(responseSpec.content())
                .thenThrow(new NonTransientAiException("401 - {\"error\":{\"type\":\"authentication_error\"}}"));

        assertThatThrownBy(() -> client.reply("ngữ cảnh", List.of()))
                .isInstanceOf(AiProviderException.class)
                .hasCauseInstanceOf(NonTransientAiException.class);
    }

    /** Review Focus #9: content() khai báo trả String nhưng trả null được (response không có
     * generation nào). Trả null xuống frontend sẽ làm Zod ném một lỗi parse vô nghĩa thay cho một 503
     * rõ ràng. */
    @Test
    void treatsANullAnswerAsAProviderFailure() {
        when(responseSpec.content()).thenReturn(null);

        assertThatThrownBy(() -> client.reply("ngữ cảnh", List.of()))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void treatsAnEmptyAnswerAsAProviderFailure() {
        when(responseSpec.content()).thenReturn("   ");

        assertThatThrownBy(() -> client.reply("ngữ cảnh", List.of()))
                .isInstanceOf(AiProviderException.class);
    }

    /**
     * Lỗi "câu trả lời rỗng" do chính class này phát hiện là lỗi GỐC, không được bọc thêm một lớp
     * AiProviderException nữa - nơi gọi đọc {@code getCause()} để log nguyên nhân thật, và một cause
     * trỏ về chính kiểu của nó là một log vô nghĩa.
     */
    @Test
    void doesNotWrapAnAiProviderExceptionTwice() {
        when(responseSpec.content()).thenReturn("");

        assertThatThrownBy(() -> client.reply("ngữ cảnh", List.of()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(thrown -> assertThat(thrown.getCause()).isNull());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AiChatClientTest`
Expected: COMPILATION FAILURE — `cannot find symbol: class AiChatClient` / `class AiChatTurn` / `class AiProviderException`.

- [ ] **Step 3: Write the module package-info**

Create `backend/src/main/java/com/eduerp/integrations/ai/package-info.java`:

```java
/**
 * Một nhà cung cấp mô hình ngôn ngữ, sau một {@code @Service} mỏng. Cơ chế thuần: không biết khóa
 * học, học viên hay trung tâm là gì - module nghiệp vụ ({@code modules.chat}) tự dựng toàn bộ nội
 * dung system message (luật chống bịa đặt + danh sách khóa học truy xuất được) rồi truyền vào.
 *
 * <p>Đơn giản hơn {@code integrations.payment} ĐÚNG MỘT BẬC, và đó là chủ đích (spec Ruling R2):
 * {@code ChatClient} của Spring AI ĐÃ TỰ là lớp trừu tượng đa nhà cung cấp - đổi Anthropic sang
 * OpenAI/Ollama chỉ là đổi dependency + cấu hình, không đổi một dòng code gọi - nên ở đây KHÔNG có
 * {@code interface} + nhiều {@code @Component} như VNPay/MoMo. Chỉ cần một lớp bọc để
 * {@code modules.chat} không bao giờ import type của Spring AI, mirror cách
 * {@code modules.billing} không bao giờ import SDK của VNPay/MoMo.
 *
 * <p>KHÔNG phụ thuộc bất kỳ {@code modules.*} nào, và KHÔNG lưu gì: lịch sử hội thoại đi vào qua
 * tham số và đi ra khỏi bộ nhớ ngay sau lệnh gọi (spec mục 2 - không lưu hội thoại ở backend).
 */
@org.springframework.modulith.ApplicationModule(displayName = "AI Chat Provider")
package com.eduerp.integrations.ai;
```

- [ ] **Step 4: Write the value types**

Create `backend/src/main/java/com/eduerp/integrations/ai/AiChatTurn.java`:

```java
package com.eduerp.integrations.ai;

/**
 * Một lượt trong hội thoại. Vai là enum chứ không phải chuỗi để không có chỗ nào viết "user"/"USER"
 * lệch nhau, và để nơi gọi không cần biết Spring AI đặt tên các kiểu message là gì.
 */
public record AiChatTurn(Role role, String content) {

    public enum Role {
        USER, ASSISTANT
    }
}
```

Create `backend/src/main/java/com/eduerp/integrations/ai/AiProviderException.java`:

```java
package com.eduerp.integrations.ai;

/**
 * Nhà cung cấp không trả lời được: timeout, 401 vì chưa cấu hình khoá, 429 của chính Anthropic, hay
 * một câu trả lời rỗng. Đây là MỘT kiểu lỗi duy nhất mà nơi gọi phải bắt, thay vì phải biết tới
 * {@code NonTransientAiException}/{@code TransientAiException}/{@code RestClientException} của ba
 * thư viện khác nhau - cùng vai với {@code PaymentSignatureException} của
 * {@code integrations.payment}.
 *
 * <p>KHÔNG kế thừa {@code AppException}: {@code integrations.*} không được quyết định HTTP status của
 * một nghiệp vụ. {@code modules.chat} dịch nó thành {@code ChatbotUnavailableException} (503) ở biên
 * của mình.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: Write the service**

Create `backend/src/main/java/com/eduerp/integrations/ai/AiChatClient.java`:

```java
package com.eduerp.integrations.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Nơi DUY NHẤT trong toàn app gọi một mô hình ngôn ngữ.
 *
 * <p>Không streaming (spec Ruling R4): một lệnh gọi, một câu trả lời đầy đủ.
 *
 * <p>Không có khoá thật thì lệnh gọi sẽ 401 và ném {@link AiProviderException} - đó là hành vi đúng
 * và đã được tính tới (spec Ruling R2 + bảng lỗi mục 5), không phải một lỗi cần vá. Mọi test chạm
 * tới class này dùng {@link #AiChatClient(ChatClient)} với một {@code ChatClient} giả lập; không test
 * nào gọi mạng thật.
 */
@Service
public class AiChatClient {

    private final ChatClient chatClient;

    @Autowired
    AiChatClient(ChatModel chatModel) {
        this(ChatClient.create(chatModel));
    }

    /** Constructor cho test: chuỗi fluent của Spring AI chỉ giả lập được khi ChatClient tiêm vào được
     * - mirror constructor-cho-test của {@code VnPayPaymentGatewayClient}. */
    AiChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * @param groundingContext TOÀN BỘ nội dung system message, do nơi gọi dựng (luật chống bịa đặt +
     *     danh sách khóa học truy xuất được). Class này không thêm một chữ nghiệp vụ nào vào đó.
     * @param history mọi lượt hỏi/đáp của phiên, lượt cuối là câu hỏi mới. Không được lưu ở đâu.
     * @throws AiProviderException mọi thất bại của nhà cung cấp, gồm cả câu trả lời rỗng
     */
    public String reply(String groundingContext, List<AiChatTurn> history) {
        String answer = callProvider(groundingContext, history);
        if (answer == null || answer.isBlank()) {
            // Review Focus #9: content() trả null được. Trả null lên trên sẽ thành {"reply": null},
            // và Zod ở frontend ném một lỗi parse không ai hiểu thay cho một 503 rõ ràng.
            throw new AiProviderException("Nhà cung cấp trả về câu trả lời rỗng", null);
        }
        return answer;
    }

    private String callProvider(String groundingContext, List<AiChatTurn> history) {
        try {
            return chatClient.prompt(toPrompt(groundingContext, history)).call().content();
        } catch (RuntimeException providerFailure) {
            // Bắt rộng là có chủ đích: Spring AI ném NonTransientAiException/TransientAiException
            // (org.springframework.ai.retry) cho lỗi HTTP, RestClientException cho lỗi mạng, và
            // IllegalArgumentException/IllegalStateException cho cấu hình sai - ba thư viện, ba cây
            // exception, và danh sách đó đổi được giữa hai bản patch của Spring AI. Với nơi gọi thì
            // cả ba đều là đúng một sự thật: "nhà cung cấp không trả lời được". Để một kiểu lạ lọt
            // ra ngoài là để nó thành 500 trống - đúng điều Review Focus #5 cấm.
            throw new AiProviderException("Không gọi được nhà cung cấp mô hình ngôn ngữ", providerFailure);
        }
    }

    /**
     * Hàm thuần, package-private để test khẳng định được thứ tự/vai của từng message mà không cần
     * dựng cả service. Dùng overload {@code prompt(Prompt)} thay vì
     * {@code prompt().system(...).messages(...)}: ChatClient xếp list của {@code messages()} TRƯỚC
     * system/user của {@code system()}/{@code user()}, nên cách fluent sẽ đẩy system message xuống
     * sau lịch sử.
     */
    static Prompt toPrompt(String groundingContext, List<AiChatTurn> history) {
        List<Message> messages = new ArrayList<>(history.size() + 1);
        messages.add(new SystemMessage(groundingContext));
        history.forEach(turn -> messages.add(toMessage(turn)));
        return new Prompt(messages);
    }

    private static Message toMessage(AiChatTurn turn) {
        return turn.role() == AiChatTurn.Role.ASSISTANT
                ? new AssistantMessage(turn.content())
                : new UserMessage(turn.content());
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AiChatClientTest`
Expected: PASS — 7 tests.

- [ ] **Step 7: Write the failing test for the AI-specific http timeout**

Create `backend/src/test/java/com/eduerp/integrations/ai/AiHttpClientConfigTest.java`:

```java
package com.eduerp.integrations.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Hai hằng dưới đây là thứ ngăn một lệnh gọi treo tới Anthropic giữ một thread Tomcat vô thời hạn, và
 * đồng thời ngăn một read timeout quá ngắn cắt ngang một câu trả lời bình thường. Đó là lý do chúng
 * được chốt bằng test thay vì để trong một file cấu hình ai cũng sửa được không ai thấy.
 */
class AiHttpClientConfigTest {

    @Test
    void theReadTimeoutIsLongEnoughForAChatAnswerButStillBounded() {
        assertThat(AiHttpClientConfig.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
        assertThat(AiHttpClientConfig.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(60));
        assertThat(AiHttpClientConfig.READ_TIMEOUT)
                .as("10 giây của cổng thanh toán quá ngắn cho một model chat - đó là lý do customizer "
                        + "toàn cục của payment đã bị tháo ở Task 5")
                .isGreaterThan(Duration.ofSeconds(30));
    }

    @Test
    void theCustomizerAppliesACleanlyBuildableRequestFactoryToAnyBuilder() {
        var customizer = new AiHttpClientConfig().aiChatTimeoutCustomizer();
        var builder = RestClient.builder();

        assertThatCode(() -> customizer.customize(builder)).doesNotThrowAnyException();
        assertThatCode(builder::build).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 8: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AiHttpClientConfigTest`
Expected: COMPILATION FAILURE — `cannot find symbol: class AiHttpClientConfig`.

- [ ] **Step 9: Write the configuration**

Create `backend/src/main/java/com/eduerp/integrations/ai/AiHttpClientConfig.java`:

```java
package com.eduerp.integrations.ai;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Giới hạn thời gian cho lệnh gọi nhà cung cấp mô hình.
 *
 * <p>{@code AnthropicChatAutoConfiguration} của Spring AI 1.0.9 nhận
 * {@code ObjectProvider<RestClient.Builder>} và dùng chính builder tự cấu hình của Boot, mà bản 1.0.9
 * KHÔNG có property timeout nào ({@code AnthropicConnectionProperties} chỉ có api-key, base-url,
 * completions-path, version, beta-version - đã đọc source). Vì vậy một
 * {@code RestClientCustomizer} là cơ chế duy nhất đặt được timeout cho đường này mà không phải viết
 * lại cả autoconfiguration.
 *
 * <p>Customizer này là TOÀN CỤC, và đó là lý do Task 5 đã tháo customizer toàn cục của
 * {@code integrations.payment} trước: sau Phân hệ 5, người tiêu thụ duy nhất còn lại của
 * {@code RestClient.Builder} tự cấu hình là {@code AnthropicApi} của Spring AI
 * ({@code MomoPaymentGatewayClient} tự ghi đè request factory của chính nó). Nếu một integration thứ
 * ba sau này cũng tiêm {@code RestClient.Builder}, nó phải tự ghi đè request factory như MoMo đang
 * làm, chứ không thêm một customizer toàn cục thứ hai.
 *
 * <p>60 giây: một câu trả lời của model chat bình thường mất 15-30 giây, nên 10 giây (giới hạn của
 * cổng thanh toán) sẽ cắt ngang mọi lệnh gọi. Vẫn có trần vì lệnh gọi này nằm trong một request HTTP
 * đồng bộ của người dùng - cộng với {@code spring.ai.retry.max-attempts: 2}, trần xấu nhất là hai
 * lượt chứ không phải mười.
 */
@Configuration
class AiHttpClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    RestClientCustomizer aiChatTimeoutCustomizer() {
        var requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(CONNECT_TIMEOUT)
                        .withReadTimeout(READ_TIMEOUT));
        return builder -> builder.requestFactory(requestFactory);
    }
}
```

- [ ] **Step 10: Register the new module in `ModularityTests`**

Trong `backend/src/test/java/com/eduerp/ModularityTests.java`, thêm `"integrations.ai"` vào `containsExactlyInAnyOrder(...)` của `everyDomainAndIntegrationPackageIsADetectedModule`, ngay trước `"integrations.cache"`:

```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "modules.payroll", "modules.enrollment",
                "modules.billing", "modules.academic", "integrations.ai", "integrations.cache",
                "integrations.mail", "integrations.notification", "integrations.payment",
                "integrations.storage");
```

- [ ] **Step 11: Run the tests to verify everything passes**

Run: `cd backend && ./mvnw test -Dtest='AiChatClientTest,AiHttpClientConfigTest,ModularityTests'`
Expected: PASS — `everyDomainAndIntegrationPackageIsADetectedModule` thấy đúng 20 module, và
`verifiesModularStructure` xanh nghĩa là `integrations.ai` không phụ thuộc `modules.*` nào.

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/java/com/eduerp/integrations/ai/ \
        backend/src/test/java/com/eduerp/integrations/ai/AiChatClientTest.java \
        backend/src/test/java/com/eduerp/integrations/ai/AiHttpClientConfigTest.java \
        backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "feat(ai): wrap the Spring AI chat client in a single integration service"
```

---

## Task 7: Khung module `modules.chat` — constants, exception, dto

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/chat/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/ChatConstants.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/ChatException.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/MessageTooLongException.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/TooManyMessagesException.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/ChatbotUnavailableException.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/dto/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/dto/ChatTurnPayload.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/dto/AskChatbotRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/dto/AskChatbotResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/internal/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/internal/rules/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/usecase/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/chat/web/package-info.java`
- Modify: `backend/src/test/java/com/eduerp/ModularityTests.java`
- Test: `backend/src/test/java/com/eduerp/modules/chat/ChatExceptionTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/chat/ChatConstantsTest.java`

**Interfaces:**
- Consumes: `com.eduerp.core.exception.AppException(String errorCode, HttpStatus status, String message)`.
- Produces: `ChatConstants.Limits.{MAX_MESSAGE_LENGTH=2000, MAX_MESSAGES_PER_MINUTE=10, MAX_RETRIEVED_COURSES=5, MAX_HISTORY_TURNS=20}`, `ChatConstants.RateLimit.WINDOW` (`Duration.ofMinutes(1)`), `ChatConstants.CacheNamespaces.CHAT_RATE_LIMIT = "chat:rate"`, `ChatConstants.TurnRole { USER, ASSISTANT }`; `sealed ChatException extends AppException permits MessageTooLongException, TooManyMessagesException, ChatbotUnavailableException`; `MessageTooLongException(int length)`, `TooManyMessagesException(UUID accountId)`, `ChatbotUnavailableException(String reason, Throwable cause)`; `ChatTurnPayload(ChatConstants.TurnRole role, String content)`, `AskChatbotRequest(String message, List<ChatTurnPayload> history)`, `AskChatbotResponse(String reply)`.

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/eduerp/modules/chat/ChatExceptionTest.java`:

```java
package com.eduerp.modules.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Mirror {@code AcademicExceptionTest}: errorCode là hợp đồng với frontend (ApiError.errorCode), đổi
 * là breaking. Ba errorCode dưới đây copy nguyên văn bảng lỗi của spec mục 5. */
class ChatExceptionTest {

    /** Review Focus #6: câu hỏi quá dài bị chặn ở backend, và thông báo phải nói ra giới hạn thật để
     * người gửi biết cần cắt bao nhiêu. */
    @Test
    void messageTooLongIsBadRequestAndNamesTheLimit() {
        var ex = new MessageTooLongException(5000);

        assertThat(ex.getErrorCode()).isEqualTo("CHAT_MESSAGE_TOO_LONG");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage())
                .contains("5000", String.valueOf(ChatConstants.Limits.MAX_MESSAGE_LENGTH));
    }

    /** Spec mục 5 gộp "rỗng" và "quá dài" vào cùng một errorCode, nhưng thông báo phải nói đúng
     * chuyện đang xảy ra - "câu hỏi dài 0 ký tự vượt giới hạn 2000" là một câu vô nghĩa. */
    @Test
    void messageTooLongSaysTheQuestionIsEmptyWhenTheLengthIsZero() {
        var ex = new MessageTooLongException(0);

        assertThat(ex.getErrorCode()).isEqualTo("CHAT_MESSAGE_TOO_LONG");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("trống").doesNotContain("0 ký tự");
    }

    /** Review Focus #1: 429 chứ không 403 - người gửi KHÔNG thiếu quyền, họ chỉ cần chờ. */
    @Test
    void tooManyMessagesIsTooManyRequestsAndNamesTheQuota() {
        var accountId = UUID.randomUUID();
        var ex = new TooManyMessagesException(accountId);

        assertThat(ex.getErrorCode()).isEqualTo("CHAT_TOO_MANY_MESSAGES");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getMessage()).contains(accountId.toString(),
                String.valueOf(ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE));
    }

    /** Review Focus #5: 503 có errorCode, không phải 500 trống. Nguyên nhân gốc phải được giữ lại -
     * không có nó thì không ai debug được "vì sao chatbot chết" từ log. */
    @Test
    void chatbotUnavailableIsServiceUnavailableAndKeepsTheRootCause() {
        var cause = new IllegalStateException("401 authentication_error");
        var ex = new ChatbotUnavailableException("lỗi từ nhà cung cấp mô hình", cause);

        assertThat(ex.getErrorCode()).isEqualTo("CHAT_PROVIDER_UNAVAILABLE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getMessage()).contains("lỗi từ nhà cung cấp mô hình");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    /** Một nguyên nhân null (lỗi do chính ta phát hiện, vd câu trả lời rỗng) không được làm vỡ
     * constructor. */
    @Test
    void chatbotUnavailableAcceptsAMissingCause() {
        var ex = new ChatbotUnavailableException("câu trả lời rỗng", null);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getCause()).isNull();
    }
}
```

Create `backend/src/test/java/com/eduerp/modules/chat/ChatConstantsTest.java`:

```java
package com.eduerp.modules.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Bốn con số dưới đây là trần chi phí thật của một tính năng gọi API trả phí (spec Ruling R3 + mục
 * 8). Chốt bằng test để một lần "nới tạm cho dễ test" không âm thầm thành cấu hình production.
 */
class ChatConstantsTest {

    @Test
    void theRateLimitIsTenMessagesPerMinutePerAccount() {
        assertThat(ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE).isEqualTo(10);
        assertThat(ChatConstants.RateLimit.WINDOW).isEqualTo(Duration.ofMinutes(1));
    }

    /** Review Focus #6 + #7: hai trần này nhân với nhau là trần cứng của prompt gửi đi
     * (20 lượt x 2000 ký tự), thứ duy nhất chặn một client gửi một hội thoại dài vô hạn. */
    @Test
    void theMessageAndHistoryLimitsBoundThePromptSize() {
        assertThat(ChatConstants.Limits.MAX_MESSAGE_LENGTH).isEqualTo(2000);
        assertThat(ChatConstants.Limits.MAX_HISTORY_TURNS).isEqualTo(20);
    }

    @Test
    void retrievalIsCappedSoTheGroundingContextStaysSmall() {
        assertThat(ChatConstants.Limits.MAX_RETRIEVED_COURSES).isEqualTo(5);
    }

    /** Namespace Redis do module này sở hữu - key được lắp bằng CacheKeyBuilder, không nối chuỗi tay. */
    @Test
    void theRateLimitNamespaceIsOwnedByThisModule() {
        assertThat(ChatConstants.CacheNamespaces.CHAT_RATE_LIMIT).isEqualTo("chat:rate");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest='ChatExceptionTest,ChatConstantsTest'`
Expected: COMPILATION FAILURE — `package com.eduerp.modules.chat does not exist`.

- [ ] **Step 3: Write the package-info files**

Create `backend/src/main/java/com/eduerp/modules/chat/package-info.java`:

```java
/**
 * Module Chat — sở hữu LUẬT của việc hỏi chatbot tư vấn khóa học: chặn độ dài câu hỏi, chặn tần suất
 * theo tài khoản, dựng system prompt chống bịa đặt, và dịch mọi thất bại hạ tầng thành một lỗi nghiệp
 * vụ có errorCode.
 *
 * <p>KHÔNG sở hữu dữ liệu nào. Không entity, không repository, không bảng DB - "mỗi phiên chat chỉ
 * tồn tại trong bộ nhớ trình duyệt" (spec mục 2). Lịch sử hội thoại đi vào qua
 * {@code AskChatbotRequest.history} và biến mất ngay sau khi response được trả. Vì không lưu gì nên
 * cũng KHÔNG có {@code ChatEvents} và KHÔNG ghi audit: một dòng audit truy ngược được về câu hỏi của
 * học viên chính là lưu hội thoại bằng cửa sau, trái đúng lý do spec đã nêu (dữ liệu học viên nhạy
 * cảm, có thể là trẻ vị thành niên).
 *
 * <p>Phụ thuộc một chiều: {@code access} ({@code AccessConstants.AccessRules.CREATE_CHAT} cho
 * {@code @PreAuthorize}), {@code courses} ({@code CoursesManagement.searchOpenCourses} để truy xuất
 * khóa học ĐANG MỞ), {@code integrations.ai} ({@code AiChatClient} để sinh câu trả lời),
 * {@code integrations.cache} ({@code CacheKeyBuilder} để lắp key rate limit), {@code shared}
 * ({@code AccountPrincipal}). KHÔNG module nào đọc ngược từ {@code chat}, nên không có facade
 * {@code ChatManagement} - mirror cách {@code modules.academic} không có facade (rule #1: chỉ tạo
 * facade khi có nơi gọi thật).
 *
 * <p>KHÔNG có guard kiểm sở hữu như {@code ClassGradeAccessGuard} của Phân hệ 4: ở đây không có khái
 * niệm "đúng lớp của ai" - ai có quyền {@code CHAT:CREATE} ở bất kỳ scope nào cũng được hỏi
 * (spec mục 3.4).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: constants, exception.</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Chat (AI Course Advisor)")
package com.eduerp.modules.chat;
```

Create `backend/src/main/java/com/eduerp/modules/chat/dto/package-info.java`:

```java
/**
 * Hợp đồng vào/ra của module chat. Controller nhận/trả các record này nên package phải được expose
 * bằng {@code @NamedInterface}, nếu không {@code ApplicationModules.verify()} sẽ fail.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.eduerp.modules.chat.dto;
```

Create `backend/src/main/java/com/eduerp/modules/chat/internal/package-info.java`:

```java
/** Chi tiết cài đặt của module chat. Spring Modulith che toàn bộ cây này khỏi mọi module khác. */
package com.eduerp.modules.chat.internal;
```

Create `backend/src/main/java/com/eduerp/modules/chat/internal/rules/package-info.java`:

```java
/** Hàm thuần, không I/O - mirror {@code academic.internal.rules}. Toàn bộ nội dung chữ của system
 * prompt chống bịa đặt nằm ở đây, nơi test được bằng {@code new} mà không cần Spring. */
package com.eduerp.modules.chat.internal.rules;
```

Create `backend/src/main/java/com/eduerp/modules/chat/usecase/package-info.java`:

```java
/** Một class = một use case (rule #7). Use case duy nhất của module này KHÔNG mang
 * {@code @Transactional} - xem javadoc {@code AskChatbot}. */
package com.eduerp.modules.chat.usecase;
```

Create `backend/src/main/java/com/eduerp/modules/chat/web/package-info.java`:

```java
/** Adapter HTTP của module chat: controller mỏng (rule #8 — không chứa nghiệp vụ). */
package com.eduerp.modules.chat.web;
```

- [ ] **Step 4: Write the constants**

Create `backend/src/main/java/com/eduerp/modules/chat/ChatConstants.java`:

```java
package com.eduerp.modules.chat;

import java.time.Duration;

/**
 * Hằng số dùng chung của module chat, nằm ở base package (không phải {@code internal}) vì {@code dto}
 * và {@code internal} cùng tier, cả hai cần tham chiếu - mirror {@code AcademicConstants}.
 */
public final class ChatConstants {

    private ChatConstants() {
    }

    /**
     * Vai của một lượt hội thoại trong hợp đồng HTTP. Là enum chứ không phải chuỗi để Jackson tự từ
     * chối một giá trị lạ và để không chỗ nào viết "user"/"USER" lệch nhau - mirror
     * {@code CoursesConstants.DayOfWeek} (enum ở base package vì cả {@code dto} lẫn {@code internal}
     * dùng).
     */
    public enum TurnRole {
        USER, ASSISTANT
    }

    public static final class Limits {
        private Limits() {
        }

        /**
         * Trần độ dài một câu hỏi, ký tự. Chặn một học viên dán cả một đoạn văn làm phình prompt/tốn
         * tiền (spec mục 3.2 bước 1). Kiểm ở BACKEND, không chỉ ở frontend - frontend bỏ qua được
         * bằng cách gọi API trực tiếp (Review Focus #6).
         */
        public static final int MAX_MESSAGE_LENGTH = 2000;

        /** Spec Ruling R3: "10 tin nhắn/phút/tài khoản - đủ cho một cuộc hội thoại tự nhiên, đủ thấp
         * để một tài khoản bị lộ không gây hoá đơn API bất ngờ". */
        public static final int MAX_MESSAGES_PER_MINUTE = 10;

        /** Số khóa học nhiều nhất được nhét vào groundingContext (spec mục 3.2 bước 3). Đây là trần
         * chi phí token của phần dữ liệu, không phải một giới hạn của danh mục khóa học. */
        public static final int MAX_RETRIEVED_COURSES = 5;

        /**
         * Review Focus #7: lịch sử nằm trong bộ nhớ trình duyệt nên nó LỚN DẦN suốt một phiên, và mỗi
         * lượt gửi lại toàn bộ - tiền token tăng tuyến tính theo độ dài hội thoại, và một client cố ý
         * gửi được 10.000 lượt trong một request mà rate limit (đếm số REQUEST) không thấy. Lịch sử
         * bị CẮT BỚT xuống số lượt gần nhất này, không bị từ chối: từ chối bằng 400 sẽ làm một hội
         * thoại dài tự nhiên đột ngột hỏng giữa phiên.
         */
        public static final int MAX_HISTORY_TURNS = 20;
    }

    public static final class RateLimit {
        private RateLimit() {
        }

        /** Cửa sổ đếm của {@code ChatRateLimiter}, khớp đơn vị của
         * {@link Limits#MAX_MESSAGES_PER_MINUTE}. */
        public static final Duration WINDOW = Duration.ofMinutes(1);
    }

    /** Namespace Redis mà module này sở hữu — việc lắp key nằm ở
     * {@code integrations.cache.CacheKeyBuilder}. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }

        public static final String CHAT_RATE_LIMIT = "chat:rate";
    }
}
```

- [ ] **Step 5: Write the exceptions**

Create `backend/src/main/java/com/eduerp/modules/chat/ChatException.java`:

```java
package com.eduerp.modules.chat;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module chat phát ra - mirror {@code AcademicException}. */
public sealed class ChatException extends AppException
        permits ChatbotUnavailableException, MessageTooLongException, TooManyMessagesException {

    protected ChatException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/chat/MessageTooLongException.java`:

```java
package com.eduerp.modules.chat;

import org.springframework.http.HttpStatus;

/**
 * Review Focus #6. Spec mục 5 gộp "tin nhắn rỗng" và "tin nhắn quá dài" vào cùng một errorCode
 * {@code CHAT_MESSAGE_TOO_LONG} - giữ đúng hợp đồng đó, nhưng thông báo phải nói đúng chuyện đang xảy
 * ra ("câu hỏi dài 0 ký tự, vượt giới hạn 2000" là một câu vô nghĩa với người đọc).
 *
 * <p>Vì sao không dùng {@code @Size} trên {@code AskChatbotRequest.message}: annotation sẽ cho
 * errorCode {@code VALIDATION_FAILED}, lệch khỏi bảng lỗi của spec và khiến frontend phải xử lý hai
 * hình dạng lỗi cho cùng một tình huống.
 */
public final class MessageTooLongException extends ChatException {

    public MessageTooLongException(int length) {
        super("CHAT_MESSAGE_TOO_LONG", HttpStatus.BAD_REQUEST, describe(length));
    }

    private static String describe(int length) {
        if (length == 0) {
            return "Câu hỏi đang để trống - hãy nhập câu hỏi về khóa học bạn quan tâm";
        }
        return "Câu hỏi dài " + length + " ký tự, vượt giới hạn "
                + ChatConstants.Limits.MAX_MESSAGE_LENGTH + " ký tự";
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/chat/TooManyMessagesException.java`:

```java
package com.eduerp.modules.chat;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Review Focus #1: 429, không phải 403. Người gửi KHÔNG thiếu quyền - họ chỉ cần chờ hết cửa sổ một
 * phút. Trả 403 ở đây sẽ làm frontend hiển thị "bạn không có quyền", một câu sai.
 */
public final class TooManyMessagesException extends ChatException {

    public TooManyMessagesException(UUID accountId) {
        super("CHAT_TOO_MANY_MESSAGES", HttpStatus.TOO_MANY_REQUESTS,
                "Tài khoản " + accountId + " đã gửi quá " + ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE
                        + " câu hỏi trong một phút, vui lòng chờ một lát rồi thử lại");
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/chat/ChatbotUnavailableException.java`:

```java
package com.eduerp.modules.chat;

import org.springframework.http.HttpStatus;

/**
 * Review Focus #5, và là bài học trực tiếp từ Critical #1 của Phân hệ 4: một lỗi hạ tầng không bao
 * giờ được rò ra thành 503/500 không có errorCode. Hai nguồn đổ về đây:
 * {@code AiProviderException} của {@code integrations.ai} (timeout, 401 vì chưa cấu hình khoá, 429
 * của chính Anthropic, câu trả lời rỗng) và một Redis không đọc được lúc kiểm rate limit
 * (xem {@code ChatRateLimiter} - fail closed).
 */
public final class ChatbotUnavailableException extends ChatException {

    public ChatbotUnavailableException(String reason, Throwable cause) {
        super("CHAT_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "Trợ lý tư vấn tạm thời không trả lời được (" + reason + "), vui lòng thử lại sau");
        // AppException không có constructor nhận cause, nhưng mất nguyên nhân gốc là mất khả năng
        // debug "vì sao chatbot chết" từ log. Throwable(String) để cause ở trạng thái CHƯA khởi tạo
        // (cause == this), nên initCause hợp lệ đúng một lần ở đây.
        initCause(cause);
    }
}
```

- [ ] **Step 6: Write the DTOs**

Create `backend/src/main/java/com/eduerp/modules/chat/dto/ChatTurnPayload.java`:

```java
package com.eduerp.modules.chat.dto;

import com.eduerp.modules.chat.ChatConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Một lượt hội thoại do CLIENT gửi lên (spec mục 3.2). Backend không lưu gì nên đây là nguồn duy
 * nhất của lịch sử - và vì nó đến từ client, nó là dữ liệu không tin được: {@code @Size} chặn một
 * lượt lịch sử dài hơn mức một tin nhắn từng được phép, còn số LƯỢNG lượt do
 * {@code ChatPromptRules.toProviderTurns} cắt bớt (Review Focus #7).
 */
public record ChatTurnPayload(@NotNull ChatConstants.TurnRole role,
        @NotBlank @Size(max = ChatConstants.Limits.MAX_MESSAGE_LENGTH) String content) {
}
```

Create `backend/src/main/java/com/eduerp/modules/chat/dto/AskChatbotRequest.java`:

```java
package com.eduerp.modules.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code message} cố ý KHÔNG mang {@code @Size}: độ dài được kiểm trong usecase để lỗi mang đúng
 * errorCode {@code CHAT_MESSAGE_TOO_LONG} của bảng lỗi (spec mục 5) thay vì
 * {@code VALIDATION_FAILED} - xem javadoc {@code MessageTooLongException}.
 *
 * <p>{@code history} vắng mặt trong JSON (lượt hỏi đầu tiên của phiên) là hợp lệ và có nghĩa "chưa
 * có lịch sử", nên compact constructor chuẩn hoá nó về danh sách rỗng: mọi nơi dùng về sau không
 * phải kiểm null, và một {@code List.copyOf} cũng khoá luôn tính bất biến của hợp đồng.
 */
public record AskChatbotRequest(@NotNull String message, @Valid List<ChatTurnPayload> history) {

    public AskChatbotRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/chat/dto/AskChatbotResponse.java`:

```java
package com.eduerp.modules.chat.dto;

/**
 * Chỉ MỘT field, và đó là toàn bộ hợp đồng ra (spec mục 3.2). Không trả kèm danh sách khóa học đã
 * truy xuất: giao diện không có chỗ hiển thị nó, và trả về nghĩa là hứa một hợp đồng phải giữ.
 * Không trả kèm id hội thoại: không có hội thoại nào được lưu (spec mục 2).
 */
public record AskChatbotResponse(String reply) {
}
```

- [ ] **Step 7: Register the new module in `ModularityTests`**

Trong `backend/src/test/java/com/eduerp/ModularityTests.java`, thêm `"modules.chat"` vào
`containsExactlyInAnyOrder(...)`, ngay sau `"modules.academic"`:

```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "modules.payroll", "modules.enrollment",
                "modules.billing", "modules.academic", "modules.chat", "integrations.ai",
                "integrations.cache", "integrations.mail", "integrations.notification",
                "integrations.payment", "integrations.storage");
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest='ChatExceptionTest,ChatConstantsTest,ModularityTests'`
Expected: PASS — `everyDomainAndIntegrationPackageIsADetectedModule` thấy đúng **21** module (19 của
Phân hệ 4 + `integrations.ai` + `modules.chat`).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/chat/ \
        backend/src/test/java/com/eduerp/modules/chat/ \
        backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "feat(chat): scaffold the chat module with constants, sealed exceptions and dtos"
```

---

## Task 8: `ChatPromptRules` — system prompt chống bịa đặt + groundingContext (hàm thuần, không I/O)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/chat/internal/rules/ChatPromptRules.java`
- Test: `backend/src/test/java/com/eduerp/modules/chat/internal/rules/ChatPromptRulesTest.java`

**Interfaces:**
- Consumes: `CoursesManagement.OpenCourseSummary` (Task 4), `AiChatTurn` + `AiChatTurn.Role` (Task 6), `ChatConstants.Limits` + `ChatConstants.TurnRole` (Task 7), `ChatTurnPayload` (Task 7).
- Produces: `ChatPromptRules.SYSTEM_PROMPT` (public static final String), `ChatPromptRules.buildGroundingContext(List<OpenCourseSummary>): String`, `ChatPromptRules.buildSystemText(List<OpenCourseSummary>): String`, `ChatPromptRules.toProviderTurns(List<ChatTurnPayload>, String): List<AiChatTurn>`, `ChatPromptRules.isMessageAcceptable(String): boolean`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/chat/internal/rules/ChatPromptRulesTest.java`:

```java
package com.eduerp.modules.chat.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.ai.AiChatTurn;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.dto.ChatTurnPayload;
import com.eduerp.modules.courses.CoursesManagement;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code GradeRulesTest}. */
class ChatPromptRulesTest {

    private static CoursesManagement.OpenCourseSummary course(String name, String description,
            Integer sessions, String fee) {
        return new CoursesManagement.OpenCourseSummary(UUID.randomUUID(), "C-" + name.hashCode(), name,
                description, sessions, fee == null ? null : new BigDecimal(fee));
    }

    @Test
    void listsEveryRetrievedCourseWithItsNameSessionsAndFee() {
        var context = ChatPromptRules.buildGroundingContext(List.of(
                course("Tiếng Anh giao tiếp", "Buổi tối thứ 2-4", 24, "7000000"),
                course("Toán 9 nâng cao", "Ôn thi vào 10", 36, "9000000")));

        assertThat(context).contains("Tiếng Anh giao tiếp", "Buổi tối thứ 2-4", "24", "7000000",
                "Toán 9 nâng cao", "Ôn thi vào 10", "36", "9000000");
    }

    /** Review Focus #2: "không tìm thấy khóa phù hợp" phải là một câu CÓ NGHĨA, không phải chuỗi rỗng
     * - chuỗi rỗng dễ khiến LLM tự suy diễn ra khóa học không tồn tại. */
    @Test
    void returnsAMeaningfulSentenceWhenNothingMatched() {
        var context = ChatPromptRules.buildGroundingContext(List.of());

        assertThat(context).contains("Không tìm thấy khóa học nào");
    }

    @Test
    void neverReturnsABlankGroundingContext() {
        assertThat(ChatPromptRules.buildGroundingContext(List.of())).isNotBlank();
        assertThat(ChatPromptRules.buildGroundingContext(List.of(course("X", null, null, null))))
                .isNotBlank();
    }

    /** Spec mục 6: "không bao giờ in null". Khóa chưa chốt giá/chưa cấu hình số buổi/chưa có mô tả là
     * ba trạng thái hợp lệ của dữ liệu thật, và cả ba phải thành một câu tiếng Việt - "học phí null"
     * là đúng cái loại rác khiến LLM bịa ra một con số. */
    @Test
    void neverPrintsTheWordNullForACourseWithMissingFields() {
        var context = ChatPromptRules.buildGroundingContext(List.of(course("Thư pháp", null, null, null)));

        assertThat(context).doesNotContain("null")
                .contains("Thư pháp", "chưa có mô tả", "chưa cấu hình tổng số buổi",
                        "chưa công bố học phí");
    }

    /** Mô tả chỉ gồm khoảng trắng cũng phải bị coi là "chưa có mô tả", không in ra một dòng trống. */
    @Test
    void treatsABlankDescriptionAsMissing() {
        assertThat(ChatPromptRules.buildGroundingContext(List.of(course("Vẽ", "   ", 12, "1000000"))))
                .contains("chưa có mô tả");
    }

    /** Global Constraint: luật chống bịa đặt là bắt buộc, không tắt được - nên nó phải có mặt trong
     * MỌI system text, kể cả khi truy xuất rỗng. */
    @Test
    void alwaysPutsTheAntiHallucinationRuleAheadOfTheCourseList() {
        var withCourses = ChatPromptRules.buildSystemText(List.of(course("Toán 9", "Mô tả", 24, "1")));
        var withoutCourses = ChatPromptRules.buildSystemText(List.of());

        assertThat(withCourses).startsWith(ChatPromptRules.SYSTEM_PROMPT).contains("Toán 9");
        assertThat(withoutCourses).startsWith(ChatPromptRules.SYSTEM_PROMPT);
    }

    /** Bốn mệnh lệnh của system prompt (spec mục 3.2) đều phải có mặt - xoá một trong bốn là mở đường
     * cho chatbot bịa khóa học, bịa học phí, hoặc trả lời bằng tiếng Anh. */
    @Test
    void theSystemPromptCarriesAllFourRulesTheSpecDictates() {
        assertThat(ChatPromptRules.SYSTEM_PROMPT)
                .contains("CHỈ trả lời dựa trên danh sách")
                .contains("không bịa")
                .contains("chưa tìm thấy")
                .contains("tiếng Việt");
    }

    @Test
    void appendsTheNewQuestionAsTheLastUserTurn() {
        var turns = ChatPromptRules.toProviderTurns(List.of(
                new ChatTurnPayload(ChatConstants.TurnRole.USER, "có khóa toán không"),
                new ChatTurnPayload(ChatConstants.TurnRole.ASSISTANT, "Có khóa Toán 9.")),
                "học phí bao nhiêu");

        assertThat(turns).hasSize(3);
        assertThat(turns.get(0)).isEqualTo(new AiChatTurn(AiChatTurn.Role.USER, "có khóa toán không"));
        assertThat(turns.get(1)).isEqualTo(new AiChatTurn(AiChatTurn.Role.ASSISTANT, "Có khóa Toán 9."));
        assertThat(turns.get(2)).isEqualTo(new AiChatTurn(AiChatTurn.Role.USER, "học phí bao nhiêu"));
    }

    @Test
    void sendsOnlyTheNewQuestionWhenThereIsNoHistoryYet() {
        var turns = ChatPromptRules.toProviderTurns(List.of(), "có khóa nào buổi tối không");

        assertThat(turns).containsExactly(
                new AiChatTurn(AiChatTurn.Role.USER, "có khóa nào buổi tối không"));
    }

    /**
     * Review Focus #7: lịch sử do client gửi lên không bị chặn bởi bất cứ gì ở client. Chỉ
     * MAX_HISTORY_TURNS lượt GẦN NHẤT được gửi - giữ lượt gần nhất (không phải lượt đầu) vì đó là
     * phần hội thoại câu hỏi hiện tại thực sự dựa vào.
     */
    @Test
    void keepsOnlyTheMostRecentTurnsWhenTheHistoryIsAbsurdlyLong() {
        var huge = IntStream.range(0, 500)
                .mapToObj(index -> new ChatTurnPayload(ChatConstants.TurnRole.USER, "câu " + index))
                .toList();

        var turns = ChatPromptRules.toProviderTurns(huge, "câu cuối");

        assertThat(turns).hasSize(ChatConstants.Limits.MAX_HISTORY_TURNS + 1);
        assertThat(turns.get(0).content())
                .as("lượt đầu được giữ phải là lượt thứ 480, không phải lượt 0")
                .isEqualTo("câu " + (500 - ChatConstants.Limits.MAX_HISTORY_TURNS));
        assertThat(turns.get(turns.size() - 1).content()).isEqualTo("câu cuối");
    }

    /** Đúng ngưỡng thì không cắt gì - một lỗi off-by-one ở đây sẽ âm thầm bỏ mất lượt cũ nhất của mọi
     * hội thoại dài đúng 20 lượt. */
    @Test
    void keepsTheWholeHistoryWhenItIsExactlyAtTheLimit() {
        var exact = IntStream.range(0, ChatConstants.Limits.MAX_HISTORY_TURNS)
                .mapToObj(index -> new ChatTurnPayload(ChatConstants.TurnRole.USER, "câu " + index))
                .toList();

        var turns = ChatPromptRules.toProviderTurns(exact, "mới");

        assertThat(turns).hasSize(ChatConstants.Limits.MAX_HISTORY_TURNS + 1);
        assertThat(turns.get(0).content()).isEqualTo("câu 0");
    }

    /** Review Focus #6: trần độ dài phải được quyết định ở đây, nơi backend kiểm được, không phải ở
     * maxLength của một ô input. */
    @Test
    void rejectsAMessageLongerThanTheLimit() {
        var tooLong = "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH + 1);

        assertThat(ChatPromptRules.isMessageAcceptable(tooLong)).isFalse();
    }

    @Test
    void acceptsAMessageExactlyAtTheLimit() {
        var exact = "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH);

        assertThat(ChatPromptRules.isMessageAcceptable(exact)).isTrue();
    }

    /** Review Focus #10: một câu hỏi toàn khoảng trắng không được tiêu một lệnh gọi LLM nào. */
    @Test
    void rejectsAMessageThatIsOnlyWhitespace() {
        assertThat(ChatPromptRules.isMessageAcceptable("   \n\t  ")).isFalse();
        assertThat(ChatPromptRules.isMessageAcceptable("")).isFalse();
    }

    /** Khoảng trắng hai đầu không được tính vào trần độ dài - người dán văn bản thường kéo theo
     * khoảng trắng, và bị 400 vì một dấu cách là một lỗi vô lý. */
    @Test
    void ignoresSurroundingWhitespaceWhenMeasuringTheLength() {
        var padded = "  " + "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH) + "  ";

        assertThat(ChatPromptRules.isMessageAcceptable(padded)).isTrue();
    }

    @Test
    void acceptsANormalQuestion() {
        assertThat(ChatPromptRules.isMessageAcceptable("có khóa tiếng Anh buổi tối không")).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=ChatPromptRulesTest`
Expected: COMPILATION FAILURE — `cannot find symbol: class ChatPromptRules`.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/eduerp/modules/chat/internal/rules/ChatPromptRules.java`:

```java
package com.eduerp.modules.chat.internal.rules;

import com.eduerp.integrations.ai.AiChatTurn;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.dto.ChatTurnPayload;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code GradeRules}. */
public final class ChatPromptRules {

    private ChatPromptRules() {
    }

    /**
     * Luật chống bịa đặt, copy nguyên văn spec mục 3.2. CỐ ĐỊNH trong code, không cấu hình được qua
     * property hay UI (spec mục 8): đây là thứ duy nhất ngăn chatbot hứa với phụ huynh một khóa học
     * hoặc một mức học phí không tồn tại, nên nó không được là một giá trị ai cũng sửa được lúc
     * runtime.
     */
    public static final String SYSTEM_PROMPT = """
            Bạn là trợ lý tư vấn của một trung tâm dạy thêm tại Việt Nam. CHỈ trả lời dựa trên danh \
            sách khóa học dưới đây - không bịa thêm khóa học, lịch học hay học phí nào không có trong \
            danh sách. Nếu không có khóa nào phù hợp với câu hỏi, hãy nói rõ là chưa tìm thấy và đề \
            nghị học viên liên hệ trung tâm. Luôn trả lời bằng tiếng Việt.""";

    static final String NO_MATCH_CONTEXT =
            "Không tìm thấy khóa học nào đang mở khớp với câu hỏi này.";

    static final String COURSE_LIST_HEADER = "Danh sách khóa học đang mở có liên quan:";

    private static final String MISSING_DESCRIPTION = "chưa có mô tả";
    private static final String MISSING_SESSION_COUNT = "chưa cấu hình tổng số buổi";
    private static final String MISSING_FEE = "chưa công bố học phí";

    /** Toàn bộ nội dung system message gửi cho provider: luật TRƯỚC, dữ liệu SAU. Thứ tự đó có chủ
     * đích - luật đặt sau dữ liệu sẽ bị một danh sách dài đẩy ra xa chỗ model "để ý" nhất. */
    public static String buildSystemText(List<CoursesManagement.OpenCourseSummary> courses) {
        return SYSTEM_PROMPT + "\n\n" + buildGroundingContext(courses);
    }

    /**
     * Review Focus #2: KHÔNG BAO GIỜ trả về chuỗi rỗng. Truy xuất rỗng là một câu khẳng định rõ ràng
     * ("không tìm thấy khóa nào"), vì một system message chỉ có luật mà không có dữ liệu nào là đúng
     * điều kiện khiến model tự lấp chỗ trống bằng khóa học nó tưởng tượng ra.
     */
    public static String buildGroundingContext(List<CoursesManagement.OpenCourseSummary> courses) {
        if (courses.isEmpty()) {
            return NO_MATCH_CONTEXT;
        }
        return COURSE_LIST_HEADER + "\n" + courses.stream()
                .map(ChatPromptRules::describe)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Review Focus #7: cắt bớt lịch sử xuống {@code MAX_HISTORY_TURNS} lượt GẦN NHẤT rồi nối câu hỏi
     * mới vào cuối. Cắt chứ không từ chối: lịch sử lớn dần là hành vi BÌNH THƯỜNG của một phiên chat
     * dài, không phải một client xấu tính, nên trả 400 ở lượt thứ 21 là làm hỏng tính năng.
     */
    public static List<AiChatTurn> toProviderTurns(List<ChatTurnPayload> history, String newMessage) {
        var recent = history.size() <= ChatConstants.Limits.MAX_HISTORY_TURNS
                ? history
                : history.subList(history.size() - ChatConstants.Limits.MAX_HISTORY_TURNS, history.size());
        var turns = new ArrayList<AiChatTurn>(recent.size() + 1);
        recent.forEach(turn -> turns.add(new AiChatTurn(toProviderRole(turn.role()), turn.content())));
        turns.add(new AiChatTurn(AiChatTurn.Role.USER, newMessage.strip()));
        return List.copyOf(turns);
    }

    /**
     * Review Focus #6: trần độ dài được quyết định ở backend. Đo sau {@code strip()} vì khoảng trắng
     * hai đầu là thứ người dán văn bản luôn kéo theo và không tốn token đáng kể.
     */
    public static boolean isMessageAcceptable(String message) {
        var trimmed = message.strip();
        return !trimmed.isEmpty() && trimmed.length() <= ChatConstants.Limits.MAX_MESSAGE_LENGTH;
    }

    private static AiChatTurn.Role toProviderRole(ChatConstants.TurnRole role) {
        return role == ChatConstants.TurnRole.ASSISTANT ? AiChatTurn.Role.ASSISTANT : AiChatTurn.Role.USER;
    }

    private static String describe(CoursesManagement.OpenCourseSummary course) {
        return "- " + course.name() + " (mã " + course.code() + "): " + describeDescription(course)
                + "; " + describeSessionCount(course) + "; " + describeFee(course);
    }

    private static String describeDescription(CoursesManagement.OpenCourseSummary course) {
        return course.description() == null || course.description().isBlank()
                ? MISSING_DESCRIPTION : course.description().strip();
    }

    private static String describeSessionCount(CoursesManagement.OpenCourseSummary course) {
        return course.standardSessionCount() == null
                ? MISSING_SESSION_COUNT : "tổng " + course.standardSessionCount() + " buổi";
    }

    private static String describeFee(CoursesManagement.OpenCourseSummary course) {
        return course.tuitionFee() == null
                ? MISSING_FEE : "học phí " + course.tuitionFee().toPlainString() + " VND";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=ChatPromptRulesTest`
Expected: PASS — 16 tests.

- [ ] **Step 5: Verify module boundaries still hold**

Run: `cd backend && ./mvnw test -Dtest=ModularityTests`
Expected: PASS — `modules.chat.internal.rules` chỉ chạm tới base package của
`modules.courses` (`CoursesManagement.OpenCourseSummary`) và của `integrations.ai` (`AiChatTurn`),
không chạm `internal` của ai.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/chat/internal/rules/ChatPromptRules.java \
        backend/src/test/java/com/eduerp/modules/chat/internal/rules/ChatPromptRulesTest.java
git commit -m "feat(chat): add the anti-hallucination prompt rules as a pure function"
```

---

## Task 9: `ChatRateLimiter` — Redis INCR + EXPIRE theo tài khoản, fail closed

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/chat/internal/ChatRateLimiter.java`
- Test: `backend/src/test/java/com/eduerp/modules/chat/internal/ChatRateLimiterTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/chat/internal/ChatRateLimiterIT.java`

**Interfaces:**
- Consumes: `org.springframework.data.redis.core.StringRedisTemplate` (dependency `spring-boot-starter-data-redis` ĐÃ CÓ trong `pom.xml`, đã xác nhận bằng grep — `PermissionCacheService` đang dùng chính nó), `com.eduerp.integrations.cache.CacheKeyBuilder.key(String, Object)`, `ChatConstants.CacheNamespaces.CHAT_RATE_LIMIT`, `ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE`, `ChatConstants.RateLimit.WINDOW`, `TooManyMessagesException`, `ChatbotUnavailableException`.
- Produces: `ChatRateLimiter.checkAndIncrement(UUID accountId): void` (public; ném `TooManyMessagesException` khi vượt hạn mức, `ChatbotUnavailableException` khi Redis không đọc được). Constructor `ChatRateLimiter(StringRedisTemplate)` package-private — test trong cùng package dựng được bằng `new`.

- [ ] **Step 1: Write the failing unit test for the fail-closed decision**

Create `backend/src/test/java/com/eduerp/modules/chat/internal/ChatRateLimiterTest.java`:

```java
package com.eduerp.modules.chat.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.chat.ChatbotUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Review Focus #8 - quyết định mà spec không nói: Redis sập đúng lúc kiểm rate limit thì FAIL CLOSED.
 *
 * <p>Lý do: cả mục đích tồn tại của bộ đếm này là chặn một hoá đơn API bất ngờ (spec mục 2), nên "đo
 * không được thì cứ gọi" chính là tình huống nó sinh ra để ngăn. Mất tính năng chat vài phút rẻ hơn
 * một hoá đơn không giới hạn - và thực tế khi Redis sập thì {@code PermissionCacheService} cũng sập
 * theo nên gần như không ai đi tới được bước này. 503 có errorCode vẫn đúng tinh thần "không bao giờ
 * 500 trống".
 */
class ChatRateLimiterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ChatRateLimiter limiter = new ChatRateLimiter(redis);

    @Test
    void failsClosedWhenRedisIsUnreachable() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatThrownBy(() -> limiter.checkAndIncrement(UUID.randomUUID()))
                .isInstanceOf(ChatbotUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    /** Lỗi xảy ra ở lệnh INCR (không phải lúc lấy opsForValue) cũng phải fail closed như nhau. */
    @Test
    void failsClosedWhenTheIncrementItselfFails() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatThrownBy(() -> limiter.checkAndIncrement(UUID.randomUUID()))
                .isInstanceOf(ChatbotUnavailableException.class);
    }
}
```

- [ ] **Step 2: Write the failing integration test against a real Redis**

Create `backend/src/test/java/com/eduerp/modules/chat/internal/ChatRateLimiterIT.java`:

```java
package com.eduerp.modules.chat.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.TooManyMessagesException;
import com.redis.testcontainers.RedisContainer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis THẬT qua Testcontainers, mirror {@code PermissionCacheServiceIT}: một bộ đếm tần suất test
 * bằng mock chỉ chứng minh được là code gọi đúng method, không chứng minh được ngưỡng và TTL thật
 * hoạt động - và chính ngưỡng là thứ chặn hoá đơn API.
 */
@Testcontainers
@SpringBootTest
class ChatRateLimiterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    ChatRateLimiter limiter;

    @Autowired
    StringRedisTemplate redisTemplate;

    private static String keyOf(UUID accountId) {
        return CacheKeyBuilder.key(ChatConstants.CacheNamespaces.CHAT_RATE_LIMIT, accountId);
    }

    /** Spec Ruling R3: đúng 10 câu hỏi/phút được phép, câu thứ 11 bị chặn. */
    @Test
    void allowsExactlyTheQuotaThenRefusesTheNextMessage() {
        var accountId = UUID.randomUUID();

        IntStream.range(0, ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE).forEach(index ->
                assertThatCode(() -> limiter.checkAndIncrement(accountId))
                        .as("câu hỏi thứ " + (index + 1) + " phải được phép")
                        .doesNotThrowAnyException());

        assertThatThrownBy(() -> limiter.checkAndIncrement(accountId))
                .isInstanceOf(TooManyMessagesException.class)
                .hasMessageContaining(accountId.toString());
    }

    /** Hạn mức là theo TÀI KHOẢN: một học viên dùng hết hạn mức không được làm người khác bị chặn. */
    @Test
    void countsEachAccountSeparately() {
        var noisy = UUID.randomUUID();
        var quiet = UUID.randomUUID();
        IntStream.range(0, ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE)
                .forEach(index -> limiter.checkAndIncrement(noisy));

        assertThatThrownBy(() -> limiter.checkAndIncrement(noisy))
                .isInstanceOf(TooManyMessagesException.class);
        assertThatCode(() -> limiter.checkAndIncrement(quiet)).doesNotThrowAnyException();
    }

    /**
     * Không có TTL thì bộ đếm là vĩnh viễn: một học viên hỏi 10 câu hôm nay sẽ bị chặn mãi mãi, và
     * Redis đầy dần theo số tài khoản. Đây là khẳng định quan trọng nhất của class này.
     */
    @Test
    void setsAOneMinuteExpiryOnTheFirstMessageOfAWindow() {
        var accountId = UUID.randomUUID();

        limiter.checkAndIncrement(accountId);

        var ttl = redisTemplate.getExpire(keyOf(accountId), TimeUnit.SECONDS);
        assertThat(ttl).as("key phải có TTL thật, -1 nghĩa là không bao giờ hết hạn")
                .isBetween(1L, ChatConstants.RateLimit.WINDOW.toSeconds());
    }

    /** Cửa sổ hết hạn (ở đây giả lập bằng cách xoá key - chờ 60 giây thật sẽ làm suite chậm vô lý)
     * thì hạn mức được cấp lại nguyên vẹn, và TTL cũng được đặt lại. */
    @Test
    void grantsAFreshQuotaAfterTheWindowExpires() {
        var accountId = UUID.randomUUID();
        IntStream.range(0, ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE)
                .forEach(index -> limiter.checkAndIncrement(accountId));

        redisTemplate.delete(keyOf(accountId));

        assertThatCode(() -> limiter.checkAndIncrement(accountId)).doesNotThrowAnyException();
        assertThat(redisTemplate.getExpire(keyOf(accountId), TimeUnit.SECONDS))
                .isBetween(1L, ChatConstants.RateLimit.WINDOW.toSeconds());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest='ChatRateLimiterTest,ChatRateLimiterIT'`
Expected: COMPILATION FAILURE — `cannot find symbol: class ChatRateLimiter`.

- [ ] **Step 4: Write minimal implementation**

Create `backend/src/main/java/com/eduerp/modules/chat/internal/ChatRateLimiter.java`:

```java
package com.eduerp.modules.chat.internal;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.ChatbotUnavailableException;
import com.eduerp.modules.chat.TooManyMessagesException;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Hạn mức tần suất theo tài khoản, cửa sổ một phút, đếm bằng Redis INCR + EXPIRE - mirror cách
 * {@code PermissionCacheService} tiêm {@code StringRedisTemplate} (dependency
 * {@code spring-boot-starter-data-redis} đã có sẵn).
 *
 * <p>Vì sao Redis chứ không một bộ đếm trong bộ nhớ: app chạy nhiều instance được, và một bộ đếm
 * trong bộ nhớ sẽ cho mỗi instance một hạn mức riêng - tức hạn mức thật là 10 nhân số instance,
 * đúng thứ mà một biện pháp chặn chi phí không được phép mắc.
 *
 * <p>Cửa sổ là "fixed window" đơn giản, không sliding: một học viên có thể gửi 10 câu ở giây 59 và 10
 * câu nữa ở giây 61. Chấp nhận được - đây là chặn lạm dụng/sự cố, không phải chống tấn công có tổ
 * chức, và một sliding window đòi một sorted set cùng việc dọn rác không đáng cho việc này.
 */
@Component
public class ChatRateLimiter {

    private final StringRedisTemplate redis;

    ChatRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @throws TooManyMessagesException đã vượt {@code MAX_MESSAGES_PER_MINUTE} trong cửa sổ hiện tại
     * @throws ChatbotUnavailableException Redis không đọc được - fail CLOSED, xem javadoc
     *     {@code ChatRateLimiterTest}
     */
    public void checkAndIncrement(UUID accountId) {
        if (increment(accountId) > ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE) {
            throw new TooManyMessagesException(accountId);
        }
    }

    private long increment(UUID accountId) {
        var key = CacheKeyBuilder.key(ChatConstants.CacheNamespaces.CHAT_RATE_LIMIT, accountId);
        try {
            Long used = redis.opsForValue().increment(key);
            if (used != null && used == 1L) {
                // Đặt TTL ĐÚNG MỘT LẦN, ở lượt đầu của cửa sổ. Đặt lại ở mọi lượt sẽ biến cửa sổ
                // thành "một phút kể từ câu hỏi CUỐI", nghĩa là một người hỏi liên tục không bao giờ
                // được cấp lại hạn mức.
                redis.expire(key, ChatConstants.RateLimit.WINDOW);
            }
            // increment() chỉ trả null khi đang trong pipeline/transaction - không phải ở đây. Coi nó
            // là lượt đầu tiên thay vì ném NPE: một bộ đếm không đọc được giá trị thì chặn bằng
            // nhánh catch bên dưới, không bằng một NullPointerException giữa luồng.
            return used == null ? 1L : used;
        } catch (RuntimeException redisUnavailable) {
            throw new ChatbotUnavailableException("không đo được hạn mức tần suất", redisUnavailable);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest='ChatRateLimiterTest,ChatRateLimiterIT'`
Expected: PASS — 2 + 4 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/chat/internal/ChatRateLimiter.java \
        backend/src/test/java/com/eduerp/modules/chat/internal/ChatRateLimiterTest.java \
        backend/src/test/java/com/eduerp/modules/chat/internal/ChatRateLimiterIT.java
git commit -m "feat(chat): add a redis-backed per-account rate limiter that fails closed"
```

---

## Task 10: Usecase `AskChatbot` — ghép năm bước theo đúng thứ tự

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/chat/usecase/AskChatbot.java`
- Test: `backend/src/test/java/com/eduerp/modules/chat/usecase/AskChatbotTest.java`

**Interfaces:**
- Consumes: `CoursesManagement.searchOpenCourses(String, int)` + `CoursesManagement.OpenCourseSummary` (Task 4), `AiChatClient.reply(String, List<AiChatTurn>)` + `AiProviderException` + `AiChatTurn` (Task 6), `ChatConstants.Limits` (Task 7), `AskChatbotRequest`/`AskChatbotResponse`/`ChatTurnPayload` (Task 7), `ChatPromptRules.{isMessageAcceptable, buildSystemText, toProviderTurns}` (Task 8), `ChatRateLimiter.checkAndIncrement(UUID)` (Task 9), `MessageTooLongException`/`ChatbotUnavailableException` (Task 7).
- Produces: `AskChatbot.execute(UUID actorAccountId, AskChatbotRequest request): AskChatbotResponse` (public, KHÔNG `@Transactional`).

**Ghi chú plan:** `execute` **cố ý không mang `@Transactional`**, khác mọi usecase khác trong repo. Lý do là bài học đã được ghi thẳng trong javadoc của `PaymentHttpClientConfig`: một lệnh gọi HTTP ra ngoài nằm trong một usecase `@Transactional` giữ một connection HikariCP suốt thời gian chờ mạng. Ở đây thời gian chờ là 15-60 giây (một model chat), nên bọc transaction quanh nó là cách nhanh nhất rút cạn connection pool của toàn app bằng vài chục câu hỏi đồng thời. Usecase này không ghi gì cả, và `CoursesManagement.searchOpenCourses` đã tự có `@Transactional(readOnly = true)` cho đúng phần cần DB.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/chat/usecase/AskChatbotTest.java`:

```java
package com.eduerp.modules.chat.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.ai.AiChatClient;
import com.eduerp.integrations.ai.AiChatTurn;
import com.eduerp.integrations.ai.AiProviderException;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.ChatbotUnavailableException;
import com.eduerp.modules.chat.MessageTooLongException;
import com.eduerp.modules.chat.TooManyMessagesException;
import com.eduerp.modules.chat.dto.AskChatbotRequest;
import com.eduerp.modules.chat.dto.ChatTurnPayload;
import com.eduerp.modules.chat.internal.ChatRateLimiter;
import com.eduerp.modules.courses.CoursesManagement;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Mirror {@code ExportUseCasesTest}: mock thẳng các collaborator ({@code mock(AiChatClient.class)} -
 * class concrete nên Mockito mock được, và cách này đảm bảo KHÔNG test nào gọi Anthropic thật, đúng
 * Global Constraint).
 *
 * <p>Thứ tự gọi là nội dung chính của class này, không phải một chi tiết: Review Focus #1 nói một
 * request bị chặn không được phép tốn một lệnh gọi API trả phí, và cách duy nhất chốt được điều đó là
 * {@code verifyNoInteractions}.
 */
class AskChatbotTest {

    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final AiChatClient aiChatClient = mock(AiChatClient.class);
    private final ChatRateLimiter rateLimiter = mock(ChatRateLimiter.class);
    private final AskChatbot askChatbot = new AskChatbot(courses, aiChatClient, rateLimiter);

    private final UUID actorAccountId = UUID.randomUUID();

    private static CoursesManagement.OpenCourseSummary course(String name) {
        return new CoursesManagement.OpenCourseSummary(UUID.randomUUID(), "C-" + name.hashCode(), name,
                "Mô tả " + name, 24, new BigDecimal("7000000"));
    }

    private String captureSystemText() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(aiChatClient).reply(captor.capture(), any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<AiChatTurn> captureTurns() {
        var captor = ArgumentCaptor.forClass(List.class);
        verify(aiChatClient).reply(anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    void answersWithTheProviderReplyGroundedOnTheRetrievedCourses() {
        when(courses.searchOpenCourses("có khóa tiếng Anh không",
                ChatConstants.Limits.MAX_RETRIEVED_COURSES))
                .thenReturn(List.of(course("Tiếng Anh giao tiếp")));
        when(aiChatClient.reply(anyString(), any())).thenReturn("Trung tâm đang mở khóa Tiếng Anh.");

        var response = askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa tiếng Anh không", List.of()));

        assertThat(response.reply()).isEqualTo("Trung tâm đang mở khóa Tiếng Anh.");
        assertThat(captureSystemText()).contains("Tiếng Anh giao tiếp");
    }

    /** Truy xuất bị giới hạn đúng hằng số của module - không ai được phép truyền một limit tuỳ ý làm
     * phình prompt. */
    @Test
    void asksTheFacadeForAtMostTheConfiguredNumberOfCourses() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("khóa nào", List.of()));

        verify(courses).searchOpenCourses("khóa nào", ChatConstants.Limits.MAX_RETRIEVED_COURSES);
    }

    /**
     * Review Focus #1 - khẳng định quan trọng nhất của cả Phân hệ 5: một request vượt hạn mức KHÔNG
     * được tốn một lệnh gọi API trả phí, và cũng không tốn một câu truy vấn DB.
     */
    @Test
    void checksTheRateLimitBeforeSpendingAnyMoneyOnTheProvider() {
        doThrow(new TooManyMessagesException(actorAccountId))
                .when(rateLimiter).checkAndIncrement(actorAccountId);

        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa nào không", List.of())))
                .isInstanceOf(TooManyMessagesException.class);

        verifyNoInteractions(aiChatClient);
        verifyNoInteractions(courses);
    }

    /** Review Focus #6: câu hỏi quá dài bị chặn TRƯỚC cả bộ đếm tần suất - một request không hợp lệ
     * không được tiêu một suất trong hạn mức của người gửi. */
    @Test
    void rejectsATooLongMessageBeforeTouchingTheRateLimiterOrTheProvider() {
        var tooLong = "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH + 1);

        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest(tooLong, List.of())))
                .isInstanceOf(MessageTooLongException.class)
                .hasMessageContaining(String.valueOf(ChatConstants.Limits.MAX_MESSAGE_LENGTH));

        verifyNoInteractions(rateLimiter);
        verifyNoInteractions(aiChatClient);
        verifyNoInteractions(courses);
    }

    /** Review Focus #10: câu hỏi rỗng/toàn khoảng trắng cũng bị chặn ở cùng chỗ, cùng errorCode
     * (spec mục 5 gộp hai tình huống). */
    @Test
    void rejectsABlankMessage() {
        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest("   \n  ", List.of())))
                .isInstanceOf(MessageTooLongException.class)
                .hasMessageContaining("trống");

        verifyNoInteractions(rateLimiter);
        verifyNoInteractions(aiChatClient);
    }

    /**
     * Review Focus #2: truy xuất rỗng KHÔNG phải một lỗi và cũng không được thành một system text
     * rỗng - nó phải là một câu khẳng định rõ ràng, vì một system message chỉ có luật mà không có dữ
     * liệu là đúng điều kiện khiến model tự bịa ra khóa học.
     */
    @Test
    void stillSendsANonBlankSystemTextWhenRetrievalFoundNothing() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("Chưa tìm thấy khóa phù hợp.");

        var response = askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa lập trình nhúng không", List.of()));

        assertThat(response.reply()).isEqualTo("Chưa tìm thấy khóa phù hợp.");
        assertThat(captureSystemText()).isNotBlank().contains("Không tìm thấy khóa học nào");
    }

    /** Luật chống bịa đặt phải có trong MỌI lệnh gọi, kể cả khi truy xuất có kết quả. */
    @Test
    void alwaysSendsTheAntiHallucinationRule() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of(course("Toán 9")));
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("toán 9 học phí bao nhiêu", List.of()));

        assertThat(captureSystemText()).contains("CHỈ trả lời dựa trên danh sách", "không bịa");
    }

    @Test
    void sendsTheConversationHistoryFollowedByTheNewQuestion() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("học phí bao nhiêu", List.of(
                new ChatTurnPayload(ChatConstants.TurnRole.USER, "có khóa toán không"),
                new ChatTurnPayload(ChatConstants.TurnRole.ASSISTANT, "Có khóa Toán 9."))));

        assertThat(captureTurns()).containsExactly(
                new AiChatTurn(AiChatTurn.Role.USER, "có khóa toán không"),
                new AiChatTurn(AiChatTurn.Role.ASSISTANT, "Có khóa Toán 9."),
                new AiChatTurn(AiChatTurn.Role.USER, "học phí bao nhiêu"));
    }

    /** Review Focus #7: một client gửi 500 lượt lịch sử trong một request không được biến thành một
     * prompt 500 lượt - rate limit đếm số REQUEST nên nó không thấy chuyện này. */
    @Test
    void truncatesAnAbsurdlyLongHistoryBeforeCallingTheProvider() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var huge = IntStream.range(0, 500)
                .mapToObj(index -> new ChatTurnPayload(ChatConstants.TurnRole.USER, "câu " + index))
                .toList();

        askChatbot.execute(actorAccountId, new AskChatbotRequest("câu mới", huge));

        assertThat(captureTurns()).hasSize(ChatConstants.Limits.MAX_HISTORY_TURNS + 1);
    }

    /** Lượt hỏi đầu tiên của phiên: history vắng mặt trong JSON là hợp lệ, không phải NullPointer. */
    @Test
    void acceptsARequestWithoutAnyHistoryField() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("câu đầu tiên", null));

        assertThat(captureTurns())
                .containsExactly(new AiChatTurn(AiChatTurn.Role.USER, "câu đầu tiên"));
    }

    /**
     * Review Focus #5: lỗi hạ tầng của nhà cung cấp phải thành MỘT lỗi nghiệp vụ 503 có errorCode.
     * Nguyên nhân gốc phải được giữ lại, nếu không không ai debug được từ log.
     */
    @Test
    void translatesAiProviderExceptionIntoABusinessFailure() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        var providerFailure = new AiProviderException("401 authentication_error", null);
        when(aiChatClient.reply(anyString(), any())).thenThrow(providerFailure);

        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa nào không", List.of())))
                .isInstanceOf(ChatbotUnavailableException.class)
                .hasCause(providerFailure);
    }

    /** Lỗi của bộ đếm tần suất (Redis sập - đã là ChatbotUnavailableException) phải nổi nguyên vẹn,
     * không bị nhánh catch của provider bọc thêm một lớp. */
    @Test
    void propagatesARateLimiterFailureUnchanged() {
        var redisFailure = new ChatbotUnavailableException("không đo được hạn mức tần suất", null);
        doThrow(redisFailure).when(rateLimiter).checkAndIncrement(actorAccountId);

        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa nào không", List.of())))
                .isSameAs(redisFailure);

        verify(aiChatClient, never()).reply(anyString(), any());
    }

    /** Hạn mức đếm theo ĐÚNG tài khoản gọi, không theo một id nào khác. */
    @Test
    void countsTheQuotaAgainstTheCallingAccount() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("câu hỏi", List.of()));

        verify(rateLimiter).checkAndIncrement(eq(actorAccountId));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AskChatbotTest`
Expected: COMPILATION FAILURE — `cannot find symbol: class AskChatbot`.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/eduerp/modules/chat/usecase/AskChatbot.java`:

```java
package com.eduerp.modules.chat.usecase;

import com.eduerp.integrations.ai.AiChatClient;
import com.eduerp.integrations.ai.AiChatTurn;
import com.eduerp.integrations.ai.AiProviderException;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.ChatbotUnavailableException;
import com.eduerp.modules.chat.MessageTooLongException;
import com.eduerp.modules.chat.dto.AskChatbotRequest;
import com.eduerp.modules.chat.dto.AskChatbotResponse;
import com.eduerp.modules.chat.internal.ChatRateLimiter;
import com.eduerp.modules.chat.internal.rules.ChatPromptRules;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Hỏi chatbot tư vấn khóa học (spec mục 3.2). Năm bước, và THỨ TỰ là nội dung nghiệp vụ chứ không
 * phải một chi tiết cài đặt:
 *
 * <ol>
 *   <li>Kiểm độ dài câu hỏi - chặn một đoạn văn khổng lồ làm phình prompt (Review Focus #6). Kiểm
 *       TRƯỚC bộ đếm tần suất: một request không hợp lệ không được tiêu một suất trong hạn mức của
 *       người gửi.</li>
 *   <li>{@code ChatRateLimiter.checkAndIncrement} - TRƯỚC khi gọi LLM, vì "một request bị chặn không
 *       được phép vẫn tốn một lệnh gọi API trả phí" (Review Focus #1).</li>
 *   <li>{@code CoursesManagement.searchOpenCourses} - truy xuất khóa học ĐANG MỞ qua facade.</li>
 *   <li>{@code ChatPromptRules.buildSystemText} - hàm thuần, dựng luật chống bịa đặt + danh sách.</li>
 *   <li>{@code AiChatClient.reply} - lệnh gọi duy nhất tốn tiền trong cả luồng.</li>
 * </ol>
 *
 * <p><b>Cố ý KHÔNG mang {@code @Transactional}</b>, khác mọi usecase khác trong repo. Đây là bài học
 * đã ghi thẳng trong javadoc của {@code PaymentHttpClientConfig}: một lệnh gọi HTTP ra ngoài nằm
 * trong một usecase {@code @Transactional} giữ một connection HikariCP suốt thời gian chờ mạng. Ở
 * đây thời gian chờ là 15-60 giây (một model chat), nên bọc transaction quanh nó là cách nhanh nhất
 * rút cạn connection pool của toàn app bằng vài chục câu hỏi đồng thời. Usecase này không ghi gì, và
 * {@code searchOpenCourses} đã tự có {@code @Transactional(readOnly = true)} cho đúng phần cần DB.
 */
@Service
public class AskChatbot {

    private final CoursesManagement courses;
    private final AiChatClient aiChatClient;
    private final ChatRateLimiter rateLimiter;

    AskChatbot(CoursesManagement courses, AiChatClient aiChatClient, ChatRateLimiter rateLimiter) {
        this.courses = courses;
        this.aiChatClient = aiChatClient;
        this.rateLimiter = rateLimiter;
    }

    public AskChatbotResponse execute(UUID actorAccountId, AskChatbotRequest request) {
        var message = request.message();
        if (!ChatPromptRules.isMessageAcceptable(message)) {
            throw new MessageTooLongException(message.strip().length());
        }
        rateLimiter.checkAndIncrement(actorAccountId);
        var matched = courses.searchOpenCourses(message.strip(),
                ChatConstants.Limits.MAX_RETRIEVED_COURSES);
        var systemText = ChatPromptRules.buildSystemText(matched);
        var turns = ChatPromptRules.toProviderTurns(request.history(), message);
        return new AskChatbotResponse(reply(systemText, turns));
    }

    private String reply(String systemText, List<AiChatTurn> turns) {
        try {
            return aiChatClient.reply(systemText, turns);
        } catch (AiProviderException providerFailure) {
            // Review Focus #5 + bài học Critical #1 của Phân hệ 4: không bao giờ để một lỗi hạ tầng
            // rò ra thành 500 trống. Bắt HẸP đúng AiProviderException - một ChatbotUnavailableException
            // đến từ ChatRateLimiter (Redis sập) phải nổi nguyên vẹn, không bị bọc hai lần.
            throw new ChatbotUnavailableException("lỗi từ nhà cung cấp mô hình", providerFailure);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AskChatbotTest`
Expected: PASS — 12 tests.

- [ ] **Step 5: Verify the usecase really has no transaction annotation**

Run: `cd backend && grep -n "Transactional" src/main/java/com/eduerp/modules/chat/usecase/AskChatbot.java`
Expected: KHÔNG có kết quả — chỉ có javadoc nhắc tới nó, không có annotation nào.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/chat/usecase/AskChatbot.java \
        backend/src/test/java/com/eduerp/modules/chat/usecase/AskChatbotTest.java
git commit -m "feat(chat): add the AskChatbot use case with rate limiting before any model call"
```

---

## Task 11: RBAC cho resource `CHAT` — migration V27 + seed verification IT

**Files:**
- Create: `backend/src/main/resources/db/migration/V27__seed_chat_rbac.sql`
- Test: `backend/src/test/java/com/eduerp/modules/access/internal/repository/ChatRbacSeedIT.java`

**Interfaces:**
- Consumes: bảng `permissions`, `permission_groups`, `permission_group_items`, `role_permission_groups` (V2/V3/V5/V8); group admin `'11111111-0000-0000-0000-000000000001'` ("Toàn quyền hệ thống", V5); role STUDENT `'22222222-0000-0000-0000-000000000003'` (V5). Cả ba id đã được xác nhận bằng grep toàn bộ `db/migration`: group `...0001`/`...0002` của V5, `...0003` của V25 — nên `...0004` còn trống.
- Produces: permission `(CHAT, CREATE)`; group mới `'11111111-0000-0000-0000-000000000004'` ("Học viên hỏi chatbot") gắn CHỈ cho role STUDENT ở scope `PERSONAL`; dòng `permission_group_items` cho nhóm admin ở scope `ORGANIZATION`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/access/internal/repository/ChatRbacSeedIT.java`:

```java
package com.eduerp.modules.access.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.access.AccessConstants;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mirror {@code AcademicRbacSeedIT}. Đây là lần ĐẦU TIÊN role STUDENT được cấp quyền cho một resource
 * ngoài {@code ACCOUNT} tự phục vụ, nên test này chốt ba điều cùng lúc: admin có CHAT ở
 * ORGANIZATION, STUDENT có CHAT:CREATE ở PERSONAL, và TEACHER KHÔNG có gì.
 */
@Testcontainers
@DataJpaTest
class ChatRbacSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    private List<String> permissionKeysOf(String roleCode) {
        return roles.findByCode(roleCode).orElseThrow()
                .getPermissionGroups().stream()
                .flatMap(group -> group.getItems().stream())
                .map(item -> item.getPermission().getResource() + ":" + item.getPermission().getAction() + ":"
                        + item.getScope().name())
                .toList();
    }

    private static String chatKey(String scope) {
        return AccessConstants.Resources.CHAT + ":" + AccessConstants.Actions.CREATE + ":" + scope;
    }

    @Test
    void adminHasOrganizationScopedChatPermission() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.ADMIN))
                .contains(chatKey(AccessConstants.ScopeNames.ORGANIZATION));
    }

    /**
     * Spec mục 1: học viên là đối tượng chính. Scope PERSONAL là đủ vì
     * {@code AccessRules.CREATE_CHAT} cố ý KHÔNG mang marker scope tối thiểu - nếu ai đó thêm marker
     * đó vào thì test này vẫn xanh nhưng {@code ChatAdminControllerIT} sẽ đỏ, nên hai test phải đọc
     * cùng nhau.
     */
    @Test
    void studentHasPersonalScopedChatCreate() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT))
                .contains(chatKey(AccessConstants.PermissionScope.PERSONAL.name()));
    }

    /**
     * Review Focus #4: V5 gắn group "Tự phục vụ cơ bản" ('...0002') cho CẢ TEACHER và STUDENT, nên
     * nếu V27 nhét CHAT vào group đó thì giáo viên cũng được cấp - trái spec mục 1 ("Giáo viên KHÔNG
     * được cấp quyền"). Test này biến sai sót đó thành build đỏ.
     */
    @Test
    void teacherGetsNoChatPermissionAtAll() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.CHAT + ":"));
    }

    /** Học viên chỉ được CREATE, không được một action nào khác trên CHAT - không có endpoint nào dùng
     * chúng, và một quyền không ai dùng là một quyền sẽ bị dùng sai về sau. */
    @Test
    void studentGetsOnlyTheCreateActionOnChat() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT))
                .filteredOn(key -> key.startsWith(AccessConstants.Resources.CHAT + ":"))
                .containsExactly(chatKey(AccessConstants.PermissionScope.PERSONAL.name()));
    }

    /** Quyền GRADE của Phân hệ 4 và quyền tự phục vụ sẵn có không được mất đi khi thêm group mới. */
    @Test
    void keepsTheExistingPermissionsOfEveryRole() {
        var personalAccountUpdate = AccessConstants.Resources.ACCOUNT + ":"
                + AccessConstants.Actions.UPDATE + ":" + AccessConstants.PermissionScope.PERSONAL.name();
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT)).contains(personalAccountUpdate);
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).contains(personalAccountUpdate);
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).contains(
                AccessConstants.Resources.GRADE + ":" + AccessConstants.Actions.UPDATE + ":"
                        + AccessConstants.PermissionScope.PERSONAL.name());
    }

    /** Học viên vẫn KHÔNG được xem điểm (Global Constraint của Phân hệ 4) - thêm quyền chat không
     * được kéo theo một quyền nào khác. */
    @Test
    void addingChatDidNotAccidentallyGiveTheStudentAnyGradePermission() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.GRADE + ":"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=ChatRbacSeedIT`
Expected: FAIL — `adminHasOrganizationScopedChatPermission` và `studentHasPersonalScopedChatCreate` đỏ
vì chưa có permission `CHAT` nào trong DB.

- [ ] **Step 3: Write the migration**

Create `backend/src/main/resources/db/migration/V27__seed_chat_rbac.sql`:

```sql
INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'CHAT', 'CREATE');

-- V5 đã seed "Toàn quyền hệ thống" xong và đã chạy rồi - không sửa lại được, nên permission mới phải
-- tự thêm dòng gán vào đúng group đó, giống cách V10/V13/V14/V19/V25 đã làm. Admin cần quyền này để
-- kiểm thử/hỗ trợ học viên (spec mục 1).
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource = 'CHAT';

-- Mirror V25: KHÔNG tái dùng group "Tự phục vụ cơ bản" ('...0002'). V5 gắn group đó cho CẢ role
-- TEACHER ('...0002') VÀ role STUDENT ('...0003') trong role_permission_groups, nên nhét CHAT vào đó
-- là cấp luôn cho giáo viên - trái spec mục 1 ("Giáo viên KHÔNG được cấp quyền - roadmap không nhắc
-- tới giáo viên dùng tính năng này"). Vì vậy: group RIÊNG, chỉ gắn cho STUDENT.
-- ChatRbacSeedIT.teacherGetsNoChatPermissionAtAll chốt lại điều này.
-- Id '...0004' còn trống: V5 dùng ...0001/...0002, V25 dùng ...0003.
INSERT INTO permission_groups (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000004', 'Học viên hỏi chatbot',
     'Gửi câu hỏi cho chatbot tư vấn khóa học, scope PERSONAL');

-- Scope PERSONAL là ĐỦ, và đó là chủ đích: AccessConstants.AccessRules.CREATE_CHAT cố ý KHÔNG mang
-- marker '@ORGANIZATION', nên ScopedPermissionEvaluator chỉ hỏi "có quyền CHAT:CREATE ở bất kỳ scope
-- nào không". Ở đây không có khái niệm sở hữu tài nguyên để kiểm thêm như *_GRADE của Phân hệ 4.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000004', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'CHAT';

INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000004'); -- STUDENT
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=ChatRbacSeedIT`
Expected: PASS — 6 tests.

- [ ] **Step 5: Verify the older seed verifications still hold**

Run: `cd backend && ./mvnw test -Dtest='DefaultRbacSeedIT,AcademicRbacSeedIT,EnrollmentBillingRbacSeedIT,RbacAdminControllerIT'`
Expected: PASS — V27 không làm lệch bất kỳ khẳng định nào của các seed trước.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V27__seed_chat_rbac.sql \
        backend/src/test/java/com/eduerp/modules/access/internal/repository/ChatRbacSeedIT.java
git commit -m "feat(access): seed the CHAT permission for admins and students only"
```

---

## Task 12: `ChatAdminController` — một endpoint POST + IT qua HTTP thật với quyền thật

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/chat/web/ChatAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/chat/web/ChatAdminControllerIT.java`

**Interfaces:**
- Consumes: `AskChatbot.execute(UUID, AskChatbotRequest)` (Task 10), `AccessConstants.AccessRules.CREATE_CHAT` (Task 1), `com.eduerp.shared.AccountPrincipal.accountId()`, `AskChatbotRequest`/`AskChatbotResponse` (Task 7).
- Produces: `POST /api/chat/messages` → `AskChatbotResponse` JSON (`{"reply": "..."}`), gác bằng `@PreAuthorize(AccessConstants.AccessRules.CREATE_CHAT)`.

**Ghi chú plan:** tên `ChatAdminController` giữ nguyên theo spec mục 3.2 để truy vết plan↔spec không đứt, dù endpoint này phục vụ học viên chứ không phải admin — javadoc nói rõ điều đó. IT dùng `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`, có sẵn từ Spring Framework 6.2 / Boot 3.4, là bản không deprecated của `@MockBean` mà repo đang dùng ở `AccountAdminControllerIT`) để thay `AiChatClient` — cách này vừa gọn hơn một `@TestConfiguration` + `@Primary`, vừa là bằng chứng kiểm được rằng **không lệnh gọi mạng thật nào xảy ra trong test**.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/chat/web/ChatAdminControllerIT.java`:

```java
package com.eduerp.modules.chat.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.integrations.ai.AiChatClient;
import com.eduerp.integrations.ai.AiProviderException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.dto.AskChatbotRequest;
import com.eduerp.modules.chat.dto.ChatTurnPayload;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Toàn bộ Phân hệ 5 end-to-end qua HTTP thật với quyền thật đọc từ Redis. Đây là chỗ DUY NHẤT chứng
 * minh ba điều mà không tầng nào khác chứng minh được: học viên scope PERSONAL đi qua được
 * {@code @PreAuthorize} (quyết định kỹ thuật #1 của plan), giáo viên bị chặn, và một lỗi hạ tầng của
 * nhà cung cấp ra 503 có errorCode chứ không 500 trống.
 *
 * <p>{@code @MockitoBean AiChatClient} thay bean thật: KHÔNG test nào trong repo được gọi Anthropic
 * thật (Global Constraint - chưa có ANTHROPIC_API_KEY, và một test phụ thuộc mạng/tiền là một test
 * sai). Mock cũng cho phép đọc lại CHÍNH XÁC chuỗi system text đã được gửi đi, thứ mà một lệnh gọi
 * thật không bao giờ cho ta thấy.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ChatAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String CHAT_ENDPOINT = "/api/chat/messages";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @MockitoBean
    AiChatClient aiChatClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    CourseRepository courses;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID newAccount(String emailPrefix, String fullName) {
        return accounts.save(new Account(emailPrefix + "-" + shortId() + "@eduerp.local",
                passwordEncoder.encode(PASSWORD), fullName, null)).getId();
    }

    private Cookie signIn(String emailPrefix, String fullName, String roleCode) throws Exception {
        var accountId = newAccount(emailPrefix, fullName);
        var account = accounts.findById(accountId).orElseThrow();
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(accountId, roleCode);
        access.evictPermissionCache(accountId);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(account.getEmail(), PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private MockHttpServletResponse ask(Cookie cookie, AskChatbotRequest request) throws Exception {
        return mockMvc.perform(post(CHAT_ENDPOINT)
                        .cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse();
    }

    private String captureSystemText() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(aiChatClient).reply(captor.capture(), any());
        return captor.getValue();
    }

    private Course saveCourse(String name, boolean active) {
        var course = new Course("CH-" + shortId(), name, "Mô tả của " + name, 24,
                new BigDecimal("7000000"));
        course.setActive(active);
        return courses.saveAndFlush(course);
    }

    /**
     * Quyết định kỹ thuật #1 của plan, chứng minh end-to-end: học viên chỉ có
     * {@code CHAT:CREATE:PERSONAL} (V27) và vẫn đi qua được {@code @PreAuthorize}. Nếu ai đó thêm
     * marker {@code @ORGANIZATION} vào {@code CREATE_CHAT} thì test này đỏ ngay.
     */
    @Test
    void allowsAStudentWhoOnlyHasPersonalScopedChatPermission() throws Exception {
        when(aiChatClient.reply(anyString(), any()))
                .thenReturn("Trung tâm đang mở khóa Tiếng Anh giao tiếp buổi tối.");
        var student = signIn("chat-hv", "Nguyễn Văn A", AccessConstants.RoleCodes.STUDENT);

        var response = ask(student, new AskChatbotRequest("có khóa tiếng Anh buổi tối không", List.of()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.getContentAsString()).get("reply").asText())
                .isEqualTo("Trung tâm đang mở khóa Tiếng Anh giao tiếp buổi tối.");
    }

    /** Spec mục 1: admin được cấp quyền thêm để kiểm thử/hỗ trợ. */
    @Test
    void allowsAnAdmin() throws Exception {
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var admin = signIn("chat-admin", "Quản trị", AccessConstants.RoleCodes.ADMIN);

        assertThat(ask(admin, new AskChatbotRequest("có khóa nào không", List.of())).getStatus())
                .isEqualTo(200);
    }

    /**
     * Review Focus #4: giáo viên đăng nhập hợp lệ nhưng KHÔNG có quyền CHAT nào (V27 dùng một group
     * riêng chỉ gắn cho STUDENT) nên bị chặn ngay ở cửa - và quan trọng nhất, KHÔNG một lệnh gọi API
     * trả phí nào xảy ra.
     */
    @Test
    void refusesATeacherEntirely() throws Exception {
        var teacher = signIn("chat-gv", "Cô Lan", AccessConstants.RoleCodes.TEACHER);

        assertThat(ask(teacher, new AskChatbotRequest("có khóa nào không", List.of())).getStatus())
                .isEqualTo(403);
        verifyNoInteractions(aiChatClient);
    }

    /**
     * Review Focus #3 end-to-end: khóa đã đóng KHỚP từ khoá nhưng không được lọt vào ngữ cảnh gửi cho
     * model. Đây là chỗ duy nhất kiểm được điều đó trên chuỗi thật DB → facade → prompt.
     */
    @Test
    void neverPutsAnInactiveCourseIntoTheSystemTextSentToTheProvider() throws Exception {
        var marker = shortId();
        saveCourse("Nhiếp ảnh " + marker + " đã đóng", false);
        saveCourse("Nhiếp ảnh " + marker + " đang mở", true);
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var student = signIn("chat-hv-active", "Học viên", AccessConstants.RoleCodes.STUDENT);

        assertThat(ask(student, new AskChatbotRequest("có khóa nhiếp ảnh " + marker + " không",
                List.of())).getStatus()).isEqualTo(200);

        assertThat(captureSystemText())
                .contains("Nhiếp ảnh " + marker + " đang mở")
                .doesNotContain("Nhiếp ảnh " + marker + " đã đóng");
    }

    /** Review Focus #2 end-to-end: không khớp khóa nào vẫn gửi đi một ngữ cảnh có nghĩa, và vẫn trả
     * 200 - "chưa tìm thấy" là một câu trả lời hợp lệ, không phải một lỗi. */
    @Test
    void stillAnswersWhenNoCourseMatches() throws Exception {
        when(aiChatClient.reply(anyString(), any())).thenReturn("Chưa tìm thấy khóa phù hợp.");
        var student = signIn("chat-hv-empty", "Học viên", AccessConstants.RoleCodes.STUDENT);

        assertThat(ask(student, new AskChatbotRequest("có khóa lái tàu vũ trụ " + shortId() + " không",
                List.of())).getStatus()).isEqualTo(200);

        assertThat(captureSystemText()).isNotBlank().contains("Không tìm thấy khóa học nào");
    }

    /** Review Focus #1 end-to-end: câu thứ 11 trong một phút bị 429 có errorCode. */
    @Test
    void returnsTooManyRequestsOnTheEleventhMessageInAMinute() throws Exception {
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var student = signIn("chat-hv-quota", "Học viên hỏi nhiều", AccessConstants.RoleCodes.STUDENT);

        IntStream.range(0, ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE).forEach(index -> {
            try {
                assertThat(ask(student, new AskChatbotRequest("câu " + index, List.of())).getStatus())
                        .as("câu hỏi thứ " + (index + 1) + " phải được phép")
                        .isEqualTo(200);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });

        var blocked = ask(student, new AskChatbotRequest("câu vượt hạn mức", List.of()));

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("CHAT_TOO_MANY_MESSAGES");
    }

    /** Review Focus #6 end-to-end: trần độ dài là của BACKEND - một client gọi thẳng API vẫn bị chặn,
     * với đúng errorCode của bảng lỗi (spec mục 5), không phải VALIDATION_FAILED. */
    @Test
    void rejectsATooLongMessageWithAProblemDetail() throws Exception {
        var student = signIn("chat-hv-long", "Học viên", AccessConstants.RoleCodes.STUDENT);
        var tooLong = "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH + 1);

        var response = ask(student, new AskChatbotRequest(tooLong, List.of()));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("CHAT_MESSAGE_TOO_LONG");
        verifyNoInteractions(aiChatClient);
    }

    /** Review Focus #10: câu hỏi rỗng cũng bị chặn, cùng errorCode. */
    @Test
    void rejectsABlankMessage() throws Exception {
        var student = signIn("chat-hv-blank", "Học viên", AccessConstants.RoleCodes.STUDENT);

        var response = ask(student, new AskChatbotRequest("   ", List.of()));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("CHAT_MESSAGE_TOO_LONG");
    }

    /**
     * Review Focus #5 end-to-end, và là bài học trực tiếp từ Critical #1 của Phân hệ 4: nhà cung cấp
     * hỏng (ở production hiện tại nghĩa là "chưa cấu hình ANTHROPIC_API_KEY" → 401) phải ra 503 CÓ
     * errorCode, không phải 500 trống.
     */
    @Test
    void reportsAProviderFailureAsServiceUnavailableWithAnErrorCode() throws Exception {
        when(aiChatClient.reply(anyString(), any()))
                .thenThrow(new AiProviderException("401 authentication_error", null));
        var student = signIn("chat-hv-503", "Học viên", AccessConstants.RoleCodes.STUDENT);

        var response = ask(student, new AskChatbotRequest("có khóa nào không", List.of()));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("CHAT_PROVIDER_UNAVAILABLE");
    }

    /** Một lượt lịch sử dài quá mức vẫn bị Bean Validation chặn ở biên (Review Focus #7, lớp thứ hai
     * bên cạnh việc cắt bớt số lượt). */
    @Test
    void rejectsAHistoryTurnLongerThanAMessageCouldEverBe() throws Exception {
        var student = signIn("chat-hv-hist", "Học viên", AccessConstants.RoleCodes.STUDENT);
        var oversizedTurn = new ChatTurnPayload(ChatConstants.TurnRole.USER,
                "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH + 1));

        var response = ask(student, new AskChatbotRequest("câu hỏi mới", List.of(oversizedTurn)));

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(aiChatClient);
    }

    /** Chưa đăng nhập thì không vào được - endpoint này tốn tiền thật mỗi lượt gọi. */
    @Test
    void refusesAnAnonymousCaller() throws Exception {
        var response = mockMvc.perform(post(CHAT_ENDPOINT).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AskChatbotRequest("có khóa nào không", List.of()))))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isIn(401, 403);
        verifyNoInteractions(aiChatClient);
    }

    /** Spec mục 2: backend KHÔNG lưu gì - response chỉ có đúng một field `reply`, không có id hội
     * thoại, không có dấu vết nào gợi ý rằng có thứ gì được lưu lại. */
    @Test
    void returnsNothingButTheReply() throws Exception {
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var student = signIn("chat-hv-shape", "Học viên", AccessConstants.RoleCodes.STUDENT);

        var body = objectMapper.readTree(
                ask(student, new AskChatbotRequest("có khóa nào không", List.of())).getContentAsString());

        assertThat(body.fieldNames()).toIterable().containsExactly("reply");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=ChatAdminControllerIT`
Expected: FAIL — mọi test gọi `/api/chat/messages` trả 404 vì chưa có controller nào ánh xạ đường dẫn đó.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/eduerp/modules/chat/web/ChatAdminController.java`:

```java
package com.eduerp.modules.chat.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.chat.dto.AskChatbotRequest;
import com.eduerp.modules.chat.dto.AskChatbotResponse;
import com.eduerp.modules.chat.usecase.AskChatbot;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint DUY NHẤT của Phân hệ 5.
 *
 * <p>Tên class giữ hậu tố {@code AdminController} theo đúng tên spec mục 3.2 đặt, để truy vết
 * plan↔spec không đứt - nhưng người dùng chính ở đây là HỌC VIÊN, không phải admin. Khác mọi
 * controller khác trong dự án, {@code @PreAuthorize} ở đây KHÔNG đòi scope {@code ORGANIZATION}:
 * học viên chỉ được cấp {@code CHAT:CREATE:PERSONAL} (V27), và ở đây không có khái niệm sở hữu tài
 * nguyên nào cần kiểm thêm, nên cũng không có guard nào trong usecase như
 * {@code ClassGradeAccessGuard} của Phân hệ 4 - xem javadoc
 * {@code AccessConstants.AccessRules.CREATE_CHAT}.
 *
 * <p>POST (không GET) dù đây là một thao tác đọc: câu hỏi và cả lịch sử hội thoại nằm trong body
 * (quá dài cho query string), và một câu hỏi của học viên không được phép lọt vào access log của
 * mọi proxy trên đường đi.
 */
@RestController
@RequestMapping("/api/chat")
class ChatAdminController {

    private final AskChatbot askChatbot;

    ChatAdminController(AskChatbot askChatbot) {
        this.askChatbot = askChatbot;
    }

    @PostMapping("/messages")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_CHAT)
    AskChatbotResponse ask(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AskChatbotRequest request) {
        return askChatbot.execute(principal.accountId(), request);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=ChatAdminControllerIT`
Expected: PASS — 12 tests.

- [ ] **Step 5: Verify module boundaries one more time**

Run: `cd backend && ./mvnw test -Dtest=ModularityTests`
Expected: PASS — `verifiesModularStructure` xanh nghĩa là `modules.chat` chỉ chạm base package/
`@NamedInterface` của `access`, `courses`, `integrations.ai`, `integrations.cache`, `shared`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/chat/web/ChatAdminController.java \
        backend/src/test/java/com/eduerp/modules/chat/web/ChatAdminControllerIT.java
git commit -m "feat(chat): expose POST /api/chat/messages guarded by the CHAT:CREATE rule"
```

---

## Task 13: Hằng số frontend — resource `CHAT`, API route, app route

**Files:**
- Modify: `frontend/src/shared/constants/permissions.ts`
- Modify: `frontend/src/shared/constants/api-routes.ts`
- Modify: `frontend/src/shared/constants/app-routes.ts`

**Interfaces:**
- Consumes: `RESOURCE`, `ACTION`, `RESOURCE_LABEL`, `ACCESS_RULE` (đã có), `API_ROUTE`, `APP_ROUTE`.
- Produces: `RESOURCE.chat = "CHAT"`, `RESOURCE_LABEL[RESOURCE.chat]`, `ACCESS_RULE.createChat` (KHÔNG có `scope`), `API_ROUTE.chat.messages = "/api/chat/messages"`, `APP_ROUTE.chat = "/chat"`.

**Ghi chú plan:** `ACCESS_RULE.createChat` **không ghi `scope`**, mirror `readGrade`/`updateGrade`. `PermissionSet.allows` mặc định `scope = PERSONAL`, nên câu hỏi trở thành "có quyền `CHAT:CREATE` ở bất kỳ scope nào không" — đúng câu mà `@PreAuthorize(CREATE_CHAT)` hỏi ở backend (Task 1). Ghi `organization` ở đây sẽ ẩn cả trang chat và mục nav khỏi học viên, tức khỏi người dùng duy nhất mà tính năng này được xây cho.
Route là `/chat`, KHÔNG `/admin/chat`: mọi route `/admin/*` hiện có đều là màn hình quản trị, và một học viên bị đẩy vào một URL bắt đầu bằng `/admin` là một tín hiệu sai.

- [ ] **Step 1: Add the resource and the access rule**

Trong `frontend/src/shared/constants/permissions.ts`:

Thêm vào `RESOURCE`, sau `grade: "GRADE",`:

```ts
  chat: "CHAT",
```

Thêm vào `RESOURCE_LABEL`, sau dòng của `grade`:

```ts
  [RESOURCE.chat]: "Hỏi đáp khóa học",
```

Thêm vào `ACCESS_RULE`, ngay sau `updateGrade`:

```ts
  /**
   * Entry thứ BA trong file không ghi scope, và đó là chủ đích - cùng lý do với hai rule `*Grade`
   * bên trên: `AccessConstants.AccessRules.CREATE_CHAT` ở backend không mang marker
   * `@ORGANIZATION`, nên câu hỏi đúng là "có quyền CHAT:CREATE ở bất kỳ scope nào không". Học viên
   * được seed `CHAT:CREATE:PERSONAL` (V27); ghi `organization` ở đây sẽ ẩn cả trang chat và mục nav
   * khỏi học viên - người dùng duy nhất mà tính năng này được xây cho.
   *
   * Khác hai rule `*Grade`: ở đây KHÔNG có câu hỏi "tài nguyên này có phải của tôi không" nào mà
   * backend phải trả lời tiếp, nên rule này là toàn bộ sự thật về quyền của màn hình chat.
   */
  createChat: { resource: RESOURCE.chat, action: ACTION.create },
```

- [ ] **Step 2: Add the API route**

Trong `frontend/src/shared/constants/api-routes.ts`, thêm vào `API_ROUTE` sau khối `academic`:

```ts
  chat: {
    /** POST một câu hỏi + lịch sử hội thoại của phiên trình duyệt, nhận lại một câu trả lời đầy đủ.
     * Không có endpoint GET nào: backend không lưu hội thoại nên không có gì để đọc lại. */
    messages: "/api/chat/messages",
  },
```

- [ ] **Step 3: Add the app route**

Trong `frontend/src/shared/constants/app-routes.ts`, thêm vào `APP_ROUTE` sau `classProgress`:

```ts
  /** Không nằm dưới /admin: đây là màn hình của HỌC VIÊN, không phải màn hình quản trị. */
  chat: "/chat",
```

- [ ] **Step 4: Verify the constants typecheck and lint**

Run: `cd frontend && npm run lint && npm run typecheck`
Expected: cả hai PASS, 0 errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/shared/constants/permissions.ts \
        frontend/src/shared/constants/api-routes.ts \
        frontend/src/shared/constants/app-routes.ts
git commit -m "feat(frontend): add the chat resource, access rule and routes to the shared constants"
```

---

## Task 14: `entities/chat` — schema + fetcher (không có hook useQuery nào)

**Files:**
- Create: `frontend/src/entities/chat/model/chat-schema.ts`
- Create: `frontend/src/entities/chat/api/chat-api.ts`
- Create: `frontend/src/entities/chat/index.ts`

**Interfaces:**
- Consumes: `apiClient.post` (`@/shared/api/api-client`), `API_ROUTE.chat.messages` (Task 13), `zod`.
- Produces: `CHAT_TURN_ROLE` (`{ user: "USER", assistant: "ASSISTANT" }`), type `ChatTurnRole`, `chatTurnSchema`, type `ChatTurn` (`{ role: ChatTurnRole; content: string }`), `askChatbotResponseSchema`, type `AskChatbotResponse` (`{ reply: string }`), interface `AskChatbotPayload` (`{ readonly message: string; readonly history: readonly ChatTurn[] }`), `CHAT_MESSAGE_MAX_LENGTH = 2000`, `chatApi.ask(payload: AskChatbotPayload): Promise<AskChatbotResponse>`.

**Ghi chú plan:** spec mục 3.5 nói rõ KHÔNG cần query key hay hook `useQuery` ở tầng entity — "mỗi câu hỏi là một mutation, không phải một query có thể cache". Vì vậy **không tạo** `chat-keys.ts` và **không tạo** `api/use-*.ts` ở đây: một file query-key không ai dùng là một file người đọc sau sẽ tưởng là nên dùng. Mutation hook nằm ở `modules/chat` (Task 15), đúng tầng của nó.

- [ ] **Step 1: Write the schema**

Create `frontend/src/entities/chat/model/chat-schema.ts`:

```ts
import { z } from "zod";

/** Bản sao của `ChatConstants.TurnRole` ở backend. Hai giá trị này đi vào JSON nên chúng là hợp đồng,
 * không phải một chi tiết hiển thị. */
export const CHAT_TURN_ROLE = {
  user: "USER",
  assistant: "ASSISTANT",
} as const;

export type ChatTurnRole = (typeof CHAT_TURN_ROLE)[keyof typeof CHAT_TURN_ROLE];

export const CHAT_TURN_ROLE_LABEL: Record<ChatTurnRole, string> = {
  [CHAT_TURN_ROLE.user]: "Bạn",
  [CHAT_TURN_ROLE.assistant]: "Trợ lý tư vấn",
};

/** Khớp `ChatConstants.Limits.MAX_MESSAGE_LENGTH`. Backend kiểm lại bằng CHAT_MESSAGE_TOO_LONG -
 * con số ở đây chỉ để người gõ thấy trần trước khi gửi, không phải lớp bảo vệ. */
export const CHAT_MESSAGE_MAX_LENGTH = 2000;

/**
 * Một lượt hội thoại. Khớp `chat.dto.ChatTurnPayload`.
 *
 * KHÔNG có `id`, không có `createdAt`: backend không lưu hội thoại (spec mục 2), nên không có gì cấp
 * cho một lượt một danh tính bền. React dùng chỉ số trong mảng làm key - hợp lệ ở đây vì danh sách
 * chỉ-thêm-vào-cuối, không bao giờ chèn giữa hay sắp lại.
 */
export const chatTurnSchema = z.object({
  role: z.enum([CHAT_TURN_ROLE.user, CHAT_TURN_ROLE.assistant]),
  content: z.string(),
});

export type ChatTurn = z.infer<typeof chatTurnSchema>;

/** Khớp `chat.dto.AskChatbotResponse` - đúng một field, và đó là toàn bộ hợp đồng ra. */
export const askChatbotResponseSchema = z.object({
  reply: z.string(),
});

export type AskChatbotResponse = z.infer<typeof askChatbotResponseSchema>;

/** Khớp `chat.dto.AskChatbotRequest`. `history` là TOÀN BỘ lịch sử của phiên trình duyệt: backend
 * không lưu gì nên mỗi request phải mang theo ngữ cảnh của chính nó (spec mục 4). */
export interface AskChatbotPayload {
  readonly message: string;
  readonly history: readonly ChatTurn[];
}
```

- [ ] **Step 2: Write the fetcher**

Create `frontend/src/entities/chat/api/chat-api.ts`:

```ts
import {
  askChatbotResponseSchema,
  type AskChatbotPayload,
  type AskChatbotResponse,
} from "@/entities/chat/model/chat-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";

export const chatApi = {
  /** Một lệnh gọi, một câu trả lời đầy đủ - không streaming (spec Ruling R4). */
  async ask(payload: AskChatbotPayload): Promise<AskChatbotResponse> {
    return askChatbotResponseSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.chat.messages, payload),
    );
  },
} as const;
```

- [ ] **Step 3: Write the barrel**

Create `frontend/src/entities/chat/index.ts`:

```ts
export { chatApi } from "@/entities/chat/api/chat-api";
export {
  askChatbotResponseSchema,
  chatTurnSchema,
  CHAT_MESSAGE_MAX_LENGTH,
  CHAT_TURN_ROLE,
  CHAT_TURN_ROLE_LABEL,
  type AskChatbotPayload,
  type AskChatbotResponse,
  type ChatTurn,
  type ChatTurnRole,
} from "@/entities/chat/model/chat-schema";
```

- [ ] **Step 4: Verify the entity typechecks, lints and builds**

Run: `cd frontend && npm run lint && npm run typecheck && npm run build`
Expected: cả ba PASS, 0 errors. `boundaries/dependencies` xác nhận `entities/chat` chỉ import
`entities/chat` + `shared`, không import entity nào khác.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/entities/chat/
git commit -m "feat(frontend): add the chat entity with its schema and ask fetcher"
```

---

## Task 15: `modules/chat` — controller hook + hai component render thuần + trang

**Files:**
- Create: `frontend/src/modules/chat/hooks/use-chat-controller.ts`
- Create: `frontend/src/modules/chat/ui/chat-message-list.tsx`
- Create: `frontend/src/modules/chat/ui/chat-input.tsx`
- Create: `frontend/src/modules/chat/pages/chat-page.tsx`
- Create: `frontend/src/modules/chat/index.ts`

**Interfaces:**
- Consumes: `chatApi.ask`, `CHAT_TURN_ROLE`, `CHAT_TURN_ROLE_LABEL`, `CHAT_MESSAGE_MAX_LENGTH`, type `ChatTurn`, type `AskChatbotPayload` (Task 14); `RequirePermission` (`@/entities/permission`); `ACCESS_RULE.createChat` (Task 13); `PageHeader`, `GlassPanel`, `GlassButton`, `ErrorNotice`, `EmptyState`, `Spinner` (`@/shared/ui/*`); `useMutation` (TanStack Query v5).
- Produces: `useChatController()` → `{ turns: readonly ChatTurn[]; onSend: (message: string) => void; isPending: boolean; error: unknown }`; `ChatMessageList({ turns, isPending })`; `ChatInput({ onSend, disabled })`; `ChatPage()`; barrel export `ChatPage`.

- [ ] **Step 1: Write the controller hook**

Create `frontend/src/modules/chat/hooks/use-chat-controller.ts`:

```ts
import { chatApi, CHAT_TURN_ROLE, type AskChatbotPayload, type ChatTurn } from "@/entities/chat";
import { useMutation } from "@tanstack/react-query";
import { useCallback, useState } from "react";

/**
 * Toàn bộ state/mutation của trang Hỏi đáp khóa học - `ui/*` chỉ render (Mandate #2).
 *
 * Lịch sử hội thoại sống TRONG React state và chỉ ở đó: không localStorage, không TanStack Query
 * cache, không gửi lên server để lưu (spec mục 2 - "tải lại trang là mất lịch sử"). Đó là một quyết
 * định về dữ liệu nhạy cảm của học viên, không phải một thiếu sót cần "cải thiện" sau này bằng một
 * lần persist nhanh.
 *
 * Không `useQuery`/queryKey nào: mỗi câu hỏi là một mutation, và cache lại câu trả lời theo câu hỏi
 * sẽ khiến cùng một câu hỏi trả về câu trả lời cũ trong khi danh mục khóa học đã đổi.
 */
export function useChatController() {
  const [turns, setTurns] = useState<readonly ChatTurn[]>([]);
  const ask = useMutation({
    mutationFn: (payload: AskChatbotPayload) => chatApi.ask(payload),
  });

  const onSend = useCallback(
    (message: string) => {
      const asked = message.trim();
      if (asked.length === 0) {
        return;
      }
      // `history` được chốt TRƯỚC khi thêm lượt mới: backend nối câu hỏi mới vào cuối lịch sử, nên
      // gửi kèm cả lượt vừa thêm sẽ làm câu hỏi xuất hiện hai lần trong prompt.
      const history = turns;
      setTurns([...history, { role: CHAT_TURN_ROLE.user, content: asked }]);
      ask.mutate(
        { message: asked, history },
        {
          onSuccess: (response) => {
            setTurns((previous) => [
              ...previous,
              { role: CHAT_TURN_ROLE.assistant, content: response.reply },
            ]);
          },
        },
      );
    },
    [turns, ask],
  );

  // Khi lỗi, câu hỏi của người dùng được GIỮ LẠI trong danh sách và lỗi hiện ra bên dưới: xoá câu
  // vừa gõ đi là bắt họ gõ lại, và với 429/503 thì việc cần làm chỉ là thử lại sau một lát.
  return { turns, onSend, isPending: ask.isPending, error: ask.error };
}
```

- [ ] **Step 2: Write the message list**

Create `frontend/src/modules/chat/ui/chat-message-list.tsx`:

```tsx
import { CHAT_TURN_ROLE, CHAT_TURN_ROLE_LABEL, type ChatTurn } from "@/entities/chat";
import { cx } from "@/shared/lib/class-names";
import { Spinner } from "@/shared/ui/spinner";

export interface ChatMessageListProps {
  readonly turns: readonly ChatTurn[];
  readonly isPending: boolean;
}

/** Render thuần một lượt. Tách ra để component danh sách không rẽ nhánh trong JSX (Mandate #3). */
function ChatBubble({ turn }: { readonly turn: ChatTurn }) {
  const isUser = turn.role === CHAT_TURN_ROLE.user;

  return (
    <li className={cx("flex flex-col gap-1", isUser ? "items-end" : "items-start")}>
      <span className="px-1 text-[10px] font-bold tracking-widest text-slate-400 uppercase">
        {CHAT_TURN_ROLE_LABEL[turn.role]}
      </span>
      <div
        className={cx(
          "max-w-[85%] rounded-2xl border px-4 py-2.5 text-sm whitespace-pre-wrap break-words",
          isUser
            ? "border-orange-200/90 bg-orange-50/90 text-slate-800"
            : "glass border-white/80 text-slate-700",
        )}
      >
        {turn.content}
      </div>
    </li>
  );
}

/** Ruling R4: không gõ-từng-chữ. Trạng thái "đang trả lời" là một bong bóng chờ, không phải một hiệu
 * ứng streaming. */
function PendingBubble() {
  return (
    <li className="flex flex-col items-start gap-1">
      <span className="px-1 text-[10px] font-bold tracking-widest text-slate-400 uppercase">
        {CHAT_TURN_ROLE_LABEL[CHAT_TURN_ROLE.assistant]}
      </span>
      <div className="glass flex items-center gap-2 rounded-2xl border border-white/80 px-4 py-2.5">
        <Spinner />
        <span className="text-sm text-slate-500">Đang tra danh mục khóa học…</span>
      </div>
    </li>
  );
}

export function ChatMessageList({ turns, isPending }: ChatMessageListProps) {
  return (
    <ul
      aria-live="polite"
      aria-label="Nội dung hội thoại"
      className="flex max-h-[55vh] flex-col gap-4 overflow-y-auto px-1"
    >
      {turns.map((turn, index) => (
        // Chỉ số làm key là hợp lệ ở đây: danh sách chỉ được THÊM VÀO CUỐI, không bao giờ chèn giữa,
        // xoá hay sắp lại - và một lượt không có id vì backend không lưu gì (spec mục 2).
        <ChatBubble key={`${turn.role}-${index}`} turn={turn} />
      ))}
      {isPending ? <PendingBubble /> : null}
    </ul>
  );
}
```

- [ ] **Step 3: Write the input**

Create `frontend/src/modules/chat/ui/chat-input.tsx`:

```tsx
import { CHAT_MESSAGE_MAX_LENGTH } from "@/entities/chat";
import { GlassButton } from "@/shared/ui/glass-button";
import { SendHorizontal } from "lucide-react";
import { useCallback, useState, type FormEvent, type KeyboardEvent } from "react";

export interface ChatInputProps {
  readonly onSend: (message: string) => void;
  readonly disabled: boolean;
}

/**
 * Ô nhập câu hỏi. State của chính ô nhập (chuỗi đang gõ, chưa gửi) nằm ở đây chứ không ở
 * `useChatController`: nó không phải state của hội thoại, và để nó lên trên sẽ khiến mọi ký tự gõ vào
 * render lại cả danh sách tin nhắn.
 */
export function ChatInput({ onSend, disabled }: ChatInputProps) {
  const [draft, setDraft] = useState("");

  const submit = useCallback(() => {
    if (draft.trim().length === 0) {
      return;
    }
    onSend(draft);
    setDraft("");
  }, [draft, onSend]);

  const onSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      submit();
    },
    [submit],
  );

  // Enter gửi, Shift+Enter xuống dòng - đúng quy ước mọi người đã quen ở một ô chat.
  const onKeyDown = useCallback(
    (event: KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key !== "Enter" || event.shiftKey) {
        return;
      }
      event.preventDefault();
      submit();
    },
    [submit],
  );

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-2" noValidate>
      <label htmlFor="chat-message" className="sr-only">
        Câu hỏi về khóa học
      </label>
      <textarea
        id="chat-message"
        rows={3}
        maxLength={CHAT_MESSAGE_MAX_LENGTH}
        value={draft}
        disabled={disabled}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={onKeyDown}
        placeholder="Ví dụ: có khóa tiếng Anh giao tiếp buổi tối không?"
        className="w-full resize-none rounded-2xl border border-slate-200/90 bg-white/75 px-4 py-3 text-sm text-slate-800 placeholder:text-slate-400 backdrop-blur-md transition-all duration-200 hover:border-orange-300/60 focus:border-orange-400 focus:bg-white focus:ring-2 focus:ring-orange-300/30 focus:outline-none disabled:opacity-60"
      />
      <div className="flex items-center justify-between gap-3">
        <span className="text-xs text-slate-400">
          {draft.length}/{CHAT_MESSAGE_MAX_LENGTH} ký tự · Enter để gửi, Shift+Enter để xuống dòng
        </span>
        <GlassButton
          type="submit"
          size="sm"
          loading={disabled}
          disabled={disabled || draft.trim().length === 0}
          icon={<SendHorizontal size={14} aria-hidden />}
        >
          Gửi câu hỏi
        </GlassButton>
      </div>
    </form>
  );
}
```

- [ ] **Step 4: Write the page**

Create `frontend/src/modules/chat/pages/chat-page.tsx`:

```tsx
import { RequirePermission } from "@/entities/permission";
import { useChatController } from "@/modules/chat/hooks/use-chat-controller";
import { ChatInput } from "@/modules/chat/ui/chat-input";
import { ChatMessageList } from "@/modules/chat/ui/chat-message-list";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { MessagesSquare } from "lucide-react";

export function ChatPage() {
  const controller = useChatController();

  return (
    <RequirePermission {...ACCESS_RULE.createChat}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Hỏi đáp khóa học"
          description="Đặt câu hỏi về các khóa học đang mở của trung tâm. Trợ lý chỉ trả lời dựa trên danh mục khóa học thật, và hội thoại không được lưu lại — tải lại trang là bắt đầu lại."
        />

        {/* Nguồn sự thật về lỗi là chính ProblemDetail của backend: 429 "chờ một lát", 503 "tạm thời
            không trả lời được", 400 "câu hỏi quá dài" - giao diện không tự diễn giải lại. */}
        {controller.error ? <ErrorNotice error={controller.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <ChatBody controller={controller} />
          <ChatInput onSend={controller.onSend} disabled={controller.isPending} />
        </GlassPanel>
      </div>
    </RequirePermission>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function ChatBody({ controller }: { readonly controller: ReturnType<typeof useChatController> }) {
  if (controller.turns.length === 0 && !controller.isPending) {
    return (
      <EmptyState
        icon={<MessagesSquare size={28} aria-hidden />}
        title="Bắt đầu bằng một câu hỏi"
        description="Thử hỏi về học phí, lịch học buổi tối, hoặc khóa phù hợp với lớp của con bạn."
      />
    );
  }
  return <ChatMessageList turns={controller.turns} isPending={controller.isPending} />;
}
```

- [ ] **Step 5: Write the barrel**

Create `frontend/src/modules/chat/index.ts`:

```ts
export { ChatPage } from "@/modules/chat/pages/chat-page";
```

- [ ] **Step 6: Verify everything lints, typechecks and builds**

Run: `cd frontend && npm run lint && npm run typecheck && npm run build`
Expected: cả ba PASS, 0 errors. Cụ thể: `boundaries/dependencies` xác nhận `modules/chat` chỉ import
`modules/chat` + `entities/*` + `shared`; `complexity` ≤ 15 cho mọi hàm; `max-lines` dưới ngưỡng cho
cả bốn file (file dài nhất là `chat-input.tsx`, khoảng 80 dòng).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/modules/chat/
git commit -m "feat(frontend): add the chat page with an in-memory conversation controller"
```

---

## Task 16: Nối trang chat vào router và thanh điều hướng (Ruling R5)

**Files:**
- Modify: `frontend/src/app/router/app-router.tsx`
- Modify: `frontend/src/app/layouts/nav-items.ts`
- Modify: `frontend/src/app/ui/app-nav.tsx`

**Interfaces:**
- Consumes: `ChatPage` (Task 15), `APP_ROUTE.chat` (Task 13), `ACCESS_RULE.createChat` (Task 13), `NAV_SECTIONS`/`NavItem` (đã có), `NAV_ICON` (đã có, trong `app-nav.tsx`).
- Produces: route `<Route path={APP_ROUTE.chat} element={<ChatPage />} />` trong nhánh `<RequireAuth />`; một `NavItem` mới trong mục "Cá nhân"; một icon cho đường dẫn đó.

**Ghi chú plan:** `NAV_ICON` trong `app-nav.tsx` là một `Record<string, ReactNode>` tra theo đường dẫn — một mục nav không có entry ở đó sẽ render một chỗ trống thay vì icon. Đây là lý do task này sửa ba file chứ không hai.
Mục nav được đặt trong section **"Cá nhân"** (chứ không "Điều hành & Nghiệp vụ"): `AppNav` ẩn cả một section khi không mục nào trong đó hiển thị được, và "Cá nhân" là section duy nhất một học viên vốn đã thấy — đặt chat ở đó cho họ một thanh điều hướng có nghĩa thay vì một section "Điều hành & Nghiệp vụ" chỉ có đúng một mục lạc.

- [ ] **Step 1: Add the lazy import and the route**

Trong `frontend/src/app/router/app-router.tsx`, thêm sau khối `ClassProgressPage`:

```tsx
const ChatPage = lazy(() => import("@/modules/chat").then((module) => ({ default: module.ChatPage })));
```

Thêm vào nhánh `<Route element={<RequireAuth />}>`, ngay sau dòng `classProgress`:

```tsx
          <Route path={APP_ROUTE.chat} element={<ChatPage />} />
```

- [ ] **Step 2: Add the nav entry**

Trong `frontend/src/app/layouts/nav-items.ts`, thay section "Cá nhân":

```ts
  {
    title: "Cá nhân",
    items: [
      /**
       * Ruling R5: khác hai trang sổ điểm/tiến trình của Phân hệ 4 (cố tình KHÔNG có mục nav riêng vì
       * chúng vào từ danh sách lớp), chat KHÔNG có "danh sách" tự nhiên nào để vào từ đó - học viên
       * đăng nhập và cần thấy ngay lối vào.
       *
       * Đặt trong "Cá nhân" chứ không "Điều hành & Nghiệp vụ": AppNav ẩn cả một section khi không mục
       * nào trong đó hiển thị được, và "Cá nhân" là section duy nhất một học viên vốn đã thấy.
       * Requirement `createChat` không ghi scope nên học viên (PERSONAL) và admin (ORGANIZATION) đều
       * thấy, giáo viên không thấy - đúng RBAC mà V27 seed.
       */
      { path: APP_ROUTE.chat, label: "Hỏi đáp khóa học", requirement: ACCESS_RULE.createChat },
      { path: APP_ROUTE.profile, label: "Hồ sơ của tôi" },
    ],
  },
```

- [ ] **Step 3: Add the nav icon**

Trong `frontend/src/app/ui/app-nav.tsx`, thêm `MessagesSquare` vào import từ `lucide-react` (giữ thứ tự alphabet của danh sách hiện có):

```tsx
import { BookOpen, Building2, CalendarDays, GraduationCap, LayoutDashboard, MessagesSquare, ShieldCheck, UserCircle, UserRoundCheck, Users } from "lucide-react";
```

Thêm vào `NAV_ICON`, trước entry của `profile`:

```tsx
  [APP_ROUTE.chat]: <MessagesSquare size={18} aria-hidden />,
```

- [ ] **Step 4: Verify lint, typecheck and build**

Run: `cd frontend && npm run lint && npm run typecheck && npm run build`
Expected: cả ba PASS, 0 errors. Build phải tạo ra một chunk riêng cho `modules/chat` (mỗi trang là một
chunk — người chỉ vào xem hồ sơ không phải tải kèm màn hình chat).

- [ ] **Step 5: Verify the nav entry really is gated and really has an icon**

Run: `cd frontend && grep -n "APP_ROUTE.chat" src/app/layouts/nav-items.ts src/app/ui/app-nav.tsx src/app/router/app-router.tsx`
Expected: ba file, mỗi file đúng một dòng — `nav-items.ts` (mục nav kèm `requirement`),
`app-nav.tsx` (icon), `app-router.tsx` (route). Thiếu dòng ở `app-nav.tsx` nghĩa là mục nav render
một chỗ trống thay cho icon.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/router/app-router.tsx \
        frontend/src/app/layouts/nav-items.ts \
        frontend/src/app/ui/app-nav.tsx
git commit -m "feat(frontend): route and surface the chat page in the sidebar for students and admins"
```

---

## Task 17: Kiểm tra toàn bộ + soát lại phạm vi

**Files:**
- Không tạo/sửa file nào. Nếu một bước nào đỏ, sửa tại chỗ rồi chạy lại cả danh sách từ đầu.

**Interfaces:**
- Consumes: toàn bộ 16 task phía trên.
- Produces: không có type mới.

- [ ] **Step 1: Verify module boundaries first**

Run: `cd backend && ./mvnw test -Dtest=ModularityTests`
Expected: PASS — 2 tests. `everyDomainAndIntegrationPackageIsADetectedModule` phải thấy đúng **21**
module (19 của Phân hệ 4 + `integrations.ai` + `modules.chat`), và `verifiesModularStructure` xanh
nghĩa là: `modules.chat` không chọc vào `internal` của module nào, `integrations.ai` không phụ thuộc
bất kỳ `modules.*` nào, và không module nào chọc vào `chat.internal`.

- [ ] **Step 2: Verify every Review Focus item has a green test**

Run: `cd backend && ./mvnw test -Dtest='ChatPromptRulesTest,ChatConstantsTest,ChatExceptionTest,AiChatClientTest,AiHttpClientConfigTest,ChatRateLimiterTest,AccessConstantsTest'`
Expected: PASS — Review Focus #2, #5, #6, #7, #8, #9, #10 ở tầng thuần/đơn vị, cộng quyết định kỹ
thuật #1 (rule không marker scope).

Run: `cd backend && ./mvnw test -Dtest='AskChatbotTest,CourseRepositoryIT,CoursesManagementIT,ChatRateLimiterIT'`
Expected: PASS — Review Focus #1, #2, #3, #6, #7, #10 ở tầng usecase/repository, và ngưỡng rate limit
trên Redis thật.

Run: `cd backend && ./mvnw test -Dtest='ChatAdminControllerIT,ChatRbacSeedIT'`
Expected: PASS — Review Focus #1, #2, #3, #4, #5, #6, #7 end-to-end qua HTTP với quyền thật, và seed
RBAC không lọt quyền sang `TEACHER`.

- [ ] **Step 3: Verify the two cross-module ripples hit every call site**

Run: `cd backend && grep -rn "RestClientCustomizer" src/main/java`
Expected: ĐÚNG MỘT kết quả, ở `integrations/ai/AiHttpClientConfig.java`. Còn một dòng nào trong
`integrations/payment` nghĩa là customizer toàn cục vẫn còn và read timeout 10 giây vẫn đang cắt mọi
lệnh gọi Anthropic (quyết định kỹ thuật #3).

Run: `cd backend && grep -rn "RestClient.Builder" src/main/java`
Expected: ĐÚNG MỘT kết quả, ở constructor của `MomoPaymentGatewayClient` — và dòng ngay sau nó phải
gọi `PaymentHttpClientConfig.timeoutRequestFactory()`. Một người tiêu thụ thứ hai xuất hiện mà không
tự áp request factory nghĩa là nó đang thừa hưởng timeout 60 giây của đường AI.

Run: `cd backend && grep -rn "searchOpenCourses\|OpenCourseSummary" src/main/java src/test/java`
Expected: mỗi dòng in ra phải thuộc một trong các nhóm sau, và KHÔNG có dòng nào ngoài danh sách:
- `modules/courses/CoursesManagement.java` — nơi định nghĩa record + method (Task 4)
- `modules/courses/package-info.java` — javadoc (Task 4 Step 5)
- `modules/chat/internal/rules/ChatPromptRules.java` — nơi dựng groundingContext (Task 8)
- `modules/chat/usecase/AskChatbot.java` — nơi gọi facade (Task 10)
- `test/.../CoursesManagementIT.java`, `test/.../ChatPromptRulesTest.java`,
  `test/.../AskChatbotTest.java`

- [ ] **Step 4: Run the full backend suite**

Run: `cd backend && ./mvnw clean verify`
Expected: BUILD SUCCESS, 0 failures, 0 errors — gồm cả Flyway chạy V26/V27 trên Postgres thật trong
mọi IT, `ddl-auto: validate` chấp nhận cột generated `search_vector` không được map, và toàn bộ 60+ IT
cũ khởi động được context với Spring AI trên classpath và `ANTHROPIC_API_KEY` rỗng.

- [ ] **Step 5: Run the full frontend suite**

Run: `cd frontend && npm run lint && npm run typecheck && npm run build`
Expected: cả ba PASS, 0 errors.

- [ ] **Step 6: Confirm nothing out of scope was added**

Run: `cd backend && grep -rni "chat_message\|chat_history\|conversation" src/main/resources/db/migration src/main/java`
Expected: KHÔNG có kết quả nào là một bảng/entity/cột. Spec mục 2 + mục 8: "Không lưu nội dung hội
thoại ở backend". Một bảng `chat_messages` xuất hiện ở đây là một cam kết vận hành (lưu trữ, chính
sách xoá dữ liệu trẻ vị thành niên) mà chưa ai yêu cầu.

Run: `cd backend && grep -rn "ChatMemory\|VectorStore\|EmbeddingModel\|pgvector" src/main/java src/main/resources pom.xml`
Expected: KHÔNG có kết quả — Ruling R1 (không embeddings/vector DB) và Global Constraint (không
`ChatMemory` của Spring AI, vì nó CHÍNH LÀ lưu hội thoại ở backend).

Run: `cd backend && grep -rn "stream()" src/main/java/com/eduerp/integrations/ai src/main/java/com/eduerp/modules/chat`
Expected: KHÔNG có kết quả gọi `ChatClient.stream()`/`ChatModel.stream()` — Ruling R4 (không
streaming). `Collectors`/`List.stream()` của Java là chuyện khác, chỉ cần không có lời gọi streaming
nào tới provider.

Run: `cd frontend && grep -rn "EventSource\|localStorage\|sessionStorage" src/modules/chat src/entities/chat`
Expected: KHÔNG có kết quả — Ruling R4 (không SSE) và spec mục 2 (lịch sử chỉ trong React state, "tải
lại trang là mất lịch sử" — persist nó vào storage là lặng lẽ đảo một quyết định về dữ liệu nhạy cảm).

Run: `cd backend && grep -rn "ChatEvents\|Chat" src/main/java/com/eduerp/modules/audit`
Expected: KHÔNG có kết quả — không ghi audit cho tin nhắn chat (xem "Hai điểm plan cố ý KHÔNG làm").

Run: `cd backend && grep -rn "ANTHROPIC_API_KEY\|sk-ant" src/main src/test --include=*.java --include=*.yml --include=*.sql`
Expected: ĐÚNG MỘT kết quả, là dòng `api-key: ${ANTHROPIC_API_KEY:}` trong `application.yml`. Một
chuỗi bắt đầu bằng `sk-ant` ở bất cứ đâu nghĩa là một khoá thật đã bị commit — phải thu hồi khoá đó
ngay, không chỉ xoá dòng.

- [ ] **Step 7: Confirm no test can reach the real provider**

Run: `cd backend && grep -rln "AiChatClient" src/test/java`
Expected: đúng ba file — `AiChatClientTest.java` (dùng constructor nhận một `ChatClient` mock),
`AskChatbotTest.java` (`mock(AiChatClient.class)`), `ChatAdminControllerIT.java`
(`@MockitoBean AiChatClient`). Không file test nào khác được chạm tới class này.

Run: `cd backend && grep -rn "new AiChatClient(" src/test/java`
Expected: chỉ `AiChatClientTest.java`, và chỉ với overload nhận `ChatClient` — một
`new AiChatClient(chatModel)` trong test sẽ dựng một client có đường ra mạng thật.

Đây là bằng chứng **bằng cấu trúc** (không phải bằng hy vọng) rằng đường gọi mạng thật của
`AiChatClient` không bao giờ được chạy trong test suite: bean thật bị thay trong IT duy nhất khởi
động context có controller chat, và hai test còn lại không bao giờ dựng nó với một `ChatModel`.
Việc gọi Anthropic THẬT chỉ xác nhận được bằng tay sau khi người dùng cung cấp
`ANTHROPIC_API_KEY` (spec mục 9.1) — không có cách nào tự động hoá điều đó trong phiên này, và không
được giả vờ là có.

- [ ] **Step 8: Commit anything the verification pass had to fix**

```bash
git status
```

Nếu working tree sạch thì không cần commit. Nếu phải sửa gì ở Step 1-7:

```bash
git add -A
git commit -m "fix: address issues found during full-suite verification"
```

---

## Những việc người dùng cần tự làm khi quay lại máy

Plan này KHÔNG tự làm được bốn việc dưới đây (copy từ spec mục 9), và chúng không nằm trong bất kỳ
task nào:

1. **Đăng ký một API key Anthropic thật** và gán vào biến môi trường `ANTHROPIC_API_KEY` (dev: file
   env cục bộ; prod: secret của môi trường triển khai, mirror cách `VNPAY_HASH_SECRET`/
   `MOMO_SECRET_KEY` đã được cấu hình). **Cho tới khi có khoá, endpoint chat trả 503
   `CHAT_PROVIDER_UNAVAILABLE`** — toàn bộ test suite vẫn xanh, nhưng tính năng chưa dùng được thật.
   Đây là điểm DUY NHẤT trong cả phân hệ cần người dùng tự cung cấp.
2. **Xác nhận ngưỡng rate limit** (10 tin nhắn/phút/tài khoản,
   `ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE`) có hợp lý với ngân sách API thật hay không — đây là
   một con số khởi điểm hợp lý, không phải một con số đã được xác nhận với người dùng (Ruling R3).
3. **Xem lại Ruling R1** (full-text search thay vì vector embeddings) nếu trung tâm có kế hoạch mở
   rộng danh mục khóa học lên hàng trăm khóa, hoặc cần chatbot hiểu diễn đạt rất tự do/đồng nghĩa.
   Điểm nâng cấp nằm gọn trong `CourseRepository.searchActiveByFullText` +
   `CoursesManagement.searchOpenCourses` — hợp đồng gọi không đổi.
4. **Xem lại phần "Ngoài phạm vi"** (spec mục 2) nếu trung tâm muốn xem lại lịch sử hội thoại của học
   viên. Đó là một thay đổi phạm vi có chủ đích, ảnh hưởng tới chính sách lưu trữ dữ liệu học viên
   (có thể là trẻ vị thành niên), không phải một tính năng thêm vào.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-10-09-phan-he-5-ai-chatbot.md`. Please review
the plan. Does it capture what you want?
