# Feature Specification: API de Páginas Wiki

**Feature Branch**: `002-pages-api` (diretório da especificação; branch não criada)

**Created**: 2026-09-27

**Status**: Draft - alinhada ao schema PostgreSQL fornecido

**Input**: Solicitação para especificar uma API REST de páginas Wiki com controle otimista de versão.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar e consultar páginas (Priority: P1)

Como pessoa usuária da Wiki, quero criar uma página Markdown dentro de uma pasta e consultar os dados salvos para publicar e ler conteúdo.

**Why this priority**: Criar e consultar conteúdo são o fluxo mínimo que torna páginas úteis para a Wiki.

**Independent Test**: Com uma pasta existente e um principal autenticado, criar uma página, consultá-la por ID e verificar título, conteúdo Markdown, pasta, criador, datas e versão.

**Acceptance Scenarios**:

1. **Given** um principal autenticado, uma pasta existente e `title`, `slug` e `content` válidos, **When** uma página é criada, **Then** a API retorna `201 Created` com os dados da página, `slug` e versão inicial `1`.
2. **Given** conteúdo Markdown com formatação, links e quebras de linha, **When** a página é criada e consultada, **Then** o conteúdo retornado corresponde ao Markdown armazenado, sem renderização ou reescrita.
3. **Given** o ID de uma página existente, **When** a página é consultada, **Then** a API retorna `200 OK` com título, slug, Markdown bruto, UUID da pasta (possivelmente nulo se a pasta foi excluída), `createdBy`, `updatedBy`, datas e versão atual.
4. **Given** uma pasta inexistente, **When** a criação é solicitada, **Then** a API retorna `404 Not Found` e não persiste a página.
5. **Given** um slug já usado, **When** a criação é solicitada, **Then** a API retorna `409 Conflict` e não cria outra página com o mesmo slug.
6. **Given** um ID de página inexistente, **When** a consulta é solicitada, **Then** a API retorna `404 Not Found`.

### User Story 2 - Listar e atualizar páginas (Priority: P1)

Como pessoa usuária da Wiki, quero encontrar páginas e atualizar seu conteúdo sem substituir uma alteração feita por outra pessoa a partir de uma versão mais recente.

**Why this priority**: Listagem e edição concorrente são necessárias para o uso compartilhado e confiável da Wiki.

**Independent Test**: Listar páginas e enviar duas atualizações com a mesma versão esperada; somente a primeira deve ser aplicada, e a segunda deve ser rejeitada sem alterar os dados mais recentes.

**Acceptance Scenarios**:

1. **Given** páginas existentes, **When** a coleção é consultada, **Then** a API retorna `200 OK` com a coleção; sem páginas, retorna coleção vazia.
2. **Given** uma página na versão esperada, **When** o cliente envia `If-Match` com seu ETag atual e a atualização é aceita, **Then** os campos enviados são salvos atomicamente, a versão aumenta exatamente uma vez e a resposta contém a nova versão e ETag.
3. **Given** uma página atualizada desde a leitura do cliente, **When** o cliente envia `If-Match` com um ETag obsoleto, **Then** a API retorna `412 Precondition Failed`, informa o ETag/versão corrente e deixa os dados mais recentes intactos.
4. **Given** um PATCH que inclua `folderId` ou `slug`, **When** a atualização é solicitada, **Then** a API rejeita o campo não editável e não altera a página.
5. **Given** título ou conteúdo inválido conforme as regras do schema, **When** a atualização é solicitada, **Then** a API retorna `400 Bad Request` e não altera a página.

### User Story 3 - Excluir páginas (Priority: P2)

Como pessoa usuária da Wiki, quero excluir uma página quando permitido pelas referências existentes, sem deixar registros inconsistentes.

**Why this priority**: Exclusão completa o ciclo de vida do conteúdo, respeitando referências que podem existir no banco.

**Independent Test**: Excluir uma página com imagens, comentários, tags e links relacionados; verificar CASCADE para imagens/comentários/tags/links de origem, SET NULL para links de destino e `204 No Content`.

**Acceptance Scenarios**:

1. **Given** uma página existente cuja exclusão é permitida pelas FKs configuradas, **When** ela é excluída, **Then** a API retorna `204 No Content`.
2. **Given** uma página referenciada por imagens, comentários, tags ou links de origem, **When** ela é excluída, **Then** o PostgreSQL remove as referências configuradas com CASCADE; links que apontavam para ela como destino permanecem com `target_page_id` nulo e a API retorna `204 No Content`.
3. **Given** uma página cuja exclusão seja rejeitada por alguma FK real além das ações fornecidas, **When** ela é excluída, **Then** a API retorna `409 Conflict` sem implementar cascata própria.
4. **Given** uma pasta referenciada por páginas, **When** a pasta é excluída conforme seu contrato, **Then** o PostgreSQL define `folder_id` das páginas como nulo e as páginas permanecem consultáveis.
5. **Given** um ID de página inexistente, **When** a exclusão é solicitada, **Then** a API retorna `404 Not Found`.

### Edge Cases

- Título ausente, nulo, vazio ou composto somente por espaços.
- Conteúdo Markdown ausente ou nulo é inválido; uma string vazia é válida e representa uma página sem conteúdo inicial.
- Conteúdo com caracteres Unicode, blocos de código, HTML, links, CRLF e linhas vazias deve ser persistido sem renderização ou normalização da API.
- UUID da pasta inexistente na criação; `folderId` não pode ser alterado pelo PATCH.
- Exclusão da pasta da página pode definir `folderId` como nulo pelo FK `ON DELETE SET NULL`.
- Slug ausente, vazio ou maior que 300 caracteres; slug já existente.
- Cabeçalho `If-Match` ausente (`428 Precondition Required`), malformado (`400 Bad Request`) ou diferente da versão persistida (`412 Precondition Failed`).
- Duas atualizações concorrentes usam a mesma versão esperada; no máximo uma pode modificar a página.
- Exclusão de página deve respeitar os CASCADE/SET NULL existentes; as referências listadas não são motivo esperado para `409`.
- Nenhuma autenticação é implementada aqui; sem principal fornecido pela infraestrutura, o request deve ser recusado por ela antes de chegar ao caso de uso.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A API MUST permitir criar uma página contendo título, slug fornecido pelo cliente, conteúdo Markdown bruto e UUID de uma pasta existente.
- **FR-002**: A API MUST derivar `createdBy` do principal autenticado, seguindo o padrão usado pela `folders-api`; não deve aceitar o criador informado pelo cliente nem implementar autenticação.
- **FR-003**: A API MUST rejeitar criação que referencie pasta inexistente com `404 Not Found`, sem persistir alterações parciais. `folderId` é imutável por PATCH; se a pasta for posteriormente excluída, o FK existente define `folder_id` como nulo.
- **FR-004**: A API MUST permitir consultar uma página por UUID, retornando `200 OK` ou `404 Not Found`.
- **FR-005**: A API MUST permitir listar páginas e retornar `200 OK`; coleção vazia é representada por array vazio. Paginação, filtros e ordenação não integram o contrato inicial.
- **FR-006**: A API MUST permitir atualizar os campos editáveis da página por DTO, exigindo que a atualização seja condicionada à versão que o cliente leu.
- **FR-007**: Toda atualização MUST exigir `If-Match` com o ETag da versão que o cliente leu. A comparação e a gravação MUST ser atômicas. Se a versão esperada for obsoleta, MUST retornar `412 Precondition Failed`, incluir a versão/ETag atual na resposta e não sobrescrever dados atuais. ETag ausente MUST resultar em `428 Precondition Required`; ETag inválido MUST resultar em `400 Bad Request`.
- **FR-008**: A versão persistida MUST ser `INTEGER NOT NULL DEFAULT 1` com `version > 0`; criação inicia em `1` e cada atualização aceita incrementa a versão exatamente uma vez sob controle otimista.
- **FR-009**: O conteúdo MUST ser tratado e armazenado como Markdown bruto; a API não deve renderizar, sanitizar, converter nem reformatar o conteúdo.
- **FR-010**: As respostas de consulta, listagem e atualização MUST expor ID, título, slug, conteúdo Markdown, `folderId` (nulo se o FK o definir nulo), `createdBy`, `updatedBy` (nulo quando não definido ou removido), datas e versão persistida atual.
- **FR-011**: A API MUST excluir páginas respeitando as ações de integridade referencial do PostgreSQL: CASCADE para `page_images`, `comments`, `page_tags` e links de origem; SET NULL para links de destino. A API não deve implementar essas cascatas. Uma violação FK real ainda retorna `409 Conflict`, mas não se presume para as relações com CASCADE/SET NULL fornecidas.
- **FR-012**: A feature MUST preservar o PostgreSQL existente e NÃO criar migrations, DDL ou tabelas de substituição. A versão existente é `INTEGER NOT NULL DEFAULT 1 CHECK (version > 0)`; criação inicia em `1` e cada atualização aceita incrementa exatamente uma vez sob controle otimista.
- **FR-013**: A API MUST utilizar HTTP REST e DTOs de entrada e saída, com códigos coerentes para sucesso, validação, ausência de página/pasta, precondição HTTP e conflito de integridade.
- **FR-014**: Regras de negócio, incluindo validação de pasta existente, slug único e comparação de versão, MUST ficar na camada Service, seguindo Controller -> Service -> Repository.
- **FR-015**: A pasta da página MUST permanecer imutável após a criação. `folderId` não é campo de atualização.
- **FR-016**: A implementação MUST reutilizar os padrões Java 21/Spring Boot/PostgreSQL já adotados pelo projeto e manter o escopo restrito à pages-api; não deve alterar folders-api, auth-api, users-api, rbac-middleware ou outras APIs.
- **FR-017**: `slug` MUST ser recebido obrigatoriamente do cliente na criação, conter de 1 a 300 caracteres e ser único. A API MUST persistir e retornar o valor exatamente como recebido, sem gerar, derivar do título ou normalizar; PATCH MUST NOT aceitar slug. Slug duplicado MUST retornar `409 Conflict` usando a constraint UNIQUE existente.

### API Contract (provisório onde marcado)

| Operação | Endpoint | Sucesso | Erros principais |
|----------|----------|---------|------------------|
| Criar página | `POST /api/pages` | `201 Created` | `400 Bad Request`, `404 Not Found` para pasta inexistente, `409 Conflict` para slug duplicado |
| Consultar página | `GET /api/pages/{id}` | `200 OK` | `400 Bad Request`, `404 Not Found` |
| Listar páginas | `GET /api/pages` | `200 OK` | `200 OK` com array vazio |
| Atualizar página | `PATCH /api/pages/{id}` | `200 OK` | `400 Bad Request`, `404 Not Found`, `412 Precondition Failed` para versão obsoleta, `428 Precondition Required` sem `If-Match` |
| Excluir página | `DELETE /api/pages/{id}` | `204 No Content` | `400 Bad Request`, `404 Not Found`, `409 Conflict` para FK impeditiva |

O DTO de criação contém `title`, `slug`, `content` e `folderId`; `slug` é obrigatório (1-300 caracteres) e vem do cliente sem normalização, `content` é obrigatório mas pode ser a string vazia, e `folderId` deve identificar uma pasta existente. `createdBy` vem do principal fornecido pelo host, tal como em `FolderController`. A resposta inclui `id`, `title`, `slug`, `content`, `folderId` (nullable após `ON DELETE SET NULL`), `createdBy`, `updatedBy` (nullable), `createdAt`, `updatedAt` e `version`, e envia a versão como ETag HTTP. O DTO de atualização contém apenas `title` e/ou `content`; o cliente envia a versão esperada no cabeçalho `If-Match`. `slug` e `folderId` não podem ser alterados.

### Key Entities *(include if feature involves data)*

- **Page**: Conteúdo da Wiki com UUID, título, slug único fornecido pelo cliente, Markdown bruto, `folderId` opcional após remoção de pasta, `createdBy`, `updatedBy` opcional, datas e versão otimista `INTEGER` iniciada em `1`.
- **Folder**: Entidade existente da `folders-api`; deve existir ao criar a página. A FK nullable com `ON DELETE SET NULL` pode tornar `folderId` nulo posteriormente. Esta feature não altera a entidade nem sua API.
- **User**: Identidade existente fornecida pelo principal autenticado e armazenada como UUID de criador; autenticação e usuários permanecem fora do escopo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Cada operação de criação, consulta individual, listagem, atualização e exclusão retorna o status e os dados definidos pelo contrato, incluindo slug e updatedBy nas respostas.
- **SC-002**: Em todos os testes de aceitação com versões concorrentes iguais, no máximo uma atualização é aceita; a atualização obsoleta não altera os dados mais recentes e recebe a versão corrente.
- **SC-003**: O conteúdo Markdown submetido é retornado sem renderização ou transformação pela API, incluindo quebras de linha e caracteres especiais cobertos pelos testes.
- **SC-004**: Todas as tentativas com página/pasta inexistente retornam `404 Not Found` sem alterações parciais.
- **SC-005**: Exclusões aplicam os CASCADE/SET NULL definidos no PostgreSQL sem lógica de cascata na API; `409 Conflict` é retornado somente para uma violação FK real.

## Assumptions

- O caminho base é `/api/pages`, alinhado ao padrão REST local da `folders-api`.
- O principal da infraestrutura expõe o UUID em `Principal.getName()`, como na implementação atual de `FolderController`; o endpoint de páginas apenas o lê e não implementa autenticação.
- A tabela `pages` existente segue o schema fornecido; não é criada nem alterada por esta feature.
- `version` é INTEGER, começa em 1, é incrementada pelo controle otimista e deve continuar positiva.
- Título é obrigatório, não branco e tem no máximo 255 caracteres. `slug` é obrigatório, vem do cliente, tem no máximo 300 caracteres e é único; conteúdo é obrigatório, pode ser vazio e não tem limite definido pelo schema.
- A API armazena Markdown como texto bruto. Renderização, sanitização de HTML e edição rica não fazem parte desta feature.
- A listagem inicial retorna todas as páginas; paginação, busca, ordenação, backlinks e comentários não fazem parte desta feature.
- A API segue as ações de exclusão das FKs existentes; não introduz cascata própria para páginas, comentários, links ou outras entidades.

## Resolved Clarifications

- **Persistência da versão**: a coluna é `version INTEGER NOT NULL DEFAULT 1` com `CHECK (version > 0)`; JPA controla optimistic locking sem incremento manual ou DDL.
- **Contrato de concorrência HTTP**: usar ETag + `If-Match`; versão obsoleta retorna `412 Precondition Failed`, ETag ausente retorna `428 Precondition Required`, e ETag atual também é exposto na resposta.
- **Movimentação entre pastas**: `folderId` é imutável depois da criação.
- **Slug**: cliente fornece slug obrigatório de 1-300 caracteres; a API não gera, deriva ou normaliza; unicidade já é garantida pelo PostgreSQL e duplicidade retorna `409`.
- **`updatedBy`**: a resposta expõe o valor nullable armazenado. A spec não define quem o popula, então esta feature não inventa essa regra.
