-- Xoá TOÀN BỘ dữ liệu demo do seed-demo.sql tạo ra, kèm mọi bản ghi người dùng tạo thêm trên dữ liệu
-- demo trong lúc trình diễn (điểm danh, hoá đơn, bảng lương... của lớp/học viên/giáo viên demo).
-- Dữ liệu demo nhận diện bằng id bắt đầu bằng "de30" (seed-demo.sql sinh id theo mẫu cố định).
-- Dữ liệu thật (id ngẫu nhiên) không bị đụng tới, trừ việc gỡ liên kết nêu rõ ở từng bước.
-- Không tự mở/đóng transaction: run-seed.sql / run-cleanup.sql bọc file này trong BEGIN ... COMMIT.

CREATE TEMP TABLE cl_branches ON COMMIT DROP AS
    SELECT id FROM branches WHERE id::text LIKE 'de30%';
CREATE TEMP TABLE cl_accounts ON COMMIT DROP AS
    SELECT id FROM accounts WHERE id::text LIKE 'de30%';
CREATE TEMP TABLE cl_courses ON COMMIT DROP AS
    SELECT id FROM courses WHERE id::text LIKE 'de30%';
CREATE TEMP TABLE cl_students ON COMMIT DROP AS
    SELECT id FROM student_profiles WHERE id::text LIKE 'de30%' OR account_id IN (SELECT id FROM cl_accounts);
-- Lớp demo + lớp người dùng tạo thêm trên khoá/giáo viên/chi nhánh demo.
CREATE TEMP TABLE cl_classes ON COMMIT DROP AS
    SELECT id FROM classes
    WHERE id::text LIKE 'de30%'
       OR course_id IN (SELECT id FROM cl_courses)
       OR teacher_id IN (SELECT id FROM cl_accounts)
       OR branch_id IN (SELECT id FROM cl_branches);
CREATE TEMP TABLE cl_enrollments ON COMMIT DROP AS
    SELECT id FROM enrollments
    WHERE id::text LIKE 'de30%'
       OR class_id IN (SELECT id FROM cl_classes)
       OR student_profile_id IN (SELECT id FROM cl_students);
CREATE TEMP TABLE cl_combos ON COMMIT DROP AS
    SELECT id FROM combos
    WHERE id::text LIKE 'de30%'
       OR student_profile_id IN (SELECT id FROM cl_students)
       OR id IN (SELECT combo_id FROM combo_enrollments WHERE enrollment_id IN (SELECT id FROM cl_enrollments));

-- Học tập: mini-task, bài tập, điểm danh, điểm.
DELETE FROM mini_task_attempts
WHERE enrollment_id IN (SELECT id FROM cl_enrollments)
   OR mini_task_id IN (SELECT id FROM mini_tasks WHERE class_id IN (SELECT id FROM cl_classes));
DELETE FROM mini_tasks WHERE class_id IN (SELECT id FROM cl_classes);
DELETE FROM assignment_results WHERE enrollment_id IN (SELECT id FROM cl_enrollments);
DELETE FROM assignments WHERE class_id IN (SELECT id FROM cl_classes);
DELETE FROM attendance_records WHERE enrollment_id IN (SELECT id FROM cl_enrollments);
DELETE FROM attendance_sessions WHERE class_id IN (SELECT id FROM cl_classes);
DELETE FROM grade_entries
WHERE enrollment_id IN (SELECT id FROM cl_enrollments)
   OR course_grade_column_id IN (SELECT id FROM course_grade_columns WHERE course_id IN (SELECT id FROM cl_courses));

-- Học phí: thanh toán xoá theo hoá đơn (ON DELETE CASCADE).
DELETE FROM invoices
WHERE id::text LIKE 'de30%'
   OR enrollment_id IN (SELECT id FROM cl_enrollments)
   OR combo_id IN (SELECT id FROM cl_combos)
   OR student_profile_id IN (SELECT id FROM cl_students)
   OR course_id IN (SELECT id FROM cl_courses);
DELETE FROM combos WHERE id IN (SELECT id FROM cl_combos);
DELETE FROM enrollments WHERE id IN (SELECT id FROM cl_enrollments);

-- Lớp, khoá học (lịch học, cột điểm xoá theo cascade).
DELETE FROM classes WHERE id IN (SELECT id FROM cl_classes);
DELETE FROM courses WHERE id IN (SELECT id FROM cl_courses);

-- Nhân sự: phiếu lương (kể cả của bảng lương người dùng tạo trong lúc demo), hợp đồng.
DELETE FROM payslips WHERE account_id IN (SELECT id FROM cl_accounts);
DELETE FROM employment_contracts WHERE account_id IN (SELECT id FROM cl_accounts);

-- Hồ sơ, nhật ký, tài khoản (account_roles/account_groups xoá theo cascade).
DELETE FROM teacher_profiles WHERE account_id IN (SELECT id FROM cl_accounts);
DELETE FROM student_profiles WHERE id IN (SELECT id FROM cl_students);
DELETE FROM audit_logs
WHERE id::text LIKE 'de30%'
   OR actor_account_id IN (SELECT id FROM cl_accounts)
   OR branch_id IN (SELECT id FROM cl_branches);
DELETE FROM accounts WHERE id IN (SELECT id FROM cl_accounts);

-- Tài khoản thật lỡ được chuyển sang chi nhánh demo: gỡ chi nhánh để xoá được chi nhánh demo.
UPDATE accounts SET home_branch_id = NULL WHERE home_branch_id IN (SELECT id FROM cl_branches);
DELETE FROM branches WHERE id IN (SELECT id FROM cl_branches);
