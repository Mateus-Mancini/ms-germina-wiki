# Data Model: Comments API

Contrato vigente em 01/10/2026. Todas as operações exigem autenticação.

| Campo | Tipo | Regra |
|---|---|---|
| id | UUID | Imutável |
| pageId | UUID | Página existente |
| userId | UUID | Identidade autenticada, nunca escolhida no JSON |
| anchor.blockId | UUID | Marcador `<!--b:UUID-->` presente no conteúdo atual |
| text | string | Trim, 1 a 2000 unidades UTF-16 |
| status | enum | OPEN ou RESOLVED; enum nativo PostgreSQL comment_status |
| createdAt | timestamp | Criação |
| updatedAt | timestamp | Edição ou remoção |
| adminReplies | array | Respostas OPEN, por createdAt ASC e id ASC |

Raízes e respostas compartilham a tabela comments. Respostas têm parent_comment_id,
herdam page_id e block_id, e são persistidas explicitamente sem cascade do pai.
O DTO de resposta administrativa contém id, commentId, adminId, text e createdAt.

Somente o autor edita raízes; autor ou administrador remove. Respostas são imutáveis,
não recebem respostas e só podem ser criadas por administrador para raiz OPEN.
Raízes RESOLVED e suas respostas ficam ocultas em consultas individuais e listagens.
A remoção é idempotente e não restaura nenhum registro.

Listagens paginam somente raízes OPEN por createdAt DESC e id DESC. As respostas dos
IDs dessa página são carregadas em lote; DTOs são materializados dentro da transação.

O nome legado assertPublishedContent verifica existência via pages. O modelo atual
não possui estados de publicação/privacidade. validateAnchor verifica o marcador no
conteúdo atual; revisão não integra o contrato nem é usada para validar anchors.
