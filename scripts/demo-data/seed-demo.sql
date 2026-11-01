-- Dữ liệu demo cho buổi xem tiến độ: chi nhánh, giáo viên + hợp đồng, học viên, khoá học, lớp ở đủ trạng
-- thái, ghi danh, hoá đơn + thanh toán + combo, điểm danh, điểm, bài tập/mini-test, mini-task + lượt làm,
-- nhật ký đăng nhập. Mọi ngày tháng tính theo "hôm nay" lúc chạy nên chạy lại vào hôm khác vẫn hợp lý.
--
-- Biến psql bắt buộc: mailbox - một hộp thư Gmail thật. Mọi tài khoản demo dùng địa chỉ dạng
--   <tên>+gv01@gmail.com, <tên>+hv001@gmail.com ... Gmail giao các địa chỉ có "+" về CHÍNH hộp thư đó,
--   nên quên mật khẩu / thư mời vẫn nhận được, không cần hàng trăm tài khoản mail thật.
-- Mật khẩu chung của mọi tài khoản demo: Demo@2026 (hash BCrypt bên dưới). last_login luôn có giá trị: hệ
-- thống coi tài khoản chưa đăng nhập lần nào là "đang chờ kích hoạt lời mời" và chặn đăng nhập bằng mật khẩu.
-- Id mọi bản ghi demo bắt đầu bằng "de30" để cleanup-demo.sql xoá sạch. Chạy qua run-seed.sql.

-- ---------------------------------------------------------------------------------------------------
-- Hàm phụ (chỉ sống trong phiên này)
-- ---------------------------------------------------------------------------------------------------
-- Id cố định: de30<loại>-0000-4000-8000-<số thứ tự>. Chạy lại cho đúng các id cũ.
CREATE FUNCTION pg_temp.did(kind int, n bigint) RETURNS uuid LANGUAGE sql IMMUTABLE AS
$$ SELECT ('de30' || lpad(to_hex(kind), 4, '0') || '-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid $$;

-- "Ngẫu nhiên" tất định 0..99 theo một chuỗi - chạy lại ra đúng bộ dữ liệu cũ.
CREATE FUNCTION pg_temp.rnd(seed text) RETURNS int LANGUAGE sql IMMUTABLE AS
$$ SELECT (abs(hashtext(seed)::bigint) % 100)::int $$;

-- Mốc giờ Việt Nam của một ngày.
CREATE FUNCTION pg_temp.at_vn(d date, t time) RETURNS timestamptz LANGUAGE sql IMMUTABLE AS
$$ SELECT (d + t) AT TIME ZONE 'Asia/Ho_Chi_Minh' $$;

CREATE TEMP TABLE sd_ctx ON COMMIT DROP AS
SELECT (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS today,
       split_part(lower(split_part(btrim(:'mailbox'), '@', 1)), '+', 1) AS mail_local,
       lower(split_part(btrim(:'mailbox'), '@', 2)) AS mail_domain,
       (SELECT a.id FROM accounts a
            JOIN account_roles ar ON ar.account_id = a.id
            JOIN roles r ON r.id = ar.role_id
        WHERE r.code = 'ADMIN' AND a.id::text NOT LIKE 'de30%'
        ORDER BY a.email LIMIT 1) AS admin_id,
       '$2a$10$Mv4FzH2s6c.abhKjIv6GBeFsbD9L5gPByTcIaP2j34QzFnlLRcCgC'::text AS pw_hash;

-- Dừng sớm với thông báo rõ nếu thiếu hộp thư hoặc chưa có tài khoản admin.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sd_ctx WHERE mail_local = '' OR mail_domain = '') THEN
        RAISE EXCEPTION 'mailbox không hợp lệ - cần dạng ten@gmail.com';
    END IF;
    IF EXISTS (SELECT 1 FROM sd_ctx WHERE admin_id IS NULL) THEN
        RAISE EXCEPTION 'Chưa có tài khoản ADMIN - hãy khởi động backend một lần để tạo admin mặc định';
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------------------
-- 1. Chi nhánh: các chi nhánh thật đang hoạt động + 1 chi nhánh demo
-- ---------------------------------------------------------------------------------------------------
INSERT INTO branches (id, code, name, address, active)
VALUES (pg_temp.did(1, 1), 'HCM-DEMO', 'Hồ Chí Minh', '268 Lý Thường Kiệt, Phường 14, Quận 10, TP. Hồ Chí Minh', true);

-- ref 1..3 -> chi nhánh; nếu ít chi nhánh thật thì quay vòng.
CREATE TEMP TABLE sd_branch ON COMMIT DROP AS
WITH pool AS (
    SELECT id, row_number() OVER (ORDER BY id::text LIKE 'de30%', code) AS k, count(*) OVER () AS total
    FROM branches WHERE active
)
SELECT ref, (SELECT id FROM pool WHERE k = ((ref - 1) % total) + 1) AS id
FROM generate_series(1, 3) ref, (SELECT max(total) AS total FROM pool) t;

-- ---------------------------------------------------------------------------------------------------
-- 2. Giáo viên (12) + hồ sơ + môn dạy + hợp đồng
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE sd_teacher_src (
    n int, tag text, full_name text, branch_ref int, phone text, bio text, subjects text[],
    contract_type text, base_salary numeric, hourly_rate numeric, months_ago int, probation boolean
) ON COMMIT DROP;
INSERT INTO sd_teacher_src VALUES
 (1,  'gv01', 'Nguyễn Thu Hương',  1, '0912345601', '8 năm dạy tiếng Anh giao tiếp, IELTS 8.0.',            ARRAY['Tiếng Anh giao tiếp','IELTS'], 'OFFICIAL', 15000000, NULL, 30, false),
 (2,  'gv02', 'Trần Minh Quân',    1, '0912345602', 'Thạc sĩ TESOL, chuyên luyện thi IELTS.',                ARRAY['IELTS'],                       'OFFICIAL', 16000000, NULL, 26, false),
 (3,  'gv03', 'Lê Hoàng Nam',      2, '0912345603', 'TOEIC 970, 5 năm luyện thi TOEIC cho sinh viên.',       ARRAY['TOEIC'],                       'OFFICIAL', 12000000, NULL, 20, false),
 (4,  'gv04', 'Phạm Ngọc Ánh',     3, '0912345604', 'Chứng chỉ TKT, dạy tiếng Anh thiếu nhi Cambridge.',     ARRAY['Tiếng Anh thiếu nhi'],         'OFFICIAL', 11000000, NULL, 18, false),
 (5,  'gv05', 'Vũ Đức Thắng',      2, '0912345605', 'Giáo viên Toán THCS, ôn thi vào lớp 10.',               ARRAY['Toán'],                        'OFFICIAL', 10000000, NULL, 1,  true),
 (6,  'gv06', 'Đặng Thị Mai',      1, '0912345606', 'Cử nhân Ngôn ngữ Trung, HSK 6.',                        ARRAY['Tiếng Trung'],                 'OFFICIAL', 11500000, NULL, 14, false),
 (7,  'gv07', 'Bùi Khánh Linh',    3, '0912345607', 'Du học sinh Nhật Bản, JLPT N1.',                        ARRAY['Tiếng Nhật'],                  'OFFICIAL', 11500000, NULL, 1,  true),
 (8,  'gv08', 'Hoàng Gia Bảo',     2, '0912345608', 'Cộng tác viên luyện đề IELTS/TOEIC cuối tuần.',         ARRAY['IELTS','TOEIC'],               'COLLABORATOR', NULL, 350000, 10, false),
 (9,  'gv09', 'Đỗ Phương Thảo',    1, '0912345609', 'Dạy tiếng Anh giao tiếp cho người đi làm.',             ARRAY['Tiếng Anh giao tiếp'],         'COLLABORATOR', NULL, 250000, 8,  false),
 (10, 'gv10', 'Ngô Quang Huy',     1, '0912345610', 'Giáo viên Toán trường THCS, dạy thêm buổi tối.',        ARRAY['Toán'],                        'COLLABORATOR', NULL, 220000, 6,  false),
 (11, 'gv11', 'David Miller',      3, '0912345611', 'Giáo viên bản ngữ (Anh), CELTA, phụ trách Speaking.',   ARRAY['IELTS','Tiếng Anh giao tiếp'], 'COLLABORATOR', NULL, 450000, 12, false),
 (12, 'gv12', 'Sarah Johnson',     3, '0912345612', 'Giáo viên bản ngữ (Mỹ), dạy phát âm và giao tiếp.',     ARRAY['Tiếng Anh giao tiếp'],         'COLLABORATOR', NULL, 400000, 9,  false);

INSERT INTO accounts (id, email, password_hash, full_name, status, home_branch_id, last_login)
SELECT pg_temp.did(2, t.n), c.mail_local || '+' || t.tag || '@' || c.mail_domain, c.pw_hash, t.full_name,
       'ACTIVE', b.id, now() - make_interval(days => 8 + pg_temp.rnd('act' || t.n) % 30)
FROM sd_teacher_src t CROSS JOIN sd_ctx c JOIN sd_branch b ON b.ref = t.branch_ref;

INSERT INTO account_roles (account_id, role_id)
SELECT pg_temp.did(2, t.n), (SELECT id FROM roles WHERE code = 'TEACHER') FROM sd_teacher_src t;

INSERT INTO teacher_profiles (id, account_id, phone, bio, active)
SELECT pg_temp.did(4, t.n), pg_temp.did(2, t.n), t.phone, t.bio, true FROM sd_teacher_src t;

INSERT INTO teacher_profile_subjects (teacher_profile_id, subject)
SELECT pg_temp.did(4, t.n), s FROM sd_teacher_src t, unnest(t.subjects) s;

INSERT INTO employment_contracts (id, account_id, contract_type, status, base_salary, hourly_rate,
                                  probation_start_date, probation_end_date, start_date, end_date, contract_file_key)
SELECT pg_temp.did(20, t.n), pg_temp.did(2, t.n), t.contract_type, 'ACTIVE', t.base_salary, t.hourly_rate,
       CASE WHEN t.probation THEN (c.today - make_interval(months => t.months_ago))::date END,
       CASE WHEN t.probation THEN (c.today - make_interval(months => t.months_ago) + interval '2 months')::date END,
       (date_trunc('month', c.today - make_interval(months => t.months_ago)))::date,
       CASE WHEN t.contract_type = 'COLLABORATOR'
            THEN (date_trunc('month', c.today - make_interval(months => t.months_ago)) + interval '2 years')::date END,
       NULL
FROM sd_teacher_src t CROSS JOIN sd_ctx c;

INSERT INTO contract_allowances (contract_id, name, amount)
SELECT pg_temp.did(20, t.n), a.name, a.amount
FROM sd_teacher_src t
CROSS JOIN (VALUES ('Phụ cấp ăn trưa', 730000), ('Phụ cấp xăng xe', 400000)) a(name, amount)
WHERE t.contract_type = 'OFFICIAL';

-- ref giáo viên của lớp: 1..12 = giáo viên demo; 101, 102 = giáo viên THẬT đang có trong hệ thống (để
-- đăng nhập bằng tài khoản thật cũng thấy lớp có dữ liệu). Thiếu giáo viên thật thì dùng giáo viên demo.
CREATE TEMP TABLE sd_teacher (ref int PRIMARY KEY, account_id uuid) ON COMMIT DROP;
INSERT INTO sd_teacher SELECT n, pg_temp.did(2, n) FROM generate_series(1, 12) n;
INSERT INTO sd_teacher
SELECT 100 + row_number() OVER (ORDER BY a.email), a.id
FROM accounts a JOIN account_roles ar ON ar.account_id = a.id JOIN roles r ON r.id = ar.role_id
WHERE r.code = 'TEACHER' AND a.status = 'ACTIVE' AND a.id::text NOT LIKE 'de30%';
INSERT INTO sd_teacher VALUES (101, pg_temp.did(2, 1)), (102, pg_temp.did(2, 10)) ON CONFLICT (ref) DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- 3. Học viên (150) + hồ sơ
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE sd_student ON COMMIT DROP AS
SELECT n,
       (ARRAY['Nguyễn','Trần','Lê','Phạm','Hoàng','Huỳnh','Phan','Vũ','Võ','Đặng','Bùi','Đỗ','Hồ','Ngô','Dương','Lý'])[(n * 7) % 16 + 1]
       || ' ' || (ARRAY['Văn','Thị','Minh','Ngọc','Thanh','Đức','Thu','Hoàng','Gia','Bảo','Khánh','Quang','Phương','Anh'])[(n * 5) % 14 + 1]
       || ' ' || (ARRAY['An','Bình','Châu','Dũng','Giang','Hà','Hải','Hiếu','Hoa','Hùng','Huy','Khoa','Lan','Linh','Long',
                        'Mai','Minh','Nam','Ngân','Nhi','Phúc','Quân','Quỳnh','Sơn','Tâm','Thảo','Trang','Tú','Vy','Yến'])[(n * 11) % 30 + 1]
           AS full_name,
       'hv' || lpad(n::text, 3, '0') AS tag,
       (n % 3) + 1 AS branch_ref,
       (date '2001-01-01' + ((n * 97) % 3650))::date AS date_of_birth,
       '09' || lpad(((n * 7919) % 100000000)::text, 8, '0') AS phone,
       (ARRAY['Facebook','Website','Người quen giới thiệu','TikTok','Zalo','Tờ rơi','Hội thảo tại trường'])[(n % 7) + 1] AS source_channel
FROM generate_series(1, 150) n;

INSERT INTO accounts (id, email, password_hash, full_name, status, home_branch_id, last_login)
SELECT pg_temp.did(3, s.n), c.mail_local || '+' || s.tag || '@' || c.mail_domain, c.pw_hash, s.full_name,
       CASE WHEN s.n > 147 THEN 'DISABLED' ELSE 'ACTIVE' END, b.id,
       now() - make_interval(days => 8 + pg_temp.rnd('act' || s.n) % 40)
FROM sd_student s CROSS JOIN sd_ctx c JOIN sd_branch b ON b.ref = s.branch_ref;

INSERT INTO account_roles (account_id, role_id)
SELECT pg_temp.did(3, s.n), (SELECT id FROM roles WHERE code = 'STUDENT') FROM sd_student s;

INSERT INTO student_profiles (id, account_id, date_of_birth, phone, source_channel, active)
SELECT pg_temp.did(5, s.n), pg_temp.did(3, s.n), s.date_of_birth, s.phone, s.source_channel, s.n <= 147
FROM sd_student s;

-- ---------------------------------------------------------------------------------------------------
-- 4. Khoá học (8) + cột điểm theo trọng số
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE sd_course (n int, code text, name text, description text, sessions int, fee numeric, subject text)
    ON COMMIT DROP;
INSERT INTO sd_course VALUES
 (1, 'GTCB',     'Tiếng Anh giao tiếp cơ bản',     'Phản xạ nói các tình huống hằng ngày, phát âm chuẩn IPA.',            36, 4500000, 'eng'),
 (2, 'IELTS-F',  'IELTS Foundation 4.0 - 5.0',      'Nền tảng 4 kỹ năng, làm quen dạng đề IELTS.',                        48, 8000000, 'ielts'),
 (3, 'IELTS-I',  'IELTS Intermediate 5.5 - 6.5',    'Chiến lược làm bài và luyện đề, chữa Writing/Speaking 1-1.',          48, 9500000, 'ielts'),
 (4, 'TOEIC-450','Luyện thi TOEIC 450+',            'Ngữ pháp trọng tâm và chiến thuật 7 phần thi Listening & Reading.',  40, 5500000, 'toeic'),
 (5, 'KIDS-ST',  'Tiếng Anh thiếu nhi Starters',    'Giáo trình Cambridge Starters cho học sinh 6-9 tuổi.',              32, 3600000, 'kids'),
 (6, 'HSK1',     'Tiếng Trung HSK 1',               'Pinyin, 150 từ vựng và ngữ pháp HSK 1.',                            30, 4000000, 'hsk'),
 (7, 'JLPT-N5',  'Tiếng Nhật JLPT N5',              'Bảng chữ cái, 800 từ vựng, ngữ pháp N5.',                           40, 4800000, 'jlpt'),
 (8, 'TOAN10',   'Toán ôn thi vào lớp 10',          'Hệ thống kiến thức đại số, hình học lớp 9 và luyện đề.',            30, 3000000, 'math');

INSERT INTO courses (id, code, name, description, standard_session_count, tuition_fee, active)
SELECT pg_temp.did(6, n), code, name, description, sessions, fee, true FROM sd_course;

CREATE TEMP TABLE sd_grade_column ON COMMIT DROP AS
SELECT c.n AS course_n, g.ord, g.name, g.weight, g.max_score, pg_temp.did(7, c.n * 10 + g.ord) AS id
FROM sd_course c
CROSS JOIN LATERAL (
    SELECT * FROM (VALUES (0, 'Listening', 25, 9), (1, 'Reading', 25, 9), (2, 'Writing', 25, 9), (3, 'Speaking', 25, 9))
        v(ord, name, weight, max_score) WHERE c.subject = 'ielts'
    UNION ALL
    SELECT * FROM (VALUES (0, 'Chuyên cần', 10, 10), (1, 'Giữa kỳ', 30, 10), (2, 'Cuối kỳ', 60, 10))
        v(ord, name, weight, max_score) WHERE c.subject <> 'ielts'
) g;

INSERT INTO course_grade_columns (id, course_id, name, weight_percent, max_score, display_order)
SELECT id, pg_temp.did(6, course_n), name, weight, max_score, ord FROM sd_grade_column;

-- ---------------------------------------------------------------------------------------------------
-- 5. Lớp (20) ở đủ trạng thái + lịch học
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE sd_class_src (
    n int, code text, course_n int, branch_ref int, teacher_ref int, status text, start_off int,
    days text[], start_time time, end_time time, max_seats int, min_seats int, enroll_count int, duration int
) ON COMMIT DROP;
INSERT INTO sd_class_src VALUES
 -- Đã kết thúc
 (1,  'GTCB-K01',     1, 1, 1,   'COMPLETED',   -150, ARRAY['MON','WED'], '18:00', '19:30', 18, 8, 14, 84),
 (2,  'IELTSF-K01',   2, 1, 2,   'COMPLETED',   -140, ARRAY['TUE','THU'], '18:00', '20:00', 16, 6, 12, 98),
 (3,  'TOEIC-K01',    4, 2, 3,   'COMPLETED',   -130, ARRAY['SAT','SUN'], '08:00', '10:00', 20, 8, 15, 84),
 (4,  'KIDS-K01',     5, 3, 4,   'COMPLETED',   -120, ARRAY['SAT','SUN'], '09:00', '10:30', 15, 6, 11, 84),
 (5,  'TOAN10-K01',   8, 2, 5,   'COMPLETED',   -110, ARRAY['MON','FRI'], '19:00', '20:30', 20, 8, 16, 77),
 -- Đang học
 (6,  'GTCB-K02',     1, 1, 101, 'IN_PROGRESS',  -42, ARRAY['MON','WED'], '18:00', '19:30', 18, 8, 13, 126),
 (7,  'IELTSF-K02',   2, 1, 2,   'IN_PROGRESS',  -49, ARRAY['TUE','THU'], '18:00', '20:00', 16, 6, 14, 168),
 (8,  'IELTSI-K01',   3, 3, 11,  'IN_PROGRESS',  -35, ARRAY['TUE','FRI'], '19:00', '21:00', 14, 6, 10, 168),
 (9,  'TOEIC-K02',    4, 2, 8,   'IN_PROGRESS',  -28, ARRAY['SAT','SUN'], '08:00', '10:00', 20, 8, 15, 140),
 (10, 'KIDS-K02',     5, 3, 4,   'IN_PROGRESS',  -21, ARRAY['SAT','SUN'], '09:00', '10:30', 15, 6, 12, 112),
 (11, 'HSK1-K01',     6, 1, 6,   'IN_PROGRESS',  -56, ARRAY['TUE','THU'], '19:00', '20:30', 15, 6, 11, 105),
 (12, 'JLPT-K01',     7, 3, 7,   'IN_PROGRESS',  -35, ARRAY['MON','THU'], '18:30', '20:00', 15, 6,  9, 140),
 (13, 'TOAN10-K02',   8, 2, 102, 'IN_PROGRESS',  -14, ARRAY['TUE','SAT'], '19:00', '20:30', 20, 8, 17, 105),
 (14, 'GTCB-K03',     1, 3, 12,  'IN_PROGRESS',  -28, ARRAY['WED','FRI'], '18:00', '19:30', 18, 8, 12, 126),
 (15, 'IELTSF-K03',   2, 2, 1,   'IN_PROGRESS',  -21, ARRAY['MON','WED'], '19:00', '21:00', 16, 6, 13, 168),
 -- Đang tuyển sinh
 (16, 'GTCB-K04',     1, 1, 9,   'ENROLLING',      7, ARRAY['TUE','THU'], '18:00', '19:30', 18, 6,  7, 126),
 (17, 'IELTSI-K02',   3, 1, 2,   'ENROLLING',     14, ARRAY['SAT','SUN'], '14:00', '16:00', 14, 6,  5, 168),
 (18, 'HSK1-K02',     6, 3, 6,   'ENROLLING',     10, ARRAY['MON','WED'], '19:00', '20:30', 15, 6,  4, 105),
 (19, 'TOAN10-K03',   8, 1, 10,  'ENROLLING',     21, ARRAY['SAT','SUN'], '08:00', '09:30', 20, 8,  9, 105),
 -- Đã huỷ (không đủ sĩ số tối thiểu)
 (20, 'JLPT-K02',     7, 2, 7,   'CANCELLED',    -10, ARRAY['TUE','FRI'], '18:30', '20:00', 15, 8,  3, 140);

CREATE TEMP TABLE sd_class ON COMMIT DROP AS
SELECT s.*, pg_temp.did(8, s.n) AS id, c.subject, c.fee, t.account_id AS teacher_id, b.id AS branch_id,
       (x.today + s.start_off)::date AS start_date,
       CASE WHEN s.status = 'CANCELLED' THEN NULL ELSE (x.today + s.start_off + s.duration)::date END AS end_date
FROM sd_class_src s
JOIN sd_course c ON c.n = s.course_n
JOIN sd_teacher t ON t.ref = s.teacher_ref
JOIN sd_branch b ON b.ref = s.branch_ref
CROSS JOIN sd_ctx x;

INSERT INTO classes (id, course_id, code, branch_id, teacher_id, max_seats, status, start_date, end_date,
                     min_seats, started_at, ended_at)
SELECT id, pg_temp.did(6, course_n), code, branch_id, teacher_id, max_seats, status, start_date, end_date, min_seats,
       CASE WHEN status IN ('IN_PROGRESS', 'COMPLETED') THEN pg_temp.at_vn(start_date, '08:00') END,
       CASE WHEN status = 'COMPLETED' THEN pg_temp.at_vn(end_date, '21:00')
            WHEN status = 'CANCELLED' THEN pg_temp.at_vn(start_date - 1, '17:00') END
FROM sd_class;

INSERT INTO class_schedules (id, class_id, day_of_week, start_time, end_time)
SELECT pg_temp.did(9, c.n * 10 + d.k), c.id, d.day, c.start_time, c.end_time
FROM sd_class c CROSS JOIN LATERAL unnest(c.days) WITH ORDINALITY d(day, k);

-- ---------------------------------------------------------------------------------------------------
-- 6. Ghi danh: học viên demo + học viên THẬT (vào 1 lớp đã học xong và 2 lớp đang học)
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE sd_enroll_src ON COMMIT DROP AS
SELECT c.n AS class_n, pg_temp.did(5, ((c.n * 37 + i * 13) % 150) + 1) AS student_profile_id, i AS seq
FROM sd_class c, generate_series(0, c.enroll_count - 1) i
UNION ALL
SELECT cls.class_n, sp.id, 100 + sp.k
FROM (SELECT p.id, row_number() OVER (ORDER BY p.id) AS k
      FROM student_profiles p JOIN accounts a ON a.id = p.account_id
      WHERE p.active AND a.status = 'ACTIVE' AND p.id::text NOT LIKE 'de30%') sp
CROSS JOIN (VALUES (1), (6), (7)) cls(class_n);

CREATE TEMP TABLE sd_enroll ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY e.class_n, e.seq) AS n, e.class_n, e.student_profile_id, e.seq,
       c.id AS class_id, c.course_n, c.branch_id, c.status AS class_status, c.start_date, c.fee, c.teacher_id,
       CASE
           WHEN c.status = 'COMPLETED' THEN 'COMPLETED'
           WHEN c.status = 'CANCELLED' THEN 'WITHDRAWN'
           WHEN c.n IN (7, 9, 11) AND e.seq = c.enroll_count - 1 THEN 'WITHDRAWN'
           ELSE 'ACTIVE'
       END AS status,
       LEAST(c.start_date - 5 - pg_temp.rnd('enr' || e.class_n || '-' || e.seq) % 15,
             x.today - 1 - pg_temp.rnd('enr2' || e.class_n || '-' || e.seq) % 5) AS enrolled_on
FROM sd_enroll_src e JOIN sd_class c ON c.n = e.class_n CROSS JOIN sd_ctx x;

ALTER TABLE sd_enroll ADD COLUMN id uuid, ADD COLUMN withdrawn_on date;
UPDATE sd_enroll SET id = pg_temp.did(10, n),
    withdrawn_on = CASE WHEN status = 'WITHDRAWN' AND class_status = 'CANCELLED' THEN start_date - 1
                        WHEN status = 'WITHDRAWN' THEN start_date + 20 END;

INSERT INTO enrollments (id, student_profile_id, class_id, course_id, branch_id, status, enrolled_at, withdrawn_at,
                         created_by_account_id)
SELECT e.id, e.student_profile_id, e.class_id, pg_temp.did(6, e.course_n), e.branch_id, e.status,
       pg_temp.at_vn(e.enrolled_on, '09:30'),
       CASE WHEN e.withdrawn_on IS NOT NULL THEN pg_temp.at_vn(e.withdrawn_on, '10:00') END,
       x.admin_id
FROM sd_enroll e CROSS JOIN sd_ctx x;

-- ---------------------------------------------------------------------------------------------------
-- 7. Học phí: combo giảm giá, hoá đơn từng kỳ, thanh toán (tiền mặt / chuyển khoản / VNPay, có cả giao dịch lỗi)
-- ---------------------------------------------------------------------------------------------------
-- Combo cho tối đa 3 học viên đang học 2 lớp khác khoá cùng lúc (cần một bậc giảm giá áp dụng cho 2 khoá).
CREATE TEMP TABLE sd_combo_pick ON COMMIT DROP AS
WITH tier AS (
    SELECT discount_percent FROM combo_discount_tiers
    WHERE active AND min_course_count <= 2 ORDER BY min_course_count DESC LIMIT 1
), pairs AS (
    SELECT e.student_profile_id, array_agg(e.n ORDER BY e.n) AS enroll_ns
    FROM sd_enroll e
    WHERE e.status = 'ACTIVE' AND e.class_status = 'IN_PROGRESS' AND e.student_profile_id::text LIKE 'de30%'
    GROUP BY e.student_profile_id
    HAVING count(DISTINCT e.course_n) >= 2
)
SELECT row_number() OVER (ORDER BY p.student_profile_id) AS n, p.student_profile_id,
       p.enroll_ns[1:2] AS enroll_ns, tier.discount_percent
FROM pairs p CROSS JOIN tier
ORDER BY p.student_profile_id LIMIT 3;

CREATE TEMP TABLE sd_combo ON COMMIT DROP AS
SELECT k.n, pg_temp.did(22, k.n) AS id, k.student_profile_id, k.discount_percent,
       min(e.branch_id::text)::uuid AS branch_id, sum(e.fee) AS total_original,
       round(sum(e.fee) * (100 - k.discount_percent) / 100, 0) AS total_discounted,
       min(e.start_date) + 7 AS due_date, max(e.enrolled_on) AS created_on
FROM sd_combo_pick k JOIN sd_enroll e ON e.n = ANY (k.enroll_ns)
GROUP BY k.n, k.student_profile_id, k.discount_percent;

INSERT INTO combos (id, student_profile_id, branch_id, total_original_amount, discount_percent,
                    total_discounted_amount, due_date, created_at, created_by_account_id)
SELECT c.id, c.student_profile_id, c.branch_id, c.total_original, c.discount_percent, c.total_discounted,
       c.due_date, pg_temp.at_vn(c.created_on, '10:00'), x.admin_id
FROM sd_combo c CROSS JOIN sd_ctx x;

INSERT INTO combo_enrollments (id, combo_id, enrollment_id, course_id, original_tuition_fee)
SELECT pg_temp.did(23, row_number() OVER (ORDER BY k.n, e.n)), pg_temp.did(22, k.n), e.id,
       pg_temp.did(6, e.course_n), e.fee
FROM sd_combo_pick k JOIN sd_enroll e ON e.n = ANY (k.enroll_ns);

-- Kế hoạch hoá đơn: mỗi dòng một kỳ. paid_on null = chưa trả.
CREATE TEMP TABLE sd_invoice_plan (
    enroll_n bigint, combo_n bigint, installment int, amount numeric, amount_paid numeric, status text,
    due_date date, issued_on date, paid_on date
) ON COMMIT DROP;

INSERT INTO sd_invoice_plan
SELECT NULL::bigint, c.n, 1, c.total_discounted, c.total_discounted, 'PAID', c.due_date, c.created_on, c.created_on + 2
FROM sd_combo c;

WITH e AS (
    SELECT en.*, pg_temp.rnd('inv' || en.n) AS r, x.today
    FROM sd_enroll en CROSS JOIN sd_ctx x
    WHERE NOT EXISTS (SELECT 1 FROM sd_combo_pick k WHERE en.n = ANY (k.enroll_ns))
)
INSERT INTO sd_invoice_plan
-- Đã học xong: trả đủ.
SELECT n, NULL::bigint, 1, fee, fee, 'PAID', enrolled_on + 7, enrolled_on, enrolled_on + 2 FROM e WHERE status = 'COMPLETED'
UNION ALL
-- Rút khỏi lớp: hoá đơn bị huỷ.
SELECT n, NULL, 1, fee, 0, 'CANCELLED', enrolled_on + 7, enrolled_on, NULL FROM e WHERE status = 'WITHDRAWN'
UNION ALL
-- Đang học - trả đủ một lần.
SELECT n, NULL, 1, fee, fee, 'PAID', enrolled_on + 7, enrolled_on, enrolled_on + 3
FROM e WHERE status = 'ACTIVE' AND class_status = 'IN_PROGRESS' AND r < 50
UNION ALL
-- Đang học - chia 2 kỳ: kỳ 1 đã trả.
SELECT n, NULL, 1, round(fee / 2, -3), round(fee / 2, -3), 'PAID', enrolled_on + 7, enrolled_on, enrolled_on + 2
FROM e WHERE status = 'ACTIVE' AND class_status = 'IN_PROGRESS' AND r >= 50 AND r < 75
UNION ALL
-- ... kỳ 2: quá hạn chưa trả / đã trả / chưa tới hạn.
SELECT n, NULL, 2, fee - round(fee / 2, -3),
       CASE WHEN start_date + 45 < today AND r >= 63 THEN fee - round(fee / 2, -3) ELSE 0 END,
       CASE WHEN start_date + 45 >= today THEN 'UNPAID' WHEN r < 63 THEN 'OVERDUE' ELSE 'PAID' END,
       start_date + 45, start_date + 15,
       CASE WHEN start_date + 45 < today AND r >= 63 THEN start_date + 40 END
FROM e WHERE status = 'ACTIVE' AND class_status = 'IN_PROGRESS' AND r >= 50 AND r < 75
UNION ALL
-- Đang học - mới trả một phần, hạn còn ở phía trước.
SELECT n, NULL, 1, fee, round(fee * 0.4, -3), 'PARTIALLY_PAID', today + 10, enrolled_on, enrolled_on + 3
FROM e WHERE status = 'ACTIVE' AND class_status = 'IN_PROGRESS' AND r >= 75 AND r < 88
UNION ALL
-- Đang học - quá hạn chưa đóng đồng nào.
SELECT n, NULL, 1, fee, 0, 'OVERDUE', start_date + 7, enrolled_on, NULL
FROM e WHERE status = 'ACTIVE' AND class_status = 'IN_PROGRESS' AND r >= 88
UNION ALL
-- Lớp sắp khai giảng: đã đóng sớm hoặc chờ đóng trước ngày khai giảng.
SELECT n, NULL, 1, fee, CASE WHEN r < 55 THEN fee ELSE 0 END, CASE WHEN r < 55 THEN 'PAID' ELSE 'UNPAID' END,
       start_date, enrolled_on, CASE WHEN r < 55 THEN enrolled_on END
FROM e WHERE status = 'ACTIVE' AND class_status = 'ENROLLING';

CREATE TEMP TABLE sd_invoice ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY p.combo_n NULLS LAST, p.enroll_n, p.installment) AS n, p.*
FROM sd_invoice_plan p;

INSERT INTO invoices (id, enrollment_id, student_profile_id, course_id, branch_id, installment_number, amount,
                      amount_paid, status, due_date, issued_at, created_by_account_id, combo_id)
SELECT pg_temp.did(11, i.n),
       e.id,
       COALESCE(e.student_profile_id, c.student_profile_id),
       CASE WHEN e.id IS NOT NULL THEN pg_temp.did(6, e.course_n) END,
       COALESCE(e.branch_id, c.branch_id),
       i.installment, i.amount, i.amount_paid, i.status, i.due_date, pg_temp.at_vn(i.issued_on, '10:00'),
       x.admin_id, c.id
FROM sd_invoice i
LEFT JOIN sd_enroll e ON e.n = i.enroll_n
LEFT JOIN sd_combo c ON c.n = i.combo_n
CROSS JOIN sd_ctx x;

-- Thanh toán thành công cho phần đã trả (tiền mặt / chuyển khoản kèm ghi chú, hoặc VNPay); vài giao
-- dịch VNPay lỗi ở hoá đơn quá hạn cho giống thực tế. Không sinh MoMo: cổng MoMo nằm ngoài đề cương.
INSERT INTO payments (id, invoice_id, amount, method, gateway_transaction_id, status, paid_at, created_at, note)
SELECT pg_temp.did(12, i.n), pg_temp.did(11, i.n), i.amount_paid, m.method,
       CASE WHEN m.method = 'VNPAY' THEN 'DEMO-VNPAY-' || lpad(i.n::text, 6, '0') END,
       'SUCCESS', pg_temp.at_vn(i.paid_on, '15:20'), pg_temp.at_vn(i.paid_on, '15:18'),
       CASE m.method WHEN 'CASH' THEN 'PT-DEMO-' || lpad(i.n::text, 4, '0')
                     WHEN 'BANK_TRANSFER' THEN 'CK VCB DEMO' || lpad(i.n::text, 6, '0') END
FROM sd_invoice i
CROSS JOIN LATERAL (SELECT CASE WHEN pg_temp.rnd('pay' || i.n) < 40 THEN 'CASH'
                                WHEN pg_temp.rnd('pay' || i.n) < 60 THEN 'BANK_TRANSFER' ELSE 'VNPAY' END AS method) m
WHERE i.amount_paid > 0
UNION ALL
SELECT pg_temp.did(12, 100000 + i.n), pg_temp.did(11, i.n), i.amount, 'VNPAY',
       'DEMO-VNPAY-FAIL-' || lpad(i.n::text, 6, '0'), 'FAILED', NULL, pg_temp.at_vn(i.due_date - 1, '20:05'), NULL
FROM sd_invoice i WHERE i.status = 'OVERDUE' AND pg_temp.rnd('fail' || i.n) < 40;

-- ---------------------------------------------------------------------------------------------------
-- 8. Điểm danh: mọi buổi đã qua (tới hôm qua) của lớp đang học / đã kết thúc
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE sd_session ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY c.n, d) AS n, c.n AS class_n, c.id AS class_id, c.teacher_id, d::date AS session_date
FROM sd_class c CROSS JOIN sd_ctx x
CROSS JOIN LATERAL generate_series(c.start_date, LEAST(c.end_date, x.today - 1), interval '1 day') d
WHERE c.status IN ('COMPLETED', 'IN_PROGRESS')
  AND (ARRAY['MON','TUE','WED','THU','FRI','SAT','SUN'])[extract(isodow FROM d)::int] = ANY (c.days);

INSERT INTO attendance_sessions (id, class_id, session_date, note, created_by_account_id, created_at, updated_at)
SELECT pg_temp.did(13, s.n), s.class_id, s.session_date,
       CASE WHEN pg_temp.rnd('note' || s.n) < 10 THEN 'Ôn tập và chữa bài kiểm tra' END,
       s.teacher_id, pg_temp.at_vn(s.session_date, '21:00'), pg_temp.at_vn(s.session_date, '21:00')
FROM sd_session s;

INSERT INTO attendance_records (id, session_id, enrollment_id, status, note)
SELECT pg_temp.did(14, row_number() OVER (ORDER BY s.n, e.n)), pg_temp.did(13, s.n), e.id, st.status,
       CASE st.status WHEN 'LATE' THEN 'Đến muộn 10 phút' WHEN 'EXCUSED' THEN 'Xin phép nghỉ (ốm)' END
FROM sd_session s
JOIN sd_enroll e ON e.class_n = s.class_n AND (e.withdrawn_on IS NULL OR s.session_date < e.withdrawn_on)
CROSS JOIN LATERAL (
    SELECT CASE WHEN r < 84 THEN 'PRESENT' WHEN r < 91 THEN 'LATE' WHEN r < 96 THEN 'ABSENT' ELSE 'EXCUSED' END AS status
    FROM (SELECT pg_temp.rnd('att' || s.n || '-' || e.n) AS r) q
) st;

-- ---------------------------------------------------------------------------------------------------
-- 9. Điểm: lớp đã kết thúc đủ mọi cột; lớp đang học có điểm chuyên cần/kỹ năng đầu và giữa kỳ
-- ---------------------------------------------------------------------------------------------------
INSERT INTO grade_entries (id, enrollment_id, course_grade_column_id, score, entered_by_account_id, entered_at, updated_at)
SELECT pg_temp.did(21, row_number() OVER (ORDER BY e.n, g.ord)), e.id, g.id,
       LEAST(g.max_score, GREATEST(0,
           CASE WHEN g.max_score = 9
                THEN round(((5.5 + (pg_temp.rnd('lv' || e.student_profile_id) % 40) / 10.0
                             + (pg_temp.rnd('g' || e.n || '-' || g.ord) % 11 - 5) / 10.0) * 0.85) * 2) / 2
                ELSE round(5.5 + (pg_temp.rnd('lv' || e.student_profile_id) % 40) / 10.0
                           + (pg_temp.rnd('g' || e.n || '-' || g.ord) % 11 - 5) / 10.0, 1)
           END)),
       e.teacher_id,
       LEAST(pg_temp.at_vn(e.start_date + 30 + g.ord * 20, '21:30'), now() - interval '1 day'),
       LEAST(pg_temp.at_vn(e.start_date + 30 + g.ord * 20, '21:30'), now() - interval '1 day')
FROM sd_enroll e
JOIN sd_grade_column g ON g.course_n = e.course_n
CROSS JOIN sd_ctx x
WHERE (e.status = 'COMPLETED')
   OR (e.status = 'ACTIVE' AND e.class_status = 'IN_PROGRESS'
       AND (g.ord = 0 OR (g.ord = 1 AND e.start_date <= x.today - 35)));

-- ---------------------------------------------------------------------------------------------------
-- 10. Bài tập về nhà & mini-test (theo dõi nộp bài, điểm, nhận xét) - mỗi tuần một bài
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE sd_topic (subject text, k int, topic text) ON COMMIT DROP;
INSERT INTO sd_topic VALUES
 ('eng',0,'Chào hỏi & giới thiệu bản thân'),('eng',1,'Thì hiện tại đơn'),('eng',2,'Mua sắm & giá cả'),
 ('eng',3,'Hỏi và chỉ đường'),('eng',4,'Thì quá khứ đơn'),('eng',5,'Đặt lịch hẹn qua điện thoại'),
 ('ielts',0,'Listening Section 1 - Form completion'),('ielts',1,'Reading - True/False/Not Given'),
 ('ielts',2,'Writing Task 1 - Line graph'),('ielts',3,'Speaking Part 2 - Describe a place'),
 ('ielts',4,'Reading - Matching headings'),('ielts',5,'Writing Task 2 - Opinion essay'),
 ('toeic',0,'Part 1 - Mô tả tranh'),('toeic',1,'Part 2 - Hỏi đáp'),('toeic',2,'Part 5 - Từ loại'),
 ('toeic',3,'Part 3 - Hội thoại'),('toeic',4,'Part 6 - Điền đoạn văn'),('toeic',5,'Part 7 - Đọc hiểu email'),
 ('kids',0,'Colours & Numbers'),('kids',1,'Animals'),('kids',2,'My family'),('kids',3,'Food I like'),
 ('kids',4,'At school'),('kids',5,'Toys'),
 ('hsk',0,'Pinyin & thanh điệu'),('hsk',1,'Chào hỏi 你好'),('hsk',2,'Số đếm & ngày tháng'),
 ('hsk',3,'Gia đình 家'),('hsk',4,'Mua sắm 多少钱'),('hsk',5,'Thời gian 几点'),
 ('jlpt',0,'Bảng chữ Hiragana'),('jlpt',1,'Bảng chữ Katakana'),('jlpt',2,'Ngữ pháp です/ます'),
 ('jlpt',3,'Trợ từ は・が・を'),('jlpt',4,'Kanji số đếm'),('jlpt',5,'Động từ thể て'),
 ('math',0,'Căn bậc hai & biến đổi'),('math',1,'Hệ phương trình bậc nhất hai ẩn'),
 ('math',2,'Phương trình bậc hai & Vi-ét'),('math',3,'Hàm số y = ax²'),('math',4,'Góc với đường tròn'),
 ('math',5,'Giải bài toán bằng cách lập phương trình');

CREATE TEMP TABLE sd_assignment ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY c.n, w.week) AS n, c.n AS class_n, c.id AS class_id, c.teacher_id, w.week AS k,
       CASE WHEN w.week % 3 = 2 THEN 'MINI_TEST' ELSE 'HOMEWORK' END AS type,
       (c.start_date + 6 + 7 * w.week)::date AS due_date, t.topic
FROM sd_class c CROSS JOIN sd_ctx x CROSS JOIN generate_series(0, 5) AS w(week)
JOIN sd_topic t ON t.subject = c.subject AND t.k = w.week
WHERE c.status IN ('COMPLETED', 'IN_PROGRESS')
  AND c.start_date + 6 + 7 * w.week <= LEAST(c.end_date, x.today + 7);

INSERT INTO assignments (id, class_id, title, description, assignment_type, due_date, max_score, active,
                         created_by_account_id, created_at)
SELECT pg_temp.did(15, a.n), a.class_id,
       CASE WHEN a.type = 'MINI_TEST' THEN 'Mini-test: ' ELSE 'Bài tập tuần ' || (a.k + 1) || ': ' END || a.topic,
       CASE WHEN a.type = 'MINI_TEST' THEN 'Kiểm tra 15 phút đầu giờ, chấm trên thang 10.'
            ELSE 'Làm bài trong tài liệu buổi học, nộp ảnh chụp cho giáo viên trước hạn.' END,
       a.type, a.due_date, 10, true, a.teacher_id, pg_temp.at_vn(a.due_date - 6, '20:00')
FROM sd_assignment a;

INSERT INTO assignment_results (id, assignment_id, enrollment_id, status, score, feedback, updated_by_account_id, updated_at)
SELECT pg_temp.did(16, row_number() OVER (ORDER BY a.n, e.n)), pg_temp.did(15, a.n), e.id, q.status,
       CASE WHEN a.type = 'MINI_TEST' OR pg_temp.rnd('sc' || a.n || '-' || e.n) < 85
            THEN LEAST(10, round((5 + (pg_temp.rnd('pt' || a.n || '-' || e.n) % 51) / 10.0) * 2) / 2) END,
       CASE WHEN pg_temp.rnd('fb' || a.n || '-' || e.n) < 25
            THEN (ARRAY['Làm bài cẩn thận, tiếp tục phát huy!','Cần chú ý ngữ pháp và chính tả.',
                        'Bài làm tốt, trình bày rõ ràng.','Còn thiếu ý, xem lại phần chữa trên lớp.',
                        'Tiến bộ rõ so với tuần trước.','Nộp muộn, lần sau chú ý hạn nộp nhé.'])
                 [pg_temp.rnd('fbt' || a.n || '-' || e.n) % 6 + 1] END,
       a.teacher_id, pg_temp.at_vn(a.due_date + 1, '21:00')
FROM sd_assignment a
JOIN sd_enroll e ON e.class_n = a.class_n AND (e.withdrawn_on IS NULL OR a.due_date < e.withdrawn_on)
CROSS JOIN sd_ctx x
CROSS JOIN LATERAL (
    SELECT CASE WHEN a.due_date >= x.today THEN CASE WHEN r < 30 THEN 'SUBMITTED' END
                WHEN r < 70 THEN 'SUBMITTED' WHEN r < 82 THEN 'LATE' END AS status
    FROM (SELECT pg_temp.rnd('sub' || a.n || '-' || e.n) AS r) z
) q
WHERE q.status IS NOT NULL;

-- ---------------------------------------------------------------------------------------------------
-- 11. Mini-task trắc nghiệm (có lời giải, ngưỡng đạt) + lượt làm của học viên
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE sd_bank (subject text, set_no int, title text, pass_percent int, q int, content text,
                           options text[], correct int, explanation text) ON COMMIT DROP;
INSERT INTO sd_bank VALUES
 ('eng',1,'Từ vựng chào hỏi & giới thiệu',80,1,'Câu nào dùng để hỏi tên một người?',ARRAY['What is your name?','How old are you?','Where are you from?','What do you do?'],0,'What is your name? = Bạn tên là gì?'),
 ('eng',1,'Từ vựng chào hỏi & giới thiệu',80,2,'"Nice to meet you" nghĩa là gì?',ARRAY['Hẹn gặp lại','Rất vui được gặp bạn','Cảm ơn bạn','Chúc ngủ ngon'],1,'Câu nói khi gặp ai đó lần đầu.'),
 ('eng',1,'Từ vựng chào hỏi & giới thiệu',80,3,'Chọn từ đúng: I ___ a student.',ARRAY['is','are','am','be'],2,'Chủ ngữ I luôn đi với am.'),
 ('eng',1,'Từ vựng chào hỏi & giới thiệu',80,4,'"Where are you from?" hỏi về điều gì?',ARRAY['Nghề nghiệp','Quê quán / quốc tịch','Tuổi','Sở thích'],1,'from = đến từ đâu.'),
 ('eng',1,'Từ vựng chào hỏi & giới thiệu',80,5,'Đáp lại "How are you?" thế nào cho tự nhiên?',ARRAY['I''m fine, thank you.','I''m 20 years old.','I''m a teacher.','I''m from Hanoi.'],0,'How are you? là câu hỏi thăm sức khoẻ.'),
 ('eng',2,'Ngữ pháp thì hiện tại đơn',60,1,'She ___ to school every day.',ARRAY['go','goes','going','gone'],1,'Chủ ngữ ngôi thứ ba số ít: go thêm -es.'),
 ('eng',2,'Ngữ pháp thì hiện tại đơn',60,2,'They ___ coffee in the morning.',ARRAY['drinks','drinking','drink','drank'],2,'They là số nhiều nên động từ giữ nguyên.'),
 ('eng',2,'Ngữ pháp thì hiện tại đơn',60,3,'Câu phủ định đúng là:',ARRAY['He don''t like tea.','He doesn''t likes tea.','He doesn''t like tea.','He not like tea.'],2,'doesn''t + động từ nguyên mẫu.'),
 ('eng',2,'Ngữ pháp thì hiện tại đơn',60,4,'Dấu hiệu nhận biết thì hiện tại đơn:',ARRAY['yesterday','usually','now','tomorrow'],1,'Trạng từ tần suất: always, usually, often...'),
 ('eng',2,'Ngữ pháp thì hiện tại đơn',60,5,'___ you speak English?',ARRAY['Does','Do','Is','Are'],1,'Câu hỏi với you dùng trợ động từ Do.'),
 ('ielts',1,'Reading: True / False / Not Given',80,1,'Khi thông tin trong bài TRÁI với câu cho sẵn, chọn:',ARRAY['TRUE','FALSE','NOT GIVEN'],1,'FALSE = bài đọc nói ngược lại.'),
 ('ielts',1,'Reading: True / False / Not Given',80,2,'Khi bài đọc không đề cập tới thông tin, chọn:',ARRAY['TRUE','FALSE','NOT GIVEN'],2,'NOT GIVEN = không có căn cứ trong bài.'),
 ('ielts',1,'Reading: True / False / Not Given',80,3,'Từ đồng nghĩa của "significant" là:',ARRAY['minor','considerable','rare','temporary'],1,'significant ≈ considerable (đáng kể).'),
 ('ielts',1,'Reading: True / False / Not Given',80,4,'Câu hỏi T/F/NG xuất hiện theo thứ tự nào trong bài?',ARRAY['Ngẫu nhiên','Theo thứ tự đoạn văn','Ngược thứ tự','Chỉ ở đoạn cuối'],1,'Đáp án T/F/NG đi theo thứ tự của bài đọc.'),
 ('ielts',1,'Reading: True / False / Not Given',80,5,'"The majority of" gần nghĩa nhất với:',ARRAY['a few','most','none of','some of'],1,'majority = phần lớn.'),
 ('ielts',2,'Writing Task 1: từ vựng xu hướng',60,1,'Từ nào mô tả sự tăng mạnh?',ARRAY['plummet','surge','fluctuate','level off'],1,'surge = tăng vọt; plummet = giảm mạnh.'),
 ('ielts',2,'Writing Task 1: từ vựng xu hướng',60,2,'"Remain stable" nghĩa là:',ARRAY['giữ ổn định','dao động','đạt đỉnh','giảm dần'],0,NULL),
 ('ielts',2,'Writing Task 1: từ vựng xu hướng',60,3,'Đoạn overview của Task 1 nên:',ARRAY['Nêu ý kiến cá nhân','Tóm tắt xu hướng chính','Liệt kê mọi số liệu','Kết luận nguyên nhân'],1,'Overview nêu đặc điểm nổi bật, không nêu ý kiến.'),
 ('ielts',2,'Writing Task 1: từ vựng xu hướng',60,4,'There was a ___ increase in sales.',ARRAY['dramatically','dramatic','drama','dramatize'],1,'Trước danh từ increase cần tính từ.'),
 ('ielts',2,'Writing Task 1: từ vựng xu hướng',60,5,'Task 1 yêu cầu tối thiểu bao nhiêu từ?',ARRAY['100','150','200','250'],1,'Task 1: 150 từ; Task 2: 250 từ.'),
 ('toeic',1,'Part 5: từ loại',80,1,'The manager made a ___ decision.',ARRAY['quick','quickly','quickness','quicken'],0,'Trước danh từ dùng tính từ.'),
 ('toeic',1,'Part 5: từ loại',80,2,'Please respond ___ to the email.',ARRAY['prompt','promptly','promptness','prompted'],1,'Bổ nghĩa cho động từ dùng trạng từ.'),
 ('toeic',1,'Part 5: từ loại',80,3,'Our ___ will be held in Room B.',ARRAY['meet','met','meeting','meets'],2,'Sau tính từ sở hữu Our cần danh từ.'),
 ('toeic',1,'Part 5: từ loại',80,4,'The report must be submitted ___ Friday.',ARRAY['by','on','at','in'],0,'by + mốc thời gian = chậm nhất là.'),
 ('toeic',1,'Part 5: từ loại',80,5,'She is ___ for the marketing campaign.',ARRAY['responsible','responsibility','responsibly','response'],0,'be responsible for = chịu trách nhiệm.'),
 ('toeic',2,'Part 2: hỏi đáp',60,1,'"When does the meeting start?" - đáp án phù hợp:',ARRAY['In the conference room.','At 9 a.m.','Mr. Lee will.','Yes, it does.'],1,'Câu hỏi When cần thời gian.'),
 ('toeic',2,'Part 2: hỏi đáp',60,2,'"Who is in charge of the project?"',ARRAY['Next week.','Ms. Tanaka is.','By email.','It''s on the desk.'],1,'Who hỏi về người.'),
 ('toeic',2,'Part 2: hỏi đáp',60,3,'"Why don''t we take a break?" là câu:',ARRAY['Hỏi lý do','Đề nghị / gợi ý','Phủ định','Hỏi nơi chốn'],1,'Why don''t we...? = gợi ý cùng làm gì.'),
 ('toeic',2,'Part 2: hỏi đáp',60,4,'"Where can I find the printer?"',ARRAY['Next to the copier.','Yes, you can.','At noon.','Three pages.'],0,NULL),
 ('toeic',2,'Part 2: hỏi đáp',60,5,'Bẫy thường gặp ở Part 2 là:',ARRAY['Đáp án lặp lại từ trong câu hỏi','Đáp án quá ngắn','Đáp án có số','Đáp án có tên người'],0,'Đáp án lặp âm/từ của câu hỏi thường là bẫy.'),
 ('kids',1,'Colours & Numbers',80,1,'What colour is the sky?',ARRAY['Blue','Red','Green','Black'],0,NULL),
 ('kids',1,'Colours & Numbers',80,2,'How many legs does a cat have?',ARRAY['Two','Three','Four','Six'],2,NULL),
 ('kids',1,'Colours & Numbers',80,3,'"Yellow" là màu gì?',ARRAY['Đỏ','Vàng','Xanh lá','Tím'],1,NULL),
 ('kids',1,'Colours & Numbers',80,4,'Five + three = ?',ARRAY['Seven','Eight','Nine','Ten'],1,'5 + 3 = 8 (eight).'),
 ('kids',1,'Colours & Numbers',80,5,'Which one is a colour?',ARRAY['Pink','Dog','Pen','Ten'],0,'Pink = màu hồng.'),
 ('kids',2,'Animals',60,1,'Con vật nào là "elephant"?',ARRAY['Con voi','Con hổ','Con khỉ','Con gà'],0,NULL),
 ('kids',2,'Animals',60,2,'A ___ says "Meow".',ARRAY['dog','cat','cow','duck'],1,NULL),
 ('kids',2,'Animals',60,3,'Which animal can fly?',ARRAY['Fish','Bird','Horse','Pig'],1,NULL),
 ('kids',2,'Animals',60,4,'"Monkey" nghĩa là:',ARRAY['Con khỉ','Con chuột','Con thỏ','Con rắn'],0,NULL),
 ('kids',2,'Animals',60,5,'Fish live in the ___.',ARRAY['tree','sky','water','house'],2,NULL),
 ('hsk',1,'Pinyin & chào hỏi',80,1,'你好 (nǐ hǎo) nghĩa là:',ARRAY['Xin chào','Cảm ơn','Tạm biệt','Xin lỗi'],0,NULL),
 ('hsk',1,'Pinyin & chào hỏi',80,2,'谢谢 (xièxie) nghĩa là:',ARRAY['Không có gì','Cảm ơn','Xin chào','Vâng'],1,NULL),
 ('hsk',1,'Pinyin & chào hỏi',80,3,'Tiếng Trung phổ thông có mấy thanh điệu chính?',ARRAY['3','4','5','6'],1,'4 thanh điệu chính và thanh nhẹ.'),
 ('hsk',1,'Pinyin & chào hỏi',80,4,'再见 (zàijiàn) nghĩa là:',ARRAY['Hẹn gặp lại','Rất vui','Chào buổi sáng','Đúng rồi'],0,NULL),
 ('hsk',1,'Pinyin & chào hỏi',80,5,'Đáp lại 谢谢 một cách lịch sự:',ARRAY['不客气 (bú kèqi)','你好','再见','对不起'],0,'不客气 = Đừng khách sáo / Không có gì.'),
 ('hsk',2,'Số đếm & thời gian',60,1,'三 (sān) là số mấy?',ARRAY['2','3','4','10'],1,NULL),
 ('hsk',2,'Số đếm & thời gian',60,2,'十 (shí) là số:',ARRAY['1','7','10','100'],2,NULL),
 ('hsk',2,'Số đếm & thời gian',60,3,'几点? (jǐ diǎn) dùng để hỏi:',ARRAY['Mấy giờ','Bao nhiêu tiền','Ở đâu','Là ai'],0,NULL),
 ('hsk',2,'Số đếm & thời gian',60,4,'多少钱? (duōshao qián) nghĩa là:',ARRAY['Bao nhiêu tiền?','Mấy tuổi?','Mấy giờ?','Đi đâu?'],0,NULL),
 ('hsk',2,'Số đếm & thời gian',60,5,'今天 (jīntiān) nghĩa là:',ARRAY['Hôm qua','Hôm nay','Ngày mai','Tuần này'],1,NULL),
 ('jlpt',1,'Hiragana cơ bản',80,1,'Chữ あ đọc là:',ARRAY['a','i','u','o'],0,NULL),
 ('jlpt',1,'Hiragana cơ bản',80,2,'Chữ か đọc là:',ARRAY['sa','ka','ta','na'],1,NULL),
 ('jlpt',1,'Hiragana cơ bản',80,3,'ありがとう nghĩa là:',ARRAY['Xin chào','Cảm ơn','Xin lỗi','Tạm biệt'],1,NULL),
 ('jlpt',1,'Hiragana cơ bản',80,4,'Chữ Hiragana nào đọc là "shi"?',ARRAY['し','ち','つ','さ'],0,NULL),
 ('jlpt',1,'Hiragana cơ bản',80,5,'おはよう dùng khi nào?',ARRAY['Buổi sáng','Buổi trưa','Buổi tối','Trước khi ngủ'],0,'おはようございます = chào buổi sáng.'),
 ('jlpt',2,'Ngữ pháp です/ます & trợ từ',60,1,'わたし ___ がくせいです。',ARRAY['を','は','に','で'],1,'Trợ từ は đánh dấu chủ đề.'),
 ('jlpt',2,'Ngữ pháp です/ます & trợ từ',60,2,'パン ___ たべます。',ARRAY['を','が','へ','と'],0,'を đánh dấu tân ngữ.'),
 ('jlpt',2,'Ngữ pháp です/ます & trợ từ',60,3,'Thể phủ định lịch sự của たべます là:',ARRAY['たべません','たべない','たべました','たべて'],0,NULL),
 ('jlpt',2,'Ngữ pháp です/ます & trợ từ',60,4,'がっこう ___ いきます。(đi đến trường)',ARRAY['を','へ','は','の'],1,'へ/に chỉ hướng di chuyển.'),
 ('jlpt',2,'Ngữ pháp です/ます & trợ từ',60,5,'これは ほん ___。',ARRAY['です','ます','でした','だろう'],0,NULL),
 ('math',1,'Căn bậc hai',80,1,'√49 = ?',ARRAY['6','7','8','9'],1,NULL),
 ('math',1,'Căn bậc hai',80,2,'Điều kiện xác định của √(x − 3) là:',ARRAY['x > 3','x ≥ 3','x ≤ 3','x ≠ 3'],1,'Biểu thức dưới dấu căn phải ≥ 0.'),
 ('math',1,'Căn bậc hai',80,3,'√(a²) = ?',ARRAY['a','−a','|a|','a²'],2,'√(a²) = |a| với mọi a.'),
 ('math',1,'Căn bậc hai',80,4,'√12 rút gọn bằng:',ARRAY['2√3','3√2','4√3','6√2'],0,'√12 = √(4·3) = 2√3.'),
 ('math',1,'Căn bậc hai',80,5,'Trục căn thức: 1/√2 = ?',ARRAY['√2','√2/2','2√2','1/2'],1,'Nhân cả tử và mẫu với √2.'),
 ('math',2,'Phương trình bậc hai & Vi-ét',60,1,'x² − 5x + 6 = 0 có nghiệm:',ARRAY['x = 1; x = 6','x = 2; x = 3','x = −2; x = −3','Vô nghiệm'],1,'2 + 3 = 5 và 2 · 3 = 6.'),
 ('math',2,'Phương trình bậc hai & Vi-ét',60,2,'Biệt thức Δ của ax² + bx + c = 0 là:',ARRAY['b² − 4ac','b² + 4ac','4ac − b²','b − 4ac'],0,NULL),
 ('math',2,'Phương trình bậc hai & Vi-ét',60,3,'Theo Vi-ét, x₁ + x₂ = ?',ARRAY['c/a','−b/a','b/a','−c/a'],1,NULL),
 ('math',2,'Phương trình bậc hai & Vi-ét',60,4,'Nếu Δ < 0 thì phương trình:',ARRAY['Có 2 nghiệm phân biệt','Có nghiệm kép','Vô nghiệm','Có vô số nghiệm'],2,NULL),
 ('math',2,'Phương trình bậc hai & Vi-ét',60,5,'Tích hai nghiệm của x² − 7x + 10 = 0 bằng:',ARRAY['7','−7','10','−10'],2,'x₁·x₂ = c/a = 10.');

-- Bộ 1 cho mọi lớp chưa huỷ; bộ 2 thêm cho lớp đã/đang học.
CREATE TEMP TABLE sd_mini_task ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY c.n, b.set_no) AS n, c.n AS class_n, c.id AS class_id, c.teacher_id, c.subject,
       b.set_no, b.title, b.pass_percent,
       CASE WHEN c.status = 'ENROLLING' THEN pg_temp.at_vn(x.today - 1, '20:00')
            ELSE pg_temp.at_vn(c.start_date + 3 + (b.set_no - 1) * 14, '20:00') END AS created_at,
       c.status AS class_status
FROM sd_class c CROSS JOIN sd_ctx x
JOIN (SELECT DISTINCT subject, set_no, title, pass_percent FROM sd_bank) b ON b.subject = c.subject
WHERE c.status <> 'CANCELLED'
  AND (b.set_no = 1 OR (c.status IN ('COMPLETED', 'IN_PROGRESS') AND c.start_date + 17 < x.today - 2));

INSERT INTO mini_tasks (id, class_id, title, description, active, created_by_account_id, created_at, updated_at, pass_percent)
SELECT pg_temp.did(17, m.n), m.class_id, m.title,
       'Ôn lại kiến thức đã học, làm lại bao nhiêu lần cũng được. Đạt khi đúng từ ' || m.pass_percent || '%.',
       true, m.teacher_id, m.created_at, m.created_at, m.pass_percent
FROM sd_mini_task m;

INSERT INTO mini_task_questions (id, mini_task_id, content, correct_option_index, display_order, explanation)
SELECT pg_temp.did(18, m.n * 10 + b.q), pg_temp.did(17, m.n), b.content, b.correct, b.q - 1, b.explanation
FROM sd_mini_task m JOIN sd_bank b ON b.subject = m.subject AND b.set_no = m.set_no;

INSERT INTO mini_task_question_options (question_id, option_index, content)
SELECT pg_temp.did(18, m.n * 10 + b.q), o.k - 1, o.content
FROM sd_mini_task m JOIN sd_bank b ON b.subject = m.subject AND b.set_no = m.set_no
CROSS JOIN LATERAL unnest(b.options) WITH ORDINALITY o(content, k);

-- Lượt làm: khoảng 3/4 học viên có làm, 1-4 lượt, điểm tăng dần qua các lượt (5 câu/bộ).
INSERT INTO mini_task_attempts (id, mini_task_id, enrollment_id, correct_count, total_questions, score_percent, submitted_at)
SELECT pg_temp.did(19, row_number() OVER (ORDER BY m.n, e.n, j)), pg_temp.did(17, m.n), e.id,
       LEAST(5, 1 + pg_temp.rnd('base' || m.n || '-' || e.n) % 4 + j - 1), 5,
       LEAST(5, 1 + pg_temp.rnd('base' || m.n || '-' || e.n) % 4 + j - 1) * 20,
       LEAST(m.created_at + make_interval(days => j * 2 + pg_temp.rnd('day' || m.n || '-' || e.n) % 5, hours => 2),
             now() - make_interval(hours => 5 - j))
FROM sd_mini_task m
JOIN sd_enroll e ON e.class_n = m.class_n AND e.status IN ('ACTIVE', 'COMPLETED')
CROSS JOIN LATERAL generate_series(1, 1 + pg_temp.rnd('try' || m.n || '-' || e.n) % 4) j
WHERE m.class_status IN ('COMPLETED', 'IN_PROGRESS')
  AND pg_temp.rnd('do' || m.n || '-' || e.n) < 75;

-- ---------------------------------------------------------------------------------------------------
-- 12. Nhật ký đăng nhập 7 ngày gần đây (dashboard "đăng nhập gần đây") + last_login
-- ---------------------------------------------------------------------------------------------------
CREATE TEMP TABLE sd_login ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY a.id, k) AS n, a.id AS account_id, a.home_branch_id,
       now() - make_interval(days => pg_temp.rnd('ld' || a.id || k) % 7,
                             hours => pg_temp.rnd('lh' || a.id || k) % 12,
                             mins => pg_temp.rnd('lm' || a.id || k) % 60) AS occurred_at
FROM accounts a CROSS JOIN generate_series(1, 3) k
WHERE a.id::text LIKE 'de30%' AND a.status = 'ACTIVE'
  AND (a.id::text LIKE 'de300002%' OR pg_temp.rnd('login' || a.id) < 30)
  AND k <= 1 + pg_temp.rnd('lk' || a.id) % 3;

INSERT INTO audit_logs (id, actor_account_id, action, entity_type, entity_id, branch_id, occurred_at)
SELECT pg_temp.did(24, l.n), l.account_id, 'LOGIN', 'ACCOUNT', l.account_id::text, l.home_branch_id, l.occurred_at
FROM sd_login l;

UPDATE accounts a SET last_login = l.last_at
FROM (SELECT account_id, max(occurred_at) AS last_at FROM sd_login GROUP BY account_id) l
WHERE a.id = l.account_id;

-- ---------------------------------------------------------------------------------------------------
-- Tóm tắt
-- ---------------------------------------------------------------------------------------------------
SELECT 'Giáo viên' AS "Dữ liệu demo", count(*) AS "Số bản ghi" FROM teacher_profiles WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Học viên', count(*) FROM student_profiles WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Khoá học', count(*) FROM courses WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Lớp học', count(*) FROM classes WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Ghi danh', count(*) FROM enrollments WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Hoá đơn', count(*) FROM invoices WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Thanh toán', count(*) FROM payments WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Combo', count(*) FROM combos WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Hợp đồng lao động', count(*) FROM employment_contracts WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Buổi điểm danh', count(*) FROM attendance_sessions WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Lượt điểm danh', count(*) FROM attendance_records WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Điểm thành phần', count(*) FROM grade_entries WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Bài tập / mini-test', count(*) FROM assignments WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Kết quả nộp bài', count(*) FROM assignment_results WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Mini-task', count(*) FROM mini_tasks WHERE id::text LIKE 'de30%'
UNION ALL SELECT 'Lượt làm mini-task', count(*) FROM mini_task_attempts WHERE id::text LIKE 'de30%';
