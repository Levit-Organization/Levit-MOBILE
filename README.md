# LEVIT Mobile

App Android nativo, feito em Kotlin, do **LEVIT**, um sistema para gerir tabelas personalizadas, equipe, cargos e permissões, recrutamento, automações e finanças de uma empresa. O app conversa com uma API REST própria (CodeIgniter 4 e PostgreSQL).

Projeto Integrador da disciplina de Dispositivos Móveis, IFES Campus Serra, curso Técnico em Informática para Internet.

## Equipe

| Integrante | Parte apresentada |
|---|---|
| [Nome 1] | Abertura, justificativa e objetivo |
| [Nome 2] | Funcionalidades, arquitetura e navegação |
| [Nome 3] | Protótipo e telas do app |
| [Nome 4] | Consulta, inserção, banco de dados e tecnologias |
| [Nome 5] | Interface, código, uso de IA e conclusão |

## Telas e funcionalidades

O app tem 18 telas ligadas por um único grafo de navegação (`main_graph.xml`).

| Área | O que faz |
|---|---|
| Acesso | Boas-vindas, login, cadastro, recuperar e redefinir senha |
| Módulos e itens | Criar tabelas com campos próprios e cadastrar itens, inclusive arquivos |
| Equipe e cargos | Convidar pessoas e definir permissões de cada cargo por módulo |
| Recrutamento | Candidatos por fase, com histórico de mudanças |
| Automações | Regras com condição e ação, e notificações no app |
| Financeiro e relatórios | Lançamentos por categoria e relatórios exportáveis |
| Perfil e configurações | Dados do usuário e preferências |

**Consulta (banco para tela):** Módulos, Equipe, Itens e Financeiro.
**Inserção (tela para banco):** formulário de tabela, itens, cargo, automação e cadastro.

## Capturas de tela

As imagens ficam na pasta `docs/prints/`. Cada tela é mostrada ao lado do protótipo do Figma.

| Protótipo | App Android |
|---|---|
| ![Login no protótipo](docs/prints/login-prototipo.png) | ![Login no app](docs/prints/login-app.png) |

## Tecnologias

| Biblioteca | Para que serve |
|---|---|
| Kotlin e Coroutines | Linguagem e trabalho em segundo plano |
| Material 3 | Componentes visuais |
| Navigation Component | Navegação entre telas |
| RecyclerView | Listas de itens, membros e cargos |
| Retrofit, Gson e OkHttp | Chamadas à API e leitura de JSON |
| Security Crypto | Token de sessão criptografado |

Versões e detalhes em [docs/BIBLIOTECAS_E_ACESSIBILIDADE.md](docs/BIBLIOTECAS_E_ACESSIBILIDADE.md).

Autenticação: login próprio da API, com token. O Firebase não foi usado, por acordo com a professora.

## Acessibilidade e interface

Contraste mínimo de 4,5:1 (WCAG AA), áreas de toque de pelo menos 44 dp, texto de no mínimo 12 sp, margens do sistema aplicadas (a câmera e a barra de status não cobrem o conteúdo). A tabela completa está em [docs/BIBLIOTECAS_E_ACESSIBILIDADE.md](docs/BIBLIOTECAS_E_ACESSIBILIDADE.md).

## Estrutura do projeto

```
app/src/main/
  java/com/example/levit/
    auth/        telas de acesso
    data/        API, repositórios e tratamento de erros
    ui/          componentes e utilitários de interface
    *Fragment.kt telas do app (uma HostActivity e vários Fragments)
  res/
    layout/      telas e itens de lista
    navigation/  grafo de navegação
    values/      strings, cores, dimensões e estilos
docs/            documentação, prints, MER e prompts
```

## Documentos da entrega

| Item | Arquivo |
|---|---|
| Modelo entidade-relacionamento | `docs/MER.pdf` |
| Apresentação | `docs/apresentacao.pdf` |
| Uso de IA e prompts | `docs/prompt.pdf` |
| Vídeo (inserção e consulta) | [inserir link] |

## Como rodar

### 1. Configure o `.env` do backend

Na pasta `api-backend`, confirme que o `.env` aponta para o PostgreSQL com as credenciais certas. Se ainda não rodou, execute:

```
php spark migrate
```

### 2. Suba o backend ouvindo em todas as interfaces

```
php spark serve --host 0.0.0.0 --port 8080
```

Deixe esse terminal aberto enquanto usa o app. O `--host 0.0.0.0` permite que outros dispositivos alcancem o servidor.

### 3. Rode o app pelo Android Studio

Com o Gradle sincronizado, aperte Run com o emulador ou o celular conectado. Depois de mudanças grandes, use `Build > Rebuild Project`.

### 4. Permita o acesso à rede local

Na primeira abertura o Android pede permissão de rede local. Toque em **Permitir**. Sem ela, nenhuma chamada ao backend funciona.

### 5. Ajuste o endereço do backend, se precisar

No emulador com o backend na mesma máquina, nada muda: o padrão `10.0.2.2:8080` já funciona. Em celular físico, toque e segure a logo na tela inicial, digite o `host:porta` e toque em **Salvar**. Isso só existe em builds debug.

| Cenário | O que digitar |
|---|---|
| Emulador, backend na mesma máquina | `10.0.2.2:8080` (padrão) |
| Celular físico, mesma rede wifi | IP local do computador, ex.: `192.168.0.23:8080` |

No Windows, o IP local aparece em `ipconfig`, no campo "Endereço IPv4".

### 6. Use o app

Cadastre uma conta ou entre com uma existente e navegue pelo menu lateral. O token dura 8 horas, depois o app pede login de novo.

## Por que a permissão de rede local existe

A partir da API 36, o Android exige a permissão `ACCESS_LOCAL_NETWORK` para apps abrirem conexões com endereços de rede local (10.x.x.x e 192.168.x.x, incluindo o `10.0.2.2` do emulador). Sem ela, as chamadas dão timeout mesmo com o servidor no ar. A `SplashActivity` pede a permissão sozinha. Em versões antigas do Android o pedido é ignorado.

## Build de release

O release só aceita HTTPS e precisa do endereço da API no build:

```
./gradlew assembleRelease -Plevit.apiUrl=https://<host>/api/v1/
```

Em release, a troca de servidor pela logo fica desligada e o tráfego HTTP puro é bloqueado. Sem `-Plevit.apiUrl`, o app de release falha ao abrir, de propósito. Em debug, o log HTTP mostra só a linha da requisição, sem corpo, para senhas e tokens não aparecerem no Logcat.

## Uso de inteligência artificial

Foi usado apenas o Claude (Anthropic), em chat. Os prompts e as técnicas estão em `docs/prompt.pdf`. Todo código gerado foi revisado, compilado e testado pela equipe.
