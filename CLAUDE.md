# Instructions for Claude Code

@AGENTS.md

## Quick Reference Commands

### Backend (Spring Boot 3.4 / Java 21)
```bash
# Run application locally
cd backend && ./mvnw spring-boot:run

# Run Spring Modulith boundary verification tests
cd backend && mvn test -Dtest=ModularityTests

# Run all backend tests
cd backend && mvn test

# Compile and verify package
cd backend && mvn clean compile test-compile

# Scaffold a new domain module
python .agents/skills/springboot-modular-scaffold/scripts/scaffold.py --add-module billing --package com.eduerp --output ./backend/src/main/java
```

### Frontend (React 19 / Vite / TypeScript)
```bash
# Run development server
cd frontend && npm run dev

# Type check
cd frontend && npm run typecheck

# Lint (ESLint + boundaries)
cd frontend && npm run lint

# Production build
cd frontend && npm run build
```
