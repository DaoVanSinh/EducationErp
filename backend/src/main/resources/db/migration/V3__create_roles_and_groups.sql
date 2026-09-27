CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    system_default BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE role_permission_groups (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_group_id UUID NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_group_id)
);

CREATE TABLE user_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE group_permission_groups (
    group_id UUID NOT NULL REFERENCES user_groups(id) ON DELETE CASCADE,
    permission_group_id UUID NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, permission_group_id)
);
