# Feature Specification: API de Pastas da Wiki

**Feature Branch**: `001-folders-api` (diretório da especificação; branch não criada)

**Created**: 2026-09-27

**Status**: Draft - pronta para planejamento

**Owner**: Camilla

**Input**: Solicitação para especificar a API REST `folders-api` do GerminaWiki, sem implementar código.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar e consultar pastas (Priority: P1)

Como pessoa usuária da Wiki, quero criar pastas raiz ou subordinadas e consultar pastas existentes para organizar o conteúdo.

**Why this priority**: Criação e consulta são a base para qualquer organização e consumo da hierarquia.

**Independent Test**: Criar uma pasta raiz e uma pasta filha, consultar cada uma por ID e verificar os dados e a relação persistida.

**Acceptance Scenarios**:

1. **Given** que há um principal autenticado, os dados obrigatórios são válidos e a pasta pai existe quando informada, **When** uma pasta é criada, **Then** a API retorna `201 Created` e a pasta pode ser consultada por ID.
2. **Given** um ID de pasta existente, **When** a pasta é consultada, **Then** a API retorna `200 OK` com seus dados, inclusive `parentFolderId` quando houver.
3. **Given** um ID inexistente, **When** a pasta é consultada, **Then** a API retorna `404 Not Found`.
4. **Given** um `parentFolderId` inexistente, **When** a criação é solicitada, **Then** a API rejeita a referência sem persistir a pasta.

### User Story 2 - Listar, atualizar e excluir pastas (Priority: P1)

Como pessoa usuária da Wiki, quero listar pastas, alterar seu nome ou posição hierárquica e removê-las quando permitido pelas referências existentes.

**Why this priority**: A manutenção do ciclo de vida das pastas é necessária para manter a organização atualizada.

**Independent Test**: Listar pastas, alterar nome e pai de uma pasta, conferir os novos dados e excluir uma pasta sem referências impeditivas.

**Acceptance Scenarios**:

1. **Given** pastas existentes, **When** a coleção é listada, **Then** a API retorna `200 OK` com as pastas.
2. **Given** uma pasta existente, **When** um ou ambos os campos atualizáveis são enviados, **Then** somente os campos enviados são alterados e a API retorna `200 OK`.
3. **Given** uma pasta com referências que impedem sua exclusão segundo as restrições vigentes, **When** ela é excluída, **Then** a operação não deixa referências inconsistentes e a API retorna conflito.
4. **Given** uma pasta excluível, **When** ela é excluída, **Then** a API retorna `204 No Content`.

### User Story 3 - Consultar a árvore de pastas (Priority: P2)

Como pessoa usuária da Wiki, quero consultar todas as pastas como uma árvore para compreender sua hierarquia em uma única resposta.

**Why this priority**: A representação hierárquica torna a organização navegável sem exigir que o cliente reconstrua relações a partir de várias consultas.

**Independent Test**: Criar raízes, filhos e netos e verificar que a árvore retorna cada pasta uma vez, sob o pai correto, incluindo uma lista vazia de filhos quando aplicável.

**Acceptance Scenarios**:

1. **Given** uma estrutura com uma ou mais raízes e descendentes, **When** a árvore é consultada, **Then** a API retorna `200 OK` com as raízes e seus descendentes aninhados.
2. **Given** nenhuma pasta cadastrada, **When** a árvore é consultada, **Then** a API retorna `200 OK` com uma coleção vazia.
3. **Given** uma tentativa de atribuir uma pasta a si própria ou a um descendente, **When** a alteração é solicitada, **Then** a API retorna `409 Conflict` e mantém a estrutura anterior.

### Edge Cases

- Nome ausente, nulo, vazio, composto somente por espaços ou maior que 150 caracteres.
- ID da pasta consultada, atualizada ou excluída não encontrado.
- Pasta pai inexistente, igual à própria pasta ou pertencente à cadeia de descendentes da pasta movida.
- Atualização sem campos: rejeitar como requisição inválida; `parentFolderId: null` significa tornar a pasta raiz, enquanto campo omitido mantém seu valor atual.
- Exclusão de pasta referenciada por pastas filhas ou por outras entidades; o resultado precisa respeitar as restrições efetivamente configuradas no banco.
- Atualizações concorrentes de relações hierárquicas não podem deixar ciclos persistidos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A API MUST permitir criar uma pasta raiz ou filha, receber os dados por DTO e retornar a representação criada.
- **FR-002**: A API MUST exigir nome não nulo, não vazio após remoção de espaços nas extremidades e com no máximo 150 caracteres; entradas inválidas MUST resultar em `400 Bad Request`.
- **FR-003**: A API MUST validar a existência da pasta pai informada e preservar a referência opcional `parentFolderId`; referência inexistente MUST ser rejeitada sem criar ou alterar dados.
- **FR-004**: A API MUST impedir que uma pasta seja seu próprio pai ou descendente de si mesma. Tentativas MUST resultar em `409 Conflict` e não podem alterar os dados persistidos.
- **FR-005**: A API MUST permitir consultar uma pasta por UUID, retornando `200 OK` quando encontrada e `404 Not Found` quando ausente.
- **FR-006**: A API MUST permitir listar as pastas existentes e retornar `200 OK`. Paginação, filtros e ordenação não fazem parte do contrato inicial desta especificação.
- **FR-007**: A API MUST permitir atualização parcial do nome e/ou da pasta pai. Campo omitido mantém o valor existente; `parentFolderId: null` remove a relação com pai. Requisições sem campos atualizáveis ou com valores inválidos MUST resultar em `400 Bad Request`.
- **FR-008**: A API MUST permitir excluir uma pasta respeitando exclusivamente as ações de integridade referencial configuradas no PostgreSQL. A API MUST NOT implementar cascata ou reparentamento próprios; quando o banco impedir a operação, deve preservar os dados e responder `409 Conflict`.
- **FR-009**: A API MUST oferecer consulta da árvore completa como uma coleção de raízes; cada nó MUST expor seus filhos em uma propriedade `children`, incluindo coleção vazia quando não houver filhos. Cada pasta existente MUST aparecer exatamente uma vez sob o pai correspondente.
- **FR-010**: Os endpoints MUST usar DTOs de entrada e saída, Bean Validation quando aplicável e os códigos HTTP definidos no contrato desta especificação.
- **FR-011**: A API MUST derivar `createdBy` do principal autenticado, nunca de um UUID escolhido pelo cliente, e preservar `createdBy`, `createdAt` e `updatedAt` nas respostas. A identidade autenticada deve estar disponível por infraestrutura existente; integrar ou implementar auth-api e rbac-middleware está fora do escopo.
- **FR-012**: Regras de negócio, incluindo validação da existência do pai, detecção de ciclos e decisões de exclusão, MUST ficar na camada Service. Controller MUST limitar-se à comunicação HTTP e Repository ao acesso/persistência.
- **FR-013**: A implementação MUST reutilizar a tabela `folders` existente, com os campos e referências descritos no modelo fornecido. MUST NOT criar tabelas ou estruturas redundantes se o esquema existente atender ao contrato.
- **FR-014**: A feature MUST utilizar Java 21, Spring Boot, Spring Web, Spring Data JPA e PostgreSQL, no fluxo Controller -> Service -> Repository, conforme a Constitution vigente.
- **FR-015**: A feature MUST limitar-se à `folders-api`; auth-api, users-api, rbac-middleware, pages-api, wikilinks-backlinks, comments-api, images-api e search-api não devem ser implementadas nem alteradas.

### API Contract

| Operação | Endpoint | Sucesso | Erros principais |
|----------|----------|---------|------------------|
| Criar pasta | `POST /api/folders` | `201 Created` | `400 Bad Request`, `404 Not Found` para pai inexistente, `409 Conflict` para restrição de integridade |
| Consultar pasta | `GET /api/folders/{id}` | `200 OK` | `404 Not Found` |
| Listar pastas | `GET /api/folders` | `200 OK` | `400 Bad Request` para parâmetros inválidos, se houver |
| Atualizar pasta | `PATCH /api/folders/{id}` | `200 OK` | `400 Bad Request`, `404 Not Found`, `409 Conflict` |
| Excluir pasta | `DELETE /api/folders/{id}` | `204 No Content` | `404 Not Found`, `409 Conflict` |
| Consultar árvore | `GET /api/folders/tree` | `200 OK` | `200 OK` com coleção vazia se não houver pastas |

O DTO de entrada de criação contém `name` e `parentFolderId` opcional; `createdBy` é obtido do principal autenticado e não é aceito como campo de entrada. O DTO de atualização contém `name` e/ou `parentFolderId`; a distinção entre campo omitido e `parentFolderId: null` deve ser preservada. A representação de pasta contém `id`, `name`, `parentFolderId`, `createdBy`, `createdAt` e `updatedAt`; nós de árvore incluem também `children`. Um formato específico de corpo para erros não é definido aqui.

### Key Entities *(include if feature involves data)*

- **Folder**: Pasta da Wiki, identificada por UUID, com nome obrigatório de até 150 caracteres, criador, datas de criação/atualização e referência opcional à pasta pai. A relação entre pastas forma uma hierarquia sem ciclos.
- **User**: Usuário existente identificado por UUID, referenciado por `Folder.createdBy`; a funcionalidade não cria nem altera usuários.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Todos os cenários de sucesso de criação, consulta individual, listagem, atualização, exclusão e consulta da árvore recebem o código HTTP e a representação especificados.
- **SC-002**: 100% das entradas com nome inválido e das tentativas de autorreferência/ciclo nos testes de aceitação são rejeitadas sem persistir alterações inválidas.
- **SC-003**: Para qualquer estrutura válida coberta pelos testes de aceitação, a árvore contém cada pasta uma única vez e reproduz corretamente as relações pai-filho.
- **SC-004**: Tentativas de excluir pastas cuja exclusão seja bloqueada por referências do banco retornam `409 Conflict` e não deixam referências inconsistentes.

## Assumptions

- A tabela `folders` e suas chaves/restrições já existem no PostgreSQL de destino; a especificação não pressupõe nem cria uma migração para uma estrutura nova.
- O principal autenticado e seu UUID estão disponíveis por infraestrutura existente quando esta API for implementada; nenhuma alteração em auth-api ou rbac-middleware está incluída.
- A API não adiciona política própria de cascata, promoção de filhos ou reparentamento; as ações de exclusão são as já configuradas nas chaves estrangeiras do PostgreSQL.
- O limite de nome de 150 caracteres é contado conforme a validação do banco e da API; nomes somente com espaços são inválidos.
- A listagem inicial retorna todas as pastas, sem paginação, filtro ou ordenação contratual.
- A consulta de árvore retorna a floresta completa, não apenas a árvore de uma raiz selecionada.
- Nenhuma regra de unicidade de nome é assumida, pois ela não consta do modelo informado.
- O projeto visível contém apenas o ponto de entrada Spring Boot e configuração Maven/Java 21; não há ainda padrões de controller/service/repository a reutilizar.
- A Constitution vigente determina Java 21, Spring Boot e PostgreSQL, em alinhamento com esta feature.

## Resolved Clarifications

- **Origem de `createdBy`**: derivar do principal autenticado; não aceitar identidade enviada pelo cliente. A infraestrutura de autenticação é uma dependência externa à feature.
- **Exclusão com dependentes**: seguir as ações das FKs existentes. Não adicionar cascata própria; responder `409 Conflict` se o PostgreSQL rejeitar a exclusão.
- **Stack e Constitution**: utilizar Java 21 conforme a Constitution atualizada pelo projeto.
