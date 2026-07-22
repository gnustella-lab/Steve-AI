# Matriz inicial de funcionalidades do Steve-AI

Baseline auditada em 2026-07-21 no commit `2ab83ea`, Minecraft 1.20.1, Forge 47.2.0, Java 17 e Gradle 8.4.

O repositório possui 75 classes Java de produção, 10 classes JUnit e um GameTest de smoke. O baseline `./gradlew test --rerun-tasks` passou com 22 testes, sem falhas ou skips. `StructureGeneratorsTest` é placeholder e não conta como cobertura real.

Esta matriz preserva o diagnóstico do baseline. O estado verificado após a Fase 1 está em
[`PHASE1_IMPLEMENTATION.md`](PHASE1_IMPLEMENTATION.md).

| Funcionalidade | Estado atual | Arquivos envolvidos | Limitações confirmadas | Melhoria proposta | Prioridade | Risco técnico |
|---|---|---|---|---|---|---|
| Ciclo de execução | Planejamento assíncrono, fila linear e ações por tick | `ActionExecutor`, `TaskPlanner`, `BaseAction` | Nova ordem cancela tudo; sem plano persistente, orçamento, retry determinístico ou checkpoint | Introduzir `AgentPlan`, resultados estruturados, política de recuperação e replanejamento limitado | P0 | Alto |
| Inventário do Steve | Inexistente; `PromptBuilder` informa `[empty]` | `SteveEntity`, `PromptBuilder`, ações | Itens minerados caem no mundo; ferramentas e tochas surgem do nada; construção não consome materiais | Criar `SteveInventory`, NBT versionado, coleta, transferência, equipamento, consumo e resumo para prompt | P0 | Alto |
| Mineração | Executa por tick e verifica região protegida | `MineBlockAction` | Teleporta, cria picareta de ferro, cria tochas, ignora durabilidade e inventário, pode perder drops | Coletar loot no inventário, selecionar ferramenta real, aplicar durabilidade e recuperar bloqueios sem teleporte padrão | P0 | Alto |
| Construção | Procedural/NBT, colaborativa e por tick | `BuildStructureAction`, `CollaborativeBuildManager`, `StructureGenerators` | Blocos surgem do nada, teleporte silencioso, sem estimativa, reserva, rollback ou modo Survival | Separar planejamento e execução, reservar e consumir materiais, checkpoints, preview e rollback | P0 | Alto |
| Crafting | Classe placeholder não registrada | `CraftItemAction`, `SteveAPI` | Não consulta `RecipeManager`, usa parâmetro divergente `count`, não fabrica nada | Planejador de receitas real, dependências, crafting 2x2/3x3, estações e testes determinísticos | P0 | Alto |
| Fundição | Inexistente | Nenhum componente dedicado | Sem fornalha, combustível, espera ou retirada do resultado | Criar ações e estado de fundição usando receitas do servidor e ticks | P0 | Alto |
| Catálogo de ações | Registry e plugin SPI já existem | `ActionRegistry`, `CoreActionsPlugin`, `PluginManager` | Prompt e validação duplicam metadados; `ActionExecutor` mantém switch; conflitos e API sem schema | Registrar `ActionDescriptor` e `JsonSchema`, gerar prompt e validar pelo registry, remover switch após migração | P0 | Médio |
| Saída estruturada | Parser Gson tolera cercas Markdown | `ResponseParser`, `PromptBuilder` | Sem schema de plano, limites globais, tipos aninhados ou reparo único; expõe campo `reasoning`; loga resposta completa inválida | Adotar `summary`, schema estrito, limites e reparo seguro único, sem log de prompt/resposta por padrão | P0 | Médio |
| Propriedade e multiplayer | Entidade tem UUID vanilla; manager indexa UUID e nome | `SteveEntity`, `SteveManager`, `SteveCommands` | Segurança e permissões usam nome; não há proprietário/autorizados/equipe; comandos são de operador | Persistir proprietário e ACL por UUID, autorizar proprietário/admin e limitar agentes por jogador | P0 | Alto |
| Permissões | Níveis ordinais e regiões em memória | `PermissionManager`, `ActionPermission` | Default `ALL`, identidade por nome, regiões não persistem, níveis permitem privilégios laterais acidentalmente | Capacidades granulares, default seguro, persistência, confirmação e auditoria | P0 | Alto |
| Memória | Objetivo e últimas 20 ações persistem em NBT | `SteveMemory`, `SteveEntity` | Sem versão, categorias, migração, retenção configurável, checkpoints ou conhecimento espacial persistente | Formato versionado com memória operacional, episódica, espacial e preferências | P1 | Alto |
| Percepção de mundo | Varredura síncrona amostrada em raio 16 | `WorldKnowledge`, `PromptBuilder` | Cerca de 4,9 mil leituras por planejamento, sem relevância por objetivo, ameaças, líquidos, chunks ou snapshot imutável | Criar `ObservationSnapshot` limitado, serializável e comparável, com coleta barata na thread do servidor | P1 | Alto |
| Fila de objetivos | `LinkedList<Task>` interna | `ActionExecutor` | Sem prioridade, pausa, retomada, inserção, status detalhado ou persistência | Criar fila de comandos e planos com política configurável e comandos administrativos | P1 | Alto |
| Resultados de ação | `success`, mensagem e `requiresReplanning` | `ActionResult`, ações | Sem parcial, retryable, código de erro ou observações | Expandir contrato imutável mantendo factories compatíveis por uma versão | P1 | Médio |
| Recuperação de movimento | Timeout simples, retries ilimitados em pathfind | `PathfindAction`, `CombatAction`, `IdleFollowAction` | Sem detector de progresso/oscilação; teleporte automático em mineração, combate, construção e idle | Detector de progresso, rotas alternativas, posições temporariamente bloqueadas e teleporte administrativo opcional | P1 | Alto |
| Sobrevivência | Combate e follow básicos | `CombatAction`, `IdleFollowAction`, `SteveEntity` | Steve é invulnerável sempre; não come, dorme, foge, busca abrigo ou protege inventário | Política operacional configurável, emergências e ações Survival reais | P1 | Alto |
| Coordenação multiagente | Compartilhamento limitado a blocos de construção | `CollaborativeBuildManager` | Identifica agente pelo nome, sem papéis, recursos ou logística; estado só em memória | `SharedJobBoard`, reservas atômicas e divisão determinística de plano | P1 | Alto |
| Provedores LLM | OpenAI, Groq e Gemini, sync legado e async resiliente | `TaskPlanner`, clientes `llm/async`, resilience | Sem endpoint custom, capabilities ou structured output; sync ainda público | Provider registry, OpenAI-compatible e remoção progressiva do caminho sync | P1 | Médio |
| Cache contextual | Caffeine compartilhado e chave inclui parâmetros | `LLMCache`, `ResilientLLMClient` | Contexto do mundo está no prompt, mas não há fingerprint explícito, escopo por mundo/jogador ou single-flight documentado | Chave determinística de observação e escopo, métricas e invalidação | P1 | Médio |
| Métricas e orçamento | Métricas parciais em resposta/cache/interceptor | `LLMResponse`, `MetricsInterceptor`, resilience | Sem agregação por Steve/jogador/plano, custo, budgets ou cooldown | Serviço de métricas e budget pré-chamada, sem registrar prompts completos | P2 | Médio |
| Interface K | GUI existente | `SteveGUI`, `SteveOverlayScreen`, handlers client | Não expõe plano, fila, inventário, latência, tokens ou controles completos | Modelo de estado sincronizado e UI desacoplada do núcleo | P2 | Alto |
| Comandos administrativos | `spawn`, `remove`, `list`, `stop`, `tell`, `status` | `SteveCommands` | Sem comandos de inventário, memória, fila, owner, provider e permissions; `status` é global | Expandir árvore Brigadier com autorização e redaction de segredos | P2 | Médio |
| Plugins | SPI, ordenação por dependência, DI e prioridades | `ActionPlugin`, `PluginManager`, `ActionRegistry` | Sem versão da API/compatibilidade/namespace; unload não desregistra ações; ciclos carregam mesmo assim | API versionada, namespace, rollback de carga, desregistro por plugin e plugin exemplo | P2 | Alto |
| Segurança destrutiva | Validação de volume e hooks de região em mineração/construção | `TaskValidator`, `PermissionManager`, ações | Regiões voláteis, default permissivo, sem blacklist, raio, confirmação, containers ou auditoria | Política fail-closed, regiões persistentes, limites e log estruturado | P0 | Alto |
| Threading | HTTP em executores; mundo alterado no tick do servidor | `TaskPlanner`, `ActionExecutor`, clientes async | Método sync legado pode bloquear; `WorldKnowledge` faz cálculo volumoso no servidor; shutdown dos executores não aparece no lifecycle | Remover chamadas sync do fluxo, snapshots limitados e lifecycle completo | P0 | Alto |
| Persistência e reinício | Entidade e memória básica usam NBT | `SteveEntity`, `SteveMemory` | Inventário/plano/permissões/coordenação não persistem; cancelamento não tem protocolo transacional | NBT versionado e checkpoints idempotentes, sem duplicação em load/cancel | P0 | Alto |
| Testes unitários | 22 testes reais no baseline, mais um placeholder | `src/test/java` | Sem inventário, schema, ownership, crafting, fila, budgets, cache contextual e migrações | TDD por fatias verticais, fakes LLM determinísticos | P0 | Médio |
| GameTests | Um smoke de runtime e registry | `SteveGameTests`, `empty.nbt` | Não cobre gameplay; release CI apenas valida esse smoke | Adicionar cenários autoritativos gradualmente, sempre verificando a linha `All N required tests passed` | P1 | Alto |
| Build e release | Jar-in-Jar verificado, JUnit no `check`, GameTest em release | `build.gradle`, `release.yml` | Não há CI em PR/push normal; deprecações do Gradle; `StructureGeneratorsTest` falso positivo | Workflow de CI separado, checks do artefato e GameTests focados | P1 | Baixo |
| JavaScript do LLM | Desativado e testado | `CodeExecutionEngine`, teste | Não há sandbox real | Manter bloqueado até limites reais de CPU, memória e timeout | P0 | Baixo |

## Decisões de preservação

1. Manter Forge 47.2.x, Minecraft 1.20.1, Java 17 e Gradle 8.x.
2. Preservar execução incremental por tick e o SPI existente.
3. Fazer toda leitura do mundo e toda mutação de entidade/bloco na thread do servidor.
4. Manter rede e persistência pesada fora da thread principal.
5. Não reativar JavaScript.
6. Evoluir por migrações compatíveis e commits de fase independentes.
