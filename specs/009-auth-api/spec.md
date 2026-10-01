# Feature Specification: Auth API

**Feature Branch**: `009-auth-api`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "auth-api: implementar autenticação de usuários com login e JWT para servir de base às demais APIs protegidas. O usuário informa email e senha; requisições autenticadas identificam o usuário pelo UUID e distinguem administradores de membros. Credenciais inválidas são rejeitadas sem revelar qual dado estava incorreto, e senhas nunca são retornadas. Não inclui cadastro, recuperação ou alteração de senha, perfil, RBAC além do transporte da identidade e papel, nem mudanças em funcionalidades não relacionadas."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entrar com email e senha (Priority: P1)

Como membro ou administrador com uma conta existente, quero informar meu email e senha e receber um token de autenticação para acessar as funcionalidades protegidas da Wiki.

**Why this priority**: A autenticação é a base necessária para que usuários acessem as APIs protegidas.

**Independent Test**: Enviar credenciais válidas de uma conta existente e confirmar que a resposta permite autenticar uma requisição protegida sem expor senha ou dados de credencial.

**Acceptance Scenarios**:

1. **Given** uma conta existente com credenciais válidas, **When** a pessoa envia email e senha para `POST /api/auth/login`, **Then** recebe `200 OK` com um token JWT Bearer (`accessToken`, `tokenType` e `expiresAt`).
2. **Given** um email inexistente ou uma senha incorreta, **When** a pessoa tenta entrar, **Then** recebe a mesma resposta genérica de falha de autenticação, sem indicação de qual credencial falhou.
3. **Given** uma requisição de entrada sem email ou senha, ou com dados em formato inválido, **When** ela é enviada, **Then** recebe `400 Bad Request` sem revelar se a conta existe.

---

### User Story 2 - Acessar APIs protegidas como usuário identificado (Priority: P1)

Como usuário autenticado, quero que minhas requisições protegidas sejam associadas de forma consistente à minha conta para que as APIs possam atribuir corretamente minhas ações.

**Why this priority**: Sem uma identidade estável compartilhada, as APIs não podem associar ações ao usuário correto.

**Independent Test**: Usar o token emitido para uma conta e confirmar em uma API protegida existente que a identidade recebida corresponde ao UUID daquela conta; repetir sem token e com token inválido.

**Acceptance Scenarios**:

1. **Given** um token válido, **When** o usuário acessa uma API protegida, **Then** a identidade principal disponibilizada à API é o UUID da conta autenticada.
2. **Given** uma requisição sem token, com token malformado ou inválido, **When** ela acessa uma API protegida, **Then** recebe `401 Unauthorized` e o acesso é recusado como não autenticado.
3. **Given** um token expirado, **When** ele é usado em uma API protegida, **Then** recebe `401 Unauthorized` e o acesso é recusado como não autenticado.

---

### User Story 3 - Diferenciar membro e administrador (Priority: P2)

Como API protegida, quero receber o papel administrativo da pessoa autenticada junto de sua identidade para reconhecer administradores sem implementar autorização geral nesta funcionalidade.

**Why this priority**: Comentários e armazenamento de imagens já dependem de distinguir ações administrativas, mas as regras de autorização próprias desses recursos permanecem com suas APIs.

**Independent Test**: Autenticar contas existentes com cada papel e verificar que as APIs consumidoras recebem o mesmo UUID e identificam como administrador somente a conta com papel `admin`.

**Acceptance Scenarios**:

1. **Given** uma conta com papel `admin`, **When** ela é autenticada e acessa uma API protegida, **Then** sua identidade mantém o UUID da conta e expõe a autoridade administrativa reconhecida pelo projeto.
2. **Given** uma conta com papel `member`, **When** ela é autenticada e acessa uma API protegida, **Then** sua identidade mantém o UUID da conta e não recebe autoridade administrativa.
3. **Given** uma conta comum, **When** ela usa seu token em uma API que aplica uma regra administrativa própria, **Then** a autenticação não a promove a administradora nem substitui as regras daquela API.

### Edge Cases

- Um email inexistente e um email existente com senha incorreta devem resultar na mesma resposta pública, incluindo o mesmo status e conteúdo de erro.
- Credenciais válidas não devem permitir acesso após a expiração do token emitido.
- Tokens ausentes, malformados ou que não possam ser validados não devem criar uma identidade parcial nem permitir acesso protegido.
- Um UUID ou papel ausente ou inválido não deve ser convertido em identidade autenticada.
- Nenhuma resposta da API, inclusive de falha, deve conter senha ou hash de senha.
- Endpoints já públicos, incluindo saúde da aplicação e leitura de endereços de imagens, devem manter seu comportamento atual.
- Contas devem existir previamente; esta funcionalidade não oferece criação nem manutenção de contas.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST oferecer `POST /api/auth/login`, que receba email e senha de uma conta existente e, quando válidos, retorne `200 OK` com os campos `accessToken`, `tokenType` (`Bearer`) e `expiresAt`.
- **FR-002**: O sistema MUST aceitar somente credenciais válidas de uma conta existente para emitir um token de autenticação.
- **FR-003**: O sistema MUST rejeitar email inexistente e senha incorreta com o mesmo status `401 Unauthorized` e o mesmo conteúdo genérico de resposta, sem revelar se a conta existe nem qual credencial está incorreta.
- **FR-004**: O sistema MUST rejeitar credenciais ausentes ou malformadas com `400 Bad Request`, sem revelar a existência de uma conta.
- **FR-005**: O sistema MUST exigir token JWT válido no cabeçalho `Authorization` com esquema `Bearer` nas rotas protegidas e rejeitar tokens ausentes, malformados, inválidos ou expirados com `401 Unauthorized`.
- **FR-006**: O sistema MUST identificar cada pessoa autenticada pelo UUID da conta e disponibilizar esse UUID como nome da identidade principal para as APIs protegidas existentes.
- **FR-007**: O sistema MUST distinguir os papéis existentes `admin` e `member`, disponibilizando a autoridade administrativa `ROLE_ADMIN` somente para contas com papel `admin`.
- **FR-008**: O sistema MUST disponibilizar a identidade autenticada às APIs consumidoras pelo contrato existente `AuthenticatedUserProvider.currentUser()`, com o UUID em `AuthenticatedUser.id` e a indicação administrativa em `AuthenticatedUser.isAdmin`.
- **FR-009**: O sistema MUST assegurar que nenhuma resposta da API retorne senha ou hash de senha.
- **FR-010**: O sistema MUST manter inalterado o comportamento de funcionalidades e rotas não relacionadas à autenticação.
- **FR-011**: A funcionalidade MUST limitar-se à autenticação e ao transporte da identidade e do papel, sem implementar cadastro, recuperação ou alteração de senha, perfil de usuário ou regras gerais de autorização.

### Contratos e Dependências de Integração

- A autenticação depende de contas previamente existentes na tabela `users`, que contém UUID, email único, `password_hash` e papel `admin` ou `member`. A criação e a manutenção dessas contas não fazem parte desta funcionalidade.
- Para consumidores que usam `java.security.Principal`, incluindo pages-api e image-storage, `Principal.getName()` MUST retornar o UUID canônico da conta autenticada, em formato reconhecível como UUID.
- Para consumidores que usam o contrato comum de usuário autenticado, `AuthenticatedUserProvider.currentUser()` MUST retornar `AuthenticatedUser.id` com esse mesmo UUID e `AuthenticatedUser.isAdmin` como `true` somente para o papel `admin`.
- A autoridade de autenticação MUST incluir exatamente `ROLE_ADMIN` para administradores e MUST NOT incluir essa autoridade para membros. Esse é o contrato consumido por image-storage e compatível com a detecção administrativa do provider existente.
- A autenticação fornece identidade e papel; cada API protegida continua responsável por suas próprias regras de autorização. Em particular, comments-api usa `AuthenticatedUserProvider` e image-storage reconhece `ROLE_ADMIN`.
- A configuração atual de rotas públicas e protegidas permanece válida: health e `GET /api/images/{id}` continuam públicos, enquanto as demais rotas protegidas exigem autenticação válida.

### Key Entities *(include if feature involves data)*

- **Conta de usuário**: conta previamente provisionada com UUID, email, credencial armazenada e papel `admin` ou `member`; não é criada nem editada por esta funcionalidade.
- **Token de autenticação**: credencial temporária emitida após entrada válida, apresentada pelo cliente para identificar a conta em requisições protegidas e transportando a distinção de papel necessária às APIs consumidoras.
- **Identidade autenticada**: representação da pessoa associada a uma requisição protegida, composta pelo UUID e pela indicação de autoridade administrativa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos testes de aceitação com credenciais válidas, o usuário recebe um token utilizável e as APIs protegidas identificam o mesmo UUID da conta autenticada.
- **SC-002**: Em 100% dos testes de aceitação com email inexistente ou senha incorreta, a resposta pública é idêntica em status e conteúdo e não permite distinguir a causa da falha.
- **SC-003**: Em 100% dos testes com token ausente, inválido ou expirado, a API protegida recusa o acesso sem atribuir uma identidade autenticada.
- **SC-004**: Em 100% dos testes de papel, somente contas `admin` recebem a autoridade `ROLE_ADMIN` e `isAdmin=true`; contas `member` não recebem nenhuma indicação administrativa.
- **SC-005**: Em 100% das respostas verificadas da API, incluindo erros de autenticação, não há senha nem hash de senha.
- **SC-006**: Um usuário com credenciais válidas consegue obter o token e concluir a entrada em até 30 segundos em condições normais de uso.

## Assumptions

- A base de dados e as contas são provisionadas por processos existentes ou futuros fora desta funcionalidade; a tabela `users` do schema atual é a fonte dos dados da conta e dos papéis.
- A validade do token é limitada e informada ao cliente; após expirar, a pessoa precisa autenticar-se novamente. Um fluxo de renovação e um mecanismo adicional de revogação não fazem parte do escopo definido.
- O papel é transportado para que as APIs consumidoras possam aplicar suas próprias regras; esta funcionalidade não concede permissões sobre recursos.
- Os nomes dos contratos compartilhados (`Principal`, `AuthenticatedUserProvider`, `AuthenticatedUser`, `ROLE_ADMIN`) são dependências de integração já presentes no projeto, não uma nova API de usuários ou uma nova camada geral de RBAC.
- Endpoints existentes que não dependem de autenticação e comportamentos funcionais fora do escopo permanecem sem alteração.
