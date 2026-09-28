# Implementation Plan: Comments API

**Branch**: `004-comments-api` | **Date**: 2026-09-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-comments-api/spec.md`

## Summary

A feature adiciona o ciclo completo de comentários ancorados: criação, consulta paginada,
edição pelo autor, remoção lógica, moderação administrativa e respostas administrativas.
O desenho segue Controller -> Service -> Repository, usa DTOs para os contratos HTTP,
persiste `Comment` e `AdminReply` em PostgreSQL e consulta portas de integração para
identidade, autorização, conteúdo publicado e validação de anchors. As decisões e
alternativas estão registradas em [research.md](research.md).

## Technical Context

**Language/Version**: Java 21 (stack real do repositório)

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC, Spring Data JPA, PostgreSQL,
Springdoc OpenAPI e dependências de teste já presentes no `pom.xml`

**Storage**: PostgreSQL com tabelas versionadas para comentários e respostas; migração SQL
em `src/main/resources/db/migration`

**Testing**: Maven Wrapper, testes unitários de serviço, testes de repository/persistência
e testes HTTP de controller/contrato

**Target Platform**: Serviço web Spring Boot executado em JVM 21

**Project Type**: Web service REST backend

**Performance Goals**: Pelo menos 95% das consultas retornam a primeira página em até 1
segundo em condições normais; tamanho de página entre 1 e 100, padrão 20

**Constraints**: Somente conteúdo publicado aceita comentários; anchors devem ser validados
contra conteúdo e revisão; autorização ocorre no Service; remoção é lógica; texto vazio ou
acima do limite definido é rejeitado; respostas são exclusivas de administradores

**Scale/Scope**: Uma feature de domínio com dois agregados persistidos, cinco operações de
comentário, uma operação de resposta administrativa e integração com identidade/conteúdo;
notificações, comentários anônimos e edição de respostas ficam fora do escopo

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Before Phase 0

- **I. Especificação como fonte de verdade**: PASS. Os cenários, requisitos e limites da
  [spec.md](spec.md) orientam este design; nenhuma capacidade adicional foi introduzida.
- **II. Arquitetura em camadas**: PASS. O plano separa controller, service, repository e
  portas de integração.
- **III. Contratos REST e DTOs**: PASS. O contrato OpenAPI em
  [contracts/comments-api.yaml](contracts/comments-api.yaml) define entradas, saídas e
  códigos de erro.
- **IV. Testes automatizados**: PASS. O plano inclui testes de regras, persistência,
  autorização, validação de anchor e contrato HTTP.
- **V. Simplicidade e manutenção**: PASS. O design reutiliza as dependências existentes
  e evita criar autenticação ou conteúdo duplicados.
- **Restrições técnicas**: EXCEÇÃO JUSTIFICADA. A Constituição exige Kotlin, mas o
  repositório atual é Java 21 e não possui código Kotlin. Migrar o projeto inteiro não é
  necessário para comentários e ficaria fora do escopo; a divergência está registrada em
  [research.md](research.md) e em Complexity Tracking.

**Gate result**: PASS WITH JUSTIFIED PRE-EXISTING EXCEPTION. A exceção deve ser ratificada
ou corrigida em uma mudança própria da Constituição antes da integração final.

### After Phase 1

- A separação Controller -> Service -> Repository permanece intacta.
- Os DTOs e códigos de resultado estão definidos em [contracts/comments-api.yaml](contracts/comments-api.yaml).
- As invariantes de anchor, autorização, remoção lógica e estados estão definidas em [data-model.md](data-model.md).
- Os testes previstos em [quickstart.md](quickstart.md) cobrem os cenários P1/P2 e os casos de erro relevantes.
- A exceção Kotlin/Java permanece a única pendência constitucional e não foi ampliada pelo design.

**Post-design gate**: PASS WITH THE SAME JUSTIFIED PRE-EXISTING EXCEPTION.

## Project Structure

### Documentation

```text
specs/004-comments-api/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/comments-api.yaml
└── tasks.md             # Criado posteriormente por /speckit-tasks
```

### Source Code

```text
src/
├── main/
│   ├── java/com/wikigerminare/
│   │   ├── controller/CommentController.java
│   │   ├── dto/comment/
│   │   │   ├── CreateCommentRequest.java
│   │   │   ├── UpdateCommentRequest.java
│   │   │   ├── CreateAdminReplyRequest.java
│   │   │   ├── CommentResponse.java
│   │   │   └── ErrorResponse.java
│   │   ├── entity/comment/
│   │   │   ├── Comment.java
│   │   │   └── AdminReply.java
│   │   ├── repository/comment/
│   │   │   ├── CommentRepository.java
│   │   │   └── AdminReplyRepository.java
│   │   ├── service/CommentService.java
│   │   └── integration/
│   │       ├── AuthenticatedUserProvider.java
│   │       └── ContentAnchorValidator.java
│   └── resources/db/migration/V4__create_comments.sql
└── test/
    └── java/com/wikigerminare/
        ├── controller/CommentControllerTest.java
        ├── service/CommentServiceTest.java
        └── repository/CommentRepositoryTest.java
```

**Structure Decision**: Projeto único Spring Boot. Os pacotes existentes sob
`src/main/java/com/wikigerminare` serão usados sem criar outro módulo. A regra de negócio
fica em `service`, persistência em `repository`, transporte em `controller/dto`, domínio
em `entity` e dependências ainda ausentes de autenticação/conteúdo ficam atrás de
`integration`. O contrato público e o guia executável ficam em `specs/004-comments-api`.

## Phase 0: Research Summary

Pesquisa concluída em [research.md](research.md). As decisões resolvidas foram:

1. Usar Java 21 e dependências já presentes, preservando o projeto real.
2. Não criar autenticação, usuários ou páginas duplicados; consumir identidade e conteúdo
   por portas de integração.
3. Validar e normalizar anchors antes da persistência e revalidá-los na edição.
4. Usar remoção lógica, respostas administrativas separadas e paginação determinística.
5. Preparar migração SQL versionada e tratar a ausência atual da infraestrutura de migração
   como dependência explícita.

## Phase 1: Design Summary

- [data-model.md](data-model.md) define `Comment`, `AdminReply`, invariantes, estados,
  value objects e portas de integração.
- [contracts/comments-api.yaml](contracts/comments-api.yaml) define listagem, criação,
  leitura, edição, remoção, resposta administrativa e erros.
- [quickstart.md](quickstart.md) define comandos Maven e cenários de aceitação executáveis.

### Implementation Sequencing Constraints

1. Disponibilizar identidade autenticada/autorização e o verificador de conteúdo publicado
   ou seus adapters antes dos testes integrados.
2. Criar a migração e os agregados de persistência antes dos repositories.
3. Implementar validações e autorização no service antes do controller.
4. Expor o contrato HTTP com DTOs e tratamento uniforme de erros.
5. Executar testes unitários, persistência, controller e os cenários do quickstart.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Constituição exige Kotlin, mas o repositório usa Java 21 | Preservar o código e o build reais; a feature não requer migração de linguagem | Migrar tudo para Kotlin aumentaria o escopo, quebraria o baseline e não resolveria uma necessidade de comentários |
