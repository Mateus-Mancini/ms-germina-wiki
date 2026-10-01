# Research: Comments API

## Decision 1: Preservar o stack real do repositório

- **Decision**: Planejar a feature em Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA e PostgreSQL, usando as camadas `controller -> service -> repository` e DTOs.
- **Rationale**: O `pom.xml` e a árvore de código existentes são Java e já possuem essas dependências. Não há implementação Kotlin para reaproveitar.
- **Alternatives considered**: Migrar a aplicação inteira para Kotlin. Foi rejeitado por ampliar drasticamente o escopo e não entregar valor específico para comentários.

## Decision 2: Isolar autenticação, autorização e conteúdo por portas de integração

- **Decision**: O serviço de comentários consumirá uma identidade autenticada e um verificador de conteúdo/anchor por interfaces de domínio; não criará um sistema novo de autenticação nem duplicará o modelo de páginas.
- **Rationale**: A especificação assume usuários, administradores, conteúdo publicado e anchors, mas nenhum desses módulos existe no código atual. Interfaces explícitas permitem testar as regras e integrar os módulos quando forem entregues.
- **Alternatives considered**: Criar usuários, autenticação e páginas dentro desta feature. Foi rejeitado por misturar responsabilidades e criar uma segunda fonte de verdade para permissões e conteúdo.

## Decision 3: Anchor estruturado e validado antes da persistência

- **Decision**: O contrato recebe um anchor estruturado com tipo e valor canônico, além do identificador do conteúdo. O serviço delega a validação de formato, pertencimento e publicação ao verificador de conteúdo antes de criar ou editar.
- **Rationale**: A regra de negócio exige que o anchor pertença ao conteúdo e possa ser revalidado quando o conteúdo mudar. Manter o valor normalizado e a revisão do conteúdo junto ao comentário permite detectar anchors obsoletos.
- **Alternatives considered**: Aceitar uma string livre sem verificação. Foi rejeitado porque permitiria comentários em posições inexistentes ou em outro conteúdo.

## Decision 4: Remoção lógica e respostas administrativas vinculadas

- **Decision**: Comentários removidos permanecem como registros inativos e não aparecem em consultas ativas; respostas administrativas são registros próprios ligados ao comentário original.
- **Rationale**: Isso preserva a consistência da discussão e permite que a moderação não altere comentários de outros usuários nem perca a relação entre resposta e comentário.
- **Alternatives considered**: Exclusão física e respostas como texto embutido no comentário. Foram rejeitados por perder histórico e dificultar auditoria e evolução do contrato.

## Decision 5: Persistência preparada para migrações versionadas

- **Decision**: O design inclui tabelas e índices em uma migração SQL versionada sob `src/main/resources/db/migration`; a execução deve seguir a infraestrutura de migrações do projeto quando ela estiver disponível.
- **Rationale**: A pasta de migrações existe, mas está vazia e o projeto não possui uma ferramenta configurada. O contrato de dados deve ser explícito sem esconder essa dependência de infraestrutura.
- **Alternatives considered**: Confiar somente na criação automática de schema do ORM. Foi rejeitado porque não fornece evolução controlada do PostgreSQL e conflita com a feature de migrações já prevista no repositório.

## Decision 6: Paginação estável

- **Decision**: Consultas usam página baseada em índice, tamanho limitado e ordenação determinística por `createdAt DESC` e identificador descendente.
- **Rationale**: A ordenação secundária evita duplicações ou saltos quando vários comentários possuem o mesmo instante de criação.
- **Alternatives considered**: Retornar todos os comentários sem paginação. Foi rejeitado por não atender FR-014 e não limitar o custo de consultas futuras.

## Resolved Risks and Dependencies

- Autenticação e autorização administrativa são dependências externas e devem expor identidade e papel administrativo ao serviço.
- Conteúdo publicado, revisão do conteúdo e validação de anchor precisam existir antes do fluxo de criação funcionar em ambiente integrado.
- A divergência entre a Constituição (Kotlin) e o código real (Java) é pré-existente; este plano não a resolve por migração ampla. Ela deve ser corrigida em uma alteração própria da Constituição antes da implementação final ou explicitamente ratificada pela equipe.
- O limite numérico de texto deve ser definido no planejamento de implementação como constante de domínio/configuração, mantendo o comportamento exigido pela especificação.
