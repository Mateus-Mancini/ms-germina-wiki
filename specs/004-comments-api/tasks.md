---

description: "Task list for Comments API implementation"
---

# Tasks: Comments API

**Input**: Design documents from `/specs/004-comments-api/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/comments-api.yaml](contracts/comments-api.yaml), [quickstart.md](quickstart.md)

**Tests**: Incluídos porque a Constituição exige testes automatizados para regras de negócio, sucesso, erro, validações e regressões.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar o projeto Java/Spring para persistência versionada, pacotes da feature e contratos de erro.

- [X] T001 [P] Criar os pacotes da feature em `src/main/java/com/wikigerminare/controller`, `src/main/java/com/wikigerminare/dto/comment`, `src/main/java/com/wikigerminare/entity/comment`, `src/main/java/com/wikigerminare/repository/comment`, `src/main/java/com/wikigerminare/service` e `src/main/java/com/wikigerminare/integration`.
- [X] T002 [P] Adicionar a dependência e configuração de migrações PostgreSQL necessárias para executar `src/main/resources/db/migration/V4__create_comments.sql` em `pom.xml` e `src/main/resources/application.properties`.
- [X] T003 [P] Criar o formato compartilhado de erro `ErrorResponse` e o tratamento de exceções HTTP em `src/main/java/com/wikigerminare/dto/comment/ErrorResponse.java` e `src/main/java/com/wikigerminare/config/ApiExceptionHandler.java`, cobrindo códigos de validação, recurso inexistente, indisponibilidade e falta de permissão.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Entregar persistência, integrações e políticas comuns que bloqueiam todas as histórias.

**Checkpoint**: A fundação deve estar pronta antes do trabalho de qualquer história.

- [X] T004 [P] Criar a migração `src/main/resources/db/migration/V4__create_comments.sql` com tabelas de comentários e respostas, UUIDs, chaves estrangeiras, status `ACTIVE`/`REMOVED`, timestamps, revisão do conteúdo e índices para `contentId`, anchor e ordenação `createdAt DESC, id DESC`.
- [X] T005 [P] Definir `AuthenticatedUser` e `AuthenticatedUserProvider` em `src/main/java/com/wikigerminare/integration/AuthenticatedUser.java` e `src/main/java/com/wikigerminare/integration/AuthenticatedUserProvider.java`, expondo `id` e `isAdmin` sem criar autenticação duplicada.
- [X] T006 [P] Definir `AnchorInput`, `ValidatedAnchor` e `ContentAnchorValidator` em `src/main/java/com/wikigerminare/integration/AnchorInput.java`, `src/main/java/com/wikigerminare/integration/ValidatedAnchor.java` e `src/main/java/com/wikigerminare/integration/ContentAnchorValidator.java`, incluindo `assertPublishedContent(contentId)` e `validateAnchor(contentId, anchorInput)`.
- [X] T007 [P] Criar a política de texto `CommentTextPolicy` em `src/main/java/com/wikigerminare/service/CommentTextPolicy.java`, rejeitando texto vazio após trim e aplicando o limite máximo definido para comentários e respostas.
- [X] T008 [P] Criar fixtures de identidade, conteúdo publicado e anchors válidos/inválidos em `src/test/java/com/wikigerminare/support/CommentTestFixtures.java` para permitir testes sem depender de autenticação ou páginas reais.

---

## Phase 3: User Story 1 - Gerenciar comentários em conteúdo da wiki (Priority: P1) 🎯 MVP

**Goal**: Permitir que usuário autenticado crie, consulte, edite e remova o próprio comentário, preservando autoria, conteúdo e anchor.

**Independent Test**: Com fixtures de usuário e conteúdo publicado, criar um comentário válido, consultá-lo, editá-lo, removê-lo e confirmar que outro usuário recebe `403` e não altera o registro.

### Tests for User Story 1

- [X] T009 [P] [US1] Criar testes unitários de criação, edição, remoção lógica, autoria imutável e autorização em `src/test/java/com/wikigerminare/service/CommentServiceTest.java`, incluindo as regras: `contentId`, `authorId`, `text`, `anchorType` e `anchorValue` são obrigatórios; edição não pode trocar conteúdo, autor ou anchor.
- [X] T010 [P] [US1] Criar testes HTTP para `POST /api/comments`, `GET /api/comments/{commentId}`, `PATCH /api/comments/{commentId}` e `DELETE /api/comments/{commentId}` em `src/test/java/com/wikigerminare/controller/CommentControllerTest.java`, verificando `201`, `200`, `204`, `400`, `401`, `403`, `404` e o schema de `ErrorResponse`.
- [X] T011 [P] [US1] Criar teste de persistência dos estados `ACTIVE` e `REMOVED`, timestamps e preservação de `authorId`, `contentId` e anchor em `src/test/java/com/wikigerminare/repository/CommentRepositoryTest.java`.

### Implementation for User Story 1

- [X] T012 [P] [US1] Criar as entidades JPA `Comment` e `AdminReply` em `src/main/java/com/wikigerminare/entity/comment/Comment.java` e `src/main/java/com/wikigerminare/entity/comment/AdminReply.java`, com os campos UUID, `contentId`, `authorId`, texto, anchor, `contentRevision`, status, timestamps e relacionamento de respostas.
- [X] T013 [P] [US1] Criar DTOs de entrada e saída em `src/main/java/com/wikigerminare/dto/comment/CreateCommentRequest.java`, `src/main/java/com/wikigerminare/dto/comment/UpdateCommentRequest.java`, `src/main/java/com/wikigerminare/dto/comment/AnchorResponse.java`, `src/main/java/com/wikigerminare/dto/comment/AdminReplyResponse.java` e `src/main/java/com/wikigerminare/dto/comment/CommentResponse.java`, mantendo `contentId`, anchor, texto, status ativo, datas e respostas no contrato.
- [X] T014 [US1] Criar `CommentRepository` e `AdminReplyRepository` em `src/main/java/com/wikigerminare/repository/comment/CommentRepository.java` e `src/main/java/com/wikigerminare/repository/comment/AdminReplyRepository.java`, com busca por id ativo e persistência de remoção lógica.
- [X] T015 [US1] Implementar criação, leitura individual, edição e remoção do próprio comentário em `src/main/java/com/wikigerminare/service/CommentService.java`, validando conteúdo publicado e anchor antes de persistir, aplicando `CommentTextPolicy`, mantendo autoria/conteúdo/anchor imutáveis e usando estado `REMOVED` de forma idempotente.
- [X] T016 [US1] Implementar `CommentController` em `src/main/java/com/wikigerminare/controller/CommentController.java` para os quatro endpoints da US1, usando DTOs, identidade do `AuthenticatedUserProvider` e códigos `201`, `200`, `204`, `400`, `401`, `403` e `404` do contrato.

**Checkpoint**: US1 deve permitir o ciclo completo do autor sem depender da US2 ou US3.

---

## Phase 4: User Story 2 - Consultar comentários por conteúdo e anchor (Priority: P1)

**Goal**: Permitir consultas públicas paginadas por conteúdo e, opcionalmente, por anchor, sem expor conteúdo indisponível.

**Independent Test**: Persistir comentários ativos em dois anchors, consultar cada filtro com paginação e confirmar ordenação mais recente primeiro, resultado vazio sem erro e rejeição de conteúdo inexistente/não publicado.

### Tests for User Story 2

- [X] T017 [P] [US2] Criar testes de repository para filtros por `contentId` e anchor, exclusão de `REMOVED`, paginação padrão `page=0,size=20`, limite `size<=100` e ordenação determinística `createdAt DESC, id DESC` em `src/test/java/com/wikigerminare/repository/CommentRepositoryQueryTest.java`.
- [X] T018 [P] [US2] Criar testes HTTP da listagem `GET /api/comments` em `src/test/java/com/wikigerminare/controller/CommentQueryControllerTest.java`, cobrindo página, totalItems, totalPages, resultado vazio, `404` de conteúdo e `409` de conteúdo indisponível.

### Implementation for User Story 2

- [X] T019 [US2] Adicionar consulta paginada a `src/main/java/com/wikigerminare/repository/comment/CommentRepository.java` com filtros opcionais de `anchorType`/`anchorValue`, somente status `ACTIVE` e ordenação `createdAt DESC, id DESC`.
- [X] T020 [US2] Implementar `CommentPageResponse` e o caso de uso de listagem em `src/main/java/com/wikigerminare/dto/comment/CommentPageResponse.java` e `src/main/java/com/wikigerminare/service/CommentService.java`, aplicando `page` mínimo `0`, `size` padrão `20`, máximo `100`, validação de conteúdo publicado e retorno de metadados de paginação.
- [X] T021 [US2] Expor `GET /api/comments` em `src/main/java/com/wikigerminare/controller/CommentController.java`, aceitando `contentId` obrigatório, filtros opcionais de anchor e parâmetros de paginação conforme `specs/004-comments-api/contracts/comments-api.yaml`.

**Checkpoint**: US1 e US2 devem funcionar independentemente: o ciclo do autor continua válido e a consulta contextual retorna somente comentários ativos do conteúdo/anchor solicitado.

---

## Phase 5: User Story 3 - Responder como administrador (Priority: P2)

**Goal**: Permitir respostas administrativas e moderação de comentários de terceiros, sem permitir essas ações a usuários comuns.

**Independent Test**: Como administrador, responder a comentário ativo e removê-lo; como usuário comum, repetir as ações e confirmar `403`, preservação do comentário e ausência de respostas em consultas ativas após remoção.

### Tests for User Story 3

- [X] T022 [P] [US3] Criar testes unitários de permissão administrativa, resposta somente para comentário `ACTIVE`, vínculo `commentId`/`adminId`, texto inválido e remoção de terceiro em `src/test/java/com/wikigerminare/service/AdminCommentServiceTest.java`.
- [X] T023 [P] [US3] Criar testes HTTP para `POST /api/comments/{commentId}/admin-replies` e `DELETE /api/comments/{commentId}` como administrador e usuário comum em `src/test/java/com/wikigerminare/controller/AdminCommentControllerTest.java`, verificando `201`, `204`, `400`, `401`, `403`, `404` e `409`.

### Implementation for User Story 3

- [X] T024 [US3] Criar os DTOs e a persistência específica de resposta administrativa em `src/main/java/com/wikigerminare/dto/comment/CreateAdminReplyRequest.java` e `src/main/java/com/wikigerminare/repository/comment/AdminReplyRepository.java`, mantendo `commentId`, `adminId`, texto e `createdAt` obrigatórios e imutáveis.
- [X] T025 [US3] Implementar criação de resposta administrativa e moderação no `src/main/java/com/wikigerminare/service/CommentService.java`, exigindo `isAdmin`, exigindo comentário `ACTIVE`, aplicando `CommentTextPolicy`, vinculando `AdminReply` ao comentário e permitindo remoção administrativa sem alterar outros comentários.
- [X] T026 [US3] Completar `src/main/java/com/wikigerminare/controller/CommentController.java` com `POST /api/comments/{commentId}/admin-replies` e autorização administrativa no `DELETE`, retornando respostas administrativas junto do comentário ativo e ocultando comentário removido e respostas das consultas ativas.

**Checkpoint**: As três histórias devem estar funcionais; US3 não pode ampliar permissões de edição de comentários ou criar conversa ilimitada entre usuários.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validar contrato, migração, desempenho esperado e o guia de aceitação sem alterar o escopo.

- [X] T027 [P] Documentar os endpoints implementados e os códigos de erro em `specs/004-comments-api/contracts/comments-api.yaml` e alinhar anotações OpenAPI em `src/main/java/com/wikigerminare/controller/CommentController.java`.
- [X] T028 [P] Adicionar teste de migração e índices em `src/test/java/com/wikigerminare/config/CommentsMigrationTest.java`, confirmando criação das tabelas, chaves, estados e índices da `V4__create_comments.sql`.
- [X] T029 Executar `./mvnw test` ou `./mvnw.cmd test`, executar os cenários de `specs/004-comments-api/quickstart.md`, revisar os limites de texto e registrar qualquer dependência ausente de autenticação/conteúdo antes da implementação final em `specs/004-comments-api/quickstart.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001-T003 podem iniciar imediatamente e em paralelo.
- **Foundational (Phase 2)**: T004-T008 dependem apenas do setup e bloqueiam as histórias.
- **User Story 1 (Phase 3)**: T009-T016 dependem da fundação; entrega o MVP.
- **User Story 2 (Phase 4)**: T017-T021 dependem de T012-T016 para reutilizar entidades, repository e serviço, mas sua consulta é testável isoladamente com fixtures.
- **User Story 3 (Phase 5)**: T022-T026 dependem de T012-T016 e podem começar em paralelo com US2 após a fundação.
- **Polish (Phase 6)**: T027-T029 dependem das histórias que forem incluídas na entrega.

### User Story Dependencies

- **US1 (P1)**: Sem dependência de outra história após a fundação; é o MVP recomendado.
- **US2 (P1)**: Reutiliza `Comment` e `CommentRepository` da US1, mas mantém consulta e testes independentes.
- **US3 (P2)**: Reutiliza `Comment`, `AdminReply` e autorização da US1; pode ser desenvolvida em paralelo com US2 após a fundação.

### Parallel Opportunities

- Setup: T001, T002 e T003 podem rodar em paralelo.
- Fundação: T004, T005, T006, T007 e T008 podem rodar em paralelo em arquivos diferentes.
- US1: T009, T010 e T011 são testes independentes; T012 e T013 também podem ser desenvolvidas em paralelo antes de T014-T016.
- US2: T017 e T018 podem rodar em paralelo; depois T019-T021 seguem a ordem repository -> service -> controller.
- US3: T022 e T023 podem rodar em paralelo; T024 pode começar antes de T025-T026.
- Após a fundação, US2 e US3 podem ser atribuídas a pessoas diferentes, desde que compartilhem os contratos de US1.

## Parallel Example: User Story 1

```text
T009: testes de regras em src/test/java/com/wikigerminare/service/CommentServiceTest.java
T010: testes HTTP em src/test/java/com/wikigerminare/controller/CommentControllerTest.java
T011: testes de persistência em src/test/java/com/wikigerminare/repository/CommentRepositoryTest.java
T012: entidades em src/main/java/com/wikigerminare/entity/comment/
T013: DTOs em src/main/java/com/wikigerminare/dto/comment/
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar T001-T008 para a fundação.
2. Completar T009-T016 para o ciclo do autor.
3. Executar o checkpoint da US1 e os cenários de criação, edição, remoção e permissão.
4. Parar para validar o MVP antes de iniciar filtros avançados ou moderação.

### Incremental Delivery

1. Fundação pronta: persistência, integração de identidade/conteúdo e política de validação.
2. US1: CRUD do autor e remoção lógica, entregável como MVP.
3. US2: filtragem por conteúdo/anchor e paginação.
4. US3: respostas administrativas e remoção por moderação.
5. Polish: migração, contrato, quickstart e suíte completa.

## Notes

- Toda tarefa segue `- [ ] T###`, usa marcador `[P]` somente quando pode ser paralelizada e inclui `[USn]` apenas nas fases de história.
- As tarefas de dados preservam as restrições do modelo: campos obrigatórios, status `ACTIVE`/`REMOVED`, texto não vazio após trim, anchor pertencente ao conteúdo e ordenação `createdAt DESC, id DESC`.
- A implementação não cria autenticação ou conteúdo duplicados; integra os módulos existentes ou seus adapters conforme `research.md`.

## Phase 7: Convergence

- [X] T030 Resolver a divergência entre a exigência constitucional de Kotlin e a implementação Java em `src/main/java` e `pom.xml`, migrando o escopo conforme decisão aprovada ou registrando uma atualização constitucional formal antes da integração final (Constitution técnica, `contradicts`).
- [X] T031 Substituir `src/main/java/com/wikigerminare/config/UnavailableAuthenticatedUserProvider.java` e `src/main/java/com/wikigerminare/config/UnavailableContentAnchorValidator.java` por adapters reais para identidade/permissão administrativa e conteúdo publicado/anchors, garantindo FR-001, FR-008, FR-009 e FR-011 em ambiente integrado (`partial`).
- [X] T032 Criar benchmark de consultas em `src/test/java/com/wikigerminare/performance/CommentQueryPerformanceTest.java` com dataset representativo, paginação padrão e medição que verifique SC-002: pelo menos 95% das consultas retornam a primeira página em até 1 segundo (`missing`).
- [ ] T033 Executar `src/main/resources/db/migration/V4__create_comments.sql` em um PostgreSQL de teste e substituir ou complementar `src/test/java/com/wikigerminare/config/CommentsMigrationTest.java` com validação de tabelas, constraints e índices reais (`partial`).
