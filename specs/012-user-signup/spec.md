# Feature Specification: User Sign-up

**Feature Branch**: `feat/user-signup`

**Created**: 2026-10-01

**Status**: Implemented; awaiting human review before integration

**Input**: User description: "crie uma nova branch que vai criar um fluxo de sign-in para a criação do usuário, utilizando speckit e respeitando constitution e composição de arquivos"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar conta e entrar na wiki (Priority: P1)

Uma pessoa sem conta informa nome, email e senha, recebe confirmação do cadastro e usa essas credenciais para entrar na wiki pelo fluxo de login existente.

**Why this priority**: Hoje o login depende de contas criadas previamente no banco. O cadastro permite que o próprio usuário comece a utilizar a aplicação.

**Independent Test**: Cadastrar uma pessoa sem autenticação, entrar com as mesmas credenciais e consultar seu próprio perfil.

**Acceptance Scenarios**:

1. **Given** email não cadastrado, **When** a pessoa envia nome, email e senha válidos sem estar autenticada, **Then** uma única conta de membro é criada e a confirmação contém seu identificador, nome e email, sem senha ou informação de armazenamento da senha.
2. **Given** cadastro concluído, **When** a pessoa entra com o email retornado e a mesma senha, **Then** obtém acesso e consegue consultar seu perfil.
3. **Given** uma conta criada pelo cadastro, **When** tenta executar operação exclusiva de administrador, **Then** o acesso é negado.

### User Story 2 - Corrigir dados inválidos ou duplicados (Priority: P2)

A pessoa recebe uma resposta clara quando o cadastro não pode ser concluído, corrige os dados e tenta novamente sem alterar contas existentes.

**Why this priority**: Protege a consistência das contas e permite recuperar erros comuns de preenchimento.

**Independent Test**: Enviar campos inválidos e email já existente; verificar que nenhuma conta é criada ou alterada e que uma tentativa válida posterior funciona.

**Acceptance Scenarios**:

1. **Given** nome, email ou senha ausente, inválido ou fora dos limites, **When** solicita cadastro, **Then** recebe erro de dados inválidos e nenhuma conta é criada.
2. **Given** uma conta existente, **When** solicita cadastro com o mesmo email, **Then** recebe conflito e a conta original mantém nome, senha e permissões.
3. **Given** duas tentativas simultâneas com o mesmo email, **When** ambas são processadas, **Then** exatamente uma cria a conta e a outra recebe conflito.
4. **Given** solicitação contendo permissões ou outros campos não suportados, **When** solicita cadastro, **Then** recebe erro de dados inválidos sem criar uma conta.

### Edge Cases

- Nome e email perdem apenas espaços nas extremidades; nome vazio após esse ajuste é inválido.
- Email permanece sensível a maiúsculas e minúsculas, conforme a regra atual de identidade e login. Um email existente com a mesma grafia e espaços externos é conflito.
- Senha permanece exatamente como recebida, inclusive espaços; senha composta somente de espaços é inválida.
- Senha com caracteres multibyte deve respeitar tanto o limite mínimo de oito caracteres quanto o máximo de 72 bytes em UTF-8; acima desse limite é rejeitada, sem truncamento.
- Documento de cadastro malformado, vazio ou com campos desconhecidos é inválido.
- Falha de persistência não confirma cadastro e não revela senha, hash ou detalhes internos ao consumidor.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir cadastro sem autenticação com somente nome, email e senha.
- **FR-002**: Nome MUST ser obrigatório, não vazio e ter no máximo 150 caracteres após remoção de espaços externos; email MUST ser obrigatório, válido e ter no máximo 255 caracteres após o mesmo ajuste.
- **FR-003**: Senha MUST ser obrigatória, não composta somente de espaços, ter pelo menos oito caracteres e no máximo 72 bytes em UTF-8; MUST ser preservada exatamente como recebida.
- **FR-004**: A conta criada MUST ter somente as permissões de membro. Tentativas de fornecer papel, identificador, senha armazenada ou campos não suportados MUST ser rejeitadas.
- **FR-005**: Nome e email MUST ser persistidos sem espaços externos; a identidade por email MUST manter a comparação sensível a maiúsculas e minúsculas existente.
- **FR-006**: Email duplicado MUST produzir conflito, inclusive em tentativas simultâneas, sem modificar a conta existente ou criar contas adicionais.
- **FR-007**: Senhas MUST ser armazenadas somente em forma irreversível compatível com o login atual e MUST NOT aparecer em respostas, representações textuais do pedido ou mensagens de erro.
- **FR-008**: Cadastro concluído MUST retornar confirmação de criação contendo identificador, nome e email, e campos opcionais de perfil inicialmente vazios, sem conceder acesso automaticamente.
- **FR-009**: Dados inválidos ou malformados MUST resultar em erro de entrada; duplicidade MUST resultar em conflito; falha de armazenamento MUST NOT resultar em confirmação de sucesso.
- **FR-010**: Usuários recém-criados MUST conseguir entrar pelo login existente e consultar seu próprio perfil; login de contas existentes e restrições de acesso MUST continuar funcionando.
- **FR-011**: Cadastro, validação, duplicidade simultânea, proteção de senha e permissões MUST ser cobertos por testes automatizados, incluindo testes com o armazenamento real usado pelo projeto.

### Key Entities

- **Usuário**: Conta existente da wiki, com identificador único, nome, email único, senha protegida, papel de membro, perfil opcional e datas de criação/atualização.
- **Solicitação de cadastro**: Nome, email e senha fornecidos pela pessoa; não permite definir identidade, permissões ou dados internos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em todos os cenários automatizados de cadastro válido, uma conta é criada e as credenciais permitem entrar e consultar o próprio perfil.
- **SC-002**: Em todos os cenários automatizados de entrada inválida ou duplicada, nenhuma conta adicional é criada e contas existentes permanecem inalteradas.
- **SC-003**: Duas tentativas simultâneas com o mesmo email produzem exatamente uma conta, uma confirmação de criação e um conflito.
- **SC-004**: Nenhuma resposta ou representação textual testada revela senha ou senha armazenada; todas as contas criadas possuem permissões de membro.

## Assumptions

- "Sign-in para criação do usuário" significa cadastro (sign-up) seguido do login existente, dado que a autenticação já está implementada.
- O escopo é o backend deste repositório; telas de cadastro pertencem ao consumidor web.
- Cadastro é aberto, sem convite, confirmação de email, restrição de domínio, recuperação de senha ou criação de administradores nesta entrega.
- Não há emissão automática de credencial no cadastro; o consumidor chama o login após a confirmação.
- Os campos de perfil opcionais são preenchidos posteriormente pelo fluxo de edição de perfil existente.
- A composição de artefatos do Spec Kit e a constituição 1.0.2 permanecem as referências do desenvolvimento.
