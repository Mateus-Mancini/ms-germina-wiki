# Feature Specification: API de Páginas Wiki

**Feature Branch**: `002-pages-api` (diretório da especificação; branch não criada)

**Created**: 2026-09-27

**Status**: Draft - decisões registradas; verificar metadados da coluna de versão antes do mapeamento e da implementação

**Input**: Solicitação para especificar uma API REST de páginas Wiki com controle otimista de versão.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar e consultar páginas (Priority: P1)

Como pessoa usuária da Wiki, quero criar uma página Markdown dentro de uma pasta e consultar os dados salvos para publicar e ler conteúdo.

**Why this priority**: Criar e consultar conteúdo são o fluxo mínimo que torna páginas úteis para a Wiki.

**Independent Test**: Com uma pasta existente e um principal autenticado, criar uma página, consultá-la por ID e verificar título, conteúdo Markdown, pasta, criador, datas e versão.

**Acceptance Scenarios**:

1. **Given** um principal autenticado, uma pasta existente e dados válidos, **When** uma página é criada, **Then** a API retorna `201 Created` com os dados da página e sua versão inicial.
2. **Given** conteúdo Markdown com formatação, links e quebras de linha, **When** a página é criada e consultada, **Then** o conteúdo retornado corresponde ao Markdown armazenado, sem renderização ou reescrita.
3. **Given** o ID de uma página existente, **When** a página é consultada, **Then** a API retorna `200 OK` com título, Markdown bruto, UUID da pasta, autor, datas e versão atual.
4. **Given** uma pasta inexistente, **When** a criação é solicitada, **Then** a API retorna `404 Not Found` e não persiste a página.
5. **Given** um ID de página inexistente, **When** a consulta é solicitada, **Then** a API retorna `404 Not Found`.

### User Story 2 - Listar e atualizar páginas (Priority: P1)

Como pessoa usuária da Wiki, quero encontrar páginas e atualizar seu conteúdo sem substituir uma alteração feita por outra pessoa a partir de uma versão mais recente.

**Why this priority**: Listagem e edição concorrente são necessárias para o uso compartilhado e confiável da Wiki.

**Independent Test**: Listar páginas e enviar duas atualizações com a mesma versão esperada; somente a primeira deve ser aplicada, e a segunda deve ser rejeitada sem alterar os dados mais recentes.

**Acceptance Scenarios**:

1. **Given** páginas existentes, **When** a coleção é consultada, **Then** a API retorna `200 OK` com a coleção; sem páginas, retorna coleção vazia.
2. **Given** uma página na versão esperada, **When** o cliente envia `If-Match` com seu ETag atual e a atualização é aceita, **Then** os campos enviados são salvos atomicamente, a versão aumenta exatamente uma vez e a resposta contém a nova versão e ETag.
3. **Given** uma página atualizada desde a leitura do cliente, **When** o cliente envia `If-Match` com um ETag obsoleto, **Then** a API retorna `412 Precondition Failed`, informa o ETag/versão corrente e deixa os dados mais recentes intactos.
4. **Given** um UUID de pasta inexistente informado na atualização, **When** a operação é solicitada, **Then** a API retorna `404 Not Found` sem alterar a página.
5. **Given** título ou conteúdo inválido conforme as regras do schema, **When** a atualização é solicitada, **Then** a API retorna `400 Bad Request` e não altera a página.

### User Story 3 - Excluir páginas (Priority: P2)

Como pessoa usuária da Wiki, quero excluir uma página quando permitido pelas referências existentes, sem deixar registros inconsistentes.

**Why this priority**: Exclusão completa o ciclo de vida do conteúdo, respeitando referências que podem existir no banco.

**Independent Test**: Excluir uma página existente sem referências que impeçam a operação, verificar `204 No Content`, e tentar excluir uma página referenciada por outra tabela para verificar o comportamento definido pelas FKs.

**Acceptance Scenarios**:

1. **Given** uma página existente cuja exclusão é permitida pelas FKs configuradas, **When** ela é excluída, **Then** a API retorna `204 No Content`.
2. **Given** uma página referenciada por registros que bloqueiam sua exclusão, **When** ela é excluída, **Then** a API retorna `409 Conflict` e não deixa referências inconsistentes.
3. **Given** um ID de página inexistente, **When** a exclusão é solicitada, **Then** a API retorna `404 Not Found`.

### Edge Cases

- Título ausente, nulo, vazio ou composto somente por espaços.
- Conteúdo Markdown ausente ou nulo é inválido; uma string vazia é válida e representa uma página sem conteúdo inicial.
- Conteúdo com caracteres Unicode, blocos de código, HTML, links, CRLF e linhas vazias deve ser persistido sem renderização ou normalização da API.
- UUID da pasta inexistente na criação ou atualização.
- Cabeçalho `If-Match` ausente (`428 Precondition Required`), malformado (`400 Bad Request`) ou diferente da versão persistida (`412 Precondition Failed`).
- Duas atualizações concorrentes usam a mesma versão esperada; no máximo uma pode modificar a página.
- Exclusão de página com referências de outras tabelas deve respeitar as ações de FK existentes, sem cascata implementada pela API.
- Nenhuma autenticação é implementada aqui; sem principal fornecido pela infraestrutura, o request deve ser recusado por ela antes de chegar ao caso de uso.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A API MUST permitir criar uma página contendo título, conteúdo Markdown bruto e UUID de uma pasta existente.
- **FR-002**: A API MUST derivar `createdBy` do principal autenticado, seguindo o padrão usado pela `folders-api`; não deve aceitar o criador informado pelo cliente nem implementar autenticação.
- **FR-003**: A API MUST rejeitar criação ou atualização que referencie pasta inexistente com `404 Not Found`, sem persistir alterações parciais.
- **FR-004**: A API MUST permitir consultar uma página por UUID, retornando `200 OK` ou `404 Not Found`.
- **FR-005**: A API MUST permitir listar páginas e retornar `200 OK`; coleção vazia é representada por array vazio. Paginação, filtros e ordenação não integram o contrato inicial.
- **FR-006**: A API MUST permitir atualizar os campos editáveis da página por DTO, exigindo que a atualização seja condicionada à versão que o cliente leu.
- **FR-007**: Toda atualização MUST exigir `If-Match` com o ETag da versão que o cliente leu. A comparação e a gravação MUST ser atômicas. Se a versão esperada for obsoleta, MUST retornar `412 Precondition Failed`, incluir a versão/ETag atual na resposta e não sobrescrever dados atuais. ETag ausente MUST resultar em `428 Precondition Required`; ETag inválido MUST resultar em `400 Bad Request`.
- **FR-008**: Cada atualização aceita MUST incrementar a versão exatamente uma vez; criação define a versão inicial conforme o mecanismo já existente no schema.
- **FR-009**: O conteúdo MUST ser tratado e armazenado como Markdown bruto; a API não deve renderizar, sanitizar, converter nem reformatar o conteúdo.
- **FR-010**: As respostas de consulta, listagem e atualização MUST expor a versão persistida atual, além dos campos necessários para o cliente: ID, título, conteúdo Markdown, pasta UUID, criador e datas de criação/atualização.
- **FR-011**: A API MUST permitir excluir uma página respeitando as ações de integridade referencial configuradas no PostgreSQL. A API não deve criar cascata, DDL ou estruturas de substituição; exclusão bloqueada por FK retorna `409 Conflict` e não deve deixar referências inconsistentes.
- **FR-012**: A feature MUST preservar o PostgreSQL existente e NÃO criar migrations, DDL ou tabelas de substituição. A tabela existente possui uma coluna numérica de versão; antes de mapear a entidade e implementar a persistência, devem ser confirmados no schema o nome, tipo, valor/default inicial e nulabilidade exatos para mapear o controle otimista sem alterações de banco.
- **FR-013**: A API MUST utilizar HTTP REST e DTOs de entrada e saída, com códigos coerentes para sucesso, validação, ausência de página/pasta, precondição HTTP e conflito de integridade.
- **FR-014**: Regras de negócio, incluindo validação de pasta existente e comparação de versão, MUST ficar na camada Service, seguindo Controller -> Service -> Repository.
- **FR-015**: A pasta da página MUST permanecer imutável após a criação. `folderId` não é campo de atualização.
- **FR-016**: A implementação MUST reutilizar os padrões Java 21/Spring Boot/PostgreSQL já adotados pelo projeto e manter o escopo restrito à pages-api; não deve alterar folders-api, auth-api, users-api, rbac-middleware ou outras APIs.

### API Contract (provisório onde marcado)

| Operação | Endpoint | Sucesso | Erros principais |
|----------|----------|---------|------------------|
| Criar página | `POST /api/pages` | `201 Created` | `400 Bad Request`, `404 Not Found` para pasta inexistente |
| Consultar página | `GET /api/pages/{id}` | `200 OK` | `400 Bad Request`, `404 Not Found` |
| Listar páginas | `GET /api/pages` | `200 OK` | `200 OK` com array vazio |
| Atualizar página | `PATCH /api/pages/{id}` | `200 OK` | `400 Bad Request`, `404 Not Found`, `412 Precondition Failed` para versão obsoleta, `428 Precondition Required` sem `If-Match` |
| Excluir página | `DELETE /api/pages/{id}` | `204 No Content` | `400 Bad Request`, `404 Not Found`, `409 Conflict` para FK impeditiva |

O DTO de criação contém `title`, `content` e `folderId`; `content` é obrigatório, mas pode ser a string vazia. `createdBy` vem do principal fornecido pelo host, tal como em `FolderController`. A resposta inclui `id`, `title`, `content`, `folderId`, `createdBy`, `createdAt`, `updatedAt` e `version`, e envia a versão como ETag HTTP. O DTO de atualização contém apenas `title` e/ou `content`; o cliente envia a versão esperada no cabeçalho `If-Match`. `folderId` não pode ser alterado.

### Key Entities *(include if feature involves data)*

- **Page**: Conteúdo da Wiki com UUID, título, Markdown bruto, pasta UUID, criador UUID, datas de criação/atualização e versão otimista persistida. O tipo e a disponibilidade do campo de versão dependem do schema existente.
- **Folder**: Entidade existente da `folders-api`, referenciada obrigatoriamente por UUID. Esta feature só valida/usa a referência; não altera a entidade nem sua API.
- **User**: Identidade existente fornecida pelo principal autenticado e armazenada como UUID de criador; autenticação e usuários permanecem fora do escopo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Cada operação de criação, consulta individual, listagem, atualização e exclusão retorna o status e os dados definidos pelo contrato.
- **SC-002**: Em todos os testes de aceitação com versões concorrentes iguais, no máximo uma atualização é aceita; a atualização obsoleta não altera os dados mais recentes e recebe a versão corrente.
- **SC-003**: O conteúdo Markdown submetido é retornado sem renderização ou transformação pela API, incluindo quebras de linha e caracteres especiais cobertos pelos testes.
- **SC-004**: Todas as tentativas com página/pasta inexistente retornam `404 Not Found` sem alterações parciais.
- **SC-005**: Uma exclusão bloqueada pelas FKs existentes retorna `409 Conflict` e não deixa referências inconsistentes.

## Assumptions

- O caminho base é `/api/pages`, alinhado ao padrão REST local da `folders-api`.
- O principal da infraestrutura expõe o UUID em `Principal.getName()`, como na implementação atual de `FolderController`; o endpoint de páginas apenas o lê e não implementa autenticação.
- A tabela `pages` já existe ou será disponibilizada pelo ambiente e pode ser mapeada sem DDL. Os nomes, limites e nulabilidade das colunas ainda não são conhecidos pelo repositório.
- O schema existente já fornece uma coluna numérica de versão compatível com comparação atômica; nome, tipo, nulabilidade e valor/default inicial devem ser verificados no PostgreSQL antes do mapeamento/implementação.
- Título é obrigatório e não pode ser composto apenas por espaços. `content` é obrigatório, mas pode ser uma string vazia. Limite máximo de título e conteúdo deve respeitar a capacidade do schema existente, sem introduzir truncamento silencioso.
- A API armazena Markdown como texto bruto. Renderização, sanitização de HTML e edição rica não fazem parte desta feature.
- A listagem inicial retorna todas as páginas; paginação, busca, ordenação, backlinks e comentários não fazem parte desta feature.
- A API segue as ações de exclusão das FKs existentes; não introduz cascata própria para páginas, comentários, links ou outras entidades.

## Resolved Clarifications

- **Persistência da versão**: existe uma coluna numérica de versão na tabela `pages`. Seu nome, tipo exato, nulabilidade e valor/default inicial serão confirmados diretamente no PostgreSQL antes do mapeamento/implementação; não criar DDL.
- **Contrato de concorrência HTTP**: usar ETag + `If-Match`; versão obsoleta retorna `412 Precondition Failed`, ETag ausente retorna `428 Precondition Required`, e ETag atual também é exposto na resposta.
- **Movimentação entre pastas**: `folderId` é imutável depois da criação.
