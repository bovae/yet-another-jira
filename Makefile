BE_DIR := be
FE_DIR := fe

.PHONY: up down be-test be-lint fmt fe-test fe-lint

up:
	docker compose up --build

down:
	docker compose down

be-test:
	cd $(BE_DIR) && ./mvnw test -Pbdd,it

# Mirrors CI's backend checks: format + Error Prone (compile) + unit & BDD tests + 90% coverage
# gate. The coverage gate needs BDD coverage, so the bdd/it profiles are required (BDD uses
# Testcontainers, so this target needs Docker running).
be-lint:
	cd $(BE_DIR) && ./mvnw spotless:check verify -Pbdd,it

fe-test:
	cd $(FE_DIR) && npm test

# Mirrors CI's frontend gate: ESLint + Prettier format check + TypeScript typecheck (src + e2e).
fe-lint:
	cd $(FE_DIR) && npm run lint && npm run format:check && npm run typecheck

fmt:
	cd $(BE_DIR) && ./mvnw spotless:apply
	cd $(FE_DIR) && npm run format
