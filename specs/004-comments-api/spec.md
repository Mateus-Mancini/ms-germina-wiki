# Feature Specification: Comments API

**Feature Branch**: `004-comments-api`

**Created**: 2026-09-28

**Status**: Implemented; contrato alinhado em 01/10/2026

**Input**: User description: "comments-api — CRUD de comentários, respostas de admin, validação de anchor"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Gerenciar comentários em conteúdo da wiki (Priority: P1)

Como usuário autenticado, quero criar, consultar, editar e remover meus comentários em um conteúdo da wiki para registrar dúvidas, contribuições e correções.

**Why this priority**: O ciclo básico de comentários é o valor central da funcionalidade e permite colaboração diretamente no conteúdo.

**Independent Test**: Criar um comentário em um página existente, consultá-lo, alterá-lo e removê-lo usando somente as permissões do autor.

**Acceptance Scenarios**:

1. **Given** um usuário autenticado e um anchor válido em página existente, **When** ele cria um comentário com texto válido, **Then** o comentário é persistido e retornado com autor, conteúdo, anchor e datas de criação e atualização.
2. **Given** um comentário pertencente ao usuário autenticado, **When** ele consulta ou edita o comentário, **Then** recebe os dados atuais e a edição é salva sem alterar o autor ou o anchor original.
3. **Given** um comentário pertencente ao usuário autenticado, **When** ele solicita sua remoção, **Then** o comentário deixa de aparecer nas consultas posteriores.
4. **Given** um usuário tentando alterar ou remover comentário de outro autor, **When** a operação é solicitada, **Then** a alteração é recusada e nenhum dado é modificado.

---

### User Story 2 - Consultar comentários por conteúdo e anchor (Priority: P1)

Como usuário autenticado, quero consultar os comentários associados a um conteúdo ou a um anchor específico para acompanhar a discussão no ponto correto da wiki.

**Why this priority**: A consulta contextual é necessária para tornar os comentários úteis durante a leitura e evitar discussões desconectadas do conteúdo.

**Independent Test**: Criar comentários em anchors diferentes e confirmar que cada consulta retorna apenas os comentários do conteúdo e do anchor solicitados, respeitando a ordenação definida.

**Acceptance Scenarios**:

1. **Given** comentários associados a diferentes anchors do mesmo conteúdo, **When** uma pessoa consulta um anchor específico, **Then** somente os comentários daquele anchor são retornados em ordem do mais recente para o mais antigo.
2. **Given** um conteúdo sem comentários para o anchor informado, **When** a consulta é realizada, **Then** o resultado é vazio e a operação não é tratada como erro.
3. **Given** um página inexistente, **When** uma pessoa tenta consultar seus comentários, **Then** a consulta é recusada sem revelar dados associados ao conteúdo.

---

### User Story 3 - Responder como administrador (Priority: P2)

Como administrador, quero responder a comentários e removê-los quando violarem as regras da comunidade, mantendo a relação entre comentário original e resposta.

**Why this priority**: Respostas administrativas dão suporte aos usuários e oferecem moderação mínima para preservar a qualidade da wiki.

**Independent Test**: Usar uma conta administrativa para responder a um comentário válido e remover um comentário inadequado; repetir as mesmas ações com uma conta comum para confirmar a restrição de permissão.

**Acceptance Scenarios**:

1. **Given** um comentário existente e uma conta administrativa, **When** o administrador envia uma resposta válida, **Then** a resposta é persistida como resposta administrativa vinculada ao comentário original.
2. **Given** uma resposta administrativa existente, **When** o administrador a consulta junto do comentário, **Then** a resposta é exibida com identidade administrativa e data de criação.
3. **Given** uma conta sem permissão administrativa, **When** tenta responder como administrador ou remover comentário de terceiro, **Then** a operação é recusada e o comentário permanece inalterado.
4. **Given** um comentário removido, **When** alguém consulta seu histórico de discussão, **Then** o comentário e suas respostas não são apresentados como conteúdo ativo.

---

### Edge Cases

- Um anchor ausente, vazio, malformado ou que não pertença ao conteúdo informado deve impedir a criação do comentário.
- Um anchor válido no momento da criação, mas removido ou alterado antes de uma edição, deve fazer a edição falhar sem perder o comentário existente.
- Texto vazio, composto apenas por espaços, ou acima do limite de tamanho definido para comentários deve ser rejeitado.
- Tentativas de criar comentário em página inexistente devem ser rejeitadas. Publicação e privacidade dependem de estados ainda ausentes no modelo de páginas.
- Consultas sem paginação usam os valores padrão; valores fora dos limites retornam 400. A ordenação usa createdAt e id para desempate.
- Remoção repetida do mesmo comentário deve retornar um resultado consistente sem restaurar ou duplicar respostas.
- Um administrador não deve conseguir responder a um comentário que não existe ou que já foi removido.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir que usuários autenticados criem comentários vinculados a uma página existente e a um anchor válido desse conteúdo.
- **FR-002**: O sistema MUST permitir consultar comentários por conteúdo e, opcionalmente, por anchor, retornando os comentários em ordem do mais recente para o mais antigo.
- **FR-003**: O sistema MUST permitir que o autor edite o texto do próprio comentário, preservando autor, conteúdo e anchor originais.
- **FR-004**: O sistema MUST permitir que o autor remova o próprio comentário.
- **FR-005**: O sistema MUST permitir que administradores removam comentários de qualquer autor por motivo de moderação.
- **FR-006**: O sistema MUST permitir que administradores criem uma resposta vinculada a um comentário ativo.
- **FR-007**: O sistema MUST identificar respostas administrativas como pertencentes ao administrador que as criou e mantê-las vinculadas ao comentário original.
- **FR-008**: O sistema MUST rejeitar operações realizadas por usuários sem autenticação ou sem a permissão necessária para o recurso solicitado.
- **FR-009**: O sistema MUST validar que o anchor existe, está no formato esperado e pertence ao conteúdo indicado antes de criar ou alterar um comentário.
- **FR-010**: O sistema MUST rejeitar texto vazio ou fora do limite de tamanho definido para comentários e respostas.
- **FR-011**: O sistema MUST impedir operações em página inexistente e ocultar respostas de raízes removidas. Publicação e privacidade ficam pendentes de um modelo de estados da página.
- **FR-012**: O sistema MUST retornar mensagens de erro consistentes e códigos de resultado distinguíveis para dados inválidos, recurso inexistente e falta de permissão.
- **FR-013**: O sistema MUST garantir que uma remoção não altere o autor, o conteúdo ou o anchor de outros comentários.
- **FR-014**: O sistema MUST aplicar paginação às consultas de comentários e informar de forma consistente a existência de páginas adicionais.

### Key Entities *(include if feature involves data)*

- **Comentário**: contribuição textual de um usuário associada a uma página existente e a um anchor; possui autor, texto, status, datas de criação e atualização e respostas administrativas.
- **Anchor**: referência validável a uma posição ou trecho específico dentro de um conteúdo da wiki; não pode apontar para outro conteúdo.
- **Resposta administrativa**: mensagem de um administrador vinculada a um comentário ativo, com autor administrativo, texto e data de criação.
- **Conteúdo da wiki**: página existente ao qual comentários e anchors pertencem.
- **Usuário**: pessoa autenticada que pode criar e gerenciar seus comentários; alguns usuários possuem permissão administrativa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Usuários autenticados conseguem concluir a criação de um comentário válido em até 30 segundos após iniciar a ação.
- **SC-002**: Pelo menos 95% das consultas de comentários retornam o primeiro conjunto de resultados em até 1 segundo em condições normais de uso.
- **SC-003**: 100% das tentativas de comentário com anchor inválido são rejeitadas sem persistir comentário parcial.
- **SC-004**: 100% das tentativas de edição ou remoção por usuário sem permissão deixam o comentário original inalterado.
- **SC-005**: Em testes de aceitação, pelo menos 90% dos usuários conseguem localizar a discussão correta ao consultar comentários por conteúdo e anchor.
- **SC-006**: Administradores conseguem responder ou remover um comentário em até 30 segundos, e a ação fica visível na consulta seguinte.

## Assumptions

- A autenticação e a identificação de usuários já existem e fornecem a identidade e a permissão administrativa necessárias.
- Conteúdos da wiki possuem um identificador estável e uma representação de anchors que pode ser validada antes da associação.
- Apenas página existente e disponível para leitura aceita novos comentários na primeira versão.
- Texto recebe trim e deve conter de 1 a 2000 unidades UTF-16 (comprimento de String Java).
- A primeira versão permite apenas uma resposta administrativa por interação direta, sem conversas ilimitadas entre usuários.
- A remoção será tratada como remoção lógica para preservar a consistência da discussão e dos registros de moderação, sem exibir o item como conteúdo ativo.
- Notificações, edição de respostas administrativas e comentários anônimos estão fora do escopo desta feature.

## Contrato vigente e limites operacionais (01/10/2026)

- Todas as operações exigem Bearer JWT, inclusive GET; chamadas anônimas retornam 401.
- Criação: `pageId`, `anchor.blockId` UUID e `text`; resposta usa `userId`, status `OPEN` e `adminReplies`.
- Consulta: `pageId` obrigatório, `blockId` opcional; `page=0`, `size=20`, máximo 100.
- Raízes: ordem `createdAt DESC, id DESC`; respostas: `createdAt ASC, id ASC`.
- Respostas administrativas não podem ser editadas nem receber outras respostas; só administrador cria uma resposta.
- Raiz `RESOLVED` e seus filhos ficam indisponíveis nas leituras, mesmo que o status armazenado do filho ainda seja OPEN.
- Anchor é o marcador `<!--b:UUID-->` no conteúdo atual da página; não há revisão no JSON nem validação de revisão.
- UUID/JSON malformado e parâmetros ausentes retornam 400. Esperas no banco possuem limite e retornam 503 com `COMMENT_STORAGE_UNAVAILABLE`.
- DTOs são construídos dentro da transação. A paginação consulta raízes e carrega respostas em uma consulta adicional para os IDs selecionados.

Os documentos de planejamento e pesquisa registram o desenho original; o contrato YAML, este documento e o modelo atualizado descrevem a API vigente.
