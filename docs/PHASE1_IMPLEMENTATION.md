# Fase 1: inventário, propriedade e catálogo de ações

Data da verificação: 2026-07-21  
Baseline: `2ab83ea`  
Stack: Minecraft 1.20.1, Forge 47.2.0, Java 17 e Gradle 8.4

## Escopo entregue

### Inventário canônico

- `SteveInventory` é a única abstração de inventário da entidade.
- A capacidade de novos Steves vem de `behavior.inventorySlots`, com 27 slots por padrão.
- A inserção não altera o `ItemStack` oferecido e retorna explicitamente o excedente.
- O carregamento preserva uma capacidade persistida maior quando a configuração é reduzida.
- `drainAll()` esvazia os slots antes de retornar os itens, tornando chamadas repetidas idempotentes.
- Pickup e loot de mineração usam a mesma operação de inserção. Excedentes permanecem no mundo.
- O inventário é salvo em `SteveInventory`, com `DataVersion = 1`, `Size` e a lista vanilla `Items`.
- NBT legado sem `DataVersion` e `Size` continua aceito.

### Propriedade por UUID

- `SteveAccessProfile` persiste proprietário, usuários autorizados, equipe e perfil de permissão.
- O formato atual usa `DataVersion = 1`, `Owner`, `Authorized`, `Team` e `PermissionProfile`.
- Spawn executado por jogador associa o UUID desse jogador.
- Console e command blocks continuam capazes de criar Steves sem owner, mas só uma fonte administrativa pode controlá-los.
- `tell`, `stop` e `remove` exigem proprietário, usuário autorizado ou administrador.
- Saves antigos sem `AccessProfile` carregam sem erro e falham fechados para jogadores comuns.
- Nomes continuam sendo apenas identificadores de interface. Segurança e permissões novas usam UUID.

### Catálogo e schema

- Cada ação executável possui factory e `ActionDescriptor` na mesma entrada de `ActionRegistry`.
- O descritor define plugin, versão do schema, permissão necessária, capacidades, exemplos e `JsonSchema`.
- Prompt, parser, `TaskValidator`, executor e `PermissionManager` consultam o mesmo catálogo.
- Ações desconhecidas ou sem descritor falham fechadas.
- Registros descritos não podem sobrescrever silenciosamente ações de outro plugin.
- A API de registro antiga permanece temporariamente disponível e emite aviso de obsolescência.
- Sete ações funcionais estão publicadas: `pathfind`, `mine`, `gather`, `place`, `build`, `attack` e `follow`.
- `CraftItemAction` permanece fora do catálogo porque ainda é um placeholder. Crafting real pertence à Fase 2.
- O formato legado de construção com `material`, `width`, `height` e `depth` foi preservado, além do formato com `blocks` e `dimensions`.

### Resposta do LLM

- O formato preferido usa apenas `summary` e `tasks`.
- `reasoning` e `plan` antigos continuam aceitos por uma janela de compatibilidade.
- O parser aplica limites de resposta, resumo, tarefas, parâmetros, strings e arrays.
- Campos estruturais desconhecidos e valores complexos não permitidos são rejeitados.
- A resposta completa do modelo não é incluída no log de erro de parsing.

## Threading e segurança de mundo

- Chamadas HTTP continuam assíncronas.
- A resposta do planejamento é consumida pelo tick da entidade na thread do servidor.
- A quebra centralizada de bloco exige `ServerLevel`, chunk carregado, thread principal e região não protegida.
- Loot só é calculado e inserido depois que o servidor confirma a destruição do bloco.

## Compatibilidade

- Tags novas são opcionais durante o carregamento.
- O overload legado de `spawnSteve` foi mantido.
- APIs de permissão por nome foram mantidas como obsoletas por uma versão.
- O parser ainda aceita `plan` e `reasoning`, mas novos prompts não os solicitam.
- O crafting incompleto não é anunciado ao modelo nem aceito pelo trust boundary.

## Verificação final

Comandos canônicos executados depois das últimas alterações de produção:

```text
./gradlew clean build --stacktrace
./gradlew runGameTestServer --stacktrace
python3 /tmp/hermes-verify-steve-phase1.py
```

Resultados:

- Build e `verifyReleaseJar`: sucesso, 13 tarefas executadas.
- JUnit: 43 métodos em 16 suites, 0 falhas, 0 erros e 0 skips. Um método preexistente é placeholder,
  portanto há 42 testes efetivos com asserções.
- GameTest: `All 4 required tests passed`.
- JAR instalável: `build/libs/steve-ai-mod-1.5.1.jar`.
- SHA-256 verificado: `e882b9daad0af394a1427416989533b761bb0150ddc75a65851d5a3e5142609d`.
- Artefato contém `mods.toml`, manifesto, serviço SPI, fixture GameTest, classes novas e metadata Jar-in-Jar.
- Seis dependências Jar-in-Jar foram conferidas.
- `git diff --check`: sucesso.
- Nenhuma linha Java alterada acima de 120 caracteres.
- Nenhuma possível credencial detectada nas linhas adicionadas.

## Auditoria de encerramento

Data: 2026-07-22

A auditoria de encerramento encontrou e corrigiu riscos de duplicação que não estavam cobertos pela
verificação inicial:

- a seleção automática de ferramenta agora troca o slot com a mão principal, em vez de copiar o stack;
- o cancelamento da mineração desfaz a troca de maneira conservativa e falha fechado se o slot de origem
  tiver sido alterado externamente;
- o carregamento de NBT limpa equipamento ausente no snapshot, evitando equipamento residual ao reutilizar
  uma instância;
- a transferência para contêiner zera o saldo efetivamente inserido e seleciona deterministicamente o
  contêiner mais próximo;
- o helper de GameTests longos usa GameTestSequence.thenWaitUntil, pois chamadas recursivas de
  runAfterDelay(1) não avançavam a ação a cada tick.

Resultados reproduzidos no estado atual:

    ./gradlew test --rerun-tasks --console=plain
    BUILD SUCCESSFUL — 104 testes, 0 falhas, 0 erros, 0 skips

    ./gradlew build --rerun-tasks --console=plain
    BUILD SUCCESSFUL

    ./gradlew runGameTestServer --console=plain
    6/6 GameTests da Fase 1 passaram
    10/12 GameTests totais passaram

Os dois GameTests restantes (craftingConsumesIngredientsAndProducesResult e
smeltingConsumesInputAndProducesIngot) pertencem explicitamente à Fase 2 e não alteram o fechamento do
escopo de fundação. Eles continuam obrigatórios e falhando visivelmente; não foram desativados nem marcados
como opcionais.

## Não objetivos desta fase

- Crafting, fundição e árvore de receitas.
- Consumo de materiais por colocação e construção.
- Interface de inventário e ações de transferência/equipamento.
- Persistência de planos e recuperação após reinício.
- Regiões protegidas persistentes e permissões granulares por capacidade.
- Namespace e versionamento formal da API externa de plugins.

Esses itens permanecem distribuídos entre as Fases 2 a 6 do plano em `.hermes/plans/2026-07-21_141638-steve-ai-survival-companion.md`.
