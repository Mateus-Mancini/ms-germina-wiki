# Feature Specification: API de Busca de Páginas

**Feature Branch**: `008-search-api` (diretório da especificação; branch Git não criada)

**Created**: 2026-09-30

**Status**: Draft

**Input**: Solicitação para oferecer busca textual de páginas com escopo opcional por pasta e descendentes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Buscar páginas na Wiki (Priority: P1)

Como pessoa usuária da Wiki, quero pesquisar palavras em títulos e conteúdo para encontrar páginas relevantes sem precisar saber em qual pasta elas estão.

**Why this priority**: A busca global oferece o principal valor da funcionalidade e inclui páginas organizadas e páginas ainda sem pasta.

**Independent Test**: Criar páginas com correspondências apenas no título, apenas no conteúdo e com relevâncias diferentes, além de uma página sem pasta; pesquisar sem filtro e conferir correspondências, ordenação e inclusão da página sem pasta.

**Acceptance Scenarios**:

1. **Given** páginas cujos títulos correspondem a `q`, **When** a pessoa faz uma busca global, **Then** as páginas correspondentes são retornadas.
2. **Given** páginas cujo conteúdo corresponde a `q`, **When** a pessoa faz uma busca global, **Then** as páginas correspondentes são retornadas.
3. **Given** duas ou mais páginas correspondentes com relevâncias diferentes, **When** os resultados são retornados, **Then** aparecem em ordem decrescente de relevância; empates têm ordenação secundária determinística por UUID crescente.
4. **Given** uma página sem pasta que corresponde a `q`, **When** a pessoa busca sem `folderId`, **Then** a página está nos resultados.
5. **Given** uma busca sem correspondências, **When** a consulta é válida, **Then** a API retorna `200 OK` com uma lista vazia.

---

### User Story 2 - Restringir a busca a uma pasta (Priority: P1)

Como pessoa usuária da Wiki, quero limitar uma busca a uma pasta, incluindo opcionalmente suas subpastas, para encontrar conteúdo dentro de uma parte específica da organização.

**Why this priority**: O filtro de pasta torna os resultados mais úteis em árvores com conteúdo relacionado distribuído por várias pastas.

**Independent Test**: Criar uma pasta com uma página, uma pasta descendente com outra página, uma pasta não relacionada com uma terceira página e uma página sem pasta; buscar na pasta selecionada com as duas opções de `includeSubfolders` e conferir a composição dos resultados.

**Acceptance Scenarios**:

1. **Given** páginas correspondentes diretamente na pasta informada e em outras pastas, **When** a busca usa `folderId` e `includeSubfolders=false` ou omite esse parâmetro, **Then** somente as páginas diretamente na pasta informada são retornadas.
2. **Given** páginas correspondentes na pasta informada, em descendentes e em pastas não relacionadas, **When** a busca usa `folderId` e `includeSubfolders=true`, **Then** são retornadas as páginas da pasta informada e de todos os seus descendentes, sem as páginas de pastas não relacionadas.
3. **Given** um `folderId` que não existe, **When** a pessoa faz a busca, **Then** a API retorna `404 Not Found` seguindo a convenção existente de pasta inexistente.
4. **Given** `includeSubfolders=true` sem `folderId`, **When** a pessoa faz uma busca global, **Then** a API pesquisa todas as páginas, inclusive as sem pasta; `includeSubfolders` não altera o escopo.

### Edge Cases

- `q` ausente, vazio ou contendo somente espaços retorna `400 Bad Request` e não executa uma busca.
- `folderId` malformado retorna `400 Bad Request`; um UUID válido sem pasta correspondente retorna `404 Not Found`.
- `includeSubfolders` ausente equivale a `false`; valor que não possa ser interpretado como booleano retorna `400 Bad Request`.
- Termos válidos que não correspondem a nenhuma página retornam `200 OK` e lista vazia.
- Uma pasta sem páginas correspondentes retorna lista vazia; com `includeSubfolders=true`, páginas correspondentes nos descendentes continuam elegíveis.
- A exclusão de uma pasta pode tornar `pages.folder_id` nulo conforme a FK atual; tais páginas continuam elegíveis em busca sem escopo de pasta.
- Conteúdo em português é indexado e consultado com a configuração de busca `english` já existente. A stemming/normalização dessa configuração é orientada ao inglês e pode produzir correspondências inesperadas ou deixar de relacionar formas morfológicas portuguesas. Esta limitação deve ser documentada; trocar a configuração está fora do escopo desta funcionalidade.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A API MUST expor `GET /api/search` com o parâmetro obrigatório `q` e os parâmetros opcionais `folderId` e `includeSubfolders`.
- **FR-002**: `q` MUST ser não nulo e conter pelo menos um caractere que não seja espaço em branco. A ausência ou invalidade de `q` MUST resultar em `400 Bad Request`, de acordo com o padrão de validação existente.
- **FR-003**: A busca MUST considerar título e conteúdo das páginas e MUST usar PostgreSQL full-text search com a configuração `english` existente.
- **FR-004**: Resultados MUST ser ordenados por relevância decrescente. Resultados com a mesma relevância MUST ter ordenação secundária determinística por UUID crescente.
- **FR-005**: A busca MUST retornar páginas correspondentes sem paginação nesta versão e retornar lista vazia com `200 OK` quando não houver correspondências.
- **FR-006**: Sem `folderId`, a busca MUST considerar todas as páginas, inclusive as cuja pasta seja nula. Nesse caso, `includeSubfolders` MUST não alterar o escopo.
- **FR-007**: Quando informado, `folderId` MUST identificar uma pasta existente; caso contrário a API MUST retornar `404 Not Found` de acordo com a convenção existente para pasta inexistente.
- **FR-008**: Com `folderId` e `includeSubfolders=false` (também o valor padrão quando omitido), a busca MUST incluir somente páginas cuja pasta seja exatamente a pasta informada.
- **FR-009**: Com `folderId` e `includeSubfolders=true`, a busca MUST incluir páginas da pasta informada e de todos os seus descendentes, e MUST excluir páginas de pastas fora dessa árvore e páginas sem pasta.
- **FR-010**: Um `folderId` que não seja um UUID válido e um `includeSubfolders` que não seja um booleano válido MUST resultar em `400 Bad Request` conforme as convenções de conversão e erro da API.
- **FR-011**: A funcionalidade MUST seguir a arquitetura Controller → Service → Repository em um pacote dedicado `com.wikigerminare.search`; a busca MUST NOT ser adicionada ao `PageController`.
- **FR-012**: A resposta MUST representar os dados das páginas encontradas usando os campos do DTO de resposta de página existente, sem modificar ou renderizar o Markdown armazenado.
- **FR-013**: A implementação MUST reutilizar o índice GIN `idx_pages_full_text_search` sobre `title` e `content` com configuração `english`, desde que compatível com a consulta, e MUST NOT introduzir mecanismo de busca ou dependência externos.
- **FR-014**: A funcionalidade MUST seguir o schema PostgreSQL existente como fonte de verdade. MUST NOT adicionar migration ou alterar o schema se o índice existente for compatível com a busca especificada.
- **FR-015**: O contrato MUST ser coberto por testes automatizados para correspondência em título e conteúdo, ordenação por relevância, filtro direto de pasta, filtro recursivo de descendentes, exclusão de pastas não relacionadas, inclusão de páginas sem pasta na busca global, pasta inexistente, `q` ausente/em branco, e os comportamentos de `includeSubfolders=false` e `true`.
- **FR-016**: A especificação e a documentação da funcionalidade MUST registrar que a configuração `english` pode degradar a qualidade de correspondência para conteúdo em português; a funcionalidade MUST NOT alterar essa configuração.

### API Contract

| Operação | Endpoint | Parâmetros | Sucesso | Erros principais |
|----------|----------|------------|---------|------------------|
| Buscar páginas | `GET /api/search` | `q` obrigatório; `folderId` opcional; `includeSubfolders` opcional, padrão `false` | `200 OK` com lista de páginas ordenada por relevância, ou lista vazia | `400 Bad Request` para parâmetros ausentes/inválidos; `404 Not Found` para pasta inexistente |

Exemplo: `GET /api/search?q=wiki&folderId=550e8400-e29b-41d4-a716-446655440000&includeSubfolders=true`. O cliente deve codificar `q` segundo as regras usuais de URL. A resposta segue o formato de coleção de páginas usado pela API de páginas; paginação não faz parte deste contrato.

### Key Entities *(include if feature involves data)*

- **Page**: Página existente com título e conteúdo pesquisáveis e uma referência nullable a `Folder`. Resultados contêm a representação de página já usada pela API.
- **Folder**: Pasta existente que contém páginas diretamente ou referencia uma pasta pai. A hierarquia define a expansão do escopo quando `includeSubfolders=true`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em todos os cenários de aceitação, a busca retorna exatamente as páginas esperadas para correspondência em título/conteúdo e escopo global/direto/recursivo.
- **SC-002**: Para qualquer conjunto de resultados de teste com relevâncias distintas, a ordem retornada corresponde à relevância decrescente; empates seguem UUID crescente de forma repetível.
- **SC-003**: 100% dos casos cobertos de `q` ausente/em branco, parâmetros malformados e pasta inexistente retornam os status definidos neste contrato.
- **SC-004**: Nenhuma página fora do escopo selecionado aparece nos resultados, e páginas sem pasta permanecem incluídas em toda busca global correspondente.

## Assumptions

- `q` é texto de busca comum, sem exigir do cliente sintaxe específica de operadores PostgreSQL.
- A resposta reutiliza os campos já definidos por `PageResponse`, inclusive conteúdo, e retorna uma lista sem paginação.
- UUID crescente foi escolhido como critério secundário estável para empates de relevância.
- O endpoint segue o padrão de acesso dos endpoints GET existentes; autenticação e autorização não são introduzidas por esta funcionalidade.
- A relação de pasta e as regras para a hierarquia são as do schema e da `folders-api` atuais. Não se altera a entidade ou API de pastas.
- A configuração PostgreSQL `english` pode não oferecer stemming apropriado para português; esta consideração não altera o escopo nem a configuração nesta etapa.
