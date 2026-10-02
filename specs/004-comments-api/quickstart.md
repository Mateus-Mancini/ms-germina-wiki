# Quickstart: Comments API

Requer JDK 21+, Maven Wrapper e Docker para PostgreSQL 18 descartável com Flyway.
Nenhuma credencial ou conexão ao banco de produção é necessária para os testes.

```powershell
.\mvnw.cmd '-DargLine=-Djava.net.preferIPv4Stack=true' test '-Dtest=*Comment*Test,SchemaMigrationTest,LambdaSecurityTest'
```

Todas as requisições exigem `Authorization: Bearer <token>`, inclusive GET.
Criação (`POST /api/comments`, Content-Type application/json):

```json
{"pageId":"UUID_DA_PAGINA","anchor":{"blockId":"UUID_DO_BLOCO"},"text":"Minha dúvida"}
```

A página deve existir e conter `<!--b:UUID_DO_BLOCO-->`. Confirme 201, Location,
pageId, userId, anchor.blockId, text normalizado, status OPEN e adminReplies.
Liste com `GET /api/comments?pageId=UUID_DA_PAGINA&blockId=UUID_DO_BLOCO&page=0&size=20`.
Consulte por `GET /api/comments/{id}`; edite como autor com PATCH e `{"text":"Novo texto"}`;
remova com DELETE (204, inclusive na repetição).

Administrador responde com `POST /api/comments/{id}/admin-replies` e `{"text":"Resposta"}`.
Confirme 201 e resposta visível após commit. Respostas não podem ser editadas nem
receber outras respostas. Após remover a raiz, a consulta do filho também deve retornar 404.

Valide 400 para UUID/JSON malformado, pageId ausente, texto inválido e paginação inválida;
401 para ausência de autenticação, 403 para falta de permissão e 409 para bloco ausente.
Os testes de PostgreSQL também seguram um lock na identidade do autor para verificar
que a criação retorna 503 COMMENT_STORAGE_UNAVAILABLE antes dos 20 segundos da Lambda,
sem gravar comentário parcial. Timeouts indicam falha da operação e ficam nos logs.

Contrato: [comments-api.yaml](contracts/comments-api.yaml). Política de publicação,
privacidade e revisão ainda depende de extensão do modelo de páginas.
