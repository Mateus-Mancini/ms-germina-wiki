# Data Model: Comments API

## Comment

Representa uma contribuição textual vinculada a um conteúdo e a uma posição desse conteúdo.

| Field | Type | Required | Rules |
|---|---|---:|---|
| `id` | UUID | yes | Identificador único e imutável. |
| `contentId` | UUID | yes | Deve referenciar conteúdo existente, publicado e disponível. |
| `authorId` | UUID | yes | Identidade do usuário que criou o comentário; não pode ser alterada. |
| `text` | string | yes | Não pode ser vazio após trim; deve respeitar o limite máximo definido para a feature. |
| `anchorType` | enum/string | yes | Tipo suportado pelo conteúdo; o valor deve ser validado pelo verificador de anchors. |
| `anchorValue` | string | yes | Valor canônico da posição; deve pertencer ao `contentId`. |
| `contentRevision` | string/long | yes | Revisão do conteúdo usada na validação do anchor. |
| `status` | enum | yes | `ACTIVE` ou `REMOVED`; remoção lógica. |
| `createdAt` | timestamp | yes | Preenchido na criação e imutável. |
| `updatedAt` | timestamp | yes | Atualizado somente quando o texto for editado. |

### Relationships

- Um `Comment` pertence a um `Content` por `contentId`.
- Um `Comment` pertence a um `User` por `authorId`.
- Um `Comment` pode possuir zero ou mais `AdminReply`.
- O anchor é validado contra o conteúdo e não é uma entidade independente nesta feature.

## AdminReply

Representa uma resposta publicada por um administrador para um comentário ativo.

| Field | Type | Required | Rules |
|---|---|---:|---|
| `id` | UUID | yes | Identificador único e imutável. |
| `commentId` | UUID | yes | Deve referenciar comentário `ACTIVE`. |
| `adminId` | UUID | yes | Deve representar usuário com permissão administrativa no momento da criação. |
| `text` | string | yes | Não pode ser vazio após trim; deve respeitar o limite máximo definido. |
| `createdAt` | timestamp | yes | Preenchido na criação e imutável. |

### Relationships

- Uma `AdminReply` pertence a exatamente um `Comment`.
- Uma resposta não pode existir sem comentário ativo.
- A consulta de um comentário ativo inclui suas respostas administrativas ordenadas por criação.
- Comentário removido e suas respostas não aparecem no conteúdo ativo.

## Value Objects and Integration Ports

### AnchorInput

- `type`: tipo de selector suportado pelo conteúdo.
- `value`: valor canônico do selector.
- `revision`: revisão opcional informada pelo consumidor; a revisão efetivamente validada é retornada pelo verificador.

### AuthenticatedUser

- `id`: identificador do usuário autenticado.
- `isAdmin`: permissão usada somente para moderação e respostas administrativas.

### ContentAnchorValidator

Contrato de integração consumido pelo serviço:

- `assertPublishedContent(contentId)`: confirma existência e disponibilidade do conteúdo.
- `validateAnchor(contentId, anchorInput)`: confirma formato, pertencimento e revisão; retorna anchor normalizado.

## State Transitions

```text
Comment: ACTIVE -> REMOVED
Comment: ACTIVE -> ACTIVE (texto editado; updatedAt muda)
Comment: REMOVED -> REMOVED (remoção repetida é idempotente)
AdminReply: criado somente para Comment ACTIVE; não possui edição ou restauração na v1
```

## Validation and Invariants

- `contentId`, `authorId`, `text`, `anchorType` e `anchorValue` são obrigatórios.
- O serviço valida conteúdo e anchor antes de persistir qualquer comentário.
- Edição não pode trocar `contentId`, `authorId`, `anchorType` ou `anchorValue`.
- Somente o autor edita/remove seu comentário; administrador pode remover qualquer comentário ativo.
- Somente administrador cria `AdminReply`, e o comentário alvo precisa estar ativo.
- Consultas ativas filtram `status = ACTIVE` e são ordenadas por `createdAt DESC, id DESC`.
