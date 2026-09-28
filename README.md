# EduERP

Hệ thống quản lý đào tạo (QLDT) — backend Spring Boot 3.4 / Java 21 (Spring Modulith), frontend
React 19 / Vite.

## Cấu trúc repo

```text
.
├── backend/
│   ├── src/main/java/com/eduerp/
│   │   ├── core/            cơ chế dùng chung (exception, web, config, security) — không nghiệp vụ
│   │   ├── shared/           kiểu dữ liệu dùng chung 3+ module (promotion, không phải nơi để đó)
│   │   ├── modules/          từng domain: identity, access, organization, audit, dashboard
│   │   └── integrations/     adapter ra hệ thống ngoài: cache (Redis), mail (SMTP)
│   ├── src/main/resources/db/migration/   Flyway, chạy tự động lúc app khởi động
│   ├── Dockerfile
│   ├── .env.example          chạy native (mvn spring-boot:run)
│   ├── .env.docker.example   stg, container trên máy này (docker-compose.yml)
│   └── .env.docker.prod.example   prod, server thật (docker-compose.prod.yml)
├── frontend/
│   ├── src/                 app → modules → entities → shared (một chiều)
│   ├── Dockerfile           build tĩnh, phục vụ qua nginx — không cần Node lúc chạy
│   └── nginx.conf
├── docker-compose.yml        stg: container trên máy này, nối ra Postgres/Redis có sẵn của máy
├── docker-compose.prod.yml   prod: tự đủ — Postgres + Redis + MinIO + app + nginx
└── Makefile                  wrapper cho hai file compose ở trên
```

`.agents/skills/` chứa các skill kiến trúc (`springboot-modular-scaffold`,
`nextjs-modular-architecture`) mà `AGENTS.md`/`CLAUDE.md` yêu cầu đọc trước khi sửa code.

## Chạy native (dev hằng ngày)

Cần Postgres + Redis đã có sẵn trên máy (xem `backend/.env.example` cho giá trị mặc định đang dùng).

```bash
cd backend
cp .env.example .env   # điền giá trị thật, đặc biệt MAIL_USERNAME/MAIL_PASSWORD (Gmail App Password)
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run    # :8080
```

```bash
cd frontend
npm install
npm run dev             # :5173, Vite proxy /api sang :8080 nên cùng origin, không cần CORS
```

## Kiểm tra trước khi commit

```bash
cd backend && mvn verify                 # Modulith boundary + 17 unit + 41 integration test (Testcontainers)
cd frontend && npm run lint && npm run typecheck && npm run build
```

## Docker

Ba tầng env file, không tầng nào commit (`.gitignore`), mỗi tầng có bản `.example` được commit:

| ENV | File thật (gitignored) | Dùng bởi | Khi nào |
|---|---|---|---|
| — | `backend/.env` | `mvn spring-boot:run` | chạy trực tiếp trên máy, không qua container |
| `stg` (mặc định) | `backend/.env.docker` | `docker-compose.yml` | container trên chính máy này, nối ra Postgres/Redis có sẵn của máy qua `host.docker.internal` |
| `prod` | `backend/.env.docker.prod` | `docker-compose.prod.yml` | server thật — Postgres/Redis/MinIO là container riêng của chính stack, không phụ thuộc gì có sẵn |

```bash
make compose-up              # build + start stg (docker-compose.yml, ENV mặc định)
make compose-up ENV=prod     # build + start prod (docker-compose.prod.yml)
make compose-down [ENV=prod]
make logs [ENV=prod]
make ps [ENV=prod]
```

Không có bước "migrate" thủ công — Flyway và `SeedDefaultAdmin` tự chạy lúc app khởi động
(`make compose-restart` để build lại và khởi động lại sau khi sửa code).

`docker-compose.prod.yml` dựng sẵn MinIO (S3-compatible) nhưng chưa có tính năng nào trong app gọi
tới nó — hạ tầng chuẩn bị trước, chưa wiring Spring cho tới khi có tính năng thật sự cần lưu file.
