# Specification Quality Checklist: WikiLinks e Backlinks

**Purpose**: Validar completude e qualidade da especificação antes do planejamento
**Created**: 2026-09-28
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] Foco no valor de navegar entre páginas relacionadas.
- [x] Escopo limitado a WikiLinks/backlinks e integração com gravação de conteúdo existente.
- [x] Seções obrigatórias, cenários, requisitos e critérios de sucesso preenchidos.
- [ ] Sintaxe de WikiLink e contratos HTTP são detalhes técnicos necessários para esta feature de backend.

## Requirement Completeness

- [x] Não há marcadores [NEEDS CLARIFICATION].
- [x] Requisitos testáveis cobrem resolução por slug, unicidade lógica por par, reavaliação de links quebrados e remoção ao editar conteúdo.
- [x] Critérios de sucesso mensuráveis para extração, deduplicação, backlinks e ações FK.
- [x] Cenários para página inexistente e coleções vazias.
- [x] Exclusões e escopo fora desta feature identificados.
- [ ] Chave primária/constraints exatos de `page_links` precisam ser confirmados por introspecção PostgreSQL antes do mapeamento JPA.

## Feature Readiness

- [x] Endpoints e resumos de resposta definidos.
- [x] Dependência da entidade `Page`/slug da feature 002 registrada.
- [x] Regras de sincronização transacional e de exclusão documentadas.
- [ ] Garantia física de unicidade de `(source_page_id, target_page_id)` depende de verificar a constraint existente; sem ela, o plano deve usar sincronização transacional coordenada ou registrar incompatibilidade, sem DDL automático.

## Notes

- O repositório contém apenas evidência documental das FKs de `page_links`, não DDL nem entidade correspondente.
- A feature ainda requer leitura dos metadados reais de `page_links` antes de definir a chave JPA.
- Não foi criada nem proposta migration/DDL.
