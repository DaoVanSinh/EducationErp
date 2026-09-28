#!/usr/bin/env python3
"""
Generate a modular Spring Boot 3.4+ / Java 21 project with Spring Modulith conventions.
Every domain module owns its constants, config, exceptions, rules, repository, use cases,
and facade. Every integration is a module in its own right.

Usage:
    python scaffold.py --add-module billing --package com.eduerp --output ./backend/src/main/java
    python scaffold.py --add-integration cache --package com.eduerp --output ./backend/src/main/java
"""

import argparse
import sys
from pathlib import Path


def to_pascal_case(snake_str: str) -> str:
    return "".join(word.capitalize() for word in snake_str.replace("-", "_").split("_"))


def to_camel_case(snake_str: str) -> str:
    pascal = to_pascal_case(snake_str)
    return pascal[0].lower() + pascal[1:] if pascal else ""


def write_file(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n", encoding="utf-8")
    print(f"Created: {path}")


def scaffold_domain_module(base_dir: Path, package: str, module_name: str) -> None:
    pascal = to_pascal_case(module_name)
    camel = to_camel_case(module_name)
    pkg_path = base_dir / package.replace(".", "/") / "modules" / module_name
    mod_pkg = f"{package}.modules.{module_name}"

    # 1. package-info.java
    write_file(
        pkg_path / "package-info.java",
        f"""/**
 * Module {pascal} — Bounded context for {module_name}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "{pascal}",
    allowedDependencies = {{"integrations::cache", "shared"}}
)
package {mod_pkg};
""",
    )

    # 2. <Module>Constants.java
    write_file(
        pkg_path / f"{pascal}Constants.java",
        f"""package {mod_pkg};

public final class {pascal}Constants {{

    private {pascal}Constants() {{}}

    public enum Status {{
        ACTIVE, INACTIVE, DRAFT
    }}

    public static final class Limits {{
        private Limits() {{}}
        public static final int MAX_NAME_LENGTH = 100;
        public static final int DEFAULT_PAGE_SIZE = 20;
    }}

    public static final class Resources {{
        private Resources() {{}}
        public static final String {module_name.upper()} = "{module_name.upper()}";
    }}

    public static final class Actions {{
        private Actions() {{}}
        public static final String CREATE = "CREATE";
        public static final String READ = "READ";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
    }}

    public static final class Authorities {{
        private Authorities() {{}}
        public static final String PERM_PREFIX = "PERM:";
        public static final String READ = "PERM:{module_name.upper()}:READ";
        public static final String WRITE = "PERM:{module_name.upper()}:WRITE";
    }}

    public static final class CacheNamespaces {{
        private CacheNamespaces() {{}}
        public static final String {pascal.upper()} = "{module_name}:item";
    }}

    public static final class ErrorCodes {{
        private ErrorCodes() {{}}
        public static final String NOT_FOUND = "{module_name.upper()}_NOT_FOUND";
        public static final String ALREADY_EXISTS = "{module_name.upper()}_ALREADY_EXISTS";
    }}
}}
""",
    )

    # 3. <Module>Properties.java
    write_file(
        pkg_path / f"{pascal}Properties.java",
        f"""package {mod_pkg};

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "{module_name}")
public record {pascal}Properties(
    boolean enabled,
    Duration cacheTtl
) {{
    public {pascal}Properties {{
        if (cacheTtl == null) cacheTtl = Duration.ofHours(1);
    }}
}}
""",
    )

    # 4. <Module>Exception.java
    write_file(
        pkg_path / f"{pascal}Exception.java",
        f"""package {mod_pkg};

import {package}.core.exception.AppException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public sealed class {pascal}Exception extends AppException
    permits {pascal}NotFoundException, {pascal}AlreadyExistsException {{

    protected {pascal}Exception(String errorCode, HttpStatus status, String message) {{
        super(errorCode, status, message);
    }}
}}

final class {pascal}NotFoundException extends {pascal}Exception {{
    public {pascal}NotFoundException(UUID id) {{
        super({pascal}Constants.ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND, "{pascal} with id " + id + " not found");
    }}
}}

final class {pascal}AlreadyExistsException extends {pascal}Exception {{
    public {pascal}AlreadyExistsException(String name) {{
        super({pascal}Constants.ErrorCodes.ALREADY_EXISTS, HttpStatus.CONFLICT, "{pascal} '" + name + "' already exists");
    }}
}}
""",
    )

    # 5. <Module>Events.java
    write_file(
        pkg_path / f"{pascal}Events.java",
        f"""package {mod_pkg};

import java.time.Instant;
import java.util.UUID;

public final class {pascal}Events {{

    private {pascal}Events() {{}}

    public record {pascal}Created(
        UUID id,
        String name,
        Instant occurredAt
    ) {{}}
}}
""",
    )

    # 6. DTO Package
    dto_pkg = f"{mod_pkg}.dto"
    write_file(
        pkg_path / "dto" / "package-info.java",
        f"""@org.springframework.modulith.NamedInterface("dto")
package {dto_pkg};
""",
    )

    write_file(
        pkg_path / "dto" / f"Create{pascal}Request.java",
        f"""package {dto_pkg};

import {mod_pkg}.{pascal}Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Create{pascal}Request(
    @NotBlank
    @Size(max = {pascal}Constants.Limits.MAX_NAME_LENGTH)
    String name,

    String description
) {{}}
""",
    )

    write_file(
        pkg_path / "dto" / f"{pascal}Response.java",
        f"""package {dto_pkg};

import {mod_pkg}.{pascal}Constants.Status;
import java.time.Instant;
import java.util.UUID;

public record {pascal}Response(
    UUID id,
    String name,
    String description,
    Status status,
    Instant createdAt
) {{}}
""",
    )

    # 7. Internal Package (Entities, Repositories, Rules, Utils)
    internal_pkg = f"{mod_pkg}.internal"
    write_file(
        pkg_path / "internal" / "package-info.java",
        f"""package {internal_pkg};
""",
    )

    # Model
    model_pkg = f"{internal_pkg}.model"
    write_file(
        pkg_path / "internal" / "model" / f"{pascal}.java",
        f"""package {model_pkg};

import {mod_pkg}.{pascal}Constants.Status;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "{module_name}s")
public class {pascal} {{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected {pascal}() {{}}

    public {pascal}(String name, String description) {{
        this.name = name;
        this.description = description;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }}

    public UUID getId() {{ return id; }}
    public String getName() {{ return name; }}
    public String getDescription() {{ return description; }}
    public Status getStatus() {{ return status; }}
    public Instant getCreatedAt() {{ return createdAt; }}

    public void update(String name, String description) {{
        this.name = name;
        this.description = description;
    }}

    @Override
    public boolean equals(Object o) {{
        if (this == o) return true;
        if (!(o instanceof {pascal} that)) return false;
        return id != null && Objects.equals(id, that.id);
    }}

    @Override
    public int hashCode() {{
        return getClass().hashCode();
    }}
}}
""",
    )

    # Repository
    repo_pkg = f"{internal_pkg}.repository"
    write_file(
        pkg_path / "internal" / "repository" / f"{pascal}Repository.java",
        f"""package {repo_pkg};

import {model_pkg}.{pascal};
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface {pascal}Repository extends JpaRepository<{pascal}, UUID> {{
    Optional<{pascal}> findByName(String name);
    boolean existsByName(String name);
}}
""",
    )

    # Rules
    rules_pkg = f"{internal_pkg}.rules"
    write_file(
        pkg_path / "internal" / "rules" / f"{pascal}Rules.java",
        f"""package {rules_pkg};

import {model_pkg}.{pascal};
import {mod_pkg}.{pascal}Constants.Status;

public final class {pascal}Rules {{

    public boolean canModify({pascal} entity) {{
        return entity != null && entity.getStatus() != Status.INACTIVE;
    }}
}}
""",
    )

    # 8. Use Cases
    usecase_pkg = f"{mod_pkg}.usecase"
    write_file(
        pkg_path / "usecase" / f"Create{pascal}.java",
        f"""package {usecase_pkg};

import {mod_pkg}.{pascal}AlreadyExistsException;
import {mod_pkg}.{pascal}Events;
import {dto_pkg}.Create{pascal}Request;
import {dto_pkg}.{pascal}Response;
import {model_pkg}.{pascal};
import {repo_pkg}.{pascal}Repository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Create{pascal} {{

    private final {pascal}Repository repository;
    private final ApplicationEventPublisher events;

    public Create{pascal}({pascal}Repository repository, ApplicationEventPublisher events) {{
        this.repository = repository;
        this.events = events;
    }}

    @Transactional
    public {pascal}Response execute(Create{pascal}Request request) {{
        if (repository.existsByName(request.name())) {{
            throw new {pascal}AlreadyExistsException(request.name());
        }}

        {pascal} entity = new {pascal}(request.name(), request.description());
        {pascal} saved = repository.save(entity);

        events.publishEvent(new {pascal}Events.{pascal}Created(
            saved.getId(), saved.getName(), Instant.now()
        ));

        return new {pascal}Response(
            saved.getId(), saved.getName(), saved.getDescription(), saved.getStatus(), saved.getCreatedAt()
        );
    }}
}}
""",
    )

    # 9. Facade
    write_file(
        pkg_path / f"{pascal}Management.java",
        f"""package {mod_pkg};

import {dto_pkg}.{pascal}Response;
import {repo_pkg}.{pascal}Repository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class {pascal}Management {{

    private final {pascal}Repository repository;

    {pascal}Management({pascal}Repository repository) {{
        this.repository = repository;
    }}

    public Optional<{pascal}Response> findById(UUID id) {{
        return repository.findById(id)
            .map(entity -> new {pascal}Response(
                entity.getId(), entity.getName(), entity.getDescription(), entity.getStatus(), entity.getCreatedAt()
            ));
    }}
}}
""",
    )

    # 10. Web Controller
    web_pkg = f"{mod_pkg}.web"
    write_file(
        pkg_path / "web" / f"{pascal}Controller.java",
        f"""package {web_pkg};

import {dto_pkg}.Create{pascal}Request;
import {dto_pkg}.{pascal}Response;
import {usecase_pkg}.Create{pascal};
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/{module_name}s")
class {pascal}Controller {{

    private final Create{pascal} create{pascal};

    {pascal}Controller(Create{pascal} create{pascal}) {{
        this.create{pascal} = create{pascal};
    }}

    @PostMapping
    ResponseEntity<{pascal}Response> create(@Valid @RequestBody Create{pascal}Request request) {{
        {pascal}Response response = create{pascal}.execute(request);
        return ResponseEntity
            .created(URI.create("/api/v1/{module_name}s/" + response.id()))
            .body(response);
    }}
}}
""",
    )

    print(f"\nSuccessfully generated domain module '{module_name}' under {pkg_path}")


def scaffold_integration(base_dir: Path, package: str, name: str) -> None:
    pkg_path = base_dir / package.replace(".", "/") / "integrations" / name
    int_pkg = f"{package}.integrations.{name}"

    if name == "cache":
        write_file(
            pkg_path / "package-info.java",
            f"""@org.springframework.modulith.ApplicationModule(displayName = "Cache Integration")
package {int_pkg};
""",
        )
        write_file(
            pkg_path / "CacheKeyBuilder.java",
            f"""package {int_pkg};

public final class CacheKeyBuilder {{

    private static final String SEPARATOR = ":";
    private static final String WILDCARD = "*";

    private CacheKeyBuilder() {{}}

    public static String key(String namespace, Object identifier) {{
        return namespace + SEPARATOR + identifier;
    }}

    public static String pattern(String namespace) {{
        return namespace + SEPARATOR + WILDCARD;
    }}

    public static String identifierIn(String namespace, String key) {{
        return key.substring(namespace.length() + SEPARATOR.length());
    }}
}}
""",
        )
        print(f"Generated cache integration under {pkg_path}")

    elif name == "messaging":
        write_file(
            pkg_path / "package-info.java",
            f"""@org.springframework.modulith.ApplicationModule(displayName = "Messaging Integration")
package {int_pkg};
""",
        )
        print(f"Generated messaging integration under {pkg_path}")


def main():
    parser = argparse.ArgumentParser(description="Spring Boot Modular Architecture Scaffolder")
    parser.add_argument("--add-module", type=str, help="Name of domain module to generate (e.g. billing, catalog)")
    parser.add_argument("--add-integration", type=str, choices=["cache", "messaging", "storage"], help="Add an integration")
    parser.add_argument("--package", type=str, default="com.eduerp", help="Root Java package (e.g. com.eduerp)")
    parser.add_argument("--output", type=str, default="./backend/src/main/java", help="Output base directory")

    args = parser.parse_args()
    base_dir = Path(args.output).resolve()

    if args.add_module:
        scaffold_domain_module(base_dir, args.package, args.add_module.lower())
    elif args.add_integration:
        scaffold_integration(base_dir, args.package, args.add_integration.lower())
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
