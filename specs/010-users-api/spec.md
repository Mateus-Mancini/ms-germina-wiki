# Feature Specification: API de Perfis de Usuários

**Feature Branch**: `010-users-api`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "Crie a specification da feature `users-api` do GerminaWiki. A feature deve permitir que usuários autenticados consultem e atualizem o próprio perfil e que qualquer consumidor autorizado do sistema consulte um perfil público. Escopo funcional: consultar o próprio perfil; atualizar o próprio perfil; consultar o perfil público de outro usuário pelo UUID; impedir que o cliente escolha qual usuário será alterado; garantir que dados privados das contas não sejam expostos no perfil público. Comportamento esperado: `/me` representa sempre o usuário autenticado; o identificador do usuário autenticado vem da identidade estabelecida pelo auth-api; a atualização é restrita ao próprio usuário; dados de autenticação e credenciais são internos e nunca aparecem em respostas públicas; o papel administrativo não deve ser exposto publicamente sem uma necessidade explicitamente justificada. Para a v1, trate como campos de perfil editáveis apenas os atributos de perfil propriamente ditos, e não credenciais ou identidade da conta. Limites: não implementar login, JWT, cadastro, troca de senha, recuperação de senha, administração de usuários, alteração de role ou funcionalidades não relacionadas. A specification deve descrever comportamento, regras, cenários de aceitação, privacidade dos campos e limites da feature, sem escolher framework ou estrutura de classes. Não criar outra specification de autenticação ou RBAC."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar o próprio perfil (Priority: P1)

Como usuário autenticado, quero consultar meu perfil para conferir os dados associados à minha conta sem que o cliente determine a identidade consultada.

**Why this priority**: A consulta do próprio perfil é a base para a pessoa visualizar e editar os próprios dados de perfil com segurança.

**Independent Test**: Autenticar como uma conta existente, consultar `/api/users/me` e verificar que os dados retornados correspondem ao UUID associado à identidade autenticada, não a um identificador escolhido no pedido.

**Acceptance Scenarios**:

1. **Given** uma identidade autenticada válida associada a um usuário existente, **When** a pessoa consulta `GET /api/users/me`, **Then** recebe `200 OK` com o perfil da conta identificada pelo auth-api.
2. **Given** um pedido sem identidade autenticada válida, **When** a pessoa consulta `GET /api/users/me`, **Then** o pedido é recusado com `401 Unauthorized`.
3. **Given** uma identidade autenticada cujo UUID não corresponda a uma conta existente, **When** a pessoa consulta `GET /api/users/me`, **Then** recebe `404 Not Found` e nenhum perfil de outra conta é retornado.

### User Story 2 - Atualizar o próprio perfil (Priority: P1)

Como usuário autenticado, quero atualizar somente meus atributos de perfil para manter minhas informações pessoais atuais sem poder alterar credenciais ou a identidade da conta.

**Why this priority**: A edição pessoal habilita a manutenção do perfil, enquanto a identidade confiável do auth-api impede que um cliente redirecione a alteração para outra conta.

**Independent Test**: Autenticar como uma conta, enviar uma atualização parcial válida e verificar que apenas os atributos de perfil enviados mudaram na conta cujo UUID veio da identidade autenticada.

**Acceptance Scenarios**:

1. **Given** uma identidade autenticada válida e um pedido com um ou mais atributos de perfil válidos, **When** a pessoa envia `PATCH /api/users/me`, **Then** somente os atributos enviados são atualizados na própria conta e a resposta contém o perfil atualizado.
2. **Given** um pedido de atualização que inclui um UUID ou outro identificador de usuário, **When** o pedido é enviado, **Then** a API o rejeita com `400 Bad Request` e não altera nenhuma conta.
3. **Given** um pedido que tenta alterar email, credenciais, papel ou metadados de conta, **When** o pedido é enviado, **Then** a API o rejeita com `400 Bad Request` e não altera nenhum dado.
4. **Given** um atributo de perfil obrigatório ou inválido, **When** o pedido é enviado, **Then** a API retorna `400 Bad Request` e preserva integralmente o perfil existente.
5. **Given** um pedido sem identidade autenticada válida, **When** a pessoa envia a atualização, **Then** o pedido é recusado com `401 Unauthorized` e nenhuma conta é alterada.

### User Story 3 - Consultar o perfil público de uma pessoa (Priority: P2)

Como consumidor autorizado do sistema, quero consultar o perfil público de outra pessoa pelo UUID para reconhecer sua participação na Wiki sem acessar dados privados da conta.

**Why this priority**: Perfis públicos tornam os autores reconhecíveis em outras experiências da Wiki sem revelar dados de conta ou autenticação.

**Independent Test**: Consultar pelo UUID perfis de duas contas distintas e conferir que cada resposta contém somente os campos públicos permitidos, independentemente de quem seja o consumidor autorizado.

**Acceptance Scenarios**:

1. **Given** o UUID de uma conta existente e um consumidor autorizado, **When** ele consulta `GET /api/users/{userId}`, **Then** recebe `200 OK` com o perfil público daquela conta.
2. **Given** uma conta existente com dados privados, **When** qualquer consumidor autorizado consulta seu perfil público, **Then** a resposta contém somente UUID, nome, avatar e biografia, sem email, papel, credenciais ou metadados internos da conta.
3. **Given** um UUID válido sem conta correspondente, **When** um consumidor autorizado consulta o perfil público, **Then** recebe `404 Not Found`.
4. **Given** um UUID malformado, **When** um consumidor autorizado consulta o perfil público, **Then** recebe `400 Bad Request`.

### Edge Cases

- O UUID presente em qualquer conteúdo enviado pelo cliente não altera qual conta `/me` consulta ou atualiza; a identidade estabelecida pelo auth-api é sempre a fonte do usuário-alvo.
- Um PATCH sem atributos editáveis é inválido e retorna `400 Bad Request`.
- A omissão de um atributo no PATCH preserva seu valor atual; o envio explícito de `null` limpa somente atributos opcionais que aceitam ausência. `name` não pode ser nulo, vazio ou composto somente por espaços.
- Campos não reconhecidos ou campos de identidade, credencial, papel e metadados enviados no PATCH são rejeitados; a rejeição não pode aplicar parcialmente os demais campos do pedido.
- Um perfil com `avatarUrl` ou `bio` nulos é retornado sem inventar valores substitutos; o perfil público continua expondo somente os campos públicos definidos.
- O fato de uma conta possuir papel `admin` não modifica o conteúdo do perfil público nem autoriza alterar o papel por esta API.
- A consulta de um perfil público não concede acesso a email, credenciais, informações de autenticação ou outros dados internos da conta.
- Os pedidos seguem as regras de acesso vigentes do sistema; esta feature não torna endpoints acessíveis anonimamente nem altera as políticas do auth-api.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A API MUST oferecer `GET /api/users/me` para consultar o perfil da conta associada à identidade autenticada da requisição.
- **FR-002**: A API MUST determinar o UUID de `/me` exclusivamente a partir da identidade estabelecida pelo auth-api; MUST NOT aceitar UUID enviado pelo cliente como seletor da conta consultada ou atualizada.
- **FR-003**: A API MUST oferecer `PATCH /api/users/me` para atualizar parcialmente o perfil da própria conta autenticada e MUST rejeitar a operação quando não houver identidade autenticada válida.
- **FR-004**: A atualização MUST permitir somente os atributos de perfil existentes `name`, `avatarUrl` e `bio`. O nome MUST ser não nulo, não vazio após desconsiderar espaços nas extremidades e ter no máximo 150 caracteres; `avatarUrl` e `bio` podem ser nulos.
- **FR-005**: Em uma atualização parcial, atributos omitidos MUST permanecer inalterados. O envio explícito de `null` MUST limpar `avatarUrl` ou `bio`; `name` nulo e um pedido sem atributo editável MUST ser rejeitado com `400 Bad Request`.
- **FR-006**: A API MUST rejeitar com `400 Bad Request` campos desconhecidos e tentativas de alterar `id`, `email`, credenciais, `role` ou metadados da conta. Uma solicitação inválida MUST NOT produzir atualização parcial.
- **FR-007**: A API MUST oferecer `GET /api/users/{userId}` para que qualquer consumidor autorizado consulte o perfil público de uma conta existente pelo UUID, sem exigir que o usuário autenticado seja o alvo do perfil.
- **FR-008**: A resposta pública MUST limitar-se a `id`, `name`, `avatarUrl` e `bio`. MUST NOT expor `email`, `role`, credenciais, dados de autenticação, hashes, ou metadados internos da conta.
- **FR-009**: O papel administrativo MUST NOT ser exposto nas respostas públicas. Esta feature não define necessidade de expô-lo em qualquer resposta e não permite alterá-lo.
- **FR-010**: Dados de credenciais e autenticação, incluindo senha e hash de senha, MUST NOT aparecer em nenhuma resposta de perfil, inclusive a resposta de `/me`.
- **FR-011**: A consulta autenticada do próprio perfil MUST incluir `id`, `name`, `email`, `avatarUrl` e `bio`; `email` é visível somente ao próprio usuário nesse contrato e não é editável por esta API.
- **FR-012**: A API MUST retornar `401 Unauthorized` para operações em `/me` sem identidade autenticada válida, `404 Not Found` para UUID de usuário inexistente e `400 Bad Request` para UUID malformado ou conteúdo de atualização inválido.
- **FR-013**: Respostas de consulta e atualização bem-sucedidas MUST retornar `200 OK`; perfis opcionais ausentes (`avatarUrl` ou `bio`) MUST ser representados como nulos, sem substituição por dados de outra conta.
- **FR-014**: A feature MUST depender do contrato de identidade já estabelecido pelo auth-api para obter o UUID autenticado e MUST NOT implementar ou redefinir autenticação, emissão/validação de token, autorização geral ou RBAC.
- **FR-015**: A feature MUST limitar-se a consulta e atualização do próprio perfil e consulta de perfil público. Cadastro, login, troca/recuperação de senha, administração de usuários, alteração de papel e mudanças em funcionalidades não relacionadas estão fora do escopo.

### Contrato de Privacidade dos Campos

| Campo ou grupo | Próprio perfil (`/me`) | Perfil público (`/{userId}`) | Pode ser atualizado nesta feature |
|---|---|---|---|
| `id` (UUID) | Incluído | Incluído | Não |
| `name` | Incluído | Incluído | Sim |
| `email` | Incluído somente para o próprio usuário | Excluído | Não |
| `avatarUrl` | Incluído | Incluído | Sim; `null` limpa o valor |
| `bio` | Incluído | Incluído | Sim; `null` limpa o valor |
| `role` | Excluído | Excluído | Não |
| Senha, hash e dados de autenticação | Excluídos | Excluídos | Não |
| Datas e demais metadados internos | Excluídos | Excluídos | Não |

### Contrato de API

| Operação | Endpoint | Sucesso | Erros principais |
|---|---|---|---|
| Consultar próprio perfil | `GET /api/users/me` | `200 OK` | `401 Unauthorized`, `404 Not Found` |
| Atualizar próprio perfil | `PATCH /api/users/me` | `200 OK` | `400 Bad Request`, `401 Unauthorized`, `404 Not Found` |
| Consultar perfil público | `GET /api/users/{userId}` | `200 OK` | `400 Bad Request`, `404 Not Found` |

O corpo de atualização aceita somente `name`, `avatarUrl` e `bio`. A distinção entre propriedade omitida e propriedade explicitamente nula deve ser preservada para que a atualização parcial não apague atributos omitidos e permita limpar os opcionais intencionalmente. O UUID do usuário-alvo não faz parte do corpo de atualização.

### Key Entities *(include if feature involves data)*

- **Conta de usuário**: Conta existente identificada por UUID, com email, credenciais, papel, atributos de perfil e metadados. Esta feature consulta e atualiza somente seus atributos de perfil autorizados.
- **Perfil público**: Projeção limitada da conta, composta pelo UUID, nome, avatar e biografia; não contém atributos privados da conta ou de autenticação.
- **Identidade autenticada**: Identidade fornecida pelo auth-api para a requisição e fonte exclusiva do UUID usado nas operações `/me`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de consulta e atualização de `/me`, o UUID do perfil alvo corresponde ao UUID da identidade autenticada e nunca a um identificador enviado pelo cliente.
- **SC-002**: Em 100% dos cenários de atualização válidos, somente atributos de perfil enviados são alterados; campos omitidos permanecem inalterados e atributos opcionais explicitamente nulos são limpos.
- **SC-003**: Em 100% dos testes de privacidade, respostas públicas contêm somente `id`, `name`, `avatarUrl` e `bio`, sem email, papel, credenciais, dados de autenticação ou metadados internos.
- **SC-004**: 100% das tentativas de alterar identidade, credenciais, papel ou metadados, bem como atualizações inválidas, são rejeitadas sem alterar dados persistidos.
- **SC-005**: Em 100% dos cenários de UUID inexistente, UUID malformado ou identidade ausente, a API retorna o status definido nesta especificação sem revelar dados de outra conta.
- **SC-006**: Em condições normais de uso, pelo menos 95% das consultas e atualizações de perfil válidas são concluídas em até 2 segundos, conforme percebido pelo consumidor.

## Assumptions

- O schema vigente contém `users.id`, `name`, `email`, `password_hash`, `avatar_url`, `bio`, `role`, `created_at` e `updated_at`. `name` é obrigatório com limite de 150 caracteres; `avatar_url` e `bio` são opcionais; os demais campos não são editáveis por esta feature.
- `name`, `avatarUrl` e `bio` são os atributos de perfil próprios para edição na v1; `email` identifica a conta e não é considerado editável como atributo de perfil.
- A visualização privada em `/me` pode incluir o email da própria pessoa, enquanto a visualização pública não o inclui. Nenhuma das duas respostas necessita expor papel ou metadados internos.
- Perfis públicos são consultáveis por qualquer consumidor autorizado segundo as regras de acesso já vigentes; “público” descreve os campos visíveis, não uma alteração à política de acesso anônimo.
- O auth-api já estabelece uma identidade autenticada cujo UUID corresponde à conta existente. Esta especificação depende desse contrato e não cria outra especificação de autenticação ou RBAC.
- As alterações de perfil persistem nos atributos existentes; não é necessária uma nova identidade, credencial, conta ou papel.
- A semântica de atualização parcial preserva propriedades omitidas e interpreta `null` como remoção somente para os atributos opcionais `avatarUrl` e `bio`.
- Esta feature não define política adicional para formato de URL de `avatarUrl`, limite de tamanho para `bio`, paginação ou ordenação de perfis, pois não são definidos pelo schema ou pelos requisitos fornecidos.
