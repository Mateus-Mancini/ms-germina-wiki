# Quickstart: Comments API

## Prerequisites

- Java 21 disponível no ambiente.
- Maven Wrapper do repositório (`mvnw.cmd`) disponível.
- PostgreSQL configurado conforme as variáveis usadas pelo ambiente.
- Integrações de identidade autenticada e conteúdo publicado/anchor configuradas, conforme descrito em [data-model.md](data-model.md).

## Validation Commands

Para validar a migração contra PostgreSQL real, configure `POSTGRES_TEST_URL`,
`POSTGRES_TEST_USER` e `POSTGRES_TEST_PASSWORD` antes dos testes.

Na raiz do repositório:

```powershell
.\mvnw.cmd test
```

A execução deve concluir sem falhas e incluir testes unitários da camada de serviço, testes de persistência e testes de contrato HTTP da feature.

Para executar a aplicação localmente depois que o banco e as integrações estiverem disponíveis:

```powershell
.\mvnw.cmd spring-boot:run
```

## Acceptance Scenarios

### 1. Criar e consultar comentário ancorado

1. Obter um `contentId` publicado e um anchor válido.
2. Enviar `POST /api/comments` com `contentId`, `anchor` e texto não vazio.
3. Confirmar `201`, identificador, autor, anchor normalizado, datas e status ativo.
4. Consultar `GET /api/comments?contentId={contentId}&anchorType={type}&anchorValue={value}`.
5. Confirmar que o item aparece somente no conteúdo e anchor informados.

### 2. Rejeitar anchor inválido

1. Enviar criação com anchor inexistente, malformado ou pertencente a outro conteúdo.
2. Confirmar `400` para formato inválido ou `409` para anchor inexistente/desatualizado, conforme o contrato.
3. Confirmar que nenhum comentário parcial foi persistido.

### 3. Restringir edição e remoção

1. Criar comentário como usuário A.
2. Tentar `PATCH` e `DELETE` como usuário B.
3. Confirmar `403` e verificar que o comentário original não foi alterado.
4. Repetir como usuário A e confirmar atualização e remoção lógica.

### 4. Resposta administrativa

1. Criar comentário ativo.
2. Enviar `POST /api/comments/{commentId}/admin-replies` com identidade administrativa.
3. Confirmar `201` e vínculo com o comentário.
4. Repetir sem permissão administrativa e confirmar `403`.
5. Remover o comentário e confirmar que ele e suas respostas deixam de aparecer nas consultas ativas.

## Contract and Model References

- Endpoints, códigos e schemas: [contracts/comments-api.yaml](contracts/comments-api.yaml)
- Entidades, invariantes e estados: [data-model.md](data-model.md)
- Regras de negócio e cenários: [spec.md](spec.md)
