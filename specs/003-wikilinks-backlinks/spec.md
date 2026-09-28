# Feature Specification: WikiLinks e Backlinks

**Feature Branch**: `003-wikilinks-backlinks`

**Created**: 2026-09-28

**Status**: Draft - schema existente de `page_links` precisa ser inspecionado antes do mapeamento

**Input**: Feature para reconhecer WikiLinks no Markdown das páginas e consultar links de saída e backlinks.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manter WikiLinks a partir do conteúdo (Priority: P1)

Como pessoa autora de uma página Wiki, quero que referências `[[slug]]` no Markdown estabeleçam relações com as páginas existentes, sem alterar o texto que escrevi.

**Why this priority**: As relações direcionadas são a base comum para visualizar links de saída e backlinks.

**Independent Test**: Criar ou atualizar uma página de origem contendo `[[slug-alvo]]` para um slug existente e verificar que uma relação direcionada existe, enquanto o conteúdo permanece byte/character-for-character igual.

**Acceptance Scenarios**:

1. **Given** uma página com `[[slug-alvo]]` fora de contexto de código e uma página com esse slug, **When** o conteúdo da origem é criado ou atualizado, **Then** existe uma relação da origem para o alvo e o Markdown não é reescrito.
2. **Given** o mesmo `[[slug-alvo]]` aparece várias vezes no conteúdo da mesma origem, **When** as relações são sincronizadas, **Then** há uma única relação lógica para o par origem-alvo.
3. **Given** o conteúdo é atualizado e a última ocorrência de `[[slug-alvo]]` é removida, **When** a sincronização termina, **Then** a relação origem-alvo é removida.
4. **Given** `[[slug-alvo]]` está em código inline ou bloco cercado de código Markdown, **When** o conteúdo é analisado, **Then** essa ocorrência não cria uma relação.
5. **Given** uma referência `[[slug-inexistente]]`, **When** o conteúdo é salvo, **Then** o Markdown é aceito e preservado, mas nenhuma relação é criada enquanto não houver página-alvo.
6. **Given** uma página é criada depois de já existirem conteúdos com `[[seu-slug]]`, **When** o novo slug passa a existir, **Then** esses conteúdos são reavaliados e as relações resolvidas são criadas automaticamente.
7. **Given** uma página contém `[[seu-próprio-slug]]`, **When** as relações são sincronizadas, **Then** uma relação source=target é criada e aparece nas consultas de outgoing e backlinks.
7. **Given** uma página contém `[[seu-próprio-slug]]`, **When** as relações são sincronizadas, **Then** é criada uma relação source=target, permitida nas consultas outgoing e backlinks.

### User Story 2 - Consultar WikiLinks de saída (Priority: P1)

Como pessoa usuária da Wiki, quero consultar as páginas apontadas por uma página para navegar pelos seus links.

**Why this priority**: A lista de saída expõe as relações que foram reconhecidas no conteúdo.

**Independent Test**: Consultar WikiLinks de uma página com zero, um ou vários destinos e conferir que cada página-alvo aparece uma vez.

**Acceptance Scenarios**:

1. **Given** uma página existente com relações para destinos existentes, **When** `GET /api/pages/{pageId}/wikilinks` é chamado, **Then** a API retorna `200 OK` com resumos dos alvos, uma vez por alvo.
2. **Given** uma página existente sem relações de saída resolvidas, **When** os WikiLinks são consultados, **Then** a API retorna `200 OK` com array vazio.
3. **Given** um `pageId` inexistente, **When** os WikiLinks são consultados, **Then** a API retorna `404 Not Found`.

### User Story 3 - Consultar backlinks (Priority: P1)

Como pessoa usuária da Wiki, quero consultar quais páginas apontam para uma página para descobrir suas referências de entrada.

**Why this priority**: Backlinks completam a navegação bidirecional da rede de páginas sem duplicar o conteúdo-fonte.

**Independent Test**: Consultar backlinks de uma página-alvo com zero, um ou vários emissores e conferir que cada origem aparece uma vez.

**Acceptance Scenarios**:

1. **Given** páginas de origem com WikiLinks resolvidos para uma página-alvo existente, **When** `GET /api/pages/{pageId}/backlinks` é chamado, **Then** a API retorna `200 OK` com resumos das origens, uma vez por origem.
2. **Given** uma página existente sem backlinks resolvidos, **When** os backlinks são consultados, **Then** a API retorna `200 OK` com array vazio.
3. **Given** um `pageId` inexistente, **When** os backlinks são consultados, **Then** a API retorna `404 Not Found`.

### Edge Cases

- Sintaxe reconhecida é somente `[[slug]]`; aliases, fragmentos e formatos diferentes não são WikiLinks nesta feature.
- Resolução usa o slug único exato da página, sem gerar, normalizar ou alterar slugs.
- Tokens dentro de código inline e blocos cercados de código não criam relações.
- Uma referência não resolvida permanece no Markdown bruto e não aparece em WikiLinks/backlinks até existir uma página com o slug.
- Criar uma página-alvo reavalia referências existentes ao seu slug; se a página for removida e outra for criada posteriormente com o mesmo slug, as referências ainda presentes podem ser resolvidas novamente.
- Ocorrências repetidas da mesma origem para o mesmo destino resultam em uma relação lógica, não em relações duplicadas.
- Ao remover uma referência do conteúdo, remover a relação somente quando nenhuma outra ocorrência do mesmo destino permanecer.
- Exclusão da página de origem remove relações de saída por `ON DELETE CASCADE`; exclusão da página-alvo define `target_page_id` como nulo por `ON DELETE SET NULL`. A API não implementa cascades.
- Consultar links/backlinks com UUID malformado retorna `400 Bad Request`; com UUID válido mas página inexistente retorna `404 Not Found`.
- Sincronização de relações ocorre na mesma transação da gravação do conteúdo da página, respeitando eventual falha de optimistic locking da `pages-api`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST reconhecer a sintaxe literal `[[slug]]` em conteúdo Markdown de páginas.
- **FR-002**: O sistema MUST ignorar ocorrências da sintaxe em código inline e blocos cercados de código Markdown, sem modificar o Markdown armazenado.
- **FR-003**: O sistema MUST resolver o slug pelo valor exato contra o slug único existente em `pages`; não deve converter, derivar ou normalizar o valor.
- **FR-004**: Na criação ou atualização do conteúdo de uma página, o sistema MUST sincronizar as relações de saída a partir das referências resolvidas presentes no conteúdo, dentro da transação da gravação da página.
- **FR-005**: Referências com slug sem página-alvo MUST permanecer no Markdown e não gerar uma relação enquanto o alvo não existir. A criação de uma página MUST reavaliar referências pendentes ao slug recém-criado e criar as relações correspondentes.
- **FR-006**: Deve existir no máximo uma relação lógica por par `source_page_id`/`target_page_id`, ainda que o token apareça repetidamente no Markdown.
- **FR-006a**: Auto-links source=target MUST ser aceitos quando o WikiLink resolve para a própria página.
- **FR-006a**: Auto-links source=target MUST ser aceitos quando o WikiLink resolve para a própria página.
- **FR-007**: Atualizar o conteúdo removendo todas as ocorrências resolvidas de um destino MUST remover a relação correspondente; manter qualquer ocorrência MUST manter uma relação lógica.
- **FR-008**: A API MUST expor `GET /api/pages/{pageId}/wikilinks`, retornando os destinos resolvidos, e `GET /api/pages/{pageId}/backlinks`, retornando as origens que apontam para o alvo.
- **FR-009**: Cada endpoint de consulta MUST retornar `200 OK` e uma coleção vazia quando não houver relações; MUST retornar `404 Not Found` quando a página indicada não existir e `400 Bad Request` para UUID malformado.
- **FR-010**: Resumos de link/backlink MUST identificar a página relacionada por `id`, `title` e `slug`. Cada página relacionada MUST aparecer uma única vez na coleção.
- **FR-011**: A feature MUST reutilizar `Page`, o slug único e a tabela PostgreSQL existente `page_links`, conhecida por relacionar `source_page_id` e `target_page_id`. Nenhuma tabela, coluna, constraint ou migration será criada automaticamente. A chave primária, constraints de unicidade e demais colunas da tabela devem ser inspecionadas antes de mapear a persistência.
- **FR-012**: A exclusão de páginas MUST respeitar `page_links.source_page_id ON DELETE CASCADE` e `page_links.target_page_id ON DELETE SET NULL`; a feature não deve implementar cascades em código.
- **FR-013**: A feature MUST seguir Controller -> Service -> Repository, usando DTOs nas respostas HTTP e regras de parsing/sincronização na camada Service. A autenticação permanece na infraestrutura existente e não será implementada aqui.
- **FR-014**: A feature MUST manter Java 21, Spring Boot 4.1.1, Spring Data JPA, PostgreSQL e dependências já existentes; qualquer incompatibilidade do schema deve ser documentada antes de propor alteração, sem criar DDL/migration.
- **FR-015**: A feature MUST limitar-se a WikiLinks e backlinks; não deve alterar endpoints/contratos de CRUD de `pages-api`, `folders-api`, autenticação, comentários, imagens, tags ou outras APIs além da integração transacional necessária ao conteúdo da página.

### API Contract

| Operação | Endpoint | Sucesso | Erros principais |
|----------|----------|---------|------------------|
| WikiLinks de saída | `GET /api/pages/{pageId}/wikilinks` | `200 OK` com array de resumos | `400 Bad Request`, `404 Not Found` |
| Backlinks | `GET /api/pages/{pageId}/backlinks` | `200 OK` com array de resumos | `400 Bad Request`, `404 Not Found` |

Não há endpoints para criar/remover relações manualmente: links são derivados do conteúdo da página e sincronizados quando esse conteúdo é gravado. O resumo de página relacionada contém `id`, `title` e `slug`; ordem de resultados não é garantida por este contrato.

### Key Entities *(include if feature involves data)*

- **PageLink**: Relação direcionada entre `source_page_id` e `target_page_id` na tabela PostgreSQL existente `page_links`. WikiLink de origem aponta para destino; backlink de um destino é a consulta inversa. `source_page_id` tem CASCADE na exclusão da origem e `target_page_id` recebe SET NULL na exclusão do destino. Chave primária, constraints e colunas adicionais não são inferidas sem introspecção do schema.
- **Page**: Entidade existente de `pages`, resolvida pelo `slug` único. O conteúdo Markdown permanece a fonte textual e não é reformatado pela sincronização.
- **LinkedPageSummary**: DTO de leitura contendo `id`, `title` e `slug` da página relacionada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Para cada página salva, a relação de saída contém exatamente um vínculo para cada slug-alvo distinto existente fora de contextos de código e nenhum vínculo para slug inexistente.
- **SC-002**: Criar uma página que satisfaça um slug referenciado anteriormente resolve automaticamente as referências pendentes presentes em conteúdos existentes.
- **SC-003**: Consultas de outgoing e backlinks retornam cada página relacionada uma única vez e retornam array vazio quando não há relação.
- **SC-004**: Remover do conteúdo a última ocorrência de um WikiLink remove a relação sem alterar outros vínculos nem o conteúdo recebido.
- **SC-005**: Alterações na relação preservam os efeitos de exclusão CASCADE/SET NULL definidos no PostgreSQL e não criam tabelas ou mudanças de schema.

## Assumptions

- `Page.slug` existe e é único, conforme schema e contrato da `002-pages-api`.
- O schema existente contém `page_links.source_page_id` e `page_links.target_page_id` com as FKs documentadas em `specs/002-pages-api/data-model.md`; outros detalhes físicos devem ser confirmados no PostgreSQL.
- O trabalho depende do fluxo de gravação de conteúdo da `pages-api`; a sincronização será integrada à criação e às atualizações de conteúdo já previstas nessa API, sem criar outro endpoint de escrita de páginas.
- Backlinks são calculados por inversão das relações persistidas; não são armazenados como uma segunda cópia.
- Os endpoints são de leitura; autenticação é fornecida pela infraestrutura existente.

## Resolved Clarifications

- **Sintaxe/destino**: reconhecer `[[slug]]` e resolver por slug exato; não há alias nem texto alternativo.
- **Persistência/duplicidade**: derivar relações do conteúdo, uma relação lógica por par origem-destino, removendo a relação quando a última ocorrência some.
- **Endpoints/links quebrados**: expor GET de WikiLinks e GET de backlinks; slug inexistente não gera relação. Quando uma página com esse slug for criada, reavaliar automaticamente os conteúdos existentes.
- **Contexto Markdown**: ignorar tokens dentro de código inline e blocos cercados de código.
- **Auto-links**: uma página pode apontar para si mesma; a relação source=target é válida.
- **Auto-links**: uma página pode apontar para si mesma; a relação source=target é válida.
