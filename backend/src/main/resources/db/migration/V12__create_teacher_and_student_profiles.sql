CREATE TABLE teacher_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
    phone VARCHAR(32),
    bio VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE teacher_profile_subjects (
    teacher_profile_id UUID NOT NULL REFERENCES teacher_profiles(id) ON DELETE CASCADE,
    subject VARCHAR(255) NOT NULL
);

CREATE INDEX idx_teacher_profile_subjects_profile ON teacher_profile_subjects (teacher_profile_id);

CREATE TABLE student_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
    date_of_birth DATE,
    phone VARCHAR(32),
    source_channel VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true
);
