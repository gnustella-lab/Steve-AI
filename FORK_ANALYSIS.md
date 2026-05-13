# Análise minuciosa do fork Steve AI

Este repositório foi inicializado como um fork do projeto upstream [`YuvDwi/Steve`](https://github.com/YuvDwi/Steve). A análise abaixo consolida pontos que devem ser melhorados, corrigidos e adicionados após a importação do código-base.

## Melhorias prioritárias

1. **Configuração de provedores LLM por serviço**
   - Separar chaves e modelos de OpenAI, Groq e Gemini em campos próprios.
   - Evitar que um único campo `openai.apiKey` seja reutilizado para provedores diferentes.
   - Validar provedor/modelo ainda na inicialização para mensagens de erro mais claras.

2. **Robustez em ambientes sem chave de API**
   - O mod deve carregar e permitir uso de fallback local mesmo quando o usuário ainda não configurou credenciais.
   - Clientes assíncronos precisam falhar rapidamente com erro de autenticação em vez de quebrar o construtor do planejador.

3. **Cobertura real de testes**
   - Substituir testes placeholder por testes unitários de parser, fallback, cache e validação de clientes.
   - Adicionar testes de integração para fluxo `TaskPlanner -> ResponseParser -> ActionExecutor` com clientes LLM simulados.

4. **Execução de JavaScript gerado por LLM**
   - Implementar timeout efetivo no `CodeExecutionEngine`; a assinatura aceita timeout, mas a execução atual avalia o código diretamente.
   - Remover ou trocar o polyfill de console que tenta chamar classes Java dentro de um contexto sem acesso ao host.
   - Adicionar limites de instruções/memória quando o runtime GraalVM permitir.

5. **Qualidade de build e compatibilidade de Java**
   - Documentar Java 17 como requisito explícito para Forge 1.20.1.
   - Adicionar verificação de toolchain/CI para evitar execução acidental com JDK incompatível.
   - Tratar avisos de depreciação do Gradle antes da migração para Gradle 9.

6. **Observabilidade e diagnóstico**
   - Padronizar logs com provider, model, Steve ID e task ID.
   - Expor métricas de cache, circuit breaker, retries e fallback via comando `/steve status`.
   - Evitar registrar corpos de erro contendo dados sensíveis.

7. **Experiência de configuração**
   - Gerar arquivo de configuração inicial com comentários por provedor.
   - Adicionar comando de validação `/steve config test` para checar credenciais sem iniciar uma ação.
   - Suportar variáveis de ambiente para chaves, reduzindo risco de commit acidental de segredos.

8. **Sistema de ações**
   - Tornar a validação de parâmetros mais estrita por ação.
   - Adicionar cancelamento cooperativo e prioridades de fila.
   - Registrar resultados parciais para ações longas como mineração e construção.

9. **Memória e contexto**
   - Persistir memória importante entre reinícios de servidor.
   - Diferenciar memória conversacional, conhecimento do mundo e objetivos ativos.
   - Adicionar política clara de retenção para evitar prompts excessivos.

10. **Segurança e permissões**
    - Restringir ações destrutivas por permissão de jogador/servidor.
    - Validar coordenadas e áreas protegidas antes de mineração/construção.
    - Auditar todos os pontos em que o LLM controla comportamento no mundo.

11. **Documentação de uso**
    - Incluir matriz de compatibilidade Minecraft/Forge/Java.
    - Documentar comandos, atalhos, configuração por provedor e exemplos de prompts.
    - Separar documentação de usuário final da documentação de arquitetura.

12. **Extensibilidade de plugins**
    - Formalizar versionamento da API de plugins.
    - Adicionar exemplos mínimos de plugins externos.
    - Validar conflitos de nome entre ações registradas por plugins diferentes.

## Implementado nesta iteração

- Arquivos binários não são versionados neste fork para manter o Pull Request compatível; o Gradle Wrapper pode ser regenerado localmente com `gradle wrapper --gradle-version 8.4`.
- O projeto local foi populado com o código do upstream, preservando a estrutura Gradle/Forge original, mas sem versionar binários como `gradle-wrapper.jar`.
- Os clientes assíncronos de OpenAI, Groq e Gemini agora aceitam inicialização sem chave configurada, reportam `isHealthy() == false` e falham rapidamente em `sendAsync()` com `LLMException.ErrorType.AUTH_ERROR` não retentável.
- Foi adicionada cobertura unitária para o comportamento acima, garantindo que a ausência de credenciais não cause falha de construção do cliente nem chamada HTTP desnecessária.

## Próximos passos recomendados

1. Separar configuração por provedor e atualizar `config/steve-common.toml.example`.
2. Introduzir mocks/fakes de `AsyncLLMClient` para testar `TaskPlanner` sem rede.
3. Implementar timeout real e limites de recursos no `CodeExecutionEngine`.
4. Configurar CI com `./gradlew test build` em Java 17.
5. Criar comandos de diagnóstico no jogo para cache, health checks e fallback.
