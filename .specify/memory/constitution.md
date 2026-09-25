<!--
Sync Impact Report
- Version change: scaffold/unversioned -> 1.0.0
- Modified principles: template principles -> I. Especificação como fonte de verdade;
  II. Arquitetura em camadas; III. Contratos REST e DTOs; IV. Testes automatizados;
  V. Simplicidade, consistência e manutenibilidade
- Added sections: Restrições técnicas e de negócio; Fluxo de desenvolvimento e qualidade
- Removed sections: none (template scaffold replaced with project-specific content)
- Follow-up TODOs: RATIFICATION_DATE requires confirmation of the original adoption date.
-->

# GerminaWiki Constitution

## Core Principles

### I. Especificação como fonte de verdade

A especificação da funcionalidade define o comportamento esperado e prevalece sobre
implementações existentes em caso de conflito. Requisitos ambíguos, incompletos ou
conflitantes MUST ser esclarecidos antes da implementação. A implementação MUST NOT
adicionar comportamentos relevantes que não estejam previstos na especificação sem
justificativa e atualização dos requisitos.

### II. Arquitetura em camadas

O backend MUST seguir o fluxo Controller -> Service -> Repository. Controllers MUST
limitar-se à comunicação HTTP; Services MUST centralizar regras de negócio, validações e
casos de uso sem depender diretamente de detalhes HTTP; Repositories MUST cuidar apenas
do acesso e da persistência de dados. Novas funcionalidades MUST respeitar essa
separação, salvo justificativa técnica documentada.

### III. Contratos REST e DTOs

As APIs MUST seguir princípios REST, utilizar métodos HTTP e códigos de status coerentes
e manter contratos claros e consistentes. Endpoints novos ou alterados MUST usar DTOs
para entrada e saída quando aplicável, validar adequadamente os dados recebidos e seguir
os padrões de nomenclatura existentes. Alterações em endpoints MUST considerar o impacto
nos consumidores da API.

### IV. Testes automatizados

Toda funcionalidade nova MUST possuir testes automatizados adequados ao comportamento.
Os testes MUST cobrir prioritariamente regras de negócio, casos de sucesso, casos de
erro, validações relevantes e comportamentos suscetíveis a regressões. Alterações em
funcionalidades existentes MUST preservar os testes atuais ou atualizá-los quando a
mudança de comportamento for intencional.

### V. Simplicidade, consistência e manutenibilidade

O código MUST ser legível, simples, consistente com o projeto e fácil de testar e
compreender por outro integrante da equipe. Antes de criar uma nova abordagem, a equipe
MUST verificar se já existe solução equivalente. Duplicação, abstrações desnecessárias
e complexidade sem necessidade clara MUST ser evitadas.

## Restrições técnicas e de negócio

O GerminaWiki é uma API desenvolvida por estudantes para fornecer o backend de uma
aplicação web no formato de Wiki sobre a escola. O backend MUST utilizar Kotlin, Spring
Boot, PostgreSQL, APIs HTTP REST e DTOs para entrada e saída. Novas tecnologias ou
bibliotecas MUST ser adicionadas somente quando necessárias para atender a um requisito
ou resolver um problema técnico identificado.

Regras de negócio MUST permanecer na camada Service. Controllers e Repositories MUST NOT
assumir responsabilidades de negócio. Quando uma regra de negócio mudar, a implementação
e os testes relacionados MUST ser atualizados de forma consistente.

## Fluxo de desenvolvimento e qualidade

O desenvolvimento MUST ocorrer em branches relacionadas às funcionalidades ou tarefas.
Cada alteração MUST possuir escopo claro e resultar em commits compreensíveis. Pull
Requests MUST descrever claramente o que foi alterado, relacionar-se à funcionalidade ou
tarefa correspondente e possuir testes adequados antes da integração. Quando aplicável,
um integrante da equipe MUST realizar a revisão.

Mudanças em funcionalidades existentes MUST ser restritas à necessidade específica da
tarefa. Refatorações amplas, mudanças de arquitetura e alterações não relacionadas MUST
ser evitadas. Impactos sobre funcionalidades existentes MUST ser avaliados e os testes
correspondentes executados.

Ferramentas de Inteligência Artificial podem auxiliar no desenvolvimento, análise,
implementação, testes e documentação, mas o código gerado MUST ser revisado por um
integrante da equipe antes da integração. A utilização de IA NÃO substitui a
responsabilidade dos desenvolvedores pela compreensão e validação do código.

## Governance

Esta Constitution define os princípios duradouros do projeto e MUST ser considerada em
decisões técnicas, nesta ordem: requisitos da especificação, princípios desta
Constitution, padrões existentes e simplicidade da solução. Em caso de dúvida, a
especificação define o que fazer, o plano define como implementar, esta Constitution
define os princípios, as tarefas definem o trabalho e o código existente orienta a
consistência.

Alterações nesta Constitution MUST ser discutidas e registradas pela equipe antes de
serem adotadas. Uma alteração MUST atualizar a versão, a data da última emenda e o
relatório de impacto. A versão segue SemVer: MAJOR para remoções ou redefinições
incompatíveis de princípios; MINOR para novos princípios ou expansão material de
orientações; PATCH para esclarecimentos e correções não semânticas.

Revisões de código e Pull Requests MUST verificar conformidade com esta Constitution,
com a especificação, com o plano e com as tarefas aplicáveis. Toda complexidade
introduzida MUST possuir justificativa. A Constitution MUST permanecer estável e novas
regras só devem ser adicionadas quando representarem uma necessidade recorrente ou um
princípio importante para a evolução do GerminaWiki.

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE): confirmar data de adoção
original | **Last Amended**: 2026-09-25
